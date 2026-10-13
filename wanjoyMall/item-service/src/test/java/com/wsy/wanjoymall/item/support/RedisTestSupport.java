package com.wsy.wanjoymall.item.support;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

// 不启动 Spring 容器，仅连接显式配置的测试 Redis DB15 做真实读写验证
public class RedisTestSupport {

    public static StringRedisTemplate newTemplate() {
        RedisStandaloneConfiguration conf = new RedisStandaloneConfiguration(
                setting("wm.test.redis.host", "WM_TEST_REDIS_HOST", "127.0.0.1"),
                Integer.parseInt(setting("wm.test.redis.port", "WM_TEST_REDIS_PORT", "6379")));
        conf.setPassword(setting("wm.test.redis.password", "WM_TEST_REDIS_PASSWORD", ""));
        conf.setDatabase(15); // 固定隔离库，避免误写日常运行使用的库。
        LettuceConnectionFactory factory = new LettuceConnectionFactory(conf);
        factory.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        return template;
    }

    private static String setting(String property, String environment, String fallback) {
        String value = System.getenv(environment);
        return System.getProperty(property, value == null ? fallback : value);
    }

    // 没有容器帮忙销毁 bean，测试类结束时手动关掉 Lettuce 工厂，释放 Netty 线程和连接
    public static void close(StringRedisTemplate template) {
        if (template == null) {
            return;
        }
        RedisConnectionFactory factory = template.getConnectionFactory();
        if (factory instanceof LettuceConnectionFactory) {
            ((LettuceConnectionFactory) factory).destroy();
        }
    }
}
