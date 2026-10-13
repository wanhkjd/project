package com.wsy.wanjoymall.api.client;

import com.wsy.wanjoymall.api.dto.ItemDTO;
import com.wsy.wanjoymall.api.dto.OrderDetailDTO;
import com.wsy.wanjoymall.api.fallback.ItemClientFallbackFacotry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collection;
import java.util.List;

@FeignClient(value = "item-service",fallbackFactory = ItemClientFallbackFacotry.class)
public interface ItemClient {

    @GetMapping("/items")
    List<ItemDTO> queryItemByIds(@RequestParam("ids") Collection<Long> ids);

    @PutMapping("/items/stock/deduct")
    void deductStock(@RequestBody List<OrderDetailDTO> items);

    // 订单超时关闭时回补库存
    @PutMapping("/items/stock/restore")
    void restoreStock(@RequestBody List<OrderDetailDTO> items);

    // 秒杀订单落库时的 DB 库存兜底扣减
    @PutMapping("/seckill/stock/deduct/{activityId}")
    void deductSeckillStock(@PathVariable("activityId") Long activityId);

    // 秒杀订单超时关闭时回补 DB + Redis 库存
    @PutMapping("/seckill/stock/restore/{activityId}/{userId}")
    void restoreSeckillStock(@PathVariable("activityId") Long activityId, @PathVariable("userId") Long userId);
}
