package com.server.anki.shopping.repository;

import com.server.anki.shopping.entity.Store;
import com.server.anki.shopping.enums.StoreStatus;
import com.server.anki.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 店铺数据访问接口
 * 提供店铺信息的存取和查询功能
 */
@Repository
public interface StoreRepository extends JpaRepository<Store, Long> {

    // 根据商家查找店铺
    List<Store> findByMerchant(User merchant);

    // 根据店铺状态查找店铺
    List<Store> findByStatus(StoreStatus status);

    // 根据店铺名称模糊查询
    Page<Store> findByStoreNameContaining(String storeName, Pageable pageable);

    // 查找特定商家的特定状态的店铺
    List<Store> findByMerchantAndStatus(User merchant, StoreStatus status);

    // 根据地理位置查找店铺（使用简单距离计算）
    @Query("SELECT s FROM Store s WHERE " +
            "6371 * acos(cos(radians(:latitude)) * cos(radians(s.latitude)) * " +
            "cos(radians(s.longitude) - radians(:longitude)) + " +
            "sin(radians(:latitude)) * sin(radians(s.latitude))) < :distance " +
            "AND s.status = 'ACTIVE'")
    List<Store> findNearbyStores(@Param("latitude") Double latitude,
                                 @Param("longitude") Double longitude,
                                 @Param("distance") Double distanceInKm);

    // 检查店铺名称是否已存在
    boolean existsByStoreName(String storeName);

    // 统计特定状态的店铺数量
    long countByStatus(StoreStatus status);

    Page<Store> findStoreByStatus(StoreStatus status, Pageable pageable);
}