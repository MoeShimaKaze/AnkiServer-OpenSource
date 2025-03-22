package com.server.anki.shopping.service;

import com.server.anki.shopping.config.StoreConfig;
import com.server.anki.shopping.dto.StoreDTO;
import com.server.anki.shopping.entity.MerchantInfo;
import com.server.anki.shopping.entity.Store;
import com.server.anki.shopping.enums.StoreStatus;
import com.server.anki.shopping.exception.InvalidOperationException;
import com.server.anki.shopping.exception.StoreNotFoundException;
import com.server.anki.shopping.repository.MerchantRepository;
import com.server.anki.shopping.repository.StoreRepository;
import com.server.anki.shopping.service.validator.StoreValidator;
import com.server.anki.user.User;
import com.server.anki.user.UserService;
import com.server.anki.wallet.service.WalletService;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 店铺管理服务
 * 负责处理店铺的创建、更新、查询等相关业务逻辑
 */
@Slf4j
@Service
public class StoreService {
    private static final Logger logger = LoggerFactory.getLogger(StoreService.class);

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private MerchantService merchantService;

    @Autowired
    private StoreValidator storeValidator;

    @Autowired
    private UserService userService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private StoreConfig storeConfig;

    @Autowired
    private MerchantRepository merchantRepository;

    @Transactional
    public Store createStore(StoreDTO storeDTO) {
        logger.info("开始处理店铺创建请求，商家ID: {}", storeDTO.getMerchantId());

        // 验证店铺信息
        storeValidator.validateCreateStore(storeDTO);

        // 验证商家身份
        if (!merchantService.isMerchant(storeDTO.getMerchantId())) {
            throw new InvalidOperationException("用户不是商家，无法创建店铺");
        }

        // 验证用户是否为商家用户组
        validateMerchantUserGroup(storeDTO.getMerchantId());

        // 获取用户信息
        User merchant = userService.getUserById(storeDTO.getMerchantId());

        // 获取商家信息
        MerchantInfo merchantInfo;
        try {
            // 先尝试通过MerchantRepository直接查找
            Optional<MerchantInfo> merchantInfoOpt = merchantRepository.findByPrimaryUser(merchant);
            if (merchantInfoOpt.isPresent()) {
                merchantInfo = merchantInfoOpt.get();
            } else {
                // 备选方案：通过getUserMerchants查找
                List<MerchantInfo> merchantInfos = merchantService.getUserMerchants(storeDTO.getMerchantId());
                if (merchantInfos.isEmpty()) {
                    throw new InvalidOperationException("未找到商家信息，请先完成商家认证");
                }
                merchantInfo = merchantInfos.get(0);
            }
        } catch (Exception e) {
            throw new InvalidOperationException("获取商家信息失败: " + e.getMessage());
        }

        // 验证并扣除保证金（管理员创建店铺不需要缴纳保证金）
        if (!userService.isAdminUser(merchant) && !hasSecurityDeposit(storeDTO.getMerchantId())) {
            // 检查用户余额是否充足
            BigDecimal securityDeposit = storeConfig.getSecurityDeposit();
            BigDecimal currentBalance = walletService.getBalance(merchant);

            if (currentBalance.compareTo(securityDeposit) < 0) {
                throw new InvalidOperationException(
                        String.format("余额不足，开店需要保证金 %.2f 元，当前余额 %.2f 元，请先充值",
                                securityDeposit, currentBalance)
                );
            }

            // 余额充足，尝试扣除保证金
            try {
                deductSecurityDeposit(merchant);
            } catch (Exception e) {
                throw new InvalidOperationException("扣除保证金失败: " + e.getMessage());
            }
        }

        // 创建店铺实体
        Store store = getStore(storeDTO, merchant);

        // 关键修复：设置商家信息关联
        store.setMerchantInfo(merchantInfo);

        // 保存店铺信息
        Store savedStore = storeRepository.save(store);
        logger.info("店铺创建成功，店铺ID: {}", savedStore.getId());

        return savedStore;
    }

