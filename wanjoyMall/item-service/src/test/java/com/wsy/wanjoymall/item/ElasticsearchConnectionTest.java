package com.wsy.wanjoymall.item;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import org.junit.jupiter.api.Tag;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.InfoResponse;
import com.wsy.wanjoymall.item.config.ElasticsearchConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringJUnitConfig(ElasticsearchConfig.class)
@TestPropertySource(properties = {
        "wm.elasticsearch.enabled=true",
        "wm.elasticsearch.host=${WM_TEST_ES_HOST:127.0.0.1}",
        "wm.elasticsearch.port=${WM_TEST_ES_PORT:9200}",
        "wm.elasticsearch.scheme=http"
})
@Tag("integration")
@EnabledIfSystemProperty(named = "wm.integration.allow-writes", matches = "true")
class ElasticsearchConnectionTest {

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Test
    void testConnection() throws Exception {
        InfoResponse response = elasticsearchClient.info();

        System.out.println("集群名称：" + response.clusterName());
        System.out.println("ES 版本：" + response.version().number());

        assertEquals("8.11.0", response.version().number());
    }
}