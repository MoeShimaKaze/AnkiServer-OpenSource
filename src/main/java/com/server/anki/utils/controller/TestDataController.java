package com.server.anki.utils.controller;

import com.server.anki.auth.AuthenticationService;
import com.server.anki.user.User;
import com.server.anki.user.UserService;
import com.server.anki.utils.TimeoutStatisticsDataGenerator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试数据生成控制器
 * 提供接口用于生成测试数据（仅限管理员使用）
 */
@RestController
@RequestMapping("/api/test-data")
public class TestDataController {
    private static final Logger logger = LoggerFactory.getLogger(TestDataController.class);

    @Autowired
    private TimeoutStatisticsDataGenerator dataGenerator;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private UserService userService;

    /**
     * 生成超时统计系统测试数据
     * 仅限管理员使用
     * @param request HTTP请求
     * @param response HTTP响应
     * @return 生成结果
     */
    @PostMapping("/generate-timeout-data")
    public ResponseEntity<String> generateTimeoutData(HttpServletRequest request, HttpServletResponse response) {
        try {
            // 身份验证 - 检查用户是否已登录
            User user = authenticationService.getAuthenticatedUser(request, response);
            if (user == null) {
                logger.warn("未授权用户尝试生成测试数据");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
            }

            // 权限检查 - 检查用户是否为管理员
            if (!userService.isAdminUser(user)) {
                logger.warn("非管理员用户 {} 尝试生成测试数据", user.getId());
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("需要管理员权限才能生成测试数据");
            }

            // 通过身份验证和权限检查，开始生成测试数据
            logger.info("管理员 {} 开始生成测试数据", user.getId());
            int recordCount = dataGenerator.generateTestData();
            logger.info("测试数据生成完成，共生成 {} 条数据", recordCount);

            return ResponseEntity.ok(String.format("超时统计系统测试数据生成成功，已创建 %d 条订单数据", recordCount));
        } catch (Exception e) {
            logger.error("生成测试数据时发生错误", e);
            return ResponseEntity.internalServerError().body("生成测试数据时发生错误: " + e.getMessage());
        }
    }
}