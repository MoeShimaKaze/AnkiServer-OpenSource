package com.server.anki.mailorder;

import com.server.anki.auth.AuthenticationService;
import com.server.anki.config.MailOrderConfig;
import com.server.anki.mailorder.dto.MailOrderDTO;
import com.server.anki.mailorder.dto.MailOrderUpdateDTO;
import com.server.anki.mailorder.entity.MailOrder;
import com.server.anki.mailorder.enums.DeliveryService;
import com.server.anki.mailorder.enums.OrderStatus;
import com.server.anki.mailorder.service.MailOrderService;
import com.server.anki.marketing.entity.SpecialDate;
import com.server.anki.marketing.entity.SpecialTimeRange;
import com.server.anki.pay.payment.PaymentResponse;
import com.server.anki.user.User;
import com.server.anki.user.UserService;
import com.server.anki.rating.Rating;
import com.server.anki.rating.RatingDTO;
import com.server.anki.rating.RatingType;
import com.server.anki.rating.OrderType;
import com.server.anki.rating.RatingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/mail-orders")
public class MailOrderController {

    private static final Logger logger = LoggerFactory.getLogger(MailOrderController.class);

    @Autowired
    private MailOrderService mailOrderService;

    @Autowired
    private UserService userService;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private MailOrderConfig mailOrderConfig;

    @Autowired
    private RatingService ratingService;

    @PostMapping("/create")
    public ResponseEntity<?> createMailOrder(@RequestBody MailOrderDTO mailOrderDTO,
                                             HttpServletRequest request,
                                             HttpServletResponse response) {
        logger.info("收到创建订单请求");

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            logger.warn("未授权的访问尝试创建订单");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
        }

        try {
            // 创建新订单对象并设置基本信息
            MailOrder mailOrder = new MailOrder();
            mailOrder.setUser(user);
            mailOrder.setName(mailOrderDTO.getName());
            mailOrder.setPickupCode(mailOrderDTO.getPickupCode());
            mailOrder.setTrackingNumber(mailOrderDTO.getTrackingNumber());
            mailOrder.setContactInfo(mailOrderDTO.getContactInfo());

            // 设置取件地址信息
            mailOrder.setPickupAddress(mailOrderDTO.getPickupAddress());
            mailOrder.setPickupLatitude(mailOrderDTO.getPickupLatitude());
            mailOrder.setPickupLongitude(mailOrderDTO.getPickupLongitude());
            mailOrder.setPickupDetail(mailOrderDTO.getPickupDetail());

            // 设置配送地址信息
            mailOrder.setDeliveryAddress(mailOrderDTO.getDeliveryAddress());
            mailOrder.setDeliveryLatitude(mailOrderDTO.getDeliveryLatitude());
            mailOrder.setDeliveryLongitude(mailOrderDTO.getDeliveryLongitude());
            mailOrder.setDeliveryDetail(mailOrderDTO.getDeliveryDetail());
            mailOrder.setDeliveryTime(LocalDateTime.parse(mailOrderDTO.getDeliveryTime().toString()));

            // 设置配送服务类型
            try {
                mailOrder.setDeliveryService(DeliveryService.fromCode(
                        Integer.parseInt(mailOrderDTO.getDeliveryService())
                ));
            } catch (NumberFormatException e) {
                logger.error("无效的配送服务代码: {}", mailOrderDTO.getDeliveryService(), e);
                return ResponseEntity.badRequest().body("无效的配送服务代码");
            }

            // 设置重量和大件标记
            mailOrder.setWeight(mailOrderDTO.getWeight());
            mailOrder.setLargeItem(mailOrderDTO.isLargeItem());

            // 创建订单并获取支付信息
            PaymentResponse paymentResponse =
                    mailOrderService.createMailOrderWithPayment(mailOrder);

            return ResponseEntity.ok(paymentResponse);

        } catch (Exception e) {
            logger.error("创建订单失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("创建订单失败: " + e.getMessage());
        }
    }

