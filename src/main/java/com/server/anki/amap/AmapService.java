package com.server.anki.amap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.server.anki.config.MapConfig;
import com.server.anki.config.RedisConfig;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Set;

@Service
@Slf4j
public class AmapService {
    private static final Logger logger = LoggerFactory.getLogger(AmapService.class);
    private final MapConfig mapConfig;


    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    public AmapService(MapConfig mapConfig,
                       RestTemplate restTemplate,
                       RedisTemplate<String, String> redisTemplate) {
        this.mapConfig = mapConfig;
        this.restTemplate = restTemplate;
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void validateApiKey() {
        if (mapConfig.getWebKey() == null || mapConfig.getWebKey().length() != 32) {
            logger.warn("高德地图Web服务API密钥格式可能不正确，请检查配置");
        }
        logger.info("高德地图API密钥已加载");
    }

    private static final String WALKING_URL = "https://restapi.amap.com/v5/direction/walking";
    private static final String CACHE_NAME = "distance_cache";
    private static final long CACHE_DURATION = 24 * 60 * 60; // 24小时缓存
    // 添加电动自行车导航URL常量
    private static final String EBIKE_URL = "https://restapi.amap.com/v5/direction/electrobike";
    private static final double DISTANCE_THRESHOLD = 3.0; // 3公里阈值

    /**
     * 智能计算配送距离
     * 根据直线距离判断使用步行还是电动自行车导航
     *
     * @param originLat 起点纬度
     * @param originLng 起点经度
     * @param destLat 终点纬度
     * @param destLng 终点经度
     * @return 最优配送距离（公里）
     */
    public double calculateOptimalDeliveryDistance(double originLat, double originLng,
                                                   double destLat, double destLng) {
        logger.debug("开始计算最优配送距离: ({}, {}) -> ({}, {})",
                originLat, originLng, destLat, destLng);

        try {
            // 1. 先计算直线距离作为参考
            double linearDistance = calculateLinearDistance(originLat, originLng, destLat, destLng) / 1000.0;
            logger.debug("直线距离为: {}公里", linearDistance);

            // 2. 根据直线距离选择导航方式
            if (linearDistance > DISTANCE_THRESHOLD) {
                // 超过3公里，使用电动自行车导航
                logger.debug("直线距离超过{}公里，使用电动自行车导航", DISTANCE_THRESHOLD);
                try {
                    double ebikeDistance = calculateEbikeDistance(originLat, originLng, destLat, destLng) / 1000.0;
                    logger.debug("电动自行车导航距离: {}公里", ebikeDistance);
                    return ebikeDistance;
                } catch (Exception e) {
                    logger.warn("电动自行车导航计算失败: {}, 回退到步行导航", e.getMessage());
                    double walkingDistance = calculateWalkingDistance(originLat, originLng, destLat, destLng) / 1000.0;
                    logger.debug("步行导航距离: {}公里", walkingDistance);
                    return walkingDistance;
                }
            } else {
                // 不超过3公里，使用步行导航
                logger.debug("直线距离不超过{}公里，使用步行导航", DISTANCE_THRESHOLD);
                double walkingDistance = calculateWalkingDistance(originLat, originLng, destLat, destLng) / 1000.0;
                logger.debug("步行导航距离: {}公里", walkingDistance);
                return walkingDistance;
            }
        } catch (Exception e) {
            // 所有计算都失败，使用直线距离作为最后的备选
            logger.error("导航距离计算失败: {}, 使用直线距离", e.getMessage());
            try {
                double linearDistance = calculateLinearDistance(originLat, originLng, destLat, destLng) / 1000.0;
                logger.debug("回退到直线距离: {}公里", linearDistance);
                return linearDistance;
            } catch (Exception ex) {
                // 连直线距离也计算失败，返回默认值
                logger.error("直线距离计算也失败: {}, 使用默认值", ex.getMessage());
                return 1.0; // 默认1公里
            }
        }
    }

    /**
     * 计算电动自行车导航距离
     */
    private double calculateEbikeDistance(double originLat, double originLng,
                                          double destLat, double destLng) {
        logger.debug("开始计算电动自行车导航距离");

        // 构建请求URL并发送请求
        String url = buildEbikeUrl(originLat, originLng, destLat, destLng);
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        if (response.getStatusCode() != HttpStatus.OK) {
            throw new AmapServiceException("高德地图API调用失败: " + response.getStatusCode());
        }

        // 解析响应获取距离
        JsonNode root;
        try {
            root = new ObjectMapper().readTree(response.getBody());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        validateAmapResponse(root);
        double distance = extractPathDistance(root);

        logger.debug("电动自行车导航距离计算结果: {}米", distance);
        return distance;
    }

    /**
     * 构建高德地图电动自行车导航API请求URL
     */
    private String buildEbikeUrl(double originLat, double originLng,
                                 double destLat, double destLng) {
        return UriComponentsBuilder.fromHttpUrl(EBIKE_URL)
                .queryParam("key", mapConfig.getWebKey())
                .queryParam("origin", String.format("%.6f,%.6f", originLng, originLat))
                .queryParam("destination", String.format("%.6f,%.6f", destLng, destLat))
                .queryParam("show_fields", "cost")
                .build()
                .toUriString();
    }

    /**
     * 从API响应中提取路径距离
     */
    private double extractPathDistance(JsonNode root) {
        JsonNode pathNode = root.path("route")
                .path("paths")
                .path(0);

        if (pathNode.isMissingNode()) {
            throw new AmapServiceException("无法从响应中获取路径信息");
        }

        String distanceStr = pathNode.path("distance").asText();
        double distance = Double.parseDouble(distanceStr);

        // 检查是否为有效距离
        if (distance <= 0) {
            throw new AmapServiceException("获取到的距离无效: " + distance);
        }

        return distance;
    }

    /**
     * 计算步行导航距离，使用Redis缓存结果
     * @param originLat 起点纬度
     * @param originLng 起点经度
     * @param destLat 终点纬度
     * @param destLng 终点经度
     * @return 步行距离（单位：米）
     */
    @Cacheable(value = CACHE_NAME, key = "T(com.server.anki.config.RedisConfig).getAmapDistanceCacheKey(#originLat, #originLng, #destLat, #destLng)")
    public double calculateWalkingDistance(double originLat, double originLng,
                                           double destLat, double destLng) {
        try {
            logger.debug("开始计算步行导航距离: ({}, {}) -> ({}, {})",
                    originLat, originLng, destLat, destLng);

            // 构建请求URL并发送请求
            String url = buildWalkingUrl(originLat, originLng, destLat, destLng);
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode() != HttpStatus.OK) {
                throw new AmapServiceException("高德地图API调用失败: " + response.getStatusCode());
            }

            // 解析响应获取距离
            JsonNode root = new ObjectMapper().readTree(response.getBody());
            validateAmapResponse(root);
            double distance = extractWalkingDistance(root);

            logger.debug("导航距离计算结果: {}米", distance);
            return distance;

        } catch (Exception e) {
            logger.error("计算步行导航距离时发生错误: ", e);
            throw new AmapServiceException("计算步行导航距离失败", e);
        }
    }

    /**
     * 清除特定路线的缓存
     */
    public void clearDistanceCache(double originLat, double originLng,
                                   double destLat, double destLng) {
        String cacheKey = RedisConfig.getAmapDistanceCacheKey(
                originLat, originLng, destLat, destLng);
        redisTemplate.delete(cacheKey);
        logger.debug("已清除路线距离缓存: {}", cacheKey);
    }

    /**
     * 清除所有距离缓存
     */
    public void clearAllDistanceCache() {
        String pattern = RedisConfig.AMAP_DISTANCE_CACHE_PREFIX + "*";
        Set<String> keys = redisTemplate.keys(pattern);
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
            logger.debug("已清除所有距离缓存, 共{}条", keys.size());
        }
    }

