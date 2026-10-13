package com.wsy.wanjoymall.item.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

// 秒杀活动，库存独立配置，与 item.stock 无关
@Data
@Accessors(chain = true)
@TableName("seckill_activity")
public class SeckillActivity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long itemId;

    // 秒杀价，单位分
    private Integer seckillPrice;

    private Integer seckillStock;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    // 0未开始 1进行中 2已结束
    private Integer status;

    private LocalDateTime createTime;
}
