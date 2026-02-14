package com.paynest.dto;

import java.math.BigDecimal;

public class BalanceResponse {

    private Long walletId;
    private String currency;
    private BigDecimal availableBalance;
    private BigDecimal frozenBalance;
    private BigDecimal ficBalance;

    public BalanceResponse(Long walletId,
                           String currency,
                           BigDecimal availableBalance,
                           BigDecimal frozenBalance,
                           BigDecimal ficBalance) {
        this.walletId = walletId;
        this.currency = currency;
        this.availableBalance = availableBalance;
        this.frozenBalance = frozenBalance;
        this.ficBalance = ficBalance;
    }

    // getters

    public Long getWalletId() {
        return walletId;
    }

    public void setWalletId(Long walletId) {
        this.walletId = walletId;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }

    public void setAvailableBalance(BigDecimal availableBalance) {
        this.availableBalance = availableBalance;
    }

    public BigDecimal getFrozenBalance() {
        return frozenBalance;
    }

    public void setFrozenBalance(BigDecimal frozenBalance) {
        this.frozenBalance = frozenBalance;
    }

    public BigDecimal getFicBalance() {
        return ficBalance;
    }

    public void setFicBalance(BigDecimal ficBalance) {
        this.ficBalance = ficBalance;
    }
}

