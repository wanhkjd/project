package com.wsy.wanjoymall.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.env.MockPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** 只解析本地配置，验证服务发现、配置导入与动态路由的一致性，不连接 Nacos。 */
class NacosConfigurationTest {
    @ParameterizedTest
    @ValueSource(strings = {"item-service", "cart-service", "user-service", "trade-service", "pay-service", "wm-gateway"})
    void defaultsUseRequestedNamespaceAndGroup(String module) throws IOException {
        assertConfiguration(module, new MockEnvironment(), "wanjoyMall", "DEFAULT_GROUP");
    }

    @ParameterizedTest
    @ValueSource(strings = {"item-service", "cart-service", "user-service", "trade-service", "pay-service", "wm-gateway"})
    void overridesApplyToDiscoveryImportsAndDynamicRoutes(String module) throws IOException {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("NACOS_NAMESPACE", "test-namespace-id")
                .withProperty("NACOS_GROUP", "TEST_GROUP")
                .withProperty("NACOS_ADDR", "nacos.example.invalid:8848")
                .withProperty("SPRING_PROFILES_ACTIVE", "local");
        assertConfiguration(module, environment, "test-namespace-id", "TEST_GROUP");
    }

    private void assertConfiguration(String module, MockEnvironment environment,
                                     String namespace, String group) throws IOException {
        Path resources = projectRoot().resolve(module).resolve("src/main/resources");
        loadYaml(environment, resources.resolve("application.yaml"));
        assertThat(environment.getProperty("spring.cloud.nacos.server-addr"))
                .isEqualTo(environment.getProperty("NACOS_ADDR", "192.168.100.129:8848"));
        assertThat(environment.getProperty("spring.profiles.active"))
                .isEqualTo(environment.getProperty("SPRING_PROFILES_ACTIVE", "dev"));
        for (String type : new String[]{"config", "discovery"}) {
            String prefix = "spring.cloud.nacos." + type;
            assertThat(environment.getProperty(prefix + ".namespace")).as(module + " " + type).isEqualTo(namespace);
            assertThat(environment.getProperty(prefix + ".group")).as(module + " " + type).isEqualTo(group);
        }

        loadYaml(environment, resources.resolve("application-dev.yaml"));
        int imports = 0;
        boolean cartConfigImported = false;
        for (var source : environment.getPropertySources()) {
            if (!(source instanceof EnumerablePropertySource<?> enumerable)) {
                continue;
            }
            for (String name : enumerable.getPropertyNames()) {
                if (name.startsWith("spring.config.import[")) {
                    String value = environment.getRequiredProperty(name);
                    assertThat(value).as(module + " " + name).startsWith("nacos:")
                            .contains("?group=" + group + "&refreshEnabled=true");
                    cartConfigImported |= value.startsWith("nacos:cart-service.yaml?");
                    imports++;
                }
            }
        }
        assertThat(imports).as(module + " explicit Nacos imports").isPositive();
        if ("cart-service".equals(module)) {
            assertThat(cartConfigImported).as("cart-service.yaml must use the same group").isTrue();
        }
        if ("wm-gateway".equals(module)) {
            assertThat(environment.getProperty("wm.gateway.dynamic-routes.group")).isEqualTo(group);
            assertThat(environment.getProperty("wm.gateway.dynamic-routes.data-id")).isEqualTo("wm-gateway-routes.json");
            loadYaml(environment, resources.resolve("application-local.yaml"));
            assertThat(environment.getProperty("wm.gateway.dynamic-routes.group")).isEqualTo(group);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"wm-common/src/main/resources/wm-shared-seata.yaml", "deploy/nacos/wm-shared-seata.yaml"})
    void seataDiscoversTcThroughNacosInsteadOfConnectingToItsHttpPort(String relativePath) throws IOException {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.application.name", "cart-service");
        loadYaml(environment, projectRoot().resolve(relativePath));
        assertThat(environment.getProperty("seata.enabled")).isEqualTo("true");
        assertThat(environment.getProperty("seata.registry.type")).isEqualTo("nacos");
        assertThat(environment.getProperty("seata.registry.nacos.server-addr")).isEqualTo("192.168.100.129:8848");
        assertThat(environment.getProperty("seata.registry.nacos.namespace")).isEmpty();
        assertThat(environment.getProperty("seata.registry.nacos.group")).isEqualTo("DEFAULT_GROUP");
        assertThat(environment.getProperty("seata.registry.nacos.application")).isEqualTo("seata-server");
        assertThat(environment.getProperty("seata.application-id")).isEqualTo("cart-service");
        assertThat(environment.getProperty("seata.config.type")).isEqualTo("file");
        assertThat(environment.getProperty("seata.tx-service-group")).isEqualTo("wm-trade-group");
        assertThat(environment.getProperty("seata.service.vgroup-mapping.wm-trade-group")).isEqualTo("default");
        assertThat(environment.getProperty("seata.data-source-proxy-mode")).isEqualTo("AT");
        assertThat(environment.getProperty("seata.service.grouplist.default")).isNull();
    }

    @Test
    void localAndNacosSeataTemplatesStayIdentical() throws IOException {
        Path root = projectRoot();
        assertThat(Files.readString(root.resolve("wm-common/src/main/resources/wm-shared-seata.yaml")))
                .isEqualTo(Files.readString(root.resolve("deploy/nacos/wm-shared-seata.yaml")));
    }

    private void loadYaml(MockEnvironment environment, Path file) throws IOException {
        for (var source : new YamlPropertySourceLoader().load(file.toString(), new FileSystemResource(file))) {
            // 保留测试中显式设置的覆盖值，同时让后加载的配置文件优先于之前的文件。
            environment.getPropertySources().addAfter(MockPropertySource.MOCK_PROPERTIES_PROPERTY_SOURCE_NAME, source);
        }
    }

    private Path projectRoot() {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null && !Files.isDirectory(directory.resolve("wm-gateway/src"))) {
            directory = directory.getParent();
        }
        if (directory == null) {
            throw new IllegalStateException("未找到 wanjoyMall 多模块项目根目录");
        }
        return directory;
    }
}
