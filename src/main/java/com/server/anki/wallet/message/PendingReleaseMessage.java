package com.server.anki.wallet.message;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 待结算金额释放消息
 * 用于处理待结算余额转化为可用余额的定时任务
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PendingReleaseMessage extends BaseWalletMessage {
    // 用户ID
    private Long userId;

    // 释放金额
    private BigDecimal amount;

    // 预计释放时间
    private LocalDateTime scheduleReleaseTime;

    // 关联的业务订单号
    private String businessOrderNo;

    public PendingReleaseMessage() {
        super();
        this.setMessageType(WalletMessageType.PENDING_RELEASE);
    }
}