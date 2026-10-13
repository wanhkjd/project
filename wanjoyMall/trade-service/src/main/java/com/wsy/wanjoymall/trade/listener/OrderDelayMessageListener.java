package com.wsy.wanjoymall.trade.listener;

import com.wsy.wanjoymall.api.client.PayClient;
import com.wsy.wanjoymall.api.dto.PayOrderDTO;
import com.wsy.wanjoymall.trade.constants.MQConstants;
import com.wsy.wanjoymall.trade.domain.po.Order;
import com.wsy.wanjoymall.trade.service.IOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderDelayMessageListener {

    private final IOrderService orderService;
    private final PayClient payClient;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = MQConstants.DELAY_ORDER_QUEUE_NAME),
            exchange = @Exchange(name = MQConstants.DELAY_EXCHANGE_NAME, delayed = "true"),
            key = MQConstants.DELAY_ORDER_KEY
    ))
    public void listenOrderDelayMessage(Long orderId){
        // 1.查询订单
        Order order = orderService.getById(orderId);
        // 2.检测订单状态，判断是否已支付
        if(order == null || order.getStatus() != 1){
            // 订单不存在或者已经支付
            return;
        }
        // 3.未支付，需要查询支付流水状态
        PayOrderDTO payOrder = payClient.queryPayOrderByBizOrderNo(orderId);
        // 4.判断是否支付
        if(payOrder != null && payOrder.getStatus() == 3){
            // 4.1.已支付，标记订单状态为已支付
            orderService.markOrderPaySuccess(orderId);
        }else{
            // 4.2.未支付，取消订单，回复库存
            try {
                orderService.cancelOrder(orderId);
            } catch (Error e) {
                // Sentinel 会把 Feign fallback 抛出的异常包装成 AssertionError，
                // 而 Spring AMQP 消费线程遇到 Error 默认会 System.exit(99) 退出整个 JVM，
                // 这里转成运行时异常，交给消费者的重试机制处理
                throw new RuntimeException("取消订单失败：" + e.getMessage(), e);
            }
        }
    }
}
