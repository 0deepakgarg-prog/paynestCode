package com.paynest.payments.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.paynest.payments.enums.PasscodeStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Builder
public class PasscodeDetailsResponse {
    private String passcode;
    private PasscodeStatus status;
    private String transactionId;
    private String cashoutTransactionId;
    private BigDecimal amount;
    private String currency;
    private PasscodePartyDetails sender;
    private PasscodePartyDetails receiver;
    private LocalDateTime createdOn;
    private LocalDateTime modifiedOn;
    private LocalDateTime redeemedOn;
}
