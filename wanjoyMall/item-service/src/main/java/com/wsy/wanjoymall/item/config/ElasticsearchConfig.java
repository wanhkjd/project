package com.wsy.wanjoymall.item.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.springframework.util.StringUtils;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "wm.elasticsearch", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ElasticsearchProperties.class)
public class ElasticsearchConfig {

    /**
     * 创建底层 RestClient
     */
    @Bean(destroyMethod = "close")
    public RestClient restClient(ElasticsearchProperties properties) {
        RestClientBuilder builder = RestClient.builder(
                new HttpHost(
                        properties.getHost(),
                        properties.getPort(),
                        properties.getScheme()
                )
        );

        // 新环境可通过配置启用基本认证；未配置用户名时仍兼容无认证的本地 ES。
        if (StringUtils.hasText(properties.getUsername())) {
            BasicCredentialsProvider credentials = new BasicCredentialsProvider();
            credentials.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(
                    properties.getUsername(), properties.getPassword()));
            builder.setHttpClientConfigCallback(client -> client.setDefaultCredentialsProvider(credentials));
        }
        return builder.build();
    }

    /**
     * 创建 Elasticsearch Transport
     */
    @Bean
    public ElasticsearchTransport elasticsearchTransport(
            RestClient restClient
    ) {
        // 1.注册 JavaTimeModule 并关闭时间戳，让 LocalDateTime 序列化为 ISO 字符串，匹配 ES 的 date 类型
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 2.用配置好的 ObjectMapper 构建 JsonpMapper，供文档对象的序列化 / 反序列化使用
        return new RestClientTransport(
                restClient,
                new JacksonJsonpMapper(objectMapper)
        );
    }

    /**
     * 创建官方 Elasticsearch Java API Client
     */
    @Bean
    public ElasticsearchClient elasticsearchClient(
            ElasticsearchTransport transport
    ) {
        return new ElasticsearchClient(transport);
    }
}