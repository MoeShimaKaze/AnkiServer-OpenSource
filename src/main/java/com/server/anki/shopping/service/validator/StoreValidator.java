package com.server.anki.shopping.service.validator;

import com.server.anki.shopping.dto.StoreDTO;
import com.server.anki.shopping.entity.Store;
import com.server.anki.shopping.exception.InvalidOperationException;
import com.server.anki.shopping.repository.StoreRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 店铺信息验证器
 * 负责验证店铺相关的业务规则
 */
@Component
public class StoreValidator {

    @Autowired
    private StoreRepository storeRepository;

    /**
     * 验证店铺创建请求
     */
    public void validateCreateStore(StoreDTO storeDTO) {
        // 验证店铺名称唯一性
        if (storeRepository.existsByStoreName(storeDTO.getStoreName())) {
            throw new InvalidOperationException("店铺名称已存在");
        }

        // 验证营业时间格式
        validateBusinessHours(storeDTO.getBusinessHours());

        // 验证地理位置信息
        validateLocation(storeDTO.getLatitude(), storeDTO.getLongitude());
    }

    /**
     * 验证店铺更新请求
     */
    public void validateUpdateStore(Store existingStore, StoreDTO storeDTO) {
        // 验证店铺名称唯一性（排除自身）
        if (!existingStore.getStoreName().equals(storeDTO.getStoreName()) &&
                storeRepository.existsByStoreName(storeDTO.getStoreName())) {
            throw new InvalidOperationException("店铺名称已存在");
        }

        // 验证营业时间格式
        validateBusinessHours(storeDTO.getBusinessHours());

        // 验证地理位置信息
        validateLocation(storeDTO.getLatitude(), storeDTO.getLongitude());
    }

    /**
     * 验证营业时间格式
     */
    private void validateBusinessHours(String businessHours) {
        // 验证格式为 "HH:mm-HH:mm"
        if (!businessHours.matches("^([01]?[0-9]|2[0-3]):[0-5][0-9]-([01]?[0-9]|2[0-3]):[0-5][0-9]$")) {
            throw new InvalidOperationException("营业时间格式不正确，应为 HH:mm-HH:mm");
        }
    }

    /**
     * 验证地理位置信息
     */
    private void validateLocation(Double latitude, Double longitude) {
        if (latitude < -90 || latitude > 90) {
            throw new InvalidOperationException("纬度范围应在-90到90之间");
        }
        if (longitude < -180 || longitude > 180) {
            throw new InvalidOperationException("经度范围应在-180到180之间");
        }
    }
}