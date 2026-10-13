package com.wsy.wanjoymall.trade.listener;

import com.wsy.wanjoymall.common.domain.SeckillOrderMsgDTO;
import com.wsy.wanjoymall.trade.constants.MQConstants;
import com.wsy.wanjoymall.trade.service.ISeckillOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SeckillOrderListener {

    private final ISeckillOrderService seckillOrderService;

    // 队列/交换机声明与 item-service 的 SeckillMqConfig 一致（都是 durable），两边谁先起都能声明成功
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = MQConstants.SECKILL_ORDER_QUEUE_NAME, durable = "true"),
            exchange = @Exchange(name = MQConstants.SECKILL_EXCHANGE_NAME),
            key = MQConstants.SECKILL_ORDER_KEY
    ))
    public void listenSeckillOrder(SeckillOrderMsgDTO msg) {
        // 落库失败抛异常 → 本地重试 3 次 → 耗尽后由 ErrorMessageConfig 投递到 error.queue 人工处理
        seckillOrderService.createSeckillOrder(msg);
    }
}
