package com.server.anki.wallet.message;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

/**
 * 转账消息
 * 用于处理用户之间的转账操作
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TransferMessage extends BaseWalletMessage {
    // 转出用户ID
    private Long fromUserId;

    // 转入用户ID
    private Long toUserId;

    // 转账金额
    private BigDecimal amount;

    // 转账原因
    private String reason;

    // 业务流水号
    private String businessOrderNo;

    // 转账类型
    private TransferType transferType;

    public TransferMessage() {
        super();
        this.setMessageType(WalletMessageType.TRANSFER);
    }

    public enum TransferType {
        NORMAL,     // 普通转账
        SYSTEM,     // 系统转账
        REFUND      // 退款转账
    }
}