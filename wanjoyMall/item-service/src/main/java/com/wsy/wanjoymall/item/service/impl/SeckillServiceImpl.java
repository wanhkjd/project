package com.wsy.wanjoymall.item.service.impl;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wsy.wanjoymall.common.constants.RedisConstants;
import com.wsy.wanjoymall.common.domain.SeckillOrderMsgDTO;
import com.wsy.wanjoymall.common.exception.BadRequestException;
import com.wsy.wanjoymall.common.exception.BizIllegalException;
import com.wsy.wanjoymall.common.utils.UserContext;
import com.wsy.wanjoymall.item.constants.MQConstants;
import com.wsy.wanjoymall.item.domain.dto.SeckillActivityDTO;
import com.wsy.wanjoymall.item.domain.dto.SeckillActivityFormDTO;
import com.wsy.wanjoymall.item.domain.po.SeckillActivity;
import com.wsy.wanjoymall.item.mapper.SeckillActivityMapper;
import com.wsy.wanjoymall.item.service.IItemService;
import com.wsy.wanjoymall.item.service.ISeckillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillServiceImpl extends ServiceImpl<SeckillActivityMapper, SeckillActivity> implements ISeckillService {

    private final StringRedisTemplate redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final IItemService itemService;

    // 脚本只加载一次；返回 0 成功 / 1 售罄 / 2 重复下单
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("lua/seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Override
    public Long createActivity(SeckillActivityFormDTO form) {
        // 1.参数校验
        if (form.getItemId() == null || itemService.queryItemById(form.getItemId()) == null) {
            throw new BadRequestException("商品不存在");
        }
        if (form.getSeckillStock() == null || form.getSeckillStock() <= 0) {
            throw new BadRequestException("秒杀库存必须大于0");
        }
        if (form.getSeckillPrice() == null || form.getSeckillPrice() <= 0) {
            throw new BadRequestException("秒杀价必须大于0");
        }
        if (form.getStartTime() == null || form.getEndTime() == null
                || !form.getStartTime().isBefore(form.getEndTime())) {
            throw new BadRequestException("活动时间不合法");
        }
        // 2.落库
        SeckillActivity activity = new SeckillActivity()
                .setItemId(form.getItemId())
                .setSeckillPrice(form.getSeckillPrice())
                .setSeckillStock(form.getSeckillStock())
                .setStartTime(form.getStartTime())
                .setEndTime(form.getEndTime())
                .setStatus(0)
                .setCreateTime(LocalDateTime.now());
        save(activity);
        return activity.getId();
    }

    @Override
    public void preheat(Long activityId) {
        // 1.查活动
        SeckillActivity activity = getById(activityId);
        if (activity == null) {
            throw new BadRequestException("秒杀活动不存在");
        }
        // 2.TTL = 到活动结束 + 冗余 1 天，结束后还能查抢购结果
        Duration ttl = Duration.between(LocalDateTime.now(), activity.getEndTime())
                .plus(RedisConstants.SECKILL_EXTRA_TTL);
        if (ttl.isNegative() || ttl.isZero()) {
            throw new BizIllegalException("活动已结束，无法预热");
        }
        // 3.活动信息、库存写 Redis；清空历史抢购记录（重复预热等于重置活动）
        String id = activityId.toString();
        SeckillActivityDTO dto = new SeckillActivityDTO()
                .setId(activity.getId())
                .setItemId(activity.getItemId())
                .setSeckillPrice(activity.getSeckillPrice())
                .setStartTime(LocalDateTimeUtil.toEpochMilli(activity.getStartTime()))
                .setEndTime(LocalDateTimeUtil.toEpochMilli(activity.getEndTime()));
        redisTemplate.opsForValue().set(RedisConstants.SECKILL_ACTIVITY_KEY + id, JSONUtil.toJsonStr(dto), ttl);
        redisTemplate.opsForValue().set(RedisConstants.SECKILL_STOCK_KEY + id, activity.getSeckillStock().toString(), ttl);
        redisTemplate.delete(RedisConstants.SECKILL_ORDER_KEY + id);
        log.info("秒杀活动{}已预热，库存{}", activityId, activity.getSeckillStock());
    }

    @Override
    public Long seckill(Long activityId) {
        Long userId = UserContext.getUser();
        String id = activityId.toString();
        String stockKey = RedisConstants.SECKILL_STOCK_KEY + id;
        String orderKey = RedisConstants.SECKILL_ORDER_KEY + id;
        // 1.活动校验只读 Redis，不打库
        String json = redisTemplate.opsForValue().get(RedisConstants.SECKILL_ACTIVITY_KEY + id);
        if (json == null) {
            throw new BadRequestException("秒杀活动不存在或未预热");
        }
        SeckillActivityDTO activity = JSONUtil.toBean(json, SeckillActivityDTO.class);
        long now = System.currentTimeMillis();
        if (now < activity.getStartTime()) {
            throw new BizIllegalException("秒杀尚未开始");
        }
        if (now > activity.getEndTime()) {
            throw new BizIllegalException("秒杀已经结束");
        }
        // 2.预生成订单id，前端凭它轮询订单状态
        long orderId = IdUtil.getSnowflakeNextId();
        // 3.Lua 原子执行：判库存 → 判重 → 扣库存 + 记录用户
        Long result = redisTemplate.execute(SECKILL_SCRIPT,
                List.of(stockKey, orderKey), userId.toString(), String.valueOf(orderId));
        if (result == null || result == 1) {
            throw new BizIllegalException("商品已售罄");
        }
        if (result == 2) {
            throw new BizIllegalException("每人限购一件，请勿重复下单");
        }
        // 4.发消息交给 trade-service 异步落库；发送失败必须把 Redis 回滚，否则库存泄漏
        SeckillOrderMsgDTO msg = new SeckillOrderMsgDTO()
                .setOrderId(orderId)
                .setUserId(userId)
                .setActivityId(activityId)
                .setItemId(activity.getItemId())
                .setSeckillPrice(activity.getSeckillPrice());
        try {
            rabbitTemplate.convertAndSend(MQConstants.SECKILL_EXCHANGE_NAME, MQConstants.SECKILL_ORDER_KEY, msg);
        } catch (Exception e) {
            redisTemplate.opsForValue().increment(stockKey);
            redisTemplate.opsForHash().delete(orderKey, userId.toString());
            log.error("秒杀消息发送失败，已回滚 Redis 库存，activityId={}, userId={}", activityId, userId, e);
            throw new BizIllegalException("系统繁忙，请稍后再试");
        }
        return orderId;
    }

    @Override
    public Long queryResult(Long activityId) {
        Object orderId = redisTemplate.opsForHash()
                .get(RedisConstants.SECKILL_ORDER_KEY + activityId, UserContext.getUser().toString());
        return orderId == null ? null : Long.valueOf(orderId.toString());
    }

    @Override
    public void deductDbStock(Long activityId) {
        // Redis 已预减过，这里是 DB 兜底；影响行数 0 说明两边已经不一致，拒绝落库让消费方回滚重试
        int rows = baseMapper.deductStock(activityId);
        if (rows != 1) {
            throw new BizIllegalException("秒杀库存不足");
        }
    }

    @Override
    public void restoreStock(Long activityId, Long userId) {
        // 1.DB 回补
        int rows = baseMapper.restoreStock(activityId);
        if (rows != 1) {
            log.warn("秒杀活动{}不存在，跳过 DB 库存回补", activityId);
        }
        // 2.Redis 还在活动期内才回补，并放开该用户的再次抢购资格；活动早已结束（key 过期）只补 DB
        String id = activityId.toString();
        String stockKey = RedisConstants.SECKILL_STOCK_KEY + id;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(stockKey))) {
            redisTemplate.opsForValue().increment(stockKey);
            redisTemplate.opsForHash().delete(RedisConstants.SECKILL_ORDER_KEY + id, userId.toString());
        }
    }
}
