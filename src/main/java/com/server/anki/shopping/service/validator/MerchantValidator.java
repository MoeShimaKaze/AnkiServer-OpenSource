package com.server.anki.shopping.service.validator;

import com.server.anki.shopping.dto.MerchantDTO;
import com.server.anki.shopping.entity.MerchantInfo;
import com.server.anki.shopping.exception.InvalidOperationException;
import com.server.anki.shopping.repository.MerchantRepository;
import com.server.anki.user.UserService;
import com.server.anki.user.enums.UserVerificationStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 商家信息验证器
 * 负责验证商家相关的业务规则，确保商家信息的合法性和完整性
 */
@Component
public class MerchantValidator {

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private UserService userService;

    /**
     * 验证商家入驻申请
     * 检查用户认证状态、营业执照信息等必要条件
     */
    public void validateMerchantRegistration(MerchantDTO merchantDTO) {
        // 验证用户认证状态
        validateUserVerification(merchantDTO.getUserId());

        // 验证营业执照信息
        validateBusinessLicense(merchantDTO.getBusinessLicense());

        // 验证联系信息
        validateContactInfo(merchantDTO.getContactName(), merchantDTO.getContactPhone());

        // 验证经营地址
        validateBusinessAddress(merchantDTO.getBusinessAddress());
    }

    /**
     * 验证商家信息更新请求
     * 确保更新操作符合业务规则
     */
    public void validateMerchantUpdate(MerchantInfo existingMerchant, MerchantDTO merchantDTO) {
        // 验证营业执照信息（如果有变更）
        if (!existingMerchant.getBusinessLicense().equals(merchantDTO.getBusinessLicense())) {
            validateBusinessLicense(merchantDTO.getBusinessLicense());
        }

        // 验证联系信息
        validateContactInfo(merchantDTO.getContactName(), merchantDTO.getContactPhone());

        // 验证经营地址
        validateBusinessAddress(merchantDTO.getBusinessAddress());
    }

    /**
     * 验证用户的认证状态
     * 只有通过实名认证的用户才能申请成为商家
     */
    private void validateUserVerification(Long userId) {
        var user = userService.getUserById(userId);
        if (user.getUserVerificationStatus() != UserVerificationStatus.VERIFIED) {
            throw new InvalidOperationException("用户未完成实名认证，无法申请成为商家");
        }
    }

    /**
     * 验证营业执照信息
     * 检查营业执照号的格式和唯一性
     */
    private void validateBusinessLicense(String businessLicense) {
        // 验证营业执照号格式（18位统一社会信用代码）
        if (!businessLicense.matches("^[A-Za-z0-9]{18}$")) {
            throw new InvalidOperationException("营业执照号格式不正确");
        }

        // 验证营业执照号唯一性
        if (merchantRepository.existsByBusinessLicense(businessLicense)) {
            throw new InvalidOperationException("该营业执照已被注册");
        }
    }

    /**
     * 验证联系信息
     * 确保联系人姓名和电话号码符合规范
     */
    private void validateContactInfo(String contactName, String contactPhone) {
        // 验证联系人姓名
        if (contactName == null || contactName.trim().length() < 2) {
            throw new InvalidOperationException("联系人姓名不能少于2个字符");
        }

        // 验证联系电话格式（中国大陆手机号）
        if (!contactPhone.matches("^1[3-9]\\d{9}$")) {
            throw new InvalidOperationException("联系电话格式不正确");
        }
    }

    /**
     * 验证经营地址
     * 确保地址信息完整且有效
     */
    private void validateBusinessAddress(String businessAddress) {
        if (businessAddress == null || businessAddress.trim().length() < 10) {
            throw new InvalidOperationException("经营地址信息不完整");
        }
    }

    /**
     * 验证商家等级变更
     * 确保等级变更符合规则
     */
    public void validateLevelChange(MerchantInfo merchant, Integer totalSales, Double rating) {
        // 这里可以添加商家等级变更的具体业务规则
        // 例如：基于销售额、评分等条件判断是否可以升级
        if (totalSales < 0 || rating < 0 || rating > 5) {
            throw new InvalidOperationException("无效的销售额或评分数据");
        }
    }

    /**
     * 验证商家状态
     * 检查商家是否具备特定操作的权限
     */
    public void validateMerchantStatus(MerchantInfo merchant) {
        // 验证商家的认证状态、信用状态等
        if (merchant.getRating() < 2.0) {
            throw new InvalidOperationException("商家信用评分过低，部分功能受限");
        }
    }
}