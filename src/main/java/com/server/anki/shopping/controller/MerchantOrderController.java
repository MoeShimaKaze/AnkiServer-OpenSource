package com.server.anki.shopping.controller;

import com.server.anki.auth.AuthenticationService;
import com.server.anki.mailorder.enums.OrderStatus;
import com.server.anki.shopping.controller.response.ShoppingOrderResponse;
import com.server.anki.shopping.entity.ShoppingOrder;
import com.server.anki.shopping.entity.Store;
import com.server.anki.shopping.repository.StoreRepository;
import com.server.anki.shopping.service.ShoppingOrderService;
import com.server.anki.user.User;
import com.server.anki.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/merchant/orders")
@Tag(name = "商家订单管理", description = "商家订单确认、查询相关接口")
public class MerchantOrderController {
    private static final Logger logger = LoggerFactory.getLogger(MerchantOrderController.class);

    @Autowired
    private ShoppingOrderService orderService;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private UserService userService;

    @Autowired
    private StoreRepository storeRepository;

    @GetMapping("/store/{storeId}/pending-confirmation")
    @Operation(summary = "获取待商家确认的订单")
    public ResponseEntity<?> getPendingConfirmationOrders(
            @PathVariable Long storeId,
            Pageable pageable,
            HttpServletRequest request,
            HttpServletResponse response) {

        User currentUser = authenticationService.getAuthenticatedUser(request, response);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            // 首先检查店铺是否存在
            Optional<Store> storeOpt = storeRepository.findById(storeId);
            if (storeOpt.isEmpty()) {
                // 返回特定的错误消息，而不是抛出异常
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "店铺不存在，请先创建店铺");
                errorResponse.put("errorCode", "STORE_NOT_FOUND");
                errorResponse.put("needCreateStore", true);

                logger.warn("获取待确认订单失败: 店铺不存在, storeId: {}, userId: {}",
                        storeId, currentUser.getId());

                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(errorResponse);
            }

            // 检查当前用户是否有权限访问该店铺
            Store store = storeOpt.get();
            if (!store.getMerchant().getId().equals(currentUser.getId()) &&
                    !userService.isAdminUser(currentUser)) {

                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "您没有权限访问该店铺的订单");

                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(errorResponse);
            }

            // 只有在店铺存在且用户有权限的情况下才获取订单
            Page<ShoppingOrder> orders = orderService.getStorePendingConfirmationOrders(storeId, pageable);
            Page<ShoppingOrderResponse> responseList = orders.map(ShoppingOrderResponse::fromOrder);

            return ResponseEntity.ok(responseList);
        } catch (Exception e) {
            logger.error("获取待确认订单失败: {}", e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "获取待确认订单失败，请稍后重试");
            errorResponse.put("errorDetail", e.getMessage());

            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * 商家确认订单
     */
    @PostMapping("/{orderNumber}/confirm")
    @Operation(summary = "商家确认订单")
    public ResponseEntity<?> confirmOrder(
            @PathVariable UUID orderNumber,
            HttpServletRequest request,
            HttpServletResponse response) {

        User currentUser = authenticationService.getAuthenticatedUser(request, response);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            ShoppingOrder confirmedOrder = orderService.confirmOrderByMerchant(
                    orderNumber,
                    currentUser.getId()
            );

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("orderNumber", confirmedOrder.getOrderNumber());
            result.put("status", confirmedOrder.getOrderStatus());
            result.put("message", "订单确认成功");

            // 如果已分配配送员，添加配送信息
            if (confirmedOrder.getOrderStatus() == OrderStatus.ASSIGNED) {
                result.put("assignedUser", confirmedOrder.getAssignedUser().getUsername());
                result.put("expectedDeliveryTime", confirmedOrder.getExpectedDeliveryTime());
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("确认订单失败，订单号: {}, 错误: {}", orderNumber, e.getMessage(), e);
            return ResponseEntity.badRequest().body("确认订单失败: " + e.getMessage());
        }
    }

    /**
     * 获取商家店铺的所有订单
     */
    @GetMapping("/store/{storeId}/all")
    @Operation(summary = "获取商家店铺的所有订单")
    public ResponseEntity<?> getAllStoreOrders(
            @PathVariable Long storeId,
            @RequestParam(required = false) OrderStatus status,
            Pageable pageable,
            HttpServletRequest request,
            HttpServletResponse response) {

        User currentUser = authenticationService.getAuthenticatedUser(request, response);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            // 首先检查店铺是否存在
            Optional<Store> storeOpt = storeRepository.findById(storeId);
            if (storeOpt.isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "店铺不存在");
                errorResponse.put("errorCode", "STORE_NOT_FOUND");

                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(errorResponse);
            }

            // 检查当前用户是否有权限访问该店铺
            Store store = storeOpt.get();
            if (!store.getMerchant().getId().equals(currentUser.getId()) &&
                    !userService.isAdminUser(currentUser)) {

                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "您没有权限访问该店铺的订单");

                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(errorResponse);
            }

            // 获取订单
            Page<ShoppingOrder> orders;
            if (status != null) {
                orders = orderService.getOrdersByStoreAndStatus(storeId, status, pageable);
            } else {
                orders = orderService.getStoreOrders(storeId, pageable);
            }

            Page<ShoppingOrderResponse> responseList = orders.map(ShoppingOrderResponse::fromOrder);

            return ResponseEntity.ok(responseList);
        } catch (Exception e) {
            logger.error("获取店铺订单失败: {}", e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "获取订单列表失败，请稍后重试");
            errorResponse.put("errorDetail", e.getMessage());

            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * 处理订单退款请求
     */
    @PostMapping("/{orderNumber}/refund/process")
    @Operation(summary = "处理订单退款请求")
    public ResponseEntity<?> processRefundRequest(
            @PathVariable UUID orderNumber,
            @RequestParam boolean approved,
            @RequestParam(required = false) String remark,
            HttpServletRequest request,
            HttpServletResponse response) {

        User currentUser = authenticationService.getAuthenticatedUser(request, response);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            // 获取订单信息
            ShoppingOrder order = orderService.getOrderByNumber(orderNumber);

            // 验证用户权限（必须是店铺所有者或管理员）
            if (!order.getStore().getMerchant().getId().equals(currentUser.getId()) &&
                    !userService.isAdminUser(currentUser)) {

                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "您没有权限处理此订单的退款请求");

                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(errorResponse);
            }

            // 处理退款请求
            orderService.processRefundResult(orderNumber, approved, remark);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("orderNumber", orderNumber);
            result.put("status", approved ? "REFUNDED" : order.getOrderStatus());
            result.put("message", approved ? "退款申请已通过" : "退款申请已拒绝");

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("处理退款请求失败，订单号: {}, 错误: {}", orderNumber, e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "处理退款请求失败: " + e.getMessage());

            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
}
