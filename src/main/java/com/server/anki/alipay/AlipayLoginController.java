package com.server.anki.alipay;

import com.alipay.api.response.AlipaySystemOauthTokenResponse;
import com.alipay.api.response.AlipayUserInfoShareResponse;
import com.server.anki.auth.AuthenticationService;
import com.server.anki.auth.ratelimit.RateLimit;
import com.server.anki.user.User;
import com.server.anki.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/alipay/login")
public class AlipayLoginController {
    private static final Logger logger = LoggerFactory.getLogger(AlipayLoginController.class);

    @Autowired
    private AlipayLoginService alipayLoginService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private AuthenticationService authenticationService;

    @GetMapping("/url")
    @RateLimit(rate = 30, timeUnit = TimeUnit.MINUTES, limitType = RateLimit.LimitType.IP)
    public ResponseEntity<?> getAuthUrl() {
        try {
            logger.info("收到获取支付宝授权页面URL的请求");

            AlipayLoginService.AuthUrlInfo authUrlInfo = alipayLoginService.generateAuthUrlInfo();
            logger.info("生成授权信息 - State: {}", authUrlInfo.state());

            return ResponseEntity.ok(new AuthUrlResponse(
                    authUrlInfo.url(),
                    authUrlInfo.state()
            ));

        } catch (Exception e) {
            logger.error("获取支付宝授权页面URL失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(new ErrorResponse("获取授权页面失败", e.getMessage()));
        }
    }

    @GetMapping("/callback")
    @RateLimit(rate = 30, timeUnit = TimeUnit.MINUTES, limitType = RateLimit.LimitType.IP)
    public ResponseEntity<?> handleCallback(
            @RequestParam("auth_code") String authCode,
            @RequestParam("state") String state,
            HttpServletRequest request,  // 添加 HttpServletRequest 参数
            HttpServletResponse response) {
        try {
            // 添加详细的请求信息日志记录
            logger.info("收到支付宝授权回调请求 - 详细信息:");
            logger.info("Request URL: {}", request.getRequestURL().toString());
            logger.info("Query String: {}", request.getQueryString());
            logger.info("Referer: {}", request.getHeader("referer"));
            logger.info("User-Agent: {}", request.getHeader("user-agent"));
            logger.info("Client IP: {}", getClientIp(request));
            logger.info("Auth Code: {}", authCode);
            logger.info("State: {}", state);

            // 添加请求去重处理
            String requestId = authCode + "_" + state;
            String processKey = "alipay:callback:processed:" + requestId;

            Boolean isFirstProcess = alipayLoginService.checkAndMarkRequest(processKey);
            if (Boolean.FALSE.equals(isFirstProcess)) {
                logger.warn("检测到重复的回调请求 - RequestId: {}", requestId);
                return ResponseEntity.ok()
                        .body(Map.of(
                                "message", "请求已处理",
                                "status", "duplicate",
                                "requestId", requestId
                        ));
            }

            try {
                AlipayLoginService.LoginResponse loginResponse =
                        alipayLoginService.handleAlipayLogin(authCode, state, response);

                logger.info("支付宝登录成功，用户ID: {}", loginResponse.userId());
                return ResponseEntity.ok(loginResponse);

            } catch (AuthCodeStateException e) {
                logger.warn("授权码状态异常: {} (状态: {})", e.getMessage(), e.getStatus());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of(
                                "message", e.getMessage(),
                                "status", e.getStatus()
                        ));
            } catch (Exception e) {
                logger.error("处理支付宝登录失败: {}", e.getMessage(), e);
                return ResponseEntity.internalServerError()
                        .body(new ErrorResponse("登录失败", e.getMessage()));
            }
        } catch (Exception e) {
            logger.error("处理回调请求时发生异常", e);
            return ResponseEntity.internalServerError()
                    .body(new ErrorResponse("系统错误", "处理请求时发生异常"));
        }
    }

