package com.server.anki.marketing.region;

import com.server.anki.marketing.region.model.RegionCreateRequest;
import com.server.anki.marketing.region.model.RegionRateResult;
import com.server.anki.marketing.region.model.RegionUpdateRequest;
import com.server.anki.utils.MySQLSpatialUtils;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.operation.valid.IsValidOp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class RegionService {

    private static final Logger logger = LoggerFactory.getLogger(RegionService.class);

    @Autowired
    private DeliveryRegionRepository regionRepository;

    /**
     * 根据高德地图坐标查找区域
     * 使用缓存提高性能
     */
    @Cacheable(value = "region", key = "#amapCoordinate")
    public Optional<DeliveryRegion> findRegionByCoordinate(String amapCoordinate) {
        logger.debug("查找坐标[{}]所在的配送区域", amapCoordinate);

        if (!MySQLSpatialUtils.validateCoordinate(amapCoordinate)) {
            throw new IllegalArgumentException("无效的坐标格式");
        }

        String point = MySQLSpatialUtils.createPointFromAmapCoordinate(amapCoordinate);
        Optional<DeliveryRegion> region = regionRepository.findRegionContainingPoint(point);

        // 处理检索到的区域，确保边界点被正确填充
        region.ifPresent(this::ensureBoundaryPointsPopulated);

        logger.debug("坐标[{}]{}",
                amapCoordinate,
                region.map(r -> "位于" + r.getName() + "区域内").orElse("不在任何配送区域内")
        );

        return region;
    }

    /**
     * 计算订单的区域费率
     * @param pickupCoordinate 取件点坐标
     * @param deliveryCoordinate 配送点坐标
     * @return 费率计算结果
     */
    @SuppressWarnings("SpringCacheableMethodCallsInspection")
    public RegionRateResult calculateOrderRegionRate(String pickupCoordinate,
                                                     String deliveryCoordinate) {
        logger.debug("计算订单区域费率: 取件点[{}], 配送点[{}]",
                pickupCoordinate, deliveryCoordinate);

        // 查找取件点和配送点所在的区域
        Optional<DeliveryRegion> pickupRegion = findRegionByCoordinate(pickupCoordinate);
        Optional<DeliveryRegion> deliveryRegion = findRegionByCoordinate(deliveryCoordinate);

        // 如果都不在任何划定区域内，使用默认费率
        if (pickupRegion.isEmpty() && deliveryRegion.isEmpty()) {
            logger.debug("取件点和配送点均不在已划定的配送区域内，使用默认费率");
            return new RegionRateResult(1.0, false, null, null);
        }

        // 获取取件点和配送点的费率
        double pickupRate = pickupRegion.map(DeliveryRegion::getRateMultiplier)
                .orElse(1.0);
        double deliveryRate = deliveryRegion.map(DeliveryRegion::getRateMultiplier)
                .orElse(1.0);

        // 检查是否跨区域配送
        boolean isCrossRegion = pickupRegion.isPresent() && deliveryRegion.isPresent()
                && !pickupRegion.get().getId().equals(deliveryRegion.get().getId());

        // 计算最终费率（取两个区域费率的平均值）
        double finalRate = (pickupRate + deliveryRate) / 2.0;

        return new RegionRateResult(
                finalRate,
                isCrossRegion,
                pickupRegion.orElse(null),
                deliveryRegion.orElse(null)
        );
    }

    @Transactional
    @CacheEvict(value = "region", allEntries = true)
    public DeliveryRegion createRegion(RegionCreateRequest request) {
        logger.info("创建新的配送区域: {}", request.name());

        // 验证区域边界的有效性
        validateRegionBoundary(request.boundaryPoints());

        // 创建区域实体
        DeliveryRegion region = new DeliveryRegion();
        region.setName(request.name());
        region.setDescription(request.description());
        region.setRateMultiplier(request.rateMultiplier());
        region.setPriority(request.priority());
        region.setActive(request.active());

        try {
            // 创建 WKT 格式的多边形字符串
            String polygonWkt = MySQLSpatialUtils.createPolygonWkt(request.boundaryPoints());
            logger.debug("创建的 WKT 多边形字符串: {}", polygonWkt);

            // 使用原生 SQL 插入数据
            int rows = regionRepository.insertRegion(
                    region.getName(),
                    region.getDescription(),
                    polygonWkt,
                    MySQLSpatialUtils.SRID,
                    region.getRateMultiplier(),
                    region.getPriority(),
                    region.isActive()
            );

            if (rows != 1) {
                throw new RegionServiceException("创建配送区域失败: 影响行数不为1");
            }

            // 使用自定义方法查询新创建的区域
            DeliveryRegion createdRegion = findByName(region.getName())
                    .orElseThrow(() -> new RegionServiceException("无法获取新创建的配送区域"));

            // 设置边界点
            if (createdRegion.getBoundary() == null) {
                createdRegion.setBoundaryPoints(request.boundaryPoints());
            } else {
                // 确保边界点被填充
                ensureBoundaryPointsPopulated(createdRegion);
            }

            return createdRegion;

        } catch (Exception e) {
            logger.error("插入区域数据时发生错误: {}", e.getMessage());
            logger.error("边界点: {}", request.boundaryPoints());
            throw new RegionServiceException("创建配送区域失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据名称查找配送区域
     * 使用原生 SQL 查询并手动处理 WKT 空间数据转换
     */
    public Optional<DeliveryRegion> findByName(String name) {
        Optional<DeliveryRegion> regionOpt = regionRepository.findByNameWithWkt(name)
                .map(this::mapToRegion);

        // 确保边界点被填充
        regionOpt.ifPresent(this::ensureBoundaryPointsPopulated);

        return regionOpt;
    }

    /**
     * 确保区域的边界点被填充
     * 如果区域有boundary对象但没有boundaryPoints，则从boundary中提取
     */
    private void ensureBoundaryPointsPopulated(DeliveryRegion region) {
        if (region == null) return;

        List<String> boundaryPoints = region.getBoundaryPoints();

        if ((boundaryPoints == null || boundaryPoints.isEmpty()) && region.getBoundary() != null) {
            boundaryPoints = new ArrayList<>();
            try {
                Coordinate[] coordinates = region.getBoundary().getExteriorRing().getCoordinates();

                // 跳过最后一个坐标，因为在闭合多边形中它与第一个坐标相同
                for (int i = 0; i < coordinates.length - 1; i++) {
                    Coordinate coord = coordinates[i];
                    // 注意：这里格式是 "纬度,经度"，与前端期望一致
                    // 如果前端期望 "经度,纬度"，则需要交换顺序
                    boundaryPoints.add(coord.y + "," + coord.x);
                }
                region.setBoundaryPoints(boundaryPoints);
                logger.debug("从Polygon中提取了{}个边界点", boundaryPoints.size());
            } catch (Exception e) {
                logger.error("从Polygon提取边界点时出错", e);
            }
        }
    }

    /**
     * 更新配送区域
     */
    @Transactional
    @CacheEvict(value = "region", allEntries = true)
    public DeliveryRegion updateRegion(Long id, RegionUpdateRequest request) {
        logger.info("更新配送区域: ID={}, 名称={}", id, request.name());

        // 查找现有区域
        DeliveryRegion existingRegion = findById(id)
                .orElseThrow(() -> new IllegalArgumentException("配送区域不存在: ID=" + id));

        // 验证区域边界的有效性（如果提供了新的边界点）
        if (request.boundaryPoints() != null && !request.boundaryPoints().isEmpty()) {
            validateRegionBoundary(request.boundaryPoints());
        }

        try {
            // 更新基本属性
            existingRegion.setName(request.name());
            existingRegion.setDescription(request.description());
            existingRegion.setRateMultiplier(request.rateMultiplier());
            existingRegion.setPriority(request.priority());
            existingRegion.setActive(request.active());

            // 如果提供了新的边界点，更新边界
            if (request.boundaryPoints() != null && !request.boundaryPoints().isEmpty()) {
                // 创建 WKT 格式的多边形字符串
                String polygonWkt = MySQLSpatialUtils.createPolygonWkt(request.boundaryPoints());
                logger.debug("更新的 WKT 多边形字符串: {}", polygonWkt);

                // 使用原生 SQL 更新边界
                int rows = regionRepository.updateRegionBoundary(id, polygonWkt);

                if (rows != 1) {
                    throw new RegionServiceException("更新配送区域边界失败: 影响行数不为1");
                }

                // 更新内存中的边界点
                existingRegion.setBoundaryPoints(request.boundaryPoints());
            }

            // 使用JPA更新其他属性
            DeliveryRegion updatedRegion = regionRepository.save(existingRegion);

            // 确保边界点被填充
            ensureBoundaryPointsPopulated(updatedRegion);

            logger.info("配送区域更新成功: ID={}", id);
            return updatedRegion;
        } catch (Exception e) {
            logger.error("更新区域数据时发生错误: {}", e.getMessage());
            if (request.boundaryPoints() != null) {
                logger.error("边界点: {}", request.boundaryPoints());
            }
            throw new RegionServiceException("更新配送区域失败: " + e.getMessage(), e);
        }
    }


    /**
     * 将原生查询结果映射为实体对象
     * 增强版本：处理WKT格式的空间数据，并确保边界点被填充
     */
    private DeliveryRegion mapToRegion(Object[] row) {
        // 创建默认区域对象
        DeliveryRegion region = new DeliveryRegion();
        region.setRateMultiplier(1.0); // 设置默认值
        region.setActive(true);        // 设置默认值
        region.setPriority(0);         // 设置默认值

        try {
            // 记录数组结构以便调试
            logger.debug("处理查询结果: 数组长度={}", row.length);

            // 记录每个字段的值和类型
            for (int i = 0; i < row.length; i++) {
                Object value = row[i];
                String type = value != null ? value.getClass().getSimpleName() : "null";
                logger.trace("字段{}: 值={}, 类型={}", i, value, type);
            }

            // 处理数组长度为1的特殊情况
            if (row.length == 1) {
                logger.warn("查询返回的数组长度为1，可能是嵌套数组或只有ID信息");
                // 情况1: 唯一元素是嵌套数组
                if (row[0] instanceof Object[]) {
                    logger.debug("检测到嵌套数组结构，递归处理内部数组");
                    return mapToRegion((Object[]) row[0]);
                }

                // 情况2: 只有ID信息
                if (row[0] instanceof Number) {
                    Long id = ((Number) row[0]).longValue();
                    region.setId(id);
                    region.setName("区域_" + id); // 设置临时名称
                    logger.debug("查询仅返回ID: {}, 使用默认值填充其他字段", id);
                    return region;
                }

                logger.warn("查询返回的单元素数组无法识别: {}", row[0]);
                return region;
            }

            // 标准情况: 数组包含所有区域字段
            if (row.length >= 7) {
                region.setId(((Number) row[0]).longValue());
                region.setName((String) row[1]);
                region.setDescription((String) row[2]);

                // 解析WKT格式
                String wkt = (String) row[3];
                logger.debug("区域[{}] WKT数据: {}", region.getName(),
                        wkt != null ? (wkt.length() > 50 ? wkt.substring(0, 50) + "..." : wkt) : "null");

                if (wkt != null && !wkt.isEmpty()) {
                    try {
                        WKTReader reader = new WKTReader();
                        Polygon polygon = (Polygon) reader.read(wkt);
                        region.setBoundary(polygon);

                        // 立即从多边形中提取边界点
                        List<String> boundaryPoints = new ArrayList<>();
                        Coordinate[] coordinates = polygon.getExteriorRing().getCoordinates();

                        logger.debug("区域[{}]边界环坐标点数量: {}", region.getName(), coordinates.length);

                        // 跳过最后一个坐标，因为在闭合多边形中它与第一个坐标相同
                        for (int i = 0; i < coordinates.length - 1; i++) {
                            Coordinate coord = coordinates[i];
                            // 注意：这里格式是 "纬度,经度"（y,x），与前端期望一致
                            String point = coord.y + "," + coord.x;
                            boundaryPoints.add(point);
                            logger.trace("边界点{}: {}", i, point);
                        }

                        region.setBoundaryPoints(boundaryPoints);
                        logger.info("成功从WKT解析并提取区域[{}]的{}个边界点",
                                region.getName(), boundaryPoints.size());

                    } catch (Exception e) {
                        logger.error("无法解析WKT格式: {}", wkt, e);
                    }
                } else {
                    logger.warn("区域[{}]没有WKT边界数据", region.getName());
                }

                region.setRateMultiplier(((Number) row[4]).doubleValue());
                region.setActive((Boolean) row[5]);
                region.setPriority(((Number) row[6]).intValue());

                logger.debug("区域[{}]基本信息: 费率={}, 激活={}, 优先级={}",
                        region.getName(), region.getRateMultiplier(),
                        region.isActive(), region.getPriority());
            } else {
                // 填充可用字段，其余使用默认值
                logger.warn("查询返回的元素数量不足(需要7个，实际{}个)，部分使用默认值", row.length);

                if (row.length > 0) region.setId(((Number) row[0]).longValue());
                if (row.length > 1) region.setName((String) row[1]);
                if (row.length > 2) region.setDescription((String) row[2]);
                // 其他字段保持默认值
            }

            return region;
        } catch (Exception e) {
            logger.error("映射区域实体时发生错误: {}", e.getMessage(), e);
            throw new RegionServiceException("映射区域数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 验证区域边界的有效性
     * 使用JTS库验证多边形是否合法
     */
    private void validateRegionBoundary(List<String> boundaryPoints) {
        // 首先验证坐标点格式
        if (MySQLSpatialUtils.areCoordinatesInvalid(boundaryPoints)) {
            throw new IllegalArgumentException("存在无效的边界点坐标格式");
        }

        // 检查点数量是否满足多边形的最小要求
        if (boundaryPoints.size() < 3) {
            throw new IllegalArgumentException("边界多边形无效：至少需要3个点");
        }

        // 使用JTS库验证多边形
        try {
            GeometryFactory factory = new GeometryFactory();

            // 创建坐标数组，加1是为了闭合多边形（首尾相连）
            Coordinate[] coordinates = new Coordinate[boundaryPoints.size() + 1];

            // 转换每个坐标点
            for (int i = 0; i < boundaryPoints.size(); i++) {
                String[] parts = boundaryPoints.get(i).split(",");
                double lon = Double.parseDouble(parts[0]);
                double lat = Double.parseDouble(parts[1]);
                coordinates[i] = new Coordinate(lon, lat);
            }

            // 闭合多边形（首尾相连）
            coordinates[boundaryPoints.size()] = coordinates[0];

            // 创建线性环和多边形
            LinearRing ring = factory.createLinearRing(coordinates);
            Polygon polygon = factory.createPolygon(ring, null);

            // 使用IsValidOp获取详细验证信息
            IsValidOp validOp = new IsValidOp(polygon);
            boolean isValid = validOp.isValid();

            // 验证多边形是否有效
            if (!isValid) {
                // 获取验证错误的位置点和原因
                Coordinate errorLocation = validOp.getValidationError().getCoordinate();
                String errorMessage = validOp.getValidationError().getMessage();

                logger.debug("多边形验证失败: {} 在坐标 ({}, {})",
                        errorMessage,
                        errorLocation.x,
                        errorLocation.y);

                throw new IllegalArgumentException("边界多边形无效：" + errorMessage);
            }

            // 记录验证通过的日志
            logger.debug("多边形验证通过: {} 个顶点", boundaryPoints.size());
        } catch (Exception e) {
            // 区分是否是我们抛出的异常
            if (e instanceof IllegalArgumentException) {
                throw e;
            }
            // 其他异常统一包装为参数异常
            logger.error("多边形验证过程出错", e);
            throw new IllegalArgumentException("边界多边形验证出错: " + e.getMessage());
        }
    }

    /**
     * 获取所有活跃的配送区域
     */
    public List<DeliveryRegion> getAllActiveRegions() {
        logger.info("开始获取所有活跃的配送区域");
        List<Object[]> regionsData = regionRepository.findAllActiveWithWkt();
        logger.info("从数据库获取到{}个活跃配送区域记录", regionsData.size());

        List<DeliveryRegion> regions = new ArrayList<>();

        for (Object[] data : regionsData) {
            DeliveryRegion region = mapToRegion(data);
            regions.add(region);
        }

        // 记录每个区域的边界点数量
        for (DeliveryRegion region : regions) {
            List<String> points = region.getBoundaryPoints();
            logger.debug("区域[{}]边界点数量: {}",
                    region.getName(),
                    points == null ? "null" : points.size());
        }

        logger.info("成功加载{}个活跃配送区域", regions.size());
        return regions;
    }


    /**
     * 获取所有配送区域(包括未激活的)
     */
    public List<DeliveryRegion> getAllRegions() {
        logger.info("开始获取所有配送区域");
        List<Object[]> regionsData = regionRepository.findAllWithWkt();
        logger.info("从数据库获取到{}个配送区域记录", regionsData.size());

        List<DeliveryRegion> regions = new ArrayList<>();

        for (Object[] data : regionsData) {
            DeliveryRegion region = mapToRegion(data);
            regions.add(region);
        }

        // 记录每个区域的边界点数量
        for (DeliveryRegion region : regions) {
            List<String> points = region.getBoundaryPoints();
            logger.debug("区域[{}]边界点数量: {}",
                    region.getName(),
                    points == null ? "null" : points.size());
        }

        logger.info("成功加载{}个配送区域", regions.size());
        return regions;
    }

    /**
     * 根据ID查找配送区域
     */
    public Optional<DeliveryRegion> findById(Long id) {
        Optional<DeliveryRegion> regionOpt = regionRepository.findById(id);
        // 确保边界点被填充
        regionOpt.ifPresent(this::ensureBoundaryPointsPopulated);
        return regionOpt;
    }

    /**
     * 删除配送区域
     */
    @Transactional
    @CacheEvict(value = "region", allEntries = true)
    public void deleteRegion(Long regionId) {
        logger.info("删除配送区域: {}", regionId);
        regionRepository.deleteById(regionId);
    }
}