package com.server.anki.auth.token;

import com.server.anki.utils.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 专门管理 Refresh Token 的 Redis 存储和验证。
 * 你可以将它命名为 TokenBlacklistService 或 RefreshTokenService 均可。
 */
@Service
public class TokenBlacklistService {

    private static final Logger logger = LoggerFactory.getLogger(TokenBlacklistService.class);

    // 存储刷新令牌的前缀
    private static final String REFRESH_TOKEN_KEY = "refresh_token:";

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 将 refreshToken 保存到 Redis 中(key=refresh_token:username)，并设置过期时间
     */
    public void saveRefreshToken(String username, String refreshToken) {
        String key = REFRESH_TOKEN_KEY + username;
        long expiration = jwtUtil.extractExpiration(refreshToken).getTime() - System.currentTimeMillis();
        // 存储到Redis
        redisTemplate.opsForValue().set(key, refreshToken, expiration, TimeUnit.MILLISECONDS);
        logger.info("Saved refresh token for user: {}", username);
    }

    /**
     * 判断传入的 refreshToken 是否和Redis里存储的一致
     */
    public boolean validateRefreshToken(String username, String refreshToken) {
        String key = REFRESH_TOKEN_KEY + username;
        String storedToken = redisTemplate.opsForValue().get(key);
        boolean isValid = refreshToken.equals(storedToken);
        logger.info("Validated refresh token for user: {}. Is valid: {}", username, isValid);
        return isValid;
    }

    /**
     * 将 Redis 里对应的 Refresh Token 删除（或加入黑名单）。
     * 本示例是直接删除，表示这个 refreshToken 作废了。
     */
    public void invalidateRefreshToken(String username) {
        String key = REFRESH_TOKEN_KEY + username;
        redisTemplate.delete(key);
        logger.info("Invalidated refresh token for user: {}", username);
    }

    /**
     * 定期清理Redis中已过期的Refresh Token
     * (可用定时任务调用 purgeExpiredTokens)
     */
    public void purgeExpiredTokens() {
        logger.info("Starting to purge expired tokens");
        Set<String> keys = redisTemplate.keys(REFRESH_TOKEN_KEY + "*");
        int purgedCount = 0;
        for (String key : keys) {
            String token = redisTemplate.opsForValue().get(key);
            // 如果token已过期，则删除
            if (token != null && jwtUtil.isTokenExpired(token)) {
                redisTemplate.delete(key);
                purgedCount++;
            }
        }
        logger.info("Purged {} expired tokens", purgedCount);
    }
}
