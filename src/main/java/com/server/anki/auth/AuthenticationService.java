package com.server.anki.auth;

import com.server.anki.auth.token.TokenPair;
import com.server.anki.auth.token.TokenService;
import com.server.anki.user.User;
import com.server.anki.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/**
 * 认证服务类
 * 负责处理所有与用户认证相关的业务逻辑，包括：
 * - 验证用户认证状态
 * - 刷新认证令牌
 * - 获取认证用户信息
 */
@Service
public class AuthenticationService {
    private static final Logger logger = LoggerFactory.getLogger(AuthenticationService.class);

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserRepository userRepository;

    /**
     * 验证用户认证状态
     * 首先检查访问令牌(Access Token)是否有效，如果无效则尝试使用刷新令牌(Refresh Token)
     *
     * @param request HTTP请求对象，用于提取令牌
     * @param response HTTP响应对象，用于设置新的令牌（如果需要刷新）
     * @return 包含用户认证状态和信息的响应实体
     */
    public ResponseEntity<AuthResponse> validateAuthentication(HttpServletRequest request, HttpServletResponse response) {
        logger.info("开始验证用户认证状态");

        // 首先尝试提取和验证访问令牌
        String accessToken = tokenService.extractAccessToken(request);
        if (accessToken == null || !tokenService.validateAccessToken(accessToken)) {
            // 访问令牌无效，尝试使用刷新令牌
            String refreshToken = tokenService.extractRefreshToken(request);
            if (refreshToken != null) {
                return refreshTokenAndValidate(refreshToken, response);
            }
            logger.warn("未找到有效的访问令牌或刷新令牌");
            return ResponseEntity.status(401).body(new AuthResponse(false, null, null, null));
        }

        return validateTokenInternal(accessToken);
    }

    /**
     * 获取当前认证用户
     * 通过令牌获取用户信息，支持自动刷新机制
     *
     * @param request HTTP请求对象
     * @param response HTTP响应对象
     * @return 认证用户实体，如果认证失败返回null
     */
    public User getAuthenticatedUser(HttpServletRequest request, HttpServletResponse response) {
        // 尝试使用访问令牌获取用户信息
        String accessToken = tokenService.extractAccessToken(request);
        if (accessToken != null && tokenService.validateAccessToken(accessToken)) {
            Long userId = tokenService.getUserIdFromToken(accessToken);
            logger.info("通过访问令牌成功获取用户信息，用户ID: {}", userId);
            return userRepository.findById(userId).orElse(null);
        }

        // 访问令牌无效，尝试使用刷新令牌
        String refreshToken = tokenService.extractRefreshToken(request);
        if (refreshToken != null) {
            TokenPair newTokens = tokenService.refreshTokens(refreshToken, response);
            if (newTokens != null) {
                Long userId = tokenService.getUserIdFromToken(newTokens.getAccessToken());
                logger.info("通过刷新令牌成功获取用户信息，用户ID: {}", userId);
                return userRepository.findById(userId).orElse(null);
            }
        }

        logger.warn("请求中未找到有效令牌，用户未认证");
        return null;
    }

    /**
     * 根据指定令牌获取用户信息
     * 主要用于特定场景下的用户身份验证
     *
     * @param token 访问令牌
     * @return 用户实体，如果令牌无效返回null
     */
    public User getAuthenticatedUserFromToken(String token) {
        if (token != null && tokenService.validateAccessToken(token)) {
            Long userId = tokenService.getUserIdFromToken(token);
            if (userId != null) {
                return userRepository.findById(userId).orElse(null);
            }
        }
        return null;
    }

    /**
     * 刷新用户认证令牌
     * 使用刷新令牌获取新的访问令牌和刷新令牌
     *
     * @param request HTTP请求对象
     * @param response HTTP响应对象
     * @return 操作结果响应实体
     */
    public ResponseEntity<?> refreshAuthentication(HttpServletRequest request, HttpServletResponse response) {
        logger.info("处理令牌刷新请求");

        String refreshToken = tokenService.extractRefreshToken(request);
        if (refreshToken == null) {
            logger.warn("Cookie中未找到刷新令牌");
            return ResponseEntity.status(401).body("未提供刷新令牌");
        }

        TokenPair newTokens = tokenService.refreshTokens(refreshToken, response);
        if (newTokens == null) {
            return ResponseEntity.status(401).body("刷新令牌无效或已过期");
        }

        return ResponseEntity.ok("令牌刷新成功");
    }

    /**
     * 内部方法：使用刷新令牌进行认证
     * 刷新令牌并返回新的用户认证信息
     */
    private ResponseEntity<AuthResponse> refreshTokenAndValidate(String refreshToken, HttpServletResponse response) {
        TokenPair newTokens = tokenService.refreshTokens(refreshToken, response);
        if (newTokens == null) {
            logger.warn("刷新令牌无效或已过期");
            return ResponseEntity.status(401).body(new AuthResponse(false, null, null, null));
        }

        // 使用新的访问令牌获取用户信息
        String newAccessToken = newTokens.getAccessToken();
        String username = tokenService.getUsernameFromToken(newAccessToken);
        Long userId = tokenService.getUserIdFromToken(newAccessToken);
        String userGroup = tokenService.getUserGroupFromToken(newAccessToken);

        logger.info("成功刷新用户令牌，用户名: {}", username);
        return ResponseEntity.ok(new AuthResponse(true, username, userId, userGroup));
    }

    /**
     * 内部方法：验证访问令牌
     * 验证令牌有效性并返回用户信息
     */
    private ResponseEntity<AuthResponse> validateTokenInternal(String token) {
        if (tokenService.validateAccessToken(token)) {
            String username = tokenService.getUsernameFromToken(token);
            Long userId = tokenService.getUserIdFromToken(token);
            String userGroup = tokenService.getUserGroupFromToken(token);
            logger.info("访问令牌验证成功，用户名: {}", username);
            return ResponseEntity.ok(new AuthResponse(true, username, userId, userGroup));
        }

        logger.warn("访问令牌无效");
        return ResponseEntity.status(401).body(new AuthResponse(false, null, null, null));
    }

    /**
     * 认证响应记录类
     * 包含用户的认证状态和基本信息
     */
    public record AuthResponse(
            boolean isLoggedIn,    // 是否已登录
            String username,       // 用户名
            Long userId,          // 用户ID
            String userGroup      // 用户组
    ) {}
}