package com.server.anki.config;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.msg.AlipayMsgClient;
import com.alipay.api.msg.MsgHandler;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 支付宝配置类
 * 包含普通支付接口配置、OAuth配置和消息服务配置
 */
@Configuration
@ConfigurationProperties(prefix = "alipay")
@Getter
@Setter
public class AlipayConfig {

    private static final Logger logger = LoggerFactory.getLogger(AlipayConfig.class);

    private String appId;
    private String privateKey;     // 开发者私钥
    private String publicKey;      // 支付宝公钥
    private String gatewayUrl;     // 支付宝网关地址
    private String notifyUrl;      // 支付异步通知地址
    private String returnUrl;      // 支付同步返回地址
    private String withdrawalNotifyUrl; // 提现异步通知地址
    private String unifiedCallbackUrl;

    // 新增超时配置参数
    private int connectTimeout = 30000;  // 默认连接超时时间，30秒
    private int readTimeout = 30000;     // 默认读取超时时间，30秒

    public String getCallbackUrl() {
        return unifiedCallbackUrl != null ? unifiedCallbackUrl : notifyUrl;
    }

    // OAuth2.0配置
    private OAuthConfig oauth = new OAuthConfig();

    // WebSocket消息服务配置
    private WsConfig ws = new WsConfig();

    /**
     * OAuth2.0 配置内部类
     * 改为非静态内部类，这样可以访问外部类的成员
     */
    @Getter
    @Setter
    public class OAuthConfig {
        // OAuth2.0授权服务器地址
        private String serverUrl;  // 授权服务器地址
        // 授权回调地址
        private String redirectUri;
        // 授权范围
        private String scope = "auth_user";
        // 授权页面主题样式
        private String theme = "default";

        // 新增的OAuth相关配置
        private long stateExpirationSeconds = 300;     // state过期时间，默认5分钟
        private long authCodeExpirationSeconds = 300;  // 授权码过期时间，默认5分钟
        private long loginCooldownSeconds = 5;         // 登录冷却时间，默认5秒
        private long loginLockTimeoutSeconds = 30;     // 登录锁定超时时间，默认30秒

        /**
         * 获取完整的授权页面URL
         * 现在可以直接访问外部类的appId
         */
        public String getAuthPageUrl() {
            return String.format("%s?app_id=%s&scope=%s&redirect_uri=%s&theme=%s",
                    serverUrl, appId, scope, redirectUri, theme);
        }
    }

    /**
     * WebSocket配置内部类
     */
    @Getter
    @Setter
    public static class WsConfig {
        private String serverHost = "openchannel.alipay.com";
        private int heartbeatInterval = 30000;
        private int reconnectInterval = 5000;
        private int bizThreadPoolCoreSize = 16;
        private int bizThreadPoolMaxSize = 32;
    }

    /**
     * 配置支付宝客户端
     * 用于处理普通的支付接口调用和OAuth认证
     * 使用系统属性设置超时参数
     */
    @Bean
    public AlipayClient alipayClient() {
        logger.info("初始化支付宝客户端, AppID: {}, 连接超时: {}ms, 读取超时: {}ms",
                appId, connectTimeout, readTimeout);

        // 设置超时系统属性
        System.setProperty("sun.net.client.defaultConnectTimeout", String.valueOf(connectTimeout));
        System.setProperty("sun.net.client.defaultReadTimeout", String.valueOf(readTimeout));
        System.setProperty("http.connectionTimeout", String.valueOf(connectTimeout));
        System.setProperty("http.socketTimeout", String.valueOf(readTimeout));

        // 使用标准构造函数
        return new DefaultAlipayClient(
                gatewayUrl,
                appId,
                privateKey,
                "json",
                "UTF-8",
                publicKey,
                "RSA2"
        );
    }

    /**
     * 配置支付宝消息客户端
     * 用于接收支付宝服务器推送的实时消息
     */
    @Bean
    public AlipayMsgClient alipayMsgClient(MsgHandler msgHandler) {
        logger.info("开始初始化支付宝消息服务客户端, AppID: {}, 服务器地址: {}", appId, ws.getServerHost());

        // 1. 获取消息客户端实例
        AlipayMsgClient client = AlipayMsgClient.getInstance(appId);

        // 2. 设置服务器连接信息
        try {
            client.setConnector(ws.getServerHost());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        logger.info("已配置消息服务器地址: {}", ws.getServerHost());

        // 3. 设置安全配置
        client.setSecurityConfig("RSA2", privateKey, publicKey);
        logger.info("已完成安全配置设置");

        // 4. 设置消息处理器
        client.setMessageHandler(msgHandler);
        logger.info("已设置消息处理器");

        // 5. 配置业务线程池
        client.setBizThreadPoolCoreSize(ws.getBizThreadPoolCoreSize());
        client.setBizThreadPoolMaxSize(ws.getBizThreadPoolMaxSize());
        logger.info("已配置业务线程池 - 核心线程数: {}, 最大线程数: {}",
                ws.getBizThreadPoolCoreSize(),
                ws.getBizThreadPoolMaxSize());

        return client;
    }
}