package com.server.anki.user;

import com.server.anki.user.enums.UserIdentity;
import com.server.anki.user.enums.UserVerificationStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Map;

@Getter
@Setter
public class UserDTO {
    private Long id;
    private String username;
    private String email;
    private LocalDate registrationDate;
    private LocalDate birthday;
    private String gender;
    private UserVerificationStatus userVerificationStatus;
    private String userGroup;
    private Boolean systemAccount;
    private Boolean canLogin;
    private String avatarUrl;
    private UserIdentity userIdentity;
    private String alipayUserId;
    private String newPassword;
    private String verificationCode;
    // 新增字段的getter和setter
    // 新增字段，用于存储认证照片URL等个人信息
    private Map<String, Object> personalInfo;

    // 默认构造函数
    public UserDTO() {
    }

    // 带参数的构造函数
    public UserDTO(Long id, String username, String email, LocalDate registrationDate,
                   LocalDate birthday, String gender, UserVerificationStatus userVerificationStatus,
                   String userGroup, Boolean systemAccount, Boolean canLogin, String avatarUrl,
                   UserIdentity userIdentity, String alipayUserId) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.registrationDate = registrationDate;
        this.birthday = birthday;
        this.gender = gender;
        this.userVerificationStatus = userVerificationStatus;
        this.userGroup = userGroup;
        this.systemAccount = systemAccount;
        this.canLogin = canLogin;
        this.avatarUrl = avatarUrl;
        this.userIdentity = userIdentity;
        this.alipayUserId = alipayUserId;
    }

    // 添加包含personalInfo的新构造函数
    public UserDTO(Long id, String username, String email, LocalDate registrationDate,
                   LocalDate birthday, String gender, UserVerificationStatus userVerificationStatus,
                   String userGroup, Boolean systemAccount, Boolean canLogin, String avatarUrl,
                   UserIdentity userIdentity, String alipayUserId, Map<String, Object> personalInfo) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.registrationDate = registrationDate;
        this.birthday = birthday;
        this.gender = gender;
        this.userVerificationStatus = userVerificationStatus;
        this.userGroup = userGroup;
        this.systemAccount = systemAccount;
        this.canLogin = canLogin;
        this.avatarUrl = avatarUrl;
        this.userIdentity = userIdentity;
        this.alipayUserId = alipayUserId;
        this.personalInfo = personalInfo;
    }

    /**
     * 从User实体创建UserDTO的静态工厂方法
     *
     * @param user 用户实体
     * @return 转换后的DTO对象，如果输入为null则返回null
     */
    public static UserDTO fromEntity(User user) {
        if (user == null) {
            return null;
        }

        return new UserDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRegistrationDate(),
                user.getBirthday(),
                user.getGender(),
                user.getUserVerificationStatus(),
                user.getUserGroup(),
                user.getSystemAccount(),
                user.canLogin(), // 使用canLogin()方法
                user.getAvatarUrl(),
                user.getUserIdentity(),
                user.getAlipayUserId(),
                user.getPersonalInfo()
        );
    }
}