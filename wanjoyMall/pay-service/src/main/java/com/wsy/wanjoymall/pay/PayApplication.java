package com.wsy.wanjoymall.pay;

import com.wsy.wanjoymall.api.config.DefaultFeignConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@MapperScan("com.wsy.wanjoymall.pay.mapper")
@SpringBootApplication
@EnableFeignClients(basePackages = "com.wsy.wanjoymall.api.client",defaultConfiguration = DefaultFeignConfig.class)
public class PayApplication {
    public static void main(String[] args) {
        SpringApplication.run(PayApplication.class, args);
    }
}