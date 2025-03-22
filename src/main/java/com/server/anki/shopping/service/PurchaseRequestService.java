package com.server.anki.shopping.service;

import com.server.anki.alipay.AlipayService;
import com.server.anki.amap.AmapService;
import com.server.anki.fee.core.FeeContext;
import com.server.anki.fee.result.FeeDistribution;
import com.server.anki.fee.result.FeeResult;
import com.server.anki.mailorder.enums.OrderStatus;
import com.server.anki.message.MessageType;
import com.server.anki.message.service.MessageService;
import com.server.anki.pay.payment.PaymentResponse;
import com.server.anki.shopping.dto.PurchaseRequestDTO;
import com.server.anki.shopping.entity.PurchaseRequest;
import com.server.anki.shopping.enums.DeliveryType;
import com.server.anki.shopping.exception.InvalidOperationException;
import com.server.anki.shopping.repository.PurchaseRequestRepository;
import com.server.anki.timeout.core.TimeoutOrderType;
import com.server.anki.timeout.enums.TimeoutStatus;
import com.server.anki.timeout.service.OrderArchiveService;
import com.server.anki.user.User;
import com.server.anki.user.UserService;
import com.server.anki.utils.TestMarkerUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 代购需求综合服务
 * 负责处理代购需求的创建、支付、状态管理等全流程业务逻辑
 */
@Service
public class PurchaseRequestService {
    private static final Logger logger = LoggerFactory.getLogger(PurchaseRequestService.class);

    @Autowired
    private PurchaseRequestRepository purchaseRequestRepository;

    @Autowired
    private UserService userService;

    @Lazy
    @Autowired
    private AlipayService alipayService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private FeeContext feeContext;  // 首先添加FeeContext的依赖注入

    @Autowired
    private AmapService amapService; // 确保添加高德地图服务依赖

    @Autowired
    private OrderArchiveService orderArchiveService;

    /**
     * 创建代购需求并生成支付订单
     * 处理用户发布代购需求的完整流程，包括信息验证、费用计算和支付订单创建
     */
    @Transactional
    public PaymentResponse createPurchaseRequest(PurchaseRequestDTO requestDTO) {
        logger.info("开始处理代购需求创建请求，用户ID: {}", requestDTO.getUserId());

        // 验证用户信息
        User user = userService.getUserById(requestDTO.getUserId());

        // 验证截止时间
        validateDeadline(requestDTO.getDeadline());

        // 创建代购需求实体
        PurchaseRequest request = getPurchaseRequest(requestDTO, user);

        // 使用calculateFees替代原来的calculateDeliveryFee
        calculateFees(request);
        // 计算总金额
        request.calculateTotalAmount();

        // 保存需求信息
        PurchaseRequest savedRequest = purchaseRequestRepository.save(request);
        logger.info("代购需求创建成功，需求ID: {}", savedRequest.getId());

        try {
            // 创建支付宝支付订单
            PaymentResponse response = alipayService.createPurchaseRequestPayment(savedRequest);

            // 发送订单创建通知
            messageService.sendMessage(
                    user,
                    String.format("您的代购需求 #%s 已创建成功，请在30分钟内完成支付。商品价格：%.2f元，配送费：%.2f元",
                            savedRequest.getRequestNumber(),
                            savedRequest.getExpectedPrice(),
                            savedRequest.getDeliveryFee()),
                    MessageType.ORDER_PAYMENT_CREATED,
                    null
            );

            return response;
        } catch (Exception e) {
            logger.error("创建支付订单失败，需求编号: {}, 错误: {}",
                    savedRequest.getRequestNumber(), e.getMessage(), e);
            throw new RuntimeException("创建支付订单失败: " + e.getMessage());
        }
    }

