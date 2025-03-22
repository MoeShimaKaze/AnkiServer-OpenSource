package com.server.anki.marketing.region.model;

import java.util.List;

/**
 * 配送区域创建请求
 */
public record RegionCreateRequest(
        String name,                  // 区域名称
        String description,           // 区域描述
        double rateMultiplier,       // 费率倍数
        int priority,                // 优先级
        boolean active,              // 是否激活
        List<String> boundaryPoints  // 边界点列表(格式:longitude,latitude)
) {
    public RegionCreateRequest {
        // 验证必填字段
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("区域名称不能为空");
        }
        if (rateMultiplier <= 0) {
            throw new IllegalArgumentException("费率倍数必须大于0");
        }
        if (boundaryPoints == null || boundaryPoints.size() < 3) {
            throw new IllegalArgumentException("区域边界至少需要3个点");
        }
    }
}