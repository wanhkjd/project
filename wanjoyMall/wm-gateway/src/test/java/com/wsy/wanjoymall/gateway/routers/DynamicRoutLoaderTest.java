package com.wsy.wanjoymall.gateway.routers;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.route.InMemoryRouteDefinitionRepository;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 不连接 Nacos，验证路由更新、校验与生命周期，避免迁移后配置监听失效。 */
class DynamicRoutLoaderTest {
    private final InMemoryRouteDefinitionRepository repository = new InMemoryRouteDefinitionRepository();
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final NacosConfigManager manager = mock(NacosConfigManager.class);
    private final DynamicRoutLoader loader = new DynamicRoutLoader(manager, repository, events);
    private static final String INITIAL = """
            [{"id":"wm-item","uri":"lb://item-service","predicates":[{"name":"Path","args":{"_genkey_0":"/items/**"}}]}]
            """;

    @BeforeEach
    void configure() {
        ReflectionTestUtils.setField(loader, "dataId", "wm-gateway-routes.json");
        ReflectionTestUtils.setField(loader, "group", "DEFAULT_GROUP");
    }

    @Test
    void replacesOldRoutesAndRefreshesAfterCompletion() {
        loader.updateConfigInfo(INITIAL);
        loader.updateConfigInfo("""
                [{"id":"wm-cart","uri":"lb://cart-service"}]
                """);
        assertThat(repository.getRouteDefinitions().collectList().block())
                .extracting(RouteDefinition::getId).containsExactly("wm-cart");
        verify(events, times(2)).publishEvent(any(RefreshRoutesEvent.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "null", "{}", "[null]", "[{\"uri\":\"lb://item-service\"}]", "[{\"id\":\"wm-item\"}]", "[{\"id\":\"duplicate\",\"uri\":\"lb://a\"},{\"id\":\"duplicate\",\"uri\":\"lb://b\"}]"})
    void malformedConfigDoesNotDeleteActiveRoutes(String invalid) {
        loader.updateConfigInfo(INITIAL);
        assertThatThrownBy(() -> loader.updateConfigInfo(invalid)).isInstanceOf(RuntimeException.class);
        assertThat(repository.getRouteDefinitions().collectList().block())
                .extracting(RouteDefinition::getId).containsExactly("wm-item");
        verify(events, times(1)).publishEvent(any(RefreshRoutesEvent.class));
    }

    @Test
    void explicitEmptyArrayClearsRoutes() {
        loader.updateConfigInfo(INITIAL);
        loader.updateConfigInfo("[]");
        assertThat(repository.getRouteDefinitions().collectList().block()).isEmpty();
        verify(events, times(2)).publishEvent(any(RefreshRoutesEvent.class));
    }

    @Test
    void loadsInitialConfigAndUnsubscribesOnShutdown() throws Exception {
        ConfigService config = mock(ConfigService.class);
        when(manager.getConfigService()).thenReturn(config);
        when(config.getConfigAndSignListener(eq("wm-gateway-routes.json"), eq("DEFAULT_GROUP"), eq(5000L), any(Listener.class)))
                .thenReturn(INITIAL);
        loader.initRouteConfigLoader();
        assertThat(repository.getRouteDefinitions().collectList().block()).hasSize(1);
        loader.close();
        verify(config).removeListener(eq("wm-gateway-routes.json"), eq("DEFAULT_GROUP"), any(Listener.class));
    }

    @Test
    void springInjectionDefaultsToDefaultGroup() throws Exception {
        ConfigService config = mock(ConfigService.class);
        when(manager.getConfigService()).thenReturn(config);
        when(config.getConfigAndSignListener(eq("wm-gateway-routes.json"), eq("DEFAULT_GROUP"), eq(5000L), any(Listener.class)))
                .thenReturn(INITIAL);
        new ApplicationContextRunner()
                .withPropertyValues("wm.gateway.dynamic-routes.enabled=true")
                .withBean(NacosConfigManager.class, () -> manager)
                .withBean(RouteDefinitionWriter.class, () -> repository)
                .withBean(DynamicRoutLoader.class)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(DynamicRoutLoader.class);
                    assertThat(repository.getRouteDefinitions().collectList().block()).hasSize(1);
                });
        verify(config).removeListener(eq("wm-gateway-routes.json"), eq("DEFAULT_GROUP"), any(Listener.class));
    }
}
