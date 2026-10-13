package com.wsy.wanjoymall.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wsy.wanjoymall.user.domain.po.Address;
import com.wsy.wanjoymall.user.mapper.AddressMapper;
import com.wsy.wanjoymall.user.service.IAddressService;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @since 2023-05-05
 */
@Service
public class AddressServiceImpl extends ServiceImpl<AddressMapper, Address> implements IAddressService {

}
