package com.server.anki.wallet.service;

import com.server.anki.wallet.entity.Wallet;
import com.server.anki.wallet.entity.WalletAudit;
import com.server.anki.wallet.repository.WalletAuditRepository;
import com.server.anki.wallet.message.WalletAuditMessage;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 钱包审计服务
 * 采用异步消息队列架构，提供完整的钱包操作审计功能
 * 支持所有钱包操作的审计日志记录，包括：
 * - 余额变更
 * - 待结算余额修改
 * - 转账操作
 * - 钱包创建
 * - 退款处理
 * - 提现操作
 */
@Service
public class WalletAuditService {
    private static final Logger auditLogger = LoggerFactory.getLogger("WALLET_AUDIT");

    // 消息队列相关常量
    private static final String AUDIT_EXCHANGE = "wallet.audit.exchange";
    private static final String AUDIT_ROUTING_KEY = "wallet.audit";

    // 本地备份相关常量
    private static final int MAX_LOCAL_RETRY = 3;
    private static final long LOCAL_RETRY_INTERVAL = 1000; // 1秒

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private WalletAuditRepository walletAuditRepository;  // 用于降级处理

    /**
     * 记录余额修改的审计日志
     * @param wallet 被修改的钱包
     * @param amount 变更金额（正数表示增加，负数表示减少）
     * @param reason 变更原因
     * @param performedBy 操作执行者
     */
    public void logBalanceModification(Wallet wallet, BigDecimal amount, String reason, String performedBy) {
        WalletAuditMessage message = createBaseAuditMessage(wallet);
        message.setAuditType("BALANCE_MODIFIED");
        message.setAmount(amount);
        message.setReason(reason);
        message.setPerformedBy(performedBy);
        message.setAdditionalInfo(String.format("Previous Balance: %s", wallet.getBalance()));

        sendAuditMessage(message);
        auditLogger.info("Balance modification audit message sent: {}, amount: {}",
                message.getMessageId(), amount);
    }

    /**
     * 记录待结算余额修改的审计日志
     * @param wallet 被修改的钱包
     * @param amount 变更金额
     * @param reason 变更原因
     */
    public void logPendingBalanceModification(Wallet wallet, BigDecimal amount, String reason) {
        WalletAuditMessage message = createBaseAuditMessage(wallet);
        message.setAuditType("PENDING_BALANCE_MODIFIED");
        message.setAmount(amount);
        message.setReason(reason);
        message.setPerformedBy("System");
        message.setAdditionalInfo(String.format("Previous Pending Balance: %s",
                wallet.getPendingBalance()));

        sendAuditMessage(message);
        auditLogger.info("Pending balance modification audit message sent: {}, amount: {}",
                message.getMessageId(), amount);
    }

    /**
     * 记录转账操作的审计日志
     * 会同时记录转出方和转入方的审计记录
     * @param fromWallet 转出方钱包
     * @param toWallet 转入方钱包
     * @param amount 转账金额
     * @param reason 转账原因
     */
    public void logTransfer(Wallet fromWallet, Wallet toWallet, BigDecimal amount, String reason) {
        // 记录转出方审计消息
        WalletAuditMessage outMessage = createBaseAuditMessage(fromWallet);
        outMessage.setAuditType("TRANSFER_OUT");
        outMessage.setAmount(amount.negate());
        outMessage.setReason(reason);
        outMessage.setPerformedBy("System");
        outMessage.setAdditionalInfo(String.format(
                "ToWallet: %d, ToUser: %d, Previous Balance: %s",
                toWallet.getId(), toWallet.getUser().getId(), fromWallet.getBalance()
        ));

        sendAuditMessage(outMessage);

        // 记录转入方审计消息
        WalletAuditMessage inMessage = createBaseAuditMessage(toWallet);
        inMessage.setAuditType("TRANSFER_IN");
        inMessage.setAmount(amount);
        inMessage.setReason(reason);
        inMessage.setPerformedBy("System");
        inMessage.setAdditionalInfo(String.format(
                "FromWallet: %d, FromUser: %d, Previous Balance: %s",
                fromWallet.getId(), fromWallet.getUser().getId(), toWallet.getBalance()
        ));

        sendAuditMessage(inMessage);

        auditLogger.info("Transfer audit messages sent: out={}, in={}, amount={}",
                outMessage.getMessageId(), inMessage.getMessageId(), amount);
    }

    /**
     * 记录钱包创建的审计日志
     * @param wallet 新创建的钱包
     */
    public void logWalletCreation(Wallet wallet) {
        WalletAuditMessage message = createBaseAuditMessage(wallet);
        message.setAuditType("WALLET_CREATED");
        message.setAmount(wallet.getBalance());
        message.setReason("New wallet creation");
        message.setPerformedBy("System");
        message.setAdditionalInfo(String.format("User: %d", wallet.getUser().getId()));

        sendAuditMessage(message);
        auditLogger.info("Wallet creation audit message sent: {}", message.getMessageId());
    }

