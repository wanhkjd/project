package com.wsy.wanjoymall.item.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "创建秒杀活动表单")
public class SeckillActivityFormDTO {
    @Schema(description = "商品id")
    private Long itemId;
    @Schema(description = "秒杀价（分）")
    private Integer seckillPrice;
    @Schema(description = "秒杀库存")
    private Integer seckillStock;
    @Schema(description = "开始时间，如 2026-09-20T20:00:00")
    private LocalDateTime startTime;
    @Schema(description = "结束时间")
    private LocalDateTime endTime;
}
