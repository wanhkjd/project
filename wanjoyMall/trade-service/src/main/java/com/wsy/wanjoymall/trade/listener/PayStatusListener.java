package com.wsy.wanjoymall.trade.listener;

import com.wsy.wanjoymall.trade.service.IOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PayStatusListener {
    private final IOrderService orderService;
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "trade.pay.success.queue", durable = "true"),
            exchange = @Exchange(name = "pay.direct"),
            key = {"pay.success"}
    ))
    public void listenPayStatus(Long orderId) {
        // 支付服务采用本地消息表 + 重试，通知可能重复投递
        // 这里不做"先查再改"，直接交给状态条件更新保证幂等，避免查询与更新之间的并发窗口
        orderService.markOrderPaySuccess(orderId);
    }
}
