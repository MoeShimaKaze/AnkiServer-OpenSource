package com.server.anki.shopping.service;

import com.server.anki.alipay.AlipayService;
import com.server.anki.fee.core.FeeContext;
import com.server.anki.fee.result.FeeResult;
import com.server.anki.mailorder.enums.OrderStatus;
import com.server.anki.message.MessageType;
import com.server.anki.message.service.MessageService;
import com.server.anki.pay.payment.PaymentResponse;
import com.server.anki.shopping.dto.ShoppingOrderDTO;
import com.server.anki.shopping.entity.Product;
import com.server.anki.shopping.entity.ShoppingOrder;
import com.server.anki.shopping.entity.Store;
import com.server.anki.shopping.enums.DeliveryType;
import com.server.anki.shopping.enums.StoreStatus;
import com.server.anki.shopping.exception.InvalidOperationException;
import com.server.anki.shopping.repository.ProductRepository;
import com.server.anki.shopping.repository.ShoppingOrderRepository;
import com.server.anki.shopping.repository.StoreRepository;
import com.server.anki.shopping.service.delivery.DeliveryStrategy;
import com.server.anki.shopping.service.delivery.MutualDeliveryStrategy;
import com.server.anki.shopping.service.delivery.PlatformDeliveryStrategy;
import com.server.anki.timeout.core.TimeoutOrderType;
import com.server.anki.timeout.enums.TimeoutStatus;
import com.server.anki.timeout.service.OrderArchiveService;
import com.server.anki.user.User;
import com.server.anki.user.UserService;
import com.server.anki.utils.TestMarkerUtils;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 商品订单服务
 * 负责处理商品订单的创建、支付、配送等相关业务逻辑
 */
@Slf4j
@Service
public class ShoppingOrderService {
    private static final Logger logger = LoggerFactory.getLogger(ShoppingOrderService.class);

    @Autowired
    private ShoppingOrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private UserService userService;

    @Lazy
    @Autowired
    private AlipayService alipayService;

    @Autowired
    private MessageService messageService;

    // 添加统一费用计算上下文
    @Autowired
    private FeeContext feeContext;

    @Autowired
    private MutualDeliveryStrategy mutualDeliveryStrategy;

    @Autowired
    private PlatformDeliveryStrategy platformDeliveryStrategy;

    @Autowired
    private MerchantService merchantService;

    @Autowired
    private ShoppingOrderRepository shoppingOrderRepository;

    @Autowired
    private OrderArchiveService orderArchiveService;

    /**
     * 创建商品订单
     * 处理用户的商品购买请求，包括库存检查、费用计算等
     */
    @Transactional
    public ShoppingOrder createOrder(ShoppingOrderDTO orderDTO) {
        logger.info("开始处理订单创建请求，用户ID: {}", orderDTO.getUserId());

        // 获取商品信息
        Product product = productRepository.findById(orderDTO.getProductId())
                .orElseThrow(() -> new InvalidOperationException("商品不存在"));

        // 验证库存
        if (product.getStock() < orderDTO.getQuantity()) {
            throw new InvalidOperationException("商品库存不足");
        }

        // 创建订单实体
        ShoppingOrder order = new ShoppingOrder();
        order.setOrderNumber(UUID.randomUUID());
        order.setUser(userService.getUserById(orderDTO.getUserId()));
        order.setStore(product.getStore());
        order.setProduct(product);
        order.setQuantity(orderDTO.getQuantity());
        order.setProductPrice(product.getPrice());
        order.setDeliveryType(orderDTO.getDeliveryType());
        order.setRecipientName(orderDTO.getRecipientName());
        order.setRecipientPhone(orderDTO.getRecipientPhone());
        order.setDeliveryAddress(orderDTO.getDeliveryAddress());
        order.setDeliveryLatitude(orderDTO.getDeliveryLatitude());
        order.setDeliveryLongitude(orderDTO.getDeliveryLongitude());
        order.setRemark(orderDTO.getRemark());
        order.setOrderStatus(OrderStatus.PAYMENT_PENDING);

        // 计算各项费用
        calculateOrderFees(order);

        // 预扣商品库存
        product.setStock(product.getStock() - orderDTO.getQuantity());
        productRepository.save(product);

        // 保存订单
        ShoppingOrder savedOrder = orderRepository.save(order);
        logger.info("订单创建成功，订单号: {}", savedOrder.getOrderNumber());

        return savedOrder;
    }

