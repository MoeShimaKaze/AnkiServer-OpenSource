package com.server.anki.shopping.controller;

import com.server.anki.auth.AuthenticationService;
import com.server.anki.mailorder.enums.OrderStatus;
import com.server.anki.pay.payment.PaymentResponse;
import com.server.anki.rating.*;
import com.server.anki.shopping.controller.response.ShoppingOrderResponse;
import com.server.anki.shopping.dto.ShoppingOrderDTO;
import com.server.anki.shopping.entity.ShoppingOrder;
import com.server.anki.shopping.enums.DeliveryType;
import com.server.anki.shopping.exception.InvalidOperationException;
import com.server.anki.shopping.service.ProductService;
import com.server.anki.shopping.service.ShoppingOrderService;
import com.server.anki.shopping.service.StoreService;
import com.server.anki.user.User;
import com.server.anki.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 购物订单控制器
 * 提供商品购买、订单管理、支付、退款等完整功能的接口
 */
@Slf4j
@RestController
@RequestMapping("/api/shopping")
@Tag(name = "购物订单", description = "商品购买、订单管理、支付、退款等接口")
public class ShoppingOrderController {
    private static final Logger logger = LoggerFactory.getLogger(ShoppingOrderController.class);

    @Autowired
    private ShoppingOrderService orderService;

    @Autowired
    private StoreService storeService;

    @Autowired
    private ProductService productService;

    @Autowired
    private UserService userService;

    @Autowired
    private AuthenticationService authenticationService;


    @Autowired
    private RatingService ratingService;

    /**
     * 创建购物订单
     */
    @PostMapping("/orders")
    @Operation(summary = "创建购物订单", description = "创建新的购物订单并返回订单信息",
            responses = {
                    @ApiResponse(responseCode = "200", description = "订单创建成功",
                            content = @Content(schema = @Schema(implementation = ShoppingOrderResponse.class))),
                    @ApiResponse(responseCode = "400", description = "请求参数错误"),
                    @ApiResponse(responseCode = "401", description = "未授权"),
                    @ApiResponse(responseCode = "500", description = "服务器错误")
            })
    public ResponseEntity<?> createOrder(
            @Valid @RequestBody ShoppingOrderDTO orderDTO,
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到创建购物订单请求, 用户ID: {}, 商品ID: {}", orderDTO.getUserId(), orderDTO.getProductId());

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
        }

