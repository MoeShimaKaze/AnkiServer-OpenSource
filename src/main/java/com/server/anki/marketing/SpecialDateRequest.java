package com.server.anki.marketing;

import com.server.anki.fee.model.FeeType;
import com.server.anki.marketing.entity.SpecialDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpecialDateRequest {
    private String name;
    private LocalDate date;
    private BigDecimal rateMultiplier;
    private String description;
    private SpecialDateType type;
    private boolean active;
    private boolean rateEnabled;
    private int priority;
    private String feeType;  // 改为String类型以匹配前端

    public SpecialDate toEntity() {
        SpecialDate entity = new SpecialDate();
        entity.setName(this.name);
        entity.setDate(this.date);
        entity.setRateMultiplier(this.rateMultiplier);
        entity.setDescription(this.description);
        entity.setType(this.type);
        entity.setActive(this.active);
        entity.setRateEnabled(this.rateEnabled);
        entity.setPriority(this.priority);

        // 将字符串类型的feeType转换为枚举类型
        entity.setFeeType(convertToFeeTypeEnum(this.feeType));

        return entity;
    }

    // 字符串转FeeType枚举的辅助方法
    private FeeType convertToFeeTypeEnum(String feeTypeStr) {
        if (feeTypeStr == null) {
            return FeeType.ALL_ORDERS;  // 默认使用通用类型
        }

        if ("ALL".equals(feeTypeStr)) {
            return FeeType.ALL_ORDERS;
        }

        try {
            return FeeType.valueOf(feeTypeStr);
        } catch (IllegalArgumentException e) {
            return FeeType.ALL_ORDERS;  // 转换失败时使用默认类型
        }
    }
}
