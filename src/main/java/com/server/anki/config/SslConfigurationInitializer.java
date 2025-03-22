package com.server.anki.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * SSL配置初始化器
 * 这个类根据 ssl.mode 属性动态配置应用服务器
 */
@Configuration
public class SslConfigurationInitializer {

    @Value("${ssl.mode:none}")
    private String sslMode;

    @Value("${ssl.port:8443}")
    private int sslPort;

    /**
     * 根据 ssl.mode 设置服务器配置
     */
    @Bean
    public WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> sslModeCustomizer(Environment env) {
        return factory -> {
            // 根据ssl.mode设置不同的配置
            if ("direct".equals(sslMode)) {
                // 直接 SSL 模式
                // 注意：基本的 SSL 配置 (key-store 等) 仍然通过 application.yml 配置
                factory.setPort(sslPort); // 使用 SSL 端口
            } else if ("proxy".equals(sslMode)) {
                // 反向代理 SSL 模式
                // 在通过Java代码设置forward headers strategy
                System.setProperty("server.forward-headers-strategy", "FRAMEWORK");
                System.setProperty("server.tomcat.remoteip.remote-ip-header", "X-Forwarded-For");
                System.setProperty("server.tomcat.remoteip.protocol-header", "X-Forwarded-Proto");
            }
            // mode=none 不需要特殊配置
        };
    }
}