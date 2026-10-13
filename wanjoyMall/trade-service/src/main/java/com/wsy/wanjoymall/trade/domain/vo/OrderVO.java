package com.wsy.wanjoymall.trade.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "订单页面VO")
public class OrderVO {
    @Schema(description = "订单id")
    private Long id;
    @Schema(description = "总金额，单位为分")
    private Integer totalFee;
    @Schema(description = "支付类型，1、支付宝，2、微信，3、扣减余额")
    private Integer paymentType;
    @Schema(description = "用户id")
    private Long userId;
    @Schema(description = "订单的状态，1、未付款 2、已付款,未发货 3、已发货,未确认 4、确认收货，交易成功 5、交易取消，订单关闭 6、交易结束，已评价")
    private Integer status;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    @Schema(description = "支付时间")
    private LocalDateTime payTime;
    @Schema(description = "发货时间")
    private LocalDateTime consignTime;
    @Schema(description = "交易完成时间")
    private LocalDateTime endTime;
    @Schema(description = "交易关闭时间")
    private LocalDateTime closeTime;
    @Schema(description = "评价时间")
    private LocalDateTime commentTime;
}