    @NotNull
    private PurchaseRequest getPurchaseRequest(PurchaseRequestDTO requestDTO, User user) {
        PurchaseRequest request = new PurchaseRequest();

        // 添加这一行，为请求设置一个唯一的UUID
        request.setRequestNumber(UUID.randomUUID());

        request.setUser(user);
        request.setTitle(requestDTO.getTitle());
        request.setDescription(requestDTO.getDescription());
        request.setCategory(requestDTO.getCategory());
        request.setExpectedPrice(requestDTO.getExpectedPrice());
        request.setImageUrl(requestDTO.getImageUrl());
        request.setDeadline(requestDTO.getDeadline());
        request.setDeliveryType(requestDTO.getDeliveryType());
        // 设置代购地址信息
        request.setPurchaseAddress(requestDTO.getPurchaseAddress());
        request.setPurchaseLatitude(requestDTO.getPurchaseLatitude());
        request.setPurchaseLongitude(requestDTO.getPurchaseLongitude());
        request.setDeliveryAddress(requestDTO.getDeliveryAddress());
        request.setDeliveryLatitude(requestDTO.getDeliveryLatitude());
        request.setDeliveryLongitude(requestDTO.getDeliveryLongitude());
        request.setRecipientName(requestDTO.getRecipientName());
        request.setRecipientPhone(requestDTO.getRecipientPhone());

        // 确保状态值符合数据库约束
        // 根据错误分析，将状态改为PENDING，这应该是数据库约束允许的值
        request.setStatus(OrderStatus.PENDING);

        // 初始化创建时间和更新时间
        request.setCreatedAt(LocalDateTime.now());
        request.setUpdatedAt(LocalDateTime.now());

        // 设置重量（如果提供）
        if (requestDTO.getWeight() != null && requestDTO.getWeight() > 0) {
            request.setWeight(requestDTO.getWeight());
            logger.debug("设置商品重量: {}公斤", requestDTO.getWeight());
        }

        // 使用智能距离计算方法设置配送距离
        try {
            double distance = amapService.calculateOptimalDeliveryDistance(
                    requestDTO.getPurchaseLatitude(), requestDTO.getPurchaseLongitude(),
                    requestDTO.getDeliveryLatitude(), requestDTO.getDeliveryLongitude());
            request.setDeliveryDistance(distance);
            logger.debug("设置配送距离成功: {}公里", distance);
        } catch (Exception e) {
            // 出现异常时设置默认距离
            request.setDeliveryDistance(1.0);
            logger.warn("配送距离计算失败，使用默认值: 1.0公里");
        }

        return request;
    }

    /**
     * 处理支付成功回调
     * 更新订单状态并发送通知
     */
    @Transactional
    public void handlePaymentSuccess(UUID requestNumber) {
        logger.info("处理代购需求支付成功，需求编号: {}", requestNumber);

        PurchaseRequest request = purchaseRequestRepository.findByRequestNumber(requestNumber)
                .orElseThrow(() -> new InvalidOperationException("代购需求不存在"));

        // 确保更新的状态符合数据库约束 - 使用 PENDING 而不是 PAYMENT_PENDING
        request.setStatus(OrderStatus.PENDING);
        request.setPaymentTime(LocalDateTime.now());

        purchaseRequestRepository.save(request);

        // 发送支付成功通知
        messageService.sendMessage(
                request.getUser(),
                String.format("代购需求 #%s 支付成功，需求已发布。配送员接单后将立即为您采购",
                        request.getRequestNumber()),
                MessageType.ORDER_PAYMENT_SUCCESS,
                null
        );
    }

    /**
     * 更新代购需求状态
     * 处理需求的接单、完成等状态变更
     */
    @Transactional
    public void updateRequestStatus(UUID requestNumber, OrderStatus newStatus, User assignedUser) {
        logger.info("更新代购需求状态，需求编号: {}, 新状态: {}", requestNumber, newStatus);

        PurchaseRequest request = purchaseRequestRepository.findByRequestNumber(requestNumber)
                .orElseThrow(() -> new InvalidOperationException("代购需求不存在"));

        // 验证状态变更的合法性
        validateStatusTransition(request.getStatus(), newStatus);

        // 更新状态和相关信息
        request.setStatus(newStatus);
        if (newStatus == OrderStatus.ASSIGNED) {
            request.setAssignedUser(assignedUser);
        } else if (newStatus == OrderStatus.COMPLETED) {
            request.setCompletionDate(LocalDateTime.now());
        } else if (newStatus == OrderStatus.DELIVERED) {
            request.setDeliveredDate(LocalDateTime.now());
        }

        purchaseRequestRepository.save(request);

        // 发送状态更新通知
        sendStatusUpdateNotification(request, newStatus);
    }

