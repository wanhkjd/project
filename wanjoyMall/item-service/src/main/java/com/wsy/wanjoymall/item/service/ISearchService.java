package com.wsy.wanjoymall.item.service;

import com.wsy.wanjoymall.common.domain.PageDTO;
import com.wsy.wanjoymall.item.domain.dto.ItemDTO;
import com.wsy.wanjoymall.item.domain.query.ItemPageQuery;

/**
 * 商品搜索服务：由 wm.elasticsearch.enabled 决定注入 ES 实现还是数据库实现
 */
public interface ISearchService {

    PageDTO<ItemDTO> search(ItemPageQuery query);
}
