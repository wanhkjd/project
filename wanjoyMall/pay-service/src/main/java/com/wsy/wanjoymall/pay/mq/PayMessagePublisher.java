package com.wsy.wanjoymall.pay.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wsy.wanjoymall.pay.domain.po.PayMessage;
import com.wsy.wanjoymall.pay.mapper.PayMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 支付成功通知的可靠投递（本地消息表 + 发布确认 + 退避重试）
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayMessagePublisher {

    public static final String PAY_EXCHANGE = "pay.direct";
    public static final String PAY_SUCCESS_KEY = "pay.success";

    // 最大重试次数，超过后标记为失败等待人工介入
    private static final int MAX_RETRY = 6;
    // 退避基数（秒），第 n 次重试延迟 = 基数 << n
    private static final long BASE_DELAY_SECONDS = 5;

    private final RabbitTemplate rabbitTemplate;
    private final PayMessageMapper payMessageMapper;

    @PostConstruct
    public void initCallbacks() {
        // 1.发布确认回调：Broker 收到消息后回 ack/nack（需配置 publisher-confirm-type=correlated）
        rabbitTemplate.setConfirmCallback(this::onConfirm);
        // 2.退回回调：消息到达交换机但没有匹配的队列（需配置 publisher-returns=true、mandatory=true）
        rabbitTemplate.setReturnsCallback(returned -> {
            String id = returned.getMessage().getMessageProperties().getHeader("msgId");
            log.error("消息被退回，无路由队列：msgId={}, replyText={}", id, returned.getReplyText());
            // 路由失败视为一次发送失败，交给重试任务处理
            if (id != null) {
                scheduleRetry(Long.valueOf(id));
            }
        });
    }

    public void saveAndPublishAfterCommit(Long bizOrderNo, Object payload) {
        // 必须在事务内调用：消息与业务同事务落库，业务回滚时消息记录也随之回滚，不会产生脏消息
        // 1.先落库，状态为待发送
        PayMessage message = new PayMessage()
                .setBizOrderNo(bizOrderNo)
                .setExchange(PAY_EXCHANGE)
                .setRoutingKey(PAY_SUCCESS_KEY)
                .setPayload(String.valueOf(payload))
                .setStatus(PayMessage.STATUS_PENDING)
                .setRetryCount(0)
                .setNextRetryTime(LocalDateTime.now().plusSeconds(BASE_DELAY_SECONDS))
                .setCreateTime(LocalDateTime.now())
                .setUpdateTime(LocalDateTime.now());
        payMessageMapper.insert(message);

        // 2.挂到当前事务上，提交成功后才发送；提交前发送可能让消费者查不到还没提交的支付状态
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send(message);
            }
        });
    }

    @Scheduled(fixedDelayString = "${wm.pay.message-retry-interval:5000}")
    public void retryPendingMessages() {
        // 兜底任务：confirm 回调丢失、服务宕机等情况下，靠它把待发送消息重投出去
        // 1.只取已到下次重试时间的待发送消息，限制批量大小避免一次扫太多
        List<PayMessage> pending = payMessageMapper.selectList(new LambdaQueryWrapper<PayMessage>()
                .eq(PayMessage::getStatus, PayMessage.STATUS_PENDING)
                .le(PayMessage::getNextRetryTime, LocalDateTime.now())
                .last("LIMIT 100"));
        for (PayMessage message : pending) {
            // 2.超过最大重试次数，标记失败并告警，避免无限重试
            if (message.getRetryCount() >= MAX_RETRY) {
                payMessageMapper.update(null, new LambdaUpdateWrapper<PayMessage>()
                        .set(PayMessage::getStatus, PayMessage.STATUS_FAILED)
                        .set(PayMessage::getUpdateTime, LocalDateTime.now())
                        .eq(PayMessage::getId, message.getId()));
                log.error("支付通知超过最大重试次数，需人工介入：msgId={}, bizOrderNo={}", message.getId(), message.getBizOrderNo());
                continue;
            }
            // 3.先推后下次重试时间再发送，防止发送耗时期间被下一轮扫描重复拿到
            scheduleRetry(message.getId());
            send(message);
        }
    }

    private void send(PayMessage message) {
        try {
            // 消息 id 同时放进 header（供 return 回调）和 CorrelationData（供 confirm 回调）
            rabbitTemplate.convertAndSend(message.getExchange(), message.getRoutingKey(),
                    Long.valueOf(message.getPayload()),
                    m -> {
                        m.getMessageProperties().setHeader("msgId", message.getId().toString());
                        return m;
                    },
                    new CorrelationData(message.getId().toString()));
        } catch (Exception e) {
            // 连接异常等本地失败不抛出，留给重试任务处理
            log.error("支付通知投递异常，等待重试：msgId={}", message.getId(), e);
        }
    }

    private void onConfirm(CorrelationData correlationData, boolean ack, String cause) {
        if (correlationData == null) {
            return;
        }
        Long id = Long.valueOf(correlationData.getId());
        if (ack) {
            // 1.ack 表示 Broker 已持久化该消息，标记已发送；带状态条件，避免迟到的 ack 覆盖已标记失败的记录
            payMessageMapper.update(null, new LambdaUpdateWrapper<PayMessage>()
                    .set(PayMessage::getStatus, PayMessage.STATUS_SENT)
                    .set(PayMessage::getUpdateTime, LocalDateTime.now())
                    .eq(PayMessage::getId, id)
                    .eq(PayMessage::getStatus, PayMessage.STATUS_PENDING));
        } else {
            // 2.nack 表示 Broker 拒收，安排重试
            log.error("Broker 拒绝消息：msgId={}, cause={}", id, cause);
            scheduleRetry(id);
        }
    }

    private void scheduleRetry(Long id) {
        // 1.已发送或已失败的消息不再安排重试
        PayMessage message = payMessageMapper.selectById(id);
        if (message == null || message.getStatus() != PayMessage.STATUS_PENDING) {
            return;
        }
        // 2.指数退避：5s, 10s, 20s, 40s ...
        int nextCount = message.getRetryCount() + 1;
        long delay = BASE_DELAY_SECONDS << Math.min(nextCount, 10);
        // 3.用 retry_count 作为版本号做条件更新，避免 confirm/return/定时任务并发时重复累加
        payMessageMapper.update(null, new LambdaUpdateWrapper<PayMessage>()
                .set(PayMessage::getRetryCount, nextCount)
                .set(PayMessage::getNextRetryTime, LocalDateTime.now().plusSeconds(delay))
                .set(PayMessage::getUpdateTime, LocalDateTime.now())
                .eq(PayMessage::getId, id)
                .eq(PayMessage::getRetryCount, message.getRetryCount()));
    }
}
