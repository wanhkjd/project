package com.wsy.wanjoymall.trade.service.impl;

import com.wsy.wanjoymall.api.client.ItemClient;
import com.wsy.wanjoymall.api.dto.ItemDTO;
import com.wsy.wanjoymall.common.domain.SeckillOrderMsgDTO;
import com.wsy.wanjoymall.common.exception.BizIllegalException;
import com.wsy.wanjoymall.trade.constants.MQConstants;
import com.wsy.wanjoymall.trade.domain.po.Order;
import com.wsy.wanjoymall.trade.domain.po.OrderDetail;
import com.wsy.wanjoymall.trade.mapper.OrderDetailMapper;
import com.wsy.wanjoymall.trade.mapper.OrderMapper;
import com.wsy.wanjoymall.trade.service.ISeckillOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillOrderServiceImpl implements ISeckillOrderService {

    // 秒杀订单只有余额支付
    private static final int PAYMENT_TYPE_BALANCE = 3;

    private final OrderMapper orderMapper;
    private final OrderDetailMapper detailMapper;
    private final ItemClient itemClient;
    private final RabbitTemplate rabbitTemplate;

    // 秒杀订单未支付自动关闭的等待时间（毫秒）
    @Value("${wm.order.seckill-pay-timeout:300000}")
    private long seckillPayTimeoutMillis;

    @Override
    @Transactional
    public void createSeckillOrder(SeckillOrderMsgDTO msg) {
        // 1.查商品快照（走 item-service 缓存）；价格用消息里的秒杀价，不用商品原价
        List<ItemDTO> items = itemClient.queryItemByIds(List.of(msg.getItemId()));
        if (items == null || items.isEmpty()) {
            // 商品服务不可用时 fallback 返回空列表，抛异常让 MQ 重试而不是建一张没有明细的单
            throw new BizIllegalException("商品[" + msg.getItemId() + "]不存在");
        }
        ItemDTO item = items.get(0);
        // 2.插入订单：id 用抢购时预生成的，(seckill_activity_id, user_id) 唯一索引保证重复消息只建一单
        Order order = new Order()
                .setId(msg.getOrderId())
                .setTotalFee(msg.getSeckillPrice())
                .setPaymentType(PAYMENT_TYPE_BALANCE)
                .setUserId(msg.getUserId())
                .setStatus(1)
                .setSeckillActivityId(msg.getActivityId());
        try {
            orderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            log.info("秒杀订单已存在，忽略重复消息：activityId={}, userId={}", msg.getActivityId(), msg.getUserId());
            return;
        }
        // 3.订单明细，数量固定 1
        OrderDetail detail = new OrderDetail()
                .setOrderId(order.getId())
                .setItemId(item.getId())
                .setNum(1)
                .setName(item.getName())
                .setSpec(item.getSpec())
                .setPrice(msg.getSeckillPrice())
                .setImage(item.getImage());
        detailMapper.insert(detail);
        // 4.先发延迟消息再扣库存：扣库存失败本地事务回滚，延迟消息到期时查不到订单会直接忽略
        rabbitTemplate.convertAndSend(
                MQConstants.DELAY_EXCHANGE_NAME,
                MQConstants.DELAY_ORDER_KEY,
                order.getId(),
                message -> {
                    message.getMessageProperties().setDelayLong(seckillPayTimeoutMillis);
                    return message;
                }
        );
        // 5.DB 库存兜底扣减：失败抛异常回滚订单，由 MQ 重试
        itemClient.deductSeckillStock(msg.getActivityId());
        log.info("秒杀订单{}已落库，activityId={}, userId={}", order.getId(), msg.getActivityId(), msg.getUserId());
    }
}
