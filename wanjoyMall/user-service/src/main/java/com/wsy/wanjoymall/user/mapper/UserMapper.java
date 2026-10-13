package com.wsy.wanjoymall.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wsy.wanjoymall.user.domain.po.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * <p>
 * 用户表 Mapper 接口
 * </p>
 *
 * @since 2023-05-05
 */
public interface UserMapper extends BaseMapper<User> {

    // 条件扣款：账户正常且余额充足才更新，由数据库原子判断，并发下不会扣成负数；#{} 预编译防注入
    @Update("UPDATE `user` SET balance = balance - #{totalFee} " +
            "WHERE id = #{userId} AND status = 1 AND balance >= #{totalFee}")
    int updateMoney(@Param("userId") Long userId, @Param("totalFee") Integer totalFee);
}