    @PutMapping("/assign/{orderNumber}")
    public ResponseEntity<String> assignOrder(@PathVariable UUID orderNumber, HttpServletRequest request, HttpServletResponse response) {
        logger.info("Received request to assign order: {}", orderNumber);
        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            logger.warn("Unauthorized attempt to assign order: {}", orderNumber);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Optional<MailOrder> mailOrderOptional = mailOrderService.findByOrderNumber(orderNumber);
        if (mailOrderOptional.isEmpty()) {
            logger.warn("Mail order not found: {}", orderNumber);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Mail order not found");
        }

        MailOrder mailOrder = mailOrderOptional.get();
        if (mailOrder.getDeliveryService() != DeliveryService.EXPRESS) {
            mailOrder.setAssignedUser(user);
            mailOrder.setOrderStatus(OrderStatus.ASSIGNED);

            mailOrderService.updateMailOrder(mailOrder);
            logger.info("Mail order {} assigned successfully to user: {}", orderNumber, user.getId());
            return ResponseEntity.ok("Mail order assigned successfully");
        } else {
            logger.warn("Attempt to assign EXPRESS delivery order: {}", orderNumber);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Cannot assign EXPRESS delivery order");
        }
    }

    @PutMapping("/in-transit/{orderNumber}")
    public ResponseEntity<String> markOrderInTransit(@PathVariable UUID orderNumber, HttpServletRequest request, HttpServletResponse response) {
        logger.info("Received request to mark order as in transit: {}", orderNumber);
        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            logger.warn("Unauthorized attempt to mark order as in transit: {}", orderNumber);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Optional<MailOrder> mailOrderOptional = mailOrderService.findByOrderNumber(orderNumber);
        if (mailOrderOptional.isEmpty()) {
            logger.warn("Mail order not found: {}", orderNumber);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Mail order not found");
        }

        MailOrder mailOrder = mailOrderOptional.get();
        // 根据配送类型设置其他字段
        if (mailOrder.getDeliveryService() == DeliveryService.EXPRESS) {
            mailOrder.setDeliveryTime(LocalDateTime.now()
                    .plusMinutes(mailOrderConfig.getDeliveryTime(mailOrder.getDeliveryService())));

            mailOrderService.updateMailOrder(mailOrder);
            logger.info("Mail order {} marked as in transit", orderNumber);
            return ResponseEntity.ok("Mail order marked as in transit");
        } else {
            logger.warn("Invalid attempt to mark order as in transit: {}", orderNumber);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Only assigned users can mark delivery orders as in transit");
        }
    }

    @PutMapping("/delivered/{orderNumber}")
    public ResponseEntity<String> markOrderDelivered(@PathVariable UUID orderNumber,
                                                     HttpServletRequest request, HttpServletResponse response) {
        logger.info("收到标记订单送达请求: {}", orderNumber);

        // 验证用户身份
        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            logger.warn("未授权的访问尝试标记订单送达: {}", orderNumber);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 查找订单
        Optional<MailOrder> mailOrderOptional = mailOrderService.findByOrderNumber(orderNumber);
        if (mailOrderOptional.isEmpty()) {
            logger.warn("未找到订单: {}", orderNumber);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("订单未找到");
        }

        MailOrder mailOrder = mailOrderOptional.get();

        // 验证配送员权限
        if (mailOrder.getAssignedUser() != null &&
                mailOrder.getAssignedUser().getId().equals(user.getId())) {
            // 更新订单状态
            mailOrder.setOrderStatus(OrderStatus.DELIVERED);
            mailOrder.setDeliveredDate(LocalDateTime.now());

            // 保存更新
            mailOrderService.updateMailOrder(mailOrder);
            logger.info("订单 {} 已标记为送达", orderNumber);
            return ResponseEntity.ok(
                    "订单已标记为已送达，状态更新通知将通过站内信发送"
            );
        } else {
            logger.warn("无效的标记送达尝试: {}", orderNumber);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("只有分配的配送员可以标记订单为已送达");
        }
    }

