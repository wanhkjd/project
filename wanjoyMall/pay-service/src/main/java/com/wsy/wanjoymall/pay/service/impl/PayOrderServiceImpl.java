package com.wsy.wanjoymall.pay.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wsy.wanjoymall.api.client.OrderClient;
import com.wsy.wanjoymall.api.client.UserClient;
import com.wsy.wanjoymall.api.dto.OrderDTO;
import com.wsy.wanjoymall.common.exception.BizIllegalException;
import com.wsy.wanjoymall.common.exception.ForbiddenException;
import com.wsy.wanjoymall.common.utils.BeanUtils;
import com.wsy.wanjoymall.common.utils.UserContext;
import com.wsy.wanjoymall.pay.domain.dto.PayApplyDTO;
import com.wsy.wanjoymall.pay.domain.dto.PayOrderFormDTO;
import com.wsy.wanjoymall.pay.domain.po.PayOrder;
import com.wsy.wanjoymall.pay.enums.PayStatus;
import com.wsy.wanjoymall.pay.mapper.PayOrderMapper;
import com.wsy.wanjoymall.pay.mq.PayMessagePublisher;
import com.wsy.wanjoymall.pay.service.IPayOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * <p>
 * 支付订单 服务实现类
 * </p>
 *
 * @since 2023-05-16
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PayOrderServiceImpl extends ServiceImpl<PayOrderMapper, PayOrder> implements IPayOrderService {

    // 订单未付款状态，与 trade-service 的 Order.status 约定一致
    private static final int ORDER_STATUS_UNPAID = 1;

    private final UserClient userClient;
    private final OrderClient orderClient;
    private final PayMessagePublisher payMessagePublisher;


    @Override
    public String applyPayOrder(PayApplyDTO applyDTO) {
        // 1.服务端校验订单：归属、状态、金额都以交易服务的订单为准，前端传来的 amount 只用于比对
        OrderDTO order = checkOrder(applyDTO);
        applyDTO.setAmount(order.getTotalFee());
        // 2.幂等性校验：同一业务订单只会有一张支付单
        PayOrder payOrder = checkIdempotent(applyDTO);
        // 3.返回结果
        return payOrder.getId().toString();
    }

    @Override
    @Transactional
    public void tryPayOrderByBalance(PayOrderFormDTO payOrderFormDTO) {
        // 1.行锁查询支付单，并发重复请求在此串行化，后来者拿到锁时看到的已是最新状态
        PayOrder po = baseMapper.selectByIdForUpdate(payOrderFormDTO.getId());
        if (po == null) {
            throw new BizIllegalException("支付单不存在");
        }
        // 2.校验订单归属，防止越权支付他人订单
        if (!Objects.equals(po.getBizUserId(), UserContext.getUser())) {
            throw new ForbiddenException("无权操作该支付单");
        }
        // 3.判断状态，已支付或已关闭的支付单直接拒绝
        if (!PayStatus.WAIT_BUYER_PAY.equalsValue(po.getStatus())) {
            throw new BizIllegalException("交易已支付或关闭！");
        }
        // 4.扣减余额：金额以服务端支付单为准，支付单 id 作为扣款流水幂等键
        //   即使本步成功后本服务宕机，重试或对账时也不会二次扣款
        userClient.deductMoney(payOrderFormDTO.getPw(), po.getAmount(), po.getId());
        // 5.修改支付单状态，带状态条件的更新是行锁之外的第二道幂等保护
        boolean success = markPayOrderSuccess(po.getId(), LocalDateTime.now());
        if (!success) {
            throw new BizIllegalException("交易已支付或关闭！");
        }
        // 6.支付通知写入本地消息表，与支付状态同事务提交，提交后再投递到 MQ
        payMessagePublisher.saveAndPublishAfterCommit(po.getBizOrderNo(), po.getBizOrderNo());
    }

    @Override
    @Transactional
    public void reconcilePayOrder(PayOrder po) {
        // 对账场景：扣款成功但支付单仍未更新（如扣款后本服务宕机），根据扣款流水补齐支付状态
        // 1.向用户服务确认该支付单是否存在扣款流水，没有流水说明用户侧没扣钱，不能凭空标记已支付
        Boolean deducted = userClient.existsDeductRecord(po.getId());
        if (!Boolean.TRUE.equals(deducted)) {
            return;
        }
        // 2.已扣款则补齐支付状态；条件更新失败说明已被其他线程处理，无需重复发通知
        boolean success = markPayOrderSuccess(po.getId(), LocalDateTime.now());
        if (success) {
            log.warn("对账补齐支付状态：payOrderId={}, bizOrderNo={}", po.getId(), po.getBizOrderNo());
            // 3.补发支付成功通知，让交易服务把订单标记为已支付
            payMessagePublisher.saveAndPublishAfterCommit(po.getBizOrderNo(), po.getBizOrderNo());
        }
    }

    @Override
    @Transactional
    public boolean closePayOrderByBizOrderNo(Long bizOrderNo) {
        // 订单超时关闭时调用，关闭后 tryPayOrderByBalance 的状态校验会拒绝后续支付
        // 1.行锁查询支付单：与正在进行的支付请求互斥，付款和关单只能有一个成功
        PayOrder po = baseMapper.selectByBizOrderNoForUpdate(bizOrderNo);
        if (po == null) {
            // 用户从未申请支付单，没有可关闭的，视为可以关单
            return true;
        }
        // 2.已支付成功：不能关闭，告知调用方改为标记订单已支付
        if (PayStatus.TRADE_SUCCESS.equalsValue(po.getStatus())) {
            return false;
        }
        // 3.已经关闭：重复的关单请求，直接返回成功
        if (PayStatus.TRADE_CLOSED.equalsValue(po.getStatus())) {
            return true;
        }
        // 4.待提交/待支付：标记为已关闭
        lambdaUpdate()
                .set(PayOrder::getStatus, PayStatus.TRADE_CLOSED.getValue())
                .eq(PayOrder::getId, po.getId())
                .update();
        log.info("订单{}超时未支付，支付单{}已关闭", bizOrderNo, po.getId());
        return true;
    }

    public boolean markPayOrderSuccess(Long id, LocalDateTime successTime) {
        return lambdaUpdate()
                .set(PayOrder::getStatus, PayStatus.TRADE_SUCCESS.getValue())
                .set(PayOrder::getPaySuccessTime, successTime)
                .eq(PayOrder::getId, id)
                // 支付状态的乐观锁判断，返回 false 说明支付单已不是待支付状态（重复请求）
                .in(PayOrder::getStatus, PayStatus.NOT_COMMIT.getValue(), PayStatus.WAIT_BUYER_PAY.getValue())
                .update();
    }

    private OrderDTO checkOrder(PayApplyDTO applyDTO) {
        // 1.查询订单，防止前端篡改金额或替他人订单申请支付
        OrderDTO order = orderClient.queryOrderById(applyDTO.getBizOrderNo());
        if (order == null || order.getId() == null) {
            throw new BizIllegalException("订单不存在");
        }
        // 2.只能为自己的订单申请支付
        if (!Objects.equals(order.getUserId(), UserContext.getUser())) {
            throw new ForbiddenException("无权操作该订单");
        }
        // 3.只有未付款订单才能申请支付单
        if (order.getStatus() == null || order.getStatus() != ORDER_STATUS_UNPAID) {
            throw new BizIllegalException("订单已支付或关闭");
        }
        // 4.前端展示的金额必须与服务端订单金额一致，不一致说明页面数据过期或被篡改
        if (!Objects.equals(order.getTotalFee(), applyDTO.getAmount())) {
            throw new BizIllegalException("支付金额与订单不符");
        }
        return order;
    }

    private PayOrder checkIdempotent(PayApplyDTO applyDTO) {
        // 1.首先查询支付单
        PayOrder oldOrder = queryByBizOrderNo(applyDTO.getBizOrderNo());
        // 2.判断是否存在
        if (oldOrder == null) {
            // 不存在支付单，说明是第一次，写入新的支付单并返回
            PayOrder payOrder = buildPayOrder(applyDTO);
            payOrder.setPayOrderNo(IdWorker.getId());
            try {
                save(payOrder);
            } catch (DuplicateKeyException e) {
                // 并发申请同一订单时，biz_order_no 唯一索引拦下后来者，直接复用已生成的支付单
                log.info("订单{}的支付单已由并发请求创建，复用已有支付单", applyDTO.getBizOrderNo());
                return queryByBizOrderNo(applyDTO.getBizOrderNo());
            }
            return payOrder;
        }
        // 3.旧单已经存在，判断是否支付成功
        if (PayStatus.TRADE_SUCCESS.equalsValue(oldOrder.getStatus())) {
            // 已经支付成功，抛出异常
            throw new BizIllegalException("订单已经支付！");
        }
        // 4.旧单已经存在，判断是否已经关闭
        if (PayStatus.TRADE_CLOSED.equalsValue(oldOrder.getStatus())) {
            // 已经关闭，抛出异常
            throw new BizIllegalException("订单已关闭");
        }
        // 5.旧单已经存在，判断支付渠道是否一致
        if (!StringUtils.equals(oldOrder.getPayChannelCode(), applyDTO.getPayChannelCode())) {
            // 支付渠道不一致，需要重置数据，然后重新申请支付单
            PayOrder payOrder = buildPayOrder(applyDTO);
            payOrder.setId(oldOrder.getId());
            payOrder.setQrCodeUrl("");
            updateById(payOrder);
            payOrder.setPayOrderNo(oldOrder.getPayOrderNo());
            return payOrder;
        }
        // 6.旧单已经存在，且可能是未支付或未提交，且支付渠道一致，直接返回旧数据
        return oldOrder;
    }

    private PayOrder buildPayOrder(PayApplyDTO payApplyDTO) {
        // 1.数据转换
        PayOrder payOrder = BeanUtils.toBean(payApplyDTO, PayOrder.class);
        // 2.初始化数据
        payOrder.setPayOverTime(LocalDateTime.now().plusMinutes(120L));
        payOrder.setStatus(PayStatus.WAIT_BUYER_PAY.getValue());
        payOrder.setBizUserId(UserContext.getUser());
        return payOrder;
    }
    public PayOrder queryByBizOrderNo(Long bizOrderNo) {
        return lambdaQuery()
                .eq(PayOrder::getBizOrderNo, bizOrderNo)
                .one();
    }
}
