package com.server.anki.config;

import com.server.anki.mailorder.enums.DeliveryService;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.EnumMap;
import java.util.Map;

/**
 * 邮件订单配置类
 * 负责管理订单服务的业务规则配置
 */
@Setter
@Getter
@Configuration
@ConfigurationProperties(prefix = "mailorder")
public class MailOrderConfig {

    private static final Logger logger = LoggerFactory.getLogger(MailOrderConfig.class);

    // 服务配置映射
    private Map<DeliveryService, ServiceConfig> serviceConfigs = new EnumMap<>(DeliveryService.class);

    // 营业时间配置
    private BusinessHoursConfig businessHours = new BusinessHoursConfig();

    // 全局基础配置
    private double platformCoefficient = 1.2;  // 平台系数
    private double warningThreshold = 0.8;     // 预警阈值
    private int checkInterval = 60000;         // 检查间隔(毫秒)
    private int maxTimeoutThreshold = 5;       // 最大超时次数阈值

    // 关键统计配置
    private int reportRetentionDays = 30;      // 报表保留天数
    private int maxTopUsersCount = 10;         // 最大统计用户数

    // 订单超时配置
    private OrderTimeoutConfig orderTimeout = new OrderTimeoutConfig();

    public MailOrderConfig() {
        initializeDefaultConfigs();
    }

    /**
     * 初始化默认配置
     */
    private void initializeDefaultConfigs() {
        try {
            // 标准服务配置
            serviceConfigs.put(DeliveryService.STANDARD, new ServiceConfig());  // 使用默认配置

            // 快递服务的自定义配置
            serviceConfigs.put(DeliveryService.EXPRESS, new ServiceConfig(
                    20,     // 取件超时时间
                    60,     // 配送超时时间
                    15,     // 确认超时时间
                    60,     // 预计配送时间
                    3000,   // 最大配送距离
                    10.0    // 最大重量
            ));

            logger.info("服务配置初始化完成");
        } catch (Exception e) {
            // 如果初始化失败，使用默认配置
            serviceConfigs.put(DeliveryService.STANDARD, new ServiceConfig());
            serviceConfigs.put(DeliveryService.EXPRESS, new ServiceConfig());
            logger.error("配置初始化失败，使用默认配置: {}", e.getMessage(), e);
        }
    }

    /**
     * 配置验证
     */
    @PostConstruct
    public void validateConfig() {
        validateBusinessHoursConfig();
        validateServiceConfigs();
    }

    /**
     * 验证营业时间配置
     */
    private void validateBusinessHoursConfig() {
        BusinessHoursConfig config = this.businessHours;

        if (config.getOpenHour() < 0 || config.getOpenHour() > 23) {
            throw new IllegalStateException("营业开始时间必须在0-23之间");
        }

        if (config.getCloseHour() < 1 || config.getCloseHour() > 24) {
            throw new IllegalStateException("营业结束时间必须在1-24之间");
        }

        if (config.getMaxAdvanceBookingDays() <= 0) {
            throw new IllegalStateException("最大提前预约天数必须大于0");
        }

        if (config.getOpenHour() >= config.getCloseHour()) {
            throw new IllegalStateException("营业开始时间必须早于结束时间");
        }
    }

    /**
     * 验证服务配置
     */
    private void validateServiceConfigs() {
        for (Map.Entry<DeliveryService, ServiceConfig> entry : serviceConfigs.entrySet()) {
            ServiceConfig config = entry.getValue();
            if (config.getPickupTimeout() <= 0) {
                throw new IllegalStateException(
                        String.format("服务%s的取件超时时间必须大于0", entry.getKey()));
            }
            if (config.getMaxDistance() <= 0) {
                throw new IllegalStateException(
                        String.format("服务%s的最大配送距离必须大于0", entry.getKey()));
            }
        }
    }

    /**
     * 获取服务配置
     */
    public ServiceConfig getServiceConfig(DeliveryService service) {
        return serviceConfigs.getOrDefault(service, serviceConfigs.get(DeliveryService.STANDARD));
    }

    /**
     * 获取配送时间
     */
    public int getDeliveryTime(DeliveryService service) {
        ServiceConfig config = getServiceConfig(service);
        return config != null ? config.getDeliveryTime() : 60;
    }

    /**
     * 营业时间配置类
     */
    @Setter
    @Getter
    public static class BusinessHoursConfig {
        private int openHour = 7;                  // 营业开始时间（小时）
        private int closeHour = 22;                // 营业结束时间（小时）
        private int maxAdvanceBookingDays = 7;     // 最大提前预约天数
        private boolean enableBusinessHourCheck = true;  // 是否启用营业时间检查
        private String businessHourErrorMsg = "配送时间必须在营业时间(07:00-22:00)内";  // 营业时间错误提示
        private String advanceBookingErrorMsg = "配送时间不能超过7天";  // 预约时间错误提示
        private String pastTimeErrorMsg = "配送时间不能早于当前时间";  // 过期时间错误提示
    }

    /**
     * 订单超时配置类
     */
    @Setter
    @Getter
    public static class OrderTimeoutConfig {
        private int generalTimeout = 30;           // 通用超时时间（分钟）
        private int paymentTimeout = 30;           // 支付超时时间（分钟）
        private int assignmentTimeout = 15;        // 接单超时时间（分钟）
        private int pickupValidationTimeout = 10;  // 取件验证超时时间（分钟）
        private int deliveryValidationTimeout = 15;// 送达验证超时时间（分钟）
        private boolean enableTimeoutWarning = true; // 是否启用超时预警
        private int warningAdvanceTime = 5;        // 预警提前时间（分钟）
        private int maxRetryAttempts = 3;          // 最大重试次数
        private int retryInterval = 5;             // 重试间隔（分钟）
    }

    /**
     * 服务配置类
     */
    @Setter
    @Getter
    public static class ServiceConfig {
        private int pickupTimeout;         // 取件超时时间
        private int deliveryTimeout;       // 配送超时时间
        private int confirmationTimeout;   // 确认超时时间
        private double maxWeight;          // 最大重量限制
        private double maxDistance;        // 最大配送距离
        private int deliveryTime;          // 预计配送时间

        // 默认构造函数
        public ServiceConfig() {
            this.pickupTimeout = 45;          // 默认45分钟取件超时
            this.deliveryTimeout = 120;       // 默认120分钟配送超时
            this.confirmationTimeout = 30;    // 默认30分钟确认超时
            this.maxWeight = 20.0;            // 默认最大重量20kg
            this.maxDistance = 5000.0;        // 默认最大距离5000米
            this.deliveryTime = 120;          // 默认预计配送时间120分钟
        }

        // 有参构造函数
        public ServiceConfig(int pickupTimeout, int deliveryTimeout,
                             int confirmationTimeout, int deliveryTime,
                             double maxDistance, double maxWeight) {
            this.pickupTimeout = pickupTimeout;
            this.deliveryTimeout = deliveryTimeout;
            this.confirmationTimeout = confirmationTimeout;
            this.deliveryTime = deliveryTime;
            this.maxDistance = maxDistance;
            this.maxWeight = maxWeight;
        }
    }
}