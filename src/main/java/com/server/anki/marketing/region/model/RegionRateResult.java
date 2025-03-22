package com.server.anki.marketing.region.model;

import com.server.anki.marketing.region.DeliveryRegion;

/**
 * 区域费率计算结果
 */
public record RegionRateResult(
        double finalRate,             // 最终费率
        boolean isCrossRegion,        // 是否跨区域
        DeliveryRegion pickupRegion,  // 取件区域
        DeliveryRegion deliveryRegion // 配送区域
) {
    public String getDescription() {
        if (!isCrossRegion) {
            if (pickupRegion == null && deliveryRegion == null) {
                return "未划分区域，使用默认费率";
            }
            DeliveryRegion region = pickupRegion != null ? pickupRegion : deliveryRegion;
            return String.format("位于%s区域内", region.getName());
        }
        // 增加防御性检查，防止空指针异常
        if (pickupRegion == null || deliveryRegion == null) {
            return "跨区域订单，但部分区域信息缺失";
        }
        return String.format("跨区域订单: 从%s到%s",
                pickupRegion.getName(),
                deliveryRegion.getName());
    }
}