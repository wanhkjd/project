package com.wsy.wanjoymall.item.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wsy.wanjoymall.item.domain.dto.ItemDTO;
import com.wsy.wanjoymall.item.domain.dto.OrderDetailDTO;
import com.wsy.wanjoymall.item.domain.po.Item;


import java.util.Collection;
import java.util.List;

/**
 * <p>
 * 商品表 服务类
 * </p>
 *
 * @since 2023-05-05
 */
public interface IItemService extends IService<Item> {

    void deductStock(List<OrderDetailDTO> items);

    void restoreStock(List<OrderDetailDTO> items);

    List<ItemDTO> queryItemByIds(Collection<Long> ids);

    // 走缓存的单个查询，不存在返回 null
    ItemDTO queryItemById(Long id);

    // 新增后删缓存：清掉创建前可能留下的空值占位
    void saveItem(ItemDTO item);

    void updateItem(ItemDTO item);

    void updateItemStatus(Long id, Integer status);

    void deleteItem(Long id);

    // 删除商品缓存，所有写路径调用
    void evictItemCache(Long id);
}
