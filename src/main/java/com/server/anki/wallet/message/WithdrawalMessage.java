package com.server.anki.wallet.message;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

/**
 * 提现消息
 * 用于处理用户的提现请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WithdrawalMessage extends BaseWalletMessage {
    // 用户ID
    private Long userId;

    // 提现金额
    private BigDecimal amount;

    // 提现方式
    private String withdrawalMethod;

    // 收款账号信息
    private String accountInfo;

    // 收款账户真实姓名
    private String accountName;

    // 提现订单号
    private String withdrawalOrderNo;

    // 提现状态
    private WithdrawalStatus status;

    public WithdrawalMessage() {
        super();
        this.setMessageType(WalletMessageType.WITHDRAWAL);
    }

    public enum WithdrawalStatus {
        PROCESSING,  // 处理中
        SUCCESS,     // 成功
        FAILED      // 失败
    }
}