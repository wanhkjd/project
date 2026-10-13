package com.wsy.wanjoymall.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wsy.wanjoymall.common.advice.CommonExceptionAdvice;
import com.wsy.wanjoymall.common.config.*;
import com.wsy.wanjoymall.common.domain.PageQuery;
import com.wsy.wanjoymall.common.interceptor.UserInfoInterceptor;
import com.wsy.wanjoymall.common.utils.UserContext;
import jakarta.validation.Validation;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class Boot3CompatibilityTest {
    @Test
    void autoConfigurationsAreRegisteredUsingBoot3Metadata() {
        assertThat(ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()).getCandidates())
                .contains(MyBatisConfig.class.getName(), MvcConfig.class.getName(),
                        MqConfig.class.getName(), JsonConfig.class.getName(),
                        CommonExceptionAdvice.class.getName(), OpenApiConfig.class.getName());
    }

    @Test
    void servletConfigurationAndOpenApiMetadataAreLoaded() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MvcConfig.class, CommonExceptionAdvice.class, OpenApiConfig.class))
                .withPropertyValues("wm.swagger.title=wanjoyMall 测试接口")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(MvcConfig.class)
                            .hasSingleBean(CommonExceptionAdvice.class);
                    assertThat(context.getBean(io.swagger.v3.oas.models.OpenAPI.class).getInfo().getTitle())
                            .isEqualTo("wanjoyMall 测试接口");
                });
    }

    @Test
    void servletConfigurationIsNotLoadedInNonServletContext() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MvcConfig.class, CommonExceptionAdvice.class, OpenApiConfig.class))
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(MvcConfig.class)
                        .doesNotHaveBean(CommonExceptionAdvice.class).doesNotHaveBean(OpenApiConfig.class));
    }

    @Test
    void optionalAmqpConfigurationDoesNotRequireRabbitOnClasspath() {
        new ApplicationContextRunner().withClassLoader(new FilteredClassLoader("org.springframework.amqp"))
                .withConfiguration(AutoConfigurations.of(MqConfig.class))
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(MqConfig.class));
    }

    @Test
    void longIdsKeepTheirStringJsonRepresentation() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, JsonConfig.class))
                .run(context -> assertThat(context.getBean(ObjectMapper.class)
                        .writeValueAsString(Map.of("id", 9007199254740993L)))
                        .isEqualTo("{\"id\":\"9007199254740993\"}"));
    }

    @Test
    void jakartaValidationStillRejectsInvalidPagination() {
        try (var factory = Validation.byDefaultProvider().configure()
                .messageInterpolator(new ParameterMessageInterpolator()).buildValidatorFactory()) {
            assertThat(factory.getValidator().validate(new PageQuery().setPageNo(0))).hasSize(1);
            assertThat(factory.getValidator().validate(new PageQuery())).isEmpty();
        }
    }

    @Test
    void jakartaInterceptorPropagatesAndCleansUserContext() throws Exception {
        var interceptor = new UserInfoInterceptor();
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        request.addHeader("user-info", "42");
        try {
            assertThat(interceptor.preHandle(request, response, this)).isTrue();
            assertThat(UserContext.getUser()).isEqualTo(42L);
        } finally {
            interceptor.afterCompletion(request, response, this, null);
        }
        assertThat(UserContext.getUser()).isNull();
    }
}
