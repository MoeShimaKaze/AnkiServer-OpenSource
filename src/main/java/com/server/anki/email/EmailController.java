package com.server.anki.email;

import com.server.anki.auth.ratelimit.RateLimit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

@Controller
@RequestMapping("/email")
public class EmailController {

    private static final Logger logger = LoggerFactory.getLogger(EmailController.class);

    @Autowired
    private EmailService emailService;

    @GetMapping("/sendVerificationCode")
    @RateLimit(rate = 30, timeUnit = TimeUnit.MINUTES, limitType = RateLimit.LimitType.IP)
    @ResponseBody
    public String sendVerificationCode(@RequestParam(required = false) String email) {
        logger.info("收到发送验证码到邮箱的请求: {}", email);
        return emailService.sendVerificationCode(email);
    }
}
