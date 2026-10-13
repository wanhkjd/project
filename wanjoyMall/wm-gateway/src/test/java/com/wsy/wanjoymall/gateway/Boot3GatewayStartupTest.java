package com.wsy.wanjoymall.gateway;

import com.wsy.wanjoymall.gateway.filters.AuthGlobalFilter;
import com.wsy.wanjoymall.gateway.utils.JwtTool;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.reactive.context.ReactiveWebApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ClassUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证网关保持 WebFlux，且重命名后的密钥库仍可完成签名和验签。 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.config.import-check.enabled=false",
        "wm.gateway.dynamic-routes.enabled=false"
})
class Boot3GatewayStartupTest {
    @Autowired private ApplicationContext context;
    @Autowired private JwtTool jwtTool;

    @Test
    void gatewayStartsWithoutServletMvcDependencies() {
        assertThat(SpringBootVersion.getVersion()).isEqualTo("3.5.16");
        assertThat(context).isInstanceOf(ReactiveWebApplicationContext.class);
        assertThat(context.getBean(AuthGlobalFilter.class)).isNotNull();
        assertThat(ClassUtils.isPresent("org.springframework.web.servlet.DispatcherServlet", getClass().getClassLoader())).isFalse();
    }

    @Test
    void renamedKeyStoreCanSignAndVerifyTokens() {
        String token = jwtTool.createToken(42L, Duration.ofMinutes(1));
        assertThat(jwtTool.parseToken(token)).isEqualTo(42L);
    }
}
