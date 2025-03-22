package com.server.anki.shopping.controller;

import com.server.anki.auth.AuthenticationService;
import com.server.anki.shopping.controller.response.ProductResponse;
import com.server.anki.shopping.entity.Product;
import com.server.anki.shopping.service.AdminProductService;
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

/**
 * 商品审核控制器
 * 提供管理员审核商品的API接口
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/products")
@Tag(name = "商品审核管理", description = "管理员审核商品相关接口")
public class AdminProductController {
    private static final Logger logger = LoggerFactory.getLogger(AdminProductController.class);

    @Autowired
    private AdminProductService adminProductService;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private UserService userService;

    /**
     * 获取待审核商品列表
     */
    @GetMapping("/pending-review")
    @Operation(summary = "获取待审核商品列表", description = "管理员获取待审核商品列表，分页")
    public ResponseEntity<?> getPendingReviewProducts(
            Pageable pageable,
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到获取待审核商品列表请求");

        // 验证管理员权限
        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
        }

        if (!userService.isAdminUser(user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("只有管理员才能访问此资源");
        }

        try {
            // 获取待审核商品列表
            Page<Product> products = adminProductService.getPendingReviewProducts(pageable);
            Page<ProductResponse> responseList = products.map(ProductResponse::fromProduct);

            logger.info("获取待审核商品列表成功，数量: {}", products.getTotalElements());
            return ResponseEntity.ok(responseList);
        } catch (Exception e) {
            logger.error("获取待审核商品列表失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("获取待审核商品列表失败: " + e.getMessage());
        }
    }

    /**
     * 审核商品
     */
    @PostMapping("/{productId}/review")
    @Operation(summary = "审核商品", description = "管理员审核商品，通过或拒绝")
    public ResponseEntity<?> reviewProduct(
            @PathVariable Long productId,
            @RequestParam boolean approved,
            @RequestParam(required = false) String remarks,
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到商品审核请求，商品ID: {}, 是否通过: {}", productId, approved);

        // 验证管理员权限
        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
        }

        if (!userService.isAdminUser(user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("只有管理员才能审核商品");
        }

        try {
            // 审核商品
            Product product = adminProductService.reviewProduct(productId, approved, remarks);

            // 添加审核员ID
            product.setReviewerId(user.getId());
            product = adminProductService.updateProduct(product);

            ProductResponse productResponse = ProductResponse.fromProduct(product);

            // 构建响应
            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("success", true);
            responseMap.put("product", productResponse);
            responseMap.put("message", approved ? "商品已通过审核，已上架" : "商品未通过审核，已拒绝");

            logger.info("商品审核完成，商品ID: {}, 新状态: {}", productId, product.getStatus());
            return ResponseEntity.ok(responseMap);
        } catch (Exception e) {
            logger.error("商品审核失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("商品审核失败: " + e.getMessage());
        }
    }

    /**
     * 获取商品状态统计
     */
    @GetMapping("/status-count")
    @Operation(summary = "获取商品状态统计", description = "获取各状态商品数量统计")
    public ResponseEntity<?> getProductStatusCount(
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到获取商品状态统计请求");

        // 验证管理员权限
        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
        }

        if (!userService.isAdminUser(user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("只有管理员才能访问此资源");
        }

        try {
            // 获取商品状态统计
            AdminProductService.ProductStatusCount statusCount = adminProductService.getProductStatusCount();

            logger.info("获取商品状态统计成功，待审核: {}, 在售: {}, 缺货: {}, 已拒绝: {}",
                    statusCount.pendingReview(),
                    statusCount.onSale(),
                    statusCount.outOfStock(),
                    statusCount.rejected());

            return ResponseEntity.ok(statusCount);
        } catch (Exception e) {
            logger.error("获取商品状态统计失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("获取商品状态统计失败: " + e.getMessage());
        }
    }
}