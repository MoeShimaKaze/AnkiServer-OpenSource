package com.server.anki.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * MySQL空间数据工具类
 * 用于处理高德地图坐标与MySQL空间数据之间的转换
 */
@Slf4j
@Component
public class MySQLSpatialUtils {

    // 坐标格式验证的正则表达式：支持正负数坐标，经度在前，纬度在后，最多6位小数
    private static final Pattern COORDINATE_PATTERN =
            Pattern.compile("^-?\\d+\\.?\\d{0,6},-?\\d+\\.?\\d{0,6}$");

    // WGS84坐标系的SRID（与高德地图使用的坐标系统兼容）
    public static final int SRID = 4326;

    /**
     * 验证坐标格式是否有效
     * 高德地图坐标格式：经度,纬度，例如：120.123456,30.123456
     */
    public static boolean validateCoordinate(String coordinate) {
        if (coordinate == null || coordinate.trim().isEmpty()) {
            log.debug("坐标验证失败：坐标为空");
            return false;
        }

        // 预处理：去除所有空格
        String originalCoordinate = coordinate;
        coordinate = coordinate.replaceAll("\\s", "");

        if (!originalCoordinate.equals(coordinate)) {
            log.debug("坐标已预处理：从 [{}] 变为 [{}]", originalCoordinate, coordinate);
        }

        if (!COORDINATE_PATTERN.matcher(coordinate).matches()) {
            log.debug("坐标验证失败：格式不匹配正则表达式, 坐标: [{}]", coordinate);
            return false;
        }

        try {
            String[] parts = coordinate.split(",");
            if (parts.length != 2) {
                log.debug("坐标验证失败：分割后不是两部分, 坐标: [{}]", coordinate);
                return false;
            }

            double longitude = Double.parseDouble(parts[0]);
            double latitude = Double.parseDouble(parts[1]);

            // 验证经纬度范围
            boolean isValid = isValidLongitude(longitude) && isValidLatitude(latitude);
            if (!isValid) {
                log.debug("坐标验证失败：经度或纬度超出范围, 经度: {}, 纬度: {}", longitude, latitude);
            }
            return isValid;
        } catch (NumberFormatException e) {
            log.debug("坐标验证失败：数字解析出错, 坐标: [{}], 错误: {}", coordinate, e.getMessage());
            return false;
        }
    }

    private static boolean isValidLatitude(double latitude) {
        return latitude >= -90 && latitude <= 90;
    }

    private static boolean isValidLongitude(double longitude) {
        return longitude >= -180 && longitude <= 180;
    }

    /**
     * 验证多个坐标点是否都有效
     *
     * @param coordinates 要验证的坐标点列表
     * @return 如果任何坐标点无效，则返回true；否则返回false
     */
    public static boolean areCoordinatesInvalid(List<String> coordinates) {
        if (coordinates == null || coordinates.isEmpty()) {
            log.debug("坐标列表验证失败：列表为空或null");
            return true;
        }

        for (int i = 0; i < coordinates.size(); i++) {
            String coordinate = coordinates.get(i);
            if (!validateCoordinate(coordinate)) {
                log.debug("坐标列表中存在无效坐标，索引:{}, 值:{}", i, coordinate);
                return true;
            }
        }

        return false;
    }

    /**
     * 创建MySQL Point对象的SQL表达式
     * 用于在空间查询中表示单个坐标点
     * 注意：MySQL的ST_GeomFromText函数期望坐标顺序为(纬度,经度)，与标准的(经度,纬度)相反
     */
    public static String createPointFromAmapCoordinate(String coordinate) {
        if (!validateCoordinate(coordinate)) {
            throw new IllegalArgumentException("无效的坐标格式: " + coordinate);
        }

        String[] parts = coordinate.split(",");
        // 交换经纬度顺序，使其符合MySQL的期望
        // 输入格式: "经度,纬度" (如 "113.232799,23.149677")
        // 输出格式: "POINT(纬度 经度)" (如 "POINT(23.149677 113.232799)")
        String point = String.format("POINT(%s %s)", parts[1], parts[0]);
        log.debug("创建点坐标 - 原始: {}, MySQL格式: {}", coordinate, point);

        return String.format(
                "ST_GeomFromText('%s', %d)",
                point, SRID
        );
    }

