package com.wsy.wanjoymall.item.service.impl;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import org.junit.jupiter.api.Tag;

import cn.hutool.json.JSONUtil;
import com.wsy.wanjoymall.common.constants.RedisConstants;
import com.wsy.wanjoymall.common.domain.SeckillOrderMsgDTO;
import com.wsy.wanjoymall.common.exception.BizIllegalException;
import com.wsy.wanjoymall.item.constants.MQConstants;
import com.wsy.wanjoymall.item.domain.dto.SeckillActivityDTO;
import com.wsy.wanjoymall.item.domain.po.SeckillActivity;
import com.wsy.wanjoymall.item.mapper.SeckillActivityMapper;
import com.wsy.wanjoymall.item.service.IItemService;
import com.wsy.wanjoymall.item.support.RedisTestSupport;
import com.wsy.wanjoymall.common.utils.UserContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("integration")
@EnabledIfSystemProperty(named = "wm.integration.allow-writes", matches = "true")
class SeckillServiceImplTest {

    private static final long ACTIVITY_ID = 900_000_777L;
    private static final long USER_ID = 900_001L;

    // 整个测试类共用一套连接，@AfterAll 统一关闭，避免每个测试方法泄漏一套 Lettuce 资源
    private static final StringRedisTemplate redis = RedisTestSupport.newTemplate();
    private RabbitTemplate rabbitTemplate;
    private SeckillActivityMapper mapper;
    private SeckillServiceImpl service;

