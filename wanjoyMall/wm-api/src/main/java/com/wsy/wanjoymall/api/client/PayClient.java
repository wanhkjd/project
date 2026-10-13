package com.wsy.wanjoymall.api.client;


import com.wsy.wanjoymall.api.dto.PayOrderDTO;
import com.wsy.wanjoymall.api.fallback.PayClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

@FeignClient(value = "pay-service", fallbackFactory = PayClientFallback.class)
public interface PayClient {
    /**
     * 根据交易订单id查询支付单
     * @param id 业务订单id
     * @return 支付单信息
     */
    @GetMapping("/pay-orders/biz/{id}")
    PayOrderDTO queryPayOrderByBizOrderNo(@PathVariable("id") Long id);

    /**
     * 订单超时关闭时关闭支付单，关闭后用户无法再支付
     * @param id 业务订单id
     * @return true：已关闭或不存在支付单；false：支付单已支付成功，调用方应改为标记订单已支付
     */
    @PutMapping("/pay-orders/biz/{id}/close")
    Boolean closePayOrderByBizOrderNo(@PathVariable("id") Long id);
}
