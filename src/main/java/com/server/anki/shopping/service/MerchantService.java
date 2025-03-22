package com.server.anki.shopping.service;

import com.server.anki.shopping.dto.MerchantDTO;
import com.server.anki.shopping.dto.MerchantEmployeeDTO;
import com.server.anki.shopping.entity.MerchantInfo;
import com.server.anki.shopping.entity.MerchantUserMapping;
import com.server.anki.shopping.enums.MerchantLevel;
import com.server.anki.shopping.enums.MerchantUserRole;
import com.server.anki.shopping.exception.MerchantNotFoundException;
import com.server.anki.shopping.repository.MerchantRepository;
import com.server.anki.shopping.repository.MerchantUserMappingRepository;
import com.server.anki.shopping.service.validator.MerchantValidator;
import com.server.anki.storage.MinioService;
import com.server.anki.user.User;
import com.server.anki.user.UserService;
import com.server.anki.user.enums.UserVerificationStatus;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 商家管理服务
 * 负责处理商家入驻、员工管理、等级评定等业务逻辑
 */
@Slf4j
@Service
public class MerchantService {
    private static final Logger logger = LoggerFactory.getLogger(MerchantService.class);

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private MerchantUserMappingRepository merchantUserMappingRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private MerchantValidator merchantValidator;

    @Autowired
    private MinioService minioService;

    /**
     * 商家入驻申请
     * 处理新商家的注册流程，包括信息验证和入驻审核
     */
    @Transactional
    public MerchantInfo registerMerchant(MerchantDTO merchantDTO) {
        logger.info("开始处理商家入驻申请，用户ID: {}", merchantDTO.getUserId());

        // 首先检查用户实名认证状态
        checkUserVerificationStatus(merchantDTO.getUserId());

        // 验证入驻申请信息
        merchantValidator.validateMerchantRegistration(merchantDTO);

        // 获取用户信息
        User user = userService.getUserById(merchantDTO.getUserId());

        // 创建商家信息实体
        MerchantInfo merchantInfo = getMerchantInfo(merchantDTO, user);

        // 保存商家信息
        MerchantInfo savedMerchant = merchantRepository.save(merchantInfo);
        logger.info("商家入驻申请已保存，商家ID: {}, UID: {}",
                savedMerchant.getId(), savedMerchant.getMerchantUid());

        // 创建用户和商家的映射关系，设置为拥有者角色
        createMerchantUserMapping(savedMerchant, user, MerchantUserRole.OWNER);

        return savedMerchant;
    }

    @NotNull
    private static MerchantInfo getMerchantInfo(MerchantDTO merchantDTO, User user) {
        MerchantInfo merchantInfo = new MerchantInfo();
        merchantInfo.setPrimaryUser(user);  // 只设置primaryUser，不再设置userId
        merchantInfo.setBusinessLicense(merchantDTO.getBusinessLicense());
        merchantInfo.setLicenseImage(merchantDTO.getLicenseImage());
        merchantInfo.setContactName(merchantDTO.getContactName());
        merchantInfo.setContactPhone(merchantDTO.getContactPhone());
        merchantInfo.setBusinessAddress(merchantDTO.getBusinessAddress());
        merchantInfo.setMerchantLevel(MerchantLevel.BRONZE); // 设置初始等级
        merchantInfo.setTotalSales(0);
        merchantInfo.setRating(5.0); // 初始评分
        merchantInfo.setVerificationStatus("PENDING"); // 审核状态
        return merchantInfo;
    }

