package com.wsy.wanjoymall.item;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import org.junit.jupiter.api.Tag;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

// 走完整容器：ConfigData 导入 wm-shared-redis.yaml → 自动装配 StringRedisTemplate
// 关闭注册与 Seata：测试实例不该注册到 Nacos，也不需要参与全局事务
@SpringBootTest(properties = {
        "spring.profiles.active=integration",
        "spring.cloud.nacos.discovery.register-enabled=false",
        "seata.enabled=false"
})
@Tag("integration")
@EnabledIfSystemProperty(named = "wm.integration.allow-writes", matches = "true")
class RedisConfigTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void shouldConnectToTestDatabaseAndReadWrite() {
        // 1.确认库号是测试专用的 15
        LettuceConnectionFactory factory = (LettuceConnectionFactory) redisTemplate.getConnectionFactory();
        assertEquals(15, factory.getDatabase());
        // 2.真实读写一次
        String key = "wm:test:p0:ping";
        redisTemplate.opsForValue().set(key, "pong");
        assertEquals("pong", redisTemplate.opsForValue().get(key));
        redisTemplate.delete(key);
    }
}
