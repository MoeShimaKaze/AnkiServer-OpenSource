package com.server.anki.utils;

/**
 * 测试标记工具类
 * 集中管理所有测试相关的标记和检测逻辑
 */
public class TestMarkerUtils {
    // 测试订单标记前缀
    public static final String TEST_ORDER_PREFIX = "[TEST]_";

    /**
     * 检查字符串是否包含测试标记
     */
    public static boolean hasTestMarker(String text) {
        return text != null && text.startsWith(TEST_ORDER_PREFIX);
    }

    /**
     * 添加测试标记到字符串
     */
    public static String addTestMarker(String text) {
        if (text == null) {
            return TEST_ORDER_PREFIX;
        }

        // 避免重复添加标记
        if (hasTestMarker(text)) {
            return text;
        }

        return TEST_ORDER_PREFIX + text;
    }

    /**
     * 从字符串中移除测试标记
     */
    public static String removeTestMarker(String text) {
        if (!hasTestMarker(text)) {
            return text;
        }

        return text.substring(TEST_ORDER_PREFIX.length());
    }
}