package com.server.anki.wallet.message;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 钱包消息工具类
 * 提供消息创建和验证的便捷方法
 */
public class WalletMessageUtils {

    /**
     * 创建钱包初始化消息
     */
    public static WalletInitMessage createInitMessage(Long userId, String username, String verificationStatus) {
        WalletInitMessage message = new WalletInitMessage();
        message.setUserId(userId);
        message.setUsername(username);
        message.setVerificationStatus(verificationStatus);
        return message;
    }

    /**
     * 创建余额变更消息
     */
    public static BalanceChangeMessage createBalanceChangeMessage(Long userId, BigDecimal amount,
                                                                  String reason, BalanceChangeMessage.BalanceChangeType changeType) {
        BalanceChangeMessage message = new BalanceChangeMessage();
        message.setUserId(userId);
        message.setAmount(amount);
        message.setReason(reason);
        message.setChangeType(changeType);
        return message;
    }

    /**
     * 创建转账消息
     */
    public static TransferMessage createTransferMessage(Long fromUserId, Long toUserId,
                                                        BigDecimal amount, String reason, TransferMessage.TransferType transferType) {
        TransferMessage message = new TransferMessage();
        message.setFromUserId(fromUserId);
        message.setToUserId(toUserId);
        message.setAmount(amount);
        message.setReason(reason);
        message.setTransferType(transferType);
        return message;
    }

    /**
     * 创建提现消息
     */
    public static WithdrawalMessage createWithdrawalMessage(Long userId, BigDecimal amount,
                                                            String withdrawalMethod, String accountInfo,
                                                            String accountName) {
        WithdrawalMessage message = new WithdrawalMessage();
        message.setUserId(userId);
        message.setAmount(amount);
        message.setWithdrawalMethod(withdrawalMethod);
        message.setAccountInfo(accountInfo);
        message.setAccountName(accountName); // 设置账户真实姓名
        message.setStatus(WithdrawalMessage.WithdrawalStatus.PROCESSING);
        return message;
    }

    /**
     * 验证消息是否可以重试
     */
    public static boolean canRetry(BaseWalletMessage message) {
        return message.getRetryCount() < 3;
    }

    /**
     * 更新消息重试信息
     */
    public static void updateRetryInfo(BaseWalletMessage message) {
        message.setRetryCount(message.getRetryCount() + 1);
        message.setLastRetryTime(LocalDateTime.now());
    }
}