    @PutMapping("/complete/{orderNumber}")
    public ResponseEntity<String> completeOrder(@PathVariable UUID orderNumber,
                                                HttpServletRequest request, HttpServletResponse response) {
        logger.info("收到完成订单请求: {}", orderNumber);

        // 验证用户身份
        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            logger.warn("未授权的访问尝试完成订单: {}", orderNumber);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 查找订单
        Optional<MailOrder> mailOrderOptional = mailOrderService.findByOrderNumber(orderNumber);
        if (mailOrderOptional.isEmpty()) {
            logger.warn("未找到订单: {}", orderNumber);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("订单未找到");
        }

        MailOrder mailOrder = mailOrderOptional.get();

        // 验证订单所有者
        if (!mailOrder.getUser().getId().equals(user.getId())) {
            logger.warn("用户 {} 不是订单 {} 的请求者", user.getId(), orderNumber);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("您不是此订单的请求者");
        }

        // 验证订单状态
        if (mailOrder.getOrderStatus() != OrderStatus.DELIVERED) {
            logger.warn("尝试完成未送达的订单: {}", orderNumber);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("订单未标记为已送达");
        }

        try {
            // 完成订单处理
            mailOrderService.completeOrder(mailOrder);
            logger.info("订单 {} 完成处理成功", orderNumber);
            return ResponseEntity.ok(
                    "订单完成处理成功，收入结算及订单状态更新通知将通过站内信发送"
            );
        } catch (Exception e) {
            // 使用两个占位符，第 1 个是 orderNumber，第 2 个是 e.getMessage()
            // 最后一个参数 e 作为 Throwable 用于输出堆栈信息
            logger.error("完成订单时发生错误: {}, {}", orderNumber, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("完成订单失败：" + e.getMessage());
        }
    }


    @PutMapping("/intervene/{orderNumber}")
    public ResponseEntity<String> interveneOrder(@PathVariable UUID orderNumber,
                                                 HttpServletRequest request,
                                                 HttpServletResponse response) {
        logger.info("Received request to intervene order: {}", orderNumber);

        User user = authenticationService.getAuthenticatedUser(request, response);

        // 未登录，返回 401
        if (user == null) {
            logger.warn("No user authenticated, attempt to intervene order: {}", orderNumber);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 已登录但无管理员权限，返回 403
        if (!userService.isAdminUser(user)) {
            logger.warn("User {} not admin, attempt to intervene order: {}", user.getId(), orderNumber);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // 继续原来的业务逻辑
        Optional<MailOrder> mailOrderOptional = mailOrderService.findByOrderNumber(orderNumber);
        if (mailOrderOptional.isEmpty()) {
            logger.warn("Mail order not found: {}", orderNumber);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Mail order not found");
        }

        try {
            mailOrderService.setOrderToIntervention(orderNumber);
            logger.info("Mail order {} marked for platform intervention", orderNumber);
            return ResponseEntity.ok("Mail order marked for platform intervention");
        } catch (RuntimeException e) {
            logger.error("Error intervening order {}: {}", orderNumber, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @PutMapping("/refund/{orderNumber}")
    public ResponseEntity<?> setOrderToRefunding(@PathVariable UUID orderNumber,
                                                 HttpServletRequest request,
                                                 HttpServletResponse response) {
        logger.info("收到设置订单退款状态请求: {}", orderNumber);

        User user = authenticationService.getAuthenticatedUser(request, response);

        // 未登录，返回 401
        if (user == null) {
            logger.warn("未登录用户尝试设置订单退款状态: {}", orderNumber);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
        }

        // 已登录但无管理员权限，返回 403
        if (!userService.isAdminUser(user)) {
            logger.warn("用户 {} 无管理员权限，尝试设置订单退款状态: {}", user.getId(), orderNumber);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("没有管理员权限");
        }

        // 继续原来的业务逻辑
        try {
            mailOrderService.setOrderToRefunding(orderNumber);
            logger.info("订单 {} 已设置为退款状态", orderNumber);
            return ResponseEntity.ok("订单已设置为退款处理中状态，相关通知将通过站内信发送");
        } catch (RuntimeException e) {
            logger.error("设置订单 {} 退款状态时发生错误: {}", orderNumber, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }


    @PutMapping("/process-refund/{orderNumber}")
    public ResponseEntity<?> processRefund(@PathVariable UUID orderNumber,
                                           HttpServletRequest request,
                                           HttpServletResponse response) {
        logger.info("Received request to process refund for order: {}", orderNumber);

        User user = authenticationService.getAuthenticatedUser(request, response);

        // 未登录，返回 401
        if (user == null) {
            logger.warn("No user authenticated, attempt to process refund for order: {}", orderNumber);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
        }

        // 已登录但无管理员权限，返回 403
        if (!userService.isAdminUser(user)) {
            logger.warn("User {} not admin, attempt to process refund for order: {}", user.getId(), orderNumber);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("没有管理员权限");
        }

        // 继续原来的业务逻辑
        try {
            mailOrderService.processRefund(orderNumber);
            logger.info("Refund processed successfully for order: {}", orderNumber);
            return ResponseEntity.ok("退款已处理完成");
        } catch (RuntimeException e) {
            logger.error("Error processing refund for order {}: {}", orderNumber, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }


    /**
     * 获取待处理订单（分页）
     */
    @GetMapping("/pending")
    public ResponseEntity<Page<MailOrderDTO>> getPendingOrders(
            @PageableDefault() Pageable pageable,
            HttpServletRequest request,
            HttpServletResponse response) {
        logger.info("接收到获取分页待处理订单请求: 页码={}, 每页大小={}",
                pageable.getPageNumber(), pageable.getPageSize());

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            logger.warn("未授权尝试获取待处理订单");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Page<MailOrder> pendingOrders = mailOrderService.getPendingOrders(pageable);
        Page<MailOrderDTO> pendingOrderDTOs = pendingOrders.map(this::convertToDTO);

        logger.info("返回{}条待处理订单，页码：{}",
                pendingOrderDTOs.getNumberOfElements(), pendingOrderDTOs.getNumber());
        return ResponseEntity.ok(pendingOrderDTOs);
    }


    /**
     * 获取指定用户的订单（分页）
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<MailOrderDTO>> getUserOrders(
            @PathVariable Long userId,
            @PageableDefault() Pageable pageable,
            HttpServletRequest request,
            HttpServletResponse response) {
        logger.info("接收到获取用户{}的分页订单请求: 页码={}, 每页大小={}",
                userId, pageable.getPageNumber(), pageable.getPageSize());

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            logger.warn("未授权尝试获取用户{}的订单", userId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!user.getId().equals(userId) && !userService.isAdminUser(user)) {
            logger.warn("用户{}禁止访问用户{}的订单", user.getId(), userId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Page<MailOrder> userOrders = mailOrderService.getUserOrders(userId, pageable);
        Page<MailOrderDTO> userOrderDTOs = userOrders.map(this::convertToDTO);

        logger.info("返回用户{}的{}条订单，页码：{}",
                userId, userOrderDTOs.getNumberOfElements(), userOrderDTOs.getNumber());
        return ResponseEntity.ok(userOrderDTOs);
    }

    /**
     * 获取指定用户的已分配订单（分页）
     */
    @GetMapping("/assigned/{userId}")
    public ResponseEntity<Page<MailOrderDTO>> getAssignedOrdersForUser(
            @PathVariable Long userId,
            @PageableDefault() Pageable pageable,
            HttpServletRequest request,
            HttpServletResponse response) {
        logger.info("接收到获取用户{}的分页已分配订单请求: 页码={}, 每页大小={}",
                userId, pageable.getPageNumber(), pageable.getPageSize());

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            logger.warn("未授权尝试获取用户{}的已分配订单", userId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!user.getId().equals(userId) && !userService.isAdminUser(user)) {
            logger.warn("用户{}禁止访问用户{}的已分配订单", user.getId(), userId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Page<MailOrder> assignedOrders = mailOrderService.getAssignedOrders(userId, pageable);
        Page<MailOrderDTO> assignedOrderDTOs = assignedOrders.map(this::convertToDTO);

        logger.info("返回用户{}的{}条已分配订单，页码：{}",
                userId, assignedOrderDTOs.getNumberOfElements(), assignedOrderDTOs.getNumber());
        return ResponseEntity.ok(assignedOrderDTOs);
    }

    /**
     * 获取所有订单（分页）
     */
    @GetMapping("/all")
    public ResponseEntity<Page<MailOrderDTO>> getAllOrders(
            @PageableDefault() Pageable pageable,
            HttpServletRequest request,
            HttpServletResponse response) {
        logger.info("接收到获取所有分页订单请求: 页码={}, 每页大小={}",
                pageable.getPageNumber(), pageable.getPageSize());

        User user = authenticationService.getAuthenticatedUser(request, response);

        if (user == null) {
            logger.warn("未授权尝试获取所有订单");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!userService.isAdminUser(user)) {
            logger.warn("非管理员用户{}尝试获取所有订单", user.getId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Page<MailOrder> allOrders = mailOrderService.getAllOrders(pageable);
        Page<MailOrderDTO> allOrderDTOs = allOrders.map(this::convertToDTO);

        logger.info("返回{}条订单，页码：{}",
                allOrderDTOs.getNumberOfElements(), allOrderDTOs.getNumber());
        return ResponseEntity.ok(allOrderDTOs);
    }

    @GetMapping("/{orderNumber}")
    public ResponseEntity<?> getOrder(@PathVariable UUID orderNumber,
                                      HttpServletRequest request,
                                      HttpServletResponse response) {
        logger.info("Received request to get order: {}", orderNumber);

        User user = authenticationService.getAuthenticatedUser(request, response);

        // 1. 未登录 => 401
        if (user == null) {
            logger.warn("No user authenticated, attempt to get order: {}", orderNumber);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 2. 查询订单
        Optional<MailOrder> mailOrderOptional = mailOrderService.findByOrderNumber(orderNumber);
        if (mailOrderOptional.isEmpty()) {
            logger.warn("Order not found: {}", orderNumber);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        MailOrder mailOrder = mailOrderOptional.get();

        // 3. 如果是管理员，则只能查看平台介入状态的订单，否则 403
        if (userService.isAdminUser(user)) {
            if (mailOrder.getOrderStatus() != OrderStatus.PLATFORM_INTERVENTION) {
                logger.warn("Admin attempted to access non-intervened order: {}", orderNumber);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("This order is not under platform intervention.");
            }
        } else {
            // 4. 如果不是管理员，则只能查看自己创建的订单
            if (!mailOrder.getUser().getId().equals(user.getId())) {
                logger.warn("User {} attempted to access order {} belonging to user {}",
                        user.getId(), orderNumber, mailOrder.getUser().getId());
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }

        // 5. 返回订单 DTO
        logger.info("Returning order details for order: {}", orderNumber);
        return ResponseEntity.ok(convertToDTO(mailOrder));
    }

    @PutMapping("/update-intervened/{orderNumber}")
    public ResponseEntity<?> updateIntervenedOrder(@PathVariable UUID orderNumber,
                                                   @RequestBody MailOrderUpdateDTO updateDTO,
                                                   HttpServletRequest request,
                                                   HttpServletResponse response) {
        logger.info("Received request to update intervened order: {}", orderNumber);

        User user = authenticationService.getAuthenticatedUser(request, response);

        // 1. 未登录 => 401
        if (user == null) {
            logger.warn("No user authenticated, attempt to update intervened order: {}", orderNumber);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 2. 已登录但非管理员 => 403
        if (!userService.isAdminUser(user)) {
            logger.warn("User {} is not admin, attempt to update intervened order: {}", user.getId(), orderNumber);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // 3. 查询订单
        Optional<MailOrder> mailOrderOptional = mailOrderService.findByOrderNumber(orderNumber);
        if (mailOrderOptional.isEmpty()) {
            logger.warn("Order not found: {}", orderNumber);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Mail order not found");
        }

        MailOrder mailOrder = mailOrderOptional.get();

        // 4. 若订单不处于 PLATFORM_INTERVENTION 状态 => 403
        if (mailOrder.getOrderStatus() != OrderStatus.PLATFORM_INTERVENTION) {
            logger.warn("Attempt to update non-intervened order: {}", orderNumber);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("This order is not under platform intervention.");
        }

        // 5. 更新订单字段
        mailOrder.setName(updateDTO.getName());
        mailOrder.setContactInfo(updateDTO.getContactInfo());
        mailOrder.setPickupAddress(updateDTO.getPickupAddress());
        mailOrder.setPickupLatitude(updateDTO.getPickupLatitude());
        mailOrder.setPickupLongitude(updateDTO.getPickupLongitude());
        mailOrder.setPickupDetail(updateDTO.getPickupDetail());
        mailOrder.setDeliveryAddress(updateDTO.getDeliveryAddress());
        mailOrder.setDeliveryLatitude(updateDTO.getDeliveryLatitude());
        mailOrder.setDeliveryLongitude(updateDTO.getDeliveryLongitude());
        mailOrder.setDeliveryDetail(updateDTO.getDeliveryDetail());
        mailOrder.setWeight(updateDTO.getWeight());
        mailOrder.setLargeItem(updateDTO.isLargeItem());
        mailOrder.setFee(updateDTO.getFee());
        mailOrder.setOrderStatus(updateDTO.getOrderStatus());

        mailOrderService.updateMailOrder(mailOrder);
        logger.info("Intervened order {} updated successfully", orderNumber);
        return ResponseEntity.ok("Intervened mail order updated successfully");
    }

    /**
     * 获取当前用户的已分配订单（分页）
     */
    @GetMapping("/assigned")
    public ResponseEntity<Page<MailOrderDTO>> getAssignedOrders(
            @PageableDefault() Pageable pageable,
            HttpServletRequest request,
            HttpServletResponse response) {
        logger.info("接收到获取分页已分配订单请求: 页码={}, 每页大小={}",
                pageable.getPageNumber(), pageable.getPageSize());

        User user = authenticationService.getAuthenticatedUser(request, response);

        if (user == null) {
            logger.warn("未授权尝试获取已分配订单");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        boolean isAdmin = userService.isAdminUser(user);
        boolean isMessenger = "messenger".equalsIgnoreCase(user.getUserGroup());

        if (!isAdmin && !isMessenger) {
            logger.warn("用户{}既不是管理员也不是配送员，禁止获取已分配订单", user.getId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Page<MailOrder> assignedOrders = mailOrderService.getAssignedOrders(user.getId(), pageable);
        Page<MailOrderDTO> assignedOrderDTOs = assignedOrders.map(this::convertToDTO);

        logger.info("返回用户{}的{}条已分配订单，页码：{}",
                user.getId(), assignedOrderDTOs.getNumberOfElements(), assignedOrderDTOs.getNumber());
        return ResponseEntity.ok(assignedOrderDTOs);
    }

    @DeleteMapping("/{orderNumber}")
    public ResponseEntity<?> deleteOrder(@PathVariable UUID orderNumber,
                                         HttpServletRequest request,
                                         HttpServletResponse response) {
        logger.info("Received request to delete order: {}", orderNumber);

        User user = authenticationService.getAuthenticatedUser(request, response);

        // 1. 未登录 => 401
        if (user == null) {
            logger.warn("No user authenticated, attempt to delete order: {}", orderNumber);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 2. 已登录但非管理员 => 403
        if (!userService.isAdminUser(user)) {
            logger.warn("User {} not admin, attempt to delete order: {}", user.getId(), orderNumber);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // 3. 权限校验通过，执行删除逻辑
        try {
            mailOrderService.deleteAndAbandonOrder(orderNumber);
            logger.info("Order {} and related ratings deleted and abandoned successfully", orderNumber);
            return ResponseEntity.ok("订单及其相关评价已删除并移至废弃订单");
        } catch (Exception e) {
            logger.error("Error deleting and abandoning order {}: {}", orderNumber, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("删除订单失败：" + e.getMessage());
        }
    }

    @PostMapping("/{orderNumber}/rate")
    public ResponseEntity<?> rateOrder(@PathVariable UUID orderNumber,
                                       @RequestBody RatingDTO ratingDTO,
                                       HttpServletRequest request,
                                       HttpServletResponse response) {
        logger.info("收到评价快递代拿订单请求: {}", orderNumber);

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            // 设置默认的订单类型为快递代拿
            ratingDTO.setOrderType(OrderType.MAIL_ORDER);
            ratingDTO.setOrderNumber(orderNumber);

            // 自动确定评价类型
            if (ratingDTO.getRatingType() == null) {
                Optional<MailOrder> orderOptional = mailOrderService.findByOrderNumber(orderNumber);
                if (orderOptional.isPresent()) {
                    MailOrder order = orderOptional.get();
                    if (order.getDeliveryService() == DeliveryService.EXPRESS) {
                        ratingDTO.setRatingType(RatingType.SENDER_TO_PLATFORM);
                    } else if (order.getUser().getId().equals(user.getId())) {
                        ratingDTO.setRatingType(RatingType.SENDER_TO_DELIVERER);
                    } else if (order.getAssignedUser().getId().equals(user.getId())) {
                        ratingDTO.setRatingType(RatingType.DELIVERER_TO_SENDER);
                    } else {
                        return ResponseEntity.badRequest().body("无法确定评价类型");
                    }
                } else {
                    return ResponseEntity.notFound().build();
                }
            }

            Rating rating = ratingService.createRating(
                    user.getId(),
                    orderNumber,
                    ratingDTO.getComment(),
                    ratingDTO.getScore(),
                    ratingDTO.getRatingType(),
                    OrderType.MAIL_ORDER
            );

            return ResponseEntity.ok(rating);
        } catch (Exception e) {
            logger.error("创建评价失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // 在 MailOrderController 类中更新 convertToDTO 方法
    private MailOrderDTO convertToDTO(MailOrder order) {
        MailOrderDTO dto = new MailOrderDTO();

        // ------------------------------
        // 基础字段映射（直接来自 MailOrder 实体）
        // ------------------------------
        dto.setOrderNumber(order.getOrderNumber());
        dto.setOrderStatus(order.getOrderStatus().toString());
        dto.setName(order.getName());
        dto.setPickupCode(order.getPickupCode());
        dto.setTrackingNumber(order.getTrackingNumber());
        dto.setContactInfo(order.getContactInfo());
        dto.setPickupAddress(order.getPickupAddress());
        dto.setPickupLatitude(order.getPickupLatitude());
        dto.setPickupLongitude(order.getPickupLongitude());
        dto.setPickupDetail(order.getPickupDetail());
        dto.setDeliveryAddress(order.getDeliveryAddress());
        dto.setDeliveryLatitude(order.getDeliveryLatitude());
        dto.setDeliveryLongitude(order.getDeliveryLongitude());
        dto.setDeliveryDetail(order.getDeliveryDetail());
        dto.setDeliveryTime(order.getDeliveryTime());
        dto.setDeliveryService(order.getDeliveryService().toString());
        dto.setWeight(order.getWeight());
        dto.setLargeItem(order.isLargeItem());
        dto.setUserIncome(order.getUserIncome());
        dto.setCreatedAt(order.getCreatedAt());
        dto.setCompletionDate(order.getCompletionDate());
        dto.setUserId(order.getUser() != null ? order.getUser().getId() : null);
        dto.setAssignedUserId(order.getAssignedUser() != null ? order.getAssignedUser().getId() : null);
        dto.setPlatformIncome(order.getPlatformIncome());
        dto.setRegionMultiplier(order.getRegionMultiplier());
        dto.setDeliveryDistance(order.getDeliveryDistance());
        dto.setDeliveredDate(order.getDeliveredDate());

        // ------------------------------
        // 动态字段映射（需通过服务类查询）
        // ------------------------------

        // 1. 区域名称查询（使用 RegionService）
        dto.setPickupRegionName(
                mailOrderService.getRegionNameByCoordinates(
                        order.getPickupLatitude(),
                        order.getPickupLongitude()
                )
        );
        dto.setDeliveryRegionName(
                mailOrderService.getRegionNameByCoordinates(
                        order.getDeliveryLatitude(),
                        order.getDeliveryLongitude()
                )
        );

        // 2. 是否跨区域（根据区域名称判断）
        boolean isCrossRegion = !dto.getPickupRegionName().equals(dto.getDeliveryRegionName());
        dto.setCrossRegion(isCrossRegion);

        // 3. 特殊时段信息（通过 SpecialDateService 查询）
        SpecialTimeRange timeRange = mailOrderService.getApplicableTimeRange(order.getDeliveryTime());
        if (timeRange != null) {
            dto.setTimeRangeName(timeRange.getName());
            dto.setTimeRangeRate(timeRange.getRateMultiplier());
        } else {
            dto.setTimeRangeName("标准时段");
            dto.setTimeRangeRate(BigDecimal.ONE);
        }

        // 4. 特殊日期信息（通过 SpecialDateService 查询）
        SpecialDate specialDate = mailOrderService.getApplicableSpecialDate(order.getDeliveryTime());
        if (specialDate != null) {
            dto.setSpecialDateName(specialDate.getName());
            dto.setSpecialDateType(specialDate.getType().toString());
            dto.setSpecialDateRate(specialDate.getRateMultiplier());
        } else {
            dto.setSpecialDateName("无特殊日期");
            dto.setSpecialDateType("NONE");
            dto.setSpecialDateRate(BigDecimal.ONE);
        }

        logger.debug("订单转换完成: {}", order.getOrderNumber());
        return dto;
    }

}