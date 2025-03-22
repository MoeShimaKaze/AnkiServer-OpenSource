package com.server.anki.marketing;

import com.server.anki.fee.model.FeeType;
import com.server.anki.marketing.entity.SpecialDate;
import com.server.anki.marketing.entity.SpecialTimeRange;
import com.server.anki.marketing.region.DeliveryRegion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 营销相关DTO类集合
 * 用于前端展示和数据交互
 */
public class MarketingDTOs {

    /**
     * API响应基类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiResponse<T> {
        private boolean success;
        private String message;
        private T data;

        public static <T> ApiResponse<T> success(T data) {
            return ApiResponse.<T>builder()
                    .success(true)
                    .data(data)
                    .build();
        }

        public static <T> ApiResponse<T> success(T data, String message) {
            return ApiResponse.<T>builder()
                    .success(true)
                    .message(message)
                    .data(data)
                    .build();
        }

        public static <T> ApiResponse<T> errorWithData(String message, T data) {
            return ApiResponse.<T>builder()
                    .success(false)
                    .message(message)
                    .data(data)
                    .build();
        }

        public static <T> ApiResponse<T> error(String message) {
            return ApiResponse.<T>builder()
                    .success(false)
                    .message(message)
                    .build();
        }
    }

    /**
     * 特殊日期DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpecialDateDTO {
        private Long id;
        private String name;
        private String date;
        private BigDecimal rateMultiplier;
        private String description;
        private String type;
        private String typeDescription;
        private boolean active;
        private String createTime;
        private String updateTime;
        private boolean rateEnabled;
        private int priority;
        private String feeType;

        public static SpecialDateDTO fromEntity(SpecialDate entity) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            // 转换FeeType为前端使用的费用类型字符串
            String frontendFeeType = convertFeeTypeToFrontend(entity.getFeeType());

            return SpecialDateDTO.builder()
                    .id(entity.getId())
                    .name(entity.getName())
                    .date(entity.getDate().toString())
                    .rateMultiplier(entity.getRateMultiplier())
                    .description(entity.getDescription())
                    .type(entity.getType().name())
                    .typeDescription(entity.getType().getDescription())
                    .active(entity.isActive())
                    .createTime(entity.getCreateTime().format(formatter))
                    .updateTime(entity.getUpdateTime() != null ? entity.getUpdateTime().format(formatter) : null)
                    .rateEnabled(entity.isRateEnabled())
                    .priority(entity.getPriority())
                    .feeType(frontendFeeType)
                    .build();
        }

        /**
         * 将FeeType转换为前端使用的费用类型
         */
        private static String convertFeeTypeToFrontend(FeeType feeType) {
            if (feeType == null) {
                return "ALL";
            }

            if (feeType == FeeType.ALL_ORDERS) {
                return "ALL";
            }

            return feeType.name();
        }

