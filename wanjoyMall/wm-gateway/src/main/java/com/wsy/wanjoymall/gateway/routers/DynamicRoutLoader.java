package com.wsy.wanjoymall.gateway.routers;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONConfig;
import cn.hutool.json.JSONObject;
import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "wm.gateway.dynamic-routes", name = "enabled", havingValue = "true")
public class DynamicRoutLoader {
    private final NacosConfigManager nacosConfigManager;
    private final RouteDefinitionWriter writer;
    private final ApplicationEventPublisher eventPublisher;
    @Value("${wm.gateway.dynamic-routes.data-id:wm-gateway-routes.json}")
    private String dataId;
    @Value("${wm.gateway.dynamic-routes.group:DEFAULT_GROUP}")
    private String group;
    private final Set<String> routeIds = new HashSet<>();
    private Listener listener;

    @PostConstruct
    public void initRouteConfigLoader() throws NacosException {
        // 项目启动时先拉取一次配置，再添加配置监听器；dev 环境配置缺失时明确启动失败。
        listener = new Listener() {
            @Override
            public Executor getExecutor() {
                return null;
            }

            @Override
            public void receiveConfigInfo(String configInfo) {
                // 监听到配置变更，需要去更新路由表；无效配置不清空已生效的路由。
                try {
                    updateConfigInfo(configInfo);
                } catch (RuntimeException e) {
                    log.error("动态路由更新失败，请检查 Nacos 配置 {} / {}", group, dataId, e);
                }
            }
        };
        String configInfo = nacosConfigManager.getConfigService()
                .getConfigAndSignListener(dataId, group, 5000, listener);
        if (StrUtil.isBlank(configInfo)) {
            throw new IllegalStateException("Nacos 缺少动态路由配置：" + group + "/" + dataId);
        }
        // 第一次读取到配置也要更新路由表。
        updateConfigInfo(configInfo);
    }

    public synchronized void updateConfigInfo(String configInfo) {
        if (StrUtil.isBlank(configInfo)) {
            throw new IllegalArgumentException("动态路由配置不能为空；显式 [] 才表示清空路由");
        }
        // 解析配置文件，转换为 RouteDefinition；在删除旧路由前先校验新配置。
        if (!JSONUtil.isTypeJSONArray(configInfo)) {
            throw new IllegalArgumentException("动态路由必须是 JSON 数组");
        }
        // 保留 null 元素以便拒绝错误配置，不能将 [null] 静默当作清空路由。
        JSONArray array = JSONUtil.parseArray(configInfo, JSONConfig.create().setIgnoreNullValue(false));
        for (Object element : array) {
            if (!(element instanceof JSONObject)) {
                throw new IllegalArgumentException("路由数组元素必须是 JSON 对象");
            }
        }
        List<RouteDefinition> routeDefinitions = array.toList(RouteDefinition.class);
        if (routeDefinitions == null) {
            throw new IllegalArgumentException("动态路由必须是 JSON 数组，不能为 null");
        }
        Set<String> newIds = new HashSet<>();
        if (!routeDefinitions.isEmpty()) {
            for (RouteDefinition route : routeDefinitions) {
                if (route == null || StrUtil.isBlank(route.getId()) || route.getUri() == null
                        || !newIds.add(route.getId())) {
                    throw new IllegalArgumentException("路由 id、uri 必填，且 id 不可重复");
                }
            }
        }
        // 删除旧的路由表。顺序完成写入后再刷新缓存，避免独立 subscribe 导致删除/保存竞争。
        Flux.fromIterable(routeIds)
                .concatMap(id -> writer.delete(Mono.just(id)))
                // 判断是否有新的路由要更新；无新路由时只清空旧表。
                .thenMany(Flux.fromIterable(routeDefinitions))
                // 更新路由表。
                .concatMap(route -> writer.save(Mono.just(route)))
                .then().block(Duration.ofSeconds(10));
        routeIds.clear();
        // 记录路由 id。
        routeIds.addAll(newIds);
        eventPublisher.publishEvent(new RefreshRoutesEvent(this));
        log.info("动态路由已刷新，共 {} 条", routeIds.size());
    }

    @PreDestroy
    public void close() {
        if (listener != null) {
            nacosConfigManager.getConfigService().removeListener(dataId, group, listener);
        }
    }
}
