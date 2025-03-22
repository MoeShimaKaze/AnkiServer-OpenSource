package com.server.anki.wallet.request;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 转账请求DTO
 */
@Getter
@Setter
public class TransferRequest {
    // Getters and Setters
    private Long toUserId;
    private BigDecimal amount;
    private String reason;

}