    /**
     * 申请退款
     * 验证退款条件并处理退款申请
     */
    @Transactional
    public void requestRefund(UUID requestNumber, String reason) {
        logger.info("处理代购需求退款申请，需求编号: {}, 原因: {}", requestNumber, reason);

        PurchaseRequest request = purchaseRequestRepository.findByRequestNumber(requestNumber)
                .orElseThrow(() -> new InvalidOperationException("代购需求不存在"));

        if (!request.isRefundable()) {
            throw new InvalidOperationException("当前状态不支持退款");
        }

        // 计算可退款金额
        request.setRefundAmount(request.calculateRefundableAmount());
        request.setStatus(OrderStatus.REFUNDING);
        request.setRefundStatus("PROCESSING");
        request.setRefundReason(reason);

        purchaseRequestRepository.save(request);

        // 发送退款申请通知
        messageService.sendMessage(
                request.getUser(),
                String.format("代购需求 #%s 退款申请已受理，退款金额：%.2f元",
                        request.getRequestNumber(),
                        request.getRefundAmount()),
                MessageType.ORDER_REFUND_PROCESSING,
                null
        );
    }

    /**
     * 更新代购需求
     * 修改：增加测试标记检测逻辑
     * @param request 待更新的代购需求对象
     * @return 更新后的代购需求对象
     */
    @Transactional
    public PurchaseRequest updatePurchaseRequest(PurchaseRequest request) {
        logger.info("更新代购需求，请求编号: {}, 状态: {}", request.getRequestNumber(), request.getStatus());

        // 验证请求对象
        if (request.getId() == null) {
            logger.error("无法更新代购需求，ID为空");
            throw new IllegalArgumentException("代购需求ID不能为空");
        }

        // 检查是否为测试订单
        boolean isTestOrder = TestMarkerUtils.hasTestMarker(request.getTitle());
        if (isTestOrder) {
            logger.info("检测到测试代购需求 {}，使用简化更新流程", request.getRequestNumber());
            try {
                return purchaseRequestRepository.save(request);
            } catch (Exception e) {
                logger.error("更新测试代购需求时出错: {}, 错误: {}",
                        request.getRequestNumber(), e.getMessage());
                throw new RuntimeException("更新测试代购需求失败", e);
            }
        }

        // 原有的普通代购需求更新逻辑
        try {
            // 获取现有的需求记录以确保它存在
            PurchaseRequest existingRequest = purchaseRequestRepository.findById(request.getId())
                    .orElseThrow(() -> {
                        logger.error("要更新的代购需求不存在，ID: {}", request.getId());
                        return new PurchaseRequestNotFoundException("代购需求不存在");
                    });

            // 记录状态变更（如果有）便于审计
            if (existingRequest.getStatus() != request.getStatus()) {
                logger.info("代购需求状态发生变更，从 {} 变为 {}, 需求编号: {}",
                        existingRequest.getStatus(), request.getStatus(), request.getRequestNumber());
            }

            // 保存更新
            PurchaseRequest updatedRequest = purchaseRequestRepository.save(request);

            // 发送状态更新通知（如果需要）
            if (existingRequest.getStatus() != request.getStatus()) {
                notifyStatusChange(updatedRequest);
            }

            logger.info("代购需求更新成功，需求编号: {}", updatedRequest.getRequestNumber());
            return updatedRequest;

        } catch (PurchaseRequestNotFoundException e) {
            // 重新抛出已经处理过的异常
            throw e;
        } catch (Exception e) {
            logger.error("更新代购需求时发生错误，需求编号: {}, 错误: {}",
                    request.getRequestNumber(), e.getMessage(), e);
            throw new RuntimeException("更新代购需求失败", e);
        }
    }

    /**
     * 通知状态变更
     * 根据不同的状态发送不同的通知
     */
    private void notifyStatusChange(PurchaseRequest request) {
        // 根据不同状态发送通知
        String message;
        MessageType messageType;

        switch (request.getStatus()) {
            case PAYMENT_PENDING:
                message = String.format("您的代购需求 #%s 已创建，等待支付", request.getRequestNumber());
                messageType = MessageType.ORDER_CREATED;
                break;
            case PENDING:
                message = String.format("您的代购需求 #%s 已支付成功，等待服务人员接单", request.getRequestNumber());
                messageType = MessageType.ORDER_PAYMENT_SUCCESS;
                break;
            case ASSIGNED:
                message = String.format("您的代购需求 #%s 已被接单，服务人员将为您购买商品", request.getRequestNumber());
                messageType = MessageType.ORDER_STATUS_UPDATED;
                break;
            case IN_TRANSIT:
                message = String.format("您的代购需求 #%s 商品已购买，正在配送中", request.getRequestNumber());
                messageType = MessageType.ORDER_STATUS_UPDATED;
                break;
            case DELIVERED:
                message = String.format("您的代购需求 #%s 商品已送达，请及时确认", request.getRequestNumber());
                messageType = MessageType.ORDER_DELIVERED;
                break;
            case COMPLETED:
                message = String.format("您的代购需求 #%s 已完成，感谢您的使用", request.getRequestNumber());
                messageType = MessageType.ORDER_COMPLETED;
                break;
            case REFUNDING:
                message = String.format("您的代购需求 #%s 退款申请已提交，等待处理", request.getRequestNumber());
                messageType = MessageType.ORDER_REFUND_REQUESTED;
                break;
            case REFUNDED:
                message = String.format("您的代购需求 #%s 已退款", request.getRequestNumber());
                messageType = MessageType.ORDER_REFUND_SUCCESS;
                break;
            case PAYMENT_TIMEOUT:
                message = String.format("您的代购需求 #%s 因支付超时已自动取消", request.getRequestNumber());
                messageType = MessageType.ORDER_PAYMENT_TIMEOUT;
                break;
            default:
                // 对于其他状态不发送通知
                return;
        }

        // 发送通知消息
        try {
            messageService.sendMessage(
                    request.getUser(),
                    message,
                    messageType,
                    null
            );
            logger.info("已发送状态变更通知，需求编号: {}, 新状态: {}",
                    request.getRequestNumber(), request.getStatus());
        } catch (Exception e) {
            // 通知发送失败不影响主流程
            logger.warn("发送状态变更通知失败: {}", e.getMessage());
        }
    }

