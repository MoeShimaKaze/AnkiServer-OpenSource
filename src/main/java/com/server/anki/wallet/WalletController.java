package com.server.anki.wallet;

import com.server.anki.auth.AuthenticationService;
import com.server.anki.email.EmailService;
import com.server.anki.user.User;
import com.server.anki.user.UserService;
import com.server.anki.user.enums.UserVerificationStatus;
import com.server.anki.wallet.entity.WithdrawalOrder;
import com.server.anki.wallet.request.TransferRequest;
import com.server.anki.wallet.request.WithdrawalRequest;
import com.server.anki.wallet.service.WalletService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private static final Logger logger = LoggerFactory.getLogger(WalletController.class);

    @Autowired
    private WalletService walletService;

    @Autowired
    private UserService userService;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private EmailService emailService;

    /**
     * 获取钱包信息
     * 同步读取操作，直接返回结果
     */
    @GetMapping("/balance")
    public ResponseEntity<?> getWalletInfo(HttpServletRequest request, HttpServletResponse response) {
        logger.info("收到获取钱包信息的请求");
        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            logger.warn("未授权的访问尝试");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("请先登录后再查看钱包信息");
        }

        try {
            logger.debug("正在获取用户 [{}] 的钱包信息", user.getId());
            WalletInfoDTO walletInfo = walletService.getWalletInfo(user);
            logger.info("成功获取用户 [{}] 的钱包信息", user.getId());
            return ResponseEntity.ok(walletInfo);
        } catch (Exception e) {
            logger.error("获取钱包信息时发生错误: {}", e.getMessage());

            // 钱包不存在的特定处理
            if (e.getMessage() != null && e.getMessage().contains("钱包不存在")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .header("X-Wallet-Error", "WALLET_NOT_EXIST")
                        .body(Map.of(
                                "success", false,
                                "errorCode", "WALLET_NOT_EXIST",
                                "message", "钱包不存在，请先完成实名认证"
                        ));
            }

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("获取钱包信息失败，请稍后重试");
        }
    }

    /**
     * 获取系统账户余额
     * 管理员专用接口
     */
    @GetMapping("/system-account/balance")
    public ResponseEntity<?> getSystemAccountBalance(HttpServletRequest request,
                                                     HttpServletResponse response) {
        logger.info("收到获取系统账户余额的请求");
        User user = authenticationService.getAuthenticatedUser(request, response);

        if (user == null) {
            logger.warn("未登录用户尝试访问系统账户余额");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
        }

        if (!userService.isAdminUser(user)) {
            logger.warn("用户 [{}] 无管理员权限，尝试访问系统账户余额", user.getId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("没有管理员权限");
        }

        try {
            WalletInfoDTO systemWallet = walletService.getWalletInfo(
                    userService.getOrCreateSystemAccount());
            logger.info("成功获取系统账户余额");
            return ResponseEntity.ok(systemWallet);
        } catch (Exception e) {
            logger.error("获取系统账户余额时发生错误: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("获取系统账户余额失败，请稍后重试");
        }
    }

    /**
     * 获取所有钱包信息（支持分页）
     * 管理员专用接口
     *
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @param sortBy 排序字段（可选）
     * @param direction 排序方向（可选，ASC或DESC）
     * @return 分页的钱包信息
     */
    @GetMapping("/all")
    public ResponseEntity<?> getAllWallets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "ASC") String direction,
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到获取所有钱包信息的请求，页码: {}, 每页大小: {}", page, size);
        User user = authenticationService.getAuthenticatedUser(request, response);

        if (user == null) {
            logger.warn("未登录用户尝试访问所有钱包信息");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
        }

        if (!userService.isAdminUser(user)) {
            logger.warn("用户 [{}] 无管理员权限，尝试访问所有钱包信息", user.getId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("没有管理员权限");
        }

        try {
            // 创建分页请求
            Pageable pageable;

            if (sortBy != null && !sortBy.isEmpty()) {
                Sort.Direction sortDirection = "DESC".equalsIgnoreCase(direction) ?
                        Sort.Direction.DESC : Sort.Direction.ASC;
                pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));
                logger.debug("使用排序参数: {}, 方向: {}", sortBy, direction);
            } else {
                pageable = PageRequest.of(page, size);
            }

            // 查询钱包信息
            Page<WalletInfoDTO> walletPage = walletService.getAllWallets(pageable);
            logger.info("成功获取钱包信息, 总条数: {}, 总页数: {}",
                    walletPage.getTotalElements(), walletPage.getTotalPages());

            return ResponseEntity.ok(walletPage);
        } catch (Exception e) {
            logger.error("获取所有钱包信息时发生错误: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("获取钱包信息失败，请稍后重试");
        }
    }

    /**
     * 检查用户提现条件
     */
    @GetMapping("/withdraw/check")
    public ResponseEntity<?> checkWithdrawalConditions(HttpServletRequest request,
                                                       HttpServletResponse response) {
        logger.info("收到检查提现条件请求");
        User user = authenticationService.getAuthenticatedUser(request, response);

        if (user == null) {
            logger.warn("未登录用户尝试检查提现条件");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录后再操作");
        }

        // 检查钱包状态
        try {
            WalletInfoDTO walletInfo = walletService.getWalletInfo(user);

            // 检查是否绑定支付宝
            boolean isBoundAlipay = user.getAlipayUserId() != null && !user.getAlipayUserId().isEmpty();

            // 构建响应
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("canWithdraw", isBoundAlipay);
            responseData.put("availableBalance", walletInfo.getAvailableBalance());

            if (!isBoundAlipay) {
                responseData.put("message", "请先绑定支付宝账户后再进行提现");
            }

            return ResponseEntity.ok(responseData);
        } catch (Exception e) {
            logger.error("检查提现条件失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("检查提现条件失败: " + e.getMessage());
        }
    }

    /**
     * 发送提现验证码
     */
    @PostMapping("/withdraw/send-code")
    public ResponseEntity<?> sendWithdrawalVerificationCode(HttpServletRequest request,
                                                            HttpServletResponse response) {
        logger.info("收到发送提现验证码请求");
        User user = authenticationService.getAuthenticatedUser(request, response);

        if (user == null) {
            logger.warn("未登录用户尝试获取提现验证码");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录后再操作");
        }

        // 检查用户是否绑定了支付宝账号
        if (user.getAlipayUserId() == null || user.getAlipayUserId().isEmpty()) {
            logger.warn("未绑定支付宝账户的用户尝试获取提现验证码，用户ID: {}", user.getId());
            return ResponseEntity.badRequest().body("请先绑定支付宝账户再进行提现");
        }

        // 检查用户是否有设置邮箱
        if (user.getEmail() == null || user.getEmail().isEmpty()) {
            logger.warn("未设置邮箱的用户尝试获取提现验证码，用户ID: {}", user.getId());
            return ResponseEntity.badRequest().body("请先设置邮箱后再进行提现");
        }

        // 发送验证码
        String result = emailService.sendVerificationCode(user.getEmail());
        logger.info("提现验证码已发送，用户ID: {}, 邮箱: {}", user.getId(), user.getEmail());
        return ResponseEntity.ok(result);
    }


    /**
     * 处理提现请求
     */
    @PostMapping("/withdraw")
    public ResponseEntity<?> withdrawFunds(@RequestBody WithdrawalRequest withdrawalRequest,
                                           HttpServletRequest request,
                                           HttpServletResponse response) {
        logger.info("收到提现请求");
        User user = authenticationService.getAuthenticatedUser(request, response);

        if (user == null) {
            logger.warn("未登录用户尝试提现");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录后再操作");
        }

        try {
            // 1. 检查用户是否已绑定支付宝账号
            if (user.getAlipayUserId() == null || user.getAlipayUserId().isEmpty()) {
                logger.warn("未绑定支付宝账户的用户尝试提现，用户ID: {}", user.getId());
                return ResponseEntity.badRequest().body("请先绑定支付宝账户再进行提现");
            }

            // 2. 检查用户是否已实名认证
            if (user.getUserVerificationStatus() != UserVerificationStatus.VERIFIED) {
                logger.warn("未实名认证的用户尝试提现，用户ID: {}", user.getId());
                return ResponseEntity.badRequest().body("请先完成实名认证后再进行提现");
            }

            // 3. 从personalInfo中获取真实姓名
            Map<String, Object> personalInfo = user.getPersonalInfo();
            String realName = (String) personalInfo.get("real_name");

            if (realName == null || realName.isEmpty()) {
                logger.warn("用户实名信息不完整，无法提现，用户ID: {}", user.getId());
                return ResponseEntity.badRequest().body("您的实名信息不完整，请重新进行实名认证");
            }

            // 4. 检查提现方式是否为支付宝
            if (!"ALIPAY".equals(withdrawalRequest.getWithdrawalMethod())) {
                logger.warn("用户尝试使用非支付宝方式提现，用户ID: {}", user.getId());
                return ResponseEntity.badRequest().body("目前仅支持提现到支付宝账户");
            }

            // 5. 验证邮箱验证码
            if (withdrawalRequest.getVerificationCode() == null || withdrawalRequest.getVerificationCode().isEmpty()) {
                logger.warn("用户提现请求缺少验证码，用户ID: {}", user.getId());
                return ResponseEntity.badRequest().body("请输入验证码");
            }

            if (!emailService.isEmailVerified(user.getEmail(), withdrawalRequest.getVerificationCode())) {
                logger.warn("用户提现验证码验证失败，用户ID: {}", user.getId());
                return ResponseEntity.badRequest().body("验证码无效或已过期");
            }

            // 6. 使用用户绑定的支付宝账号进行提现
            withdrawalRequest.setAccountInfo(user.getAlipayUserId());

            // 7. 基本验证
            if (withdrawalRequest.getAmount() == null ||
                    withdrawalRequest.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.badRequest().body("提现金额必须大于0");
            }

            // 发送提现处理消息
            walletService.processWithdrawal(
                    user,
                    withdrawalRequest.getAmount(),
                    withdrawalRequest.getWithdrawalMethod(),
                    withdrawalRequest.getAccountInfo(),
                    realName  // 添加真实姓名参数
            );

            logger.info("用户 [{}] 的提现请求已受理，金额: {}",
                    user.getId(), withdrawalRequest.getAmount());

            // 返回更加友好的响应
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("success", true);
            responseData.put("message", "提现申请已受理，请等待处理结果通知");
            responseData.put("orderTime", LocalDateTime.now());
            responseData.put("amount", withdrawalRequest.getAmount());
            responseData.put("estimatedTime", "预计1-3个工作日内到账");

            return ResponseEntity.ok(responseData);
        } catch (Exception e) {
            logger.error("处理提现请求时发生错误: {}", e.getMessage(), e);

            // 返回更友好的错误信息
            String errorMsg = "提现申请提交失败";
            if (e.getMessage() != null) {
                // 提取关键错误信息
                if (e.getMessage().contains("余额不足")) {
                    errorMsg = "您的余额不足，无法完成提现操作";
                } else if (e.getMessage().contains("支付宝网关超时")) {
                    errorMsg = "支付宝服务暂时不可用，请稍后再试";
                } else {
                    errorMsg = "提现申请提交失败，请稍后再试";
                }
            }

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "message", errorMsg,
                            "canRetry", true
                    ));
        }
    }

    /**
     * 转账操作
     * 通过异步消息队列处理用户间转账请求
     *
     * @param transferRequest 转账请求参数对象
     * @param request HTTP请求对象
     * @param response HTTP响应对象
     * @return 处理结果响应体
     */
    @PostMapping("/transfer")
    public ResponseEntity<?> transferFunds(@RequestBody TransferRequest transferRequest,
                                           HttpServletRequest request,
                                           HttpServletResponse response) {
        logger.info("收到转账请求");
        User fromUser = authenticationService.getAuthenticatedUser(request, response);

        if (fromUser == null) {
            logger.warn("未登录用户尝试转账");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录后再操作");
        }

        // 参数验证
        if (transferRequest.getToUserId() == null ||
                transferRequest.getAmount() == null ||
                transferRequest.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            logger.warn("转账请求参数无效: {}", transferRequest);
            return ResponseEntity.badRequest().body("转账参数无效");
        }

        try {
            // 获取接收方用户信息
            User toUser = userService.getUserById(transferRequest.getToUserId());

            // 检查是否可以转账给自己
            if (fromUser.getId().equals(toUser.getId())) {
                logger.warn("用户 [{}] 尝试转账给自己", fromUser.getId());
                return ResponseEntity.badRequest().body("不能转账给自己");
            }

            // 发送转账处理消息到消息队列
            walletService.transferFunds(
                    fromUser,
                    toUser,
                    transferRequest.getAmount(),
                    transferRequest.getReason()
            );

            logger.info("用户 [{}] 向用户 [{}] 的转账请求已受理，金额: {}",
                    fromUser.getId(), toUser.getId(), transferRequest.getAmount());

            return ResponseEntity.ok("转账请求已受理，请等待处理结果通知");
        } catch (Exception e) {
            logger.error("处理转账请求时发生错误: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("转账申请提交失败，请稍后重试");
        }
    }

    /**
     * 获取用户提现订单历史
     */
    @GetMapping("/withdrawal/history")
    public ResponseEntity<?> getWithdrawalHistory(
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到获取用户提现历史记录请求");
        User user = authenticationService.getAuthenticatedUser(request, response);

        if (user == null) {
            logger.warn("未登录用户尝试获取提现历史");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录后再操作");
        }

        try {
            List<WithdrawalOrder> withdrawalOrders =
                    walletService.getUserWithdrawalHistory(user);

            // 转换为前端友好的格式
            List<Map<String, Object>> responseData = withdrawalOrders.stream()
                    .map(order -> {
                        Map<String, Object> orderData = new HashMap<>();
                        orderData.put("id", order.getId());
                        orderData.put("orderNumber", order.getOrderNumber());
                        orderData.put("amount", order.getAmount());
                        orderData.put("withdrawalMethod", order.getWithdrawalMethod());
                        orderData.put("status", order.getStatus().name());
                        orderData.put("createdTime", order.getCreatedTime());
                        orderData.put("processedTime", order.getProcessedTime());
                        orderData.put("completedTime", order.getCompletedTime());
                        orderData.put("errorMessage", order.getErrorMessage());
                        // 添加更友好的状态描述
                        orderData.put("statusText", getWithdrawalStatusText(order.getStatus()));
                        return orderData;
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(responseData);
        } catch (Exception e) {
            logger.error("获取提现历史记录失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("获取提现历史记录失败: " + e.getMessage());
        }
    }

    // 私有方法：获取提现状态的友好文本描述
    private String getWithdrawalStatusText(WithdrawalOrder.WithdrawalStatus status) {
        return switch (status) {
            case PROCESSING -> "处理中";
            case SUCCESS -> "提现成功";
            case FAILED -> "提现失败";
        };
    }

    /**
     * 查询单个提现订单状态
     */
    @GetMapping("/withdrawal/status/{orderNumber}")
    public ResponseEntity<?> getWithdrawalStatus(
            @PathVariable String orderNumber,
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到查询提现订单状态请求，订单号: {}", orderNumber);
        User user = authenticationService.getAuthenticatedUser(request, response);

        if (user == null) {
            logger.warn("未登录用户尝试查询提现状态");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录后再操作");
        }

        try {
            Optional<WithdrawalOrder> orderOpt = walletService.getWithdrawalOrder(orderNumber);

            if (orderOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("提现订单不存在");
            }

            WithdrawalOrder order = orderOpt.get();

            // 验证是否为当前用户的订单
            if (!order.getUser().getId().equals(user.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("无权查看此订单");
            }

            Map<String, Object> orderData = new HashMap<>();
            orderData.put("id", order.getId());
            orderData.put("orderNumber", order.getOrderNumber());
            orderData.put("amount", order.getAmount());
            orderData.put("withdrawalMethod", order.getWithdrawalMethod());
            orderData.put("status", order.getStatus().name());
            orderData.put("statusText", getWithdrawalStatusText(order.getStatus()));
            orderData.put("createdTime", order.getCreatedTime());
            orderData.put("processedTime", order.getProcessedTime());
            orderData.put("completedTime", order.getCompletedTime());
            orderData.put("errorMessage", order.getErrorMessage());

            return ResponseEntity.ok(orderData);
        } catch (Exception e) {
            logger.error("查询提现订单状态失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("查询提现订单状态失败: " + e.getMessage());
        }
    }
}

