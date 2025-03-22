package com.server.anki.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "amap")
public class MapConfig {
    /**
     * JavaScript API密钥，用于在浏览器端调用高德地图JavaScript SDK
     */
    private String key;

    /**
     * Web服务API密钥，用于服务端调用高德地图HTTP接口
     * 例如：路径规划、地理编码、搜索等服务
     */
    private String webKey;

    /**
     * JavaScript API安全密钥，用于JavaScript API的安全校验
     */
    private String securityJsCode;
}