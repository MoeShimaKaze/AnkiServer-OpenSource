package com.server.anki.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ForwardedHeaderFilter;

/**
 * 反向代理配置类
 * 支持反向代理SSL模式：反向代理处理SSL，应用使用HTTP
 * 仅在ssl.mode=proxy时启用
 */
@Configuration
@ConditionalOnProperty(name = "ssl.mode", havingValue = "proxy")
public class ProxyConfig {

    /**
     * 创建ForwardedHeaderFilter以处理转发的请求头
     * 这将处理X-Forwarded-For, X-Forwarded-Proto, X-Forwarded-Host等头信息
     * 使应用能够感知原始客户端信息
     */
    @Bean
    public ForwardedHeaderFilter forwardedHeaderFilter() {
        return new ForwardedHeaderFilter();
    }
}