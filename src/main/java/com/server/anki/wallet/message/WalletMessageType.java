package com.server.anki.wallet.message;

import lombok.Getter;

/**
 * 钱包消息类型枚举
 * 定义所有支持的钱包操作类型
 */
@Getter
public enum WalletMessageType {
    WALLET_INIT("钱包初始化"),
    BALANCE_CHANGE("余额变更"),
    TRANSFER("转账处理"),
    WITHDRAWAL("提现处理"),
    REFUND("退款处理"),
    PENDING_RELEASE("待结算金额释放");

    private final String description;

    WalletMessageType(String description) {
        this.description = description;
    }

}