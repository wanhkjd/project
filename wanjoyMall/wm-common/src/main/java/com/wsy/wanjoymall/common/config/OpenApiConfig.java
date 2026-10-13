package com.wsy.wanjoymall.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * 为 Servlet 微服务提供 OpenAPI 3 文档元数据；不向 WebFlux 网关引入 MVC 配置。
 * 保留各服务 wm.swagger.title 配置，接口说明由原有注解迁移而来。
 */
@AutoConfiguration
@ConditionalOnClass(OpenAPI.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class OpenApiConfig {
    @Bean
    @ConditionalOnMissingBean(OpenAPI.class)
    public OpenAPI serviceOpenApi(Environment environment) {
        String title = environment.getProperty("wm.swagger.title", "wanjoyMall 接口文档");
        return new OpenAPI().info(new Info().title(title).version("1.0.0"));
    }
}
