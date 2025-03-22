package com.server.anki.alipay;

import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipaySystemOauthTokenRequest;
import com.alipay.api.request.AlipayUserInfoShareRequest;
import com.alipay.api.response.AlipaySystemOauthTokenResponse;
import com.alipay.api.response.AlipayUserInfoShareResponse;
import com.server.anki.auth.AuthenticationService;
import com.server.anki.config.AlipayConfig;
import com.server.anki.config.RedisConfig;
import com.server.anki.auth.token.TokenPair;
import com.server.anki.auth.token.TokenService;
import com.server.anki.user.User;
import com.server.anki.user.UserRepository;
import com.server.anki.user.enums.UserVerificationStatus;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 支付宝登录服务
 * 处理支付宝OAuth2.0授权登录相关的业务逻辑
 */
@Service
@Slf4j
public class AlipayLoginService {

    private static final Logger logger = LoggerFactory.getLogger(AlipayLoginService.class);

    @Autowired
    private AlipayClient alipayClient;

    @Autowired
    private AlipayConfig alipayConfig;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    @Qualifier("alipayRedisTemplate")
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private RedisConfig redisConfig;

    // 状态常量定义
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_PROCESSING = "processing";
    private static final String STATUS_COMPLETED = "completed";
    private static final String STATUS_FAILED = "failed";

    /**
     * 生成授权URL和state
     * 1. 生成唯一state并存储到Redis
     * 2. 构建支付宝授权URL
     */
    public AuthUrlInfo generateAuthUrlInfo() {
        try {
            String state = UUID.randomUUID().toString();
            String stateKey = RedisConfig.getAlipayStateKey(state);

            // 初始状态设置为pending
            redisTemplate.opsForValue().set(
                    stateKey,
                    STATUS_PENDING,
                    Duration.ofSeconds(redisConfig.getStateExpirationSeconds())
            );

            logger.info("Generated state for alipay auth: {}", state);

            String encodedRedirectUri = URLEncoder.encode(
                    alipayConfig.getOauth().getRedirectUri(),
                    StandardCharsets.UTF_8
            );

            String authUrl = String.format("%s?app_id=%s&scope=%s&redirect_uri=%s&state=%s",
                    alipayConfig.getOauth().getServerUrl(),
                    alipayConfig.getAppId(),
                    alipayConfig.getOauth().getScope(),
                    encodedRedirectUri,
                    state
            );

            return new AuthUrlInfo(authUrl, state);

        } catch (Exception e) {
            logger.error("Error generating auth URL: ", e);
            throw new RuntimeException("无法生成授权URL", e);
        }
    }

    /**
     * 处理用户重新授权
     * 当用户重新授权时调用，更新相关权限和状态
     */
    @Transactional
    public void handleUserReauthorized(String alipayUserId, String authToken) {
        logger.info("处理用户重新授权: {}", alipayUserId);

        try {
            // 查找用户
            Optional<User> userOpt = userRepository.findByAlipayUserId(alipayUserId);
            if (userOpt.isEmpty()) {
                logger.warn("未找到支付宝用户，无法更新授权: {}", alipayUserId);
                return;
            }

            User user = userOpt.get();

            // 更新用户授权状态
            user.setCanLogin(true);

            // 更新Redis中的授权令牌
            String tokenKey = "alipay:auth:token:" + alipayUserId;
            redisTemplate.opsForValue().set(tokenKey, authToken, Duration.ofDays(30));

            userRepository.save(user);

            logger.info("用户授权状态已更新: {}", alipayUserId);

        } catch (Exception e) {
            logger.error("处理用户重新授权失败: {}", e.getMessage(), e);
            throw new RuntimeException("处理用户重新授权失败", e);
        }
    }

    /**
     * 处理用户授权过期
     * 当用户授权过期时调用，更新相关状态
     */
    @Transactional
    public void handleUserAuthExpired(String alipayUserId) {
        logger.info("处理用户授权过期: {}", alipayUserId);

        try {
            // 查找用户
            Optional<User> userOpt = userRepository.findByAlipayUserId(alipayUserId);
            if (userOpt.isEmpty()) {
                logger.warn("未找到支付宝用户，无法处理授权过期: {}", alipayUserId);
                return;
            }

            // 从Redis中移除授权令牌
            String tokenKey = "alipay:auth:token:" + alipayUserId;
            redisTemplate.delete(tokenKey);

            logger.info("用户授权令牌已移除: {}", alipayUserId);

            // 注意：这里不需要禁用用户登录权限，因为不影响本地账号登录
            // 仅在用户尝试使用支付宝登录时检查授权状态

        } catch (Exception e) {
            logger.error("处理用户授权过期失败: {}", e.getMessage(), e);
            throw new RuntimeException("处理用户授权过期失败", e);
        }
    }

