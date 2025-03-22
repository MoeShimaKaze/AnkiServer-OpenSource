package com.server.anki.shopping.controller;

import com.server.anki.auth.AuthenticationService;
import com.server.anki.shopping.service.DashboardService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "统计面板", description = "商家和管理员统计数据接口")
public class DashboardController {
    private static final Logger logger = LoggerFactory.getLogger(DashboardController.class);

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private UserService userService;

    @GetMapping("/stats")
    @Operation(summary = "获取统计数据", description = "根据用户角色返回不同的统计数据")
    public ResponseEntity<?> getStatistics(HttpServletRequest request, HttpServletResponse response) {
        User currentUser = authenticationService.getAuthenticatedUser(request, response);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            Map<String, Object> statistics;
            if (userService.isAdminUser(currentUser)) {
                // 管理员用户，返回全平台统计数据
                statistics = dashboardService.getAdminStatistics();
            } else {
                // 商家用户，只返回自己店铺的统计数据
                statistics = dashboardService.getMerchantStatistics(currentUser.getId());
            }

            return ResponseEntity.ok(statistics);
        } catch (Exception e) {
            logger.error("获取统计数据失败: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("获取统计数据失败: " + e.getMessage());
        }
    }

    @GetMapping("/merchant/store/{storeId}/stats")
    @Operation(summary = "获取店铺统计数据", description = "获取特定店铺的统计数据")
    public ResponseEntity<?> getStoreStatistics(
            @PathVariable Long storeId,
            HttpServletRequest request,
            HttpServletResponse response) {

        User currentUser = authenticationService.getAuthenticatedUser(request, response);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            // 检查是否有权限查看该店铺的统计数据
            if (!dashboardService.hasStoreAccess(currentUser.getId(), storeId) && !userService.isAdminUser(currentUser)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("您没有权限访问该店铺的统计数据");
            }

            Map<String, Object> statistics = dashboardService.getStoreStatistics(storeId);
            return ResponseEntity.ok(statistics);
        } catch (Exception e) {
            logger.error("获取店铺统计数据失败: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("获取店铺统计数据失败: " + e.getMessage());
        }
    }
}