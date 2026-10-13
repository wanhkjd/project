package com.wsy.wanjoymall.pay;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.wsy.wanjoymall.pay.mq.PayMessagePublisher;
import com.wsy.wanjoymall.pay.task.PayReconcileTask;

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
        "spring.datasource.url=jdbc:h2:mem:wm_pay;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class Boot3StartupTest {
    @Autowired
    private MockMvc mvc;
    // 禁止支付重试与对账后台任务在启动测试中访问数据库或消息服务器。
    @MockitoBean
    private PayMessagePublisher payMessagePublisher;
    @MockitoBean
    private PayReconcileTask payReconcileTask;

    @Test
    void applicationStartsOnBoot3516AndServesOpenApi3() throws Exception {
        assertThat(SpringBootVersion.getVersion()).isEqualTo("3.5.16");
        mvc.perform(get("/doc.html")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value(org.hamcrest.Matchers.startsWith("3.")))
                .andExpect(jsonPath("$.info.title").value("wanjoyMall支付服务接口文档"));
    }
}
