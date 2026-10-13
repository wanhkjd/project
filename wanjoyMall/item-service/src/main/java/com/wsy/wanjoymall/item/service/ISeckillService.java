package com.wsy.wanjoymall.item.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wsy.wanjoymall.item.domain.dto.SeckillActivityFormDTO;
import com.wsy.wanjoymall.item.domain.po.SeckillActivity;

public interface ISeckillService extends IService<SeckillActivity> {

    Long createActivity(SeckillActivityFormDTO form);

    // 活动信息 + 库存写入 Redis，清空历史抢购记录
    void preheat(Long activityId);

    // 抢购，成功返回预生成的订单id
    Long seckill(Long activityId);

    // 当前用户在该活动的抢购结果，未抢到返回 null
    Long queryResult(Long activityId);

    // trade-service 落库时调用：DB 条件扣减兜底
    void deductDbStock(Long activityId);

    // 秒杀订单超时关闭时调用：DB + Redis 回补
    void restoreStock(Long activityId, Long userId);
}
