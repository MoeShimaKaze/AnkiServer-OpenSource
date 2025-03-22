package com.server.anki.register;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 用户注册请求类
 */
public class RegistrationRequest {
    // getter和setter方法
    @Setter
    @Getter
    private String username; // 用户名
    @Setter
    @Getter
    private String email; // 邮箱
    @Setter
    @Getter
    private String password; // 密码
    @Getter
    @Setter
    private LocalDate birthday; // 生日
    @Setter
    @Getter
    private String gender; // 性别
    private boolean isVerified; // 是否已验证
    @Setter
    @Getter
    private String verificationCode; // 添加验证码字段

    // 构造函数
    public RegistrationRequest() {}

    public boolean isVerified() {
        return isVerified;
    }

    public void setVerified(boolean verified) {
        isVerified = verified;
    }
}
