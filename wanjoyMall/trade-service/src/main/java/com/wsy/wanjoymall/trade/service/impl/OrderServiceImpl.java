package com.wsy.wanjoymall.trade.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wsy.wanjoymall.api.client.CartClient;
import com.wsy.wanjoymall.api.client.ItemClient;
import com.wsy.wanjoymall.api.client.PayClient;
import com.wsy.wanjoymall.api.dto.ItemDTO;
import com.wsy.wanjoymall.common.exception.BadRequestException;
import com.wsy.wanjoymall.common.utils.UserContext;
import com.wsy.wanjoymall.trade.constants.MQConstants;
import com.wsy.wanjoymall.trade.domain.dto.OrderDetailDTO;
import com.wsy.wanjoymall.trade.domain.dto.OrderFormDTO;
import com.wsy.wanjoymall.trade.domain.po.Order;
import com.wsy.wanjoymall.trade.domain.po.OrderDetail;
import com.wsy.wanjoymall.trade.mapper.OrderMapper;
import com.wsy.wanjoymall.trade.service.IOrderDetailService;
import com.wsy.wanjoymall.trade.service.IOrderService;
import org.apache.seata.spring.annotation.GlobalTransactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.wsy.wanjoymall.common.utils.BeanUtils;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @since 2023-05-05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements IOrderService {

 //   private final IItemService itemService;
    private final ItemClient itemClient;
    private final IOrderDetailService detailService;
//    private final ICartService cartService;
    private final CartClient cartClient;
    private final PayClient payClient;
    private final RabbitTemplate rabbitTemplate;

    // 未支付订单自动关闭的等待时间（毫秒），到期后延迟消息触发支付状态检查
    @Value("${wm.order.pay-timeout:900000}")
    private long payTimeoutMillis;

    @Override
    @GlobalTransactional
    public Long createOrder(OrderFormDTO orderFormDTO) {
        // Seata AT 模式管理跨服务事务：任一步骤抛异常，购物车与库存服务的分支事务会连同本地订单数据一起回滚
        // 1.订单数据
        Order order = new Order();
        // 1.1.查询商品
        List<OrderDetailDTO> detailDTOS = orderFormDTO.getDetails();
        // 1.2.获取商品id和数量的Map，同一商品出现多次时数量累加，避免 toMap 因重复 key 抛异常
        Map<Long, Integer> itemNumMap = detailDTOS.stream()
                .collect(Collectors.toMap(OrderDetailDTO::getItemId, OrderDetailDTO::getNum, Integer::sum));
        Set<Long> itemIds = itemNumMap.keySet();
        // 1.3.查询商品：商品服务不可用时 FallbackFactory 降级返回空列表，这里按"商品不存在"拒绝下单
        List<ItemDTO> items = itemClient.queryItemByIds(itemIds);
        if (items == null || items.size() < itemIds.size()) {
            throw new BadRequestException("商品不存在");
        }
        // 1.4.基于商品价格、购买数量计算商品总价：totalFee（以服务端商品价格为准，不信任前端金额）
        int total = 0;
        for (ItemDTO item : items) {
            total += item.getPrice() * itemNumMap.get(item.getId());
        }
        order.setTotalFee(total);
        // 1.5.其它属性
        order.setPaymentType(orderFormDTO.getPaymentType());
        order.setUserId(UserContext.getUser());
        order.setStatus(1);
        // 1.6.将Order写入数据库order表中
        save(order);

        // 2.保存订单详情
        List<OrderDetail> details = buildDetails(order.getId(), items, itemNumMap);
        detailService.saveBatch(details);

        // 3.清理购物车商品
        cartClient.deleteCartItemByIds(itemIds);

        // 4.扣减库存：库存不足或远程调用失败均抛出异常，由 Seata 回滚整个下单链路
        List<com.wsy.wanjoymall.api.dto.OrderDetailDTO> apidetailDTOS = BeanUtils.copyList(detailDTOS, com.wsy.wanjoymall.api.dto.OrderDetailDTO.class);
        itemClient.deductStock(apidetailDTOS);

        // 5.发送延迟消息，到期后检查支付状态，未支付则取消订单
        rabbitTemplate.convertAndSend(
                MQConstants.DELAY_EXCHANGE_NAME,
                MQConstants.DELAY_ORDER_KEY,
                order.getId(),
                message -> {
                    message.getMessageProperties().setDelayLong(payTimeoutMillis);
                    return message;
                }
        );

        return order.getId();
    }

    @Override
    public void markOrderPaySuccess(Long orderId) {
        // 用 status = 1 作为更新条件：支付通知重复投递、延迟消息与 MQ 通知同时到达时，
        // 只有第一次更新生效，后续请求影响行数为 0 而被自然忽略，无需额外去重表
        boolean updated = lambdaUpdate()
                .set(Order::getStatus, 2)
                .set(Order::getPayTime, LocalDateTime.now())
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, 1)
                .update();
        if (!updated) {
            log.info("订单{}已处理，忽略重复支付通知", orderId);
        }
    }



    private List<OrderDetail> buildDetails(Long orderId, List<ItemDTO> items, Map<Long, Integer> numMap) {
        List<OrderDetail> details = new ArrayList<>(items.size());
        for (ItemDTO item : items) {
            OrderDetail detail = new OrderDetail();
            detail.setName(item.getName());
            detail.setSpec(item.getSpec());
            detail.setPrice(item.getPrice());
            detail.setNum(numMap.get(item.getId()));
            detail.setItemId(item.getId());
            detail.setImage(item.getImage());
            detail.setOrderId(orderId);
            details.add(detail);
        }
        return details;
    }

    //4.2.未支付，取消订单，回复库存
    @Override
    @GlobalTransactional
    public void cancelOrder(Long orderId) {
        // 1.先关闭支付单：关闭后用户无法再支付，避免"订单已关单、库存已回补，用户却付款成功"
        //   支付服务不在 Seata 全局事务内，但关闭操作幂等，本方法失败重试时可以安全重复调用
        Boolean closed = payClient.closePayOrderByBizOrderNo(orderId);
        if (closed == null) {
            throw new RuntimeException("关闭支付单结果未知，稍后重试");
        }
        if (!closed) {
            // 支付单已支付成功（延迟消息到期与付款几乎同时发生），改为标记订单已支付
            log.info("订单{}在关单前已支付，跳过取消", orderId);
            markOrderPaySuccess(orderId);
            return;
        }
        // 2.条件更新订单为已关闭(5)：只有未付款订单会更新成功，重复的延迟消息在此被过滤
        boolean updated = lambdaUpdate()
                .set(Order::getStatus, 5)
                .set(Order::getCloseTime, LocalDateTime.now())
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, 1)
                .update();
        if (!updated) {
            log.info("订单{}已不是未付款状态，跳过取消", orderId);
            return;
        }
        // 3.回补库存：秒杀订单与普通订单走不同的库存
        //   失败抛异常，Seata 回滚上面的订单状态更新，消息重试后整体重做
        Order order = getById(orderId);
        if (order.getSeckillActivityId() != null) {
            // 3.1.秒杀订单：回补 seckill_activity 表 + Redis，并放开该用户再次抢购
            itemClient.restoreSeckillStock(order.getSeckillActivityId(), order.getUserId());
            log.info("秒杀订单{}超时未支付已关闭，秒杀库存已回补", orderId);
            return;
        }
        // 3.2.普通订单：按订单明细调用商品服务加回 item.stock
        List<OrderDetail> details = detailService.lambdaQuery()
                .eq(OrderDetail::getOrderId, orderId)
                .list();
        List<com.wsy.wanjoymall.api.dto.OrderDetailDTO> items = BeanUtils.copyList(details, com.wsy.wanjoymall.api.dto.OrderDetailDTO.class);
        itemClient.restoreStock(items);
        log.info("订单{}超时未支付已关闭，库存已回补", orderId);
    }

}
