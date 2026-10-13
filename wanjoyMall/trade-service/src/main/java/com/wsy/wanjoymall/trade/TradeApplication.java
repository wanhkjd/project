package com.wsy.wanjoymall.trade;

import com.wsy.wanjoymall.api.config.DefaultFeignConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@MapperScan("com.wsy.wanjoymall.trade.mapper")
@SpringBootApplication
@EnableFeignClients(basePackages = "com.wsy.wanjoymall.api.client",defaultConfiguration = DefaultFeignConfig.class)
public class TradeApplication {
    public static void main(String[] args) {
        SpringApplication.run(TradeApplication.class, args);
    }
}