package com.wsy.wanjoymall.trade.service;

import com.wsy.wanjoymall.common.domain.SeckillOrderMsgDTO;

public interface ISeckillOrderService {

    // 消费秒杀消息落库：幂等，重复投递只建一单
    void createSeckillOrder(SeckillOrderMsgDTO msg);
}
