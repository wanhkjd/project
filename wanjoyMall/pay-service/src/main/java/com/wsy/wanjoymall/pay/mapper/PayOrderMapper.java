package com.wsy.wanjoymall.pay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wsy.wanjoymall.pay.domain.po.PayOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * <p>
 * 支付订单 Mapper 接口
 * </p>
 *
 * @since 2023-05-16
 */
public interface PayOrderMapper extends BaseMapper<PayOrder> {

    // 按主键加行锁查询，需在事务内调用：同一支付单的并发支付请求在此排队
    @Select("SELECT * FROM `pay_order` WHERE id = #{id} FOR UPDATE")
    PayOrder selectByIdForUpdate(@Param("id") Long id);

    // 按业务订单号加行锁查询，关单时与正在进行的支付请求互斥，避免"关单和付款同时成功"
    @Select("SELECT * FROM `pay_order` WHERE biz_order_no = #{bizOrderNo} FOR UPDATE")
    PayOrder selectByBizOrderNoForUpdate(@Param("bizOrderNo") Long bizOrderNo);
}
