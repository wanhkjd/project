package com.wsy.wanjoymall.trade.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wsy.wanjoymall.trade.domain.dto.OrderFormDTO;
import com.wsy.wanjoymall.trade.domain.po.Order;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @since 2023-05-05
 */
public interface IOrderService extends IService<Order> {

    Long createOrder(OrderFormDTO orderFormDTO);

    void markOrderPaySuccess(Long orderId);

    void cancelOrder(Long orderId);
}
