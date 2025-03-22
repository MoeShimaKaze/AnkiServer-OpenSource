package com.server.anki.question.enums;

import lombok.Getter;

/**
 * 问题状态枚举
 * 定义了问题从发布到解决的各个状态
 */
@Getter
public enum QuestionStatus {
    OPEN("待解决", "问题已发布,等待回复和解决"),
    IN_PROGRESS("处理中", "已有人接受处理此问题"),
    RESOLVED("已解决", "问题已经得到解决"),
    CLOSED("已关闭", "问题已关闭");

    private final String displayName;
    private final String description;

    QuestionStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * 根据显示名称获取问题状态
     */
    public static QuestionStatus fromDisplayName(String displayName) {
        for (QuestionStatus status : QuestionStatus.values()) {
            if (status.getDisplayName().equals(displayName)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的问题状态：" + displayName);
    }

    /**
     * 检查是否为终态
     */
    public boolean isFinalState() {
        return this == RESOLVED || this == CLOSED;
    }

    /**
     * 检查是否可以修改
     */
    public boolean isModifiable() {
        return this == OPEN || this == IN_PROGRESS;
    }
}