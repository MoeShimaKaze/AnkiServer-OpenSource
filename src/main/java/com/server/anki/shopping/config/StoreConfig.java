package com.server.anki.shopping.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * 店铺配置类
 * 用于管理店铺相关的配置参数，如保证金金额等
 */
@Setter
@Getter
@Configuration
@ConfigurationProperties(prefix = "store")
public class StoreConfig {

    // 商家店铺保证金额度，默认1000元
    private BigDecimal securityDeposit = new BigDecimal("1000.00");

}