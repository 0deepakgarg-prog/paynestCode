package com.paynest.payments.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentHistoryResponse {

    private long totalRecords;
    private List<PaymentHistoryTransactionResponse> transactions;
    private String traceId;
    private LocalDateTime responseTimestamp;
}