    // 获取客户端真实IP的辅助方法
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    private record AuthUrlResponse(String url, String state) {}
    private record ErrorResponse(String error, String message) {}

    /**
     * 获取支付宝绑定授权URL
     */
    @GetMapping("/bind-url")
    public ResponseEntity<?> getBindAuthUrl(HttpServletRequest request, HttpServletResponse response) {
        try {
            logger.info("收到获取支付宝绑定授权页面URL的请求");

            // 验证用户登录状态
            User user = authenticationService.getAuthenticatedUser(request, response);
            if (user == null) {
                logger.warn("未登录用户尝试获取支付宝绑定URL");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                        "success", false,
                        "message", "请先登录后再操作"
                ));
            }

            // 生成授权URL和状态码
            AlipayLoginService.AuthUrlInfo authUrlInfo = alipayLoginService.generateAuthUrlInfo();
            logger.info("生成绑定授权信息 - State: {}, 用户ID: {}", authUrlInfo.state(), user.getId());

            // 在Redis中存储用户ID和state的关联，用于回调时识别是绑定操作
            String bindKey = "alipay:bind:" + authUrlInfo.state();
            redisTemplate.opsForValue().set(bindKey, user.getId().toString(), Duration.ofMinutes(10));

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "url", authUrlInfo.url(),
                    "state", authUrlInfo.state()
            ));

        } catch (Exception e) {
            logger.error("获取支付宝绑定页面URL失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "message", "获取授权页面失败: " + e.getMessage()
                    ));
        }
    }

    /**
     * 处理支付宝绑定回调
     */
    @GetMapping("/bind-callback")
    public ResponseEntity<?> handleBindCallback(
            @RequestParam("auth_code") String authCode,
            @RequestParam("state") String state,
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到支付宝绑定回调请求 - State: {}, Auth Code: {}", state, authCode);

        try {
            // 验证state并获取用户ID
            String bindKey = "alipay:bind:" + state;
            String userIdStr = redisTemplate.opsForValue().get(bindKey);

            if (userIdStr == null) {
                logger.warn("无效的绑定状态: {}", state);
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "无效的绑定请求或链接已过期"
                ));
            }

            Long userId = Long.parseLong(userIdStr);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> {
                        logger.error("找不到对应的用户: {}", userId);
                        return new RuntimeException("用户不存在");
                    });

            // 获取支付宝访问令牌
            AlipaySystemOauthTokenResponse tokenResponse = alipayLoginService.getAccessToken(authCode);
            String accessToken = tokenResponse.getAccessToken();

            // 获取支付宝用户信息
            AlipayUserInfoShareResponse userInfoResponse = alipayLoginService.getUserInfo(accessToken);
            String alipayUserId = userInfoResponse.getUserId();

            // 检查支付宝账号是否已被其他用户绑定
            Optional<User> existingUser = userRepository.findByAlipayUserId(alipayUserId);
            if (existingUser.isPresent() && !existingUser.get().getId().equals(userId)) {
                logger.warn("支付宝账号 {} 已被其他用户绑定", alipayUserId);
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "该支付宝账号已被其他用户绑定"
                ));
            }

            // 绑定支付宝账号
            user.setAlipayUserId(alipayUserId);
            userRepository.save(user);

            // 删除Redis中的绑定记录
            redisTemplate.delete(bindKey);

            logger.info("用户 {} 成功绑定支付宝账号 {}", userId, alipayUserId);

            // 构建响应数据
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "支付宝账号绑定成功");
            result.put("alipayUserId", alipayUserId);

            // 添加部分用户信息（如果有）
            if (userInfoResponse.getNickName() != null) {
                result.put("nickname", userInfoResponse.getNickName());
            }
            if (userInfoResponse.getAvatar() != null) {
                result.put("avatar", userInfoResponse.getAvatar());
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("处理支付宝绑定回调失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "message", "绑定失败: " + e.getMessage()
                    ));
        }
    }
}