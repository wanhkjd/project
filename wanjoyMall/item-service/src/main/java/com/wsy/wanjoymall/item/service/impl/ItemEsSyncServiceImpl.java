package com.wsy.wanjoymall.item.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.wsy.wanjoymall.common.utils.BeanUtils;
import com.wsy.wanjoymall.item.domain.po.Item;
import com.wsy.wanjoymall.item.domain.po.ItemDoc;
import com.wsy.wanjoymall.item.service.ItemEsSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * ES 文档同步实现：wm.elasticsearch.enabled 为 true 时启用
 */
@ConditionalOnProperty(prefix = "wm.elasticsearch", name = "enabled", havingValue = "true")
@Service
@RequiredArgsConstructor
@Slf4j
public class ItemEsSyncServiceImpl implements ItemEsSyncService {

    private static final String INDEX_NAME = "items";

    private final ElasticsearchClient client;

    @Override
    public void saveOrUpdate(Item item) {
        if (item == null || item.getId() == null) {
            return;
        }
        try {
            // 1.Item 转 ItemDoc，Long 类型 id 自动转 String
            ItemDoc itemDoc = BeanUtils.copyBean(item, ItemDoc.class);
            // 2.按 id 写入文档：id 已存在则整条覆盖，不存在则新增
            client.index(i -> i.index(INDEX_NAME).id(itemDoc.getId()).document(itemDoc));
        } catch (Exception e) {
            // ES 同步失败不能影响数据库写入，记日志靠后续重建索引兜底
            log.warn("同步商品[{}]到 ES 失败", item.getId(), e);
        }
    }

    @Override
    public void deleteById(Long id) {
        if (id == null) {
            return;
        }
        try {
            // 按 id 删除文档，文档不存在时 ES 返回 not_found 不报错
            client.delete(d -> d.index(INDEX_NAME).id(id.toString()));
        } catch (Exception e) {
            log.warn("从 ES 删除商品[{}]失败", id, e);
        }
    }
}