    /**
     * 创建WKT格式的多边形字符串
     * 用于在空间查询中表示区域边界
     * 注意：MySQL的ST_GeomFromText函数期望坐标顺序为(纬度,经度)，与标准的(经度,纬度)相反
     */
    public static String createPolygonWkt(List<String> points) {
        if (areCoordinatesInvalid(points)) {
            throw new IllegalArgumentException("存在无效的边界点坐标");
        }

        // 确保多边形闭合（第一个点和最后一个点相同）
        List<String> closedPoints = new ArrayList<>(points);
        if (!points.isEmpty() && !points.get(0).equals(points.get(points.size() - 1))) {
            closedPoints.add(points.get(0));
            log.debug("多边形未闭合，已自动添加闭合点");
        }

        // 构造WKT格式的多边形文本
        StringBuilder wktBuilder = new StringBuilder("POLYGON((");

        for (int i = 0; i < closedPoints.size(); i++) {
            String[] coords = closedPoints.get(i).split(",");
            // 关键点: 交换经纬度顺序，使其符合MySQL的期望
            // 输入格式: "经度,纬度" (如 "113.232799,23.149677")
            // 输出格式: "纬度 经度" (如 "23.149677 113.232799")
            wktBuilder.append(coords[1]).append(" ").append(coords[0]);

            if (i < closedPoints.size() - 1) {
                wktBuilder.append(",");
            }
        }

        wktBuilder.append("))");

        String result = wktBuilder.toString();
        log.debug("生成多边形WKT: {}", result);
        return result;
    }

    /**
     * 从WKT格式的多边形文本中提取坐标点列表
     * 注意：在WKT中，坐标顺序为(纬度,经度)，而我们需要返回(经度,纬度)格式
     */
    public static List<String> extractPointsFromPolygonWkt(String polygonWkt) {
        // 去除POLYGON(())外壳
        String pointsStr = polygonWkt
                .replace("POLYGON((", "")
                .replace("))", "");

        // 分割并转换坐标点，不再交换坐标顺序
        return Arrays.stream(pointsStr.split(","))
                .map(String::trim)
                .map(p -> {
                    String[] coords = p.split(" ");
                    // 保持经度在前，纬度在后的原始顺序
                    return coords[0] + "," + coords[1];
                })
                .collect(Collectors.toList());
    }

    /**
     * 计算两点之间的直线距离（米）
     * 使用 Haversine 公式计算地球表面两点间的大圆距离
     */
    public static double calculateDistance(String coordinate1, String coordinate2) {
        if (!validateCoordinate(coordinate1) || !validateCoordinate(coordinate2)) {
            throw new IllegalArgumentException("无效的坐标格式");
        }

        // 解析坐标点
        Point2D.Double point1 = parseAndConvertToRadians(coordinate1);
        Point2D.Double point2 = parseAndConvertToRadians(coordinate2);

        // 计算 Haversine 公式中的 a 值
        double a = calculateHaversineA(point1, point2);

        // 计算大圆距离
        double c = 2 * Math.asin(Math.sqrt(a));

        // 地球平均半径（米）
        double earthRadius = 6371000;

        return c * earthRadius;
    }

    /**
     * 解析坐标字符串并转换为弧度
     */
    private static Point2D.Double parseAndConvertToRadians(String coordinate) {
        String[] parts = coordinate.split(",");
        double longitude = Math.toRadians(Double.parseDouble(parts[0]));
        double latitude = Math.toRadians(Double.parseDouble(parts[1]));
        return new Point2D.Double(longitude, latitude);
    }

    /**
     * 计算 Haversine 公式中的 a 值
     */
    private static double calculateHaversineA(Point2D.Double p1, Point2D.Double p2) {
        double deltaLon = p2.x - p1.x;  // Δλ
        double deltaLat = p2.y - p1.y;  // Δφ

        return Math.pow(Math.sin(deltaLat / 2), 2) +
                Math.cos(p1.y) * Math.cos(p2.y) *
                        Math.pow(Math.sin(deltaLon / 2), 2);
    }
}