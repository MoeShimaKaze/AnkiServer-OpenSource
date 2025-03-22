// StoreStatus.java
package com.server.anki.shopping.enums;

import lombok.Getter;

/**
 * 店铺状态枚举
 * 用于表示店铺的当前运营状态
 */
@Getter
public enum StoreStatus {
    PENDING_REVIEW("待审核", "店铺信息正在审核中"),
    ACTIVE("营业中", "店铺正常营业"),
    SUSPENDED("已暂停", "店铺暂停营业"),
    CLOSED("已关闭", "店铺已永久关闭"),
    REJECTED("已拒绝", "店铺申请被拒绝");

    private final String label;
    private final String description;

    StoreStatus(String label, String description) {
        this.label = label;
        this.description = description;
    }
}