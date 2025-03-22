package com.server.anki.wallet.service;

import com.server.anki.config.RabbitMQConfig;
import com.server.anki.message.MessageType;
import com.server.anki.message.service.MessageService;
import com.server.anki.user.User;
import com.server.anki.user.UserRepository;
import com.server.anki.user.enums.UserVerificationStatus;
import com.server.anki.wallet.WalletInfoDTO;
import com.server.anki.wallet.entity.Wallet;
import com.server.anki.wallet.entity.WithdrawalOrder;
import com.server.anki.wallet.message.*;
import com.server.anki.wallet.repository.WalletRepository;
import com.server.anki.wallet.repository.WithdrawalOrderRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 钱包核心服务类
 * 负责处理钱包相关的核心业务逻辑，并将具体操作委托给消息队列异步处理
 */
@Service
public class WalletService {
    private static final Logger logger = LoggerFactory.getLogger(WalletService.class);

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private WalletAuditService walletAuditService;

    @Autowired
    @Lazy
    private MessageService messageService;

    @Autowired
    private WithdrawalOrderRepository withdrawalOrderRepository;

    /**
     * 系统启动时初始化已验证用户的钱包
     */
    @PostConstruct
    public void initializeWalletsForVerifiedUsers() {
        logger.info("开始初始化已验证用户的钱包");
        try {
            // 使用分页查询获取所有已验证用户
            List<User> verifiedUsers = new ArrayList<>();
            int pageSize = 100;
            int pageNumber = 0;
            Page<User> userPage;

            do {
                Pageable pageable = PageRequest.of(pageNumber, pageSize);
                userPage = userRepository.findByUserVerificationStatus(UserVerificationStatus.VERIFIED, pageable);
                verifiedUsers.addAll(userPage.getContent());
                pageNumber++;
            } while (userPage.hasNext());

            for (User user : verifiedUsers) {
                // 如果用户没有钱包，发送初始化消息
                if (walletRepository.findByUser(user).isEmpty()) {
                    WalletInitMessage message = WalletMessageUtils.createInitMessage(
                            user.getId(),
                            user.getUsername(),
                            user.getUserVerificationStatus().name()
                    );
                    sendWalletMessage(message);
                }
            }

            logger.info("钱包初始化消息已发送，共 {} 个用户", verifiedUsers.size());
        } catch (Exception e) {
            logger.error("钱包初始化过程发生错误: {}", e.getMessage(), e);
        }
    }

    /**
     * 转账操作 - 返回转账结果
     * 修改：添加返回值表示转账操作是否成功发起
     */
    @Transactional(readOnly = true)
    public boolean transferFunds(User fromUser, User toUser, BigDecimal amount, String reason) {
        logger.info("发起转账请求: from={}, to={}, amount={}",
                fromUser.getId(), toUser.getId(), amount);

        try {
            // 创建转账消息
            TransferMessage message = WalletMessageUtils.createTransferMessage(
                    fromUser.getId(),
                    toUser.getId(),
                    amount,
                    reason,
                    TransferMessage.TransferType.NORMAL
            );

            sendWalletMessage(message);
            return true;  // 消息发送成功，转账请求已成功发起
        } catch (Exception e) {
            logger.error("转账请求发送失败: {}", e.getMessage(), e);
            return false;  // 消息发送失败
        }
    }

    /**
     * 检查用户余额是否足够
     * 新增：验证用户余额是否足够支付特定金额
     */
    @Transactional(readOnly = true)
    public boolean hasSufficientFunds(User user, BigDecimal amount) {
        BigDecimal balance = getBalance(user);
        return balance.compareTo(amount) >= 0;
    }

