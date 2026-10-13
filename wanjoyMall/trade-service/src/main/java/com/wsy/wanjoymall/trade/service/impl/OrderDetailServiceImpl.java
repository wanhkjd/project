package com.wsy.wanjoymall.trade.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wsy.wanjoymall.trade.domain.po.OrderDetail;
import com.wsy.wanjoymall.trade.mapper.OrderDetailMapper;
import com.wsy.wanjoymall.trade.service.IOrderDetailService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 订单详情表 服务实现类
 * </p>
 *
 * @since 2023-05-05
 */
@Service
public class OrderDetailServiceImpl extends ServiceImpl<OrderDetailMapper, OrderDetail> implements IOrderDetailService {

}
