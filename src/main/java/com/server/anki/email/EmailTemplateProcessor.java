package com.server.anki.email;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.HashMap;
import java.util.Map;

@Component
public class EmailTemplateProcessor {

    private static final String VERIFICATION_TEMPLATE_PATH = "verification-email.html";

    @Autowired
    private TemplateEngine templateEngine;

    /**
     * 处理邮件模板
     * @param email 用户邮箱
     * @param verificationCode 验证码
     * @param expiryMinutes 验证码过期时间（分钟）
     * @return 处理后的邮件内容
     */
    public String getVerificationEmailContent(String email, String verificationCode, int expiryMinutes) {
        // 准备变量
        Context context = new Context();
        Map<String, Object> variables = new HashMap<>();
        variables.put("verificationCode", verificationCode);
        variables.put("expiryMinutes", expiryMinutes);
        variables.put("applicationName", "校园服务平台");  // 可替换为配置中的应用名称
        variables.put("currentYear", String.valueOf(java.time.Year.now().getValue()));

        context.setVariables(variables);

        // 使用模板引擎处理模板
        return templateEngine.process(VERIFICATION_TEMPLATE_PATH, context);
    }
}

