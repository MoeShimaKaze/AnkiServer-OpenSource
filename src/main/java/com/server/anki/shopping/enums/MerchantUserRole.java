package com.server.anki.shopping.enums;

import lombok.Getter;

/**
 * 商家用户角色枚举
 * 定义商家用户的权限级别
 */
@Getter
public enum MerchantUserRole {
    OWNER("拥有者", "商户的创建者，拥有全部权限"),
    ADMIN("管理员", "可以管理商家信息、商品和订单的管理员"),
    OPERATOR("操作员", "可以处理日常订单和库存的操作员"),
    VIEWER("查看者", "只有查看权限的员工");

    private final String label;
    private final String description;

    MerchantUserRole(String label, String description) {
        this.label = label;
        this.description = description;
    }
}