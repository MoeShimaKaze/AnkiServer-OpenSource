package com.server.anki.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisConfig {

    @Autowired
    private AlipayConfig alipayConfig;

    // Redis 连接属性
    @Value("${spring.data.redis.host}")
    private String host;

    @Value("${spring.data.redis.port}")
    private int port;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Value("${spring.data.redis.database:0}")
    private int database;

    // Token Redis 键前缀常量
    public static final String ACCESS_TOKEN_BLACKLIST_PREFIX = "token:blacklist:access:";
    public static final String REFRESH_TOKEN_PREFIX = "token:refresh:";

    // Token 过期时间配置
    @Value("${token.expiration.access}")
    @Getter
    private long accessTokenExpiration;

    @Value("${token.expiration.refresh}")
    @Getter
    private long refreshTokenExpiration;

    // 支付超时相关常量
    public static final String PAYMENT_TIMEOUT_KEY_PREFIX = "pay:timeout:";
    // 从配置文件读取支付超时时间
    @Value("${payment.timeout.duration}")
    @Getter
    private long paymentTimeoutDuration;

    // 添加高德地图距离缓存相关常量
    public static final String AMAP_DISTANCE_CACHE_PREFIX = "amap:distance:";

    // 配置距离缓存过期时间（24小时）
    @Value("${amap.cache.duration:86400}")
    private long amapCacheDuration;

    /**
     * 获取高德地图距离缓存的Redis键
     */
        public static String getAmapDistanceCacheKey(double originLat, double originLng,
                                                 double destLat, double destLng) {
        return String.format("%s%.6f:%.6f:%.6f:%.6f",
                AMAP_DISTANCE_CACHE_PREFIX, originLat, originLng, destLat, destLng);
    }

    /**
     * 获取访问令牌黑名单的 Redis 键
     */
    public static String getAccessTokenBlacklistKey(String token) {
        return ACCESS_TOKEN_BLACKLIST_PREFIX + token;
    }

    /**
     * 获取刷新令牌的 Redis 键
     */
    public static String getRefreshTokenKey(String username) {
        return REFRESH_TOKEN_PREFIX + username;
    }

    /**
     * 创建 Redis 连接工厂
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(host);
        config.setPort(port);
        if (StringUtils.hasText(password)) {
            config.setPassword(password);
        }
        config.setDatabase(database);

        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();
        return factory;
    }

    /**
     * 创建字符串类型的 RedisTemplate
     */
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    /**
     * 创建 Token 黑名单专用的 RedisTemplate
     */
    @Bean(name = "tokenBlacklistTemplate")
    public RedisTemplate<String, String> tokenBlacklistTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    /**
     * 配置特定的缓存配置
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // 创建自定义的ObjectMapper以正确处理Record类的序列化问题
        ObjectMapper objectMapper = createTimeoutStatisticsObjectMapper();

        // 使用自定义的ObjectMapper创建序列化器
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(objectMapper);

        // 创建默认的缓存配置
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(jsonSerializer));

        // 创建超时统计特定的缓存配置
        RedisCacheConfiguration timeoutStatsConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))  // 超时统计缓存30分钟
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(jsonSerializer));

        // 创建高德地图距离缓存的特定配置
        RedisCacheConfiguration amapConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(amapCacheDuration))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(jsonSerializer));

        // 创建节假日缓存的特定配置
        RedisCacheConfiguration holidayConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofDays(1))  // 节假日缓存保留1天
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(jsonSerializer));

        // 构建缓存配置Map
        Map<String, RedisCacheConfiguration> configMap = new HashMap<>();
        configMap.put("amapDistanceCache", amapConfig);
        // 添加超时统计相关的缓存配置
        configMap.put("userTimeoutStats", timeoutStatsConfig);
        configMap.put("systemTimeoutStats", timeoutStatsConfig);
        configMap.put("userTimeoutRanking", timeoutStatsConfig);

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(configMap)
                .build();
    }

    /**
     * 创建专门用于超时统计的ObjectMapper
     * 增强配置以处理复杂嵌套对象的序列化和反序列化
     */
    private ObjectMapper createTimeoutStatisticsObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();

        // 添加Java 8时间模块支持
        objectMapper.registerModule(new JavaTimeModule());

        // 设置可见性
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);

        // 配置序列化特性
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, false);

        // 添加这些特性来处理特殊情况
        objectMapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        objectMapper.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);

        // 更改默认类型处理，确保所有嵌套对象都有类型信息
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.EVERYTHING, // 使用EVERYTHING而不是NON_FINAL
                JsonTypeInfo.As.PROPERTY
        );

        return objectMapper;
    }

    // 节假日缓存相关常量
    public static final String HOLIDAY_CACHE_PREFIX = "holiday:";

    /**
     * 获取节假日缓存的Redis键
     */
    public static String getHolidayCacheKey(LocalDate date) {
        return HOLIDAY_CACHE_PREFIX + date.toString();
    }
    /**
     * 获取支付超时的 Redis key
     */
    public static String getPaymentTimeoutKey(String orderId) {
        return PAYMENT_TIMEOUT_KEY_PREFIX + orderId;
    }


    // 支付宝登录相关的Redis键前缀
    public static final String ALIPAY_AUTH_STATE_PREFIX = "alipay:auth:state:";
    public static final String ALIPAY_AUTH_CODE_PREFIX = "alipay:auth:code:";
    public static final String ALIPAY_LOGIN_ATTEMPT_PREFIX = "alipay:login:attempt:";
    public static final String ALIPAY_USER_MAPPING_PREFIX = "alipay:user:mapping:";

    // 支付宝消息处理相关常量
    public static final String ALIPAY_MSG_PREFIX = "alipay:msg:";
    public static final String ALIPAY_MSG_PROCESSING_PREFIX = "alipay:msg:processing:";
    public static final String ALIPAY_MSG_COMPLETED_PREFIX = "alipay:msg:completed:";
    public static final String ALIPAY_MSG_FAILED_PREFIX = "alipay:msg:failed:";

    // 支付宝用户授权令牌常量
    public static final String ALIPAY_AUTH_TOKEN_PREFIX = "alipay:auth:token:";

    // 消息幂等性配置
    @Value("${alipay.msg.expiration:604800}")  // 默认7天(秒)
    @Getter
    private long msgExpiration;

    // 支付宝登录相关的配置参数
    @Value("${alipay.login.state.expiration:300}")  // 5分钟
    @Getter
    private long stateExpiration;

    @Value("${alipay.login.auth-code.expiration:300}")  // 5分钟
    @Getter
    private long authCodeExpiration;

    @Value("${alipay.login.cooldown:5}")  // 5秒
    @Getter
    private long loginCooldown;

    // Redis key生成方法
    public static String getAlipayStateKey(String state) {
        return ALIPAY_AUTH_STATE_PREFIX + state;
    }

    /**
     * 生成支付宝授权码的Redis键
     * 用于跟踪授权码的状态(pending/processing/completed/failed)
     */
    public static String getAlipayAuthCodeKey(String authCode) {
        return ALIPAY_AUTH_CODE_PREFIX + authCode;
    }

    /**
     * 生成支付宝用户映射的Redis键
     * 用于存储授权码与用户名之间的映射关系
     */
    public static String getAlipayUserMappingKey(String authCode) {
        return ALIPAY_USER_MAPPING_PREFIX + authCode;
    }

    /**
     * 获取支付宝消息处理的Redis键
     * 用于标识消息处理状态，确保幂等性
     */
    public static String getAlipayMsgKey(String msgId) {
        return ALIPAY_MSG_PREFIX + msgId;
    }

    /**
     * 获取正在处理的支付宝消息的Redis键
     */
    public static String getAlipayMsgProcessingKey(String msgId) {
        return ALIPAY_MSG_PROCESSING_PREFIX + msgId;
    }

    /**
     * 获取处理完成的支付宝消息的Redis键
     */
    public static String getAlipayMsgCompletedKey(String msgId) {
        return ALIPAY_MSG_COMPLETED_PREFIX + msgId;
    }

    /**
     * 获取处理失败的支付宝消息的Redis键
     */
    public static String getAlipayMsgFailedKey(String msgId) {
        return ALIPAY_MSG_FAILED_PREFIX + msgId;
    }

    /**
     * 获取支付宝用户授权令牌的Redis键
     */
    public static String getAlipayAuthTokenKey(String userId) {
        return ALIPAY_AUTH_TOKEN_PREFIX + userId;
    }

    /**
     * 获取支付宝交易处理的Redis键
     * 用于确保交易处理的幂等性
     */
    public static String getAlipayTradeKey(String orderNumber) {
        return ALIPAY_MSG_PREFIX + "trade:" + orderNumber;
    }

    /**
     * 获取支付宝提现处理的Redis键
     * 用于确保提现处理的幂等性
     */
    public static String getAlipayWithdrawalKey(String withdrawalNumber) {
        return ALIPAY_MSG_PREFIX + "withdrawal:" + withdrawalNumber;
    }

    // 但是过期时间通过实例方法获取
    public long getStateExpirationSeconds() {
        return alipayConfig.getOauth().getStateExpirationSeconds();
    }


    public long getLoginCooldownSeconds() {
        return alipayConfig.getOauth().getLoginCooldownSeconds();
    }

    // 支付宝专用的RedisTemplate
    @Bean(name = "alipayRedisTemplate")
    public RedisTemplate<String, String> alipayRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}