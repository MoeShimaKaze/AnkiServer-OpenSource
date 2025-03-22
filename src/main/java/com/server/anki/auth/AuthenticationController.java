package com.server.anki.auth;

import com.server.anki.auth.ratelimit.RateLimit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * 认证控制器
 * 处理所有与用户认证相关的HTTP请求
 * 主要负责路由分发，具体业务逻辑委托给AuthenticationService处理
 */
@RestController
public class AuthenticationController {

    @Autowired
    private AuthenticationService authenticationService;

    /**
     * 验证用户令牌
     * 用于检查用户的认证状态，必要时会自动刷新令牌
     */
    @GetMapping("/validateToken")
    @RateLimit(rate = 30, timeUnit = TimeUnit.MINUTES, limitType = RateLimit.LimitType.IP)
    public ResponseEntity<AuthenticationService.AuthResponse> validateToken(
            HttpServletRequest request,
            HttpServletResponse response) {
        return authenticationService.validateAuthentication(request, response);
    }

    /**
     * 刷新认证令牌
     * 用于主动刷新用户的认证令牌
     */
    @PostMapping("/refresh")
    @RateLimit(rate = 30, timeUnit = TimeUnit.MINUTES, limitType = RateLimit.LimitType.IP)
    public ResponseEntity<?> refreshToken(
            HttpServletRequest request,
            HttpServletResponse response) {
        return authenticationService.refreshAuthentication(request, response);
    }
}