package com.server.anki.wallet;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Setter
@Getter
public class WalletInfoDTO {
    private BigDecimal totalBalance;
    private BigDecimal availableBalance;
    private BigDecimal pendingBalance;
    private Long userId;
    private String username;

    public WalletInfoDTO(BigDecimal totalBalance, BigDecimal availableBalance, BigDecimal pendingBalance, Long userId, String username) {
        this.totalBalance = totalBalance;
        this.availableBalance = availableBalance;
        this.pendingBalance = pendingBalance;
        this.userId = userId;
        this.username = username;
    }
}