package com.wsy.wanjoymall.common.constants;

import java.time.Duration;

// 全局 Redis key 前缀与 TTL，业务代码禁止散落魔法字符串
public class RedisConstants {

    // ===== P1 商品缓存 =====
    public static final String ITEM_INFO_KEY = "item:info:";
    // 基础 30 分钟，±5 分钟随机抖动防雪崩
    public static final int ITEM_INFO_TTL_BASE_MINUTES = 30;
    public static final int ITEM_INFO_TTL_RANDOM_MINUTES = 5;
    // 空值占位（防穿透）：同一个 key 写空串
    public static final Duration ITEM_NULL_TTL = Duration.ofMinutes(2);
    // 缓存重建互斥锁（防击穿）
    public static final String ITEM_LOCK_KEY = "item:lock:";
    public static final Duration ITEM_LOCK_TTL = Duration.ofSeconds(10);

    // ===== P2 登录会话 =====
    public static final String LOGIN_TOKEN_KEY = "login:token:";
    public static final String LOGIN_USER_KEY = "login:user:";
    public static final Duration LOGIN_TTL = Duration.ofMinutes(30);

    // ===== P3 购物车 =====
    public static final String CART_KEY = "cart:";
    public static final Duration CART_TTL = Duration.ofDays(30);

    // ===== P4 防重复提交 =====
    public static final String SUBMIT_KEY = "submit:";
    public static final Duration SUBMIT_TTL = Duration.ofSeconds(5);

    // ===== P5 秒杀（按 activityId 组 key） =====
    public static final String SECKILL_ACTIVITY_KEY = "seckill:activity:";
    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    // Hash：field=userId, value=orderId
    public static final String SECKILL_ORDER_KEY = "seckill:order:";
    // 活动结束后再保留 1 天，便于查询抢购结果
    public static final Duration SECKILL_EXTRA_TTL = Duration.ofDays(1);
}
