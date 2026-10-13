package com.wsy.wanjoymall.item.service;

import com.wsy.wanjoymall.item.domain.po.Item;

/**
 * 商品写操作后同步 ES 文档：由 wm.elasticsearch.enabled 决定是真同步还是空操作
 */
public interface ItemEsSyncService {

    void saveOrUpdate(Item item);

    void deleteById(Long id);
}