    /**
     * 计算两点之间的直线距离，使用Redis缓存结果
     * @param lat1 起点纬度
     * @param lng1 起点经度
     * @param lat2 终点纬度
     * @param lng2 终点经度
     * @return 直线距离（单位：米）
     */
    @Cacheable(value = CACHE_NAME, key = "T(com.server.anki.config.RedisConfig).getAmapDistanceCacheKey(#lat1, #lng1, #lat2, #lng2)")
    public double calculateLinearDistance(double lat1, double lng1, double lat2, double lng2) {
        try {
            logger.debug("开始计算直线距离: ({}, {}) -> ({}, {})",
                    lat1, lng1, lat2, lng2);

            // 地球半径（米）
            final double EARTH_RADIUS = 6371000;

            // 将经纬度转换为弧度
            double radLat1 = Math.toRadians(lat1);
            double radLng1 = Math.toRadians(lng1);
            double radLat2 = Math.toRadians(lat2);
            double radLng2 = Math.toRadians(lng2);

            // 经纬度差值
            double deltaLat = radLat2 - radLat1;
            double deltaLng = radLng2 - radLng1;

            // 使用Haversine公式计算球面距离
            double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                    Math.cos(radLat1) * Math.cos(radLat2) *
                            Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);
            double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
            double distance = EARTH_RADIUS * c;

            logger.debug("直线距离计算结果: {}米", distance);
            return distance;

        } catch (Exception e) {
            logger.error("计算直线距离时发生错误: ", e);
            throw new AmapServiceException("计算直线距离失败", e);
        }
    }

    /**
     * 清除特定直线距离的缓存
     */
    public void clearLinearDistanceCache(double lat1, double lng1, double lat2, double lng2) {
        String cacheKey = RedisConfig.getAmapDistanceCacheKey(lat1, lng1, lat2, lng2);
        redisTemplate.delete(cacheKey);
        logger.debug("已清除直线距离缓存: {}", cacheKey);
    }


    /**
     * 构建高德地图步行导航API请求URL
     */
    private String buildWalkingUrl(double originLat, double originLng,
                                   double destLat, double destLng) {
        return UriComponentsBuilder.fromHttpUrl(WALKING_URL)
                .queryParam("key", mapConfig.getWebKey())
                .queryParam("origin", String.format("%.6f,%.6f", originLng, originLat))
                .queryParam("destination", String.format("%.6f,%.6f", destLng, destLat))
                .queryParam("show_fields", "cost")
                .build()
                .toUriString();
    }


    /**
     * 验证高德地图API响应
     */
    private void validateAmapResponse(JsonNode root) {
        String status = root.path("status").asText();
        String info = root.path("info").asText();

        if (!"1".equals(status) || !"OK".equalsIgnoreCase(info)) {
            String infoCode = root.path("infocode").asText();
            logger.error("高德地图API返回错误: status={}, info={}, infocode={}",
                    status, info, infoCode);
            throw new AmapServiceException(
                    String.format("高德地图API返回错误: status=%s, info=%s, infocode=%s",
                            status, info, infoCode));
        }
    }

    /**
     * 从API响应中提取步行导航距离
     */
    private double extractWalkingDistance(JsonNode root) {
        JsonNode pathNode = root.path("route")
                .path("paths")
                .path(0);

        if (pathNode.isMissingNode()) {
            throw new AmapServiceException("无法从响应中获取路径信息");
        }

        String distanceStr = pathNode.path("distance").asText();
        double distance = Double.parseDouble(distanceStr);

        // 检查是否为有效距离
        if (distance <= 0) {
            throw new AmapServiceException("获取到的距离无效: " + distance);
        }

        return distance;
    }

    /**
     * 从缓存获取距离
     */
    private Double getCachedDistance(String key) {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            Cache.ValueWrapper value = cache.get(key);
            if (value != null) {
                return (Double) value.get();
            }
        }
        return null;
    }

    /**
     * 将距离存入缓存
     */
    private void cacheDistance(String key, double distance) {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            cache.put(key, distance);
        }
    }
}
