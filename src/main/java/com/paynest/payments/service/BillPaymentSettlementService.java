package com.paynest.payments.service;


import com.paynest.config.tenant.TenantTime;
import com.paynest.Utilities.IdGenerator;
import com.paynest.common.Constants;
import com.paynest.config.PropertyReader;
import com.paynest.config.security.JWTUtils;
import com.paynest.config.tenant.TraceContext;
import com.paynest.exception.ApplicationException;
import com.paynest.exception.PaymentErrorCode;
import com.paynest.payments.dto.BillPaymentSettlementRequest;
import com.paynest.payments.dto.BillPaymentSettlementResponse;
import com.paynest.payments.entity.BillPaymentStatusRecord;
import com.paynest.payments.entity.TransactionDetails;
import com.paynest.payments.entity.Transactions;
import com.paynest.payments.enums.BillPaymentStatus;
import com.paynest.payments.enums.InitiatedBy;
import com.paynest.payments.enums.TransactionStatus;
import com.paynest.payments.repository.TransactionDetailsRepository;
import com.paynest.payments.repository.TransactionsRepository;
import com.paynest.users.entity.AccountIdentifier;
import com.paynest.users.entity.Wallet;
import com.paynest.users.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class BillPaymentSettlementService {

    private static final String OPERATION_NAME = "BILLPAY_SETTLE";
    private static final String ROLLBACK_PREFIX = "RB";
    private static final String ROLLBACK_SERVICE_CODE = "BILLPAY_RB";
    private static final String ROLLBACK_ATTR1_NAME = "transaction id";
    private static final String ROLLBACK_ATTR2_NAME = "settlement status";

    private final BillPaymentStatusService billPaymentStatusService;
    private final TransactionsRepository transactionsRepository;
    private final TransactionDetailsRepository transactionDetailsRepository;
    private final WalletRepository walletRepository;
    private final PropertyReader propertyReader;
    private final PaymentTransactionRecorderService paymentTransactionRecorderService;
    private final BalanceService balanceService;

    public BillPaymentSettlementResponse settle(BillPaymentSettlementRequest request) {
        validateRequest(request);

        Transactions transaction = transactionsRepository.findFirstByTraceId(request.getTraceId())
                .orElseThrow(() -> new ApplicationException(
                        PaymentErrorCode.INVALID_BILL_PAYMENT_TRANSACTION,
                        null,
                        Map.of("traceId", request.getTraceId())
                ));

        BillPaymentStatusRecord billPaymentStatusRecord =
                billPaymentStatusService.getPendingRecord(transaction.getTransactionId());

        validateBillTransaction(transaction);

        if (Boolean.TRUE.equals(request.getSettlementStatus())) {
            billPaymentStatusService.markSuccess(
                    billPaymentStatusRecord,
                    getCurrentActorAccountId(),
                    request.getComments(),
                    request.getAdditionalInfo()
            );
            paymentTransactionRecorderService.updateTransactionAdditionalInfo(
                    transaction.getTransactionId(),
                    request.getAdditionalInfo()
            );
            touchSettledTransaction(transaction);
            return buildSettlementResponse(transaction.getTransactionId(), BillPaymentStatus.SUCCESS, null);
        }

        return rollbackBillPayment(transaction, billPaymentStatusRecord, request);
    }

    private BillPaymentSettlementResponse rollbackBillPayment(
            Transactions transaction,
            BillPaymentStatusRecord billPaymentStatusRecord,
            BillPaymentSettlementRequest request
    ) {
        List<TransactionDetails> transactionDetails = transactionDetailsRepository
                .findByIdTransactionId(transaction.getTransactionId());
        TransactionDetails debitDetail = getRequiredDetail(transactionDetails, Constants.TXN_TYPE_DR);
        TransactionDetails creditDetail = getRequiredDetail(transactionDetails, Constants.TXN_TYPE_CR);

        Wallet customerWallet = getRequiredWallet(debitDetail);
        Wallet billerWallet = getRequiredWallet(creditDetail);

        String rollbackTransactionId = IdGenerator.generateTransactionId(
                ROLLBACK_PREFIX,
                getRequiredServerInstance()
        );

        BigDecimal rollbackAmount = toRequestAmount(transaction.getTransactionValue());

        paymentTransactionRecorderService.recordTransaction(
                rollbackTransactionId,
                rollbackAmount,
                transaction.getRequestGateway(),
                ROLLBACK_SERVICE_CODE,
                transaction.getLanguage(),
                buildIdentifier(
                        transaction.getCreditorAccountId(),
                        transaction.getCreditorIdentifierType(),
                        transaction.getCreditorIdentifierValue()
                ),
                buildIdentifier(
                        transaction.getDebitorAccountId(),
                        transaction.getDebitorIdentifierType(),
                        transaction.getDebitorIdentifierValue()
                ),
                creditDetail.getUserType(),
                debitDetail.getUserType(),
                billerWallet,
                customerWallet,
                InitiatedBy.DEBITOR,
                null,
                copyAdditionalInfo(request.getAdditionalInfo()),
                transaction.getPaymentReference(),
                request.getComments()
        );

        try {
            balanceService.transferWalletAmount(
                    billerWallet,
                    customerWallet,
                    rollbackAmount,
                    ROLLBACK_SERVICE_CODE,
                    InitiatedBy.DEBITOR,
                    rollbackTransactionId
            );
        } catch (ApplicationException ex) {
            throw ex.withTransactionId(rollbackTransactionId);
        }

        billPaymentStatusService.markFailed(
                billPaymentStatusRecord,
                getCurrentActorAccountId(),
                request.getComments(),
                request.getAdditionalInfo(),
                rollbackTransactionId
        );

        paymentTransactionRecorderService.updateTransactionAdditionalInfo(
                transaction.getTransactionId(),
                request.getAdditionalInfo()
        );
        Transactions rollbackTransaction = updateRollbackTransactionAttributes(
                rollbackTransactionId,
                transaction.getTransactionId(),
                request.getSettlementStatus()
        );
        markOriginalTransactionReconciled(transaction, rollbackTransaction.getTransferStatus());

        return buildSettlementResponse(
                transaction.getTransactionId(),
                BillPaymentStatus.FAILED,
                rollbackTransactionId
        );
    }

    private void validateRequest(BillPaymentSettlementRequest request) {
        if (request == null || request.getTraceId() == null || request.getTraceId().isBlank()) {
            throw new ApplicationException(PaymentErrorCode.BILL_PAYMENT_NOT_FOUND);
        }

        if (request.getSettlementStatus() == null) {
            throw new ApplicationException(PaymentErrorCode.BILL_PAYMENT_STATUS_MISSING);
        }

        if (request.getComments() != null) {
            String normalizedComments = request.getComments().trim();
            if (normalizedComments.length() > 300) {
                throw new ApplicationException(PaymentErrorCode.COMMENTS_TOO_LONG);
            }
            request.setComments(normalizedComments.isEmpty() ? null : normalizedComments);
        }

        request.setTraceId(request.getTraceId().trim());
    }

    private void validateBillTransaction(Transactions transaction) {
        if (!"BILLPAY".equalsIgnoreCase(transaction.getServiceCode())
                || !Constants.TRANSACTION_SUCCESS.equalsIgnoreCase(transaction.getTransferStatus())) {
            throw new ApplicationException(
                    PaymentErrorCode.INVALID_BILL_PAYMENT_TRANSACTION,
                    null,
                    Map.of(
                            "transactionId", transaction.getTransactionId(),
                            "serviceCode", transaction.getServiceCode(),
                            "transferStatus", transaction.getTransferStatus()
                    )
            );
        }
    }

    private TransactionDetails getRequiredDetail(List<TransactionDetails> details, String entryType) {
        return details.stream()
                .filter(detail -> entryType.equalsIgnoreCase(detail.getEntryType()))
                .findFirst()
                .orElseThrow(() -> new ApplicationException(
                        PaymentErrorCode.INVALID_TRANSACTION_DETAILS,
                        null,
                        Map.of("entryType", entryType)
                ));
    }

    private Wallet getRequiredWallet(TransactionDetails transactionDetail) {
        Long walletId;
        try {
            walletId = Long.parseLong(transactionDetail.getWalletNumber());
        } catch (NumberFormatException ex) {
            throw new ApplicationException(
                    PaymentErrorCode.INVALID_TRANSACTION_DETAILS,
                    null,
                    Map.of("walletId", transactionDetail.getWalletNumber())
            );
        }

        return walletRepository.findById(walletId)
                .orElseThrow(() -> new ApplicationException(
                        PaymentErrorCode.INVALID_TRANSACTION_DETAILS,
                        null,
                        Map.of("walletId", transactionDetail.getWalletNumber())
                ));
    }

    private AccountIdentifier buildIdentifier(String accountId, String identifierType, String identifierValue) {
        AccountIdentifier identifier = new AccountIdentifier();
        identifier.setAccountId(accountId);
        identifier.setIdentifierType(normalizeIdentifierType(identifierType, "ACCOUNT_ID"));
        identifier.setIdentifierValue(normalizeIdentifierValue(identifierValue, accountId));
        identifier.setStatus(Constants.ACCOUNT_STATUS_ACTIVE);
        return identifier;
    }

    private Map<String, Object> copyAdditionalInfo(Map<String, Object> requestAdditionalInfo) {
        if (requestAdditionalInfo == null || requestAdditionalInfo.isEmpty()) {
            return null;
        }
        return Map.copyOf(requestAdditionalInfo);
    }

    private void touchSettledTransaction(Transactions transaction) {
        LocalDateTime now = TenantTime.now();
        transaction.setTransferOn(now);
        transaction.setModifiedOn(now);
        transactionsRepository.save(transaction);
    }

    private void markOriginalTransactionReconciled(
            Transactions transaction,
            String reconciliationStatus
    ) {
        LocalDateTime now = TenantTime.now();
        transaction.setTransferOn(now);
        transaction.setModifiedOn(now);
        transaction.setReconciliationDone(resolveReconciliationStatus(reconciliationStatus));
        transaction.setReconciliationDate(now);
        transaction.setReconciliationBy(transaction.getTransactionId());
        transactionsRepository.save(transaction);
    }

    private Transactions updateRollbackTransactionAttributes(
            String rollbackTransactionId,
            String originalTransactionId,
            Boolean settlementStatus
    ) {
        Transactions rollbackTransaction = transactionsRepository.findByTransactionId(rollbackTransactionId);
        if (rollbackTransaction == null) {
            throw new IllegalStateException("Rollback transaction not found: " + rollbackTransactionId);
        }

        LocalDateTime now = TenantTime.now();
        rollbackTransaction.setAttr1Name(ROLLBACK_ATTR1_NAME);
        rollbackTransaction.setAttr1Value(originalTransactionId);
        rollbackTransaction.setAttr2Name(ROLLBACK_ATTR2_NAME);
        rollbackTransaction.setAttr2Value(String.valueOf(settlementStatus));
        rollbackTransaction.setModifiedOn(now);
        transactionsRepository.save(rollbackTransaction);
        return rollbackTransaction;
    }

    private BigDecimal toRequestAmount(BigDecimal storedAmount) {
        BigDecimal currencyFactor = new BigDecimal(propertyReader.getPropertyValue("currency.factor"));
        return storedAmount.divide(currencyFactor, 2, RoundingMode.HALF_UP);
    }

    private String getRequiredServerInstance() {
        String serverInstance = propertyReader.getPropertyValue("server.instance");
        if (serverInstance == null || serverInstance.isBlank()) {
            throw new IllegalStateException("server.instance is not configured");
        }
        return serverInstance.trim();
    }

    private String getCurrentActorAccountId() {
        try {
            return JWTUtils.getCurrentAccountId();
        } catch (Exception ex) {
            return "SYSTEM";
        }
    }

    private String resolveReconciliationStatus(String rollbackTransferStatus) {
        if (rollbackTransferStatus == null || rollbackTransferStatus.isBlank()) {
            return Constants.TRANSACTION_SUCCESS;
        }
        return rollbackTransferStatus.trim();
    }

    private BillPaymentSettlementResponse buildSettlementResponse(
            String transactionId,
            BillPaymentStatus billPaymentStatus,
            String rollbackTransactionId
    ) {
        boolean success = billPaymentStatus == BillPaymentStatus.SUCCESS;
        return BillPaymentSettlementResponse.builder()
                .responseStatus(TransactionStatus.SUCCESS)
                .operationType(OPERATION_NAME)
                .code(success
                        ? "BILL_SETTLEMENT_SUCCESS"
                        : "BILL_SETTLEMENT_FAILED_ROLLED_BACK")
                .message(success
                        ? "Bill payment settled successfully"
                        : "Bill payment marked failed and rollback completed")
                .timestamp(TenantTime.instant())
                .traceId(TraceContext.getTraceId())
                .transactionId(transactionId)
                .rollbackTransactionId(rollbackTransactionId)
                .billStatus(billPaymentStatus)
                .build();
    }

    private String normalizeIdentifierType(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeIdentifierValue(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
