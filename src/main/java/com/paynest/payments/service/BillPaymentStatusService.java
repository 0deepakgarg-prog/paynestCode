package com.paynest.payments.service;

import com.paynest.exception.ApplicationException;
import com.paynest.exception.PaymentErrorCode;
import com.paynest.payments.entity.BillPaymentStatusRecord;
import com.paynest.payments.enums.BillPaymentStatus;
import com.paynest.payments.repository.BillPaymentStatusRepository;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BillPaymentStatusService {

    private final BillPaymentStatusRepository billPaymentStatusRepository;

    public void createPendingStatus(
            String transactionId,
            String traceId,
            String customerAccountId,
            String billerAccountId
    ) {
        LocalDateTime now = LocalDateTime.now();
        BillPaymentStatusRecord record = new BillPaymentStatusRecord();
        record.setTransactionId(transactionId);
        record.setTraceId(traceId);
        record.setCustomerAccountId(customerAccountId);
        record.setBillerAccountId(billerAccountId);
        record.setStatus(BillPaymentStatus.PENDING);
        record.setCreatedOn(now);
        record.setModifiedOn(now);
        billPaymentStatusRepository.save(record);
    }

    public BillPaymentStatusRecord getRequiredRecord(String transactionId) {
        return billPaymentStatusRepository.findById(transactionId)
                .orElseThrow(() -> new ApplicationException(
                        PaymentErrorCode.BILL_PAYMENT_NOT_FOUND,
                        null,
                        Map.of("transactionId", transactionId)
                ));
    }

    public BillPaymentStatusRecord getPendingRecord(String transactionId) {
        BillPaymentStatusRecord record = getRequiredRecord(transactionId);
        if (record.getStatus() != BillPaymentStatus.PENDING) {
            throw new ApplicationException(
                    PaymentErrorCode.BILL_PAYMENT_ALREADY_SETTLED,
                    null,
                    Map.of(
                            "transactionId", transactionId,
                            "status", record.getStatus().name()
                    )
            );
        }
        return record;
    }

    public void markSuccess(
            BillPaymentStatusRecord record,
            String settledBy,
            String comments,
            Map<String, Object> additionalInfo
    ) {
        updateRecord(record, BillPaymentStatus.SUCCESS, settledBy, comments, additionalInfo, null);
    }

    public void markFailed(
            BillPaymentStatusRecord record,
            String settledBy,
            String comments,
            Map<String, Object> additionalInfo,
            String rollbackTransactionId
    ) {
        updateRecord(record, BillPaymentStatus.FAILED, settledBy, comments, additionalInfo, rollbackTransactionId);
    }

    private void updateRecord(
            BillPaymentStatusRecord record,
            BillPaymentStatus status,
            String settledBy,
            String comments,
            Map<String, Object> additionalInfo,
            String rollbackTransactionId
    ) {
        LocalDateTime now = LocalDateTime.now();
        record.setStatus(status);
        record.setSettledBy(normalizeOptionalText(settledBy));
        record.setSettledOn(now);
        record.setComments(normalizeOptionalText(comments));
        record.setAdditionalInfo(mergeAdditionalInfo(record.getAdditionalInfo(), additionalInfo));
        record.setRollbackTransactionId(rollbackTransactionId);
        record.setModifiedOn(now);
        billPaymentStatusRepository.save(record);
    }

    private String mergeAdditionalInfo(String existingValue, Map<String, Object> additionalInfo) {
        if ((existingValue == null || existingValue.isBlank())
                && (additionalInfo == null || additionalInfo.isEmpty())) {
            return null;
        }

        JSONObject merged = existingValue == null || existingValue.isBlank()
                ? new JSONObject()
                : new JSONObject(existingValue);

        if (additionalInfo != null && !additionalInfo.isEmpty()) {
            for (Map.Entry<String, Object> entry : additionalInfo.entrySet()) {
                merged.put(entry.getKey(), entry.getValue());
            }
        }

        return merged.toString();
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
