package com.server.anki.fee.core;

import com.server.anki.fee.model.FeeType;
import com.server.anki.fee.model.TimeoutType;
import com.server.anki.marketing.SpecialDateService;
import com.server.anki.marketing.region.RegionService;
import com.server.anki.marketing.region.model.RegionRateResult;
import com.server.anki.shopping.enums.MerchantLevel;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

/**
 * 费用计算配置类
 * 统一管理费用计算相关的配置参数，包括基础费率、特殊费率和各类服务费用
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "fee")
public class FeeConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(FeeConfiguration.class);

    // 基础费率配置
    private Map<FeeType, RateConfig> rateConfigs = new EnumMap<>(FeeType.class);

    // 平台费率配置
    private Map<MerchantLevel, BigDecimal> platformRates;

    // 距离费率配置
    private DistanceConfig distanceConfig;

    // 超时费用配置
    private TimeoutConfig timeoutConfig;

    // 费用分配配置
    private DistributionRateConfig distributionRates;

    // 增值服务配置
    private ValueAddedServiceConfig valueAddedServiceConfig;

    // 特殊日期和假期费率配置
    private SpecialDateConfig specialDateConfig = new SpecialDateConfig();

    @Autowired
    private RegionService regionService;

    @Autowired
    @Lazy
    private SpecialDateService specialDateService;

    /**
     * 距离费率配置类
     */
    @Data
    public static class DistanceConfig {
        private double baseFreeDistance;      // 基础免费距离
        private Map<FeeType, BigDecimal> ratePerKm;  // 每公里费率
        private Map<FeeType, Double> maxDistance;    // 最大配送距离
    }

    /**
     * 超时费用配置类
     */
    @Data
    public static class TimeoutConfig {
        private Map<FeeType, Map<TimeoutType, BigDecimal>> timeoutFees; // 超时费用
        private Map<FeeType, BigDecimal> largeItemTimeoutMultipliers;   // 大件超时倍率
        private Map<FeeType, BigDecimal> weightTimeoutMultipliers;      // 重量超时倍率
        private BigDecimal holidayMultiplier;                           // 节假日倍率

        private Map<FeeType, Integer> pickupTimeout;      // 取件超时时间限制(分钟)
        private Map<FeeType, Integer> deliveryTimeout;    // 配送超时时间限制(分钟)
        private Map<FeeType, Integer> confirmationTimeout;// 确认超时时间限制(分钟)
        private int maxHourlyIncrements;                 // 最大累计小时数
        private double hourlyIncrementRate;              // 每小时递增率
    }

    /**
     * 费用分配配置类
     */
    @Data
    public static class DistributionRateConfig {
        private Map<FeeType, BigDecimal> platformRates;  // 平台费率
        private Map<FeeType, BigDecimal> deliveryRates;  // 配送费率
        private Map<FeeType, BigDecimal> merchantRates;  // 商家费率
    }



    /**
     * 增值服务配置类
     */
    @Data
    public static class ValueAddedServiceConfig {
        private Map<FeeType, BigDecimal> insuranceRates;     // 保险费率
        private Map<FeeType, BigDecimal> signatureServiceFees; // 签名服务费
        private Map<FeeType, BigDecimal> packagingServiceFees; // 包装服务费
    }

    /**
     * 费率配置类
     */
    @Data
    public static class RateConfig {
        private BigDecimal baseRate;           // 基础费率
        private BigDecimal serviceRate;        // 服务费率
        private BigDecimal largeItemRate;      // 大件费率
        private BigDecimal weightRate;         // 重量费率
        private BigDecimal insuranceRate;      // 保险费率
    }

    /**
     * 特殊日期配置类
     */
    @Data
    public static class SpecialDateConfig {
        private boolean enableHolidayMultiplier = true;    // 是否启用假日费率
        private boolean enableSpecialDateRate = true;      // 是否启用特殊日期费率
        private boolean enableSpecialTimeMultiplier = true; // 是否启用特殊时段费率
        private BigDecimal holidayMultiplier = BigDecimal.valueOf(1.5); // 假日费率倍数
        private HolidayApiConfig holidayApi = new HolidayApiConfig();   // API配置
    }

    /**
     * 假日API配置类
     */
    @Data
    public static class HolidayApiConfig {
        private String url = "http://api.haoshenqi.top/holiday";  // API地址
        private String cron = "0 0 1 * * ?";           // 更新时间
        private int batchMonths = 3;                   // 批量更新月数
        private String cachePrefix = "holiday:";        // 节假日缓存前缀
        private int cacheDuration = 86400;             // 缓存时间（秒）
    }

    /**
     * 初始化配置
     */
    @PostConstruct
    public void init() {
        initializeDefaultConfigs();
        validateConfig();
    }

    /**
     * 初始化默认配置值
     */
    private void initializeDefaultConfigs() {
        if (rateConfigs.isEmpty()) {
            initializeDefaultRateConfigs();
        }
        if (specialDateConfig == null) {
            specialDateConfig = new SpecialDateConfig();
        }
    }

    /**
     * 初始化默认费率配置
     */
    private void initializeDefaultRateConfigs() {
        for (FeeType feeType : FeeType.values()) {
            RateConfig config = new RateConfig();
            config.setBaseRate(BigDecimal.valueOf(feeType.getDefaultServiceRate()));
            config.setServiceRate(BigDecimal.valueOf(0.1));
            config.setLargeItemRate(BigDecimal.valueOf(1.5));
            config.setWeightRate(BigDecimal.valueOf(0.5));
            config.setInsuranceRate(BigDecimal.valueOf(0.01));

            rateConfigs.put(feeType, config);
        }
    }

    /**
     * 验证配置有效性
     */
    private void validateConfig() {
        validateRateConfigs();
        validateSpecialDateConfig();
        validateDistanceConfig();
        validateTimeoutConfig();
    }

    /**
     * 验证费率配置
     */
    private void validateRateConfigs() {
        if (rateConfigs == null || rateConfigs.isEmpty()) {
            throw new IllegalStateException("费率配置不能为空");
        }

        for (Map.Entry<FeeType, RateConfig> entry : rateConfigs.entrySet()) {
            RateConfig config = entry.getValue();
            if (config.getBaseRate() == null || config.getBaseRate().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException(
                        String.format("费用类型[%s]的基础费率必须大于0", entry.getKey()));
            }
        }
    }

    /**
     * 验证特殊日期配置
     */
    private void validateSpecialDateConfig() {
        if (specialDateConfig.getHolidayMultiplier().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("假日费率倍数必须大于0");
        }

        HolidayApiConfig apiConfig = specialDateConfig.getHolidayApi();
        if (apiConfig.getUrl() == null || apiConfig.getUrl().trim().isEmpty()) {
            throw new IllegalStateException("假日API地址不能为空");
        }
    }

    /**
     * 验证距离配置
     */
    private void validateDistanceConfig() {
        if (distanceConfig == null) {
            throw new IllegalStateException("距离费率配置不能为空");
        }
        if (distanceConfig.getBaseFreeDistance() < 0) {
            throw new IllegalStateException("基础免费距离不能小于0");
        }
    }

    /**
     * 验证超时配置
     */
    private void validateTimeoutConfig() {
        if (timeoutConfig == null) {
            throw new IllegalStateException("超时费用配置不能为空");
        }
    }

    // 以下是所有原有的Getter方法

    public BigDecimal getPickupTimeoutFee(FeeType feeType) {
        return timeoutConfig.getTimeoutFees().get(feeType).get(TimeoutType.PICKUP);
    }

    public BigDecimal getDeliveryTimeoutFee(FeeType feeType) {
        return timeoutConfig.getTimeoutFees().get(feeType).get(TimeoutType.DELIVERY);
    }

    public BigDecimal getConfirmationTimeoutFee(FeeType feeType) {
        return timeoutConfig.getTimeoutFees().get(feeType).get(TimeoutType.CONFIRMATION);
    }

    public BigDecimal getLargeItemTimeoutMultiplier(FeeType feeType) {
        return timeoutConfig.getLargeItemTimeoutMultipliers().get(feeType);
    }

    public BigDecimal getTimeoutWeightMultiplier(FeeType feeType) {
        return timeoutConfig.getWeightTimeoutMultipliers().get(feeType);
    }

    public BigDecimal getHolidayMultiplier() {
        return timeoutConfig.getHolidayMultiplier();
    }

    public boolean isHoliday(LocalDate date) {
        return specialDateService.isHoliday(date);
    }

    public BigDecimal getBaseRate(FeeType feeType) {
        return rateConfigs.get(feeType).getBaseRate();
    }

    public BigDecimal getServiceRate(FeeType feeType) {
        return rateConfigs.get(feeType).getServiceRate();
    }

    public BigDecimal getLargeItemMultiplier(FeeType feeType) {
        return rateConfigs.get(feeType).getLargeItemRate();
    }

    public BigDecimal getWeightMultiplier(FeeType feeType) {
        RateConfig config = rateConfigs.get(feeType);
        if (config == null) {
            return BigDecimal.valueOf(0.5);
        }
        return config.getWeightRate();
    }

    public BigDecimal getDistanceRate(FeeType feeType) {
        return distanceConfig.getRatePerKm().get(feeType);
    }

    public double getBaseFreeDistance(FeeType feeType) {
        return distanceConfig.getBaseFreeDistance();
    }

    public double getMaxDeliveryDistance(FeeType feeType) {
        return distanceConfig.getMaxDistance().get(feeType);
    }

    public BigDecimal getMerchantRate(FeeType feeType, MerchantLevel level) {
        return platformRates.get(level);
    }


    public int getPickupTimeout(FeeType feeType) {
        return timeoutConfig.getPickupTimeout().get(feeType);
    }

    public int getDeliveryTimeout(FeeType feeType) {
        return timeoutConfig.getDeliveryTimeout().get(feeType);
    }

    public int getConfirmationTimeout(FeeType feeType) {
        return timeoutConfig.getConfirmationTimeout().get(feeType);
    }

    public int getMaxHourlyIncrements() {
        return timeoutConfig.getMaxHourlyIncrements();
    }

    public double getHourlyIncrementRate() {
        return timeoutConfig.getHourlyIncrementRate();
    }

    public BigDecimal getPlatformRate(FeeType feeType) {
        return distributionRates.getPlatformRates().get(feeType);
    }

    public BigDecimal getDeliveryRate(FeeType feeType) {
        return distributionRates.getDeliveryRates().get(feeType);
    }

    public BigDecimal getRegionMultiplier(Double pickupLat, Double pickupLng,
                                          Double deliveryLat, Double deliveryLng,
                                          FeeType feeType) {
        String pickupCoordinate = String.format("%.6f,%.6f", pickupLng, pickupLat);
        String deliveryCoordinate = String.format("%.6f,%.6f", deliveryLng, deliveryLat);

        RegionRateResult rateResult = regionService.calculateOrderRegionRate(
                pickupCoordinate, deliveryCoordinate);

        return BigDecimal.valueOf(rateResult.finalRate());
    }

    public BigDecimal getInsuranceRate(FeeType feeType) {
        return valueAddedServiceConfig.getInsuranceRates().get(feeType);
    }

    public BigDecimal getSignatureServiceFee(FeeType feeType) {
        return valueAddedServiceConfig.getSignatureServiceFees().get(feeType);
    }

    public BigDecimal getPackagingServiceFee(FeeType feeType) {
        return valueAddedServiceConfig.getPackagingServiceFees().get(feeType);
    }

