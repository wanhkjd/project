package com.wsy.wanjoymall.item;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import org.junit.jupiter.api.Tag;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexResponse;
import com.wsy.wanjoymall.item.config.ElasticsearchConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringJUnitConfig(ElasticsearchConfig.class)
@TestPropertySource(properties = {
        "wm.elasticsearch.enabled=true",
        "wm.elasticsearch.host=${WM_TEST_ES_HOST:127.0.0.1}",
        "wm.elasticsearch.port=${WM_TEST_ES_PORT:9200}",
        "wm.elasticsearch.scheme=http"
})
@Tag("integration")
@EnabledIfSystemProperty(named = "wm.integration.allow-writes", matches = "true")
class ElasticsearchIndexTest {

    private static final String INDEX_NAME = "wm-items-integration-test";

    private static final String MAPPING_JSON =
            "{" +
            "  \"mappings\": {" +
            "    \"properties\": {" +
            "      \"id\": { \"type\": \"keyword\" }," +
            "      \"name\": { \"type\": \"text\", \"analyzer\": \"ik_max_word\" }," +
            "      \"price\": { \"type\": \"integer\" }," +
            "      \"stock\": { \"type\": \"integer\" }," +
            "      \"image\": { \"type\": \"keyword\", \"index\": false }," +
            "      \"category\": { \"type\": \"keyword\" }," +
            "      \"brand\": { \"type\": \"keyword\" }," +
            "      \"sold\": { \"type\": \"integer\" }," +
            "      \"commentCount\": { \"type\": \"integer\", \"index\": false }," +
            "      \"isAD\": { \"type\": \"boolean\" }," +
            "      \"updateTime\": { \"type\": \"date\" }" +
            "    }" +
            "  }" +
            "}";

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Test
    void testCreateIndex() throws Exception {
        // 1.准备请求对象，指定索引库名，并用 DSL 的 JSON 填充 Mapping
        CreateIndexRequest request = new CreateIndexRequest.Builder()
                .index(INDEX_NAME)
                .withJson(new StringReader(MAPPING_JSON))
                .build();

        // 2.发送请求，创建索引库
        CreateIndexResponse response = elasticsearchClient.indices().create(request);

        // 3.打印结果并校验是否创建成功
        System.out.println("创建索引库结果：" + response.acknowledged());
        assertTrue(response.acknowledged());
    }

    @Test
    void testExistsIndex() throws Exception {
        // 1.发送 exists 请求，判断 items 索引库是否存在
        boolean exists = elasticsearchClient.indices()
                .exists(e -> e.index(INDEX_NAME))
                .value();

        // 2.打印结果
        System.out.println(INDEX_NAME + " 是否存在：" + exists);
    }

    @Test
    void testDeleteIndex() throws Exception {
        // 1.准备请求对象，指定要删除的索引库名
        DeleteIndexRequest request = new DeleteIndexRequest.Builder()
                .index(INDEX_NAME)
                .build();

        // 2.发送请求，删除索引库
        DeleteIndexResponse response = elasticsearchClient.indices().delete(request);

        // 3.打印结果并校验是否删除成功
        System.out.println("删除索引库结果：" + response.acknowledged());
        assertTrue(response.acknowledged());
    }
}
