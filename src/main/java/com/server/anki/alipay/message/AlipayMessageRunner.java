package com.server.anki.alipay.message;

import com.alipay.api.msg.AlipayMsgClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class AlipayMessageRunner implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(AlipayMessageRunner.class);

    // 定义连接超时时间
    private static final long CONNECTION_TIMEOUT_SECONDS = 10;

    private final AlipayMsgClient alipayMsgClient;
    private boolean lastConnectionState = false;

    public AlipayMessageRunner(AlipayMsgClient alipayMsgClient) {
        this.alipayMsgClient = alipayMsgClient;
    }

    @Override
    public void run(String... args) {
        try {
            logger.info("正在检查支付宝消息服务连接状态...");

            if (!alipayMsgClient.isConnected()) {
                logger.info("开始建立支付宝消息服务连接...");

                // 启动连接
                alipayMsgClient.connect();

                // 使用CompletableFuture异步等待连接建立
                CompletableFuture<Boolean> connectionFuture = CompletableFuture.supplyAsync(() -> {
                    // 在新的线程中检查连接状态
                    while (!Thread.currentThread().isInterrupted()) {
                        if (alipayMsgClient.isConnected()) {
                            return true;
                        }
                        try {
                            // 短暂暂停以避免过于频繁的检查
                            TimeUnit.MILLISECONDS.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return false;
                        }
                    }
                    return false;
                });

                try {
                    // 等待连接完成，设置超时时间
                    Boolean connected = connectionFuture.get(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                    if (connected) {
                        lastConnectionState = true;
                        logger.info("支付宝消息服务连接成功!");
                    } else {
                        logger.error("支付宝消息服务连接失败，请检查配置和网络状态");
                    }
                } catch (java.util.concurrent.TimeoutException e) {
                    logger.error("支付宝消息服务连接超时");
                    connectionFuture.cancel(true);
                    throw new RuntimeException("连接支付宝消息服务超时", e);
                }
            } else {
                lastConnectionState = true;
                logger.info("支付宝消息服务已连接");
            }
        } catch (Exception e) {
            logger.error("支付宝消息服务连接异常: {}", e.getMessage(), e);
            throw new RuntimeException("连接支付宝消息服务失败", e);
        }
    }

    /**
     * 定期检查连接状态
     * 每30秒检查一次连接状态，如果状态发生变化则记录日志
     */
    @Scheduled(fixedRate = 30000)
    public void monitorConnection() {
        boolean currentState = alipayMsgClient.isConnected();

        // 只有在状态发生变化时才记录日志
        if (currentState != lastConnectionState) {
            if (currentState) {
                logger.info("支付宝消息服务已重新连接");
            } else {
                logger.warn("支付宝消息服务连接已断开，等待自动重连...");
            }
            lastConnectionState = currentState;
        }
    }
}