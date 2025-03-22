package com.server.anki.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.server.anki.rating.Rating;
import com.server.anki.user.enums.UserIdentity;
import com.server.anki.user.enums.UserVerificationStatus;
import com.server.anki.wallet.entity.Wallet;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Table(name = "user")
@Entity
public class User {
    private static final Logger logger = LoggerFactory.getLogger(User.class);

    @JsonIgnore
    @OneToMany(mappedBy = "rater")
    @JsonManagedReference
    private List<Rating> givenRatings;

    @JsonIgnore
    @OneToMany(mappedBy = "ratedUser")
    @JsonManagedReference
    private List<Rating> receivedRatings;

    @Column(name = "is_system_account", nullable = false, columnDefinition = "boolean default false")
    private Boolean systemAccount = false;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 真实个人信息，存储为JSON格式
    @Column(name = "personal_info", columnDefinition = "TEXT")
    @Lob
    private String personalInfo; // 存储JSON格式的真实个人信息

    @Enumerated(EnumType.STRING)
    private UserIdentity userIdentity;

    @Column(name = "verification_status")
    @Enumerated(EnumType.STRING)
    private UserVerificationStatus userVerificationStatus = UserVerificationStatus.UNVERIFIED;

    private String username;
    private String email;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "encrypted_password")
    private String encryptedPassword;

    @Column(name = "registration_date")
    private LocalDate registrationDate;

    private LocalDate birthday = LocalDate.now();
    private String gender = "未知";

    @Column(name = "user_group")
    private String userGroup = "user";

    @Column(name = "can_login")
    private boolean canLogin = true;

    @Setter
    @Column(name = "alipay_user_id")
    private String alipayUserId;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    @JsonManagedReference
    private Wallet wallet;

    public User() {
    }

    public User(String username, String email, String encryptedPassword, LocalDate registrationDate) {
        this.username = username;
        this.email = email;
        this.encryptedPassword = encryptedPassword;
        this.registrationDate = registrationDate;
        this.systemAccount = false;
    }

    public boolean canLogin() {
        return canLogin;
    }

    // 设置JSON字段的getter和setter
    public void setPersonalInfo(Map<String, Object> personalInfoMap) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule()); // 添加对 Java 8 日期/时间类型的支持
            // 明确指定类型为 Map<String, Object> 使用 TypeReference
            this.personalInfo = objectMapper.writeValueAsString(personalInfoMap);
        } catch (Exception e) {
            // 处理异常
            logger.error("设置个人信息失败", e);
        }
    }

    public Map<String, Object> getPersonalInfo() {
        if (this.personalInfo == null || this.personalInfo.isEmpty()) {
            return new HashMap<>();
        }

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule()); // 添加对 Java 8 日期/时间类型的支持
            return objectMapper.readValue(this.personalInfo, new TypeReference<>() {});
        } catch (Exception e) {
            logger.error("获取个人信息失败", e);
            return new HashMap<>();
        }
    }

    // 修改后的方法，处理字符串格式的日期
    public LocalDate getVerificationSubmissionDate() {
        Object dateObj = getPersonalInfo().get("verificationSubmissionDate");
        if (dateObj == null) {
            return null;
        }

        if (dateObj instanceof LocalDate) {
            return (LocalDate) dateObj;
        } else if (dateObj instanceof String) {
            try {
                // 尝试解析ISO格式的日期字符串
                return LocalDate.parse((String) dateObj);
            } catch (DateTimeParseException e) {
                logger.warn("无法解析验证提交日期: {}", dateObj, e);
                return null;
            }
        } else {
            logger.warn("验证提交日期类型不正确: {}", dateObj.getClass().getName());
            return null;
        }
    }

    public void setVerificationSubmissionDate(LocalDate verificationSubmissionDate) {
        Map<String, Object> personalInfo = getPersonalInfo();
        personalInfo.put("verificationSubmissionDate", verificationSubmissionDate);
        setPersonalInfo(personalInfo);
    }
}