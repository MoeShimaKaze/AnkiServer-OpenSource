package com.server.anki.mailorder.enums;

import lombok.Getter;

@Getter
public enum DeliveryService {
    STANDARD(1),
    EXPRESS(2);

    private final int serviceCode;

    DeliveryService(int serviceCode) {
        this.serviceCode = serviceCode;
    }

    public static DeliveryService fromCode(int code) {
        for (DeliveryService service : DeliveryService.values()) {
            if (service.getServiceCode() == code) {
                return service;
            }
        }
        throw new IllegalArgumentException("Invalid service code: " + code);
    }
}
