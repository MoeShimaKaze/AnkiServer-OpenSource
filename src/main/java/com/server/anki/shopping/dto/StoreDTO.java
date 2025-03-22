package com.server.anki.shopping.dto;

import com.server.anki.shopping.enums.StoreStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

@Data
public class StoreDTO {
    private Long id;

    @NotBlank(message = "店铺名称不能为空")
    @Size(min = 2, max = 50, message = "店铺名称长度必须在2-50个字符之间")
    private String storeName;

    @Size(max = 500, message = "店铺描述不能超过500个字符")
    private String description;

    @NotNull(message = "商家ID不能为空")
    private Long merchantId;

    private String merchantName;  // 商家名称，用于展示

    @NotNull(message = "店铺状态不能为空")
    private StoreStatus status;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "联系电话格式不正确")
    private String contactPhone;

    @NotBlank(message = "营业时间不能为空")
    private String businessHours;

    @NotBlank(message = "店铺地址不能为空")
    private String location;

    @NotNull(message = "店铺经度不能为空")
    private Double longitude;

    @NotNull(message = "店铺纬度不能为空")
    private Double latitude;

    @Size(max = 500, message = "备注不能超过500个字符")
    private String remarks;  // 添加备注字段

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}