    /**
     * 处理支付宝回调登录 - 改进版本
     * 增加了状态管理和并发控制
     */
    @Transactional
    public LoginResponse handleAlipayLogin(String authCode, String state, HttpServletResponse response)
            throws Exception {
        // 验证state
        if (!validateState(state)) {
            logger.warn("Invalid state detected: {}", state);
            throw new AuthCodeStateException("无效的授权状态", "invalid_state");
        }

        // 更新state状态为processing
        updateStateStatus(state, STATUS_PROCESSING);

        // 获取分布式锁
        String lockKey = "alipay_login:" + authCode;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean locked = lock.tryLock(5, 30, TimeUnit.SECONDS);
            if (!locked) {
                throw new AuthCodeStateException("授权码正在处理中", "processing");
            }

            // 检查授权码状态
            String authCodeKey = RedisConfig.getAlipayAuthCodeKey(authCode);
            String status = redisTemplate.opsForValue().get(authCodeKey);

            // 如果授权码已经处理完成，生成新的令牌并返回用户信息
            if (STATUS_COMPLETED.equals(status)) {
                String username = redisTemplate.opsForValue().get(
                        RedisConfig.getAlipayUserMappingKey(authCode));
                if (username != null) {
                    Optional<User> userOptional = userRepository.findByUsername(username);
                    if (userOptional.isPresent()) {
                        User user = userOptional.get();

                        // 生成新的令牌对并设置到响应中
                        TokenPair tokenPair = tokenService.generateTokenPair(user, response);

                        // 创建认证响应对象，包含完整的认证信息
                        AuthenticationService.AuthResponse authResponse =
                                new AuthenticationService.AuthResponse(
                                        true,                    // 标记为已登录状态
                                        user.getUsername(),      // 用户名
                                        user.getId(),            // 用户ID
                                        user.getUserGroup()      // 用户组
                                );

                        // 返回包含认证信息的登录响应
                        return new LoginResponse(
                                "登录成功",                  // 状态消息
                                user.getId(),                // 用户ID
                                username,                    // 用户名
                                user.getEmail(),             // 邮箱
                                user.getUserGroup(),         // 用户组
                                user.getAvatarUrl(),         // 头像URL
                                authResponse,                // 认证信息
                                true                         // 标记已生成令牌
                        );
                    }
                }

                // 如果找不到用户信息，抛出异常
                logger. error("无法找到已完成授权的用户信息 - AuthCode: {}", authCode);
                throw new AuthCodeStateException("无法找到用户信息", "user_not_found");
            }

            // 标记授权码为处理中
            redisTemplate.opsForValue().set(
                    authCodeKey,
                    STATUS_PROCESSING,
                    Duration.ofSeconds(alipayConfig.getOauth().getAuthCodeExpirationSeconds())
            );

            try {
                // 获取支付宝访问令牌
                AlipaySystemOauthTokenResponse tokenResponse = getAccessToken(authCode);
                String accessToken = tokenResponse.getAccessToken();
                logger.debug("获取支付宝访问令牌成功: {}", accessToken);

                // 获取用户信息
                AlipayUserInfoShareResponse userInfoResponse = getUserInfo(accessToken);
                logger.debug("获取支付宝用户信息成功: {}", userInfoResponse.getUserId());

                // 查找或创建用户
                User user = findOrCreateAlipayUser(userInfoResponse);
                logger.info("用户处理完成: {}", user.getUsername());

                // 生成JWT令牌
                TokenPair tokenPair = tokenService.generateTokenPair(user, response);

                // 创建认证响应
                AuthenticationService.AuthResponse authResponse =
                        new AuthenticationService.AuthResponse(
                                true,
                                user.getUsername(),
                                user.getId(),
                                user.getUserGroup()
                        );

                // 保存用户映射关系
                String mappingKey = RedisConfig.getAlipayUserMappingKey(authCode);
                redisTemplate.opsForValue().set(
                        mappingKey,
                        user.getUsername(),
                        Duration.ofSeconds(alipayConfig.getOauth().getAuthCodeExpirationSeconds())
                );

                // 标记授权码处理完成
                redisTemplate.opsForValue().set(
                        authCodeKey,
                        STATUS_COMPLETED,
                        Duration.ofSeconds(alipayConfig.getOauth().getAuthCodeExpirationSeconds())
                );

                // 返回完整的登录响应
                return new LoginResponse(
                        "登录成功",
                        user.getId(),
                        user.getUsername(),
                        user.getEmail(),
                        user.getUserGroup(),
                        user.getAvatarUrl(),
                        authResponse,  // 包含认证信息
                        true  // 标记已生成token
                );

            } catch (Exception e) {
                // 标记授权码处理失败
                redisTemplate.opsForValue().set(
                        authCodeKey,
                        STATUS_FAILED,
                        Duration.ofSeconds(alipayConfig.getOauth().getAuthCodeExpirationSeconds())
                );
                throw e;
            }

        } finally {
            // 释放锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
            // 处理完成后将state标记为completed
            updateStateStatus(state, STATUS_COMPLETED);
        }
    }


    /**
     * 获取支付宝访问令牌
     */
    public AlipaySystemOauthTokenResponse getAccessToken(String authCode) throws Exception {
        logger.debug("开始获取访问令牌，授权码: {}", authCode);

        int maxRetries = 1;
        int currentRetry = 0;
        Exception lastException = null;

        while (currentRetry <= maxRetries) {
            try {
                AlipaySystemOauthTokenRequest tokenRequest = new AlipaySystemOauthTokenRequest();
                tokenRequest.setCode(authCode);
                tokenRequest.setGrantType("authorization_code");

                AlipaySystemOauthTokenResponse tokenResponse = alipayClient.execute(tokenRequest);

                if (tokenResponse.isSuccess()) {
                    return tokenResponse;
                }

                if ("isv.code-invalid".equals(tokenResponse.getSubCode())) {
                    logger.warn("授权码无效或已被使用: {}", authCode);
                    throw new IllegalArgumentException("授权码无效或已过期");
                }

                currentRetry++;
                if (currentRetry <= maxRetries) {
                    Thread.sleep(1000);
                }
                lastException = new RuntimeException(tokenResponse.getMsg());
            } catch (Exception e) {
                lastException = e;
                currentRetry++;
                if (currentRetry <= maxRetries) {
                    Thread.sleep(1000);
                }
            }
        }

        throw new RuntimeException("获取访问令牌失败", lastException);
    }

    /**
     * 获取支付宝用户信息
     */
    public AlipayUserInfoShareResponse getUserInfo(String accessToken) throws Exception {
        logger.debug("开始获取用户信息，访问令牌: {}", accessToken);

        AlipayUserInfoShareRequest userInfoRequest = new AlipayUserInfoShareRequest();
        AlipayUserInfoShareResponse userInfoResponse = alipayClient.execute(
                userInfoRequest,
                accessToken
        );

        if (!userInfoResponse.isSuccess()) {
            logger.error("获取用户信息失败: {}", userInfoResponse.getMsg());
            throw new RuntimeException("获取用户信息失败: " + userInfoResponse.getMsg());
        }

        return userInfoResponse;
    }

    /**
     * 查找或创建支付宝用户
     */
    private User findOrCreateAlipayUser(AlipayUserInfoShareResponse userInfo) {
        String alipayUserId = userInfo.getUserId();
        logger.debug("处理支付宝用户，用户ID: {}", alipayUserId);

        try {
            Optional<User> existingUser = userRepository.findByAlipayUserId(alipayUserId);

            if (existingUser.isPresent()) {
                User user = existingUser.get();
                updateUserInfo(user, userInfo);
                return user;
            }

            return createNewAlipayUser(userInfo);

        } catch (Exception e) {
            logger.error("处理支付宝用户时发生错误", e);
            throw new RuntimeException("创建支付宝用户失败", e);
        }
    }

    /**
     * 更新现有用户信息
     */
    private void updateUserInfo(User user, AlipayUserInfoShareResponse userInfo) {
        boolean needsUpdate = false;

        if (userInfo.getAvatar() != null && !userInfo.getAvatar().equals(user.getAvatarUrl())) {
            user.setAvatarUrl(userInfo.getAvatar());
            needsUpdate = true;
        }

        if (user.getEmail() == null && userInfo.getEmail() != null) {
            user.setEmail(userInfo.getEmail());
            needsUpdate = true;
        }

        String newGender = convertAlipayGender(userInfo.getGender());
        if (!newGender.equals(user.getGender())) {
            user.setGender(newGender);
            needsUpdate = true;
        }

        if (needsUpdate) {
            userRepository.save(user);
            logger.info("已更新用户信息，用户ID: {}", user.getId());
        }
    }

    /**
     * 创建新的支付宝用户
     */
    private User createNewAlipayUser(AlipayUserInfoShareResponse userInfo) {
        logger.info("开始创建新的支付宝用户，支付宝ID: {}", userInfo.getUserId());

        User newUser = new User();
        newUser.setAlipayUserId(userInfo.getUserId());
        newUser.setUsername(generateUsername(userInfo.getNickName(), userInfo.getUserId()));
        newUser.setEmail(userInfo.getEmail());
        newUser.setRegistrationDate(LocalDate.now());
        newUser.setUserGroup("user");
        newUser.setCanLogin(true);
        newUser.setUserVerificationStatus(UserVerificationStatus.UNVERIFIED);

        if (userInfo.getAvatar() != null) {
            newUser.setAvatarUrl(userInfo.getAvatar());
        }
        newUser.setGender(convertAlipayGender(userInfo.getGender()));

        User savedUser = userRepository.save(newUser);
        logger.info("成功创建新用户，用户ID: {}", savedUser.getId());

        return savedUser;
    }

    /**
     * 生成唯一用户名
     */
    private String generateUsername(String nickName, String alipayUserId) {
        if (alipayUserId == null || alipayUserId.trim().isEmpty()) {
            throw new IllegalArgumentException("支付宝用户ID不能为空");
        }

        String baseUsername;
        if (nickName != null && !nickName.trim().isEmpty()) {
            baseUsername = nickName.trim();
        } else {
            String userIdSuffix = alipayUserId.length() > 6
                    ? alipayUserId.substring(alipayUserId.length() - 6)
                    : alipayUserId;
            baseUsername = "用户" + userIdSuffix;
        }

        String username = baseUsername;
        int suffix = 1;
        int maxAttempts = 999;

        while (userRepository.existsByUsername(username)) {
            if (suffix > maxAttempts) {
                logger.warn("用户名生成达到最大尝试次数 ({}), 使用UUID生成随机用户名", maxAttempts);
                username = "用户" + UUID.randomUUID().toString().substring(0, 8);
                break;
            }
            username = baseUsername + suffix++;
        }

        logger.debug("生成用户名成功: {} (来源: {})", username,
                nickName != null ? "昵称" : "支付宝ID");
        return username;
    }

    /**
     * 验证state状态 - 改进版本
     * 允许pending和processing两种状态
     */
    private boolean validateState(String state) {
        String stateKey = RedisConfig.getAlipayStateKey(state);
        String stateValue = redisTemplate.opsForValue().get(stateKey);
        return STATUS_PENDING.equals(stateValue) || STATUS_PROCESSING.equals(stateValue);
    }

    /**
     * 更新state状态 - 新增方法
     */
    private void updateStateStatus(String state, String status) {
        String stateKey = RedisConfig.getAlipayStateKey(state);
        redisTemplate.opsForValue().set(stateKey, status,
                Duration.ofSeconds(redisConfig.getStateExpirationSeconds()));
        logger.debug("Updated state {} to status: {}", state, status);
    }

    /**
     * 转换支付宝性别格式
     */
    private String convertAlipayGender(String alipayGender) {
        if (alipayGender == null) {
            return "other";
        }
        return switch (alipayGender.toLowerCase()) {
            case "m" -> "male";
            case "f" -> "female";
            default -> "other";
        };
    }

    public Boolean checkAndMarkRequest(String processKey) {
        return redisTemplate.opsForValue()
                .setIfAbsent(processKey, "1", Duration.ofMinutes(5));
    }

    /**
     * 内部记录类：授权URL信息
     */
    public record AuthUrlInfo(String url, String state) {}

    /**
     * 内部记录类：登录响应
     */
    // 更新LoginResponse记录类以包含认证信息
    public record LoginResponse(
            String message,
            Long userId,
            String username,
            String email,
            String userGroup,
            String avatarUrl,
            AuthenticationService.AuthResponse authResponse,
            boolean tokenGenerated
    ) {}
}