    /**
     * 扣除保证金
     * 修改：接收User对象而不是userId
     */
    private void deductSecurityDeposit(User user) {
        User systemAccount = userService.getOrCreateSystemAccount();
        BigDecimal securityDeposit = storeConfig.getSecurityDeposit();

        try {
            // 使用现有的transferFunds方法
            walletService.transferFunds(
                    user,
                    systemAccount,
                    securityDeposit,
                    "商家店铺保证金"
            );

            logger.info("已成功扣除用户[{}]的店铺保证金: {}", user.getId(), securityDeposit);
        } catch (Exception e) {
            logger.error("扣除保证金失败，用户ID: {}, 错误: {}", user.getId(), e.getMessage());
            throw new RuntimeException("扣除保证金失败，请确保钱包余额充足: " + e.getMessage());
        }
    }

    @NotNull
    private static Store getStore(StoreDTO storeDTO, User merchant) {
        Store store = new Store();
        store.setStoreName(storeDTO.getStoreName());
        store.setDescription(storeDTO.getDescription());
        store.setMerchant(merchant);
        store.setStatus(StoreStatus.PENDING_REVIEW);
        store.setContactPhone(storeDTO.getContactPhone());
        store.setBusinessHours(storeDTO.getBusinessHours());
        store.setLocation(storeDTO.getLocation());
        store.setLatitude(storeDTO.getLatitude());
        store.setLongitude(storeDTO.getLongitude());
        return store;
    }

    /**
     * 验证用户是否有创建店铺的权限
     * 管理员和商家身份都可以创建店铺
     */
    private void validateMerchantUserGroup(Long userId) {
        User user = userService.getUserById(userId);
        if (!"store".equals(user.getUserGroup()) && !userService.isAdminUser(user)) {
            throw new InvalidOperationException("用户不是商家用户组(store)或管理员，无法创建店铺");
        }
    }

    /**
     * 获取活跃店铺列表
     * 分页查询处于营业中状态的店铺
     *
     * @param pageable 分页参数
     * @return 店铺分页列表
     */
    public Page<Store> getActiveStores(Pageable pageable) {
        return storeRepository.findStoreByStatus(StoreStatus.ACTIVE, pageable);
    }

    /**
     * 审核店铺申请
     * 管理员专用方法，用于审核待审核状态的店铺
     *
     * @param storeId 店铺ID
     * @param approved 是否批准
     * @param remarks 审核备注
     * @return 更新后的店铺信息
     */
    @Transactional
    public Store auditStore(Long storeId, boolean approved, String remarks) {
        logger.info("开始审核店铺，店铺ID: {}, 是否通过: {}", storeId, approved);

        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new StoreNotFoundException(storeId));

        // 验证店铺是否处于待审核状态
        if (store.getStatus() != StoreStatus.PENDING_REVIEW) {
            throw new InvalidOperationException("只能审核处于待审核状态的店铺");
        }

        // 设置新状态（通过或拒绝）
        StoreStatus newStatus = approved ? StoreStatus.ACTIVE : StoreStatus.REJECTED;
        store.setStatus(newStatus);

        // 设置审核备注
        store.setRemarks(remarks);

        // 保存更新
        Store updatedStore = storeRepository.save(store);
        logger.info("店铺审核完成，店铺ID: {}, 审核结果: {}", storeId, newStatus);

