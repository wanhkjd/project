package com.wsy.wanjoymall.api.config;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.wsy.wanjoymall.api.fallback.ItemClientFallbackFacotry;
import com.wsy.wanjoymall.api.fallback.PayClientFallback;
import com.wsy.wanjoymall.common.exception.CommonException;
import com.wsy.wanjoymall.common.utils.UserContext;
import feign.Logger;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import feign.Response;
import feign.Util;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

import java.io.IOException;

public class DefaultFeignConfig {
    @Bean
    public Logger.Level feignLoggerLevel(){
        return Logger.Level.FULL;
    }
    @Bean
    public RequestInterceptor userInfoRequestInterceptor(){
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
               // 用户身份透传：把 ThreadLocal 中的用户 id 放进 user-info 头，下游拦截器据此恢复 UserContext
               Long userId = UserContext.getUser();
               if (userId != null){
                   template.header("user-info",userId.toString());
               }
            }
        };
    }

    @Bean
    public ErrorDecoder errorDecoder(){
        // 下游服务由 CommonExceptionAdvice 返回 R{code,msg}，这里还原为同码同信息的业务异常
        // 例如 user-service 抛"用户密码错误"，pay-service 收到的不再是笼统的 FeignException
        ErrorDecoder defaultDecoder = new ErrorDecoder.Default();
        return (methodKey, response) -> {
            // 1.响应体是一次性的输入流，先读成字节数组
            byte[] body = readBody(response);
            if (body != null) {
                String text = new String(body, Util.UTF_8);
                // 2.能解析出 msg 的按业务异常还原
                if (JSONUtil.isTypeJSONObject(text)) {
                    JSONObject json = JSONUtil.parseObj(text);
                    String msg = json.getStr("msg");
                    if (StrUtil.isNotBlank(msg)) {
                        return new CommonException(msg, json.getInt("code", response.status()));
                    }
                }
                // 3.不是约定格式（如 404 页面），把已读取的 body 放回去交给默认解码器
                response = response.toBuilder().body(body).build();
            }
            return defaultDecoder.decode(methodKey, response);
        };
    }

    private static byte[] readBody(Response response) {
        if (response.body() == null) {
            return null;
        }
        try {
            return Util.toByteArray(response.body().asInputStream());
        } catch (IOException e) {
            return null;
        }
    }

    @Bean
    public ItemClientFallbackFacotry itemClientFallbackFacotry(){
        return  new ItemClientFallbackFacotry();
    }
    @Bean
    public PayClientFallback payClientFallback(){
        return  new PayClientFallback();
    }
}
