package com.server.anki.utils;

import cn.hutool.core.date.ChineseDate;
import cn.hutool.core.date.DateUtil;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.Date;
import java.util.Objects;

/**
 * 节假日工具类
 * 提供节假日相关的判断和工具方法
 * 使用Hutool实现农历转换
 */
@Slf4j
@UtilityClass
public class HolidayUtil {

    /**
     * 获取节假日名称
     * 首先检查农历节日，然后检查阳历节日
     * @param date 日期
     * @return 节假日名称
     */
    public String getHolidayName(LocalDate date) {
        try {
            // 优先检查农历节日
            String lunarHolidayName = getLunarHolidayName(date);
            return Objects.requireNonNullElseGet(lunarHolidayName, () -> getSolarHolidayName(date));

            // 检查阳历节日
        } catch (Exception e) {
            log.error("获取节假日名称时发生错误: {}", e.getMessage(), e);
            return "普通工作日";
        }
    }

    /**
     * 获取农历节日名称
     * 使用Hutool的ChineseDate进行农历日期转换
     * @param date 公历日期
     * @return 农历节日名称，如果不是节日则返回null
     */
    private String getLunarHolidayName(LocalDate date) {
        try {
            // 转换为Hutool可用的Date对象
            Date utilDate = DateUtil.date(date);
            // 转换为农历日期
            ChineseDate chineseDate = new ChineseDate(utilDate);

            int lunarMonth = chineseDate.getMonth();
            int lunarDay = chineseDate.getDay();

            // 判断农历节日
            // 春节相关（正月）
            if (lunarMonth == 1) {
                if (lunarDay == 1) return "春节";
                if (lunarDay <= 7) return "春节假期";
                if (lunarDay == 15) return "元宵节";
            }

            // 特殊节日判断
            if (lunarMonth == 5 && lunarDay == 5) {
                return "端午节";
            }
            if (lunarMonth == 8 && lunarDay == 15) {
                return "中秋节";
            }

            // 其他传统节日判断
            if (lunarMonth == 7 && lunarDay == 7) {
                return "七夕节";
            }
            if (lunarMonth == 9 && lunarDay == 9) {
                return "重阳节";
            }

            // 除夕特殊处理：检查下一天是否为正月初一
            LocalDate nextDay = date.plusDays(1);
            Date nextUtilDate = DateUtil.date(nextDay);
            ChineseDate nextChineseDate = new ChineseDate(nextUtilDate);
            if (nextChineseDate.getMonth() == 1 && nextChineseDate.getDay() == 1) {
                return "除夕";
            }

            return null;
        } catch (Exception e) {
            log.error("农历转换出错: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取阳历节日名称
     * @param date 公历日期
     * @return 阳历节日名称
     */
    private String getSolarHolidayName(LocalDate date) {
        int month = date.getMonthValue();
        int day = date.getDayOfMonth();

        return switch (month) {
            case 1 -> {
                if (day == 1) yield "元旦";
                if (day <= 3) yield "元旦假期";
                else yield "普通工作日";
            }
            case 4 -> {
                // 清明节前后
                if (day >= 4 && day <= 6) yield "清明节";
                else yield "普通工作日";
            }
            case 5 -> {
                if (day == 1) yield "劳动节";
                if (day <= 5) yield "劳动节假期";
                else yield "普通工作日";
            }
            case 10 -> {
                if (day == 1) yield "国庆节";
                if (day <= 7) yield "国庆节假期";
                else yield "普通工作日";
            }
            default -> "普通工作日";
        };
    }

    /**
     * 判断是否为法定节假日
     * @param date 日期
     * @return 是否为法定节假日
     */
    public boolean isStatutoryHoliday(LocalDate date) {
        String holidayName = getHolidayName(date);
        return isHolidayPeriod(holidayName);
    }

    /**
     * 判断名称是否为节假日名称
     * @param holidayName 节日名称
     * @return 是否为节假日
     */
    public boolean isHolidayPeriod(String holidayName) {
        return holidayName.contains("节") ||
                holidayName.contains("假期") ||
                holidayName.contains("春节") ||
                holidayName.contains("除夕");
    }

    /**
     * 是否为周末
     * @param date 日期
     * @return 是否为周末
     */
    public boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek().getValue() >= 6;
    }

    /**
     * 获取节假日缓存键
     * @param prefix 缓存前缀
     * @param date 日期
     * @return 缓存键
     */
    public String getHolidayCacheKey(String prefix, LocalDate date) {
        return prefix + date.toString();
    }

    /**
     * 判断是否为工作日
     * 非节假日且非周末的日期为工作日
     * @param date 日期
     * @return 是否为工作日
     */
    public boolean isWorkday(LocalDate date) {
        return !isStatutoryHoliday(date) && !isWeekend(date);
    }

    /**
     * 获取节假日类型
     * @param date 日期
     * @return 节假日类型描述
     */
    public String getHolidayType(LocalDate date) {
        if (isStatutoryHoliday(date)) {
            return "法定节假日";
        }
        if (isWeekend(date)) {
            return "周末";
        }
        return "工作日";
    }
}