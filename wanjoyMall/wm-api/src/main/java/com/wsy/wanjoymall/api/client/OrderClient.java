package com.wsy.wanjoymall.api.client;

import com.wsy.wanjoymall.api.dto.OrderDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

// 服务名必须与 trade-service 在 Nacos 注册的名称一致，否则负载均衡找不到实例
@FeignClient("trade-service")
public interface OrderClient {

    // 支付服务申请支付单时用它核对订单归属、状态与金额
    @GetMapping("/orders/{id}")
    OrderDTO queryOrderById(@PathVariable("id") Long id);

    @PutMapping("/orders/{orderId}")
    void markOrderPaySuccess(@PathVariable("orderId") Long orderId);

}