    /**
     * 创建商家和用户的映射关系
     */
    @Transactional
    public MerchantUserMapping createMerchantUserMapping(MerchantInfo merchantInfo, User user, MerchantUserRole role) {
        logger.info("创建商家用户映射关系，商家ID: {}, 用户ID: {}, 角色: {}",
                merchantInfo.getId(), user.getId(), role);

        // 检查映射是否已存在
        Optional<MerchantUserMapping> existingMapping =
                merchantUserMappingRepository.findByMerchantInfoAndUser(merchantInfo, user);

        if (existingMapping.isPresent()) {
            // 如果已存在，更新角色
            MerchantUserMapping mapping = existingMapping.get();
            mapping.setRole(role);
            logger.info("更新已存在的商家用户映射，ID: {}", mapping.getId());
            return merchantUserMappingRepository.save(mapping);
        }

        // 创建新的映射
        MerchantUserMapping mapping = new MerchantUserMapping();
        mapping.setMerchantInfo(merchantInfo);
        mapping.setUser(user);
        mapping.setRole(role);
        mapping.setInvitationAccepted(true);

        // 为所有用户设置invitedByUserId，对于创建者，设置为自己的ID
        if (role == MerchantUserRole.OWNER) {
            mapping.setInvitedByUserId(user.getId());  // 创建者自己邀请自己
        }

        merchantInfo.addUserMapping(mapping);
        MerchantUserMapping savedMapping = merchantUserMappingRepository.save(mapping);
        logger.info("商家用户映射创建成功，ID: {}", savedMapping.getId());

        return savedMapping;
    }

    /**
     * 添加员工到商家
     */
    @Transactional
    public MerchantUserMapping addEmployee(String merchantUid, Long userId, MerchantUserRole role, Long invitedByUserId) {
        logger.info("添加员工到商家, merchantUid: {}, userId: {}, role: {}", merchantUid, userId, role);

        // 检查用户实名认证状态
        checkUserVerificationStatus(userId);

        // 获取商家信息
        MerchantInfo merchantInfo = getMerchantInfoByUid(merchantUid);

        // 获取用户信息
        User user = userService.getUserById(userId);

        // 检查是否已经是员工
        Optional<MerchantUserMapping> existingMapping =
                merchantUserMappingRepository.findByMerchantInfoAndUser(merchantInfo, user);

        if (existingMapping.isPresent()) {
            throw new IllegalArgumentException("该用户已经是此商家的员工");
        }

        // 创建新映射
        MerchantUserMapping mapping = new MerchantUserMapping();
        mapping.setMerchantInfo(merchantInfo);
        mapping.setUser(user);
        mapping.setRole(role);
        mapping.setInvitedByUserId(invitedByUserId);

        // 如果是自主加入，设置为已接受邀请
        mapping.setInvitationAccepted(invitedByUserId == null || invitedByUserId.equals(userId));

        merchantInfo.addUserMapping(mapping);
        MerchantUserMapping savedMapping = merchantUserMappingRepository.save(mapping);
        logger.info("员工添加成功，映射ID: {}", savedMapping.getId());

        // 如果是OWNER或ADMIN，更新用户组为store
        if (role == MerchantUserRole.OWNER || role == MerchantUserRole.ADMIN) {
            user.setUserGroup("store");
            userService.saveUser(user);
            logger.info("用户组已更新为store，用户ID: {}", userId);
        }

        return savedMapping;
    }

    /**
     * 接受商家邀请
     */
    @Transactional
    public MerchantUserMapping acceptInvitation(Long mappingId) {
        logger.info("接受商家邀请，映射ID: {}", mappingId);

        MerchantUserMapping mapping = merchantUserMappingRepository.findById(mappingId)
                .orElseThrow(() -> new IllegalArgumentException("找不到指定的邀请"));

        mapping.setInvitationAccepted(true);
        return merchantUserMappingRepository.save(mapping);
    }

    /**
     * 拒绝商家邀请
     */
    @Transactional
    public void rejectInvitation(Long mappingId) {
        logger.info("拒绝商家邀请，映射ID: {}", mappingId);

        MerchantUserMapping mapping = merchantUserMappingRepository.findById(mappingId)
                .orElseThrow(() -> new IllegalArgumentException("找不到指定的邀请"));

        merchantUserMappingRepository.delete(mapping);
    }

