package com.wsy.wanjoymall.item.service.impl;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import org.junit.jupiter.api.Tag;

import cn.hutool.json.JSONUtil;
import com.wsy.wanjoymall.common.constants.RedisConstants;
import com.wsy.wanjoymall.item.domain.dto.ItemDTO;
import com.wsy.wanjoymall.item.domain.dto.OrderDetailDTO;
import com.wsy.wanjoymall.item.domain.po.Item;
import com.wsy.wanjoymall.item.mapper.ItemMapper;
import com.wsy.wanjoymall.item.support.RedisTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@Tag("integration")
@EnabledIfSystemProperty(named = "wm.integration.allow-writes", matches = "true")
class ItemServiceImplTest {

    // 用不会和真实数据冲突的大 id
    private static final long ID_A = 900_000_001L;
    private static final long ID_B = 900_000_002L;
    private static final long ID_MISSING = 900_000_003L;

    // 整个测试类共用一套连接，@AfterAll 统一关闭，避免每个测试方法泄漏一套 Lettuce 资源
    private static final StringRedisTemplate redis = RedisTestSupport.newTemplate();
    private static StringRedisTemplate deadRedisTemplate;
    private ItemMapper mapper;
    private ItemServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(ItemMapper.class);
        service = new ItemServiceImpl(redis, new NoOpItemEsSyncService());
        // ServiceImpl 的 baseMapper 是 @Autowired 字段，测试里手动注入
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        cleanKeys();
    }

    @AfterEach
    void cleanKeys() {
        for (long id : new long[]{ID_A, ID_B, ID_MISSING}) {
            redis.delete(RedisConstants.ITEM_INFO_KEY + id);
            redis.delete(RedisConstants.ITEM_LOCK_KEY + id);
        }
    }

    @AfterAll
    static void closeRedis() {
        RedisTestSupport.close(redis);
        RedisTestSupport.close(deadRedisTemplate);
    }

    // 指向一个没有 Redis 监听的端口，模拟 Redis 宕机；命令超时设短让测试快
    private static StringRedisTemplate deadRedis() {
        if (deadRedisTemplate == null) {
            RedisStandaloneConfiguration conf = new RedisStandaloneConfiguration("127.0.0.1", 1);
            LettuceClientConfiguration client = LettuceClientConfiguration.builder()
                    .commandTimeout(Duration.ofMillis(300)).build();
            LettuceConnectionFactory factory = new LettuceConnectionFactory(conf, client);
            factory.afterPropertiesSet();
            StringRedisTemplate t = new StringRedisTemplate(factory);
            t.afterPropertiesSet();
            deadRedisTemplate = t;
        }
        return deadRedisTemplate;
    }

    private Item item(long id, String name) {
        return new Item().setId(id).setName(name).setPrice(100).setStock(5).setStatus(1);
    }

    @Test
    void hit_shouldNotTouchDb() {
        ItemDTO cached = new ItemDTO();
        cached.setId(ID_A);
        cached.setName("cached");
        redis.opsForValue().set(RedisConstants.ITEM_INFO_KEY + ID_A, JSONUtil.toJsonStr(cached));

        ItemDTO result = service.queryItemById(ID_A);

        assertEquals("cached", result.getName());
        verifyNoInteractions(mapper);
    }

    @Test
    void miss_shouldLoadFromDbAndCacheWithRandomTtl() {
        when(mapper.selectById(ID_A)).thenReturn(item(ID_A, "db"));

        ItemDTO result = service.queryItemById(ID_A);

        assertEquals("db", result.getName());
        String json = redis.opsForValue().get(RedisConstants.ITEM_INFO_KEY + ID_A);
        assertEquals("db", JSONUtil.toBean(json, ItemDTO.class).getName());
        long ttl = redis.getExpire(RedisConstants.ITEM_INFO_KEY + ID_A, TimeUnit.MINUTES);
        assertTrue(ttl >= 24 && ttl <= 35, "ttl=" + ttl);
        // 锁已释放
        assertFalse(Boolean.TRUE.equals(redis.hasKey(RedisConstants.ITEM_LOCK_KEY + ID_A)));
    }

    @Test
    void missing_shouldCacheEmptyAndNotHitDbTwice() {
        when(mapper.selectById(ID_MISSING)).thenReturn(null);

        assertNull(service.queryItemById(ID_MISSING));
        assertNull(service.queryItemById(ID_MISSING));

        verify(mapper, times(1)).selectById(ID_MISSING);
        assertEquals("", redis.opsForValue().get(RedisConstants.ITEM_INFO_KEY + ID_MISSING));
        long ttl = redis.getExpire(RedisConstants.ITEM_INFO_KEY + ID_MISSING, TimeUnit.SECONDS);
        assertTrue(ttl > 0 && ttl <= 120, "ttl=" + ttl);
    }

    @Test
    void batch_shouldOnlyQueryDbForMisses() {
        ItemDTO cached = new ItemDTO();
        cached.setId(ID_A);
        cached.setName("cachedA");
        redis.opsForValue().set(RedisConstants.ITEM_INFO_KEY + ID_A, JSONUtil.toJsonStr(cached));
        when(mapper.selectBatchIds(anyCollection())).thenReturn(List.of(item(ID_B, "dbB")));

        List<ItemDTO> result = service.queryItemByIds(List.of(ID_A, ID_B));

        assertEquals(2, result.size());
        verify(mapper).selectBatchIds(argThat(c -> c.size() == 1 && c.contains(ID_B)));
        assertNotNull(redis.opsForValue().get(RedisConstants.ITEM_INFO_KEY + ID_B));
    }

    @Test
    void batch_shouldCacheEmptyForIdsMissingInDb() {
        when(mapper.selectBatchIds(anyCollection())).thenReturn(List.of());

        List<ItemDTO> result = service.queryItemByIds(List.of(ID_MISSING));

        assertTrue(result.isEmpty());
        assertEquals("", redis.opsForValue().get(RedisConstants.ITEM_INFO_KEY + ID_MISSING));
        long ttl = redis.getExpire(RedisConstants.ITEM_INFO_KEY + ID_MISSING, TimeUnit.SECONDS);
        assertTrue(ttl > 0 && ttl <= 120, "ttl=" + ttl);
    }

    @Test
    void deductStock_shouldEvictCache_whenNoTransaction() {
        redis.opsForValue().set(RedisConstants.ITEM_INFO_KEY + ID_A, "{\"id\":900000001,\"stock\":5}");
        when(mapper.updateStock(any())).thenReturn(1);

        service.deductStock(List.of(new OrderDetailDTO().setItemId(ID_A).setNum(1)));

        assertFalse(Boolean.TRUE.equals(redis.hasKey(RedisConstants.ITEM_INFO_KEY + ID_A)));
    }

    @Test
    void deductStock_shouldEvictAfterCommit_whenSynchronizationActive() {
        redis.opsForValue().set(RedisConstants.ITEM_INFO_KEY + ID_A, "{\"id\":900000001,\"stock\":5}");
        when(mapper.updateStock(any())).thenReturn(1);
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.deductStock(List.of(new OrderDetailDTO().setItemId(ID_A).setNum(1)));
            // 1.提交前缓存还在
            assertTrue(Boolean.TRUE.equals(redis.hasKey(RedisConstants.ITEM_INFO_KEY + ID_A)));
            // 2.模拟提交：触发 afterCommit
            for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
                s.afterCommit();
            }
            assertFalse(Boolean.TRUE.equals(redis.hasKey(RedisConstants.ITEM_INFO_KEY + ID_A)));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void evict_shouldDeleteKey() {
        redis.opsForValue().set(RedisConstants.ITEM_INFO_KEY + ID_A, "{}");

        service.evictItemCache(ID_A);

        assertFalse(Boolean.TRUE.equals(redis.hasKey(RedisConstants.ITEM_INFO_KEY + ID_A)));
    }

    @Test
    void saveItem_shouldEvictPlaceholder() {
        redis.opsForValue().set(RedisConstants.ITEM_INFO_KEY + ID_A, "", RedisConstants.ITEM_NULL_TTL);
        // ServiceImpl.save 走 baseMapper.insert；模拟入库并回填 id
        when(mapper.insert(any(Item.class))).thenAnswer(inv -> {
            inv.getArgument(0, Item.class).setId(ID_A);
            return 1;
        });
        ItemDTO dto = new ItemDTO();
        dto.setName("new");

        service.saveItem(dto);

        assertFalse(Boolean.TRUE.equals(redis.hasKey(RedisConstants.ITEM_INFO_KEY + ID_A)));
    }

    @Test
    void redisDown_queryItemById_shouldFallBackToDb() {
        ItemServiceImpl svc = new ItemServiceImpl(deadRedis(), new NoOpItemEsSyncService());
        ReflectionTestUtils.setField(svc, "baseMapper", mapper);
        when(mapper.selectById(ID_A)).thenReturn(item(ID_A, "db"));

        ItemDTO result = svc.queryItemById(ID_A);

        assertEquals("db", result.getName());
    }

    @Test
    void redisDown_queryItemByIds_shouldFallBackToDb() {
        ItemServiceImpl svc = new ItemServiceImpl(deadRedis(), new NoOpItemEsSyncService());
        ReflectionTestUtils.setField(svc, "baseMapper", mapper);
        when(mapper.selectBatchIds(anyCollection())).thenReturn(List.of(item(ID_A, "dbA")));

        List<ItemDTO> result = svc.queryItemByIds(List.of(ID_A));

        assertEquals(1, result.size());
        assertEquals("dbA", result.get(0).getName());
    }

    @Test
    void redisDown_evict_shouldNotThrow() {
        ItemServiceImpl svc = new ItemServiceImpl(deadRedis(), new NoOpItemEsSyncService());

        assertDoesNotThrow(() -> svc.evictItemCache(ID_A));
    }
}
