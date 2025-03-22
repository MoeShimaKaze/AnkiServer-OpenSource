package com.server.anki.login;

import lombok.Getter;
import lombok.Setter;

/**
 * 用户登录请求类
 */
@Setter
@Getter
public class LoginRequest {
    // getter和setter方法
    private String email; // 邮箱
    private String password; // 密码

    // 构造函数
    public LoginRequest() {}

}
