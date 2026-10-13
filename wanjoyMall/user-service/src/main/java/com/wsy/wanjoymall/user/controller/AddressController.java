package com.wsy.wanjoymall.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;


import com.wsy.wanjoymall.common.exception.BadRequestException;
import com.wsy.wanjoymall.common.utils.BeanUtils;
import com.wsy.wanjoymall.common.utils.CollUtils;
import com.wsy.wanjoymall.common.utils.UserContext;
import com.wsy.wanjoymall.user.domain.dto.AddressDTO;
import com.wsy.wanjoymall.user.domain.po.Address;
import com.wsy.wanjoymall.user.service.IAddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 */
@RestController
@RequestMapping("/addresses")
@RequiredArgsConstructor
@Tag(name = "收货地址管理接口")
public class AddressController {

    private final IAddressService addressService;

    @Operation(summary = "根据id查询地址")
    @GetMapping("{addressId}")
    public AddressDTO findAddressById(@Parameter(description = "地址id") @PathVariable("addressId") Long id) {
        // 1.根据id查询
        Address address = addressService.getById(id);
        // 2.判断当前用户
        Long userId = UserContext.getUser();
        if(!address.getUserId().equals(userId)){
            throw new BadRequestException("地址不属于当前登录用户");
        }
        return BeanUtils.copyBean(address, AddressDTO.class);
    }
    @Operation(summary = "查询当前用户地址列表")
    @GetMapping
    public List<AddressDTO> findMyAddresses() {
        // 1.查询列表
        List<Address> list = addressService.query().eq("user_id", UserContext.getUser()).list();
        // 2.判空
        if (CollUtils.isEmpty(list)) {
            return CollUtils.emptyList();
        }
        // 3.转vo
        return BeanUtils.copyList(list, AddressDTO.class);
    }
}
