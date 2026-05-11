package com.paynest.payments.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class O2CApprovalRequest {

    @NotBlank
    private String transactionId;

    @NotBlank
    private String status;

    @Size(max = 300)
    private String comments;
}
