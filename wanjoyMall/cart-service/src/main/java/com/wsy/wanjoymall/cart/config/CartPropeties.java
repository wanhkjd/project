package com.wsy.wanjoymall.cart.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "wm.cart")
public class CartPropeties {
    private  Integer maxItems;
}