    /**
     * 检查用户余额是否足够 - 根据用户ID
     * 新增：支持传入用户ID进行余额检查
     */
    @Transactional(readOnly = true)
    public boolean hasSufficientFunds(Long userId, BigDecimal amount) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在: " + userId));
        return hasSufficientFunds(user, amount);
    }

    /**
     * 处理提现请求
     */
    @Transactional(readOnly = true)
    public void processWithdrawal(User user, BigDecimal amount,
                                  String withdrawalMethod, String accountInfo, String accountName) {
        logger.info("发起提现请求: userId={}, amount={}, method={}",
                user.getId(), amount, withdrawalMethod);

        WithdrawalMessage message = WalletMessageUtils.createWithdrawalMessage(
                user.getId(),
                amount,
                withdrawalMethod,
                accountInfo,
                accountName  // 传递真实姓名
        );

        sendWalletMessage(message);
    }

    /**
     * 增加待结算余额
     */
    @Transactional(readOnly = true)
    public void addPendingFunds(User user, BigDecimal amount, String reason) {
        logger.info("添加待结算金额: userId={}, amount={}", user.getId(), amount);

        BalanceChangeMessage message = WalletMessageUtils.createBalanceChangeMessage(
                user.getId(),
                amount,
                reason,
                BalanceChangeMessage.BalanceChangeType.PENDING
        );

        sendWalletMessage(message);
    }

    /**
     * 获取用户可用余额
     */
    @Transactional(readOnly = true)
    public BigDecimal getBalance(User user) {
        logger.debug("获取用户 {} 的可用余额", user.getId());
        return walletRepository.findByUser(user)
                .map(Wallet::getBalance)
                .orElse(BigDecimal.ZERO);
    }

    /**
     * 获取用户待结算余额
     */
    @Transactional(readOnly = true)
    public BigDecimal getPendingBalance(User user) {
        logger.debug("获取用户 {} 的待结算余额", user.getId());
        return walletRepository.findByUser(user)
                .map(Wallet::getPendingBalance)
                .orElse(BigDecimal.ZERO);
    }

    /**
     * 获取用户钱包总余额
     */
    @Transactional(readOnly = true)
    public BigDecimal getTotalBalance(User user) {
        logger.debug("获取用户 {} 的总余额", user.getId());
        return walletRepository.findByUser(user)
                .map(wallet -> wallet.getBalance().add(wallet.getPendingBalance()))
                .orElse(BigDecimal.ZERO);
    }


    /**
     * 获取钱包详细信息
     */
    @Transactional(readOnly = true)
    public WalletInfoDTO getWalletInfo(User user) {
        logger.debug("获取用户 {} 的钱包详细信息", user.getId());

        Wallet wallet = walletRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("钱包不存在"));

        return new WalletInfoDTO(
                wallet.getBalance().add(wallet.getPendingBalance()),
                wallet.getBalance(),
                wallet.getPendingBalance(),
                user.getId(),
                user.getUsername()
        );
    }

    /**
     * 处理提现失败退款
     * 当提现到支付宝失败时，将资金退回用户钱包
     *
     * @param withdrawalOrder 提现订单对象
     * @return 退款处理结果
     */
    @Transactional
    public boolean refundFailedWithdrawal(WithdrawalOrder withdrawalOrder) {
        logger.info("处理提现失败退款: 订单号={}, 金额={}, 用户ID={}",
                withdrawalOrder.getOrderNumber(),
                withdrawalOrder.getAmount(),
                withdrawalOrder.getUser().getId());

        try {
            // 1. 获取用户钱包
            User user = withdrawalOrder.getUser();
            Wallet wallet = walletRepository.findByUser(user)
                    .orElseThrow(() -> new RuntimeException("找不到用户钱包，用户ID: " + user.getId()));

            // 2. 检查提现订单状态
            if (withdrawalOrder.getStatus() != WithdrawalOrder.WithdrawalStatus.FAILED) {
                logger.warn("只能退款失败状态的提现订单: {}, 当前状态: {}",
                        withdrawalOrder.getOrderNumber(), withdrawalOrder.getStatus());
                return false;
            }

            // 3. 创建余额变更消息
            BalanceChangeMessage message = WalletMessageUtils.createBalanceChangeMessage(
                    user.getId(),
                    withdrawalOrder.getAmount(),  // 退还原始提现金额
                    "提现失败退款: " + withdrawalOrder.getOrderNumber(),
                    BalanceChangeMessage.BalanceChangeType.AVAILABLE
            );

            // 4. 发送余额变更消息进行异步处理
            sendWalletMessage(message);

            // 5. 记录审计日志
            walletAuditService.logBalanceModification(
                    wallet,
                    withdrawalOrder.getAmount(),
                    "提现失败退款，订单号: " + withdrawalOrder.getOrderNumber(),
                    "System"
            );

            // 6. 发送通知给用户
            try {
                messageService.sendMessage(
                        user,
                        String.format("您的提现申请(金额: %.2f元)处理失败，资金已退回您的钱包",
                                withdrawalOrder.getAmount()),
                        MessageType.WALLET_REFUND,
                        null
                );
            } catch (Exception e) {
                // 通知发送失败不影响退款流程
                logger.error("发送提现失败通知时出错: {}", e.getMessage(), e);
            }

            logger.info("提现失败退款处理完成: {}", withdrawalOrder.getOrderNumber());
            return true;
        } catch (Exception e) {
            logger.error("处理提现失败退款时出错: {}", e.getMessage(), e);
            throw new RuntimeException("处理提现失败退款失败", e);
        }
    }
    /**
     * 获取所有钱包信息（分页版本）
     *
     * @param pageable 分页参数
     * @return 分页后的钱包信息列表
     */
    @Transactional(readOnly = true)
    public Page<WalletInfoDTO> getAllWallets(Pageable pageable) {
        logger.debug("获取所有钱包信息，页码: {}, 每页大小: {}",
                pageable.getPageNumber(), pageable.getPageSize());

        // 使用分页查询获取钱包数据
        Page<Wallet> walletPage = walletRepository.findAll(pageable);

        // 将Wallet实体转换为DTO
        List<WalletInfoDTO> walletInfoDTOs = walletPage.getContent().stream()
                .map(wallet -> new WalletInfoDTO(
                        wallet.getBalance().add(wallet.getPendingBalance()),
                        wallet.getBalance(),
                        wallet.getPendingBalance(),
                        wallet.getUser().getId(),
                        wallet.getUser().getUsername()
                ))
                .collect(Collectors.toList());

        // 创建并返回分页DTO结果
        return new PageImpl<>(
                walletInfoDTOs,
                pageable,
                walletPage.getTotalElements()
        );
    }

    /**
     * 发送钱包相关消息到消息队列
     */
    public void sendWalletMessage(BaseWalletMessage message) {
        String routingKey = RabbitMQConfig.getWalletRoutingKey(message.getMessageType());

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.WALLET_EXCHANGE,
                routingKey,
                message
        );

        logger.info("钱包消息已发送: type={}, messageId={}",
                message.getMessageType(),
                message.getMessageId());
    }

    /**
     * 获取用户的提现历史记录
     * @param user 用户对象
     * @return 用户的提现订单列表，按时间倒序排列
     */
    @Transactional(readOnly = true)
    public List<WithdrawalOrder> getUserWithdrawalHistory(User user) {
        logger.debug("获取用户 {} 的提现历史记录", user.getId());
        return withdrawalOrderRepository.findByUserOrderByCreatedTimeDesc(user);
    }

    /**
     * 根据订单号获取提现订单
     * @param orderNumber 提现订单号
     * @return 提现订单（如果存在）
     */
    @Transactional(readOnly = true)
    public Optional<WithdrawalOrder> getWithdrawalOrder(String orderNumber) {
        logger.debug("根据订单号查询提现订单: {}", orderNumber);
        return withdrawalOrderRepository.findByOrderNumber(orderNumber);
    }
}