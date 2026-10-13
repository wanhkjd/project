package com.wsy.wanjoymall.common.domain;

import lombok.Data;
import lombok.experimental.Accessors;

// 秒杀抢购成功后由 item-service 发出、trade-service 消费落库的消息
@Data
@Accessors(chain = true)
public class SeckillOrderMsgDTO {
    // 抢购时预生成的订单id，前端凭它轮询
    private Long orderId;
    private Long userId;
    private Long activityId;
    private Long itemId;
    // 秒杀价，单位分，落库时不再查商品表算价
    private Integer seckillPrice;
}
