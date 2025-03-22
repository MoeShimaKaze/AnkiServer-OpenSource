package com.server.anki.marketing.region.model;

import java.util.List;

/**
 * 配送区域更新请求
 */
public record RegionUpdateRequest(
        String name,
        String description,
        double rateMultiplier,
        int priority,
        boolean active,
        List<String> boundaryPoints
) {
    public RegionUpdateRequest {
        if (name != null && name.trim().isEmpty()) {
            throw new IllegalArgumentException("区域名称不能为空");
        }
        if (rateMultiplier <= 0) {
            throw new IllegalArgumentException("费率倍数必须大于0");
        }
        if (boundaryPoints != null && boundaryPoints.size() < 3) {
            throw new IllegalArgumentException("区域边界至少需要3个点");
        }
    }
}