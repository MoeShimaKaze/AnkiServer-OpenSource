package com.server.anki;

import com.server.anki.email.VerificationCodeRepository;
import com.server.anki.auth.token.TokenBlacklistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class CleanupTask {

    private static final Logger logger = LoggerFactory.getLogger(CleanupTask.class);

    @Autowired
    private VerificationCodeRepository verificationCodeRepository;

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @Scheduled(cron = "0 0 3 * * *")
    public void cleanupExpiredVerificationCodes() {
        logger.info("Starting scheduled cleanup of expired verification codes and tokens");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoffTime = now.minusMinutes(10);

        try {
            verificationCodeRepository.deleteBySendTimeBefore(cutoffTime);
            logger.info("Deleted expired verification codes");
        } catch (Exception e) {
            logger.error("Error occurred while cleaning up expired verification codes", e);
        }

        cleanupExpiredTokens();
        logger.info("Completed scheduled cleanup task");
    }

    public void cleanupExpiredTokens() {
        logger.info("Starting cleanup of expired tokens in blacklist");
        try {
            tokenBlacklistService.purgeExpiredTokens();
        } catch (Exception e) {
            logger.error("Error occurred while cleaning up expired tokens", e);
        }
    }
}