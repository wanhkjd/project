package com.wsy.wanjoymall.item.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wsy.wanjoymall.common.domain.PageDTO;
import com.wsy.wanjoymall.item.domain.dto.ItemDTO;
import com.wsy.wanjoymall.item.domain.po.Item;
import com.wsy.wanjoymall.item.domain.query.ItemPageQuery;
import com.wsy.wanjoymall.item.service.IItemService;
import com.wsy.wanjoymall.item.service.ISearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 数据库搜索实现：wm.elasticsearch.enabled 为 false 或未配置时启用
 */
@ConditionalOnProperty(prefix = "wm.elasticsearch", name = "enabled", havingValue = "false", matchIfMissing = true)
@Service
@RequiredArgsConstructor
@Slf4j
public class DbSearchServiceImpl implements ISearchService {

    private final IItemService itemService;

    @Override
    public PageDTO<ItemDTO> search(ItemPageQuery query) {
        // 1.分页条件查询：关键字模糊匹配，品牌、分类精确匹配，价格区间过滤，只查上架商品
        Page<Item> result = itemService.lambdaQuery()
                .like(StrUtil.isNotBlank(query.getKey()), Item::getName, query.getKey())
                .eq(StrUtil.isNotBlank(query.getBrand()), Item::getBrand, query.getBrand())
                .eq(StrUtil.isNotBlank(query.getCategory()), Item::getCategory, query.getCategory())
                .eq(Item::getStatus, 1)
                .between(query.getMaxPrice() != null, Item::getPrice, query.getMinPrice(), query.getMaxPrice())
                .page(query.toMpPage("update_time", false));
        // 2.封装分页结果，并把 Item 转为 ItemDTO
        return PageDTO.of(result, ItemDTO.class);
    }
}
