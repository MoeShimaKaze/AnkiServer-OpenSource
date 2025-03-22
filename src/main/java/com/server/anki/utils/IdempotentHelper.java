package com.server.anki.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 幂等性处理助手
 * 使用Redis实现消息幂等性处理，避免重复处理相同的消息
 */
@Component
public class IdempotentHelper {
    private static final Logger logger = LoggerFactory.getLogger(IdempotentHelper.class);

    // 消息处理状态
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    /**
     * 尝试处理一个消息，确保幂等性
     *
     * @param key 唯一标识消息的键
     * @param expirationDays 过期时间(天)
     * @return 如果消息可以处理返回true，否则返回false
     */
    public boolean tryProcess(String key, int expirationDays) {
        try {
            // 检查消息是否已处理完成
            String status = redisTemplate.opsForValue().get(key);
            if (STATUS_COMPLETED.equals(status)) {
                logger.debug("消息已完成处理，跳过: {}", key);
                return false;
            }

            // 如果消息正在处理中，也跳过
            if (STATUS_PROCESSING.equals(status)) {
                logger.debug("消息正在处理中，跳过: {}", key);
                return false;
            }

            // 标记消息为处理中，设置过期时间
            Boolean setResult = redisTemplate.opsForValue()
                    .setIfAbsent(key, STATUS_PROCESSING, Duration.ofDays(expirationDays));

            if (Boolean.TRUE.equals(setResult)) {
                logger.debug("开始处理消息: {}", key);
                return true;
            } else {
                logger.debug("消息已被其他进程处理，跳过: {}", key);
                return false;
            }
        } catch (Exception e) {
            logger.error("检查消息幂等性时发生错误: {}", e.getMessage(), e);
            // 发生错误时，为了安全起见默认返回true，允许处理
            // 但在高并发场景下可能会导致重复处理
            return true;
        }
    }

    /**
     * 标记消息处理完成
     *
     * @param key 唯一标识消息的键
     */
    public void markProcessed(String key) {
        try {
            redisTemplate.opsForValue().set(key, STATUS_COMPLETED);
            logger.debug("消息已标记为处理完成: {}", key);
        } catch (Exception e) {
            logger.error("标记消息处理完成时发生错误: {}", e.getMessage(), e);
        }
    }

    /**
     * 标记消息处理失败
     *
     * @param key 唯一标识消息的键
     */
    public void markFailed(String key) {
        try {
            redisTemplate.opsForValue().set(key, STATUS_FAILED);
            logger.debug("消息已标记为处理失败: {}", key);
        } catch (Exception e) {
            logger.error("标记消息处理失败时发生错误: {}", e.getMessage(), e);
        }
    }
}