    /**
     * 获取商家员工列表
     */
    @Transactional(readOnly = true)
    public List<MerchantEmployeeDTO> getMerchantEmployees(String merchantUid) {
        logger.info("获取商家员工列表, merchantUid: {}", merchantUid);

        // 获取商家信息
        MerchantInfo merchantInfo = getMerchantInfoByUid(merchantUid);

        // 获取员工映射
        List<MerchantUserMapping> mappings = merchantUserMappingRepository.findByMerchantInfo(merchantInfo);

        // 转换为DTO
        return mappings.stream()
                .filter(MerchantUserMapping::getInvitationAccepted)
                .map(mapping -> {
                    MerchantEmployeeDTO dto = new MerchantEmployeeDTO();
                    dto.setUserId(mapping.getUser().getId());
                    dto.setUsername(mapping.getUser().getUsername());
                    dto.setRole(mapping.getRole());
                    dto.setEmail(mapping.getUser().getEmail());
                    dto.setAvatarUrl(mapping.getUser().getAvatarUrl());
                    dto.setJoinedAt(mapping.getCreatedAt());
                    dto.setIsPrimaryUser(merchantInfo.getPrimaryUser().getId().equals(mapping.getUser().getId()));
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取待接受的商家邀请
     */
    @Transactional(readOnly = true)
    public List<MerchantUserMapping> getPendingInvitations(Long userId) {
        logger.info("获取待接受的商家邀请，用户ID: {}", userId);

        User user = userService.getUserById(userId);

        return merchantUserMappingRepository.findByUser(user).stream()
                .filter(mapping -> !mapping.getInvitationAccepted())
                .collect(Collectors.toList());
    }

    /**
     * 更新员工角色
     */
    @Transactional
    public MerchantUserMapping updateEmployeeRole(String merchantUid, Long userId, MerchantUserRole newRole) {
        logger.info("更新员工角色, merchantUid: {}, userId: {}, newRole: {}", merchantUid, userId, newRole);

        // 获取商家信息
        MerchantInfo merchantInfo = getMerchantInfoByUid(merchantUid);

        // 检查是否是主要用户
        if (merchantInfo.getPrimaryUser().getId().equals(userId) && newRole != MerchantUserRole.OWNER) {
            throw new IllegalArgumentException("不能更改主要用户的角色");
        }

        // 获取映射关系
        MerchantUserMapping mapping = merchantUserMappingRepository
                .findByMerchantUidAndUserId(merchantUid, userId)
                .orElseThrow(() -> new IllegalArgumentException("该用户不是此商家的员工"));

        // 更新角色
        mapping.setRole(newRole);

        // 更新用户组（如果需要）
        User user = mapping.getUser();
        if (newRole == MerchantUserRole.OWNER || newRole == MerchantUserRole.ADMIN) {
            user.setUserGroup("store");
            userService.saveUser(user);
        }

        logger.info("员工角色更新成功，用户ID: {}, 新角色: {}", userId, newRole);
        return merchantUserMappingRepository.save(mapping);
    }

    /**
     * 移除员工
     */
    @Transactional
    public void removeEmployee(String merchantUid, Long userId) {
        logger.info("移除员工, merchantUid: {}, userId: {}", merchantUid, userId);

        // 获取商家信息
        MerchantInfo merchantInfo = getMerchantInfoByUid(merchantUid);

        // 检查是否是主要用户
        if (merchantInfo.getPrimaryUser().getId().equals(userId)) {
            throw new IllegalArgumentException("不能移除主要用户");
        }

        // 获取映射关系
        MerchantUserMapping mapping = merchantUserMappingRepository
                .findByMerchantUidAndUserId(merchantUid, userId)
                .orElseThrow(() -> new IllegalArgumentException("该用户不是此商家的员工"));

        // 检查是否是拥有者
        if (mapping.getRole() == MerchantUserRole.OWNER) {
            // 检查是否是唯一的拥有者
            long ownerCount = merchantUserMappingRepository
                    .findByMerchantInfoAndRole(merchantInfo, MerchantUserRole.OWNER)
                    .size();

            if (ownerCount <= 1) {
                throw new IllegalArgumentException("不能移除唯一的商家拥有者");
            }
        }

        // 移除映射关系
        merchantInfo.removeUserMapping(mapping);
        merchantUserMappingRepository.delete(mapping);
        logger.info("员工已移除，用户ID: {}", userId);

        // 如果用户不再是任何商家的员工，更新用户组为普通用户
        List<MerchantUserMapping> userMappings = merchantUserMappingRepository
                .findByUser(mapping.getUser());

        if (userMappings.isEmpty()) {
            User user = mapping.getUser();
            user.setUserGroup("user");
            userService.saveUser(user);
            logger.info("用户组已更新为user，用户ID: {}", userId);
        }
    }

    /**
     * 根据UID获取商家信息
     */
    @Transactional(readOnly = true)
    public MerchantInfo getMerchantInfoByUid(String merchantUid) {
        logger.debug("根据UID获取商家信息, merchantUid: {}", merchantUid);
        return merchantRepository.findByMerchantUid(merchantUid)
                .orElseThrow(() -> new MerchantNotFoundException("根据UID找不到商家信息"));
    }

    /**
     * 获取用户关联的商家列表
     */
    @Transactional(readOnly = true)
    public List<MerchantInfo> getUserMerchants(Long userId) {
        logger.debug("获取用户关联的商家列表, userId: {}", userId);

        // 获取用户信息
        User user = userService.getUserById(userId);

        // 获取用户的商家映射
        List<MerchantUserMapping> mappings = merchantUserMappingRepository.findByUser(user);

        // 提取商家信息
        return mappings.stream()
                .filter(MerchantUserMapping::getInvitationAccepted)
                .map(MerchantUserMapping::getMerchantInfo)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 更新商家信息
     */
    @Transactional
    public MerchantInfo updateMerchantInfo(Long merchantId, MerchantDTO merchantDTO) {
        logger.info("开始更新商家信息，商家ID: {}", merchantId);

        // 获取现有商家信息
        MerchantInfo existingMerchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new MerchantNotFoundException(merchantId));

        // 验证更新请求
        merchantValidator.validateMerchantUpdate(existingMerchant, merchantDTO);

        // 更新商家信息
        existingMerchant.setContactName(merchantDTO.getContactName());
        existingMerchant.setContactPhone(merchantDTO.getContactPhone());
        existingMerchant.setBusinessAddress(merchantDTO.getBusinessAddress());

        // 保存更新
        MerchantInfo updatedMerchant = merchantRepository.save(existingMerchant);
        logger.info("商家信息更新成功，商家ID: {}", merchantId);

        return updatedMerchant;
    }

    /**
     * 评估并更新商家等级
     */
    @Transactional
    public void evaluateMerchantLevel(Long merchantId) {
        logger.info("开始评估商家等级，商家ID: {}", merchantId);

        MerchantInfo merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new MerchantNotFoundException(merchantId));

        // 验证等级变更条件
        merchantValidator.validateLevelChange(merchant, merchant.getTotalSales(), merchant.getRating());

        // 根据条件确定新等级
        MerchantLevel newLevel = calculateMerchantLevel(merchant);

        // 如果等级发生变化，更新商家信息
        if (newLevel != merchant.getMerchantLevel()) {
            merchant.setMerchantLevel(newLevel);
            merchantRepository.save(merchant);
            logger.info("商家等级已更新，商家ID: {}, 新等级: {}", merchantId, newLevel);
        }
    }

    /**
     * 检查用户是否有指定角色
     */
    @Transactional(readOnly = true)
    public boolean isUserHasRequiredRole(Long userId, String merchantUid, MerchantUserRole minimumRole) {
        logger.debug("检查用户是否有所需角色, userId: {}, merchantUid: {}, minimumRole: {}",
                userId, merchantUid, minimumRole);

        try {
            MerchantInfo merchantInfo = getMerchantInfoByUid(merchantUid);

            Optional<MerchantUserMapping> mapping =
                    merchantUserMappingRepository.findByMerchantUidAndUserId(merchantUid, userId);

            if (mapping.isEmpty() || !mapping.get().getInvitationAccepted()) {
                return false;
            }

            MerchantUserRole userRole = mapping.get().getRole();

            // 按权限级别检查
            return getRolePriority(userRole) >= getRolePriority(minimumRole);
        } catch (Exception e) {
            logger.warn("检查用户角色时发生错误: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取角色优先级
     */
    private int getRolePriority(MerchantUserRole role) {
        return switch (role) {
            case OWNER -> 4;
            case ADMIN -> 3;
            case OPERATOR -> 2;
            case VIEWER -> 1;
        };
    }

    /**
     * 实名认证商家
     */
    @Transactional
    public MerchantInfo verifyMerchant(Long userId, String businessLicense, String realName,
                                       MultipartFile licenseImage, String contactPhone,
                                       String businessAddress) throws Exception {
        logger.info("处理商家实名认证, userId: {}", userId);

        // 检查用户实名认证状态
        checkUserVerificationStatus(userId);

        // 获取用户信息
        User user = userService.getUserById(userId);

        // 检查该用户是否已经是商家
        Optional<MerchantInfo> existingMerchant = merchantRepository.findByPrimaryUser(user);
        if (existingMerchant.isPresent()) {
            throw new IllegalArgumentException("该用户已经是商家，不能重复认证");
        }

        // 检查营业执照是否已被使用
        if (merchantRepository.existsByBusinessLicense(businessLicense)) {
            throw new IllegalArgumentException("该营业执照已被注册");
        }

        // 处理营业执照图片上传
        String licenseImageUrl = null;
        if (licenseImage != null && !licenseImage.isEmpty()) {
            licenseImageUrl = minioService.uploadFile(
                    licenseImage,
                    "merchant",
                    String.format("license_%d_%d", userId, System.currentTimeMillis()),
                    false
            );
        }

        // 创建商家信息
        MerchantInfo merchantInfo = new MerchantInfo();
        merchantInfo.setPrimaryUser(user);  // 只设置primaryUser，不再设置userId
        merchantInfo.setBusinessLicense(businessLicense);
        merchantInfo.setLicenseImage(licenseImageUrl);
        merchantInfo.setContactName(realName);
        merchantInfo.setContactPhone(contactPhone);
        merchantInfo.setBusinessAddress(businessAddress);
        merchantInfo.setMerchantLevel(MerchantLevel.BRONZE);
        merchantInfo.setVerificationStatus("PENDING");

        // 保存商家信息
        MerchantInfo savedMerchant = merchantRepository.save(merchantInfo);

        // 创建用户和商家的映射关系，并设置invitedByUserId为自己
        MerchantUserMapping mapping = createMerchantUserMapping(savedMerchant, user, MerchantUserRole.OWNER);
        mapping.setInvitedByUserId(userId);  // 确保设置为自己的ID
        merchantUserMappingRepository.save(mapping);

        // 更新用户的个人信息
        Map<String, Object> personalInfo = user.getPersonalInfo();
        if (personalInfo == null) {
            personalInfo = new HashMap<>();
        }
        personalInfo.put("merchant_uid", savedMerchant.getMerchantUid());
        personalInfo.put("is_merchant", true);
        user.setPersonalInfo(personalInfo);

        // 更新用户组
        user.setUserGroup("store");
        userService.saveUser(user);

        logger.info("商家实名认证完成，商家ID: {}, UID: {}",
                savedMerchant.getId(), savedMerchant.getMerchantUid());
        return savedMerchant;
    }

    /**
     * 审核商家认证
     */
    @Transactional
    public MerchantInfo auditMerchantVerification(String merchantUid, boolean approved, String remarks) {
        logger.info("审核商家认证, merchantUid: {}, approved: {}", merchantUid, approved);

        MerchantInfo merchant = getMerchantInfoByUid(merchantUid);

        if (approved) {
            merchant.setVerificationStatus("APPROVED");
        } else {
            merchant.setVerificationStatus("REJECTED");
        }

        return merchantRepository.save(merchant);
    }

    /**
     * 获取待审核的商家
     */
    @Transactional(readOnly = true)
    public List<MerchantInfo> getPendingMerchants() {
        logger.info("获取待审核的商家列表");
        return merchantRepository.findByVerificationStatus("PENDING");
    }

    /**
     * 检查用户是否是商家
     */
    public boolean isMerchant(Long userId) {
        logger.debug("检查用户是否是商家, userId: {}", userId);
        User user = userService.getUserById(userId);

        List<MerchantUserMapping> mappings = merchantUserMappingRepository.findByUser(user);

        return !mappings.isEmpty() &&
                mappings.stream().anyMatch(m ->
                        m.getInvitationAccepted() &&
                                (m.getRole() == MerchantUserRole.OWNER || m.getRole() == MerchantUserRole.ADMIN)
                );
    }

    /**
     * 计算商家等级
     */
    private MerchantLevel calculateMerchantLevel(MerchantInfo merchant) {
        if (merchant.getTotalSales() >= 1000 && merchant.getRating() >= 4.8) {
            return MerchantLevel.DIAMOND;
        } else if (merchant.getTotalSales() >= 500 && merchant.getRating() >= 4.5) {
            return MerchantLevel.PLATINUM;
        } else if (merchant.getTotalSales() >= 200 && merchant.getRating() >= 4.2) {
            return MerchantLevel.GOLD;
        } else if (merchant.getTotalSales() >= 50 && merchant.getRating() >= 4.0) {
            return MerchantLevel.SILVER;
        } else {
            return MerchantLevel.BRONZE;
        }
    }

    /**
     * 更新商家评分
     */
    @Transactional
    public void updateMerchantRating(Long merchantId, Double newRating) {
        logger.info("更新商家评分，商家ID: {}, 新评分: {}", merchantId, newRating);

        MerchantInfo merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new MerchantNotFoundException(merchantId));

        // 计算新的综合评分
        double currentRating = merchant.getRating();
        int totalRatings = merchant.getTotalSales();
        double updatedRating = ((currentRating * totalRatings) + newRating) / (totalRatings + 1);

        // 更新评分
        merchant.setRating(updatedRating);
        merchantRepository.save(merchant);

        // 评估是否需要更新商家等级
        evaluateMerchantLevel(merchantId);
    }

    /**
     * 获取商家信息
     */
    public MerchantInfo getMerchantInfo(Long merchantId) {
        return merchantRepository.findById(merchantId)
                .orElseThrow(() -> new MerchantNotFoundException(merchantId));
    }

    /**
     * 检查用户是否已通过实名认证，适合进行商家认证
     * @param userId 用户ID
     * @return 实名认证检查结果
     * @throws IllegalArgumentException 如果用户未实名认证或认证状态不符合要求
     */
    public boolean checkUserVerificationStatus(Long userId) {
        logger.info("检查用户实名认证状态, userId: {}", userId);

        // 获取用户信息
        User user = userService.getUserById(userId);

        // 检查用户是否已实名认证
        if (user.getUserVerificationStatus() != UserVerificationStatus.VERIFIED) {
            logger.warn("用户 {} 未完成实名认证，无法进行商家认证", userId);
            throw new IllegalArgumentException("用户未完成实名认证，请先完成实名认证后再申请成为商家");
        }

        logger.info("用户 {} 已通过实名认证，可以继续商家认证流程", userId);
        return true;
    }
}