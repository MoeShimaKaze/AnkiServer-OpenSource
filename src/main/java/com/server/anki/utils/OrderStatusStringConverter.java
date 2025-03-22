package com.server.anki.utils;

import com.server.anki.mailorder.enums.OrderStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

// 添加转换器类
@Converter
public class OrderStatusStringConverter implements AttributeConverter<OrderStatus, String> {
    @Override
    public String convertToDatabaseColumn(OrderStatus attribute) {
        if (attribute == null) return null;
        // 记录转换过程
        String result = attribute.name();
        System.out.println("转换枚举到数据库: " + attribute + " -> " + result);
        return result;
    }

    @Override
    public OrderStatus convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            return OrderStatus.valueOf(dbData);
        } catch (IllegalArgumentException e) {
            System.err.println("无法将数据库值转换为枚举: " + dbData);
            return null;
        }
    }
}
