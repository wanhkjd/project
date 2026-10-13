package com.wsy.wanjoymall.item.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wsy.wanjoymall.item.domain.po.SeckillActivity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface SeckillActivityMapper extends BaseMapper<SeckillActivity> {

    // DB 兜底扣减：Redis 已预减，这里是第二道防超卖；影响行数 0 说明库存已被扣空
    @Update("UPDATE `seckill_activity` SET seckill_stock = seckill_stock - 1 WHERE id = #{activityId} AND seckill_stock >= 1")
    int deductStock(@Param("activityId") Long activityId);

    // 超时关单回补
    @Update("UPDATE `seckill_activity` SET seckill_stock = seckill_stock + 1 WHERE id = #{activityId}")
    int restoreStock(@Param("activityId") Long activityId);
}
