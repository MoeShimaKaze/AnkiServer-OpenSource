package com.server.anki.marketing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.server.anki.fee.core.FeeConfiguration;
import com.server.anki.fee.model.FeeType;
import com.server.anki.marketing.entity.SpecialDate;
import com.server.anki.marketing.entity.SpecialTimeRange;
import com.server.anki.marketing.repository.SpecialDateRepository;
import com.server.anki.marketing.repository.SpecialTimeRangeRepository;
import com.server.anki.question.exception.ServiceException;
import com.server.anki.utils.HolidayUtil;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 特殊日期服务
 * 负责管理节假日、特殊日期和特殊时段的费率计算
 */
@SuppressWarnings("SpringTransactionalMethodCallsInspection")
@Service
@Slf4j
public class SpecialDateService {
    private static final Logger logger = LoggerFactory.getLogger(SpecialDateService.class);

    @Autowired
    private SpecialDateRepository specialDateRepository;

    @Autowired
    private SpecialTimeRangeRepository specialTimeRangeRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FeeConfiguration feeConfiguration;

    /**
     * 应用启动时执行一次节假日数据初始化
     */
    @SuppressWarnings("SpringTransactionalMethodCallsInspection")
    @PostConstruct
    public void initHolidayData() {
        logger.info("应用启动，开始初始化节假日数据");

        try {
            // 检查节假日功能是否启用
            if (!feeConfiguration.isHolidayMultiplierEnabled()) {
                logger.info("节假日费率未启用，跳过初始化");
                return;
            }

            // 获取配置的批量更新月数并执行同步
            int batchMonths = feeConfiguration.getHolidayBatchMonths();
            LocalDate today = LocalDate.now();
            LocalDate endDate = today.plusMonths(batchMonths);

            syncHolidayData(today, endDate);
            logger.info("节假日数据初始化完成");
        } catch (Exception e) {
            logger.error("节假日数据初始化失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 定时更新节假日信息
     */
    @Scheduled(cron = "#{feeConfiguration.holidayUpdateCron}")
    public void updateHolidayInfo() {
        if (!feeConfiguration.isHolidayMultiplierEnabled()) {
            logger.debug("节假日费率未启用，跳过更新");
            return;
        }

        logger.info("开始更新节假日信息");
        try {
            int batchMonths = feeConfiguration.getHolidayBatchMonths();
            LocalDate today = LocalDate.now();
            LocalDate endDate = today.plusMonths(batchMonths);

            syncHolidayData(today, endDate);
        } catch (Exception e) {
            logger.error("更新节假日信息时发生错误: {}", e.getMessage(), e);
        }
    }

    /**
     * 同步指定日期范围的节假日数据
     */
    @Transactional
    public void syncHolidayData(LocalDate startDate, LocalDate endDate) {
        logger.info("同步节假日数据: {} 至 {}", startDate, endDate);

        try {
            String apiUrl = feeConfiguration.getHolidayApiUrl();

            // 按月同步数据
            LocalDate currentDate = startDate;
            while (!currentDate.isAfter(endDate)) {
                String yearMonth = String.format("%d-%02d",
                        currentDate.getYear(), currentDate.getMonthValue());
                String url = apiUrl + "?date=" + yearMonth;

                syncMonthHolidayData(url);
                currentDate = currentDate.plusMonths(1);
            }

            logger.info("节假日数据同步完成");
        } catch (Exception e) {
            logger.error("同步节假日数据时发生错误: {}", e.getMessage(), e);
            throw new RuntimeException("同步节假日数据失败", e);
        }
    }

    /**
     * 获取指定日期的费率倍数
     */
    public BigDecimal getDateRateMultiplier(LocalDate date, FeeType feeType) {
        // 检查特殊日期费率功能是否启用
        if (!feeConfiguration.isSpecialDateRateEnabled()) {
            logger.debug("特殊日期费率功能未启用");
            return BigDecimal.ONE;
        }

        // 先检查是否有针对特定订单类型的费率设置
        List<SpecialDate> specificDates = specialDateRepository.findActiveAndRateEnabledByDateAndFeeType(date, feeType);

        // 如果找到针对特定类型的设置，直接使用最高优先级的那个
        if (!specificDates.isEmpty()) {
            SpecialDate highestPriorityDate = specificDates.get(0);
            logger.debug("应用特定类型[{}]特殊日期[{}]费率: {}",
                    feeType,
                    highestPriorityDate.getName(),
                    highestPriorityDate.getRateMultiplier());
            return highestPriorityDate.getRateMultiplier();
        }

        // 如果没有找到特定类型的设置，查找通用类型的设置
        List<SpecialDate> allOrdersDates = specialDateRepository.findActiveAndRateEnabledByDateAndFeeType(date, FeeType.ALL_ORDERS);

        if (!allOrdersDates.isEmpty()) {
            SpecialDate highestPriorityDate = allOrdersDates.get(0);
            logger.debug("应用通用类型特殊日期[{}]费率: {}",
                    highestPriorityDate.getName(),
                    highestPriorityDate.getRateMultiplier());
            return highestPriorityDate.getRateMultiplier();
        }

        return BigDecimal.ONE;
    }

    /**
     * 获取时段费率倍数
     */
    public BigDecimal getTimeRangeRateMultiplier(int hour, FeeType feeType) {
        // 检查特殊时段费率功能是否启用
        if (!feeConfiguration.isSpecialTimeMultiplierEnabled()) {
            return BigDecimal.ONE;
        }

        // 先检查特定类型的时段费率
        List<SpecialTimeRange> specificTimeRanges = specialTimeRangeRepository.findActiveByHourAndFeeType(hour, feeType);
        if (specificTimeRanges != null && !specificTimeRanges.isEmpty()) {
            // 取特定类型中最大的倍率
            return specificTimeRanges.stream()
                    .map(SpecialTimeRange::getRateMultiplier)
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ONE);
        }

        // 如果没有特定类型的时段费率，检查通用类型
        List<SpecialTimeRange> allOrdersTimeRanges = specialTimeRangeRepository.findActiveByHourAndFeeType(hour, FeeType.ALL_ORDERS);
        if (allOrdersTimeRanges != null && !allOrdersTimeRanges.isEmpty()) {
            // 取通用类型中最大的倍率
            return allOrdersTimeRanges.stream()
                    .map(SpecialTimeRange::getRateMultiplier)
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ONE);
        }

        return BigDecimal.ONE;
    }

    /**
     * 检查指定日期是否为节假日
     */
    public boolean isHoliday(LocalDate date) {
        // 首先查询缓存
        String key = getHolidayCacheKey(date);
        String cachedValue = redisTemplate.opsForValue().get(key);

        if (cachedValue != null) {
            return Boolean.parseBoolean(cachedValue);
        }

        // 缓存未命中，查询数据库
        boolean isHoliday = specialDateRepository.findByDate(date)
                .map(holiday -> SpecialDateType.HOLIDAY.equals(holiday.getType()))
                .orElse(false);

        // 更新缓存
        if (isHoliday) {
            updateHolidayCache(date);
        }

        return isHoliday;
    }

    /**
     * 获取指定日期的所有有效特殊日期
     */
    public List<SpecialDate> getActiveSpecialDates(LocalDate date) {
        return specialDateRepository.findActiveByDate(date);
    }

    /**
     * 根据小时查询活跃的特殊时段
     */
    public List<SpecialTimeRange> findActiveTimeRangesByHour(int hour) {
        logger.debug("查询活跃的特殊时段 - 小时: {}", hour);

        // 校验输入合法性
        if (hour < 0 || hour > 23) {
            logger.error("无效的小时值: {}", hour);
            throw new IllegalArgumentException("小时必须在 0 到 23 之间");
        }

        try {
            return specialTimeRangeRepository.findActiveByHour(hour);
        } catch (Exception e) {
            logger.error("查询特殊时段失败 - 小时: {}, 错误原因: {}", hour, e.getMessage(), e);
            throw new ServiceException("查询时段数据时发生错误", e);
        }
    }

    /**
     * 创建特殊日期
     */
    @Transactional
    public SpecialDate createSpecialDate(SpecialDate specialDate) {
        validateSpecialDate(specialDate);

        // 设置默认值
        if (specialDate.getRateMultiplier() == null) {
            specialDate.setRateMultiplier(BigDecimal.ONE);
        }

        specialDate.setActive(true);
        specialDate.setRateEnabled(true);

        SpecialDate saved = specialDateRepository.save(specialDate);
        logger.info("创建特殊日期: {}", saved.getName());
        return saved;
    }

    /**
     * 更新特殊日期
     */
    @Transactional
    public SpecialDate updateSpecialDate(Long id, SpecialDate updatedDate) {
        SpecialDate existingDate = specialDateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("特殊日期不存在"));

        validateSpecialDate(updatedDate);

        // 更新属性
        existingDate.setName(updatedDate.getName());
        existingDate.setRateMultiplier(updatedDate.getRateMultiplier());
        existingDate.setDescription(updatedDate.getDescription());
        existingDate.setType(updatedDate.getType()); // 也应该更新类型
        existingDate.setActive(updatedDate.isActive());
        existingDate.setRateEnabled(updatedDate.isRateEnabled());
        existingDate.setPriority(updatedDate.getPriority());
        existingDate.setFeeType(updatedDate.getFeeType()); // 新增：更新费用类型

        SpecialDate saved = specialDateRepository.save(existingDate);
        logger.info("更新特殊日期: {}", saved.getName());
        return saved;
    }

