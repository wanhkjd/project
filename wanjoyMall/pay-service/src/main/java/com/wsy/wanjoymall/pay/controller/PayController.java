package com.wsy.wanjoymall.pay.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import com.wsy.wanjoymall.api.dto.PayOrderDTO;
import com.wsy.wanjoymall.common.exception.BizIllegalException;
import com.wsy.wanjoymall.common.exception.ForbiddenException;
import com.wsy.wanjoymall.common.utils.BeanUtils;
import com.wsy.wanjoymall.common.utils.UserContext;
import com.wsy.wanjoymall.pay.domain.dto.PayApplyDTO;
import com.wsy.wanjoymall.pay.domain.dto.PayOrderFormDTO;
import com.wsy.wanjoymall.pay.domain.po.PayOrder;
import com.wsy.wanjoymall.pay.enums.PayType;
import com.wsy.wanjoymall.pay.service.IPayOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "支付相关接口")
@RestController
@RequestMapping("pay-orders")
@RequiredArgsConstructor
public class PayController {

    private final IPayOrderService payOrderService;

    @Operation(summary = "生成支付单")
    @PostMapping
    public String applyPayOrder(@RequestBody PayApplyDTO applyDTO){
        if(!PayType.BALANCE.equalsValue(applyDTO.getPayType())){
            // 目前只支持余额支付
            throw new BizIllegalException("抱歉，目前只支持余额支付");
        }
        return payOrderService.applyPayOrder(applyDTO);
    }

    @Operation(summary = "尝试基于用户余额支付")
    @Parameter(description = "支付单id", name = "id")
    @PostMapping("{id}")
    public void tryPayOrderByBalance(@PathVariable("id") Long id, @RequestBody PayOrderFormDTO payOrderFormDTO){
        payOrderFormDTO.setId(id);
        payOrderService.tryPayOrderByBalance(payOrderFormDTO);
    }
    @Operation(summary = "根据id查询支付单")
    @GetMapping("/biz/{id}")
    public PayOrderDTO queryPayOrderByBizOrderNo(@PathVariable("id") Long id){
        PayOrder payOrder = payOrderService.lambdaQuery().eq(PayOrder::getBizOrderNo, id).one();
        return BeanUtils.copyBean(payOrder, PayOrderDTO.class);
    }

    @Operation(summary = "订单超时关闭时关闭支付单")
    @Parameter(description = "业务订单id", name = "id")
    @PutMapping("/biz/{id}/close")
    public Boolean closePayOrderByBizOrderNo(@PathVariable("id") Long id){
        // 交易服务通过 MQ 线程调用时没有用户上下文；用户经网关调用时只允许关闭自己的支付单
        Long userId = UserContext.getUser();
        if (userId != null) {
            PayOrder payOrder = payOrderService.lambdaQuery().eq(PayOrder::getBizOrderNo, id).one();
            if (payOrder != null && !userId.equals(payOrder.getBizUserId())) {
                throw new ForbiddenException("无权操作该支付单");
            }
        }
        return payOrderService.closePayOrderByBizOrderNo(id);
    }
}
