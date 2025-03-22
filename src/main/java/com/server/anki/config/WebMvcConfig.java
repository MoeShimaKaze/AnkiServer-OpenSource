package com.server.anki.config;

import com.server.anki.auth.ratelimit.RateLimitInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置类
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册限流拦截器，并配置拦截路径
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns(
                        "/login",
                        "/register",
                        "/validateToken",
                        "/refresh",
                        "/api/public/**",
                        "/api/auth/**",
                        "/api/open/**"
                );
    }
}