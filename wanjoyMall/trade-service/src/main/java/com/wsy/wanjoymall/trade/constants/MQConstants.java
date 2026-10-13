package com.wsy.wanjoymall.trade.constants;

public interface MQConstants {
    String DELAY_EXCHANGE_NAME = "trade.delay.direct";
    String DELAY_ORDER_QUEUE_NAME = "trade.delay.order.queue";
    String DELAY_ORDER_KEY = "delay.order.query";

    // 秒杀：item-service 抢购成功后投递，这里消费落库；名称必须与 item-service 侧一致
    String SECKILL_EXCHANGE_NAME = "seckill.direct";
    String SECKILL_ORDER_QUEUE_NAME = "trade.seckill.order.queue";
    String SECKILL_ORDER_KEY = "seckill.order.create";
}