    private final String activityKey = RedisConstants.SECKILL_ACTIVITY_KEY + ACTIVITY_ID;
    private final String stockKey = RedisConstants.SECKILL_STOCK_KEY + ACTIVITY_ID;
    private final String orderKey = RedisConstants.SECKILL_ORDER_KEY + ACTIVITY_ID;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        mapper = mock(SeckillActivityMapper.class);
        service = new SeckillServiceImpl(redis, rabbitTemplate, mock(IItemService.class));
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        UserContext.setUser(USER_ID);
        clean();
    }

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
        clean();
    }

    @AfterAll
    static void closeRedis() {
        RedisTestSupport.close(redis);
    }

    private void clean() {
        redis.delete(List.of(activityKey, stockKey, orderKey));
    }

    // 写一个正在进行中的活动到 Redis，库存 stock
    private void preheatInRedis(int stock) {
        long now = System.currentTimeMillis();
        SeckillActivityDTO dto = new SeckillActivityDTO()
                .setId(ACTIVITY_ID).setItemId(317578L).setSeckillPrice(100)
                .setStartTime(now - 60_000).setEndTime(now + 600_000);
        redis.opsForValue().set(activityKey, JSONUtil.toJsonStr(dto));
        redis.opsForValue().set(stockKey, String.valueOf(stock));
    }

    @Test
    void preheat_shouldWriteActivityStockAndClearOrders() {
        SeckillActivity activity = new SeckillActivity()
                .setId(ACTIVITY_ID).setItemId(317578L).setSeckillPrice(100).setSeckillStock(5)
                .setStartTime(LocalDateTime.now().plusMinutes(1))
                .setEndTime(LocalDateTime.now().plusMinutes(10));
        when(mapper.selectById(ACTIVITY_ID)).thenReturn(activity);
        redis.opsForHash().put(orderKey, "1", "1");

        service.preheat(ACTIVITY_ID);

        assertEquals("5", redis.opsForValue().get(stockKey));
        SeckillActivityDTO dto = JSONUtil.toBean(redis.opsForValue().get(activityKey), SeckillActivityDTO.class);
        assertEquals(317578L, dto.getItemId());
        assertTrue(dto.getStartTime() < dto.getEndTime());
        assertEquals(0, redis.opsForHash().size(orderKey));
        assertTrue(redis.getExpire(stockKey, TimeUnit.HOURS) > 23);
    }

    @Test
    void seckill_success_shouldDecrStockRecordOrderAndSendMq() {
        preheatInRedis(1);

        Long orderId = service.seckill(ACTIVITY_ID);

        assertNotNull(orderId);
        assertEquals("0", redis.opsForValue().get(stockKey));
        assertEquals(orderId.toString(), redis.opsForHash().get(orderKey, String.valueOf(USER_ID)));
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(eq(MQConstants.SECKILL_EXCHANGE_NAME),
                eq(MQConstants.SECKILL_ORDER_KEY), captor.capture());
        SeckillOrderMsgDTO msg = (SeckillOrderMsgDTO) captor.getValue();
        assertEquals(orderId, msg.getOrderId());
        assertEquals(USER_ID, msg.getUserId());
        assertEquals(100, msg.getSeckillPrice());
        // 抢购结果可查
        assertEquals(orderId, service.queryResult(ACTIVITY_ID));
    }

    @Test
    void seckill_soldOut_shouldFail() {
        preheatInRedis(0);

        BizIllegalException e = assertThrows(BizIllegalException.class, () -> service.seckill(ACTIVITY_ID));

        assertTrue(e.getMessage().contains("售罄"));
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void seckill_duplicate_shouldFail() {
        preheatInRedis(2);
        service.seckill(ACTIVITY_ID);

        BizIllegalException e = assertThrows(BizIllegalException.class, () -> service.seckill(ACTIVITY_ID));

        assertTrue(e.getMessage().contains("重复"));
        assertEquals("1", redis.opsForValue().get(stockKey));
    }

    @Test
    void seckill_notStarted_shouldFail() {
        long now = System.currentTimeMillis();
        SeckillActivityDTO dto = new SeckillActivityDTO()
                .setId(ACTIVITY_ID).setItemId(1L).setSeckillPrice(1)
                .setStartTime(now + 60_000).setEndTime(now + 600_000);
        redis.opsForValue().set(activityKey, JSONUtil.toJsonStr(dto));
        redis.opsForValue().set(stockKey, "1");

        BizIllegalException e = assertThrows(BizIllegalException.class, () -> service.seckill(ACTIVITY_ID));

        assertTrue(e.getMessage().contains("未开始"));
        assertEquals("1", redis.opsForValue().get(stockKey));
    }

    @Test
    void seckill_mqFail_shouldRollbackRedis() {
        preheatInRedis(1);
        doThrow(new AmqpConnectException(new RuntimeException("mq down")))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        assertThrows(BizIllegalException.class, () -> service.seckill(ACTIVITY_ID));

        assertEquals("1", redis.opsForValue().get(stockKey));
        assertNull(redis.opsForHash().get(orderKey, String.valueOf(USER_ID)));
        assertNull(service.queryResult(ACTIVITY_ID));
    }

    @Test
    void seckill_concurrent_shouldNeverOversell() throws Exception {
        int stock = 100, users = 200;
        preheatInRedis(stock);
        // 线程池必须 >= users：每个任务 countDown 后阻塞在 go.await()，池子比任务少会饿死，ready 永远归不了零
        ExecutorService pool = Executors.newFixedThreadPool(users);
        CountDownLatch ready = new CountDownLatch(users);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < users; i++) {
            long userId = 800_000L + i;
            futures.add(pool.submit(() -> {
                UserContext.setUser(userId);
                ready.countDown();
                try {
                    go.await();
                    service.seckill(ACTIVITY_ID);
                    success.incrementAndGet();
                } catch (BizIllegalException ignored) {
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    UserContext.removeUser();
                }
            }));
        }
        assertTrue(ready.await(30, TimeUnit.SECONDS), "worker 线程未在 30s 内全部就绪，疑似线程池饿死");
        go.countDown();
        for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(stock, success.get());
        assertEquals("0", redis.opsForValue().get(stockKey));
        assertEquals(stock, redis.opsForHash().size(orderKey));
        verify(rabbitTemplate, times(stock)).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    void deductDbStock_zeroRows_shouldThrow() {
        when(mapper.deductStock(ACTIVITY_ID)).thenReturn(0);

        assertThrows(BizIllegalException.class, () -> service.deductDbStock(ACTIVITY_ID));
    }

    @Test
    void restoreStock_shouldIncrRedisAndRemoveUserWhenKeyExists() {
        preheatInRedis(0);
        redis.opsForHash().put(orderKey, String.valueOf(USER_ID), "123");
        when(mapper.restoreStock(ACTIVITY_ID)).thenReturn(1);

        service.restoreStock(ACTIVITY_ID, USER_ID);

        verify(mapper).restoreStock(ACTIVITY_ID);
        assertEquals("1", redis.opsForValue().get(stockKey));
        assertNull(redis.opsForHash().get(orderKey, String.valueOf(USER_ID)));
    }

    @Test
    void restoreStock_shouldOnlyTouchDbWhenRedisKeyExpired() {
        when(mapper.restoreStock(ACTIVITY_ID)).thenReturn(1);

        service.restoreStock(ACTIVITY_ID, USER_ID);

        verify(mapper).restoreStock(ACTIVITY_ID);
        assertFalse(Boolean.TRUE.equals(redis.hasKey(stockKey)));
    }
}
