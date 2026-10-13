package com.wsy.wanjoymall.api.fallback;

import com.wsy.wanjoymall.api.client.ItemClient;
import com.wsy.wanjoymall.api.dto.ItemDTO;
import com.wsy.wanjoymall.api.dto.OrderDetailDTO;
import com.wsy.wanjoymall.common.exception.CommonException;
import com.wsy.wanjoymall.common.utils.CollUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;

import java.util.Collection;
import java.util.List;
@Slf4j
public class ItemClientFallbackFacotry implements FallbackFactory<ItemClient> {

    @Override
    public ItemClient create(Throwable cause) {
        // 需开启 feign.sentinel.enabled 才会生效；相比 fallback，FallbackFactory 能拿到原始异常 cause
        return new ItemClient() {
            @Override
            public List<ItemDTO> queryItemByIds(Collection<Long> ids) {
                // 查询失败：降级为空列表，调用方（购物车、下单）按"无商品"处理，不让整个页面报错
                log.error("查询商品失败",cause);
                return CollUtils.emptyList();
            }

            @Override
            public void deductStock(List<OrderDetailDTO> items) {
                // 写入失败：不能吞掉，必须抛出让上游事务回滚，否则会出现"订单创建成功但库存没扣"
                log.error("扣减商品库存失败",cause);
                throw writeFailure("扣减库存失败", cause);
            }

            @Override
            public void restoreStock(List<OrderDetailDTO> items) {
                // 写入失败：抛出让关单事务回滚，由 MQ 重试，否则会出现"订单已关闭但库存没回补"
                log.error("恢复商品库存失败",cause);
                throw writeFailure("恢复库存失败", cause);
            }

            @Override
            public void deductSeckillStock(Long activityId) {
                // 写入失败：抛出让秒杀订单本地事务回滚，MQ 重试
                log.error("扣减秒杀库存失败", cause);
                throw writeFailure("扣减秒杀库存失败", cause);
            }

            @Override
            public void restoreSeckillStock(Long activityId, Long userId) {
                log.error("回补秒杀库存失败", cause);
                throw writeFailure("回补秒杀库存失败", cause);
            }
        };
    }

    private static RuntimeException writeFailure(String action, Throwable cause) {
        // 1.ErrorDecoder 已把下游业务错误（如库存不足）还原为带 code/msg 的异常，原样抛出
        if (cause instanceof CommonException) {
            return (CommonException) cause;
        }
        // 2.网络超时、服务不可用等非业务错误，包装后抛出
        return new RuntimeException(action + "：" + cause.getMessage(), cause);
    }
}
