package com.wsy.wanjoymall.api.fallback;
import com.wsy.wanjoymall.api.client.PayClient;
import com.wsy.wanjoymall.api.dto.PayOrderDTO;
import com.wsy.wanjoymall.common.exception.CommonException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;

@Slf4j
public class PayClientFallback implements FallbackFactory<PayClient> {
    @Override
    public PayClient create(Throwable cause) {
        return new PayClient() {
            @Override
            public PayOrderDTO queryPayOrderByBizOrderNo(Long id) {
                // 查询失败：降级为 null，调用方按"未查到支付单"处理
                log.error("查询支付单失败", cause);
                return null;
            }

            @Override
            public Boolean closePayOrderByBizOrderNo(Long id) {
                // 写入失败：不能默认"已关闭"，否则关单后用户仍能支付；抛出让关单流程回滚并由 MQ 重试
                log.error("关闭支付单失败", cause);
                if (cause instanceof CommonException) {
                    throw (CommonException) cause;
                }
                throw new RuntimeException("关闭支付单失败：" + cause.getMessage(), cause);
            }
        };
    }
}