    /**
     * 代购需求不存在异常
     */
    public static class PurchaseRequestNotFoundException extends RuntimeException {
        public PurchaseRequestNotFoundException(String message) {
            super(message);
        }
    }
    /**
     * 搜索代购需求
     * 支持基本搜索和高级筛选功能，包括关键词、状态、配送方式和价格区间
     *
     * @param keyword 搜索关键词，可搜索标题和描述
     * @param status 需求状态，可为空表示不限状态
     * @param deliveryType 配送方式，可为空表示不限配送方式
     * @param minPrice 最低价格，可为空表示不限最低价格
     * @param maxPrice 最高价格，可为空表示不限最高价格
     * @param pageable 分页参数
     * @return 分页的需求列表
     */
    public Page<PurchaseRequest> searchRequests(
            String keyword,
            OrderStatus status,
            DeliveryType deliveryType,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Pageable pageable) {

        // 如果只有关键词和状态，使用基本搜索
        if (deliveryType == null && minPrice == null && maxPrice == null) {
            logger.debug("执行基本搜索 - 关键词: {}, 状态: {}", keyword, status);
            return purchaseRequestRepository.searchRequests(keyword, status, pageable);
        }

        // 否则使用高级搜索
        logger.debug("执行高级搜索 - 关键词: {}, 状态: {}, 配送方式: {}, 价格区间: {}-{}",
                keyword, status, deliveryType,
                minPrice != null ? minPrice : "不限",
                maxPrice != null ? maxPrice : "不限");

        return purchaseRequestRepository.searchRequestsAdvanced(
                keyword,
                status,
                deliveryType,
                minPrice,
                maxPrice,
                pageable
        );
    }

    /**
     * 基本搜索代购需求
     * 仅支持关键词和状态筛选的简单搜索
     *
     * @param keyword 搜索关键词
     * @param status 需求状态，可为空
     * @param pageable 分页参数
     * @return 分页的需求列表
     */
    public Page<PurchaseRequest> searchRequests(
            String keyword,
            OrderStatus status,
            Pageable pageable) {

        logger.debug("执行基本搜索 - 关键词: {}, 状态: {}", keyword, status);
        return purchaseRequestRepository.searchRequests(keyword, status, pageable);
    }

    /**
     * 获取用户发布的代购需求
     */
    public Page<PurchaseRequest> getUserRequests(User user, Pageable pageable) {
        return purchaseRequestRepository.findByUser(user, pageable);
    }


    /**
     * 增加需求浏览量
     * @param requestNumber 需求编号
     * @return 是否更新成功
     */
    @Transactional
    public boolean incrementViewCount(UUID requestNumber) {
        logger.info("增加需求浏览量，需求编号: {}", requestNumber);

        Optional<PurchaseRequest> requestOpt = purchaseRequestRepository.findByRequestNumber(requestNumber);
        if (requestOpt.isPresent()) {
            PurchaseRequest request = requestOpt.get();
            purchaseRequestRepository.incrementViewCount(request.getId());
            return true;
        }

        return false;
    }

