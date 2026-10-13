package com.wsy.wanjoymall.item.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "wm.elasticsearch")
public class ElasticsearchProperties {

    /**
     * 是否启用 Elasticsearch，true 走 ES 搜索，false 或不配置则走数据库搜索
     */
    private boolean enabled = true;

    /**
     * Elasticsearch 地址
     */
    private String host = "localhost";

    /**
     * Elasticsearch 端口
     */
    private Integer port = 9200;

    /**
     * http 或 https
     */
    private String scheme = "http";

    /**
     * 用户名，可为空
     */
    private String username;

    /**
     * 密码，可为空
     */
    private String password;
}