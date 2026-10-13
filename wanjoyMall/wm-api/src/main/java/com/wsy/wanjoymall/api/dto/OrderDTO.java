package com.wsy.wanjoymall.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * <p>
 * 订单
 * </p>
 */
@Data
@Schema(description = "订单数据传输实体")
public class OrderDTO {
    @Schema(description = "订单id")
    private Long id;
    @Schema(description = "总金额，单位分")
    private Integer totalFee;
    @Schema(description = "支付类型，1、支付宝，2、微信，3、扣减余额")
    private Integer paymentType;
    @Schema(description = "下单用户id")
    private Long userId;
    @Schema(description = "订单状态，1、未付款 2、已付款,未发货 3、已发货,未确认 4、确认收货，交易成功 5、交易取消，订单关闭 6、交易结束，已评价")
    private Integer status;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    @Schema(description = "支付时间")
    private LocalDateTime payTime;
}
