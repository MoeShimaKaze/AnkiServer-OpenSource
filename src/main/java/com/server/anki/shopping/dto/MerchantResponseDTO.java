package com.server.anki.shopping.dto;

import com.server.anki.shopping.entity.MerchantInfo;
import com.server.anki.shopping.enums.MerchantLevel;
import com.server.anki.user.UserDTO;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商家信息响应数据传输对象
 * 用于API返回商家信息，避免敏感字段泄露
 */
@Data
public class MerchantResponseDTO {
    private Long id;
    private String merchantUid;
    private String businessLicense;
    private String licenseImage;
    private String contactName;
    private String contactPhone;
    private String businessAddress;
    private MerchantLevel merchantLevel;
    private Integer totalSales;
    private Double rating;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String verificationStatus;
    private UserDTO primaryUser;

    // 无参构造函数
    public MerchantResponseDTO() {
    }

    /**
     * 从MerchantInfo实体和UserDTO构造响应DTO
     * @param merchant 商家信息实体
     * @param userDTO 用户DTO
     * @return 商家响应DTO
     */
    public static MerchantResponseDTO fromEntity(MerchantInfo merchant, UserDTO userDTO) {
        if (merchant == null) {
            return null;
        }

        MerchantResponseDTO dto = new MerchantResponseDTO();
        dto.setId(merchant.getId());
        dto.setMerchantUid(merchant.getMerchantUid());
        dto.setBusinessLicense(merchant.getBusinessLicense());
        dto.setLicenseImage(merchant.getLicenseImage()); // 已经是完整的预签名URL
        dto.setContactName(merchant.getContactName());
        dto.setContactPhone(merchant.getContactPhone());
        dto.setBusinessAddress(merchant.getBusinessAddress());
        dto.setMerchantLevel(merchant.getMerchantLevel());
        dto.setTotalSales(merchant.getTotalSales());
        dto.setRating(merchant.getRating());
        dto.setCreatedAt(merchant.getCreatedAt());
        dto.setUpdatedAt(merchant.getUpdatedAt());
        dto.setVerificationStatus(merchant.getVerificationStatus());
        dto.setPrimaryUser(userDTO);

        return dto;
    }

    /**
     * 从MerchantInfo实体构造响应DTO
     * @param merchant 商家信息实体
     * @return 商家响应DTO
     */
    public static MerchantResponseDTO fromEntity(MerchantInfo merchant) {
        if (merchant == null) {
            return null;
        }

        MerchantResponseDTO dto = new MerchantResponseDTO();
        dto.setId(merchant.getId());
        dto.setMerchantUid(merchant.getMerchantUid());
        dto.setBusinessLicense(merchant.getBusinessLicense());
        dto.setLicenseImage(merchant.getLicenseImage());
        dto.setContactName(merchant.getContactName());
        dto.setContactPhone(merchant.getContactPhone());
        dto.setBusinessAddress(merchant.getBusinessAddress());
        dto.setMerchantLevel(merchant.getMerchantLevel());
        dto.setTotalSales(merchant.getTotalSales());
        dto.setRating(merchant.getRating());
        dto.setCreatedAt(merchant.getCreatedAt());
        dto.setUpdatedAt(merchant.getUpdatedAt());
        dto.setVerificationStatus(merchant.getVerificationStatus());

        // 自动转换用户信息
        if (merchant.getPrimaryUser() != null) {
            dto.setPrimaryUser(UserDTO.fromEntity(merchant.getPrimaryUser()));
        }

        return dto;
    }
}