package com.wsy.wanjoymall.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.env.MockPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** 仅解析本地配置和 SQL 源码，验证已有业务库名及测试隔离，不连接数据库或 Nacos。 */
class DatabaseConfigurationTest {
    @ParameterizedTest
    @CsvSource({"item-service,hm-item", "cart-service,hm-cart", "user-service,hm-user",
            "trade-service,hm-trade", "pay-service,hm-pay"})
    void defaultsUseExistingBusinessDatabases(String module, String database) throws IOException {
        for (String profile : new String[]{"dev", "local"}) {
            MockEnvironment environment = loadService(module, profile, new MockEnvironment());
            assertThat(environment.getProperty("wm.db.database")).as(module + " " + profile).isEqualTo(database);
            assertThat(environment.getProperty("spring.datasource.url")).contains("/" + database + "?");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"item-service", "cart-service", "user-service", "trade-service", "pay-service"})
    void explicitDatabaseOverrideStillSelectsAnIsolatedDatabase(String module) throws IOException {
        MockEnvironment environment = loadService(module, "dev",
                new MockEnvironment().withProperty("WM_DB_NAME", "isolated_test"));
        assertThat(environment.getProperty("wm.db.database")).isEqualTo("isolated_test");
        assertThat(environment.getProperty("spring.datasource.url")).contains("/isolated_test?");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "cart-service/src/main/java/com/wsy/wanjoymall/cart/mapper/CartMapper.java",
            "item-service/src/main/java/com/wsy/wanjoymall/item/mapper/ItemMapper.java",
            "item-service/src/main/java/com/wsy/wanjoymall/item/mapper/SeckillActivityMapper.java",
            "pay-service/src/main/java/com/wsy/wanjoymall/pay/mapper/PayOrderMapper.java",
            "user-service/src/main/java/com/wsy/wanjoymall/user/mapper/UserMapper.java"
    })
    void customMapperSqlDoesNotForceASeparateBusinessDatabase(String relativePath) throws IOException {
        String mapper = Files.readString(projectRoot().resolve(relativePath));
        for (String service : new String[]{"item", "cart", "user", "trade", "pay"}) {
            assertThat(mapper).doesNotContain("`wm-" + service + "`.", "`hm-" + service + "`.");
        }
    }

    @Test
    void sharedJdbcTemplatesRemainIdenticalAndUseTheServiceDatabase() throws IOException {
        String local = Files.readString(projectRoot().resolve("wm-common/src/main/resources/wm-shared-jdbc.yaml"));
        String nacos = Files.readString(projectRoot().resolve("deploy/nacos/wm-shared-jdbc.yaml"));
        assertThat(local).isEqualTo(nacos).contains("$" + "{wm.db.database}");
    }

    @Test
    void manualUpgradeScriptTargetsExistingDatabases() throws IOException {
        String sql = Files.readString(projectRoot().resolve("sql/wanjoymall-upgrade.sql"));
        for (String service : new String[]{"item", "user", "trade", "pay"}) {
            assertThat(sql).contains("USE `hm-" + service + "`;")
                    .doesNotContain("USE `wm-" + service + "`;");
        }
    }

    @Test
    void integrationProfileStillUsesItsDedicatedTestDatabase() throws IOException {
        MockEnvironment environment = new MockEnvironment();
        loadYaml(environment, projectRoot().resolve("item-service/src/test/resources/application-integration.yaml"));
        assertThat(environment.getProperty("wm.db.database")).isEqualTo("wm-item-integration-test");
    }

    private MockEnvironment loadService(String module, String profile, MockEnvironment environment) throws IOException {
        environment.withProperty("wm.db.host", "db.example.invalid");
        Path resources = projectRoot().resolve(module).resolve("src/main/resources");
        loadYaml(environment, resources.resolve("application.yaml"));
        loadYaml(environment, resources.resolve("application-" + profile + ".yaml"));
        loadYaml(environment, projectRoot().resolve("wm-common/src/main/resources/wm-shared-jdbc.yaml"));
        return environment;
    }

    private void loadYaml(MockEnvironment environment, Path file) throws IOException {
        for (var source : new YamlPropertySourceLoader().load(file.toString(), new FileSystemResource(file))) {
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