// 新增的特殊日期相关方法

    /**
     * 检查假日费率功能是否启用
     */
    public boolean isHolidayMultiplierEnabled() {
        return specialDateConfig.isEnableHolidayMultiplier();
    }

    /**
     * 检查特殊日期费率功能是否启用
     */
    public boolean isSpecialDateRateEnabled() {
        return specialDateConfig.isEnableSpecialDateRate();
    }

    /**
     * 检查特殊时段费率功能是否启用
     */
    public boolean isSpecialTimeMultiplierEnabled() {
        return specialDateConfig.isEnableSpecialTimeMultiplier();
    }

    /**
     * 获取节假日费率倍数
     * 如果节假日费率功能未启用,返回1.0
     */
    public BigDecimal getHolidayRateMultiplier() {
        if (!isHolidayMultiplierEnabled()) {
            return BigDecimal.ONE;
        }
        return specialDateConfig.getHolidayMultiplier();
    }

    /**
     * 获取假日API的URL地址
     */
    public String getHolidayApiUrl() {
        return specialDateConfig.getHolidayApi().getUrl();
    }

    /**
     * 获取假日数据更新的Cron表达式
     */
    public String getHolidayUpdateCron() {
        return specialDateConfig.getHolidayApi().getCron();
    }

    /**
     * 获取假日数据批量更新的月数
     */
    public int getHolidayBatchMonths() {
        return specialDateConfig.getHolidayApi().getBatchMonths();
    }

    /**
     * 获取假日缓存的键前缀
     */
    public String getHolidayCachePrefix() {
        return specialDateConfig.getHolidayApi().getCachePrefix();
    }

    /**
     * 获取假日缓存的过期时间(秒)
     */
    public int getHolidayCacheDuration() {
        return specialDateConfig.getHolidayApi().getCacheDuration();
    }





    /**
     * 验证指定的日期时间是否在限制范围内
     * @param dateTime 要验证的日期时间
     * @return 验证结果消息,如果验证通过返回null
     */
    public String validateDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "日期时间不能为空";
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime maxFutureTime = now.plusMonths(getHolidayBatchMonths());

        if (dateTime.isBefore(now)) {
            return "不能设置过去的时间";
        }

        if (dateTime.isAfter(maxFutureTime)) {
            return String.format("不能设置超过%d个月后的时间", getHolidayBatchMonths());
        }

        return null;
    }
}