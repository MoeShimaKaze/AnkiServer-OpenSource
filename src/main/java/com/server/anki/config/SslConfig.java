package com.server.anki.config;

import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SSL 配置类
 * 支持直接 SSL 模式：应用直接处理 SSL 连接
 * 仅在 ssl.mode=direct 时启用
 */
@Configuration
@ConditionalOnProperty(name = "ssl.mode", havingValue = "direct")
public class SslConfig {

    @Value("${server.port:32222}")
    private int serverPort;

    @Value("${ssl.port:8443}")
    private int sslPort;

    /**
     * 配置同时支持 HTTP 和 HTTPS
     * 应用将同时监听 HTTP 端口和 HTTPS 端口
     */
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> servletContainerCustomizer() {
        return factory -> {
            // 添加 HTTP 连接器
            Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
            connector.setPort(serverPort);
            connector.setSecure(false);
            // 设置重定向端口为 SSL 端口
            connector.setRedirectPort(sslPort);

            // 将 server.port 变为 SSL 端口
            factory.setPort(sslPort);
            factory.addAdditionalTomcatConnectors(connector);

            // 使用属性方式设置 SSL 配置，通过 server.ssl.* 属性控制
            // 这不需要直接使用 Http11NioProtocol API
            factory.addConnectorCustomizers(sslConnector -> {
                // 通过 connector 属性设置 SSL 选项
                sslConnector.setProperty("sslEnabledProtocols", "TLSv1.2,TLSv1.3");

                // 设置安全头信息
                sslConnector.setProperty("hstsEnabled", "true");
                sslConnector.setProperty("hstsMaxAgeSeconds", "31536000");
                sslConnector.setProperty("hstsIncludeSubDomains", "true");

                // 设置连接超时
                sslConnector.setProperty("connectionTimeout", "20000");

                // 以下属性通过 application.yml 中的 server.ssl.* 配置即可
                // 不需要在代码中直接设置
            });
        };
    }
}