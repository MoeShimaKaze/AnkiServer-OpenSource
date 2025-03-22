package com.server.anki.shopping.dto;

import com.server.anki.shopping.enums.MerchantLevel;
import lombok.Data;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

/**
 * 商家信息数据传输对象
 * 用于前端创建和更新商家信息
 */
@Data
public class MerchantDTO {
    private Long id;

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    private String merchantUid;

    @NotBlank(message = "营业执照号不能为空")
    @Pattern(regexp = "^[A-Za-z0-9]{18}$", message = "营业执照号格式不正确")
    private String businessLicense;

    private String licenseImage;  // 营业执照图片URL

    @NotBlank(message = "联系人姓名不能为空")
    @Size(min = 2, max = 50, message = "联系人姓名长度必须在2-50个字符之间")
    private String contactName;

    @NotBlank(message = "联系电话不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "联系电话格式不正确")
    private String contactPhone;

    @NotBlank(message = "经营地址不能为空")
    private String businessAddress;

    private MerchantLevel merchantLevel;
    private Integer totalSales;
    private Double rating;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String verificationStatus;
}