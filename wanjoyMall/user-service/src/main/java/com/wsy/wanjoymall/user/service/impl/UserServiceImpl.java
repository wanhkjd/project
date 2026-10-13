package com.wsy.wanjoymall.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wsy.wanjoymall.common.exception.BadRequestException;
import com.wsy.wanjoymall.common.exception.BizIllegalException;
import com.wsy.wanjoymall.common.exception.ForbiddenException;
import com.wsy.wanjoymall.common.utils.UserContext;
import com.wsy.wanjoymall.user.config.JwtProperties;
import com.wsy.wanjoymall.user.domain.dto.LoginFormDTO;
import com.wsy.wanjoymall.user.domain.po.DeductRecord;
import com.wsy.wanjoymall.user.domain.po.User;
import com.wsy.wanjoymall.user.domain.vo.UserLoginVO;
import com.wsy.wanjoymall.user.enums.UserStatus;
import com.wsy.wanjoymall.user.mapper.DeductRecordMapper;
import com.wsy.wanjoymall.user.mapper.UserMapper;
import com.wsy.wanjoymall.user.service.IUserService;
import com.wsy.wanjoymall.user.utils.JwtTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.time.LocalDateTime;

/**
 * <p>
 * 用户表 服务实现类
 * </p>
 *
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private final PasswordEncoder passwordEncoder;

    private final JwtTool jwtTool;

    private final JwtProperties jwtProperties;

    private final DeductRecordMapper deductRecordMapper;

    @Override
    public UserLoginVO login(LoginFormDTO loginDTO) {
        // 1.数据校验
        String username = loginDTO.getUsername();
        String password = loginDTO.getPassword();
        // 2.根据用户名或手机号查询
        User user = lambdaQuery().eq(User::getUsername, username).one();
        Assert.notNull(user, "用户名错误");
        // 3.校验是否禁用
        if (user.getStatus() == UserStatus.FROZEN) {
            throw new ForbiddenException("用户被冻结");
        }
        // 4.校验密码
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BadRequestException("用户名或密码错误");
        }
        // 5.生成TOKEN
        String token = jwtTool.createToken(user.getId(), jwtProperties.getTokenTTL());
        // 6.封装VO返回
        UserLoginVO vo = new UserLoginVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setBalance(user.getBalance());
        vo.setToken(token);
        return vo;
    }

    @Override
    @Transactional
    public void deductMoney(String pw, Integer totalFee, Long payOrderId) {
        log.info("开始扣款");
        // 1.金额校验：负数或 0 会让 balance - totalFee 变成加钱
        if (totalFee == null || totalFee <= 0) {
            throw new BadRequestException("扣款金额必须大于0");
        }
        Long userId = UserContext.getUser();
        // 2.校验密码
        User user = getById(userId);
        if (user == null || !passwordEncoder.matches(pw, user.getPassword())) {
            // 密码错误
            throw new BizIllegalException("用户密码错误");
        }
        // 3.校验账户状态
        if (user.getStatus() == UserStatus.FROZEN) {
            throw new ForbiddenException("账户已冻结");
        }
        // 4.先写扣款流水，pay_order_id 唯一索引保证同一支付单只扣一次
        DeductRecord record = new DeductRecord()
                .setPayOrderId(payOrderId)
                .setUserId(userId)
                .setAmount(totalFee)
                .setCreateTime(LocalDateTime.now());
        try {
            deductRecordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            // 已经为该支付单扣过款（例如支付服务重试），直接按成功返回
            log.info("支付单{}已扣款，忽略重复请求", payOrderId);
            return;
        }
        // 5.条件扣款：余额充足且账户正常才会更新成功，影响行数为 0 说明条件不满足
        //   扣款失败抛异常，上面写的流水随事务一起回滚
        int rows = baseMapper.updateMoney(userId, totalFee);
        if (rows != 1) {
            throw new BizIllegalException("余额不足或账户状态异常！");
        }
        log.info("扣款成功，支付单：{}，金额：{}", payOrderId, totalFee);
    }

    @Override
    public boolean existsDeductRecord(Long payOrderId) {
        // 供支付服务对账：判断该支付单在用户侧是否已经扣过款
        return deductRecordMapper.selectCount(
                new LambdaQueryWrapper<DeductRecord>().eq(DeductRecord::getPayOrderId, payOrderId)) > 0;
    }
}
