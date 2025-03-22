package com.server.anki.question.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.server.anki.question.enums.QuestionStatus;
import com.server.anki.question.enums.QuestionType;
import com.server.anki.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * 问题实体类
 */
@Setter
@Getter
@Entity
@Table(name = "questions")
public class Question {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "question_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private QuestionType questionType;

    @Column(name = "image_url")
    private String imageUrl;  // 新增图片URL字段

    @Column(name = "description", columnDefinition = "TEXT", nullable = false)
    private String description;  // base64编码的问题描述

    // 添加getter和setter
    @Getter
    @Column(name = "short_title", length = 30)
    private String shortTitle;  // 添加短标题字段

    @Column(name = "contact_info", nullable = false)
    private String contactInfo;

    @Column(name = "contact_name", nullable = false)
    private String contactName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private QuestionStatus status = QuestionStatus.OPEN;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<QuestionReply> replies = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accepted_user_id")
    private User acceptedUser;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "view_count", nullable = false)
    private int viewCount = 0;

    @Version
    @Column(name = "version")
    private Long version;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void addReply(QuestionReply reply) {
        replies.add(reply);
        reply.setQuestion(this);
    }

    public void removeReply(QuestionReply reply) {
        replies.remove(reply);
        reply.setQuestion(null);
    }

    public void acceptReply(QuestionReply reply) {
        if (this.status != QuestionStatus.OPEN) {
            throw new IllegalStateException("只有待解决的问题才能接受答复");
        }
        this.acceptedUser = reply.getUser();
        this.status = QuestionStatus.IN_PROGRESS;
    }

    public void markAsResolved() {
        if (this.status != QuestionStatus.IN_PROGRESS) {
            throw new IllegalStateException("只有处理中的问题才能标记为已解决");
        }
        this.status = QuestionStatus.RESOLVED;
        this.resolvedAt = LocalDateTime.now();
    }

    public void close() {
        if (this.status == QuestionStatus.CLOSED) {
            throw new IllegalStateException("问题已经关闭");
        }
        this.status = QuestionStatus.CLOSED;
        this.closedAt = LocalDateTime.now();
    }

    public void setShortTitle(String shortTitle) {
        if (shortTitle != null && shortTitle.length() > 30) {
            throw new IllegalArgumentException("短标题不能超过30个字符");
        }
        this.shortTitle = shortTitle;
    }

    public boolean canReply() {
        return this.status == QuestionStatus.OPEN;
    }

    public void incrementViewCount() {
        this.viewCount++;
    }
}