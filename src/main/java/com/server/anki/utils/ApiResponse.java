package com.server.anki.utils;

import lombok.Getter;

import java.util.Collections;
import java.util.Map;

/**
 * API统一响应格式
 * 用于前后端交互的标准化响应
 */
@Getter
public class ApiResponse {
    private boolean success;
    private String message;
    private Object data;
    private Map<String, Object> meta;

    private ApiResponse(boolean success, String message, Object data, Map<String, Object> meta) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.meta = meta;
    }

    /**
     * 创建成功响应
     *
     * @param data 响应数据
     * @return API响应
     */
    public static ApiResponse success(Object data) {
        return new ApiResponse(true, "操作成功", data, Collections.emptyMap());
    }

    /**
     * 创建成功响应
     *
     * @param data 响应数据
     * @param meta 元数据
     * @return API响应
     */
    public static ApiResponse success(Object data, Map<String, Object> meta) {
        return new ApiResponse(true, "操作成功", data, meta);
    }

    /**
     * 创建成功响应
     *
     * @param message 成功消息
     * @return API响应
     */
    public static ApiResponse success(String message) {
        return new ApiResponse(true, message, null, Collections.emptyMap());
    }

    /**
     * 创建错误响应
     *
     * @param message 错误消息
     * @return API响应
     */
    public static ApiResponse error(String message) {
        return new ApiResponse(false, message, null, Collections.emptyMap());
    }

    /**
     * 创建错误响应
     *
     * @param message 错误消息
     * @param data 附加数据
     * @return API响应
     */
    public static ApiResponse error(String message, Object data) {
        return new ApiResponse(false, message, data, Collections.emptyMap());
    }

}