package com.server.anki.config;

import com.server.anki.auth.filter.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${ssl.mode:none}")
    private String sslMode;

    @Value("${app.domain:}")
    private String appDomain;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // 根据SSL模式进行不同配置
        if ("direct".equals(sslMode)) {
            // 直接SSL模式 - 要求安全通道
            http.requiresChannel(channel ->
                    channel.anyRequest().requiresSecure());
        } else if ("proxy".equals(sslMode)) {
            // 反向代理模式 - 添加安全头但不要求安全通道
            http.headers(headers -> headers
                    .httpStrictTransportSecurity(hsts -> hsts
                            .includeSubDomains(true)
                            .maxAgeInSeconds(31536000)
                    )
            );
        }

        return http
                // 配置CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 禁用CSRF，因为我们使用JWT进行身份验证
                .csrf(AbstractHttpConfigurer::disable)
                // 使用无状态会话管理
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 配置请求授权
                .authorizeHttpRequests(authorize -> authorize
                        // 无需验证Token的路径配置
                        .requestMatchers(
                                "/login",
                                "/logout",
                                "/register",
                                "/validateToken",
                                "/email/sendVerificationCode",
                                "/api/alipay/login/callback",
                                "/api/alipay/login/url",
                                "/api/alipay/order/notify",
                                "/api/alipay/withdrawal/notify",
                                "/api/alipay/notify",
                                "/api/alipay/order/return"
                        ).permitAll()
                        // 所有其他请求需要验证
                        .anyRequest().authenticated()
                )
                // 为登出添加自定义配置
                .logout(AbstractHttpConfigurer::disable)
                // 添加JWT认证过滤器
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 创建允许的来源列表
        List<String> allowedOrigins = new ArrayList<>();
        allowedOrigins.add("http://localhost:3000");
        // 根据SSL模式添加额外的来源
        if ("direct".equals(sslMode) || "proxy".equals(sslMode)) {
            allowedOrigins.add("https://localhost:3000");

            // 如果配置了应用域名，也添加
            if (appDomain != null && !appDomain.isEmpty()) {
                allowedOrigins.add("https://" + appDomain);
            }
        }

        // 设置允许的来源
        configuration.setAllowedOrigins(allowedOrigins);

        // 允许的HTTP方法
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

        // 允许的请求头
        configuration.setAllowedHeaders(List.of("*"));

        // 允许发送凭证
        configuration.setAllowCredentials(true);

        // 预检请求的有效期，单位为秒
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}