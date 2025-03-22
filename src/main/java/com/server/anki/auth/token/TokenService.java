package com.server.anki.auth.token;

import com.server.anki.config.RedisConfig;
import com.server.anki.utils.JwtUtil;
import com.server.anki.user.User;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Token 服务类
 * 负责处理访问令牌和刷新令牌的创建、验证和失效处理
 */
@Service
public class TokenService {
    private static final Logger logger = LoggerFactory.getLogger(TokenService.class);

    @Autowired
    @Qualifier("tokenBlacklistTemplate")
    private RedisTemplate<String, String> tokenBlacklistTemplate;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private RedisConfig redisConfig;

    @Getter
    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    /**
     * 生成令牌对，并保存到 Cookie
     */
    public TokenPair generateTokenPair(User user, HttpServletResponse response) {
        String accessToken = generateAccessToken(user);
        String refreshToken = generateRefreshToken(user);

        // 保存刷新令牌到 Redis
        saveRefreshToken(user.getUsername(), refreshToken);

        // 设置 Cookie
        setCookie("access_token", accessToken,
                (int) (redisConfig.getAccessTokenExpiration() / 1000), response);
        setCookie("refresh_token", refreshToken,
                (int) (redisConfig.getRefreshTokenExpiration() / 1000), response);

        return new TokenPair(accessToken, refreshToken);
    }

    /**
     * 刷新令牌对
     * 当 refreshToken 校验通过后，生成新的访问令牌和刷新令牌
     */
    public TokenPair refreshTokens(String oldRefreshToken, HttpServletResponse response) {
        try {
            if (oldRefreshToken == null || !jwtUtil.validateToken(oldRefreshToken)) {
                logger.warn("刷新令牌无效");
                return null;
            }

            String username = jwtUtil.extractUsername(oldRefreshToken);

            // 验证 Redis 中存储的刷新令牌
            if (!validateRefreshToken(username, oldRefreshToken)) {
                logger.warn("刷新令牌与 Redis 中的不匹配，可能已被使用或失效");
                return null;
            }

            // 使旧的刷新令牌失效
            invalidateRefreshToken(username);

            // 获取用户信息
            Long userId = jwtUtil.extractUserId(oldRefreshToken);
            String userGroup = jwtUtil.extractUserGroup(oldRefreshToken);

            // 生成新令牌对
            String newAccessToken = jwtUtil.generateToken(username, userId, userGroup,
                    redisConfig.getAccessTokenExpiration());
            String newRefreshToken = jwtUtil.generateToken(username, userId, userGroup,
                    redisConfig.getRefreshTokenExpiration());

            // 保存新的刷新令牌
            saveRefreshToken(username, newRefreshToken);

            // 设置 Cookie
            setCookie("access_token", newAccessToken,
                    (int) (redisConfig.getAccessTokenExpiration() / 1000), response);
            setCookie("refresh_token", newRefreshToken,
                    (int) (redisConfig.getRefreshTokenExpiration() / 1000), response);

            logger.info("用户 {} 的令牌已成功刷新", username);
            return new TokenPair(newAccessToken, newRefreshToken);

        } catch (Exception e) {
            logger.error("刷新令牌时发生错误", e);
            return null;
        }
    }

    /**
     * 生成访问令牌
     */
    public String generateAccessToken(User user) {
        return jwtUtil.generateToken(
                user.getUsername(),
                user.getId(),
                user.getUserGroup(),
                redisConfig.getAccessTokenExpiration()
        );
    }

    /**
     * 生成刷新令牌
     */
    public String generateRefreshToken(User user) {
        return jwtUtil.generateToken(
                user.getUsername(),
                user.getId(),
                user.getUserGroup(),
                redisConfig.getRefreshTokenExpiration()
        );
    }

