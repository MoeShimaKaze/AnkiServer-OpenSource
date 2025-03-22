package com.server.anki.alipay;

import com.alibaba.fastjson.JSONObject;
import com.server.anki.auth.AuthenticationService;
import com.server.anki.auth.ratelimit.RateLimit;
import com.server.anki.mailorder.entity.MailOrder;
import com.server.anki.mailorder.service.MailOrderService;
import com.server.anki.pay.payment.*;
import com.server.anki.shopping.entity.PurchaseRequest;
import com.server.anki.shopping.entity.ShoppingOrder;
import com.server.anki.shopping.service.PurchaseRequestService;
import com.server.anki.shopping.service.ShoppingOrderService;
import com.server.anki.user.User;
import com.server.anki.wallet.entity.WithdrawalOrder;
import com.server.anki.wallet.repository.WithdrawalOrderRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URLDecoder;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 支付宝支付相关控制器
 * 处理订单支付、钱包充值和提现等功能
 */
@RestController
@RequestMapping("/api/alipay")
public class AlipayController {
    private static final Logger logger = LoggerFactory.getLogger(AlipayController.class);

    @Autowired
    private AlipayService alipayService;

    @Autowired
    private MailOrderService mailOrderService;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private PaymentOrderRepository paymentOrderRepository;

    @Autowired
    private WithdrawalOrderRepository withdrawalOrderRepository;

    @Autowired
    private PurchaseRequestService purchaseRequestService;

    @Autowired
    private ShoppingOrderService shoppingOrderService;

    /**
     * 处理支付宝统一回调通知
     * 增强日志记录，确保请求内容完整保留
     */
    @PostMapping("/notify")
    @RateLimit(rate = 30, timeUnit = TimeUnit.MINUTES, limitType = RateLimit.LimitType.IP)
    public String handleAlipayNotification(HttpServletRequest request) {
        logger.info("收到支付宝通用回调通知");

        // 确保请求可多次读取
        HttpServletRequest requestToUse = request;
        if (!(request instanceof ContentCachingRequestWrapper)) {
            requestToUse = new ContentCachingRequestWrapper(request);
        }

        // 记录请求基本信息
        logger.info("请求方法: {}", requestToUse.getMethod());
        logger.info("请求URL: {}", requestToUse.getRequestURL().toString());
        logger.info("Content-Type: {}", requestToUse.getContentType());

        // 记录请求头信息
        logger.info("===== 请求头信息 =====");
        Enumeration<String> headerNames = requestToUse.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            logger.info("{}: {}", headerName, requestToUse.getHeader(headerName));
        }

        // 转换请求参数
        Map<String, String> params = convertRequestToMap(requestToUse);

        // 记录完整参数用于调试
        logNotifyParams(params);

