package com.server.anki.message;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.server.anki.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
@Setter
@Getter
@Table(name = "message")
@Entity
public class Message {
    // Getters and setters
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)  // 修改为立即加载
    @JoinColumn(name = "user_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password",
            "wallet", "givenRatings", "receivedRatings"}) // 只序列化必要的用户信息
    private User user;

    // 添加用户ID字段
    @JsonProperty("userId")
    @Column(name = "user_id", insertable = false, updatable = false)
    private Long userId;

    @Column(name = "content")
    private String content;

    @Column(name = "type")
    @Enumerated(EnumType.STRING)
    private MessageType type;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    @Column(name = "created_date", nullable = false)
    private LocalDateTime createdDate;

    @Setter
    @Getter
    @Column(name = "is_read", nullable = false)
    private boolean read;

    // 新增重试相关字段
    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "last_retry_time")
    private LocalDateTime lastRetryTime;

    @Column(name = "failure_reason")
    private String failureReason;

}