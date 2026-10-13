package com.wsy.wanjoymall.api.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient("user-service")
public interface UserClient {

    // 扣款接口不配降级：扣款失败必须让支付事务感知并回滚；payOrderId 是用户侧扣款流水的幂等键
    @PutMapping("users/money/deduct")
    void deductMoney(@RequestParam("pw") String pw,
                     @RequestParam("amount") Integer amount,
                     @RequestParam("payOrderId") Long payOrderId);

    // 供对账任务判断"钱是否已扣"
    @GetMapping("users/money/deduct/{payOrderId}")
    Boolean existsDeductRecord(@PathVariable("payOrderId") Long payOrderId);
}
