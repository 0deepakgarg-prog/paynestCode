package com.paynest.payments.bulkpayment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BulkSalaryBatchRefundRequest {

    @NotBlank
    @Size(max = 30)
    private String enterpriseAccountId;

    @NotBlank
    @Size(max = 50)
    private String enterpriseWalletType;

    @Size(max = 30)
    private String performedBy;

    @Size(max = 500)
    private String remarks;
}