    /**
     * 处理支付成功后的订单流程
     * @param orderNumber 订单编号
     */
    @Transactional
    public void processAfterPaymentSuccess(UUID orderNumber) {
        logger.info("处理商品订单支付成功，订单编号: {}", orderNumber);

        ShoppingOrder order = shoppingOrderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new RuntimeException("商品订单不存在"));

        // 更新订单状态
        order.setOrderStatus(OrderStatus.MERCHANT_PENDING);
        order.setPaymentTime(LocalDateTime.now());

        // 保存更新
        shoppingOrderRepository.save(order);

        // 发送消息通知用户
        messageService.sendMessage(
                order.getUser(),
                String.format("您的商品订单 #%s 支付成功，订单正在处理中", order.getOrderNumber()),
                MessageType.ORDER_PAYMENT_SUCCESS,
                null
        );

        // 通知商家有新订单
        if (order.getStore() != null && order.getStore().getMerchant() != null) {
            messageService.sendMessage(
                    order.getStore().getMerchant(),
                    String.format("您有新的商品订单 #%s，请及时处理", order.getOrderNumber()),
                    MessageType.STORE_NEW_ORDER,
                    null
            );
        }

        logger.info("商品订单支付成功处理完成，订单编号: {}", orderNumber);
    }

    /**
     * 根据订单号获取订单
     * 如果订单不存在则抛出异常
     */
    public ShoppingOrder getOrderByNumber(UUID orderNumber) {
        logger.info("查询订单信息，订单号: {}", orderNumber);
        return orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> {
                    logger.error("未找到订单，订单号: {}", orderNumber);
                    return new InvalidOperationException("订单不存在");
                });
    }

    /**
     * 计算订单费用
     * 使用统一费用计算框架计算订单费用
     */
    private void calculateOrderFees(ShoppingOrder order) {
        logger.debug("开始计算订单费用，订单号: {}", order.getOrderNumber());

        try {
            // 使用统一费用计算上下文计算费用
            FeeResult feeResult = feeContext.calculateFee(order);

            // 使用订单的更新方法设置费用信息
            order.updateFeeResult(feeResult);

            logger.debug("订单费用计算完成 - 订单号: {}, 配送费: {}, 服务费: {}, 总金额: {}",
                    order.getOrderNumber(),
                    order.getDeliveryFee(),
                    order.getServiceFee(),
                    order.getTotalAmount());

        } catch (Exception e) {
            logger.error("计算订单费用时发生错误: {}", e.getMessage(), e);
            throw new RuntimeException("费用计算失败", e);
        }
    }

    /**
     * 处理订单支付成功
     * 更新订单状态并安排配送
     */
    @Transactional
    public void handlePaymentSuccess(UUID orderNumber) {
        logger.info("处理订单支付成功，订单号: {}", orderNumber);

        ShoppingOrder order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new InvalidOperationException("订单不存在"));

        // 更新订单状态
        order.setOrderStatus(OrderStatus.PENDING);
        order.setPaymentTime(LocalDateTime.now());

        // 选择配送策略
        DeliveryStrategy deliveryStrategy = order.getDeliveryType() == DeliveryType.MUTUAL ?
                mutualDeliveryStrategy : platformDeliveryStrategy;

        // 分配配送员
        User deliveryUser = deliveryStrategy.assignDeliveryUser(order);
        if (deliveryUser != null) {
            order.setAssignedUser(deliveryUser);
            order.setOrderStatus(OrderStatus.ASSIGNED);
            order.setExpectedDeliveryTime(deliveryStrategy.calculateExpectedDeliveryTime(order));
        }

        orderRepository.save(order);
    }

    /**
     * 创建订单支付
     * 处理购物订单的支付创建流程
     */
    @Transactional
    public PaymentResponse createOrderPayment(UUID orderNumber) {
        logger.info("创建商品订单支付，订单号: {}", orderNumber);

        ShoppingOrder order = getOrderByNumber(orderNumber);

        // 验证订单状态
        if (order.getOrderStatus() != OrderStatus.PAYMENT_PENDING) {
            throw new InvalidOperationException("订单状态不正确，无法支付");
        }

        try {
            // 创建支付宝支付订单
            PaymentResponse response = alipayService.createShoppingOrderPayment(order);

            // 发送订单创建通知
            messageService.sendMessage(
                    order.getUser(),
                    String.format("您的订单 #%s 已创建成功，请在30分钟内完成支付，订单金额：%.2f元",
                            order.getOrderNumber(),
                            order.getTotalAmount()),
                    MessageType.ORDER_PAYMENT_CREATED,
                    null
            );

            return response;
        } catch (Exception e) {
            logger.error("创建订单支付失败，订单号: {}, 错误: {}", orderNumber, e.getMessage(), e);
            throw new RuntimeException("创建订单支付失败: " + e.getMessage());
        }
    }

    /**
     * 处理支付成功回调
     * 更新订单状态并初始化配送流程
     */
    @Transactional
    public void processPaymentSuccess(UUID orderNumber) {
        logger.info("处理商品订单支付成功，订单号: {}", orderNumber);

        ShoppingOrder order = getOrderByNumber(orderNumber);

        // 更新订单状态为已支付待商家确认
        order.setOrderStatus(OrderStatus.MERCHANT_PENDING);
        order.setPaymentTime(LocalDateTime.now());

        orderRepository.save(order);

        // 发送支付成功通知给用户
        messageService.sendMessage(
                order.getUser(),
                String.format("订单 #%s 支付成功，订单金额：%.2f元，等待商家确认",
                        order.getOrderNumber(),
                        order.getTotalAmount()),
                MessageType.ORDER_PAYMENT_SUCCESS,
                null
        );

        // 通知商家有新订单需要确认
        Store store = order.getStore();
        User merchant = store.getMerchant();
        messageService.sendMessage(
                merchant,
                String.format("您的店铺 '%s' 有新订单 #%s 需要确认，请及时处理",
                        store.getStoreName(),
                        order.getOrderNumber()),
                MessageType.ORDER_STATUS_UPDATED,
                null
        );
    }

    @Transactional
    public ShoppingOrder confirmOrderByMerchant(UUID orderNumber, Long merchantId) {
        logger.info("商家确认订单，订单号: {}, 商家ID: {}", orderNumber, merchantId);

        ShoppingOrder order = getOrderByNumber(orderNumber);

        // 验证订单状态
        if (order.getOrderStatus() != OrderStatus.MERCHANT_PENDING) {
            throw new InvalidOperationException("当前订单状态不允许确认操作");
        }

        // 验证商家身份
        Store orderStore = order.getStore();
        User merchant = userService.getUserById(merchantId);

        // 检查操作用户是否为店铺所属商家或商家员工
        String merchantUid = orderStore.getMerchantInfo().getMerchantUid();
        boolean isAuthorized = merchantService.isUserHasRequiredRole(
                merchantId,
                merchantUid,
                com.server.anki.shopping.enums.MerchantUserRole.OPERATOR
        );

        if (!isAuthorized) {
            throw new InvalidOperationException("您没有权限确认此订单");
        }

        // 确认商品库存
        Product product = order.getProduct();
        if (product.getStock() < order.getQuantity()) {
            throw new InvalidOperationException("商品库存不足，无法确认订单");
        }

        // 根据配送方式进行相应处理
        if (order.getDeliveryType() == DeliveryType.PLATFORM) {
            // 平台配送：尝试分配配送员
            DeliveryStrategy deliveryStrategy = platformDeliveryStrategy;
            User deliveryUser = deliveryStrategy.assignDeliveryUser(order);

            if (deliveryUser != null) {
                // 分配成功，直接进入 ASSIGNED 状态
                order.setAssignedUser(deliveryUser);
                order.setOrderStatus(OrderStatus.ASSIGNED);
                order.setExpectedDeliveryTime(deliveryStrategy.calculateExpectedDeliveryTime(order));

                // 通知配送员
                messageService.sendMessage(
                        deliveryUser,
                        String.format("新的订单 #%s 已分配给您，请及时处理", order.getOrderNumber()),
                        MessageType.ORDER_STATUS_UPDATED,
                        null
                );
            } else {
                // 未能分配配送员，进入 PENDING 状态
                order.setOrderStatus(OrderStatus.PENDING);
            }
        } else {
            // 互助配送：进入 PENDING 状态，等待用户接单
            order.setOrderStatus(OrderStatus.PENDING);
        }

        // 保存订单
        ShoppingOrder savedOrder = orderRepository.save(order);

        // 通知用户
        String statusMessage = order.getOrderStatus() == OrderStatus.ASSIGNED ?
                "正在配送中" : "等待配送员接单";

        messageService.sendMessage(
                order.getUser(),
                String.format("您的订单 #%s 已被商家确认，%s",
                        order.getOrderNumber(),
                        statusMessage),
                MessageType.ORDER_STATUS_UPDATED,
                null
        );

        return savedOrder;
    }

    /**
     * 更新订单配送状态
     * 处理订单配送过程中的状态变更
     */
    @Transactional
    public void updateDeliveryStatus(UUID orderNumber, OrderStatus newStatus) {
        logger.info("更新订单配送状态，订单号: {}, 新状态: {}", orderNumber, newStatus);

        ShoppingOrder order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new InvalidOperationException("订单不存在"));

        // 验证状态变更的合法性
        validateDeliveryStatusTransition(order.getOrderStatus(), newStatus);

        // 更新订单状态
        order.setOrderStatus(newStatus);

        // 如果订单已送达，记录送达时间
        if (newStatus == OrderStatus.DELIVERED) {
            order.setDeliveredTime(LocalDateTime.now());
        }

        orderRepository.save(order);
    }

    /**
     * 申请订单退款
     * 处理用户的退款申请，包括退款资格验证和金额计算
     */
    @Transactional
    public void requestRefund(UUID orderNumber, String reason) {
        logger.info("处理订单退款申请，订单号: {}, 原因: {}", orderNumber, reason);

        ShoppingOrder order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new InvalidOperationException("订单不存在"));

        // 验证订单是否可以退款
        if (!order.isRefundable()) {
            throw new InvalidOperationException("当前订单状态不支持退款");
        }

        // 计算可退款金额
        BigDecimal refundableAmount = order.calculateRefundableAmount();
        if (refundableAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidOperationException("订单不符合退款条件");
        }

        // 更新订单状态
        order.setOrderStatus(OrderStatus.REFUNDING);
        order.setRefundStatus("PROCESSING");
        order.setRefundReason(reason);
        order.setRefundRequestedAt(LocalDateTime.now());
        order.setRefundAmount(refundableAmount);

        orderRepository.save(order);

        logger.info("退款申请已受理，订单号: {}, 退款金额: {}",
                orderNumber, refundableAmount);
    }

    /**
     * 处理退款审核结果
     */
    @Transactional
    public void processRefundResult(UUID orderNumber, boolean approved, String remark) {
        logger.info("处理退款审核结果，订单号: {}, 是否通过: {}", orderNumber, approved);

        ShoppingOrder order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new InvalidOperationException("订单不存在"));

        if (!OrderStatus.REFUNDING.equals(order.getOrderStatus())) {
            throw new InvalidOperationException("订单不在退款处理中");
        }

        if (approved) {
            // 退款审核通过
            order.updateRefundStatus("APPROVED", remark);
            order.setOrderStatus(OrderStatus.REFUNDED);

            // 这里可以调用支付系统处理实际的退款操作
            // processActualRefund(order);
        } else {
            // 退款审核拒绝
            order.updateRefundStatus("REJECTED", remark);
            order.setOrderStatus(order.getOrderStatus());  // 恢复原订单状态
        }

        orderRepository.save(order);

        logger.info("退款审核处理完成，订单号: {}, 结果: {}",
                orderNumber, order.getRefundStatus());
    }

    /**
     * 获取用户订单列表
     */
    public Page<ShoppingOrder> getUserOrders(Long userId, Pageable pageable) {
        User user = userService.getUserById(userId);
        return orderRepository.findByUser(user, pageable);
    }

    /**
     * 获取商家订单列表
     */
    public Page<ShoppingOrder> getStoreOrders(Long storeId, Pageable pageable) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new InvalidOperationException("店铺不存在"));
        return orderRepository.findByStore(store, pageable);
    }

    /**
     * 获取配送员订单列表
     */
    public List<ShoppingOrder> getDeliveryUserOrders(Long userId) {
        User deliveryUser = userService.getUserById(userId);
        return orderRepository.findByAssignedUserAndOrderStatusIn(
                deliveryUser,
                Arrays.asList(OrderStatus.ASSIGNED, OrderStatus.IN_TRANSIT)
        );
    }

    /**
     * 验证订单配送状态变更的合法性
     */
    private void validateDeliveryStatusTransition(OrderStatus currentStatus, OrderStatus newStatus) {
        // 只能按照正常流程变更状态
        if (!isValidStatusTransition(currentStatus, newStatus)) {
            throw new InvalidOperationException(
                    String.format("不允许从 %s 状态变更为 %s 状态", currentStatus, newStatus));
        }
    }

    /**
     * 检查订单状态变更是否合法
     */
    private boolean isValidStatusTransition(OrderStatus currentStatus, OrderStatus newStatus) {
        return switch (currentStatus) {
            case ASSIGNED -> newStatus == OrderStatus.IN_TRANSIT;
            case IN_TRANSIT -> newStatus == OrderStatus.DELIVERED;
            case DELIVERED -> newStatus == OrderStatus.COMPLETED;
            default -> false;
        };
    }

    /**
     * 检查订单是否可以申请退款
     */
    private boolean canRequestRefund(ShoppingOrder order) {
        // 只有待配送或已分配状态的订单可以申请退款
        return order.getOrderStatus() == OrderStatus.PENDING ||
                order.getOrderStatus() == OrderStatus.ASSIGNED;
    }

    /**
     * 获取待商家确认的订单
     *
     * @param storeId 店铺ID
     * @param pageable 分页参数
     * @return 订单分页结果
     */
    public Page<ShoppingOrder> getStorePendingConfirmationOrders(Long storeId, Pageable pageable) {
        logger.debug("获取店铺待确认订单, storeId: {}", storeId);

        // 首先检查店铺是否存在
        Optional<Store> storeOpt = storeRepository.findById(storeId);
        if (storeOpt.isEmpty()) {
            logger.warn("获取待确认订单 - 店铺不存在, storeId: {}", storeId);
            return Page.empty(pageable);
        }

        Store store = storeOpt.get();

        // 检查店铺状态 - 只有活跃的店铺才能查看订单
        if (store.getStatus() != StoreStatus.ACTIVE) {
            logger.info("获取待确认订单 - 店铺状态非活跃, storeId: {}, status: {}",
                    storeId, store.getStatus());
            return Page.empty(pageable);
        }

        // 获取待确认订单
        try {
            // 根据店铺ID和订单状态查询订单
            Page<ShoppingOrder> pendingOrders = orderRepository.findByStoreAndOrderStatus(
                    store,
                    OrderStatus.PAYMENT_PENDING, // 假设这是待确认状态
                    pageable
            );

            logger.debug("获取店铺待确认订单完成, storeId: {}, 订单数量: {}",
                    storeId, pendingOrders.getNumberOfElements());

            return pendingOrders;
        } catch (Exception e) {
            logger.error("获取待确认订单查询出错, storeId: {}, error: {}", storeId, e.getMessage(), e);
            return Page.empty(pageable);
        }
    }

    /**
     * 根据店铺ID和订单状态获取订单列表
     * @param storeId 店铺ID
     * @param orderStatus 订单状态
     * @param pageable 分页参数
     * @return 订单分页结果
     */
    public Page<ShoppingOrder> getOrdersByStoreAndStatus(Long storeId, OrderStatus orderStatus, Pageable pageable) {
        logger.debug("根据店铺ID和状态获取订单, storeId: {}, status: {}", storeId, orderStatus);

        // 首先检查店铺是否存在
        Optional<Store> storeOpt = storeRepository.findById(storeId);
        if (storeOpt.isEmpty()) {
            logger.warn("获取店铺订单 - 店铺不存在, storeId: {}", storeId);
            return Page.empty(pageable);
        }

        Store store = storeOpt.get();

        // 获取指定状态的订单
        try {
            // 使用仓库中的findByStoreAndOrderStatus方法
            Page<ShoppingOrder> orders = orderRepository.findByStoreAndOrderStatus(
                    store,
                    orderStatus,
                    pageable
            );

            logger.debug("获取店铺特定状态订单完成, storeId: {}, status: {}, 订单数量: {}",
                    storeId, orderStatus, orders.getNumberOfElements());

            return orders;
        } catch (Exception e) {
            logger.error("获取店铺特定状态订单查询出错, storeId: {}, status: {}, error: {}",
                    storeId, orderStatus, e.getMessage(), e);
            return Page.empty(pageable);
        }
    }

    /**
     * 根据用户ID和多个订单状态获取订单列表
     *
     * @param userId 用户ID
     * @param statusList 订单状态列表
     * @param pageable 分页参数
     * @return 订单分页列表
     */
    public Page<ShoppingOrder> getUserOrdersByStatusList(Long userId, List<OrderStatus> statusList, Pageable pageable) {
        logger.debug("查询多状态订单, 用户ID: {}, 状态列表: {}", userId, statusList);

        User user = userService.getUserById(userId);

        if (statusList.size() == 1) {
            // 如果只有一个状态，使用现有方法
            return shoppingOrderRepository.findByUserAndOrderStatus(user, statusList.get(0), pageable);
        } else {
            // 多个状态查询
            return shoppingOrderRepository.findByUserAndOrderStatusIn(user, statusList, pageable);
        }
    }

    /**
     * 根据用户ID和订单状态获取订单列表
     *
     * @param userId 用户ID
     * @param status 订单状态
     * @param pageable 分页参数
     * @return 订单分页列表
     */
    public Page<ShoppingOrder> getUserOrdersByStatus(Long userId, OrderStatus status, Pageable pageable) {
        User user = userService.getUserById(userId);
        return shoppingOrderRepository.findByUserAndOrderStatus(user, status, pageable);
    }

    /**
     * 接单
     * 用户接受一个待配送的订单，分配给自己
     *
     * @param orderNumber 订单编号
     * @param userId 用户ID
     * @return 更新后的订单
     */
    @Transactional
    public ShoppingOrder acceptOrder(UUID orderNumber, Long userId) {
        logger.info("处理用户接单请求，订单号: {}, 用户ID: {}", orderNumber, userId);

        // 获取订单信息
        ShoppingOrder order = shoppingOrderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new InvalidOperationException("订单不存在"));

        // 验证订单状态
        if (order.getOrderStatus() != OrderStatus.PENDING) {
            throw new InvalidOperationException("只能接受待处理状态的订单");
        }

        // 验证配送类型
        if (order.getDeliveryType() != DeliveryType.MUTUAL) {
            throw new InvalidOperationException("只能接受互助配送类型的订单");
        }

        // 获取用户信息
        User user = userService.getUserById(userId);

        // 验证用户不能接自己的订单
        if (order.getUser().getId().equals(userId)) {
            throw new InvalidOperationException("不能接自己发布的订单");
        }

        // 检查用户当前在配送中的订单数量
        List<ShoppingOrder> activeOrders = shoppingOrderRepository.findByAssignedUserAndOrderStatusIn(
                user,
                Arrays.asList(OrderStatus.ASSIGNED, OrderStatus.IN_TRANSIT)
        );

        if (activeOrders.size() >= 3) {
            throw new InvalidOperationException("您当前配送的订单已达上限，请先完成现有订单");
        }

        // 选择配送策略
        DeliveryStrategy deliveryStrategy = order.getDeliveryType() == DeliveryType.MUTUAL ?
                mutualDeliveryStrategy : platformDeliveryStrategy;

        // 更新订单状态
        order.setOrderStatus(OrderStatus.ASSIGNED);
        order.setAssignedUser(user);
        order.setExpectedDeliveryTime(deliveryStrategy.calculateExpectedDeliveryTime(order));

        // 保存订单
        ShoppingOrder updatedOrder = shoppingOrderRepository.save(order);

        // 发送通知
        messageService.sendMessage(
                order.getUser(),
                String.format("您的订单 #%s 已被接单，配送员: %s，预计送达时间: %s",
                        order.getOrderNumber(),
                        user.getUsername(),
                        updatedOrder.getExpectedDeliveryTime()),
                MessageType.ORDER_STATUS_UPDATED,
                null
        );

        // 发送通知给配送员
        messageService.sendMessage(
                user,
                String.format("您已成功接单 #%s，商品: %s，配送费: %.2f元",
                        order.getOrderNumber(),
                        order.getProduct().getName(),
                        order.getDeliveryFee()),
                MessageType.ORDER_STATUS_UPDATED,
                null
        );

        logger.info("用户接单成功，订单号: {}, 用户ID: {}", orderNumber, userId);

        return updatedOrder;
    }

    /**
     * 获取可接单列表
     * 分页查询可被接单的订单
     *
     * @param deliveryType 配送类型过滤
     * @param pageable 分页参数
     * @return 可接单的订单分页列表
     */
    public Page<ShoppingOrder> getAvailableOrders(DeliveryType deliveryType, Pageable pageable) {
        if (deliveryType != null) {
            return shoppingOrderRepository.findByOrderStatusAndDeliveryType(
                    OrderStatus.PENDING, deliveryType, pageable);
        } else {
            return shoppingOrderRepository.findByOrderStatus(OrderStatus.PENDING, pageable);
        }
    }

    /**
     * 根据位置获取可接单列表
     * 分页查询特定位置附近可被接单的订单
     *
     * @param latitude 纬度
     * @param longitude 经度
     * @param distance 距离范围（公里）
     * @param deliveryType 配送类型过滤
     * @param pageable 分页参数
     * @return 可接单的订单分页列表
     */
    public Page<ShoppingOrder> getAvailableOrdersByLocation(
            Double latitude,
            Double longitude,
            Double distance,
            DeliveryType deliveryType,
            Pageable pageable) {
        return shoppingOrderRepository.findNearbyAvailableOrders(
                latitude, longitude, distance,
                deliveryType != null ? deliveryType.name() : null,
                pageable);
    }

    /**
     * 记录订单介入原因
     *
     * @param orderNumber 订单编号
     * @param reason 介入原因
     * @param userId 申请介入的用户ID
     */
    @Transactional
    public void recordInterventionReason(UUID orderNumber, String reason, Long userId) {
        logger.info("记录订单介入原因，订单号: {}", orderNumber);

        ShoppingOrder order = shoppingOrderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new InvalidOperationException("订单不存在"));

        // 验证订单状态
        if (order.getOrderStatus() != OrderStatus.PLATFORM_INTERVENTION) {
            throw new InvalidOperationException("只有处于平台介入状态的订单才能记录介入原因");
        }

        // 在实际项目中，这里应该保存到专门的介入记录表
        // 为简化实现，这里暂时使用订单的备注字段记录
        String interventionInfo = String.format("平台介入申请 - 用户ID: %d, 原因: %s, 时间: %s",
                userId, reason, LocalDateTime.now());

        order.setRemark(interventionInfo);
        shoppingOrderRepository.save(order);

        // 通知管理员
        // 这里可以添加通知管理员的逻辑

        logger.info("订单介入原因已记录，订单号: {}", orderNumber);
    }

    /**
     * 重置超时订单状态以便重新分配
     * 清除原配送员信息，更新相关状态和时间，记录超时次数
     */
    @Transactional
    public void resetOrderForReassignment(ShoppingOrder order) {
        logger.info("重置商品订单 {} 状态以便重新分配", order.getOrderNumber());
        try {
            // 增加超时计数
            order.setTimeoutCount(order.getTimeoutCount() + 1);
            logger.debug("商品订单 {} 超时次数更新为: {}", order.getOrderNumber(), order.getTimeoutCount());

            // 判断归档阈值：使用TimeoutOrderType中定义的阈值
            int archiveThreshold = TimeoutOrderType.SHOPPING_ORDER.getArchiveThreshold();
            if (order.getTimeoutCount() >= archiveThreshold) {
                // 超时次数达到阈值，归档订单
                archiveOrder(order);
                return;
            }

            // 若有原配送员，发送通知
            if (order.getAssignedUser() != null) {
                messageService.sendMessage(
                        order.getAssignedUser(),
                        String.format("由于您未在规定时间内完成取件，订单 #%s 已被系统收回（第 %d 次超时）",
                                order.getOrderNumber(), order.getTimeoutCount()),
                        MessageType.ORDER_STATUS_UPDATED,
                        null
                );
            }

            // 重置订单状态
            order.setAssignedUser(null);
            order.setOrderStatus(OrderStatus.PENDING);
            order.setTimeoutStatus(TimeoutStatus.NORMAL);
            order.setTimeoutWarningSent(false);

            // 更新配送时间以便重新接单
            updateDeliveryTimeForReassignment(order);

            // 通知订单创建者
            String userMessage = String.format("您的商品订单 #%s 因超时（第 %d 次）正在重新分配配送员",
                    order.getOrderNumber(), order.getTimeoutCount());
            messageService.sendMessage(order.getUser(), userMessage, MessageType.ORDER_STATUS_UPDATED, null);

            shoppingOrderRepository.save(order);
            logger.info("商品订单 {} 状态重置完成，当前超时次数: {}", order.getOrderNumber(), order.getTimeoutCount());
        } catch (Exception e) {
            logger.error("重置商品订单 {} 状态错误: {}", order.getOrderNumber(), e.getMessage(), e);
            throw new RuntimeException("重置商品订单状态失败: " + e.getMessage());
        }
    }

    /**
     * 归档订单
     */
    @Transactional
    public void archiveOrder(ShoppingOrder order) {
        logger.info("归档商品订单: {}", order.getOrderNumber());
        try {
            // 调用通用订单归档服务
            orderArchiveService.archiveShoppingOrder(order);

            // 设置为已取消状态并保存
            order.setOrderStatus(OrderStatus.CANCELLED);
            order.setRemark(order.getRemark() != null ?
                    order.getRemark() + " | 系统归档: 超时次数过多" :
                    "系统归档: 超时次数过多");
            shoppingOrderRepository.save(order);

            // 通知用户
            messageService.sendMessage(
                    order.getUser(),
                    String.format("您的商品订单 #%s 因多次超时已被系统归档",
                            order.getOrderNumber()),
                    MessageType.ORDER_STATUS_UPDATED,
                    null
            );

            logger.info("商品订单 {} 已归档", order.getOrderNumber());
        } catch (Exception e) {
            logger.error("归档商品订单 {} 失败: {}", order.getOrderNumber(), e.getMessage(), e);
            throw new RuntimeException("商品订单归档失败: " + e.getMessage());
        }
    }

    /**
     * 处理商品订单平台介入
     */
    @Transactional
    public void handleOrderIntervention(UUID orderNumber) {
        logger.info("将商品订单 {} 更新为平台介入状态", orderNumber);
        try {
            ShoppingOrder order = shoppingOrderRepository.findByOrderNumber(orderNumber)
                    .orElseThrow(() -> new RuntimeException("未找到商品订单"));

            order.setOrderStatus(OrderStatus.PLATFORM_INTERVENTION);
            order.setInterventionTime(LocalDateTime.now());
            shoppingOrderRepository.save(order);

            // 通知订单所有者
            messageService.sendMessage(
                    order.getUser(),
                    String.format("您的商品订单 #%s 已转入平台介入处理",
                            order.getOrderNumber()),
                    MessageType.ORDER_STATUS_UPDATED,
                    null
            );

            // 如果有配送员，也通知配送员
            if (order.getAssignedUser() != null) {
                messageService.sendMessage(
                        order.getAssignedUser(),
                        String.format("商品订单 #%s 已转入平台介入处理",
                                order.getOrderNumber()),
                        MessageType.ORDER_STATUS_UPDATED,
                        null
                );
            }

            logger.info("商品订单 {} 已成功更新为平台介入状态", orderNumber);
        } catch (Exception e) {
            logger.error("将商品订单 {} 更新为平台介入状态时出错: {}", orderNumber, e.getMessage(), e);
            throw new RuntimeException("更新商品订单状态失败: " + e.getMessage());
        }
    }

    /**
     * 更新订单的预计配送时间
     */
    private void updateDeliveryTimeForReassignment(ShoppingOrder order) {
        // 根据配送类型计算新的配送时间
        int deliveryTimeMinutes = TimeoutOrderType.SHOPPING_ORDER.getDefaultTimeoutMinutes();

        // 计算新的预计配送时间
        LocalDateTime newDeliveryTime = LocalDateTime.now().plusMinutes(deliveryTimeMinutes);

        // 更新订单配送时间
        order.setExpectedDeliveryTime(newDeliveryTime);

        logger.debug("商品订单 {} 更新预计配送时间为: {}",
                order.getOrderNumber(),
                newDeliveryTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }

    /**
     * 更新商品订单
     * 修改：增加测试标记检测逻辑
     * @param order 待更新的商品订单
     * @return 更新后的商品订单
     */
    @Transactional
    public ShoppingOrder updateShoppingOrder(ShoppingOrder order) {
        logger.info("更新商品订单: {}", order.getOrderNumber());

        if (order.getId() == null) {
            throw new IllegalArgumentException("订单ID不能为空");
        }

        // 检查是否为测试订单
        boolean isTestOrder = TestMarkerUtils.hasTestMarker(order.getRemark());
        if (isTestOrder) {
            logger.info("检测到测试订单 {}，使用简化更新流程", order.getOrderNumber());
            try {
                return shoppingOrderRepository.save(order);
            } catch (Exception e) {
                logger.error("更新测试订单时出错: {}, 错误: {}", order.getOrderNumber(), e.getMessage());
                throw new RuntimeException("更新测试订单失败", e);
            }
        }

        // 原有的普通订单更新逻辑
        try {
            ShoppingOrder updatedOrder = shoppingOrderRepository.save(order);
            logger.info("商品订单更新成功: {}", updatedOrder.getOrderNumber());
            return updatedOrder;
        } catch (Exception e) {
            logger.error("更新商品订单时出错: {}, 错误: {}", order.getOrderNumber(), e.getMessage(), e);
            throw new RuntimeException("更新商品订单失败", e);
        }
    }
}