    /**
     * 使访问令牌失效（加入黑名单）
     */
    public void invalidateAccessToken(String token) {
        if (token != null) {
            String key = RedisConfig.getAccessTokenBlacklistKey(token);
            Date expiration = jwtUtil.getExpirationDateFromToken(token);
            if (expiration != null) {
                long ttl = expiration.getTime() - System.currentTimeMillis();
                if (ttl > 0) {
                    tokenBlacklistTemplate.opsForValue()
                            .set(key, "1", ttl, TimeUnit.MILLISECONDS);
                    logger.info("访问令牌已添加到黑名单");
                }
            }
        }
    }

    /**
     * 验证访问令牌
     */
    public boolean validateAccessToken(String token) {
        if (token == null) {
            return false;
        }
        // 先检查黑名单
        if (isAccessTokenBlacklisted(token)) {
            logger.warn("访问令牌在黑名单中");
            return false;
        }
        // 再验证 JWT
        return jwtUtil.validateToken(token);
    }

    /**
     * 检查访问令牌是否在黑名单中
     */
    private boolean isAccessTokenBlacklisted(String token) {
        String key = RedisConfig.getAccessTokenBlacklistKey(token);
        return Boolean.TRUE.equals(tokenBlacklistTemplate.hasKey(key));
    }

    /**
     * 从 Cookie 中获取访问令牌
     */
    public String extractAccessToken(HttpServletRequest request) {
        return extractTokenFromCookie(request, "access_token");
    }

    /**
     * 从 Cookie 中获取刷新令牌
     */
    public String extractRefreshToken(HttpServletRequest request) {
        return extractTokenFromCookie(request, "refresh_token");
    }

    /**
     * 从 Cookie 中提取指定名称的令牌
     */
    private String extractTokenFromCookie(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookieName.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    /**
     * 设置 Cookie
     */
    private void setCookie(String name, String value, int maxAge, HttpServletResponse response) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // 生产环境应为 true
        cookie.setPath("/");
        cookie.setMaxAge(maxAge);
        response.addCookie(cookie);
    }

    /**
     * 保存刷新令牌到 Redis
     */
    public void saveRefreshToken(String username, String refreshToken) {
        String key = RedisConfig.getRefreshTokenKey(username);
        Date expiration = jwtUtil.getExpirationDateFromToken(refreshToken);
        if (expiration != null) {
            long ttl = expiration.getTime() - System.currentTimeMillis();
            if (ttl > 0) {
                tokenBlacklistTemplate.opsForValue()
                        .set(key, refreshToken, ttl, TimeUnit.MILLISECONDS);
                logger.info("已为用户 {} 保存刷新令牌", username);
            }
        }
    }

    /**
     * 验证刷新令牌
     */
    public boolean validateRefreshToken(String username, String refreshToken) {
        String key = RedisConfig.getRefreshTokenKey(username);
        String storedToken = tokenBlacklistTemplate.opsForValue().get(key);
        return refreshToken.equals(storedToken);
    }

    /**
     * 使刷新令牌失效
     */
    public void invalidateRefreshToken(String username) {
        String key = RedisConfig.getRefreshTokenKey(username);
        tokenBlacklistTemplate.delete(key);
        logger.info("用户 {} 的刷新令牌已失效", username);
    }

    /**
     * 获取令牌中的用户名
     * 这个方法通过 JwtUtil 从令牌中提取存储的用户名信息
     */
    public String getUsernameFromToken(String token) {
        return jwtUtil.extractUsername(token);
    }

    /**
     * 获取令牌中的用户ID
     * 这个方法通过 JwtUtil 从令牌中提取存储的用户ID信息
     */
    public Long getUserIdFromToken(String token) {
        return jwtUtil.extractUserId(token);
    }

    /**
     * 获取令牌中的用户组信息
     * 这个方法通过 JwtUtil 从令牌中提取存储的用户组信息
     */
    public String getUserGroupFromToken(String token) {
        return jwtUtil.extractUserGroup(token);
    }
}