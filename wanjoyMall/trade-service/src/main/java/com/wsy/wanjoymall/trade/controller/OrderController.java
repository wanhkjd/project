package com.wsy.wanjoymall.trade.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;

import com.wsy.wanjoymall.common.utils.BeanUtils;
import com.wsy.wanjoymall.trade.domain.dto.OrderFormDTO;
import com.wsy.wanjoymall.trade.domain.vo.OrderVO;
import com.wsy.wanjoymall.trade.service.IOrderService;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.Param;
import org.springframework.web.bind.annotation.*;

@Tag(name = "订单管理接口")
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {
    private final IOrderService orderService;

    @Operation(summary = "根据id查询订单")
    @GetMapping("{id}")
    public OrderVO queryOrderById(@Param ("订单id")@PathVariable("id") Long orderId) {
        return BeanUtils.copyBean(orderService.getById(orderId), OrderVO.class);
    }

    @Operation(summary = "创建订单")
    @PostMapping
    public Long createOrder(@RequestBody OrderFormDTO orderFormDTO){
        return orderService.createOrder(orderFormDTO);
    }

    @Operation(summary = "标记订单已支付")
    @Parameter(name = "orderId", description = "订单id", in = ParameterIn.PATH)
    @PutMapping("/{orderId}")
    public void markOrderPaySuccess(@PathVariable("orderId") Long orderId) {
        orderService.markOrderPaySuccess(orderId);
    }



}
