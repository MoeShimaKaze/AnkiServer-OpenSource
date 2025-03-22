package com.server.anki.wallet.message;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 钱包初始化消息
 * 用于处理新用户钱包的创建
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WalletInitMessage extends BaseWalletMessage {
    // 用户ID
    private Long userId;

    // 用户名(冗余字段，用于日志)
    private String username;

    // 用户验证状态
    private String verificationStatus;

    public WalletInitMessage() {
        super();
        this.setMessageType(WalletMessageType.WALLET_INIT);
    }
}