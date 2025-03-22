package com.server.anki.wallet.message;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 钱包消息基类
 * 包含所有钱包相关消息的共同属性
 */
@Data
public abstract class BaseWalletMessage {
    // 消息唯一标识
    private String messageId;

    // 消息类型
    private WalletMessageType messageType;

    // 消息创建时间
    private LocalDateTime createTime;

    // 消息重试次数
    private int retryCount;

    // 最后一次重试时间
    private LocalDateTime lastRetryTime;

    // 描述信息
    private String description;

    public BaseWalletMessage() {
        this.messageId = java.util.UUID.randomUUID().toString();
        this.createTime = LocalDateTime.now();
        this.retryCount = 0;
    }
}