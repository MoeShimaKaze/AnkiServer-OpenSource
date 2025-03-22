// ProductStatus.java
package com.server.anki.shopping.enums;

import lombok.Getter;

/**
 * 商品状态枚举
 * 用于表示商品的上架和销售状态
 */
@Getter
public enum ProductStatus {
    DRAFT("草稿", "商品信息未完善"),
    PENDING_REVIEW("待审核", "商品信息待审核"),
    ON_SALE("在售", "商品正常销售中"),
    OUT_OF_STOCK("缺货", "商品暂时缺货"),
    DISCONTINUED("已下架", "商品已停止销售"),
    REJECTED("已拒绝", "商品未通过审核");

    private final String label;
    private final String description;

    ProductStatus(String label, String description) {
        this.label = label;
        this.description = description;
    }
}
