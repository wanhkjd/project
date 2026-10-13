package com.wsy.wanjoymall.item.service.impl;

import com.wsy.wanjoymall.item.domain.po.Item;
import com.wsy.wanjoymall.item.service.ItemEsSyncService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * ES 关闭（wm.elasticsearch.enabled=false 或未配置）时的空实现：不做任何 ES 操作
 */
@ConditionalOnProperty(prefix = "wm.elasticsearch", name = "enabled", havingValue = "false", matchIfMissing = true)
@Service
public class NoOpItemEsSyncService implements ItemEsSyncService {

    @Override
    public void saveOrUpdate(Item item) {
        // ES 未启用，无需同步
    }

    @Override
    public void deleteById(Long id) {
        // ES 未启用，无需同步
    }
}
