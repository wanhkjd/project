package com.wsy.wanjoymall.item.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;


import com.wsy.wanjoymall.common.domain.PageDTO;
import com.wsy.wanjoymall.item.domain.dto.ItemDTO;
import com.wsy.wanjoymall.item.domain.query.ItemPageQuery;
import com.wsy.wanjoymall.item.service.ISearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "搜索相关接口")
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final ISearchService searchService;

    @Operation(summary = "搜索商品")
    @GetMapping("/list")
    public PageDTO<ItemDTO> search(ItemPageQuery query) {
        // 走 ES 还是数据库，取决于 wm.elasticsearch.enabled 注入了哪个实现
        return searchService.search(query);
    }
}
