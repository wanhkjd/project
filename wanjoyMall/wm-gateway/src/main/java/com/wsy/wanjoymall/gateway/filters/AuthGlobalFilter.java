package com.wsy.wanjoymall.gateway.filters;

import com.wsy.wanjoymall.common.exception.UnauthorizedException;
import com.wsy.wanjoymall.gateway.config.AuthProperties;
import com.wsy.wanjoymall.gateway.utils.JwtTool;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
@Component
@RequiredArgsConstructor
public class AuthGlobalFilter implements GlobalFilter, Ordered {
    private final AuthProperties authProperties;
    private final JwtTool jwtTool;
    private AntPathMatcher antPathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        //获取request
        ServerHttpRequest request = exchange.getRequest();
        //判断是否需要登录校验
        if (!isExclude(request.getPath().toString())){
            //获取Token
            List<String> headers = request.getHeaders().get("authorization");

            String token = null;
            if (headers != null && !headers.isEmpty() ){
                token = headers.get(0);
            }

            //解析并校验token
            Long userId = null;

            try {
                userId = jwtTool.parseToken(token);
            } catch (UnauthorizedException e) {
                //拦截 设置响应状态码401
                ServerHttpResponse response = exchange.getResponse();
                response.setStatusCode(HttpStatus.UNAUTHORIZED);

                return response.setComplete();
            }

            //传递用户信息
            String userInfo = userId.toString();
            ServerWebExchange swe = exchange.mutate().request(builder -> builder.header("user-info", userInfo)).build();

            //放行

            return chain.filter(swe);

        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return 0;
    }
    private boolean isExclude(String path) {
        for (String pathPattern : authProperties.getExcludePaths()) {
            if (antPathMatcher.match(pathPattern,path)) {
                return true;
            }
        }
    return false;
    }
}
