package com.wsy.wanjoymall.pay.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 支付通知本地消息表
 * </p>
 */
@Data
@Accessors(chain = true)
@TableName("pay_message")
public class PayMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    // 待发送：尚未收到 Broker 确认，重试任务会扫描
    public static final int STATUS_PENDING = 0;
    // 已发送：收到 Broker ack
    public static final int STATUS_SENT = 1;
    // 失败：超过最大重试次数，需要人工介入
    public static final int STATUS_FAILED = 2;

    /**
     * id
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 业务订单号
     */
    private Long bizOrderNo;

    /**
     * 目标交换机
     */
    private String exchange;

    /**
     * 路由键
     */
    private String routingKey;

    /**
     * 消息体（当前为订单 id）
     */
    private String payload;

    /**
     * 状态，0：待发送，1：已确认，2：超过重试上限
     */
    private Integer status;

    /**
     * 已重试次数，同时作为并发更新时的版本号
     */
    private Integer retryCount;

    /**
     * 下次允许重试的时间
     */
    private LocalDateTime nextRetryTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
