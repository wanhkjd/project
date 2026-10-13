package com.wsy.wanjoymall.trade.service.impl;

import com.wsy.wanjoymall.api.client.ItemClient;
import com.wsy.wanjoymall.api.dto.ItemDTO;
import com.wsy.wanjoymall.common.domain.SeckillOrderMsgDTO;
import com.wsy.wanjoymall.common.exception.BizIllegalException;
import com.wsy.wanjoymall.trade.constants.MQConstants;
import com.wsy.wanjoymall.trade.domain.po.Order;
import com.wsy.wanjoymall.trade.domain.po.OrderDetail;
import com.wsy.wanjoymall.trade.mapper.OrderDetailMapper;
import com.wsy.wanjoymall.trade.mapper.OrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SeckillOrderServiceImplTest {

    private OrderMapper orderMapper;
    private OrderDetailMapper detailMapper;
    private ItemClient itemClient;
    private RabbitTemplate rabbitTemplate;
    private SeckillOrderServiceImpl service;

    private SeckillOrderMsgDTO msg() {
        return new SeckillOrderMsgDTO()
                .setOrderId(123456789L).setUserId(1L).setActivityId(9L).setItemId(317578L).setSeckillPrice(100);
    }

    private ItemDTO item() {
        ItemDTO dto = new ItemDTO();
        dto.setId(317578L);
        dto.setName("RIMOWA");
        dto.setSpec("{}");
        dto.setImage("/img.jpg");
        dto.setPrice(28900);
        return dto;
    }

    @BeforeEach
    void setUp() {
        orderMapper = mock(OrderMapper.class);
        detailMapper = mock(OrderDetailMapper.class);
        itemClient = mock(ItemClient.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        service = new SeckillOrderServiceImpl(orderMapper, detailMapper, itemClient, rabbitTemplate);
        ReflectionTestUtils.setField(service, "seckillPayTimeoutMillis", 300000L);
        when(itemClient.queryItemByIds(anyCollection())).thenReturn(List.of(item()));
    }

    @Test
    void shouldInsertOrderAndDetailWithSeckillPriceThenDeductAndSendDelay() throws Exception {
        service.createSeckillOrder(msg());

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).insert(orderCaptor.capture());
        Order order = orderCaptor.getValue();
        assertEquals(123456789L, order.getId());
        assertEquals(100, order.getTotalFee());
        assertEquals(1, order.getStatus());
        assertEquals(9L, order.getSeckillActivityId());
        assertEquals(1L, order.getUserId());

        ArgumentCaptor<OrderDetail> detailCaptor = ArgumentCaptor.forClass(OrderDetail.class);
        verify(detailMapper).insert(detailCaptor.capture());
        OrderDetail detail = detailCaptor.getValue();
        assertEquals(123456789L, detail.getOrderId());
        assertEquals(1, detail.getNum());
        assertEquals(100, detail.getPrice());
        assertEquals("RIMOWA", detail.getName());

        verify(rabbitTemplate).convertAndSend(eq(MQConstants.DELAY_EXCHANGE_NAME), eq(MQConstants.DELAY_ORDER_KEY),
                eq(123456789L), any(MessagePostProcessor.class));
        // Spring AMQP 3.2 使用 Long 延迟属性，毫秒值必须保持不变。
        ArgumentCaptor<MessagePostProcessor> postProcessor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(eq(MQConstants.DELAY_EXCHANGE_NAME), eq(MQConstants.DELAY_ORDER_KEY),
                eq(123456789L), postProcessor.capture());
        Message delayed = postProcessor.getValue().postProcessMessage(new Message(new byte[0], new MessageProperties()));
        assertEquals(300000L, delayed.getMessageProperties().getDelayLong());
        verify(itemClient).deductSeckillStock(9L);
    }

    @Test
    void duplicateOrder_shouldBeIgnoredIdempotently() {
        when(orderMapper.insert(any(Order.class))).thenThrow(new DuplicateKeyException("dup"));

        assertDoesNotThrow(() -> service.createSeckillOrder(msg()));

        verifyNoInteractions(detailMapper);
        verify(itemClient, never()).deductSeckillStock(anyLong());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class), any(MessagePostProcessor.class));
    }

    @Test
    void itemMissing_shouldThrowSoMqRetries() {
        when(itemClient.queryItemByIds(anyCollection())).thenReturn(List.of());

        assertThrows(BizIllegalException.class, () -> service.createSeckillOrder(msg()));

        verifyNoInteractions(orderMapper);
    }

    @Test
    void dbStockDeductFailure_shouldPropagate() {
        doThrow(new BizIllegalException("秒杀库存不足")).when(itemClient).deductSeckillStock(9L);

        assertThrows(BizIllegalException.class, () -> service.createSeckillOrder(msg()));
    }
}