    /**
     * 启用/禁用特殊日期的费率
     */
    @Transactional
    public void updateRateEnabled(Long id, boolean enabled) {
        SpecialDate specialDate = specialDateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("特殊日期不存在"));

        specialDate.setRateEnabled(enabled);
        specialDateRepository.save(specialDate);
        logger.info("{}特殊日期[{}]费率", enabled ? "启用" : "禁用", specialDate.getName());
    }

    /**
     * 批量启用/禁用特殊日期的费率
     */
    @Transactional
    public void batchUpdateRateEnabled(List<Long> ids, boolean enabled) {
        validateSpecialDatesExist(ids);
        int updated = specialDateRepository.updateRateEnabledStatus(ids, enabled);
        logger.info("批量{}{}个特殊日期的费率", enabled ? "启用" : "禁用", updated);
    }

    /**
     * 删除特殊日期
     */
    @Transactional
    public void deleteSpecialDate(Long id) {
        SpecialDate specialDate = specialDateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("特殊日期不存在"));

        // 如果是节假日类型，同时清除缓存
        if (SpecialDateType.HOLIDAY.equals(specialDate.getType())) {
            clearHolidayCache(specialDate.getDate());
        }

        specialDateRepository.deleteById(id);
        logger.info("删除特殊日期: {}", specialDate.getName());
    }

    /**
     * 根据日期范围查询特殊日期列表
     */
    public List<SpecialDate> getSpecialDatesByDateRange(LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate);
        return specialDateRepository.findByDateRange(startDate, endDate);
    }

    /**
     * 处理单个月份的节假日数据同步
     */
    private void syncMonthHolidayData(String url) {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                logger.warn("获取节假日数据失败: {}", url);
                return;
            }

            JsonNode rootNode = objectMapper.readTree(response.getBody());
            if (rootNode.isArray()) {
                rootNode.forEach(this::processHolidayData);
            } else {
                processHolidayData(rootNode);
            }
        } catch (Exception e) {
            logger.error("处理节假日数据时发生错误: {}", e.getMessage());
        }
    }

    /**
     * 处理单条节假日数据
     */
    private void processHolidayData(JsonNode dayNode) {
        try {
            String dateStr = dayNode.get("date").asText();
            int status = dayNode.get("status").asInt();

            // 仅处理法定节假日（status=3）的数据
            if (status == 3) {
                LocalDate date = LocalDate.parse(dateStr);

                // 创建通用类型的节假日记录
                SpecialDate specialDate = specialDateRepository.findByDateAndFeeType(date, FeeType.ALL_ORDERS)
                        .orElse(new SpecialDate());

                // 直接使用带费用类型的方法一次性设置所有属性
                updateSpecialDateEntityWithFeeType(specialDate, date);

                // 保存实体
                specialDateRepository.save(specialDate);

                // 更新缓存
                updateHolidayCache(date);
                logger.debug("已保存节假日信息: {}", date);
            }
        } catch (Exception e) {
            logger.error("处理节假日数据时发生错误: {}", e.getMessage());
        }
    }

    /**
     * 更新特殊日期实体的信息（带费用类型）
     */
    private void updateSpecialDateEntityWithFeeType(SpecialDate specialDate, LocalDate date) {
        specialDate.setDate(date);
        specialDate.setName(HolidayUtil.getHolidayName(date));
        specialDate.setType(SpecialDateType.HOLIDAY);
        specialDate.setRateMultiplier(feeConfiguration.getHolidayRateMultiplier());
        specialDate.setDescription("法定节假日");
        specialDate.setActive(true);
        specialDate.setRateEnabled(true);
        specialDate.setPriority(100);  // 设置较高优先级
        specialDate.setFeeType(FeeType.ALL_ORDERS);  // 设置特定的费用类型
    }

    /**
     * 更新节假日缓存
     */
    private void updateHolidayCache(LocalDate date) {
        try {
            String key = getHolidayCacheKey(date);
            redisTemplate.opsForValue().set(
                    key,
                    "true",
                    feeConfiguration.getHolidayCacheDuration(),
                    TimeUnit.SECONDS
            );
        } catch (Exception e) {
            logger.error("更新节假日缓存时发生错误: {}", e.getMessage());
        }
    }

    /**
     * 清除节假日缓存
     */
    private void clearHolidayCache(LocalDate date) {
        try {
            String key = getHolidayCacheKey(date);
            redisTemplate.delete(key);
        } catch (Exception e) {
            logger.error("清除节假日缓存时发生错误: {}", e.getMessage());
        }
    }

    /**
     * 获取缓存键名
     */
    private String getHolidayCacheKey(LocalDate date) {
        return HolidayUtil.getHolidayCacheKey(feeConfiguration.getHolidayCachePrefix(), date);
    }

    /**
     * 验证特殊日期数据的合法性
     */
    private void validateSpecialDate(SpecialDate specialDate) {
        // 基础验证
        if (specialDate.getDate() == null) {
            throw new IllegalArgumentException("日期不能为空");
        }
        if (specialDate.getName() == null || specialDate.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("名称不能为空");
        }
        if (specialDate.getRateMultiplier() != null &&
                specialDate.getRateMultiplier().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("费率倍数必须大于0");
        }
        if (specialDate.getType() == null) {
            throw new IllegalArgumentException("类型不能为空");
        }

        // 日期范围验证
        validateDateRange(specialDate.getDate());

        // 日期冲突验证
        checkDateConflict(specialDate.getDate(), specialDate.getPriority());
    }

    /**
     * 检查日期范围的有效性
     */
    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("起止日期不能为空");
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("开始日期不能晚于结束日期");
        }

        LocalDate today = LocalDate.now();
        LocalDate maxDate = today.plusMonths(feeConfiguration.getHolidayBatchMonths());

        if (startDate.isBefore(today)) {
            throw new IllegalArgumentException("不能查询过去的日期");
        }
        if (endDate.isAfter(maxDate)) {
            throw new IllegalArgumentException(
                    String.format("查询范围不能超过%d个月", feeConfiguration.getHolidayBatchMonths()));
        }
    }

    /**
     * 验证单个日期的有效性
     */
    private void validateDateRange(LocalDate date) {
        LocalDate today = LocalDate.now();
        LocalDate maxFutureDate = today.plusMonths(feeConfiguration.getHolidayBatchMonths());

        if (date.isBefore(today)) {
            throw new IllegalArgumentException("不能设置过去的日期");
        }
        if (date.isAfter(maxFutureDate)) {
            throw new IllegalArgumentException(
                    String.format("不能设置超过%d个月后的日期", feeConfiguration.getHolidayBatchMonths()));
        }
    }

    /**
     * 检查日期是否存在冲突
     */
    private void checkDateConflict(LocalDate date, Integer priority) {
        List<SpecialDate> existingDates = specialDateRepository.findActiveByDate(date);
        if (!existingDates.isEmpty() && priority == null) {
            throw new IllegalArgumentException("该日期已存在特殊日期，请设置优先级");
        }
    }

    /**
     * 验证特殊日期ID列表的有效性
     */
    private void validateSpecialDatesExist(List<Long> ids) {
        List<SpecialDate> existingDates = specialDateRepository.findAllById(ids);
        if (existingDates.size() != ids.size()) {
            throw new IllegalArgumentException("部分特殊日期不存在");
        }
    }
}