        try {
            // 验证签名
            if (!alipayService.verifyNotify(params)) {
                logger.error("支付宝通知签名验证失败");

                // 尝试记录原始请求体内容，帮助调试
                try {
                    String requestBody = getRequestBody(requestToUse);
                    logger.info("请求体原始内容: {}", requestBody);
                } catch (Exception e) {
                    logger.warn("读取请求体失败: {}", e.getMessage());
                }

                return "failure";
            }

            logger.info("支付宝通知签名验证成功");

            // 确定通知类型 - 支付或提现
            if (isWithdrawalNotification(params)) {
                // 处理提现通知
                processWithdrawalNotification(params);
            } else {
                // 处理支付通知
                processPaymentNotification(params);
            }

            return "success"; // 必须返回"success"确认收到通知
        } catch (Exception e) {
            logger.error("处理支付宝通知失败", e);
            return "failure";
        }
    }

    /**
     * 将请求参数转换为Map
     * 确保正确保留签名参数
     */
    private Map<String, String> convertRequestToMap(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();

        try {
            // 1. 从请求参数获取
            Map<String, String[]> requestParams = request.getParameterMap();
            logger.info("支付宝回调原始参数数量: {}", requestParams.size());

            // 严格按照支付宝示例代码处理
            for (String name : requestParams.keySet()) {
                String[] values = requestParams.get(name);
                String valueStr = "";
                for (int i = 0; i < values.length; i++) {
                    valueStr = (i == values.length - 1) ? valueStr + values[i]
                            : valueStr + values[i] + ",";
                }

                // 不要进行任何编码转换，直接原样保存
                params.put(name, valueStr);

                // 记录签名参数
                if ("sign".equals(name)) {
                    logger.info("找到签名参数，长度: {}", valueStr.length());
                }
            }

            // 2. 检查签名参数
            if (!params.containsKey("sign") && request.getContentType() != null &&
                    request.getContentType().contains("application/x-www-form-urlencoded")) {
                // 尝试从请求体读取
                logger.info("参数中没有签名，尝试从请求体读取");
                try {
                    StringBuilder stringBuilder = new StringBuilder();
                    BufferedReader bufferedReader = request.getReader();
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        stringBuilder.append(line);
                    }
                    String body = stringBuilder.toString();
                    logger.info("请求体长度: {}", body.length());

                    // 解析表单数据
                    if (!body.isEmpty()) {
                        String[] pairs = body.split("&");
                        for (String pair : pairs) {
                            int idx = pair.indexOf("=");
                            if (idx > 0) {
                                String key = URLDecoder.decode(pair.substring(0, idx), "UTF-8");
                                String value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
                                params.put(key, value);

                                if ("sign".equals(key)) {
                                    logger.info("从请求体找到签名参数，长度: {}", value.length());
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.warn("读取请求体失败: {}", e.getMessage());
                }
            }

            // 确保签名存在
            if (!params.containsKey("sign")) {
                logger.warn("最终的参数集合中没有找到签名参数!");
            } else {
                logger.info("最终参数中包含签名，长度: {}", params.get("sign").length());
            }

            return params;
        } catch (Exception e) {
            logger.error("转换请求参数失败: {}", e.getMessage(), e);
            return params;  // 返回尽可能多的已解析参数
        }
    }

    /**
     * 读取请求体原始内容
     */
    private String getRequestBody(HttpServletRequest request) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            char[] buff = new char[1024];
            int len;
            while ((len = reader.read(buff)) != -1) {
                sb.append(buff, 0, len);
            }
        }
        return sb.toString();
    }

    /**
     * 记录通知参数日志
     */
    private void logNotifyParams(Map<String, String> params) {
        logger.info("===== 支付宝回调参数（共{}个） =====", params.size());
        params.forEach((key, value) -> {
            // 隐藏敏感信息，只显示长度
            if ("sign".equals(key)) {
                logger.info("{} = <签名内容，长度:{}字符>", key, value.length());
            } else {
                logger.info("{} = {}", key, value);
            }
        });
        logger.info("==================================");
    }

    /**
     * 判断是否为提现通知
     */
    private boolean isWithdrawalNotification(Map<String, String> params) {
        // 先根据 msg_method 判断
        if ("alipay.fund.trans.order.changed".equals(params.get("msg_method"))) {
            return true;
        }
        // 检查顶层参数
        String bizType = params.get("biz_type");
        String outBizNo = params.get("out_biz_no");
        // 如果顶层未包含，则尝试从 biz_content 中解析
        if (outBizNo == null && params.containsKey("biz_content")) {
            JSONObject json = JSONObject.parseObject(params.get("biz_content"));
            outBizNo = json.getString("out_biz_no");
        }
        return "TRANS_ACCOUNT_NO_PWD".equals(bizType) || (outBizNo != null && outBizNo.startsWith("W"));
    }


    /**
     * 处理提现通知
     */
    private void processWithdrawalNotification(Map<String, String> params) {
        // 尝试直接获取
        String outBizNo = params.get("out_biz_no");
        String tradeNo = params.get("order_id");
        String status = params.get("status");

        // 如果为空，从 biz_content 中解析
        if ((outBizNo == null || tradeNo == null || status == null) && params.containsKey("biz_content")) {
            JSONObject json = JSONObject.parseObject(params.get("biz_content"));
            if (outBizNo == null) {
                outBizNo = json.getString("out_biz_no");
            }
            if (tradeNo == null) {
                tradeNo = json.getString("order_id");
            }
            if (status == null) {
                status = json.getString("status");
            }
        }

        logger.info("处理提现回调通知: 订单号={}, 支付宝交易号={}, 状态={}", outBizNo, tradeNo, status);

        if (outBizNo == null || outBizNo.isEmpty()) {
            logger.warn("提现回调缺少订单号");
            return;
        }

        // 查找提现订单并更新状态
        Optional<WithdrawalOrder> orderOpt = withdrawalOrderRepository.findByOrderNumber(outBizNo);
        if (orderOpt.isEmpty()) {
            logger.warn("未找到提现订单: {}", outBizNo);
            return;
        }

        WithdrawalOrder order = orderOpt.get();
        if ("SUCCESS".equals(status)) {
            order.setStatus(WithdrawalOrder.WithdrawalStatus.SUCCESS);
            order.setAlipayOrderId(tradeNo);
            order.setCompletedTime(LocalDateTime.now());
            logger.info("提现成功更新订单状态: {}", outBizNo);
        } else if ("FAIL".equals(status)) {
            order.setStatus(WithdrawalOrder.WithdrawalStatus.FAILED);
            order.setErrorMessage("提现失败: " + params.get("error_code"));
            order.setCompletedTime(LocalDateTime.now());
            logger.info("提现失败更新订单状态: {}", outBizNo);
        }
        withdrawalOrderRepository.save(order);
        logger.info("提现订单状态已更新: {}, 新状态: {}", outBizNo, order.getStatus());
    }


    /**
     * 处理支付通知
     */
    private void processPaymentNotification(Map<String, String> params) {
        try {
            // 输出关键参数用于调试
            logger.info("处理支付回调通知: 商户订单号={}, 交易状态={}, 支付宝交易号={}",
                    params.get("out_trade_no"),
                    params.get("trade_status"),
                    params.get("trade_no"));

            alipayService.handlePaymentSuccess(params);

            // 这里不再需要手动处理邮件订单，因为handlePaymentSuccess内部已通过processOrderByType处理
        } catch (Exception e) {
            logger.error("处理支付通知失败: {}", e.getMessage(), e);
            throw e; // 重新抛出以通知调用者处理失败
        }
    }

    /**
     * 支付结果异步通知处理
     * 保留原方法以保持兼容性，内部调用新的通用处理方法
     */
    @PostMapping("/order/notify")
    @RateLimit(rate = 30, timeUnit = TimeUnit.MINUTES, limitType = RateLimit.LimitType.IP)
    public String orderPaymentNotify(HttpServletRequest request) {
        logger.info("收到支付宝支付结果异步通知");
        Map<String, String> params = convertRequestToMap(request);

        try {
            // 记录完整参数用于调试
            logNotifyParams(params);

            // 验证签名
            if (!alipayService.verifyNotify(params)) {
                logger.error("支付宝通知签名验证失败");
                return "failure";
            }

            // 处理支付通知
            processPaymentNotification(params);

            return "success";
        } catch (Exception e) {
            logger.error("处理支付通知失败: {}", e.getMessage(), e);
            // 返回 fail，这样支付宝会重试通知
            return "fail";
        }
    }

    /**
     * 提现异步通知处理
     */
    @PostMapping("/withdrawal/notify")
    @RateLimit(rate = 30, timeUnit = TimeUnit.MINUTES, limitType = RateLimit.LimitType.IP)
    public String withdrawalNotify(HttpServletRequest request) {
        logger.info("收到支付宝提现结果异步通知");
        Map<String, String> params = convertRequestToMap(request);

        try {
            // 记录完整参数用于调试
            logNotifyParams(params);

            // 验证签名
            if (!alipayService.verifyNotify(params)) {
                logger.error("支付宝提现通知签名验证失败");
                return "failure";
            }

            // 处理提现通知
            processWithdrawalNotification(params);

            return "success";
        } catch (Exception e) {
            logger.error("处理提现通知失败: {}", e.getMessage(), e);
            // 返回 fail，这样支付宝会重试通知
            return "fail";
        }
    }

    /**
     * 查询支付状态
     */
    @GetMapping("/order/status/{orderNumber}")
    public ResponseEntity<?> queryPaymentStatus(
            @PathVariable String orderNumber,
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("查询支付状态: {}", orderNumber);

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
        }

        try {
            PaymentOrder paymentOrder = paymentOrderRepository.findByOrderNumber(orderNumber)
                    .orElseThrow(() -> new RuntimeException("支付订单不存在"));

            if (!paymentOrder.getUser().getId().equals(user.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("无权访问此订单");
            }

            // 使用统一查询方法
            PaymentStatusResponse statusResponse = alipayService.queryUnifiedPaymentStatus(orderNumber);

            // 构建响应数据
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("orderNumber", paymentOrder.getOrderInfo());
            responseData.put("orderType", paymentOrder.getOrderType().name());
            responseData.put("paymentStatus", paymentOrder.getStatus().name());
            responseData.put("amount", paymentOrder.getAmount());
            responseData.put("expireTime", paymentOrder.getExpireTime());

            // 根据查询状态返回不同的响应
            if (statusResponse.getStatus() == PaymentStatusResponse.Status.CREATING) {
                return ResponseEntity.status(HttpStatus.ACCEPTED).body(responseData);
            } else if (statusResponse.getStatus() == PaymentStatusResponse.Status.SUCCESS) {
                responseData.put("alipayStatus", statusResponse.getTradeStatus());
                return ResponseEntity.ok(responseData);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", statusResponse.getMessage()));
            }

        } catch (Exception e) {
            logger.error("查询支付状态失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("查询支付状态失败: " + e.getMessage());
        }
    }

    @GetMapping("/pending-orders/{type}")
    public ResponseEntity<?> getPendingPaymentOrdersByType(
            @PathVariable String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false, defaultValue = "createdTime,desc") String sort,
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到查询特定类型待支付订单请求，类型: {}, 页码: {}, 每页数量: {}, 排序: {}",
                type, page, size, sort);

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            logger.warn("未授权的访问尝试获取待支付订单");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
        }

        try {
            // 获取用户的待支付订单
            List<PaymentOrder> allPendingPayments = paymentOrderRepository.findByUserAndStatus(
                    user, PaymentStatus.WAITING
            );

            // 过滤掉已过期的订单
            List<PaymentOrder> validPayments = allPendingPayments.stream()
                    .filter(payment -> payment.getExpireTime().isAfter(LocalDateTime.now()))
                    .collect(Collectors.toList());

            // 根据类型筛选订单
            OrderType orderType = mapStringToOrderType(type);
            if (orderType != null) {
                validPayments = validPayments.stream()
                        .filter(payment -> payment.getOrderType() == orderType)
                        .collect(Collectors.toList());
            }

            // 解析排序参数
            Comparator<PaymentOrder> comparator = getPaymentOrderComparator(sort);

            validPayments.sort(comparator);

            // 计算分页数据
            int totalItems = validPayments.size();
            int totalPages = (int) Math.ceil((double) totalItems / size);

            // 防止页码超出范围
            if (page >= totalPages && totalItems > 0) {
                page = totalPages - 1;
            }

            // 截取当前页的数据
            int start = Math.min(page * size, totalItems);
            int end = Math.min(start + size, totalItems);
            List<PaymentOrder> pagedPayments = validPayments.subList(start, end);

            // 处理订单数据
            List<Map<String, Object>> responseData = new ArrayList<>();
            for (PaymentOrder payment : pagedPayments) {
                switch (payment.getOrderType()) {
                    case MAIL_ORDER:
                        processMailOrderPayment(payment, responseData);
                        break;
                    case SHOPPING_ORDER:
                        processShoppingOrderPayment(payment, responseData);
                        break;
                    case PURCHASE_REQUEST:
                        processPurchaseRequestPayment(payment, responseData);
                        break;
                    default:
                        // 处理其他类型订单或跳过
                        logger.debug("未处理的订单类型: {}, 订单号: {}",
                                payment.getOrderType(), payment.getOrderNumber());
                        break;
                }
            }

            // 构建包含分页信息的响应
            Map<String, Object> result = new HashMap<>();
            result.put("content", responseData);
            result.put("page", page);
            result.put("size", size);
            result.put("totalItems", totalItems);
            result.put("totalPages", totalPages);
            result.put("sort", sort);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("获取待支付订单失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("获取待支付订单失败: " + e.getMessage());
        }
    }

    /**
     * 将字符串类型映射到OrderType枚举
     */
    private OrderType mapStringToOrderType(String type) {
        switch (type.toLowerCase()) {
            case "mail-order":
                return OrderType.MAIL_ORDER;
            case "shopping-order":
                return OrderType.SHOPPING_ORDER;
            case "purchase-request":
                return OrderType.PURCHASE_REQUEST;
            case "all":
                return null; // 返回null表示查询所有类型
            default:
                logger.warn("未知的订单类型: {}, 默认查询所有类型", type);
                return null;
        }
    }

    /**
     * 处理邮件订单支付
     */
    private void processMailOrderPayment(PaymentOrder payment, List<Map<String, Object>> responseData) {
        try {
            UUID mailOrderNumber = UUID.fromString(payment.getOrderInfo());
            MailOrder mailOrder = mailOrderService.findByOrderNumber(mailOrderNumber).orElse(null);

            if (mailOrder != null) {
                // 创建或获取支付信息
                PaymentResponse paymentResponse = alipayService.createMailOrderPayment(mailOrder);

                Map<String, Object> orderData = new HashMap<>();
                orderData.put("orderNumber", payment.getOrderNumber());
                orderData.put("originalOrderNumber", mailOrderNumber.toString());
                orderData.put("orderType", payment.getOrderType().name());
                orderData.put("fee", payment.getAmount());
                orderData.put("expireTime", payment.getExpireTime());
                orderData.put("createdAt", payment.getCreatedTime());
                orderData.put("name", mailOrder.getName());
                orderData.put("pickupAddress", mailOrder.getPickupAddress());
                orderData.put("pickupDetail", mailOrder.getPickupDetail());
                orderData.put("deliveryAddress", mailOrder.getDeliveryAddress());
                orderData.put("deliveryDetail", mailOrder.getDeliveryDetail());
                orderData.put("deliveryTime", mailOrder.getDeliveryTime());
                orderData.put("payForm", paymentResponse.getPayForm());
                orderData.put("payUrl", paymentResponse.getPayUrl()); // 添加支付链接

                responseData.add(orderData);
            } else {
                logger.warn("未找到关联的邮件订单, UUID: {}, 支付订单号: {}",
                        mailOrderNumber, payment.getOrderNumber());
            }
        } catch (Exception e) {
            logger.error("处理邮件订单数据失败, 订单号: {}, 错误: {}",
                    payment.getOrderNumber(), e.getMessage(), e);
        }
    }

    /**
     * 处理商品订单支付
     */
    private void processShoppingOrderPayment(PaymentOrder payment, List<Map<String, Object>> responseData) {
        try {
            UUID orderNumber = UUID.fromString(payment.getOrderInfo());
            ShoppingOrder order = shoppingOrderService.getOrderByNumber(orderNumber);

            if (order != null) {
                // 创建或获取支付信息
                PaymentResponse paymentResponse = shoppingOrderService.createOrderPayment(orderNumber);

                Map<String, Object> orderData = new HashMap<>();
                orderData.put("orderNumber", payment.getOrderNumber());
                orderData.put("originalOrderNumber", orderNumber.toString());
                orderData.put("orderType", payment.getOrderType().name());
                orderData.put("fee", payment.getAmount());
                orderData.put("expireTime", payment.getExpireTime());
                orderData.put("createdAt", payment.getCreatedTime());
                orderData.put("storeName", order.getStore().getStoreName());
                orderData.put("productName", order.getProduct().getName());
                orderData.put("productImage", order.getProduct().getImageUrl());
                orderData.put("quantity", order.getQuantity());
                orderData.put("deliveryAddress", order.getDeliveryAddress());
                orderData.put("recipientName", order.getRecipientName());
                orderData.put("recipientPhone", order.getRecipientPhone());
                orderData.put("deliveryType", order.getDeliveryType().name());
                orderData.put("payForm", paymentResponse.getPayForm());
                orderData.put("payUrl", paymentResponse.getPayUrl()); // 添加支付链接

                responseData.add(orderData);
            } else {
                logger.warn("未找到关联的商品订单, UUID: {}, 支付订单号: {}",
                        orderNumber, payment.getOrderNumber());
            }
        } catch (Exception e) {
            logger.error("处理商品订单数据失败, 订单号: {}, 错误: {}",
                    payment.getOrderNumber(), e.getMessage(), e);
        }
    }

    /**
     * 处理代购需求支付
     */
    private void processPurchaseRequestPayment(PaymentOrder payment, List<Map<String, Object>> responseData) {
        try {
            UUID requestNumber = UUID.fromString(payment.getOrderInfo());
            PurchaseRequest request = purchaseRequestService.findByRequestNumber(requestNumber).orElse(null);

            if (request != null) {
                // 创建或获取支付信息
                PaymentResponse paymentResponse = alipayService.createPurchaseRequestPayment(request);

                Map<String, Object> orderData = new HashMap<>();
                orderData.put("orderNumber", payment.getOrderNumber());
                orderData.put("originalOrderNumber", requestNumber.toString());
                orderData.put("orderType", payment.getOrderType().name());
                orderData.put("fee", payment.getAmount());
                orderData.put("expireTime", payment.getExpireTime());
                orderData.put("createdAt", payment.getCreatedTime());
                orderData.put("title", request.getTitle());
                orderData.put("description", request.getDescription());
                orderData.put("expectedPrice", request.getExpectedPrice());
                orderData.put("deliveryFee", request.getDeliveryFee());
                orderData.put("deliveryAddress", request.getDeliveryAddress());
                orderData.put("payForm", paymentResponse.getPayForm());
                orderData.put("payUrl", paymentResponse.getPayUrl()); // 添加支付链接

                responseData.add(orderData);
            } else {
                logger.warn("未找到关联的代购需求, UUID: {}, 支付订单号: {}",
                        requestNumber, payment.getOrderNumber());
            }
        } catch (Exception e) {
            logger.error("处理代购需求数据失败, 订单号: {}, 错误: {}",
                    payment.getOrderNumber(), e.getMessage(), e);
        }
    }

    /**
     * 为了保持向后兼容，添加不带类型参数的端点
     */
    @GetMapping("/pending-orders")
    public ResponseEntity<?> getAllPendingPaymentOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false, defaultValue = "createdTime,desc") String sort,
            HttpServletRequest request,
            HttpServletResponse response) {

        // 调用处理特定类型的方法，传入"all"表示所有类型
        return getPendingPaymentOrdersByType("all", page, size, sort, request, response);
    }

    @NotNull
    private static Comparator<PaymentOrder> getPaymentOrderComparator(String sort) {
        String[] sortParams = sort.split(",");
        String sortField = sortParams[0];
        boolean isAsc = sortParams.length > 1 && "asc".equalsIgnoreCase(sortParams[1]);

        // 排序
        Comparator<PaymentOrder> comparator = switch (sortField) {
            case "amount" -> Comparator.comparing(PaymentOrder::getAmount);
            case "expireTime" -> Comparator.comparing(PaymentOrder::getExpireTime);
            default -> Comparator.comparing(PaymentOrder::getCreatedTime);
        };

        if (!isAsc) {
            comparator = comparator.reversed();
        }
        return comparator;
    }
}