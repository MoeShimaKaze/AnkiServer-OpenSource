package com.server.anki.utils;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 分布式锁工具类
 * 基于Redisson实现，提供分布式锁的获取、释放等功能
 * 包含看门狗机制，自动延长锁的有效期
 */
@Component
public class DistributedLockHelper {
    private static final Logger logger = LoggerFactory.getLogger(DistributedLockHelper.class);

    // 定义锁的前缀常量，便于统一管理和识别不同业务的锁
    private static final String PAYMENT_LOCK_PREFIX = "payment:order:lock:";
    private static final String REFUND_LOCK_PREFIX = "payment:refund:lock:";
    private static final String WALLET_LOCK_PREFIX = "wallet:transaction:lock:";

    // 定义锁的默认超时时间
    private static final long DEFAULT_WAIT_TIME = 5;
    private static final TimeUnit DEFAULT_TIME_UNIT = TimeUnit.SECONDS;

    private final RedissonClient redissonClient;

    @Autowired
    public DistributedLockHelper(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 获取支付订单锁的key
     * @param orderNumber 订单号
     * @return 锁的key
     */
    public static String getPaymentLockKey(String orderNumber) {
        return PAYMENT_LOCK_PREFIX + orderNumber;
    }

    /**
     * 获取退款锁的key
     * @param orderNumber 订单号
     * @return 锁的key
     */
    public static String getRefundLockKey(String orderNumber) {
        return REFUND_LOCK_PREFIX + orderNumber;
    }

    /**
     * 获取钱包交易锁的key
     * @param userId 用户ID
     * @return 锁的key
     */
    public static String getWalletLockKey(Long userId) {
        return WALLET_LOCK_PREFIX + userId;
    }

    /**
     * 尝试获取分布式锁（使用默认超时时间）
     * @param lockKey 锁的key
     * @return 是否获取成功
     */
    public boolean tryLock(String lockKey) {
        return tryLock(lockKey, DEFAULT_WAIT_TIME, DEFAULT_TIME_UNIT);
    }

    /**
     * 尝试获取分布式锁
     * @param lockKey 锁的key
     * @param waitTime 等待时间
     * @param timeUnit 时间单位
     * @return 是否获取成功
     */
    public boolean tryLock(String lockKey, long waitTime, TimeUnit timeUnit) {
        try {
            RLock lock = redissonClient.getLock(lockKey);
            boolean locked = lock.tryLock(waitTime, timeUnit);
            if (locked) {
                logger.debug("成功获取分布式锁，lockKey: {}", lockKey);
            } else {
                logger.warn("获取分布式锁失败，lockKey: {}", lockKey);
            }
            return locked;
        } catch (InterruptedException e) {
            logger.error("获取分布式锁过程中被中断，lockKey: {}", lockKey, e);
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            logger.error("获取分布式锁异常，lockKey: {}", lockKey, e);
            return false;
        }
    }

    /**
     * 释放分布式锁
     * @param lockKey 锁的key
     */
    public void unlock(String lockKey) {
        try {
            RLock lock = redissonClient.getLock(lockKey);
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                logger.debug("成功释放分布式锁，lockKey: {}", lockKey);
            } else {
                logger.warn("当前线程并未持有该锁，无需释放，lockKey: {}", lockKey);
            }
        } catch (Exception e) {
            logger.error("释放分布式锁异常，lockKey: {}", lockKey, e);
        }
    }

    /**
     * 强制释放锁（谨慎使用）
     * @param lockKey 锁的key
     */
    public void forceUnlock(String lockKey) {
        try {
            RLock lock = redissonClient.getLock(lockKey);
            if (lock.isLocked()) {
                lock.forceUnlock();
                logger.warn("强制释放分布式锁，lockKey: {}", lockKey);
            }
        } catch (Exception e) {
            logger.error("强制释放分布式锁异常，lockKey: {}", lockKey, e);
        }
    }

    /**
     * 查询锁是否被当前线程持有
     * @param lockKey 锁的key
     * @return 是否持有锁
     */
    public boolean isHeldByCurrentThread(String lockKey) {
        try {
            RLock lock = redissonClient.getLock(lockKey);
            return lock.isHeldByCurrentThread();
        } catch (Exception e) {
            logger.error("查询锁状态异常，lockKey: {}", lockKey, e);
            return false;
        }
    }

    /**
     * 查询锁是否已被任何线程锁定
     * @param lockKey 锁的key
     * @return 是否已被锁定
     */
    public boolean isLocked(String lockKey) {
        try {
            RLock lock = redissonClient.getLock(lockKey);
            return lock.isLocked();
        } catch (Exception e) {
            logger.error("查询锁状态异常，lockKey: {}", lockKey, e);
            return false;
        }
    }

    /**
     * 获取锁的剩余过期时间
     * @param lockKey 锁的key
     * @param timeUnit 时间单位
     * @return 剩余时间，如果锁不存在则返回-2，如果锁存在但没有过期时间则返回-1
     */
    public long getRemainTimeToLive(String lockKey, TimeUnit timeUnit) {
        try {
            RLock lock = redissonClient.getLock(lockKey);
            return lock.remainTimeToLive();
        } catch (Exception e) {
            logger.error("获取锁剩余时间异常，lockKey: {}", lockKey, e);
            return -2;
        }
    }
}