package com.server.anki.register;

import com.server.anki.auth.ratelimit.RateLimit;
import com.server.anki.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.concurrent.TimeUnit;

/**
 * 用户注册控制器
 */
@Controller
public class RegistrationController {

    private static final Logger logger = LoggerFactory.getLogger(RegistrationController.class);

    @Autowired
    private UserService userService;

    /**
     * 处理用户注册请求
     *
     * @param request 注册请求体
     * @return 注册结果
     */
    @PostMapping("/register")
    @RateLimit(rate = 30, timeUnit = TimeUnit.MINUTES, limitType = RateLimit.LimitType.IP)
    @ResponseBody
    public String registerUser(@RequestBody RegistrationRequest request) {
        // 收到用户注册请求日志
        logger.info("收到用户注册请求，邮箱：{}", request.getEmail());

        try {
            userService.registerUser(request);
            return "注册成功";
        } catch (IllegalArgumentException e) {
            // 参数校验失败警告（保留原始错误消息）
            logger.warn("用户注册失败：{}", e.getMessage());
            return "注册失败：" + e.getMessage();
        } catch (Exception e) {
            // 未知错误日志（包含异常堆栈）
            logger.error("用户注册过程中发生未知错误", e);
            return "注册失败：系统错误";
        }
    }
}
