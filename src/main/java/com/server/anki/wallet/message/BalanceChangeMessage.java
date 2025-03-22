package com.server.anki.wallet.message;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

/**
 * 余额变更消息
 * 用于处理钱包余额的增减操作
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BalanceChangeMessage extends BaseWalletMessage {
    // 用户ID
    private Long userId;

    // 变更金额（正数表示增加，负数表示减少）
    private BigDecimal amount;

    // 变更原因
    private String reason;

    // 变更类型（可用余额/待结算余额）
    private BalanceChangeType changeType;

    // 关联业务订单号（如果有）
    private String businessOrderNo;

    public BalanceChangeMessage() {
        super();
        this.setMessageType(WalletMessageType.BALANCE_CHANGE);
    }

    public enum BalanceChangeType {
        AVAILABLE,    // 可用余额
        PENDING      // 待结算余额
    }
}