package com.server.anki.wallet.service;

import com.server.anki.alipay.AlipayService;
import com.server.anki.user.User;
import com.server.anki.user.UserRepository;
import com.server.anki.wallet.entity.Wallet;
import com.server.anki.wallet.entity.WithdrawalOrder;
import com.server.anki.wallet.repository.WalletRepository;
import com.server.anki.wallet.repository.WithdrawalOrderRepository;
import com.server.anki.wallet.exception.InsufficientFundsException;
import com.server.anki.wallet.message.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Random;

/**
 * 钱包事务服务类
 * 负责处理具体的钱包操作，确保事务性和数据一致性
 */
@Service
public class WalletTransactionService {
    private static final Logger logger = LoggerFactory.getLogger(WalletTransactionService.class);

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletAuditService walletAuditService;

    @Autowired
    private AlipayService alipayService;

    @Autowired
    private WithdrawalOrderRepository withdrawalOrderRepository;

    /**
     * 创建钱包
     */
    @Transactional
    public Wallet handleWalletInit(WalletInitMessage message) {
        logger.info("处理钱包初始化: userId={}", message.getUserId());

        User user = userRepository.findById(message.getUserId())
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // 检查钱包是否已存在
        if (walletRepository.findByUser(user).isPresent()) {
            logger.info("用户钱包已存在: {}", user.getId());
            return walletRepository.findByUser(user).get();
        }

        Wallet newWallet = new Wallet();
        newWallet.setUser(user);
        newWallet.setBalance(BigDecimal.ZERO);
        newWallet.setPendingBalance(BigDecimal.ZERO);

        Wallet savedWallet = walletRepository.save(newWallet);
        walletAuditService.logWalletCreation(savedWallet);

        logger.info("钱包创建成功: userId={}", user.getId());
        return savedWallet;
    }

    /**
     * 处理转账
     */
    @Transactional
    public void handleTransfer(TransferMessage message) {
        logger.info("处理转账操作: messageId={}", message.getMessageId());

        User fromUser = userRepository.findById(message.getFromUserId())
                .orElseThrow(() -> new RuntimeException("转出用户不存在"));
        User toUser = userRepository.findById(message.getToUserId())
                .orElseThrow(() -> new RuntimeException("转入用户不存在"));

        Wallet fromWallet = getOrCreateWallet(fromUser);
        Wallet toWallet = getOrCreateWallet(toUser);

        // 检查余额
        if (fromWallet.getBalance().compareTo(message.getAmount()) < 0) {
            try {
                throw new InsufficientFundsException("余额不足");
            } catch (InsufficientFundsException e) {
                throw new RuntimeException(e);
            }
        }

        // 执行转账
        fromWallet.setBalance(fromWallet.getBalance().subtract(message.getAmount()));
        toWallet.setBalance(toWallet.getBalance().add(message.getAmount()));

        walletRepository.save(fromWallet);
        walletRepository.save(toWallet);

        // 记录审计日志
        walletAuditService.logTransfer(fromWallet, toWallet,
                message.getAmount(), message.getReason());

        logger.info("转账处理完成: messageId={}", message.getMessageId());
    }

