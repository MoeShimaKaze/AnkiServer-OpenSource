package com.server.anki.auth.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 请求频率限制拦截器
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    @Autowired
    private RateLimiterHandler rateLimiterHandler;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        return rateLimiterHandler.handle(request, response, handler);
    }
}