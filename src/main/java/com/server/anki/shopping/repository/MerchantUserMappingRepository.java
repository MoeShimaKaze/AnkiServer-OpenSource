package com.server.anki.shopping.repository;

import com.server.anki.shopping.entity.MerchantInfo;
import com.server.anki.shopping.entity.MerchantUserMapping;
import com.server.anki.shopping.enums.MerchantUserRole;
import com.server.anki.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 商家用户映射仓库
 * 提供商家-用户关系的数据访问方法
 */
@Repository
public interface MerchantUserMappingRepository extends JpaRepository<MerchantUserMapping, Long> {
    List<MerchantUserMapping> findByMerchantInfo(MerchantInfo merchantInfo);

    List<MerchantUserMapping> findByUser(User user);

    Optional<MerchantUserMapping> findByMerchantInfoAndUser(MerchantInfo merchantInfo, User user);

    List<MerchantUserMapping> findByMerchantInfoAndRole(MerchantInfo merchantInfo, MerchantUserRole role);

    @Query("SELECT m FROM MerchantUserMapping m WHERE m.merchantInfo.merchantUid = :merchantUid")
    List<MerchantUserMapping> findByMerchantUid(@Param("merchantUid") String merchantUid);

    @Query("SELECT m FROM MerchantUserMapping m WHERE m.merchantInfo.merchantUid = :merchantUid AND m.user.id = :userId")
    Optional<MerchantUserMapping> findByMerchantUidAndUserId(
            @Param("merchantUid") String merchantUid,
            @Param("userId") Long userId);

    @Query("SELECT COUNT(m) > 0 FROM MerchantUserMapping m WHERE m.merchantInfo.merchantUid = :merchantUid AND m.user.id = :userId")
    boolean existsByMerchantUidAndUserId(
            @Param("merchantUid") String merchantUid,
            @Param("userId") Long userId);
}