        // 验证订单创建者身份
        if (!user.getId().equals(orderDTO.getUserId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("无权为其他用户创建订单");
        }

        try {
            // 创建订单
            ShoppingOrder order = orderService.createOrder(orderDTO);

            // 返回订单信息
            return ResponseEntity.ok(ShoppingOrderResponse.fromOrder(order));
        } catch (InvalidOperationException e) {
            logger.error("创建订单失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            logger.error("创建订单发生异常: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("创建订单失败: " + e.getMessage());
        }
    }

    /**
     * 创建订单支付
     */
    @PostMapping("/orders/{orderNumber}/payment")
    @Operation(summary = "创建订单支付", description = "为指定订单创建支付请求，返回支付信息")
    public ResponseEntity<?> createOrderPayment(
            @PathVariable String orderNumber,
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到创建订单支付请求, 订单号: {}", orderNumber);

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
        }

        try {
            UUID orderUUID = UUID.fromString(orderNumber);

            // 验证用户是否为订单所有者
            ShoppingOrder order = orderService.getOrderByNumber(orderUUID);
            if (!order.getUser().getId().equals(user.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("无权支付他人订单");
            }

            // 创建支付
            PaymentResponse paymentResponse = orderService.createOrderPayment(orderUUID);

            return ResponseEntity.ok(paymentResponse);
        } catch (IllegalArgumentException e) {
            logger.error("订单号格式无效: {}", e.getMessage());
            return ResponseEntity.badRequest().body("订单号格式无效");
        } catch (InvalidOperationException e) {
            logger.error("创建支付失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            logger.error("创建支付发生异常: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("创建支付失败: " + e.getMessage());
        }
    }

    /**
     * 查询订单状态
     */
    @GetMapping("/orders/{orderNumber}")
    @Operation(summary = "查询订单状态", description = "获取特定订单的详细信息和状态")
    public ResponseEntity<?> getOrderStatus(
            @PathVariable String orderNumber,
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到查询订单状态请求, 订单号: {}", orderNumber);

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
        }

        try {
            UUID orderUUID = UUID.fromString(orderNumber);
            ShoppingOrder order = orderService.getOrderByNumber(orderUUID);

            // 验证访问权限 - 订单所有者、配送员或商家可以查看
            boolean hasAccess = order.getUser().getId().equals(user.getId()) ||
                    (order.getAssignedUser() != null && order.getAssignedUser().getId().equals(user.getId())) ||
                    (order.getStore().getMerchant().getId().equals(user.getId()));

            if (!hasAccess && !userService.isAdminUser(user)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("无权查看该订单");
            }

            return ResponseEntity.ok(ShoppingOrderResponse.fromOrder(order));
        } catch (IllegalArgumentException e) {
            logger.error("订单号格式无效: {}", e.getMessage());
            return ResponseEntity.badRequest().body("订单号格式无效");
        } catch (Exception e) {
            logger.error("查询订单状态失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("查询订单状态失败: " + e.getMessage());
        }
    }

    /**
     * 获取用户订单列表
     */
    @GetMapping("/orders/user")
    @Operation(summary = "获取用户订单列表", description = "分页获取当前登录用户的订单列表")
    public ResponseEntity<?> getUserOrders(
            @RequestParam(required = false) List<OrderStatus> status,
            Pageable pageable,
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到获取用户订单列表请求, 状态过滤: {}", status);
        logger.debug("详细请求信息: URL={}, 分页信息: 页码={}, 每页大小={}, 排序={}",
                request.getRequestURL().toString() + (request.getQueryString() != null ? "?" + request.getQueryString() : ""),
                pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort());

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            logger.warn("获取用户订单列表失败: 用户未登录");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
        }

        try {
            Page<ShoppingOrder> orders;
            if (status != null && !status.isEmpty()) {
                logger.info("按多状态查询订单, 用户ID: {}, 状态列表: {}", user.getId(), status);
                // 调用新增的服务方法，支持多状态查询
                orders = orderService.getUserOrdersByStatusList(user.getId(), status, pageable);
            } else {
                logger.info("查询用户所有订单, 用户ID: {}", user.getId());
                orders = orderService.getUserOrders(user.getId(), pageable);
            }

            Page<ShoppingOrderResponse> responseList = orders.map(ShoppingOrderResponse::fromOrder);
            logger.info("获取用户订单成功, 用户ID: {}, 订单数量: {}, 总页数: {}",
                    user.getId(), responseList.getNumberOfElements(), responseList.getTotalPages());

            return ResponseEntity.ok(responseList);
        } catch (Exception e) {
            logger.error("获取用户订单列表失败: 用户ID={}, 错误类型={}, 错误信息={}",
                    user.getId(), e.getClass().getSimpleName(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("获取订单列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取配送员的待配送订单
     */
    @GetMapping("/orders/deliverer")
    @Operation(summary = "获取配送员的待配送订单", description = "获取当前登录用户作为配送员的待配送订单")
    public ResponseEntity<?> getDelivererOrders(
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到获取配送员订单列表请求");

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
        }

        try {
            List<ShoppingOrder> orders = orderService.getDeliveryUserOrders(user.getId());
            List<ShoppingOrderResponse> responseList = orders.stream()
                    .map(ShoppingOrderResponse::fromOrder)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(responseList);
        } catch (Exception e) {
            logger.error("获取配送员订单列表失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("获取订单列表失败: " + e.getMessage());
        }
    }

    /**
     * 更新订单状态
     */
    @PutMapping("/orders/{orderNumber}/status")
    @Operation(summary = "更新订单状态", description = "更新特定订单的状态，仅配送员、商家或管理员可操作")
    public ResponseEntity<?> updateOrderStatus(
            @PathVariable String orderNumber,
            @RequestParam OrderStatus newStatus,
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到更新订单状态请求, 订单号: {}, 新状态: {}", orderNumber, newStatus);

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
        }

        try {
            UUID orderUUID = UUID.fromString(orderNumber);
            ShoppingOrder order = orderService.getOrderByNumber(orderUUID);

            // 验证操作权限
            boolean hasAccess = false;

            // 配送员可以更新配送状态
            boolean isDeliverer = order.getAssignedUser() != null &&
                    order.getAssignedUser().getId().equals(user.getId());

            // 商家可以确认订单
            boolean isMerchant = order.getStore().getMerchant().getId().equals(user.getId());

            // 顾客可以确认收货并完成订单
            boolean isCustomer = order.getUser().getId().equals(user.getId());

            // 管理员可以进行所有操作
            boolean isAdmin = userService.isAdminUser(user);

            // 根据当前状态和目标状态判断操作权限
            switch (order.getOrderStatus()) {
                case PENDING:
                    // 配送员可以接单，订单状态变为ASSIGNED
                    if (newStatus == OrderStatus.ASSIGNED && (isDeliverer || isAdmin)) {
                        hasAccess = true;
                    }
                    break;
                case ASSIGNED:
                    // 配送员可以开始配送，订单状态变为IN_TRANSIT
                    if (newStatus == OrderStatus.IN_TRANSIT && (isDeliverer || isAdmin)) {
                        hasAccess = true;
                    }
                    break;
                case IN_TRANSIT:
                    // 配送员可以标记送达，订单状态变为DELIVERED
                    if (newStatus == OrderStatus.DELIVERED && (isDeliverer || isAdmin)) {
                        hasAccess = true;
                    }
                    break;
                case DELIVERED:
                    // 客户可以确认收货，订单状态变为COMPLETED
                    if (newStatus == OrderStatus.COMPLETED && (isCustomer || isAdmin)) {
                        hasAccess = true;
                    }
                    break;
                default:
                    break;
            }

            // 商家可以处理申请退款的订单
            if (order.getOrderStatus() == OrderStatus.REFUNDING &&
                    (isMerchant || isAdmin) &&
                    (newStatus == OrderStatus.REFUNDED || newStatus == OrderStatus.PLATFORM_INTERVENTION)) {
                hasAccess = true;
            }

            if (!hasAccess) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("无权更新订单状态或状态变更不允许");
            }

            // 更新订单状态
            orderService.updateDeliveryStatus(orderUUID, newStatus);

            // 返回更新成功消息及当前状态
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("orderNumber", orderNumber);
            result.put("status", newStatus);
            result.put("message", "订单状态已更新");

            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            logger.error("订单号格式无效: {}", e.getMessage());
            return ResponseEntity.badRequest().body("订单号格式无效");
        } catch (InvalidOperationException e) {
            logger.error("更新订单状态失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            logger.error("更新订单状态失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("更新订单状态失败: " + e.getMessage());
        }
    }

    /**
     * 申请退款
     */
    @PostMapping("/orders/{orderNumber}/refund")
    @Operation(summary = "申请退款", description = "为指定订单申请退款，需要提供退款原因")
    public ResponseEntity<?> requestRefund(
            @PathVariable String orderNumber,
            @RequestParam String reason,
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到申请退款请求, 订单号: {}, 原因: {}", orderNumber, reason);

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
        }

        try {
            UUID orderUUID = UUID.fromString(orderNumber);
            ShoppingOrder order = orderService.getOrderByNumber(orderUUID);

            // 验证用户是否为订单所有者
            if (!order.getUser().getId().equals(user.getId()) && !userService.isAdminUser(user)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("无权操作此订单");
            }

            // 申请退款
            orderService.requestRefund(orderUUID, reason);

            // 获取最新的退款金额
            ShoppingOrder updatedOrder = orderService.getOrderByNumber(orderUUID);
            BigDecimal refundAmount = updatedOrder.getRefundAmount();

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("orderNumber", orderNumber);
            result.put("refundAmount", refundAmount);
            result.put("status", "REFUNDING");
            result.put("message", "退款申请已提交");

            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            logger.error("订单号格式无效: {}", e.getMessage());
            return ResponseEntity.badRequest().body("订单号格式无效");
        } catch (InvalidOperationException e) {
            logger.error("申请退款失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            logger.error("申请退款失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("申请退款失败: " + e.getMessage());
        }
    }

    /**
     * 处理退款结果
     */
    @PostMapping("/orders/{orderNumber}/refund/process")
    @Operation(summary = "处理退款结果", description = "商家处理退款申请，同意或拒绝")
    public ResponseEntity<?> processRefundResult(
            @PathVariable String orderNumber,
            @RequestParam boolean approved,
            @RequestParam(required = false) String remark,
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到处理退款结果请求, 订单号: {}, 是否通过: {}", orderNumber, approved);

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
        }

        try {
            UUID orderUUID = UUID.fromString(orderNumber);
            ShoppingOrder order = orderService.getOrderByNumber(orderUUID);

            // 验证用户是否为商家或管理员
            boolean isMerchant = order.getStore().getMerchant().getId().equals(user.getId());
            boolean isAdmin = userService.isAdminUser(user);

            if (!isMerchant && !isAdmin) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("无权处理退款");
            }

            // 处理退款结果
            orderService.processRefundResult(orderUUID, approved, remark);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("orderNumber", orderNumber);
            result.put("status", approved ? "REFUNDED" : "REFUND_REJECTED");
            result.put("message", approved ? "退款已通过" : "退款已拒绝");

            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            logger.error("订单号格式无效: {}", e.getMessage());
            return ResponseEntity.badRequest().body("订单号格式无效");
        } catch (InvalidOperationException e) {
            logger.error("处理退款失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            logger.error("处理退款失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("处理退款失败: " + e.getMessage());
        }
    }

    /**
     * 评价订单
     */
    @PostMapping("/orders/{orderNumber}/rate")
    @Operation(summary = "评价订单", description = "为指定订单创建评价")
    public ResponseEntity<?> rateOrder(
            @PathVariable String orderNumber,
            @RequestBody RatingDTO ratingDTO,
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到评价订单请求, 订单号: {}", orderNumber);

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
        }

        try {
            UUID orderUUID = UUID.fromString(orderNumber);

            // 设置默认的订单类型
            ratingDTO.setOrderType(OrderType.SHOPPING_ORDER);
            ratingDTO.setOrderNumber(orderUUID);

            // 自动确定评价类型
            if (ratingDTO.getRatingType() == null) {
                ShoppingOrder order = orderService.getOrderByNumber(orderUUID);

                if (order.getUser().getId().equals(user.getId())) {
                    // 顾客评价商家
                    ratingDTO.setRatingType(RatingType.CUSTOMER_TO_MERCHANT);
                } else if (order.getStore().getMerchant().getId().equals(user.getId())) {
                    // 商家评价顾客
                    ratingDTO.setRatingType(RatingType.MERCHANT_TO_CUSTOMER);
                } else if (order.getAssignedUser() != null &&
                        order.getAssignedUser().getId().equals(user.getId())) {
                    // 配送员评价顾客
                    ratingDTO.setRatingType(RatingType.DELIVERER_TO_CUSTOMER);
                } else {
                    return ResponseEntity.badRequest().body("无法确定评价类型");
                }
            }

            // 创建评价
            Rating rating = ratingService.createRating(
                    user.getId(),
                    orderUUID,
                    ratingDTO.getComment(),
                    ratingDTO.getScore(),
                    ratingDTO.getRatingType(),
                    OrderType.SHOPPING_ORDER
            );

            return ResponseEntity.ok(rating);
        } catch (IllegalArgumentException e) {
            logger.error("创建评价失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body("参数格式无效: " + e.getMessage());
        } catch (Exception e) {
            logger.error("创建评价失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("创建评价失败: " + e.getMessage());
        }
    }

    /**
     * 申请平台介入
     */
    @PostMapping("/orders/{orderNumber}/intervention")
    @Operation(summary = "申请平台介入", description = "为指定订单申请平台介入处理")
    public ResponseEntity<?> requestIntervention(
            @PathVariable String orderNumber,
            @RequestParam String reason,
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到申请平台介入请求, 订单号: {}", orderNumber);

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
        }

        try {
            UUID orderUUID = UUID.fromString(orderNumber);
            ShoppingOrder order = orderService.getOrderByNumber(orderUUID);

            // 验证用户是否有权申请介入(订单相关方均可)
            boolean isInvolved = order.getUser().getId().equals(user.getId()) ||
                    order.getStore().getMerchant().getId().equals(user.getId()) ||
                    (order.getAssignedUser() != null && order.getAssignedUser().getId().equals(user.getId()));

            if (!isInvolved && !userService.isAdminUser(user)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("无权申请平台介入");
            }

            // 更新订单状态为平台介入
            orderService.updateDeliveryStatus(orderUUID, OrderStatus.PLATFORM_INTERVENTION);

            // 记录介入原因
            orderService.recordInterventionReason(orderUUID, reason, user.getId());

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("orderNumber", orderNumber);
            result.put("status", "PLATFORM_INTERVENTION");
            result.put("message", "平台介入申请已提交");

            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            logger.error("订单号格式无效: {}", e.getMessage());
            return ResponseEntity.badRequest().body("订单号格式无效");
        } catch (InvalidOperationException e) {
            logger.error("申请平台介入失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            logger.error("申请平台介入失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("申请平台介入失败: " + e.getMessage());
        }
    }

    /**
     * 接单(用于互助配送)
     */
    @PostMapping("/orders/{orderNumber}/accept")
    @Operation(summary = "接单", description = "接受一个待配送的订单(用于互助配送)")
    public ResponseEntity<?> acceptOrder(
            @PathVariable String orderNumber,
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到接单请求, 订单号: {}", orderNumber);

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
        }

        try {
            UUID orderUUID = UUID.fromString(orderNumber);

            // 接单并分配给当前用户
            ShoppingOrder acceptedOrder = orderService.acceptOrder(orderUUID, user.getId());

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("orderNumber", orderNumber);
            result.put("status", acceptedOrder.getOrderStatus());
            result.put("message", "您已成功接单");
            result.put("deliveryFee", acceptedOrder.getDeliveryFee());
            result.put("expectedDeliveryTime", acceptedOrder.getExpectedDeliveryTime());

            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            logger.error("订单号格式无效: {}", e.getMessage());
            return ResponseEntity.badRequest().body("订单号格式无效");
        } catch (InvalidOperationException e) {
            logger.error("接单失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            logger.error("接单失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("接单失败: " + e.getMessage());
        }
    }

    /**
     * 查询可接单列表
     */
    @GetMapping("/orders/available")
    @Operation(summary = "查询可接单列表", description = "查询当前可以接单的订单列表")
    public ResponseEntity<?> getAvailableOrders(
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false, defaultValue = "3.0") Double distance,
            @RequestParam(required = false) DeliveryType deliveryType,
            Pageable pageable,
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到查询可接单列表请求, 位置: [{}, {}], 距离: {}", latitude, longitude, distance);

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
        }

        try {
            Page<ShoppingOrder> orders;

            // 如果提供了位置信息，则按距离筛选
            if (latitude != null && longitude != null) {
                orders = orderService.getAvailableOrdersByLocation(
                        latitude, longitude, distance, deliveryType, pageable);
            } else {
                // 否则返回所有可接单订单
                orders = orderService.getAvailableOrders(deliveryType, pageable);
            }

            Page<ShoppingOrderResponse> responseList = orders.map(ShoppingOrderResponse::fromOrder);
            return ResponseEntity.ok(responseList);
        } catch (Exception e) {
            logger.error("查询可接单列表失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("查询可接单列表失败: " + e.getMessage());
        }
    }
}