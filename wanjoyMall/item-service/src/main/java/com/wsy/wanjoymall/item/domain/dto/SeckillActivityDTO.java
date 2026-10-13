package com.wsy.wanjoymall.item.domain.dto;

import lombok.Data;
import lombok.experimental.Accessors;

// 预热后放在 Redis 里的活动信息，抢购时只读它不查库；时间用毫秒时间戳避免 JSON 日期格式歧义
@Data
@Accessors(chain = true)
public class SeckillActivityDTO {
    private Long id;
    private Long itemId;
    private Integer seckillPrice;
    private Long startTime;
    private Long endTime;
}
