package com.server.anki.shopping.controller.response;

import com.server.anki.shopping.entity.Store;
import com.server.anki.shopping.enums.StoreStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 店铺信息响应类
 * 用于向前端返回店铺相关信息
 */
@Data
@Builder
public class StoreResponse {
    private Long id;
    private String storeName;
    private String description;
    private Long merchantId;
    private String merchantName;
    private StoreStatus status;
    private String contactPhone;
    private String businessHours;
    private String location;
    private Double latitude;
    private Double longitude;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Double rating;       // 店铺评分
    private Integer orderCount;  // 订单总数
    private Boolean isOpen;      // 是否营业中
    private String remarks;  // 添加备注字段


    /**
     * 将Store实体转换为响应对象
     */
    public static StoreResponse fromStore(Store store) {
        return StoreResponse.builder()
                .id(store.getId())
                .storeName(store.getStoreName())
                .description(store.getDescription())
                .merchantId(store.getMerchant().getId())
                .merchantName(store.getMerchant().getUsername())
                .status(store.getStatus())
                .contactPhone(store.getContactPhone())
                .businessHours(store.getBusinessHours())
                .location(store.getLocation())
                .latitude(store.getLatitude())
                .longitude(store.getLongitude())
                .createdAt(store.getCreatedAt())
                .updatedAt(store.getUpdatedAt())
                .rating(calculateStoreRating(store))
                .orderCount(calculateOrderCount(store))
                .isOpen(isStoreOpen(store))
                .remarks(store.getRemarks())  // 设置备注字段
                .build();
    }

    /**
     * 计算店铺评分
     */
    private static Double calculateStoreRating(Store store) {
        // 可以根据订单评价计算店铺评分
        return store.getMerchantInfo().getRating();
    }

    /**
     * 计算店铺订单总数
     */
    private static Integer calculateOrderCount(Store store) {
        // 添加null检查，避免空指针异常
        return store.getProducts() != null ? store.getProducts().size() : 0;
    }

    /**
     * 判断店铺是否正在营业
     */
    private static Boolean isStoreOpen(Store store) {
        return store.getStatus() == StoreStatus.ACTIVE;
        // 可以根据营业时间判断当前是否营业
    }
}