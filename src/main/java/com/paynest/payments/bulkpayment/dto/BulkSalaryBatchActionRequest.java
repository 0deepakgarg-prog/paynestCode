package com.paynest.payments.bulkpayment.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BulkSalaryBatchActionRequest {

    @Size(max = 30)
    private String performedBy;

    @Size(max = 500)
    private String remarks;
}
