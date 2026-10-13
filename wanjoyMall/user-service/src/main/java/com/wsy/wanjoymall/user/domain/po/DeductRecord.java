package com.wsy.wanjoymall.user.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 余额扣款流水
 * </p>
 */
@Data
@Accessors(chain = true)
@TableName("deduct_record")
public class DeductRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 支付单id，唯一索引，作为扣款幂等键
     */
    private Long payOrderId;

    /**
     * 扣款用户id
     */
    private Long userId;

    /**
     * 扣款金额，单位分
     */
    private Integer amount;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
