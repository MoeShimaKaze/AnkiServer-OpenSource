package com.server.anki.auth.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 请求频率限制注解
 * 用于标记需要进行访问频率限制的接口
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * 限流唯一标识，默认为方法全限定名
     */
    String key() default "";

    /**
     * 单位时间内允许的请求次数
     */
    long rate() default 10;

    /**
     * 时间单位，默认为秒
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * 时间数值，与时间单位组合使用
     * 例如：timeValue=1, timeUnit=TimeUnit.MINUTES 表示1分钟内允许的请求次数
     */
    long timeValue() default 1;

    /**
     * 限流类型
     */
    LimitType limitType() default LimitType.IP;

    /**
     * 限流策略类型
     */
    enum LimitType {
        /**
         * 根据IP地址限流
         */
        IP,
        /**
         * 针对接口限流，与IP无关
         */
        GLOBAL
    }
}