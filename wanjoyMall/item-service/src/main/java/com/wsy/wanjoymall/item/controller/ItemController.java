package com.wsy.wanjoymall.item.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;


import cn.hutool.core.thread.ThreadUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wsy.wanjoymall.common.domain.PageDTO;
import com.wsy.wanjoymall.common.domain.PageQuery;
import com.wsy.wanjoymall.item.domain.dto.ItemDTO;
import com.wsy.wanjoymall.item.domain.dto.OrderDetailDTO;
import com.wsy.wanjoymall.item.domain.po.Item;
import com.wsy.wanjoymall.item.service.IItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "商品管理相关接口")
@RestController
@RequestMapping("/items")
@RequiredArgsConstructor
public class ItemController {

    private final IItemService itemService;

    @Operation(summary = "分页查询商品")
    @GetMapping("/page")
    public PageDTO<ItemDTO> queryItemByPage(PageQuery query) {
        // 1.分页查询
        Page<Item> result = itemService.page(query.toMpPage("update_time", false));
        // 2.封装并返回
        return PageDTO.of(result, ItemDTO.class);
    }

    @Operation(summary = "根据id批量查询商品")
    @GetMapping
    public List<ItemDTO> queryItemByIds(@RequestParam("ids") List<Long> ids){
        //模拟
        ThreadUtil.sleep(500);
        return itemService.queryItemByIds(ids);
    }

    @Operation(summary = "根据id查询商品")
    @GetMapping("{id}")
    public ItemDTO queryItemById(@PathVariable("id") Long id) {
        return itemService.queryItemById(id);
    }

    @Operation(summary = "新增商品")
    @PostMapping
    public void saveItem(@RequestBody ItemDTO item) {
        // 新增
        itemService.saveItem(item);
    }

    @Operation(summary = "更新商品状态")
    @PutMapping("/status/{id}/{status}")
    public void updateItemStatus(@PathVariable("id") Long id, @PathVariable("status") Integer status){
        itemService.updateItemStatus(id, status);
    }

    @Operation(summary = "更新商品")
    @PutMapping
    public void updateItem(@RequestBody ItemDTO item) {
        // 不允许修改商品状态，所以强制设置为null，更新时，就会忽略该字段
        item.setStatus(null);
        itemService.updateItem(item);
    }

    @Operation(summary = "根据id删除商品")
    @DeleteMapping("{id}")
    public void deleteItemById(@PathVariable("id") Long id) {
        itemService.deleteItem(id);
    }

    @Operation(summary = "批量扣减库存")
    @PutMapping("/stock/deduct")
    public void deductStock(@RequestBody List<OrderDetailDTO> items){
        itemService.deductStock(items);
    }

    @Operation(summary = "批量恢复库存（订单超时关闭时回补）")
    @PutMapping("/stock/restore")
    public void restoreStock(@RequestBody List<OrderDetailDTO> items){
        itemService.restoreStock(items);
    }
}
