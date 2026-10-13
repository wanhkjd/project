package com.wsy.wanjoymall.item.config;

import com.wsy.wanjoymall.item.constants.MQConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 生产方也声明一遍：trade-service 没起来时消息先堆在队列里，而不是因为没有绑定被交换机丢弃
@Configuration
public class SeckillMqConfig {

    @Bean
    public DirectExchange seckillExchange() {
        return new DirectExchange(MQConstants.SECKILL_EXCHANGE_NAME, true, false);
    }

    @Bean
    public Queue seckillOrderQueue() {
        return new Queue(MQConstants.SECKILL_ORDER_QUEUE_NAME, true);
    }

    @Bean
    public Binding seckillOrderBinding(Queue seckillOrderQueue, DirectExchange seckillExchange) {
        return BindingBuilder.bind(seckillOrderQueue).to(seckillExchange).with(MQConstants.SECKILL_ORDER_KEY);
    }
}
