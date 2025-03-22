package com.server.anki.question.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.server.anki.question.enums.QuestionStatus;
import com.server.anki.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 问题回复实体类
 */
@Setter
@Getter
@Entity
@Table(name = "question_replies")
public class QuestionReply {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "applied", nullable = false)
    private boolean applied = false;

    @Version
    @Column(name = "version")
    private Long version;

    @Setter
    @Getter
    @Column(name = "is_rejected", nullable = false)// 提供默认值，确保不会是null
    private boolean rejected = false;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void apply() {
        if (this.applied) {
            throw new IllegalStateException("已经申请过了");
        }
        this.applied = true;
    }

    public boolean canApply() {
        return !this.applied && this.question.getStatus() == QuestionStatus.OPEN;
    }

    public boolean isReplyFromUser(Long userId) {
        return this.user != null && this.user.getId().equals(userId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QuestionReply)) return false;
        return id != null && id.equals(((QuestionReply) o).getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}