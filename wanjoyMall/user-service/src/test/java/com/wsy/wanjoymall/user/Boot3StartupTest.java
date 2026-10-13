package com.wsy.wanjoymall.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 仅用内存数据库验证真实应用装配和 OpenAPI，不连接已有中间件。 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.config.import-check.enabled=false",
        "spring.cloud.sentinel.enabled=false",
        "seata.enabled=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.listener.direct.auto-startup=false",
        "spring.rabbitmq.dynamic=false",
        "wm.elasticsearch.enabled=false",
        "wm.gateway.dynamic-routes.enabled=false",
        "spring.cloud.service-registry.auto-registration.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:wm_user;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class Boot3StartupTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void applicationStartsOnBoot3516AndServesOpenApi3() throws Exception {
        assertThat(SpringBootVersion.getVersion()).isEqualTo("3.5.16");
        mvc.perform(get("/doc.html")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value(org.hamcrest.Matchers.startsWith("3.")))
                .andExpect(jsonPath("$.info.title").value("wanjoyMall用户管理服务接口文档"));
    }
}
