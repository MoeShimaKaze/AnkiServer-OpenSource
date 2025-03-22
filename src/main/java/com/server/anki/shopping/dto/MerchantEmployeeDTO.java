package com.server.anki.shopping.dto;

import com.server.anki.shopping.enums.MerchantUserRole;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商家员工数据传输对象
 * 用于返回商家员工信息
 */
@Data
public class MerchantEmployeeDTO {
    private Long userId;
    private String username;
    private MerchantUserRole role;
    private String email;
    private String avatarUrl;
    private LocalDateTime joinedAt;
    private Boolean isPrimaryUser;
}