    /**
     * 记录退款操作的审计日志
     * @param wallet 接收退款的钱包
     * @param amount 退款金额
     * @param reason 退款原因
     */
    public void logRefund(Wallet wallet, BigDecimal amount, String reason) {
        WalletAuditMessage message = createBaseAuditMessage(wallet);
        message.setAuditType("REFUND");
        message.setAmount(amount);
        message.setReason(reason);
        message.setPerformedBy("System");
        message.setAdditionalInfo(String.format("Previous Balance: %s", wallet.getBalance()));

        sendAuditMessage(message);
        auditLogger.info("Refund audit message sent: {}, amount: {}",
                message.getMessageId(), amount);
    }

    /**
     * 记录提现操作的审计日志
     * @param wallet 提现的钱包
     * @param amount 提现金额
     * @param withdrawalMethod 提现方式
     * @param accountInfo 收款账户信息
     * @param reason 提现原因
     */
    public void logWithdrawal(Wallet wallet, BigDecimal amount,
                              String withdrawalMethod, String accountInfo, String reason) {
        WalletAuditMessage message = createBaseAuditMessage(wallet);
        message.setAuditType("WITHDRAWAL");
        message.setAmount(amount.negate());
        message.setReason(reason);
        message.setPerformedBy("User");
        message.setAdditionalInfo(String.format(
                "Method: %s, Account: %s, Previous Balance: %s",
                withdrawalMethod, accountInfo, wallet.getBalance()
        ));

        sendAuditMessage(message);
        auditLogger.info("Withdrawal audit message sent: {}, amount: {}",
                message.getMessageId(), amount);
    }

    /**
     * 记录待结算余额扣除的审计日志
     * @param wallet 被扣除的钱包
     * @param amount 扣除金额
     * @param reason 扣除原因
     */
    public void logPendingBalanceDeduction(Wallet wallet, BigDecimal amount, String reason) {
        WalletAuditMessage message = createBaseAuditMessage(wallet);
        message.setAuditType("PENDING_BALANCE_DEDUCTED");
        message.setAmount(amount.negate());
        message.setReason(reason);
        message.setPerformedBy("System");
        message.setAdditionalInfo(String.format("Previous Pending Balance: %s",
                wallet.getPendingBalance()));

        sendAuditMessage(message);
        auditLogger.info("Pending balance deduction audit message sent: {}, amount: {}",
                message.getMessageId(), amount);
    }

    /**
     * 创建基础审计消息
     * 设置消息的基本属性
     */
    private WalletAuditMessage createBaseAuditMessage(Wallet wallet) {
        WalletAuditMessage message = new WalletAuditMessage();
        message.setWalletId(wallet.getId());
        message.setUserId(wallet.getUser().getId());
        message.setTimestamp(LocalDateTime.now());
        return message;
    }

    /**
     * 发送审计消息到消息队列
     * 包含重试机制和降级处理
     */
    private void sendAuditMessage(WalletAuditMessage message) {
        AtomicInteger retryCount = new AtomicInteger(0);
        boolean sent = false;

        // 尝试发送消息，最多重试3次
        while (!sent && retryCount.get() < MAX_LOCAL_RETRY) {
            try {
                rabbitTemplate.convertAndSend(AUDIT_EXCHANGE, AUDIT_ROUTING_KEY, message);
                sent = true;

                if (retryCount.get() > 0) {
                    auditLogger.info("Audit message sent successfully after {} retries: {}",
                            retryCount.get(), message.getMessageId());
                }
            } catch (AmqpException e) {
                int currentRetry = retryCount.incrementAndGet();
                auditLogger.warn("Failed to send audit message: {}, retry {}/{}",
                        message.getMessageId(), currentRetry, MAX_LOCAL_RETRY);

                if (currentRetry < MAX_LOCAL_RETRY) {
                    try {
                        //noinspection BusyWait
                        Thread.sleep(LOCAL_RETRY_INTERVAL);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        // 如果所有重试都失败，进行降级处理
        if (!sent) {
            handleAuditMessageError(message);
        }
    }

    /**
     * 处理审计消息发送失败的情况
     * 实现降级策略，直接写入本地数据库
     */
    private void handleAuditMessageError(WalletAuditMessage message) {
        try {
            auditLogger.warn("Falling back to local storage for audit message: {}",
                    message.getMessageId());

            // 创建审计记录并保存到本地数据库
            WalletAudit audit = getWalletAudit(message);

            walletAuditRepository.save(audit);

            auditLogger.info("Audit message saved locally: {}", message.getMessageId());
        } catch (Exception e) {
            auditLogger.error("Failed to save audit message locally: {}, error: {}",
                    message.getMessageId(), e.getMessage(), e);
        }
    }

    @NotNull
    private static WalletAudit getWalletAudit(WalletAuditMessage message) {
        WalletAudit audit = new WalletAudit();
        audit.setWalletId(message.getWalletId());
        audit.setUserId(message.getUserId());
        audit.setAction(message.getAuditType());
        audit.setAmount(message.getAmount());
        audit.setReason(message.getReason());
        audit.setPerformedBy(message.getPerformedBy());
        audit.setTimestamp(message.getTimestamp());
        audit.setAdditionalInfo(String.format("%s [LOCAL_FALLBACK]",
                message.getAdditionalInfo()));
        return audit;
    }
}