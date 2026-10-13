package com.wsy.wanjoymall.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.Parameter;

import com.wsy.wanjoymall.user.domain.dto.LoginFormDTO;
import com.wsy.wanjoymall.user.domain.vo.UserLoginVO;
import com.wsy.wanjoymall.user.service.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "用户相关接口")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final IUserService userService;

    @Operation(summary = "用户登录接口")
    @PostMapping("login")
    public UserLoginVO login(@RequestBody @Validated LoginFormDTO loginFormDTO){
        return userService.login(loginFormDTO);
    }

    @Operation(summary = "扣减余额")
    @Parameters({
            @Parameter(name = "pw", description = "支付密码"),
            @Parameter(name = "amount", description = "支付金额"),
            @Parameter(name = "payOrderId", description = "支付单id，用于扣款幂等")
    })
    @PutMapping("/money/deduct")
    public void deductMoney(@RequestParam("pw") String pw,
                            @RequestParam("amount") Integer amount,
                            @RequestParam("payOrderId") Long payOrderId){
        userService.deductMoney(pw, amount, payOrderId);
    }

    @Operation(summary = "查询支付单是否已扣款")
    @GetMapping("/money/deduct/{payOrderId}")
    public Boolean existsDeductRecord(@PathVariable("payOrderId") Long payOrderId){
        return userService.existsDeductRecord(payOrderId);
    }
}