    /**
     * 处理提现
     */
    @Transactional
    public void handleWithdrawal(WithdrawalMessage message) {
        logger.info("处理提现请求: messageId={}", message.getMessageId());

        User user = userRepository.findById(message.getUserId())
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        Wallet wallet = getOrCreateWallet(user);

        // 检查余额
        if (wallet.getBalance().compareTo(message.getAmount()) < 0) {
            try {
                throw new InsufficientFundsException("余额不足以完成提现");
            } catch (InsufficientFundsException e) {
                throw new RuntimeException(e);
            }
        }

        // 创建提现订单
        WithdrawalOrder withdrawalOrder = new WithdrawalOrder();
        withdrawalOrder.setUser(user);
        withdrawalOrder.setOrderNumber(message.getWithdrawalOrderNo() != null ?
                message.getWithdrawalOrderNo() : generateWithdrawalOrderNumber());
        withdrawalOrder.setAmount(message.getAmount());
        withdrawalOrder.setWithdrawalMethod(message.getWithdrawalMethod());
        withdrawalOrder.setAccountInfo(message.getAccountInfo());
        withdrawalOrder.setStatus(WithdrawalOrder.WithdrawalStatus.PROCESSING);
        withdrawalOrder.setProcessedTime(LocalDateTime.now());

        withdrawalOrderRepository.save(withdrawalOrder);

        try {
            // 调用支付宝提现API
            if ("ALIPAY".equals(message.getWithdrawalMethod())) {
                // 通过支付宝进行提现操作
                String orderNo = (message.getWithdrawalOrderNo() != null)
                        ? message.getWithdrawalOrderNo()
                        : generateWithdrawalOrderNumber();
                withdrawalOrder.setOrderNumber(orderNo);

                // 调用支付宝提现时传入统一生成的订单号
                AlipayService.AlipayWithdrawalResponse response = alipayService.withdrawToAlipay(
                        user.getId(), message.getAmount(), message.getAccountInfo(),
                        message.getAccountName(), orderNo);

                if (response.isSuccess()) {
                    // 提现请求已受理，更新订单状态
                    withdrawalOrder.setAlipayOrderId(response.getOrderId());

                    // 根据支付宝返回状态判断
                    if ("SUCCESS".equals(response.getStatus())) {
                        withdrawalOrder.setStatus(WithdrawalOrder.WithdrawalStatus.SUCCESS);
                        withdrawalOrder.setCompletedTime(LocalDateTime.now());

                        // 扣减余额
                        wallet.setBalance(wallet.getBalance().subtract(message.getAmount()));
                        walletRepository.save(wallet);
                    } else {
                        // 处理中状态，保持订单状态不变
                        logger.info("提现请求已提交到支付宝，等待结果，订单号: {}",
                                withdrawalOrder.getOrderNumber());
                    }
                } else {
                    // 提现请求失败
                    withdrawalOrder.setStatus(WithdrawalOrder.WithdrawalStatus.FAILED);

                    // 处理错误消息，确保不超过字段长度
                    String errorMsg = response.getErrorMessage();
                    if (errorMsg != null && errorMsg.length() > 255) {
                        errorMsg = errorMsg.substring(0, 252) + "...";
                    }
                    withdrawalOrder.setErrorMessage(errorMsg);
                    withdrawalOrder.setCompletedTime(LocalDateTime.now());
                    logger.error("提现失败: {}", errorMsg);
                }
            } else {
                // 不支持的提现方式
                withdrawalOrder.setStatus(WithdrawalOrder.WithdrawalStatus.FAILED);
                withdrawalOrder.setErrorMessage("不支持的提现方式: " + message.getWithdrawalMethod());
                withdrawalOrder.setCompletedTime(LocalDateTime.now());
                logger.error("不支持的提现方式: {}", message.getWithdrawalMethod());
            }

            // 保存更新的订单状态
            withdrawalOrderRepository.save(withdrawalOrder);

            // 记录审计日志
            if (withdrawalOrder.getStatus() == WithdrawalOrder.WithdrawalStatus.SUCCESS) {
                walletAuditService.logWithdrawal(wallet, message.getAmount(),
                        message.getWithdrawalMethod(), message.getAccountInfo(),
                        "用户提现");
            }

        } catch (Exception e) {
            // 处理提现过程中的异常
            withdrawalOrder.setStatus(WithdrawalOrder.WithdrawalStatus.FAILED);

            // 提取并截断错误信息
            String errorMsg = "提现处理异常: ";
            if (e.getMessage() != null) {
                errorMsg += truncateErrorMessage(e.getMessage(), 230);
            } else {
                errorMsg += e.getClass().getSimpleName();
            }

            withdrawalOrder.setErrorMessage(errorMsg);
            withdrawalOrder.setCompletedTime(LocalDateTime.now());
            withdrawalOrderRepository.save(withdrawalOrder);

            logger.error("提现处理异常: {}", e.getMessage(), e);
            throw new RuntimeException("提现处理失败", e);
        }

        logger.info("提现处理完成: messageId={}, 状态={}",
                message.getMessageId(), withdrawalOrder.getStatus());
    }

    /**
     * 截断错误信息，确保不超过数据库字段长度
     */
    private String truncateErrorMessage(String message, int maxLength) {
        if (message == null) return null;
        if (message.length() <= maxLength) return message;
        return message.substring(0, maxLength) + "...";
    }

    /**
     * 处理余额变更
     */
    @Transactional
    public void handleBalanceChange(BalanceChangeMessage message) {
        logger.info("处理余额变更: messageId={}", message.getMessageId());

        User user = userRepository.findById(message.getUserId())
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        Wallet wallet = getOrCreateWallet(user);

        if (message.getChangeType() == BalanceChangeMessage.BalanceChangeType.PENDING) {
            wallet.setPendingBalance(wallet.getPendingBalance().add(message.getAmount()));
            walletAuditService.logPendingBalanceModification(wallet,
                    message.getAmount(), message.getReason());
        } else {
            wallet.setBalance(wallet.getBalance().add(message.getAmount()));
            walletAuditService.logBalanceModification(wallet,
                    message.getAmount(), message.getReason(), "System");
        }

        walletRepository.save(wallet);
        logger.info("余额变更完成: messageId={}", message.getMessageId());
    }

    /**
     * 获取或创建钱包
     */
    private Wallet getOrCreateWallet(User user) {
        return walletRepository.findByUser(user)
                .orElseGet(() -> {
                    Wallet newWallet = new Wallet();
                    newWallet.setUser(user);
                    newWallet.setBalance(BigDecimal.ZERO);
                    newWallet.setPendingBalance(BigDecimal.ZERO);
                    return walletRepository.save(newWallet);
                });
    }

    /**
     * 生成提现订单号
     */
    private String generateWithdrawalOrderNumber() {
        return "WD" + System.currentTimeMillis() +
                String.format("%04d", new Random().nextInt(10000));
    }
}