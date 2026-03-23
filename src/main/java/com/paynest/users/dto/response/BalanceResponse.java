package com.paynest.users.dto.response;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

@Data
@RequiredArgsConstructor
public class BalanceResponse {

    private String walletType;
    private String currency;
    private BigDecimal availableBalance;
    private BigDecimal frozenBalance;
    private BigDecimal ficBalance;

    public BalanceResponse(String walletType, String currency, BigDecimal availableBalance, BigDecimal frozenBalance, BigDecimal ficBalance) {
        this.walletType = walletType;
        this.currency = currency;
        this.availableBalance = availableBalance;
        this.frozenBalance = frozenBalance;
        this.ficBalance = ficBalance;
    }
}


