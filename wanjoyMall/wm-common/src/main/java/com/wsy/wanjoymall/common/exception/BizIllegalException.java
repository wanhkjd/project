package com.wsy.wanjoymall.common.exception;

public class BizIllegalException extends CommonException{

    // 业务规则不满足（库存不足、重复支付、余额不足等）属于客户端请求在当前状态下不可执行，统一映射为 400
    private static final int CODE = 400;

    public BizIllegalException(String message) {
        super(message, CODE);
    }

    public BizIllegalException(String message, Throwable cause) {
        super(message, cause, CODE);
    }

    public BizIllegalException(Throwable cause) {
        super(cause, CODE);
    }
}
