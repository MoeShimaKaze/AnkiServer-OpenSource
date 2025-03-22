package com.server.anki.login;

import com.server.anki.auth.token.TokenService;
import com.server.anki.auth.ratelimit.RateLimit;
import com.server.anki.user.User;
import com.server.anki.user.UserDTO;
import com.server.anki.user.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@RestController
public class LoginController {

    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);

    @Autowired
    private UserRepository userRepository;

     @Autowired
     private PasswordEncoder passwordEncoder;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    /**
     * 登录接口：校验账号密码成功后，签发AccessToken & RefreshToken
     * 令牌通过HttpOnly Cookie传递，响应体只返回用户信息
     */
    @PostMapping("/login")
    @RateLimit(rate = 30, timeUnit = TimeUnit.MINUTES, limitType = RateLimit.LimitType.IP)
    public ResponseEntity<?> loginUser(@RequestBody LoginRequest request, HttpServletResponse response) {
        logger.info("正在处理登录请求，用户邮箱: {}", request.getEmail());

        try {
            // 1. 查找用户
            Optional<User> userOptional = userRepository.findByEmail(request.getEmail());
            if (userOptional.isEmpty()) {
                logger.warn("登录失败：未找到用户，邮箱: {}", request.getEmail());
                return ResponseEntity.status(401).body("用户不存在");
            }

            User user = userOptional.get();

            // 2. 检查账户状态
            if (!user.canLogin()) {
                logger.warn("登录尝试失败：账户已禁用，用户: {}", user.getEmail());
                return ResponseEntity.status(403).body("该账户已被禁止登录");
            }

            // 3. 验证密码
            if (!passwordEncoder.matches(request.getPassword(), user.getEncryptedPassword())) {
                logger.warn("登录失败：密码错误，用户: {}", user.getEmail());
                return ResponseEntity.status(401).body("密码错误");
            }

            // 4. 生成令牌对并设置到HttpOnly Cookie
            tokenService.generateTokenPair(user, response);

            // 5. 只返回用户信息
            UserDTO userDTO = convertToUserDTO(user);
            logger.info("用户登录成功: {}", user.getEmail());

            return ResponseEntity.ok(userDTO);

        } catch (Exception e) {
            logger.error("登录过程中发生错误", e);
            return ResponseEntity.status(500).body("登录处理失败，请稍后重试");
        }
    }

    /**
     * 登出接口：使 Access Token 失效并清除客户端 Cookie
     * 支持POST和GET请求，以处理前端可能的重定向情况
     */
    @RequestMapping(value = "/logout", method = {RequestMethod.POST, RequestMethod.GET})
    public ResponseEntity<?> logoutUser(HttpServletRequest request, HttpServletResponse response) {
        logger.info("正在处理登出请求，方法: {}", request.getMethod());

        try {
            // 获取令牌
            String accessToken = tokenService.extractAccessToken(request);
            String refreshToken = tokenService.extractRefreshToken(request);
            String username = null;

            // 使访问令牌失效
            if (accessToken != null) {
                username = tokenService.getUsernameFromToken(accessToken);
                tokenService.invalidateAccessToken(accessToken);
                logger.info("已使访问令牌失效");
            }

            // 使刷新令牌失效
            if (refreshToken != null) {
                if (username == null) {
                    username = tokenService.getUsernameFromToken(refreshToken);
                }
                if (username != null) {
                    // 清理所有该用户的授权信息
                    tokenService.getTokenBlacklistService().invalidateRefreshToken(username);
                    cleanupUserAuthData(username);
                    logger.info("已使用户 {} 的刷新令牌失效", username);
                }
            }

            // 清除所有认证相关Cookie
            clearAllAuthCookies(response);

            // 设置响应头防止缓存
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Expires", "0");

            logger.info("用户登出成功");
            return ResponseEntity.ok("登出成功");
        } catch (Exception e) {
            logger.error("登出过程中发生错误", e);
            // 即使发生错误，也要尝试清除Cookie
            clearAllAuthCookies(response);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("登出失败：" + e.getMessage());
        }
    }

    /**
     * 清除所有认证相关的Cookie
     */
    private void clearAllAuthCookies(HttpServletResponse response) {
        // 定义需要清理的所有Cookie名称
        String[] cookiesToClear = {
                "access_token",
                "refresh_token",
                "alipay_auth_state",
                "alipay_auth_token",
                "JSESSIONID"
        };

        for (String cookieName : cookiesToClear) {
            // 根路径Cookie
            Cookie cookie = new Cookie(cookieName, "");
            cookie.setMaxAge(0);
            cookie.setPath("/");
            cookie.setHttpOnly(true);
            response.addCookie(cookie);

            // API路径Cookie
            Cookie apiCookie = new Cookie(cookieName, "");
            apiCookie.setMaxAge(0);
            apiCookie.setPath("/api");
            apiCookie.setHttpOnly(true);
            response.addCookie(apiCookie);

            logger.debug("已清除Cookie: {}", cookieName);
        }
    }

    private void cleanupUserAuthData(String username) {
        // 清理用户相关的所有Redis数据
        String pattern = "*:" + username + "*";
        Set<String> keys = redisTemplate.keys(pattern);
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private UserDTO convertToUserDTO(User user) {
        return new UserDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRegistrationDate(),
                user.getBirthday(),
                user.getGender(),
                user.getUserVerificationStatus(),
                user.getUserGroup(),
                user.getSystemAccount(),
                user.canLogin(),
                user.getAvatarUrl(),
                user.getUserIdentity(),
                user.getAlipayUserId()
        );
    }

}
