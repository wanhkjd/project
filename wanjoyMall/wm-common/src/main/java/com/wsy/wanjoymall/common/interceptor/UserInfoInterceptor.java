package com.wsy.wanjoymall.common.interceptor;

import cn.hutool.core.util.StrUtil;
import com.wsy.wanjoymall.common.utils.UserContext;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class UserInfoInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //获取用户id
        String userInfo = request.getHeader("user-info");
        //判断用户id
        if (StrUtil.isNotBlank(userInfo)){
            //存入用户id
            UserContext.setUser(Long.valueOf(userInfo));
        }

        //放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {
       //清理用户id
       UserContext.removeUser();

    }
}
