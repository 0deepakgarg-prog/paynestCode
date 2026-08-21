package com.paynest.limits.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateTransactionLimitStatusRequest {

    @NotBlank(message = "status is required")
    @Size(max = 20, message = "status must not exceed 20 characters")
    private String status;
}
