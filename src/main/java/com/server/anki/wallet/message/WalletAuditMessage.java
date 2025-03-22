package com.server.anki.wallet.message;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 钱包审计消息基类
 * 定义所有审计消息的共同属性
 */
@Data
public class WalletAuditMessage {
    private String messageId = java.util.UUID.randomUUID().toString();
    private LocalDateTime timestamp = LocalDateTime.now();
    private String auditType;
    private Long walletId;
    private Long userId;
    private BigDecimal amount;
    private String reason;
    private String performedBy;
    private String additionalInfo;

    // 用于重试机制
    private int retryCount = 0;
    private LocalDateTime lastRetryTime;
}