        public static List<SpecialDateDTO> fromEntities(List<SpecialDate> entities) {
            return entities.stream()
                    .map(SpecialDateDTO::fromEntity)
                    .collect(Collectors.toList());
        }
    }

    /**
     * 特殊时段DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpecialTimeRangeDTO {
        private Long id;
        private String name;
        private int startHour;
        private int endHour;
        private String timeRangeDisplay;
        private BigDecimal rateMultiplier;
        private String description;
        private boolean active;
        private String createTime;
        private String updateTime;
        private String feeType;

        public static SpecialTimeRangeDTO fromEntity(SpecialTimeRange entity) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            String timeRange = String.format("%02d:00-%02d:00",
                    entity.getStartHour(),
                    entity.getEndHour());

            return SpecialTimeRangeDTO.builder()
                    .id(entity.getId())
                    .name(entity.getName())
                    .startHour(entity.getStartHour())
                    .endHour(entity.getEndHour())
                    .timeRangeDisplay(timeRange)
                    .rateMultiplier(entity.getRateMultiplier())
                    .description(entity.getDescription())
                    .active(entity.isActive())
                    .createTime(entity.getCreateTime().format(formatter))
                    .updateTime(entity.getUpdateTime() != null ? entity.getUpdateTime().format(formatter) : null)
                    .feeType(entity.getFeeType() != null ? entity.getFeeType().name() : null)
                    .build();
        }

        public static List<SpecialTimeRangeDTO> fromEntities(List<SpecialTimeRange> entities) {
            return entities.stream()
                    .map(SpecialTimeRangeDTO::fromEntity)
                    .collect(Collectors.toList());
        }
    }

    /**
     * 配送区域DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeliveryRegionDTO {
        private Long id;
        private String name;
        private String description;
        private List<String> boundaryPoints;
        private double rateMultiplier;
        private boolean active;
        private int priority;

        public static DeliveryRegionDTO fromEntity(DeliveryRegion entity) {
            if (entity == null) return null;

            // 获取边界点
            List<String> points = entity.getBoundaryPoints();

            // 添加日志
            System.out.println("DTO转换 - 区域[" + entity.getName() + "]边界点: " +
                    (points == null ? "null" :
                            (points.isEmpty() ? "空列表" : points.size() + "个点")));

            // 如果边界点为空但有边界多边形，则尝试从多边形中提取
            if ((points == null || points.isEmpty()) && entity.getBoundary() != null) {
                System.out.println("DTO转换 - 尝试从多边形中提取边界点");
                points = extractBoundaryPoints(entity.getBoundary());
                System.out.println("DTO转换 - 提取结果: " +
                        (points == null ? "null" : points.size() + "个点"));
            }

            return DeliveryRegionDTO.builder()
                    .id(entity.getId())
                    .name(entity.getName())
                    .description(entity.getDescription())
                    .boundaryPoints(points)
                    .rateMultiplier(entity.getRateMultiplier())
                    .active(entity.isActive())
                    .priority(entity.getPriority())
                    .build();
        }

        public static List<DeliveryRegionDTO> fromEntities(List<DeliveryRegion> entities) {
            return entities.stream()
                    .map(DeliveryRegionDTO::fromEntity)
                    .collect(Collectors.toList());
        }

        // 修改坐标点提取方法，确保返回"纬度,经度"格式
        private static List<String> extractBoundaryPoints(org.locationtech.jts.geom.Polygon polygon) {
            if (polygon == null) {
                return List.of();
            }

            org.locationtech.jts.geom.Coordinate[] coordinates = polygon.getExteriorRing().getCoordinates();
            return java.util.Arrays.stream(coordinates)
                    // 忽略最后一个坐标（因为在闭合多边形中它与第一个点相同）
                    .limit(coordinates.length - 1)  // 正确写法：直接使用数字常量
                    // 注意格式修改为 "纬度,经度"（y,x）
                    .map(coord -> coord.y + "," + coord.x)
                    .collect(Collectors.toList());
        }
    }

    /**
     * 特殊日期创建/更新请求
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpecialDateRequest {
        private String name;
        private LocalDate date;
        private BigDecimal rateMultiplier;
        private String description;
        private SpecialDateType type;
        private boolean active;
        private boolean rateEnabled;
        private int priority;
        private FeeType feeType;

        public SpecialDate toEntity() {
            SpecialDate entity = new SpecialDate();
            entity.setName(this.name);
            entity.setDate(this.date);
            entity.setRateMultiplier(this.rateMultiplier);
            entity.setDescription(this.description);
            entity.setType(this.type);
            entity.setActive(this.active);
            entity.setRateEnabled(this.rateEnabled);
            entity.setPriority(this.priority);
            entity.setFeeType(this.feeType);
            return entity;
        }
    }

    /**
     * 特殊时段创建/更新请求
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpecialTimeRangeRequest {
        private String name;
        private int startHour;
        private int endHour;
        private BigDecimal rateMultiplier;
        private String description;
        private boolean active;
        private FeeType feeType;

        public SpecialTimeRange toEntity() {
            SpecialTimeRange entity = new SpecialTimeRange();
            entity.setName(this.name);
            entity.setStartHour(this.startHour);
            entity.setEndHour(this.endHour);
            entity.setRateMultiplier(this.rateMultiplier);
            entity.setDescription(this.description);
            entity.setActive(this.active);
            entity.setFeeType(this.feeType);
            return entity;
        }
    }

    /**
     * 营销概览DTO
     * 用于首页展示
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MarketingOverviewDTO {
        private int totalSpecialDates;
        private int activeSpecialDates;
        private int totalTimeRanges;
        private int activeTimeRanges;
        private int totalRegions;
        private int activeRegions;
        private List<SpecialDateDTO> upcomingSpecialDates;
        private List<SpecialTimeRangeDTO> currentTimeRanges;
    }
}