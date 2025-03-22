package com.server.anki.shopping.repository;

import com.server.anki.shopping.entity.MerchantInfo;
import com.server.anki.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 商家信息仓库
 * 提供商家信息的数据访问方法
 */
@Repository
public interface MerchantRepository extends JpaRepository<MerchantInfo, Long> {
    Optional<MerchantInfo> findByPrimaryUser(User user);

    Optional<MerchantInfo> findByBusinessLicense(String businessLicense);

    Optional<MerchantInfo> findByMerchantUid(String merchantUid);

    @Query("SELECT m FROM MerchantInfo m JOIN m.userMappings um WHERE um.user = :user")
    List<MerchantInfo> findByUser(@Param("user") User user);

    List<MerchantInfo> findByRatingBetweenOrderByRatingDesc(Double minRating, Double maxRating);

    boolean existsByBusinessLicense(String businessLicense);

    boolean existsByMerchantUid(String merchantUid);

    @Query("SELECT m FROM MerchantInfo m WHERE m.verificationStatus = :status")
    List<MerchantInfo> findByVerificationStatus(@Param("status") String status);
}