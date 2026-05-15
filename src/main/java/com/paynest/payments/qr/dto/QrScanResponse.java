package com.paynest.payments.qr.dto;

import com.paynest.enums.AccountType;
import com.paynest.payments.qr.enums.QrType;
import com.paynest.users.enums.IdentifierType;
import com.paynest.users.enums.WalletType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class QrScanResponse {
    private QrType qrType;
    private String qrIntentId;
    private String operationType;
    private IdentifierType creditorIdentifierType;
    private String creditorIdentifierValue;
    private AccountType creditorAccountType;
    private WalletType creditorWalletType;
    private String currency;
    private BigDecimal amount;
    private LocalDateTime expiresAt;
}
