package com.wsy.wanjoymall.common.config;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

@AutoConfiguration
@ConditionalOnClass(Jackson2JsonMessageConverter.class)
public class MqConfig {
    @Bean
    @ConditionalOnMissingBean(MessageConverter.class)
    public MessageConverter messageConverter(){
        return new Jackson2JsonMessageConverter();
    }
}
