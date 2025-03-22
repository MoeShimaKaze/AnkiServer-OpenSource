package com.server.anki.question.enums;

import lombok.Getter;

/**
 * 问题类型枚举
 * 定义了系统支持的各种电脑问题类型
 */
@Getter
public enum QuestionType {
    HARDWARE_REPAIR("硬件维修", "电脑硬件相关的维修问题"),
    SOFTWARE_ISSUE("软件问题", "软件安装、运行、兼容性等问题"),
    SYSTEM_INSTALL("系统安装", "操作系统安装与配置问题"),
    VIRUS_REMOVAL("病毒清除", "病毒、木马等恶意软件清除"),
    DATA_RECOVERY("数据恢复", "意外删除或丢失的数据恢复"),
    PERFORMANCE_OPTIMIZATION("性能优化", "系统运行速度优化、内存管理等"),
    OTHER("其他", "其他类型的电脑相关问题");

    private final String displayName;
    private final String description;

    QuestionType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * 根据显示名称获取问题类型
     */
    public static QuestionType fromDisplayName(String displayName) {
        for (QuestionType type : QuestionType.values()) {
            if (type.getDisplayName().equals(displayName)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的问题类型：" + displayName);
    }
}