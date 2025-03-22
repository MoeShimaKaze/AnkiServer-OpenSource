package com.server.anki.config;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 日期时间转换器配置
 * 解决前端传入带时区的ISO时间字符串无法转换为LocalDateTime的问题
 */
@Configuration
public class DateTimeConverterConfig implements WebMvcConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(DateTimeConverterConfig.class);

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new StringToLocalDateTimeConverter());
    }

    /**
     * 字符串转LocalDateTime转换器
     * 支持标准ISO格式和带时区的ISO格式
     */
    private static class StringToLocalDateTimeConverter implements Converter<String, LocalDateTime> {
        @Override
        public LocalDateTime convert(@NotNull String source) {
            if (source.isEmpty()) {
                return null;
            }

            try {
                // 尝试直接解析为LocalDateTime
                try {
                    return LocalDateTime.parse(source);
                } catch (Exception e) {
                    // 如果包含Z或时区偏移信息，尝试解析为ZonedDateTime或OffsetDateTime
                    if (source.endsWith("Z") || source.contains("+") || source.contains("-")) {
                        try {
                            // 尝试解析为ZonedDateTime
                            ZonedDateTime zdt = ZonedDateTime.parse(source);
                            // 转换为系统默认时区的LocalDateTime
                            return zdt.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
                        } catch (Exception e2) {
                            // 尝试解析为OffsetDateTime
                            OffsetDateTime odt = OffsetDateTime.parse(source);
                            // 转换为系统默认时区的LocalDateTime
                            return odt.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
                        }
                    }
                    // 其他格式解析失败
                    throw e;
                }
            } catch (Exception e) {
                logger.warn("解析日期时间字符串失败: {}", source);
                return null;
            }
        }
    }
}