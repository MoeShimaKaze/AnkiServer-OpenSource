package com.server.anki.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host}")
    private String host;

    @Value("${spring.data.redis.port}")
    private int port;

    @Value("${spring.data.redis.password:}")
    private String password;

    // 看门狗超时时间，默认30秒
    @Value("${redisson.lock.watchdog.timeout:30000}")
    private long watchdogTimeout;

    @Bean
    public RedissonClient redissonClient() {
        // 创建配置对象
        Config config = new Config();

        // 设置看门狗超时时间
        config.setLockWatchdogTimeout(watchdogTimeout);

        // 使用单节点模式配置Redis连接
        config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                // 如果密码不为空，则设置密码
                .setPassword(password.isEmpty() ? null : password)
                .setDatabase(0)
                // 设置连接池大小
                .setConnectionPoolSize(64)
                // 设置连接超时时间
                .setConnectTimeout(10000)
                // 设置命令等待超时时间
                .setTimeout(3000)
                // 设置重试次数
                .setRetryAttempts(3)
                // 设置重试间隔时间
                .setRetryInterval(1500);

        // 创建并返回RedissonClient实例
        return Redisson.create(config);
    }
}