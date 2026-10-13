package com.wsy.wanjoymall.item.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.wsy.wanjoymall.item.domain.dto.SeckillActivityFormDTO;
import com.wsy.wanjoymall.item.service.ISeckillService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

// 路径前缀不能放在 /items 下：网关对 /items/** 免登录且不透传 userId，一人一单会失效
@Tag(name = "秒杀相关接口")
@RestController
@RequestMapping("/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final ISeckillService seckillService;

    @Operation(summary = "创建秒杀活动")
    @PostMapping("/activity")
    public Long createActivity(@RequestBody SeckillActivityFormDTO form) {
        return seckillService.createActivity(form);
    }

    @Operation(summary = "预热秒杀活动到Redis")
    @PostMapping("/activity/{id}/preheat")
    public void preheat(@PathVariable("id") Long id) {
        seckillService.preheat(id);
    }

    @Operation(summary = "抢购，返回预生成的订单id")
    @PostMapping("/{activityId}")
    public Long seckill(@PathVariable("activityId") Long activityId) {
        return seckillService.seckill(activityId);
    }

    @Operation(summary = "查询本人抢购结果，未抢到返回空")
    @GetMapping("/result/{activityId}")
    public Long queryResult(@PathVariable("activityId") Long activityId) {
        return seckillService.queryResult(activityId);
    }

    @Operation(summary = "秒杀库存DB兜底扣减（trade-service 落库时调用）")
    @PutMapping("/stock/deduct/{activityId}")
    public void deductDbStock(@PathVariable("activityId") Long activityId) {
        seckillService.deductDbStock(activityId);
    }

    @Operation(summary = "秒杀库存回补（订单超时关闭时调用）")
    @PutMapping("/stock/restore/{activityId}/{userId}")
    public void restoreStock(@PathVariable("activityId") Long activityId, @PathVariable("userId") Long userId) {
        seckillService.restoreStock(activityId, userId);
    }
}
