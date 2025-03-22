package com.server.anki.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

@Service
public class EmailService {
    // 日志记录器
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    // 验证码有效期（分钟）
    private static final int VERIFICATION_CODE_EXPIRY = 10;
    // 发送冷却时间（分钟）
    private static final int SEND_COOLDOWN = 2;
    // 验证码长度
    private static final int CODE_LENGTH = 6;
    // 最大重试次数
    private static final int MAX_RETRY_ATTEMPTS = 3;

    @Autowired
    private JavaMailSender javaMailSender;

    @Autowired
    private VerificationCodeRepository verificationCodeRepository;

    @Autowired
    private EmailTemplateProcessor emailTemplateProcessor;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${spring.application.name:校园服务平台}")
    private String applicationName;

    /**
     * 发送验证码邮件
     * 包含频率限制、验证码生成和发送逻辑
     */
    public String sendVerificationCode(String email) {
        logger.info("Received request to send verification code to email: {}", email);

        if (email == null || email.isEmpty()) {
            logger.warn("Attempt to send verification code with empty email");
            return "邮箱地址不能为空！";
        }

        // 检查发送频率限制
        if (isUnderCooldown(email)) {
            return "验证码已经发送，请稍后再试！";
        }

        // 清理过期验证码
        cleanupExpiredCodes(email);

        // 生成并保存新验证码
        String verificationCode = generateVerificationCode();
        VerificationCode newCode = saveVerificationCode(email, verificationCode);

        // 发送验证码邮件
        if (sendVerificationEmail(email, verificationCode)) {
            logger.info("Verification code sent successfully to email: {}", email);
            return "验证码已发送到您的邮箱，请注意查收！";
        } else {
            // 发送失败时删除已保存的验证码
            verificationCodeRepository.delete(newCode);
            logger.error("Failed to send verification code to email: {}", email);
            return "邮件发送失败，请稍后再试！";
        }
    }

    /**
     * 检查邮箱验证码是否有效
     */
    public boolean isEmailVerified(String email, String code) {
        List<VerificationCode> savedCodes = verificationCodeRepository.findFirstByEmailOrderBySendTimeDesc(email);
        if (!savedCodes.isEmpty()) {
            VerificationCode savedCode = savedCodes.get(0);
            boolean isValid = savedCode.getVerificationCode().equals(code) &&
                    savedCode.getSendTime().plusMinutes(VERIFICATION_CODE_EXPIRY).isAfter(LocalDateTime.now());

            // 如果验证成功，立即删除已使用的验证码
            if (isValid) {
                verificationCodeRepository.delete(savedCode);
            }
            return isValid;
        }
        return false;
    }

    /**
     * 检查是否在发送冷却时间内
     */
    private boolean isUnderCooldown(String email) {
        List<VerificationCode> existingCodes = verificationCodeRepository.findFirstByEmailOrderBySendTimeDesc(email);
        if (!existingCodes.isEmpty()) {
            VerificationCode latestCode = existingCodes.get(0);
            LocalDateTime cooldownTime = LocalDateTime.now().minusMinutes(SEND_COOLDOWN);
            return latestCode.getSendTime().isAfter(cooldownTime);
        }
        return false;
    }

    /**
     * 清理过期的验证码
     */
    private void cleanupExpiredCodes(String email) {
        List<VerificationCode> existingCodes = verificationCodeRepository.findFirstByEmailOrderBySendTimeDesc(email);
        if (!existingCodes.isEmpty()) {
            VerificationCode latestCode = existingCodes.get(0);
            LocalDateTime expiryTime = LocalDateTime.now().minusMinutes(VERIFICATION_CODE_EXPIRY);
            if (latestCode.getSendTime().isBefore(expiryTime)) {
                verificationCodeRepository.delete(latestCode);
            }
        }
    }

    /**
     * 生成验证码
     */
    private String generateVerificationCode() {
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    /**
     * 保存验证码到数据库
     */
    private VerificationCode saveVerificationCode(String email, String code) {
        VerificationCode newCode = new VerificationCode();
        newCode.setEmail(email);
        newCode.setVerificationCode(code);
        newCode.setSendTime(LocalDateTime.now());
        return verificationCodeRepository.save(newCode);
    }

    /**
     * 发送HTML格式的验证码邮件
     * 包含重试机制
     */
    // 发送验证码邮件
    @Retryable(
            value = {MessagingException.class},
            maxAttempts = MAX_RETRY_ATTEMPTS,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    private boolean sendVerificationEmail(String email, String verificationCode) {
        try {
            // 获取模板处理后的内容
            String emailContent = emailTemplateProcessor.getVerificationEmailContent(email, verificationCode, VERIFICATION_CODE_EXPIRY);

            // 创建邮件消息
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(email);
            helper.setFrom(fromEmail);
            helper.setSubject(applicationName + " - 验证码");
            helper.setText(emailContent, true);  // true表示使用HTML格式

            // 发送邮件
            javaMailSender.send(message);
            logger.info("HTML verification email sent successfully to: {}", email);
            return true;
        } catch (MessagingException e) {
            logger.error("Failed to send HTML verification email to: {}. Error: {}", email, e.getMessage());
            throw new EmailSendException("发送验证码邮件失败", e);
        }
    }
}