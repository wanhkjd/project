package com.wsy.wanjoymall.pay.task;

import com.wsy.wanjoymall.pay.domain.po.PayOrder;
import com.wsy.wanjoymall.pay.enums.PayStatus;
import com.wsy.wanjoymall.pay.enums.PayType;
import com.wsy.wanjoymall.pay.service.IPayOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 余额支付对账任务
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayReconcileTask {

    private final IPayOrderService payOrderService;

    @Scheduled(fixedDelayString = "${wm.pay.reconcile-interval:30000}")
    public void reconcile() {
        // 处理"用户服务已扣款、支付服务在更新支付单前宕机"导致的钱扣了但支付单还是待支付
        // 1.只看余额支付、仍待支付、且更新时间已超过 1 分钟的支付单，避免和正在进行的支付请求打架
        List<PayOrder> orders = payOrderService.lambdaQuery()
                .eq(PayOrder::getStatus, PayStatus.WAIT_BUYER_PAY.getValue())
                .eq(PayOrder::getPayType, PayType.BALANCE.getValue())
                .le(PayOrder::getUpdateTime, LocalDateTime.now().minusMinutes(1))
                .last("LIMIT 100")
                .list();
        // 2.逐单对账，单笔失败不影响其他支付单
        for (PayOrder order : orders) {
            try {
                payOrderService.reconcilePayOrder(order);
            } catch (Exception e) {
                log.error("对账失败：payOrderId={}", order.getId(), e);
            }
        }
    }
}
