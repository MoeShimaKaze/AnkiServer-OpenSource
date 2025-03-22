package com.server.anki.mailorder.dto;

import com.server.anki.mailorder.enums.OrderStatus;
import lombok.Getter;
import lombok.Setter;

public class MailOrderUpdateDTO {
    // Getters and setters
    @Setter
    @Getter
    private String name;
    @Setter
    @Getter
    private String contactInfo;
    // 取件地址相关字段
    @Setter
    @Getter
    private String pickupAddress;
    @Setter
    @Getter
    private Double pickupLatitude;
    @Setter
    @Getter
    private Double pickupLongitude;
    @Setter
    @Getter
    private String pickupDetail;

    // 配送地址相关字段
    @Setter
    @Getter
    private String deliveryAddress;
    @Setter
    @Getter
    private Double deliveryLatitude;
    @Setter
    @Getter
    private Double deliveryLongitude;
    @Setter
    @Getter
    private String deliveryDetail;
    @Setter
    @Getter
    private double weight;
    private boolean isLargeItem;
    @Setter
    @Getter
    private double fee;
    @Setter
    @Getter
    private OrderStatus orderStatus;

    @Setter
    @Getter
    private Double deliveryDistance;

    @Setter
    @Getter
    private double regionMultiplier = 1.0;

    @Setter
    @Getter
    private double userIncome;

    @Setter
    @Getter
    private double platformIncome;

    public boolean isLargeItem() {
        return isLargeItem;
    }

    public void setLargeItem(boolean largeItem) {
        isLargeItem = largeItem;
    }

}