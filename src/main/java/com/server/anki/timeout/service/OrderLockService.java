package com.server.anki.timeout.service;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 订单处理锁服务
 * 基于Redisson实现分布式锁，确保同一订单不会被并发处理
 */
@Service
public class OrderLockService {
    private static final Logger logger = LoggerFactory.getLogger(OrderLockService.class);

    // 锁前缀，用于区分不同类型的锁
    private static final String ORDER_LOCK_PREFIX = "order:lock:";

    @Autowired
    private RedissonClient redissonClient;

    // 默认锁超时时间，防止死锁
    @Value("${order.lock.lease-time:10000}")
    private long leaseTimeMillis;

    // 默认等待锁获取的超时时间
    @Value("${order.lock.wait-time:1000}")
    private long waitTimeMillis;

    /**
     * 获取订单锁的键名
     */
    private String getLockKey(UUID orderNumber) {
        return ORDER_LOCK_PREFIX + orderNumber.toString();
    }

    /**
     * 尝试获取订单锁
     * @param orderNumber 订单编号
     * @return 是否成功获取锁
     */
    public boolean tryLock(UUID orderNumber) {
        return tryLock(orderNumber, waitTimeMillis, leaseTimeMillis);
    }

    /**
     * 尝试获取订单锁，可指定等待和租约时间
     * @param orderNumber 订单编号
     * @param waitTime 等待获取锁的最长时间(毫秒)
     * @param leaseTime 锁的租约时间(毫秒)，超过该时间锁自动释放
     * @return 是否成功获取锁
     */
    public boolean tryLock(UUID orderNumber, long waitTime, long leaseTime) {
        String lockKey = getLockKey(orderNumber);
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean acquired = lock.tryLock(waitTime, leaseTime, TimeUnit.MILLISECONDS);
            if (acquired) {
                logger.debug("成功获取订单 {} 的分布式锁", orderNumber);
            } else {
                logger.debug("无法获取订单 {} 的分布式锁，可能正在被其他线程处理", orderNumber);
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("获取订单 {} 锁时被中断", orderNumber);
            return false;
        } catch (Exception e) {
            logger.error("获取订单 {} 锁时发生错误: {}", orderNumber, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 释放订单锁
     * @param orderNumber 订单编号
     */
    public void unlock(UUID orderNumber) {
        String lockKey = getLockKey(orderNumber);
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                logger.debug("释放订单 {} 的分布式锁", orderNumber);
            }
        } catch (Exception e) {
            logger.error("释放订单 {} 锁时发生错误: {}", orderNumber, e.getMessage(), e);
        }
    }

    /**
     * 检查订单锁是否被当前线程持有
     * @param orderNumber 订单编号
     * @return 是否被当前线程持有
     */
    public boolean isLockedByCurrentThread(UUID orderNumber) {
        String lockKey = getLockKey(orderNumber);
        RLock lock = redissonClient.getLock(lockKey);
        return lock.isHeldByCurrentThread();
    }

    /**
     * 检查订单是否被锁定
     * @param orderNumber 订单编号
     * @return 是否被锁定
     */
    public boolean isLocked(UUID orderNumber) {
        String lockKey = getLockKey(orderNumber);
        RLock lock = redissonClient.getLock(lockKey);
        return lock.isLocked();
    }
}