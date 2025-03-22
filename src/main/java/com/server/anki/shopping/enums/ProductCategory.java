// ProductCategory.java
package com.server.anki.shopping.enums;

import lombok.Getter;

/**
 * 商品分类枚举
 * 用于对商品进行分类管理
 */
@Getter
public enum ProductCategory {
    FOOD("食品", "包括零食、饮料等"),
    DAILY_NECESSITIES("日用品", "包括生活用品、文具等"),
    ELECTRONICS("电子产品", "包括数码配件等"),
    CLOTHING("服装", "包括衣服、鞋帽等"),
    BOOKS("图书", "包括教材、课外读物等"),
    BEAUTY("美妆", "包括化妆品、护肤品等"),
    SPORTS("运动", "包括运动器材、运动服饰等"),
    OTHER("其他", "其他类别商品"),
    MEDICINE("药品", "药物");

    private final String label;
    private final String description;

    ProductCategory(String label, String description) {
        this.label = label;
        this.description = description;
    }
}