        return updatedStore;
    }

    /**
     * 检查商家是否已缴纳保证金
     * 判断依据：
     * 1. 如果是管理员，视为已缴纳保证金
     * 2. 商家是否已有非拒绝状态的店铺
     */
    public boolean hasSecurityDeposit(Long merchantId) {
        User merchant = userService.getUserById(merchantId);

        // 管理员不需要缴纳保证金
        if (userService.isAdminUser(merchant)) {
            return true;
        }

        List<Store> stores = storeRepository.findByMerchant(merchant);

        // 如果已有店铺且状态不是被拒绝，则认为已缴纳保证金
        return stores.stream()
                .anyMatch(store -> store.getStatus() != StoreStatus.REJECTED);
    }

    /**
     * 更新店铺信息
     * 允许商家更新店铺的基本信息，需要验证更新的合法性
     */
    @Transactional
    public Store updateStore(Long storeId, StoreDTO storeDTO) {
        logger.info("开始更新店铺信息，店铺ID: {}", storeId);

        Store existingStore = storeRepository.findById(storeId)
                .orElseThrow(() -> new StoreNotFoundException(storeId));

        // 验证更新请求
        storeValidator.validateUpdateStore(existingStore, storeDTO);

        // 更新店铺信息
        existingStore.setStoreName(storeDTO.getStoreName());
        existingStore.setDescription(storeDTO.getDescription());
        existingStore.setContactPhone(storeDTO.getContactPhone());
        existingStore.setBusinessHours(storeDTO.getBusinessHours());
        existingStore.setLocation(storeDTO.getLocation());
        existingStore.setLatitude(storeDTO.getLatitude());
        existingStore.setLongitude(storeDTO.getLongitude());

        // 保存更新
        Store updatedStore = storeRepository.save(existingStore);
        logger.info("店铺信息更新成功，店铺ID: {}", storeId);

        return updatedStore;
    }

    /**
     * 更新店铺状态
     * 用于处理店铺的审核、暂停、关闭等状态变更
     */
    @Transactional
    public void updateStoreStatus(Long storeId, StoreStatus newStatus) {
        logger.info("更新店铺状态，店铺ID: {}, 新状态: {}", storeId, newStatus);

        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new StoreNotFoundException(storeId));

        // 验证状态变更的合法性
        validateStatusTransition(store.getStatus(), newStatus);

        store.setStatus(newStatus);
        storeRepository.save(store);
    }

    /**
     * 搜索附近的店铺
     * 基于地理位置查找指定范围内的活跃店铺
     */
    public List<Store> findNearbyStores(Double latitude, Double longitude, Double distance) {
        return storeRepository.findNearbyStores(latitude, longitude, distance);
    }

    /**
     * 搜索店铺
     * 根据店铺名称搜索店铺
     */
    public Page<Store> searchStores(String keyword, Pageable pageable) {
        return storeRepository.findByStoreNameContaining(keyword, pageable);
    }

    /**
     * 获取商家的所有店铺
     * 查询指定商家开设的所有店铺
     * 修改后：直接使用用户ID查询，并处理用户不是商家的情况
     */
    public List<Store> getMerchantStores(Long userId) {
        logger.debug("获取用户关联的店铺列表, userId: {}", userId);

        try {
            // 获取用户信息
            User user = userService.getUserById(userId);

            // 直接查询该用户的店铺
            List<Store> stores = storeRepository.findByMerchant(user);
            logger.info("成功获取用户店铺列表，userId: {}, 店铺数量: {}", userId, stores.size());
            return stores;
        } catch (Exception e) {
            logger.warn("获取商家店铺时出现异常，返回空列表. userId: {}, 错误: {}", userId, e.getMessage());
            // 返回空列表而不是抛出异常
            return new ArrayList<>();
        }
    }

    /**
     * 验证店铺状态变更的合法性
     */
    private void validateStatusTransition(StoreStatus currentStatus, StoreStatus newStatus) {
        // 禁止直接从未审核状态变为营业状态
        if (currentStatus == StoreStatus.PENDING_REVIEW && newStatus == StoreStatus.ACTIVE) {
            throw new InvalidOperationException("店铺必须通过审核才能营业");
        }

        // 已关闭的店铺不能变更状态
        if (currentStatus == StoreStatus.CLOSED) {
            throw new InvalidOperationException("已关闭的店铺不能变更状态");
        }
    }

    /**
     * 根据ID获取店铺
     */
    public Store getStoreById(Long storeId) {
        return storeRepository.findById(storeId)
                .orElseThrow(() -> new StoreNotFoundException(storeId));
    }
}