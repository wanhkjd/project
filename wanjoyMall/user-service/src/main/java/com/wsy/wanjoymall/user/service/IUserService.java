package com.wsy.wanjoymall.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wsy.wanjoymall.user.domain.dto.LoginFormDTO;
import com.wsy.wanjoymall.user.domain.po.User;
import com.wsy.wanjoymall.user.domain.vo.UserLoginVO;

/**
 * <p>
 * 用户表 服务类
 * </p>
 *
 * @since 2023-05-05
 */
public interface IUserService extends IService<User> {

    UserLoginVO login(LoginFormDTO loginFormDTO);

    void deductMoney(String pw, Integer totalFee, Long payOrderId);

    boolean existsDeductRecord(Long payOrderId);
}
