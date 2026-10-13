package com.wsy.wanjoymall.common.advice;

import com.wsy.wanjoymall.common.domain.R;
import com.wsy.wanjoymall.common.exception.BadRequestException;
import com.wsy.wanjoymall.common.exception.CommonException;
import com.wsy.wanjoymall.common.exception.DbException;
import com.wsy.wanjoymall.common.utils.WebUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import jakarta.servlet.ServletException;

import org.springframework.validation.BindException;
import java.util.stream.Collectors;

// 通过 META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports 自动装配，各微服务的扫描包(com.wsy.wanjoymall.xxx)不需要覆盖本包
// 网关是 WebFlux 应用，用 ConditionalOnWebApplication(SERVLET) 避免在网关里加载
@RestControllerAdvice
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Slf4j
public class CommonExceptionAdvice {

    @ExceptionHandler(DbException.class)
    public Object handleDbException(DbException e) {
        log.error("mysql数据库操作异常 -> ", e);
        return processResponse(e);
    }

    @ExceptionHandler(CommonException.class)
    public Object handleBadRequestException(CommonException e) {
        log.error("自定义异常 -> {} , 异常原因：{}  ",e.getClass().getName(), e.getMessage());
        log.debug("", e);
        return processResponse(e);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getAllErrors()
                .stream().map(ObjectError::getDefaultMessage)
                .collect(Collectors.joining("|"));
        log.error("请求参数校验异常 -> {}", msg);
        log.debug("", e);
        return processResponse(new BadRequestException(msg));
    }
    @ExceptionHandler(BindException.class)
    public Object handleBindException(BindException e) {
        log.error("请求参数绑定异常 ->BindException， {}", e.getMessage());
        log.debug("", e);
        return processResponse(new BadRequestException("请求参数格式错误"));
    }

    @ExceptionHandler(ServletException.class)
    public Object handleServletException(ServletException e) {
        // Sentinel 调用 Feign fallback 时，fallback 抛出的业务异常会被反射包装成 AssertionError，
        // 再被 DispatcherServlet 包成 ServletException；取最底层根因，是业务异常就按原 code/msg 返回
        // 注意 ServletException 自带的 getRootCause() 只返回直接包装的异常，要用 Spring 的工具取最深层
        Throwable root = NestedExceptionUtils.getRootCause(e);
        if (root instanceof CommonException) {
            return handleBadRequestException((CommonException) root);
        }
        log.error("参数异常 -> ServletException，{}", e.getMessage());
        log.debug("", e);
        return processResponse(new BadRequestException("请求参数处理异常"));
    }

    @ExceptionHandler(Exception.class)
    public Object handleRuntimeException(Exception e) {
        log.error("其他异常 uri : {} -> ", WebUtils.getRequest().getRequestURI(), e);
        return processResponse(new CommonException("服务器内部异常", 500));
    }

    private ResponseEntity<R<Void>> processResponse(CommonException e){
        return ResponseEntity.status(e.getCode()).body(R.error(e));
    }
}
