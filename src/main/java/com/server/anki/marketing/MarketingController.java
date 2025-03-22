package com.server.anki.marketing;

import com.server.anki.fee.model.FeeType;
import com.server.anki.marketing.MarketingDTOs.*;
import com.server.anki.marketing.entity.SpecialDate;
import com.server.anki.marketing.entity.SpecialTimeRange;
import com.server.anki.marketing.region.DeliveryRegion;
import com.server.anki.marketing.region.RegionService;
import com.server.anki.marketing.region.model.RegionCreateRequest;
import com.server.anki.marketing.region.model.RegionUpdateRequest;
import com.server.anki.marketing.repository.SpecialTimeRangeRepository;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 营销管理控制器
 * 提供特殊日期费率、特殊时段费率和区域费率的管理接口
 */
@RestController
@RequestMapping("/api/marketing")
@Api(tags = "营销管理")
@Slf4j
public class MarketingController {

    private static final Logger logger = LoggerFactory.getLogger(MarketingController.class);

    @Autowired
    private SpecialDateService specialDateService;

    @Autowired
    private SpecialTimeRangeRepository specialTimeRangeRepository;

    @Autowired
    private RegionService regionService;

    // ==================== 特殊日期管理接口 ====================

    /**
     * 获取所有特殊日期
     */
    @GetMapping("/dates")
    @ApiOperation("获取所有特殊日期")
    public ResponseEntity<ApiResponse<List<SpecialDateDTO>>> getAllSpecialDates(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        try {
            List<SpecialDate> specialDates;

            if (startDate != null && endDate != null) {
                specialDates = specialDateService.getSpecialDatesByDateRange(startDate, endDate);
            } else {
                // 如果未指定日期范围，则获取未来一个月的特殊日期
                LocalDate today = LocalDate.now();
                LocalDate oneMonthLater = today.plusMonths(1);
                specialDates = specialDateService.getSpecialDatesByDateRange(today, oneMonthLater);
            }

            List<SpecialDateDTO> dtoList = SpecialDateDTO.fromEntities(specialDates);
            return ResponseEntity.ok(ApiResponse.success(dtoList));
        } catch (Exception e) {
            logger.error("获取特殊日期列表失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取特殊日期列表失败: " + e.getMessage()));
        }
    }

    /**
     * 获取特定日期的特殊日期
     */
    @GetMapping("/dates/byDate")
    @ApiOperation("获取特定日期的特殊日期")
    public ResponseEntity<ApiResponse<List<SpecialDateDTO>>> getSpecialDatesByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        try {
            List<SpecialDate> specialDates = specialDateService.getActiveSpecialDates(date);
            List<SpecialDateDTO> dtoList = SpecialDateDTO.fromEntities(specialDates);
            return ResponseEntity.ok(ApiResponse.success(dtoList));
        } catch (Exception e) {
            logger.error("获取特定日期的特殊日期失败: {}", date, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取特定日期的特殊日期失败: " + e.getMessage()));
        }
    }

    /**
     * 创建特殊日期
     */
    @PostMapping("/dates")
    @ApiOperation("创建特殊日期")
    public ResponseEntity<ApiResponse<SpecialDateDTO>> createSpecialDate(@RequestBody SpecialDateRequest request) {
        try {
            // 转换DTO到实体
            SpecialDate specialDate = request.toEntity();

            SpecialDate createdDate = specialDateService.createSpecialDate(specialDate);
            SpecialDateDTO dto = SpecialDateDTO.fromEntity(createdDate);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(dto, "特殊日期创建成功"));
        } catch (IllegalArgumentException e) {
            logger.error("创建特殊日期参数错误", e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("创建特殊日期参数错误: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("创建特殊日期失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("创建特殊日期失败: " + e.getMessage()));
        }
    }

    /**
     * 更新特殊日期
     */
    @PutMapping("/dates/{id}")
    @ApiOperation("更新特殊日期")
    public ResponseEntity<ApiResponse<SpecialDateDTO>> updateSpecialDate(
            @PathVariable Long id,
            @RequestBody SpecialDateRequest request) {

        try {
            // 转换DTO到实体
            SpecialDate specialDate = request.toEntity();

            SpecialDate updatedDate = specialDateService.updateSpecialDate(id, specialDate);
            SpecialDateDTO dto = SpecialDateDTO.fromEntity(updatedDate);

            return ResponseEntity.ok(ApiResponse.success(dto, "特殊日期更新成功"));
        } catch (IllegalArgumentException e) {
            logger.error("更新特殊日期参数错误: {}", id, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("更新特殊日期参数错误: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("更新特殊日期失败: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("更新特殊日期失败: " + e.getMessage()));
        }
    }

    /**
     * 删除特殊日期
     */
    @DeleteMapping("/dates/{id}")
    @ApiOperation("删除特殊日期")
    public ResponseEntity<ApiResponse<Void>> deleteSpecialDate(@PathVariable Long id) {
        try {
            specialDateService.deleteSpecialDate(id);
            return ResponseEntity.ok(ApiResponse.success(null, "特殊日期删除成功"));
        } catch (IllegalArgumentException e) {
            logger.error("删除特殊日期参数错误: {}", id, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("删除特殊日期参数错误: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("删除特殊日期失败: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("删除特殊日期失败: " + e.getMessage()));
        }
    }

    /**
     * 批量更新特殊日期费率状态
     */
    @PutMapping("/dates/batch/rate-status")
    @ApiOperation("批量更新特殊日期费率状态")
    public ResponseEntity<ApiResponse<Void>> batchUpdateSpecialDateRateStatus(
            @RequestBody BatchRateStatusRequest request) {

        try {
            specialDateService.batchUpdateRateEnabled(request.getIds(), request.isEnabled());
            return ResponseEntity.ok(ApiResponse.success(null,
                    "批量" + (request.isEnabled() ? "启用" : "禁用") + "特殊日期费率成功"));
        } catch (IllegalArgumentException e) {
            logger.error("批量更新特殊日期费率状态参数错误", e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("批量更新特殊日期费率状态参数错误: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("批量更新特殊日期费率状态失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("批量更新特殊日期费率状态失败: " + e.getMessage()));
        }
    }

    // ==================== 特殊时段管理接口 ====================

    /**
     * 获取所有特殊时段
     */
    @GetMapping("/timeranges")
    @ApiOperation("获取所有特殊时段")
    public ResponseEntity<ApiResponse<List<SpecialTimeRangeDTO>>> getAllSpecialTimeRanges() {
        try {
            List<SpecialTimeRange> timeRanges = specialTimeRangeRepository.findAll();
            List<SpecialTimeRangeDTO> dtoList = SpecialTimeRangeDTO.fromEntities(timeRanges);
            return ResponseEntity.ok(ApiResponse.success(dtoList));
        } catch (Exception e) {
            logger.error("获取特殊时段列表失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取特殊时段列表失败: " + e.getMessage()));
        }
    }

    /**
     * 获取所有活跃的特殊时段
     */
    @GetMapping("/timeranges/active")
    @ApiOperation("获取所有活跃的特殊时段")
    public ResponseEntity<ApiResponse<List<SpecialTimeRangeDTO>>> getActiveSpecialTimeRanges() {
        try {
            List<SpecialTimeRange> timeRanges = specialTimeRangeRepository.findByActiveTrue();
            List<SpecialTimeRangeDTO> dtoList = SpecialTimeRangeDTO.fromEntities(timeRanges);
            return ResponseEntity.ok(ApiResponse.success(dtoList));
        } catch (Exception e) {
            logger.error("获取活跃特殊时段列表失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取活跃特殊时段列表失败: " + e.getMessage()));
        }
    }

    /**
     * 根据小时获取活跃特殊时段
     */
    @GetMapping("/timeranges/hour/{hour}")
    @ApiOperation("根据小时获取活跃特殊时段")
    public ResponseEntity<ApiResponse<List<SpecialTimeRangeDTO>>> getSpecialTimeRangesByHour(@PathVariable int hour) {
        try {
            List<SpecialTimeRange> timeRanges = specialDateService.findActiveTimeRangesByHour(hour);
            List<SpecialTimeRangeDTO> dtoList = SpecialTimeRangeDTO.fromEntities(timeRanges);
            return ResponseEntity.ok(ApiResponse.success(dtoList));
        } catch (IllegalArgumentException e) {
            logger.error("获取特殊时段参数错误: 小时={}", hour, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("获取特殊时段参数错误: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("获取特殊时段失败: 小时={}", hour, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取特殊时段失败: " + e.getMessage()));
        }
    }

    /**
     * 根据小时和费用类型获取活跃特殊时段
     */
    @GetMapping("/timeranges/hour/{hour}/feetype/{feeType}")
    @ApiOperation("根据小时和费用类型获取活跃特殊时段")
    public ResponseEntity<ApiResponse<List<SpecialTimeRangeDTO>>> getSpecialTimeRangesByHourAndFeeType(
            @PathVariable int hour,
            @PathVariable FeeType feeType) {
        try {
            List<SpecialTimeRange> timeRanges = specialTimeRangeRepository.findActiveByHourAndFeeType(hour, feeType);
            List<SpecialTimeRangeDTO> dtoList = SpecialTimeRangeDTO.fromEntities(timeRanges);
            return ResponseEntity.ok(ApiResponse.success(dtoList));
        } catch (IllegalArgumentException e) {
            logger.error("获取特殊时段参数错误: 小时={}, 费用类型={}", hour, feeType, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("获取特殊时段参数错误: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("获取特殊时段失败: 小时={}, 费用类型={}", hour, feeType, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取特殊时段失败: " + e.getMessage()));
        }
    }

    /**
     * 创建特殊时段
     */
    @PostMapping("/timeranges")
    @ApiOperation("创建特殊时段")
    public ResponseEntity<ApiResponse<SpecialTimeRangeDTO>> createSpecialTimeRange(@RequestBody SpecialTimeRangeRequest request) {
        try {
            // 验证时间范围
            if (request.getStartHour() < 0 || request.getStartHour() > 23 ||
                    request.getEndHour() < 0 || request.getEndHour() > 24 ||
                    request.getStartHour() >= request.getEndHour()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("时间范围无效，开始时间必须早于结束时间，且在有效范围内"));
            }

            // 检查时间段是否重叠
            if (specialTimeRangeRepository.hasOverlappingTimeRange(request.getStartHour(), request.getEndHour())) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("时间段与现有时间段重叠"));
            }

            // 创建新的特殊时段
            SpecialTimeRange timeRange = request.toEntity();

            SpecialTimeRange savedTimeRange = specialTimeRangeRepository.save(timeRange);
            SpecialTimeRangeDTO dto = SpecialTimeRangeDTO.fromEntity(savedTimeRange);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(dto, "特殊时段创建成功"));
        } catch (Exception e) {
            logger.error("创建特殊时段失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("创建特殊时段失败: " + e.getMessage()));
        }
    }

    /**
     * 更新特殊时段
     */
    @PutMapping("/timeranges/{id}")
    @ApiOperation("更新特殊时段")
    public ResponseEntity<ApiResponse<SpecialTimeRangeDTO>> updateSpecialTimeRange(
            @PathVariable Long id,
            @RequestBody SpecialTimeRangeRequest request) {

        try {
            // 查找现有时段
            SpecialTimeRange existingTimeRange = specialTimeRangeRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("特殊时段不存在"));

            // 验证时间范围
            if (request.getStartHour() < 0 || request.getStartHour() > 23 ||
                    request.getEndHour() < 0 || request.getEndHour() > 24 ||
                    request.getStartHour() >= request.getEndHour()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("时间范围无效，开始时间必须早于结束时间，且在有效范围内"));
            }

            // 更新属性
            SpecialTimeRange timeRange = request.toEntity();
            timeRange.setId(id);

            SpecialTimeRange updatedTimeRange = specialTimeRangeRepository.save(timeRange);
            SpecialTimeRangeDTO dto = SpecialTimeRangeDTO.fromEntity(updatedTimeRange);

            return ResponseEntity.ok(ApiResponse.success(dto, "特殊时段更新成功"));
        } catch (IllegalArgumentException e) {
            logger.error("更新特殊时段参数错误: {}", id, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("更新特殊时段参数错误: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("更新特殊时段失败: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("更新特殊时段失败: " + e.getMessage()));
        }
    }

    /**
     * 删除特殊时段
     */
    @DeleteMapping("/timeranges/{id}")
    @ApiOperation("删除特殊时段")
    public ResponseEntity<ApiResponse<Void>> deleteSpecialTimeRange(@PathVariable Long id) {
        try {
            specialTimeRangeRepository.deleteById(id);
            return ResponseEntity.ok(ApiResponse.success(null, "特殊时段删除成功"));
        } catch (Exception e) {
            logger.error("删除特殊时段失败: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("删除特殊时段失败: " + e.getMessage()));
        }
    }

    /**
     * 获取所有支持的订单类型和费用类型
     */
    @GetMapping("/fee-types")
    @ApiOperation("获取所有支持的费用类型")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> getSupportedFeeTypes() {
        try {
            List<Map<String, String>> feeTypes = new ArrayList<>();

            // 添加通用费用类型
            Map<String, String> allType = new HashMap<>();
            allType.put("code", "ALL");
            allType.put("label", "所有订单类型");
            allType.put("description", "适用于所有类型的订单");
            feeTypes.add(allType);

            // 添加实际的订单类型
            for (FeeType feeType : FeeType.values()) {
                if (feeType != FeeType.ALL_ORDERS) { // 跳过ALL_ORDERS，前端已有对应的ALL
                    Map<String, String> orderType = new HashMap<>();
                    orderType.put("code", feeType.name());
                    orderType.put("label", feeType.getLabel());
                    orderType.put("description", feeType.getDescription());
                    feeTypes.add(orderType);
                }
            }

            return ResponseEntity.ok(ApiResponse.success(feeTypes));
        } catch (Exception e) {
            logger.error("获取费用类型列表失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取费用类型列表失败: " + e.getMessage()));
        }
    }

    // ==================== 配送区域管理接口 ====================

    /**
     * 获取所有配送区域
     */
    @GetMapping("/regions")
    @ApiOperation("获取所有配送区域")
    public ResponseEntity<ApiResponse<List<DeliveryRegionDTO>>> getAllRegions(
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {

        try {
            logger.info("收到获取配送区域请求，includeInactive={}", includeInactive);

            List<DeliveryRegion> regions;

            if (includeInactive) {
                regions = regionService.getAllRegions();
            } else {
                regions = regionService.getAllActiveRegions();
            }

            logger.info("从服务层获取到{}个区域", regions.size());

            // 记录每个区域的边界点数据情况
            for (DeliveryRegion region : regions) {
                List<String> points = region.getBoundaryPoints();
                logger.debug("控制器层 - 区域[{}]的边界点: {}",
                        region.getName(),
                        points == null ? "null" :
                                (points.isEmpty() ? "空列表" : points.size() + "个点"));
            }

            List<DeliveryRegionDTO> dtoList = DeliveryRegionDTO.fromEntities(regions);

            // 记录转换为DTO后的边界点情况
            logger.info("转换为DTO后得到{}个区域对象", dtoList.size());
            for (DeliveryRegionDTO dto : dtoList) {
                List<String> points = dto.getBoundaryPoints();
                logger.debug("控制器层 - DTO[{}]的边界点: {}",
                        dto.getName(),
                        points == null ? "null" :
                                (points.isEmpty() ? "空列表" : points.size() + "个点"));
            }

            return ResponseEntity.ok(ApiResponse.success(dtoList));
        } catch (Exception e) {
            logger.error("获取配送区域列表失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取配送区域列表失败: " + e.getMessage()));
        }
    }

    /**
     * 查询单个区域（需要添加此方法）
     */
    @GetMapping("/regions/{id}")
    @ApiOperation("查询单个配送区域")
    public ResponseEntity<ApiResponse<DeliveryRegionDTO>> getRegionById(@PathVariable Long id) {
        try {
            return regionService.findById(id)
                    .map(region -> {
                        // 确保边界点数据被填充
                        ensureRegionBoundaryPoints(region);
                        DeliveryRegionDTO dto = DeliveryRegionDTO.fromEntity(region);
                        return ResponseEntity.ok(ApiResponse.success(dto));
                    })
                    .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(ApiResponse.error("配送区域不存在")));
        } catch (Exception e) {
            logger.error("查询配送区域失败: ID={}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("查询配送区域失败: " + e.getMessage()));
        }
    }

    /**
     * 创建配送区域
     */
    @PostMapping("/regions")
    @ApiOperation("创建配送区域")
    public ResponseEntity<ApiResponse<DeliveryRegionDTO>> createRegion(@RequestBody RegionCreateRequest request) {
        try {
            DeliveryRegion region = regionService.createRegion(request);
            DeliveryRegionDTO dto = DeliveryRegionDTO.fromEntity(region);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(dto, "配送区域创建成功"));
        } catch (IllegalArgumentException e) {
            logger.error("创建配送区域参数错误", e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("创建配送区域参数错误: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("创建配送区域失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("创建配送区域失败: " + e.getMessage()));
        }
    }

    /**
     * 删除配送区域
     */
    @DeleteMapping("/regions/{id}")
    @ApiOperation("删除配送区域")
    public ResponseEntity<ApiResponse<Void>> deleteRegion(@PathVariable Long id) {
        try {
            regionService.deleteRegion(id);
            return ResponseEntity.ok(ApiResponse.success(null, "配送区域删除成功"));
        } catch (Exception e) {
            logger.error("删除配送区域失败: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("删除配送区域失败: " + e.getMessage()));
        }
    }

    /**
     * 查询坐标所在的配送区域
     */
    @GetMapping("/regions/coordinate")
    @ApiOperation("查询坐标所在的配送区域")
    public ResponseEntity<ApiResponse<DeliveryRegionDTO>> getRegionByCoordinate(@RequestParam String coordinate) {
        try {
            return regionService.findRegionByCoordinate(coordinate)
                    .map(region -> {
                        // 确保边界点数据被填充
                        ensureRegionBoundaryPoints(region);
                        DeliveryRegionDTO dto = DeliveryRegionDTO.fromEntity(region);
                        return ResponseEntity.ok(ApiResponse.success(dto));
                    })
                    .orElse(ResponseEntity.ok(ApiResponse.success(new DeliveryRegionDTO(), "坐标不在任何配送区域内")));
        } catch (IllegalArgumentException e) {
            logger.error("查询配送区域参数错误: 坐标={}", coordinate, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("查询配送区域参数错误: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("查询配送区域失败: 坐标={}", coordinate, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("查询配送区域失败: " + e.getMessage()));
        }
    }

    /**
     * 更新配送区域（需要添加此方法）
     */
    @PutMapping("/regions/{id}")
    @ApiOperation("更新配送区域")
    public ResponseEntity<ApiResponse<DeliveryRegionDTO>> updateRegion(
            @PathVariable Long id,
            @RequestBody RegionUpdateRequest request) {
        try {
            // 首先获取现有区域
            DeliveryRegion existingRegion = regionService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("配送区域不存在: ID=" + id));

            // 更新区域属性（使用您已有的RegionService方法）
            // 这里需要根据您的具体实现调整
            DeliveryRegion updatedRegion = regionService.updateRegion(id, request);

            // 确保边界点数据被填充
            ensureRegionBoundaryPoints(updatedRegion);

            DeliveryRegionDTO dto = DeliveryRegionDTO.fromEntity(updatedRegion);
            return ResponseEntity.ok(ApiResponse.success(dto, "配送区域更新成功"));
        } catch (IllegalArgumentException e) {
            logger.error("更新配送区域参数错误: ID={}", id, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("更新配送区域参数错误: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("更新配送区域失败: ID={}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("更新配送区域失败: " + e.getMessage()));
        }
    }

    /**
     * 计算订单的区域费率
     */
    @GetMapping("/regions/rate")
    @ApiOperation("计算订单的区域费率")
    public ResponseEntity<ApiResponse<Map<String, Object>>> calculateRegionRate(
            @RequestParam String pickupCoordinate,
            @RequestParam String deliveryCoordinate) {

        try {
            var result = regionService.calculateOrderRegionRate(pickupCoordinate, deliveryCoordinate);

            // 使用HashMap代替Map.of()，允许null值
            Map<String, Object> data = new HashMap<>();
            data.put("finalRate", result.finalRate());
            data.put("isCrossRegion", result.isCrossRegion());
            data.put("description", result.getDescription());
            data.put("pickupRegion", result.pickupRegion() != null ? result.pickupRegion().getName() : null);
            data.put("deliveryRegion", result.deliveryRegion() != null ? result.deliveryRegion().getName() : null);

            return ResponseEntity.ok(ApiResponse.success(data));
        } catch (IllegalArgumentException e) {
            logger.error("计算区域费率参数错误", e);
            // 这里保留Map.of()是可以的，因为确定没有null值
            return ResponseEntity.badRequest()
                    .body(ApiResponse.errorWithData("计算区域费率参数错误: " + e.getMessage(), Map.of()));
        } catch (Exception e) {
            logger.error("计算区域费率失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.errorWithData("计算区域费率失败: " + e.getMessage(), Map.of()));
        }
    }

    /**
     * 辅助方法：确保区域的边界点被填充
     */
    private void ensureRegionBoundaryPoints(DeliveryRegion region) {
        if (region == null) return;

        // 检查是否有边界点数据
        List<String> boundaryPoints = region.getBoundaryPoints();

        if ((boundaryPoints == null || boundaryPoints.isEmpty()) && region.getBoundary() != null) {
            // 如果没有边界点但有多边形对象，则提取边界点
            try {
                boundaryPoints = new ArrayList<>();
                org.locationtech.jts.geom.Coordinate[] coordinates =
                        region.getBoundary().getExteriorRing().getCoordinates();

                // 跳过最后一个坐标（因为在闭合多边形中它与第一个点重复）
                for (int i = 0; i < coordinates.length - 1; i++) {
                    org.locationtech.jts.geom.Coordinate coord = coordinates[i];
                    // 注意：这里格式是 "纬度,经度"，与前端期望一致
                    boundaryPoints.add(coord.y + "," + coord.x);
                }

                // 设置边界点到区域对象
                region.setBoundaryPoints(boundaryPoints);
                logger.debug("从Polygon中提取了{}个边界点", boundaryPoints.size());
            } catch (Exception e) {
                logger.error("从Polygon提取边界点时出错: {}", e.getMessage(), e);
            }
        }
    }

    // ==================== 请求/响应类 ====================

    /**
     * 批量费率状态更新请求
     */
    @Setter
    @Getter
    public static class BatchRateStatusRequest {
        // Getters and setters
        private List<Long> ids;
        private boolean enabled;

    }

}