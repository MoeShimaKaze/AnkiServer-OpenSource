package com.server.anki.auth.filter;

import com.server.anki.auth.token.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Autowired
    private TokenService tokenService;

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response,
                                    @NotNull FilterChain filterChain) throws ServletException, IOException {
        try {
            // 从请求中提取访问令牌
            String accessToken = tokenService.extractAccessToken(request);

            // 如果访问令牌有效，设置认证信息
            if (accessToken != null && tokenService.validateAccessToken(accessToken)) {
                String username = tokenService.getUsernameFromToken(accessToken);
                Long userId = tokenService.getUserIdFromToken(accessToken);
                String userGroup = tokenService.getUserGroupFromToken(accessToken);

                // 创建认证对象并设置到安全上下文中
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                username,
                                null,
                                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + userGroup))
                        );

                SecurityContextHolder.getContext().setAuthentication(authentication);
                logger.info("用户认证成功: {}", username);
            }
        } catch (Exception e) {
            logger.error("JWT认证过程发生错误", e);
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}