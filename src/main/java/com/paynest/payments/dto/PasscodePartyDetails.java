package com.paynest.payments.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Builder
public class PasscodePartyDetails {
    private String accountId;
    private String accountType;
    private String msisdn;
    private String firstName;
    private String lastName;
    private String kycDocumentId;
}
