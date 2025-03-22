package com.server.anki.auth.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.server.anki.utils.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 请求频率限制处理器
 */
@Component
public class RateLimiterHandler {

    private static final Logger logger = LoggerFactory.getLogger(RateLimiterHandler.class);

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 处理请求频率限制
     *
     * @param request 请求对象
     * @param response 响应对象
     * @param handler 处理器对象
     * @return 是否通过限流检查
     */
    public boolean handle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;
        Method method = handlerMethod.getMethod();

        // 获取方法上的RateLimit注解
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);
        if (rateLimit == null) {
            // 获取类上的RateLimit注解
            rateLimit = method.getDeclaringClass().getAnnotation(RateLimit.class);
            if (rateLimit == null) {
                return true;
            }
        }

        // 构建限流器的key
        String key = buildLimitKey(request, rateLimit, method);

        try {
            // 获取Redisson限流器
            RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);

            // 尝试初始化限流器（只有第一次需要）
            boolean initialized = rateLimiter.trySetRate(
                    RateType.OVERALL,
                    rateLimit.rate(),
                    rateLimit.timeValue(),
                    convertTimeUnit(rateLimit.timeUnit())
            );

            if (initialized) {
                logger.info("初始化限流器: key={}, rate={}, timeValue={}, timeUnit={}",
                        key, rateLimit.rate(), rateLimit.timeValue(), rateLimit.timeUnit());
            }

            // 尝试获取令牌
            boolean acquired = rateLimiter.tryAcquire(1);
            if (!acquired) {
                // 请求被限流，返回错误响应
                handleRateLimitExceeded(response);
                return false;
            }

            return true;
        } catch (Exception e) {
            logger.error("限流处理异常", e);
            return true; // 发生异常时，不进行限流
        }
    }

    /**
     * 构建限流器的唯一键
     */
    private String buildLimitKey(HttpServletRequest request, RateLimit rateLimit, Method method) {
        String prefix = "rate_limit:";
        String key = rateLimit.key();

        // 如果没有指定key，则使用方法的全限定名
        if (!StringUtils.hasText(key)) {
            key = method.getDeclaringClass().getName() + "." + method.getName();
        }

        // 根据限流类型构建不同的key
        if (rateLimit.limitType() == RateLimit.LimitType.IP) {
            String clientIp = IpUtil.getClientIp(request);
            return prefix + key + ":" + clientIp;
        }

        return prefix + key;
    }

    /**
     * 处理请求被限流的情况
     */
    private void handleRateLimitExceeded(HttpServletResponse response) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());

        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("code", HttpStatus.TOO_MANY_REQUESTS.value());
        result.put("message", "请求过于频繁，请稍后再试");

        response.getWriter().write(objectMapper.writeValueAsString(result));
        logger.warn("请求被限流");
    }

    /**
     * 转换TimeUnit为RateIntervalUnit
     */
    private RateIntervalUnit convertTimeUnit(TimeUnit timeUnit) {
        switch (timeUnit) {
            case SECONDS:
                return RateIntervalUnit.SECONDS;
            case MINUTES:
                return RateIntervalUnit.MINUTES;
            case HOURS:
                return RateIntervalUnit.HOURS;
            case DAYS:
                return RateIntervalUnit.DAYS;
            default:
                return RateIntervalUnit.SECONDS;
        }
    }
}