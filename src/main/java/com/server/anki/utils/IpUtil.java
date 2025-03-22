package com.server.anki.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * IP地址工具类
 */
public class IpUtil {

    private static final String UNKNOWN = "unknown";
    private static final String LOCALHOST_IPV4 = "127.0.0.1";
    private static final String LOCALHOST_IPV6 = "0:0:0:0:0:0:0:1";

    /**
     * 获取客户端真实IP地址
     * 考虑代理服务器转发情况
     *
     * @param request HTTP请求
     * @return 客户端IP地址
     */
    public static String getClientIp(HttpServletRequest request) {
        String ip = null;

        // X-Forwarded-For：Squid 服务代理
        String ipAddresses = request.getHeader("X-Forwarded-For");

        if (!isValidIpAddresses(ipAddresses)) {
            // Proxy-Client-IP：apache 服务代理
            ipAddresses = request.getHeader("Proxy-Client-IP");
        }

        if (!isValidIpAddresses(ipAddresses)) {
            // WL-Proxy-Client-IP：weblogic 服务代理
            ipAddresses = request.getHeader("WL-Proxy-Client-IP");
        }

        if (!isValidIpAddresses(ipAddresses)) {
            // HTTP_CLIENT_IP：有些代理服务器
            ipAddresses = request.getHeader("HTTP_CLIENT_IP");
        }

        if (!isValidIpAddresses(ipAddresses)) {
            // X-Real-IP：nginx服务代理
            ipAddresses = request.getHeader("X-Real-IP");
        }

        // 有些网络通过多层代理，那么获取到的ip就会有多个，一般都是通过逗号（,）分割开来
        if (StringUtils.hasText(ipAddresses)) {
            ip = ipAddresses.split(",")[0];
        }

        // 如果还未获取到IP，则从请求中获取
        if (!StringUtils.hasText(ip) || UNKNOWN.equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
            if (LOCALHOST_IPV4.equals(ip) || LOCALHOST_IPV6.equals(ip)) {
                // 根据网卡取本机配置的IP
                ip = LOCALHOST_IPV4;
            }
        }

        return ip;
    }

    /**
     * 校验IP地址列表是否有效
     */
    private static boolean isValidIpAddresses(String ipAddresses) {
        return !StringUtils.hasText(ipAddresses) || UNKNOWN.equalsIgnoreCase(ipAddresses);
    }
}