    /**
     * 获取推荐的代购需求列表
     * 根据用户当前位置和需求浏览量进行推荐
     *
     * @param userLatitude 用户纬度
     * @param userLongitude 用户经度
     * @param status 需求状态（可选）
     * @param pageable 分页参数
     * @return 分页的推荐需求列表
     */
    public Page<PurchaseRequest> getRecommendedRequests(
            Double userLatitude,
            Double userLongitude,
            OrderStatus status,
            Pageable pageable) {

        logger.info("获取推荐需求列表 - 用户位置: {}, {}", userLatitude, userLongitude);

        // 验证用户位置
        if (userLatitude == null || userLongitude == null) {
            throw new IllegalArgumentException("用户位置不能为空");
        }

        // 限制纬度和经度范围
        if (userLatitude < -90 || userLatitude > 90 || userLongitude < -180 || userLongitude > 180) {
            throw new IllegalArgumentException("无效的地理坐标");
        }

        // 计算搜索范围（方形区域，大约10公里）
        double latRange = 0.1; // 约10公里
        double lngRange = 0.1 / Math.cos(Math.toRadians(userLatitude)); // 根据纬度调整经度范围

        double minLat = userLatitude - latRange;
        double maxLat = userLatitude + latRange;
        double minLng = userLongitude - lngRange;
        double maxLng = userLongitude + lngRange;

        // 只查询待接单的需求
        OrderStatus queryStatus = OrderStatus.PENDING;
        if (status != null) {
            queryStatus = status;
        }

        // 获取附近的需求
        List<PurchaseRequest> nearbyRequests = purchaseRequestRepository.findNearbyRequestsForRecommendation(
                minLat, maxLat, minLng, maxLng, queryStatus);

        // 如果没有找到任何需求，直接返回空页
        if (nearbyRequests.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }

        // 计算每个需求的推荐得分（距离和浏览量的加权得分）
        List<RecommendationScore> scoredRequests = nearbyRequests.stream()
                .map(request -> {
                    // 计算地理距离（单位：千米）
                    double distance = calculateDistance(
                            userLatitude, userLongitude,
                            request.getPurchaseLatitude(), request.getPurchaseLongitude());

                    // 计算推荐得分 = 距离得分（越近越好）* 0.7 + 浏览量得分 * 0.3
                    // 距离得分: 距离越近，得分越高
                    double distanceScore = 10.0 / (1.0 + distance);

                    // 浏览量得分: 浏览量越高，得分越高
                    double viewCountScore = Math.log10(2 + request.getViewCount()); // 加2确保正值

                    // 最终得分: 70%距离 + 30%浏览量
                    double finalScore = distanceScore * 0.7 + viewCountScore * 0.3;

                    return new RecommendationScore(request, finalScore, distance);
                })
                .sorted(Comparator.comparing(RecommendationScore::score).reversed()) // 按得分降序
                .toList();

        // 手动分页
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), scoredRequests.size());

        if (start >= scoredRequests.size()) {
            return new PageImpl<>(Collections.emptyList(), pageable, scoredRequests.size());
        }

        List<PurchaseRequest> pagedRequests = scoredRequests.subList(start, end)
                .stream()
                .map(RecommendationScore::request)
                .collect(Collectors.toList());

        return new PageImpl<>(pagedRequests, pageable, scoredRequests.size());
    }

    /**
     * 获取所有可接单的互助配送代购需求
     * @param pageable 分页参数
     * @return 分页的可接单互助配送代购需求列表
     */
    public Page<PurchaseRequest> getAvailableMutualRequests(Pageable pageable) {
        logger.info("获取可接单互助配送代购需求");
        return purchaseRequestRepository.findAvailableMutualRequests(pageable);
    }

    /**
     * 计算两点之间的地理距离（使用Haversine公式）
     * @param lat1 第一点的纬度
     * @param lng1 第一点的经度
     * @param lat2 第二点的纬度
     * @param lng2 第二点的经度
     * @return 两点之间的距离（千米）
     */
    private double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        final int R = 6371; // 地球半径（千米）

        double latDistance = Math.toRadians(lat2 - lat1);
        double lngDistance = Math.toRadians(lng2 - lng1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lngDistance / 2) * Math.sin(lngDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }

    /**
         * 推荐得分包装类
         */
        private record RecommendationScore(PurchaseRequest request, double score, double distance) {
    }
    /**
     * 计算请求的所有费用
     * 根据配送类型选择对应的费用计算器计算各项费用
     */
    private void calculateFees(PurchaseRequest request) {
        logger.debug("开始计算代购需求费用, 需求编号: {}", request.getOrderNumber());

        try {
            // 使用统一费用计算上下文计算费用
            FeeResult feeResult = feeContext.calculateFee(request);

            // 设置费用明细
            request.setDeliveryFee(feeResult.getDeliveryFee());
            request.setServiceFee(feeResult.getServiceFee());
            request.setTotalAmount(feeResult.getTotalFee());

            // 设置费用分配 - 添加null检查
            FeeDistribution distribution = feeResult.getDistribution();
            if (distribution != null) {
                request.setUserIncome(distribution.getDeliveryIncome().doubleValue());
                request.setPlatformIncome(distribution.getPlatformIncome().doubleValue());
            } else {
                // 设置默认费用分配
                request.setUserIncome(request.getDeliveryFee().doubleValue() * 0.8); // 配送员获得80%配送费
                request.setPlatformIncome(request.getServiceFee().doubleValue()); // 平台获得服务费
                logger.warn("费用分配为null，已使用默认分配方案");
            }

            logger.debug("代购需求费用计算完成 - 需求编号: {}, 配送费: {}, 服务费: {}, 总金额: {}",
                    request.getOrderNumber(),
                    request.getDeliveryFee(),
                    request.getServiceFee(),
                    request.getTotalAmount());

        } catch (Exception e) {
            logger.error("计算代购需求费用时发生错误: {}", e.getMessage(), e);
            // 设置默认费用
            request.setDeliveryFee(BigDecimal.valueOf(8));  // 默认8元配送费
            request.setServiceFee(BigDecimal.valueOf(2));   // 默认2元服务费
            request.setTotalAmount(request.getExpectedPrice().add(
                    request.getDeliveryFee()).add(request.getServiceFee())); // 计算总金额
            request.setUserIncome(6.4);  // 默认配送员收入(配送费的80%)
            request.setPlatformIncome(3.6); // 默认平台收入(服务费+配送费的20%)
            logger.info("费用计算出错，已使用默认费用");
        }
    }

    /**
     * 验证截止时间
     */
    private void validateDeadline(LocalDateTime deadline) {
        if (deadline.isBefore(LocalDateTime.now())) {
            throw new InvalidOperationException("截止时间不能早于当前时间");
        }

        if (deadline.isAfter(LocalDateTime.now().plusDays(7))) {
            throw new InvalidOperationException("截止时间不能超过7天");
        }
    }

    /**
     * 验证状态变更的合法性
     */
    private void validateStatusTransition(OrderStatus currentStatus, OrderStatus newStatus) {
        // 添加明确的检查，拒绝MERCHANT_PENDING状态
        if (newStatus == OrderStatus.MERCHANT_PENDING) {
            throw new InvalidOperationException("代购订单不支持商家待处理状态");
        }
        if (currentStatus == OrderStatus.COMPLETED ||
                currentStatus == OrderStatus.CANCELLED) {
            throw new InvalidOperationException("该需求已结束，无法变更状态");
        }

        // 验证状态流转的合法性
        switch (currentStatus) {
            case PAYMENT_PENDING:
                if (newStatus != OrderStatus.PENDING && newStatus != OrderStatus.CANCELLED
                        && newStatus != OrderStatus.PAYMENT_TIMEOUT) {
                    throw new InvalidOperationException("支付待处理状态只能变更为待处理、已取消或支付超时");
                }
                break;
            case PENDING:
                if (newStatus != OrderStatus.ASSIGNED && newStatus != OrderStatus.CANCELLED
                        && newStatus != OrderStatus.PLATFORM_INTERVENTION) {
                    throw new InvalidOperationException("待处理状态只能变更为已接单、已取消或平台介入");
                }
                break;
            case ASSIGNED:
                if (newStatus != OrderStatus.IN_TRANSIT && newStatus != OrderStatus.CANCELLED
                        && newStatus != OrderStatus.PLATFORM_INTERVENTION) {
                    throw new InvalidOperationException("已接单状态只能变更为配送中、已取消或平台介入");
                }
                break;
            case IN_TRANSIT:
                if (newStatus != OrderStatus.DELIVERED && newStatus != OrderStatus.CANCELLED
                        && newStatus != OrderStatus.PLATFORM_INTERVENTION) {
                    throw new InvalidOperationException("配送中状态只能变更为已送达、已取消或平台介入");
                }
                break;
            case DELIVERED:
                if (newStatus != OrderStatus.COMPLETED && newStatus != OrderStatus.PLATFORM_INTERVENTION) {
                    throw new InvalidOperationException("已送达状态只能变更为已完成或平台介入");
                }
                break;
            case PLATFORM_INTERVENTION:
                // 平台介入状态可以转变为多种状态，根据处理结果决定
                if (!Arrays.asList(OrderStatus.PENDING, OrderStatus.ASSIGNED,
                        OrderStatus.IN_TRANSIT, OrderStatus.DELIVERED,
                        OrderStatus.COMPLETED, OrderStatus.CANCELLED,
                        OrderStatus.REFUNDING).contains(newStatus)) {
                    throw new InvalidOperationException("平台介入状态只能变更为有效的后续处理状态");
                }
                break;
            case MERCHANT_PENDING:
                if (newStatus != OrderStatus.PENDING && newStatus != OrderStatus.CANCELLED) {
                    throw new InvalidOperationException("商家待处理状态只能变更为待处理或已取消");
                }
                break;
            default:
                throw new InvalidOperationException("当前状态不支持变更");
        }
    }

    /**
     * 发送状态更新通知
     */
    private void sendStatusUpdateNotification(PurchaseRequest request, OrderStatus newStatus) {
        String message = switch (newStatus) {
            case ASSIGNED -> String.format("您的代购需求 #%s 已被接单，配送员将为您采购商品",
                    request.getRequestNumber());
            case IN_TRANSIT -> String.format("您的代购需求 #%s 正在配送中",
                    request.getRequestNumber());
            case DELIVERED -> String.format("您的代购需求 #%s 已送达，请查收",
                    request.getRequestNumber());
            case COMPLETED -> String.format("您的代购需求 #%s 已完成",
                    request.getRequestNumber());
            case CANCELLED -> String.format("您的代购需求 #%s 已取消",
                    request.getRequestNumber());
            default -> null;
        };

        if (message != null) {
            messageService.sendMessage(
                    request.getUser(),
                    message,
                    MessageType.ORDER_STATUS_UPDATED,
                    null
            );
        }
    }

    /**
     * 根据请求编号查找代购需求
     *
     * @param requestNumber 请求编号
     * @return 包含代购需求的Optional对象
     */
    public Optional<PurchaseRequest> findByRequestNumber(UUID requestNumber) {
        return purchaseRequestRepository.findByRequestNumber(requestNumber);
    }


    /**
     * 重置超时订单状态以便重新分配
     * 清除原配送员信息，更新相关状态和时间，记录超时次数
     */
    @Transactional
    public void resetOrderForReassignment(PurchaseRequest request) {
        logger.info("重置代购需求 {} 状态以便重新分配", request.getRequestNumber());
        try {
            // 增加超时计数
            request.setTimeoutCount(request.getTimeoutCount() + 1);
            logger.debug("代购需求 {} 超时次数更新为: {}", request.getRequestNumber(), request.getTimeoutCount());

            // 判断归档阈值
            int archiveThreshold = TimeoutOrderType.PURCHASE_REQUEST.getArchiveThreshold();
            if (request.getTimeoutCount() >= archiveThreshold) {
                // 超时次数达到阈值，归档订单
                archiveOrder(request);
                return;
            }

            // 若有原配送员，发送通知
            if (request.getAssignedUser() != null) {
                messageService.sendMessage(
                        request.getAssignedUser(),
                        String.format("由于您未在规定时间内完成代购，需求 #%s 已被系统收回（第 %d 次超时）",
                                request.getRequestNumber(), request.getTimeoutCount()),
                        MessageType.ORDER_STATUS_UPDATED,
                        null
                );
            }

            // 重置订单状态
            request.setAssignedUser(null);
            request.setStatus(OrderStatus.PENDING);
            request.setTimeoutStatus(TimeoutStatus.NORMAL);
            request.setTimeoutWarningSent(false);

            // 更新配送时间以便重新接单
            updateDeliveryTimeForReassignment(request);

            // 通知用户
            String userMessage = String.format("您的代购需求 #%s 因超时（第 %d 次）正在重新分配配送员",
                    request.getRequestNumber(), request.getTimeoutCount());
            messageService.sendMessage(request.getUser(), userMessage, MessageType.ORDER_STATUS_UPDATED, null);

            purchaseRequestRepository.save(request);
            logger.info("代购需求 {} 状态重置完成，当前超时次数: {}", request.getRequestNumber(), request.getTimeoutCount());
        } catch (Exception e) {
            logger.error("重置代购需求 {} 状态错误: {}", request.getRequestNumber(), e.getMessage(), e);
            throw new RuntimeException("重置代购需求状态失败: " + e.getMessage());
        }
    }

    /**
     * 归档代购需求
     */
    @Transactional
    public void archiveOrder(PurchaseRequest request) {
        logger.info("归档代购需求: {}", request.getRequestNumber());
        try {
            // 调用通用订单归档服务
            orderArchiveService.archivePurchaseRequest(request);

            // 将状态设置为已取消并保存
            request.setStatus(OrderStatus.CANCELLED);
            request.setDescription(request.getDescription() != null ?
                    request.getDescription() + " | 系统归档: 超时次数过多" :
                    "系统归档: 超时次数过多");
            purchaseRequestRepository.save(request);

            // 通知用户
            messageService.sendMessage(
                    request.getUser(),
                    String.format("您的代购需求 #%s 因多次超时已被系统归档",
                            request.getRequestNumber()),
                    MessageType.ORDER_STATUS_UPDATED,
                    null
            );

            logger.info("代购需求 {} 已归档", request.getRequestNumber());
        } catch (Exception e) {
            logger.error("归档代购需求 {} 失败: {}", request.getRequestNumber(), e.getMessage(), e);
            throw new RuntimeException("代购需求归档失败: " + e.getMessage());
        }
    }

    /**
     * 处理代购需求平台介入
     */
    @Transactional
    public void handleOrderIntervention(UUID requestNumber) {
        logger.info("将代购需求 {} 更新为平台介入状态", requestNumber);
        try {
            PurchaseRequest request = purchaseRequestRepository.findByRequestNumber(requestNumber)
                    .orElseThrow(() -> new RuntimeException("未找到代购需求"));

            request.setStatus(OrderStatus.PLATFORM_INTERVENTION);
            request.setInterventionTime(LocalDateTime.now());
            purchaseRequestRepository.save(request);

            // 通知订单所有者
            messageService.sendMessage(
                    request.getUser(),
                    String.format("您的代购需求 #%s 已转入平台介入处理",
                            request.getRequestNumber()),
                    MessageType.ORDER_STATUS_UPDATED,
                    null
            );

            // 如果有配送员，也通知配送员
            if (request.getAssignedUser() != null) {
                messageService.sendMessage(
                        request.getAssignedUser(),
                        String.format("代购需求 #%s 已转入平台介入处理",
                                request.getRequestNumber()),
                        MessageType.ORDER_STATUS_UPDATED,
                        null
                );
            }

            logger.info("代购需求 {} 已成功更新为平台介入状态", requestNumber);
        } catch (Exception e) {
            logger.error("将代购需求 {} 更新为平台介入状态时出错: {}", requestNumber, e.getMessage(), e);
            throw new RuntimeException("更新代购需求状态失败: " + e.getMessage());
        }
    }

    /**
     * 完成代购需求
     * 用于确认超时自动完成
     */
    @Transactional
    public void completeOrder(PurchaseRequest request) {
        logger.info("自动完成代购需求: {}", request.getRequestNumber());
        try {
            request.setStatus(OrderStatus.COMPLETED);
            request.setCompletionDate(LocalDateTime.now());
            purchaseRequestRepository.save(request);

            // 通知用户
            messageService.sendMessage(
                    request.getUser(),
                    String.format("您的代购需求 #%s 已自动完成",
                            request.getRequestNumber()),
                    MessageType.ORDER_STATUS_UPDATED,
                    null
            );

            // 如果有配送员，处理收入
            if (request.getAssignedUser() != null) {
                // 处理配送员收入逻辑可以在这里添加
                logger.info("代购需求 {} 已完成，配送员: {}",
                        request.getRequestNumber(), request.getAssignedUser().getId());
            }

            logger.info("代购需求 {} 自动完成处理完毕", request.getRequestNumber());
        } catch (Exception e) {
            logger.error("自动完成代购需求 {} 失败: {}", request.getRequestNumber(), e.getMessage(), e);
            throw new RuntimeException("自动完成代购需求失败: " + e.getMessage());
        }
    }

    /**
     * 更新代购需求的预计配送时间
     */
    private void updateDeliveryTimeForReassignment(PurchaseRequest request) {
        // 使用默认的配送时间
        int deliveryTimeMinutes = TimeoutOrderType.PURCHASE_REQUEST.getDefaultTimeoutMinutes();

        // 计算新的预计配送时间
        LocalDateTime newDeliveryTime = LocalDateTime.now().plusMinutes(deliveryTimeMinutes);

        // 更新需求配送时间
        request.setDeliveryTime(newDeliveryTime);
        logger.debug("代购需求 {} 更新预计配送时间为: {}",
                request.getRequestNumber(),
                newDeliveryTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }
}