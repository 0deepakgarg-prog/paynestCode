package com.paynest.payments.bulkpayment.service;

import com.paynest.Utilities.IdGenerator;
import com.paynest.common.Constants;
import com.paynest.config.PropertyReader;
import com.paynest.config.repository.EnumerationRepository;
import com.paynest.config.tenant.TenantTime;
import com.paynest.enums.RequestGateway;
import com.paynest.exception.ApplicationException;
import com.paynest.exception.PaymentErrorCode;
import com.paynest.payments.bulkpayment.dto.BulkPaymentInternalTransferRequest;
import com.paynest.payments.bulkpayment.dto.BulkSalaryBatchActionRequest;
import com.paynest.payments.bulkpayment.dto.BulkSalaryBatchRefundRequest;
import com.paynest.payments.bulkpayment.dto.BulkSalaryBatchSummaryResponse;
import com.paynest.payments.bulkpayment.dto.BulkSalaryPaymentEntryRequest;
import com.paynest.payments.bulkpayment.dto.BulkSalaryPaymentRequest;
import com.paynest.payments.bulkpayment.dto.SalaryPaymentInternalRequest;
import com.paynest.payments.bulkpayment.entity.Batch;
import com.paynest.payments.bulkpayment.entity.BatchDetail;
import com.paynest.payments.bulkpayment.enums.BatchDetailStatus;
import com.paynest.payments.bulkpayment.enums.BatchStatus;
import com.paynest.payments.bulkpayment.repository.BatchDetailRepository;
import com.paynest.payments.bulkpayment.repository.BatchRepository;
import com.paynest.payments.dto.BasePaymentResponse;
import com.paynest.users.entity.Account;
import com.paynest.users.entity.AccountIdentifier;
import com.paynest.users.entity.Wallet;
import com.paynest.users.entity.WalletBalance;
import com.paynest.users.repository.AccountIdentifierRepository;
import com.paynest.users.repository.AccountRepository;
import com.paynest.users.repository.WalletBalanceRepository;
import com.paynest.users.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
@RequiredArgsConstructor
@Slf4j
public class BulkSalaryBatchService {

    private static final int MAX_BATCH_ENTRIES = 1000;
    private static final String BATCH_TYPE_SALARY = "SALARY";
    private static final String BATCH_ID_PREFIX = "BS";
    private static final String SYSTEM_CONFIG = "SYSTEM_CONFIG";
    private static final String VALIDATION_EXECUTOR_COUNT = "BULK_PAYMENT_VALIDATION_EXECUTOR_COUNT";
    private static final String PROCESSING_EXECUTOR_COUNT = "BULK_PAYMENT_PROCESSING_EXECUTOR_COUNT";
    private static final String EXECUTOR_DELAY_MS = "BULK_PAYMENT_EXECUTOR_DELAY_MS";
    private static final String EXECUTION_WINDOW_ENABLED = "BULK_PAYMENT_EXECUTION_WINDOW_ENABLED";
    private static final String EXECUTION_WINDOW_START = "BULK_PAYMENT_EXECUTION_WINDOW_START";
    private static final String EXECUTION_WINDOW_END = "BULK_PAYMENT_EXECUTION_WINDOW_END";
    private static final int DEFAULT_VALIDATION_EXECUTOR_COUNT = 3;
    private static final int DEFAULT_PROCESSING_EXECUTOR_COUNT = 3;
    private static final long DEFAULT_EXECUTOR_DELAY_MS = 1000L;
    private static final String DEFAULT_EXECUTION_WINDOW_START = "20:00";
    private static final String DEFAULT_EXECUTION_WINDOW_END = "06:00";

    private final BatchRepository batchRepository;
    private final BatchDetailRepository batchDetailRepository;
    private final AccountRepository accountRepository;
    private final AccountIdentifierRepository accountIdentifierRepository;
    private final WalletRepository walletRepository;
    private final WalletBalanceRepository walletBalanceRepository;
    private final EnumerationRepository enumerationRepository;
    private final PropertyReader propertyReader;
    private final BulkPaymentInternalPaymentService internalPaymentService;

    @Transactional
    public BulkSalaryBatchSummaryResponse createBatch(BulkSalaryPaymentRequest request) {
        validateCreateRequest(request);
        boolean queueValidation = hasActiveBatch();

        Batch batch = new Batch();
        batch.setBatchId(generateBatchId());
        batch.setBatchReference(blankToNull(request.getBatchReference()));
        batch.setBatchType(normalize(defaultIfBlank(request.getBatchType(), BATCH_TYPE_SALARY)));
        batch.setStatus(queueValidation ? BatchStatus.VALIDATION_INITIATED : BatchStatus.VALIDATION_IN_PROGRESS);
        batch.setTotalRecords(request.getPayments().size());
        batch.setValidRecords(0);
        batch.setFailedRecords(0);
        batch.setTotalAmount(request.getTotalAmount());
        batch.setCurrency(normalize(request.getCurrency()));
        batch.setDebitorAccountId(request.getDebitorAccountId());
        batch.setDebitorWalletType(normalize(request.getDebitorWalletType()));
        batch.setDebitorCurrency(normalize(request.getDebitorCurrency()));
        batch.setCreatedBy(request.getDebitorAccountId());
        batch.setModifiedBy(request.getDebitorAccountId());
        if (!queueValidation) {
            batch.setValidationStartedOn(TenantTime.now());
        }
        batch.setRemarks(request.getRemarks());
        batch.setAdditionalInfo(request.getAdditionalInfo());
        batchRepository.save(batch);

        List<BatchDetail> details = request.getPayments().stream()
                .map(entry -> toBatchDetail(batch.getBatchId(), entry))
                .toList();
        batchDetailRepository.saveAll(details);

        if (queueValidation) {
            return summary(
                    batchRepository.findById(batch.getBatchId()).orElse(batch),
                    "Batch created and queued for validation"
            );
        }

        runValidation(batch.getBatchId());
        return summary(batchRepository.findById(batch.getBatchId()).orElse(batch), "Batch created and validation completed");
    }

    @Transactional
    public BulkSalaryBatchSummaryResponse startValidation(String batchId) {
        Batch batch = getBatchForUpdate(batchId);
        if (batch.getStatus() != BatchStatus.VALIDATION_INITIATED) {
            throw invalidState("Batch must be VALIDATION_INITIATED before validation can start", batch);
        }
        if (hasActiveBatch()) {
            return summary(batch, "Batch remains queued because another batch is running");
        }

        batch.setStatus(BatchStatus.VALIDATION_IN_PROGRESS);
        batch.setValidationStartedOn(TenantTime.now());
        batch.setModifiedBy(batch.getCreatedBy());
        batchRepository.save(batch);
        runValidation(batchId);
        return summary(batchRepository.findById(batchId).orElse(batch), "Batch validation completed");
    }

    public BulkSalaryBatchSummaryResponse approveBatch(String batchId, BulkSalaryBatchActionRequest request) {
        Batch batch = getBatchForUpdate(batchId);
        if (batch.getStatus() != BatchStatus.PENDING_APPROVAL) {
            throw invalidState("Batch must be PENDING_APPROVAL before approval", batch);
        }

        BigDecimal validatedAmount = batchDetailRepository.sumAmountByBatchIdAndStatus(
                batchId,
                BatchDetailStatus.VALIDATED
        );
        if (validatedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApplicationException("BULK_BATCH_NO_VALID_ENTRIES", "No validated entries available for prefunding");
        }

        BasePaymentResponse prefundResponse = internalPaymentService.prefund(prefundRequest(batch, validatedAmount));

        batch.setStatus(BatchStatus.APPROVED);
        batch.setTransactionId(prefundResponse.getTransactionId());
        batch.setApprovedBy(actor(request));
        batch.setApprovedOn(TenantTime.now());
        batch.setRemarks(mergeRemarks(batch.getRemarks(), request != null ? request.getRemarks() : null));
        batch.setModifiedBy(actor(request));
        batchRepository.save(batch);

        if (!isExecutionWindowOpen()) {
            return summary(batch, "Batch approved and prefunded. Execution is waiting for the configured window");
        }
        if (hasActiveBatch()) {
            return summary(batch, "Batch approved and prefunded. Execution is waiting because another batch is running");
        }

        return processBatch(batchId);
    }

    @Transactional
    public BulkSalaryBatchSummaryResponse rejectBatch(String batchId, BulkSalaryBatchActionRequest request) {
        Batch batch = getBatchForUpdate(batchId);
        if (batch.getStatus() != BatchStatus.PENDING_APPROVAL && batch.getStatus() != BatchStatus.APPROVED) {
            throw invalidState("Batch must be PENDING_APPROVAL or APPROVED before rejection", batch);
        }

        batch.setStatus(BatchStatus.REJECTED);
        batch.setRejectedBy(actor(request));
        batch.setRejectedOn(TenantTime.now());
        batch.setRemarks(mergeRemarks(batch.getRemarks(), request != null ? request.getRemarks() : null));
        batch.setModifiedBy(actor(request));
        batchRepository.save(batch);
        return summary(batch, "Batch rejected");
    }

    public BulkSalaryBatchSummaryResponse processBatch(String batchId) {
        ensureExecutionWindowOpen();
        ensureNoOtherActiveBatch(batchId);
        Batch batch = markBatchProcessing(batchId);
        if (batch.getTransactionId() == null || batch.getTransactionId().isBlank()) {
            throw new ApplicationException("BULK_BATCH_NOT_PREFUNDED", "Batch must be prefunded during approval before processing");
        }

        runProcessing(batchId, BatchDetailStatus.VALIDATED);
        Batch refreshed = finalizeBatch(batchId);
        return summary(refreshed, "Batch processing completed");
    }

    public void processNextApprovedBatchIfPossible() {
        if (!isExecutionWindowOpen() || hasActiveBatch()) {
            return;
        }

        batchRepository.findFirstByStatusOrderByCreatedOnAsc(BatchStatus.APPROVED)
                .ifPresent(batch -> {
                    try {
                        processBatch(batch.getBatchId());
                    } catch (ApplicationException ex) {
                        log.warn(
                                "Bulk salary scheduled processing skipped. batchId={}, code={}, message={}",
                                batch.getBatchId(),
                                ex.getErrorCode(),
                                ex.getMessage()
                        );
                    }
                });
    }

    public BulkSalaryBatchSummaryResponse retryFailed(String batchId) {
        ensureExecutionWindowOpen();
        Batch batch = getBatch(batchId);
        if (batch.getStatus() != BatchStatus.PARTIAL_SUCCESS && batch.getStatus() != BatchStatus.FAILED) {
            throw invalidState("Batch must be PARTIAL_SUCCESS or FAILED before retry", batch);
        }

        batchDetailRepository.findByBatchIdAndStatus(batchId, BatchDetailStatus.FAILED)
                .forEach(detail -> {
                    detail.setStatus(BatchDetailStatus.VALIDATED);
                    detail.setProcessingErrorCode(null);
                    detail.setProcessingErrorMessage(null);
                    batchDetailRepository.save(detail);
                });

        ensureNoOtherActiveBatch(batchId);
        markBatchProcessing(batchId);
        runProcessing(batchId, BatchDetailStatus.VALIDATED);
        Batch refreshed = finalizeBatch(batchId);
        return summary(refreshed, "Failed entries retried");
    }

    public BulkSalaryBatchSummaryResponse refundFailed(String batchId, BulkSalaryBatchRefundRequest request) {
        Batch batch = getBatch(batchId);
        BigDecimal refundAmount = batchDetailRepository.sumAmountByBatchIdAndStatus(batchId, BatchDetailStatus.FAILED);
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApplicationException("BULK_BATCH_NO_FAILED_AMOUNT", "No failed amount available for refund");
        }

        BasePaymentResponse refundResponse = internalPaymentService.refund(refundRequest(batch, request, refundAmount));
        batchDetailRepository.findByBatchIdAndStatus(batchId, BatchDetailStatus.FAILED)
                .forEach(detail -> {
                    detail.setStatus(BatchDetailStatus.SKIPPED);
                    detail.setProcessingErrorCode(null);
                    detail.setProcessingErrorMessage("Refunded through transaction " + refundResponse.getTransactionId());
                    batchDetailRepository.save(detail);
                });

        Batch locked = getBatchForUpdate(batchId);
        locked.setStatus(BatchStatus.REFUNDED);
        locked.setModifiedBy(request.getPerformedBy());
        locked.setProcessingCompletedOn(TenantTime.now());
        locked.setRemarks(mergeRemarks(locked.getRemarks(), request.getRemarks()));
        batchRepository.save(locked);
        return summary(locked, "Failed entries refunded");
    }

    public BulkSalaryBatchSummaryResponse getBatchSummary(String batchId) {
        return summary(getBatch(batchId), "Batch fetched");
    }

    private void validateCreateRequest(BulkSalaryPaymentRequest request) {
        if (request.getPayments().size() > MAX_BATCH_ENTRIES) {
            throw new ApplicationException("BULK_BATCH_TOO_LARGE", "Batch entries must not exceed " + MAX_BATCH_ENTRIES);
        }
        if (request.getBatchReference() != null && batchRepository.existsByBatchReference(request.getBatchReference())) {
            throw new ApplicationException("BULK_BATCH_REFERENCE_EXISTS", "Batch reference already exists");
        }
        if (!normalize(request.getCurrency()).equals(normalize(request.getDebitorCurrency()))) {
            throw new ApplicationException("BULK_BATCH_CURRENCY_MISMATCH", "Batch currency must match debitor currency");
        }

        Set<String> itemReferences = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (BulkSalaryPaymentEntryRequest entry : request.getPayments()) {
            if (!normalize(request.getCurrency()).equals(normalize(entry.getCurrency()))
                    || !normalize(request.getCurrency()).equals(normalize(entry.getCreditorCurrency()))) {
                throw new ApplicationException("BULK_BATCH_CURRENCY_MISMATCH", "All entry currencies must match batch currency");
            }
            if (entry.getItemReference() != null && !itemReferences.add(entry.getItemReference())) {
                throw new ApplicationException("BULK_BATCH_DUPLICATE_ITEM_REFERENCE", "Duplicate item reference in batch");
            }
            total = total.add(entry.getAmount());
        }
        if (total.compareTo(request.getTotalAmount()) != 0) {
            throw new ApplicationException("BULK_BATCH_TOTAL_MISMATCH", "Total amount does not match payment entries");
        }

        Account enterprise = getActiveAccount(request.getDebitorAccountId());
        Wallet enterpriseWallet = getWallet(
                enterprise.getAccountId(),
                request.getCurrency(),
                request.getDebitorWalletType()
        );
        validateWalletUsable(enterpriseWallet, "Debitor wallet is not active or is locked");
        validateSufficientBalance(enterpriseWallet, request.getTotalAmount());
    }

    private void runValidation(String batchId) {
        List<BatchDetail> details = batchDetailRepository.findByBatchId(batchId);
        runWorkers(details, configInt(VALIDATION_EXECUTOR_COUNT, DEFAULT_VALIDATION_EXECUTOR_COUNT), this::validateDetail);
        updateValidationResult(batchId);
    }

    private void validateDetail(BatchDetail detail) {
        try {
            sleepBetweenExecutorRuns();
            detail.setStatus(BatchDetailStatus.VALIDATION_IN_PROGRESS);
            batchDetailRepository.save(detail);

            AccountIdentifier identifier = getIdentifier(
                    detail.getCreditorIdentifierType(),
                    detail.getCreditorIdentifierValue()
            );
            Account account = getActiveAccount(identifier.getAccountId());
            Wallet wallet = getWallet(
                    account.getAccountId(),
                    detail.getCurrency(),
                    detail.getCreditorWalletType()
            );
            validateWalletUsable(wallet, "Creditor wallet is not active or is locked");

            detail.setStatus(BatchDetailStatus.VALIDATED);
            detail.setValidationErrorCode(null);
            detail.setValidationErrorMessage(null);
        } catch (Exception ex) {
            detail.setStatus(BatchDetailStatus.VALIDATION_FAILED);
            detail.setValidationErrorCode(resolveErrorCode(ex));
            detail.setValidationErrorMessage(ex.getMessage());
        }
        batchDetailRepository.save(detail);
    }

    @Transactional
    protected void updateValidationResult(String batchId) {
        Batch batch = getBatchForUpdate(batchId);
        long valid = batchDetailRepository.countByBatchIdAndStatus(batchId, BatchDetailStatus.VALIDATED);
        long failed = batchDetailRepository.countByBatchIdAndStatus(batchId, BatchDetailStatus.VALIDATION_FAILED);
        batch.setValidRecords(Math.toIntExact(valid));
        batch.setFailedRecords(Math.toIntExact(failed));
        batch.setValidationCompletedOn(TenantTime.now());
        batch.setStatus(valid > 0 && failed == 0 ? BatchStatus.PENDING_APPROVAL : BatchStatus.FAILED);
        if (failed > 0) {
            batch.setFailureReason("One or more batch entries failed validation");
        }
        batchRepository.save(batch);
    }

    private void runProcessing(String batchId, BatchDetailStatus pickupStatus) {
        List<BatchDetail> details = batchDetailRepository.findByBatchIdAndStatus(batchId, pickupStatus);
        runWorkers(details, configInt(PROCESSING_EXECUTOR_COUNT, DEFAULT_PROCESSING_EXECUTOR_COUNT), this::processDetail);
    }

    private void processDetail(BatchDetail detail) {
        try {
            sleepBetweenExecutorRuns();
            detail.setStatus(BatchDetailStatus.PROCESSING);
            batchDetailRepository.save(detail);

            BasePaymentResponse response = internalPaymentService.paySalary(salaryPaymentRequest(detail));
            detail.setTransactionId(response.getTransactionId());
            detail.setStatus(BatchDetailStatus.SUCCESS);
            detail.setProcessingErrorCode(null);
            detail.setProcessingErrorMessage(null);
        } catch (Exception ex) {
            detail.setStatus(BatchDetailStatus.FAILED);
            detail.setProcessingErrorCode(resolveErrorCode(ex));
            detail.setProcessingErrorMessage(ex.getMessage());
        }
        batchDetailRepository.save(detail);
    }

    @Transactional
    protected Batch markBatchProcessing(String batchId) {
        Batch batch = getBatchForUpdate(batchId);
        if (batch.getStatus() != BatchStatus.APPROVED
                && batch.getStatus() != BatchStatus.PARTIAL_SUCCESS
                && batch.getStatus() != BatchStatus.FAILED) {
            throw invalidState("Batch must be APPROVED, PARTIAL_SUCCESS, or FAILED before processing", batch);
        }
        batch.setStatus(BatchStatus.PROCESSING);
        batch.setProcessingStartedOn(TenantTime.now());
        batch.setModifiedBy(batch.getApprovedBy() != null ? batch.getApprovedBy() : batch.getCreatedBy());
        return batchRepository.save(batch);
    }

    private boolean hasActiveBatch() {
        return batchRepository.existsByStatusIn(List.of(
                BatchStatus.VALIDATION_IN_PROGRESS,
                BatchStatus.PROCESSING
        ));
    }

    private void ensureNoOtherActiveBatch(String batchId) {
        List<Batch> activeBatches = new ArrayList<>();
        activeBatches.addAll(batchRepository.findByStatus(BatchStatus.VALIDATION_IN_PROGRESS));
        activeBatches.addAll(batchRepository.findByStatus(BatchStatus.PROCESSING));

        activeBatches.stream()
                .filter(batch -> !batch.getBatchId().equals(batchId))
                .findFirst()
                .ifPresent(batch -> {
                    throw new ApplicationException(
                            "BULK_BATCH_ALREADY_RUNNING",
                            "Another batch is already running. batchId=" + batch.getBatchId()
                    );
                });
    }

    @Transactional
    protected Batch finalizeBatch(String batchId) {
        Batch batch = getBatchForUpdate(batchId);
        long success = batchDetailRepository.countByBatchIdAndStatus(batchId, BatchDetailStatus.SUCCESS);
        long failed = batchDetailRepository.countByBatchIdAndStatus(batchId, BatchDetailStatus.FAILED);
        long skipped = batchDetailRepository.countByBatchIdAndStatus(batchId, BatchDetailStatus.SKIPPED);
        batch.setValidRecords(Math.toIntExact(success));
        batch.setFailedRecords(Math.toIntExact(failed + skipped));
        batch.setProcessingCompletedOn(TenantTime.now());
        if (success == batch.getTotalRecords()) {
            batch.setStatus(BatchStatus.SUCCESS);
        } else if (success > 0) {
            batch.setStatus(BatchStatus.PARTIAL_SUCCESS);
        } else {
            batch.setStatus(BatchStatus.FAILED);
        }
        batchRepository.save(batch);
        return batch;
    }

    private BatchDetail toBatchDetail(String batchId, BulkSalaryPaymentEntryRequest entry) {
        BatchDetail detail = new BatchDetail();
        detail.setBatchId(batchId);
        detail.setItemReference(blankToNull(entry.getItemReference()));
        detail.setStatus(BatchDetailStatus.PENDING);
        detail.setAmount(entry.getAmount());
        detail.setCurrency(normalize(entry.getCurrency()));
        detail.setCreditorWalletType(normalize(entry.getCreditorWalletType()));
        detail.setCreditorCurrency(normalize(entry.getCreditorCurrency()));
        detail.setCreditorIdentifierType(normalize(entry.getCreditorIdentifierType()));
        detail.setCreditorIdentifierValue(entry.getCreditorIdentifierValue());
        detail.setPaymentReference(entry.getPaymentReference());
        detail.setComments(entry.getComments());
        detail.setAdditionalInfo(entry.getAdditionalInfo());
        return detail;
    }

    private BulkPaymentInternalTransferRequest prefundRequest(Batch batch, BigDecimal amount) {
        BulkPaymentInternalTransferRequest request = new BulkPaymentInternalTransferRequest();
        request.setBatchId(batch.getBatchId());
        request.setAccountId(batch.getDebitorAccountId());
        request.setWalletType(batch.getDebitorWalletType());
        request.setAmount(amount);
        request.setCurrency(batch.getCurrency());
        request.setRequestGateway(RequestGateway.WEB);
        request.setPreferredLang("en");
        request.setPaymentReference(batch.getBatchReference());
        request.setComments("Bulk salary prefund for batch " + batch.getBatchId());
        return request;
    }

    private SalaryPaymentInternalRequest salaryPaymentRequest(BatchDetail detail) {
        SalaryPaymentInternalRequest request = new SalaryPaymentInternalRequest();
        request.setBatchId(detail.getBatchId());
        request.setBatchDetailId(String.valueOf(detail.getBatchDetailId()));
        request.setCreditorIdentifierType(detail.getCreditorIdentifierType());
        request.setCreditorIdentifierValue(detail.getCreditorIdentifierValue());
        request.setCreditorWalletType(detail.getCreditorWalletType());
        request.setAmount(detail.getAmount());
        request.setCurrency(detail.getCurrency());
        request.setRequestGateway(RequestGateway.WEB);
        request.setPreferredLang("en");
        request.setPaymentReference(detail.getPaymentReference());
        request.setComments(detail.getComments());
        return request;
    }

    private BulkPaymentInternalTransferRequest refundRequest(
            Batch batch,
            BulkSalaryBatchRefundRequest source,
            BigDecimal amount
    ) {
        BulkPaymentInternalTransferRequest request = new BulkPaymentInternalTransferRequest();
        request.setBatchId(batch.getBatchId());
        request.setAccountId(source.getEnterpriseAccountId());
        request.setWalletType(source.getEnterpriseWalletType());
        request.setAmount(amount);
        request.setCurrency(batch.getCurrency());
        request.setRequestGateway(RequestGateway.WEB);
        request.setPreferredLang("en");
        request.setPaymentReference(batch.getBatchReference());
        request.setComments("Bulk salary refund for batch " + batch.getBatchId());
        return request;
    }

    private void runWorkers(List<BatchDetail> details, int executorCount, DetailWorker worker) {
        ExecutorService executorService = Executors.newFixedThreadPool(Math.max(1, executorCount));
        List<Future<?>> futures = new ArrayList<>();
        for (BatchDetail detail : details) {
            futures.add(executorService.submit(() -> worker.accept(detail)));
        }
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception ex) {
                throw new ApplicationException("BULK_EXECUTOR_FAILED", ex.getMessage());
            }
        }
        executorService.shutdown();
    }

    private Account getActiveAccount(String accountId) {
        List<Account> accounts = accountRepository.findByAccountIdAndStatus(accountId, Constants.ACCOUNT_STATUS_ACTIVE);
        if (accounts == null || accounts.isEmpty()) {
            throw new ApplicationException(PaymentErrorCode.ACCOUNT_NOT_FOUND, null, java.util.Map.of("accountId", accountId));
        }
        return accounts.get(0);
    }

    private AccountIdentifier getIdentifier(String identifierType, String identifierValue) {
        return accountIdentifierRepository
                .findByIdentifierTypeAndIdentifierValueAndStatus(
                        normalize(identifierType),
                        identifierValue,
                        Constants.ACCOUNT_STATUS_ACTIVE
                )
                .orElseThrow(() -> new ApplicationException(PaymentErrorCode.ACCOUNT_IDENTIFIER_NOT_FOUND));
    }

    private Wallet getWallet(String accountId, String currency, String walletType) {
        return walletRepository
                .findByAccountIdAndCurrencyAndWalletType(accountId, normalize(currency), normalize(walletType))
                .orElseThrow(() -> new ApplicationException(PaymentErrorCode.WALLET_NOT_FOUND));
    }

    private void validateWalletUsable(Wallet wallet, String message) {
        if (!Constants.ACCOUNT_STATUS_ACTIVE.equalsIgnoreCase(wallet.getStatus()) || Boolean.TRUE.equals(wallet.getIsLocked())) {
            throw new ApplicationException(PaymentErrorCode.INVALID_WALLET, message);
        }
    }

    private void validateSufficientBalance(Wallet wallet, BigDecimal amount) {
        WalletBalance balance = walletBalanceRepository.findByWalletId(wallet.getWalletId())
                .orElseThrow(() -> new ApplicationException(PaymentErrorCode.WALLET_BALANCE_NOT_FOUND));
        BigDecimal netBalance = balance.getAvailableBalance()
                .subtract(balance.getFicBalance())
                .subtract(balance.getFrozenBalance());
        BigDecimal dbAmount = amount.multiply(currencyFactor()).setScale(2, RoundingMode.HALF_UP);
        if (netBalance.compareTo(dbAmount) < 0) {
            throw new ApplicationException(PaymentErrorCode.INSUFFICIENT_BALANCE);
        }
    }

    private Batch getBatch(String batchId) {
        return batchRepository.findById(batchId)
                .orElseThrow(() -> new ApplicationException("BULK_BATCH_NOT_FOUND", "Bulk batch not found"));
    }

    private Batch getBatchForUpdate(String batchId) {
        return batchRepository.findById(batchId)
                .orElseThrow(() -> new ApplicationException("BULK_BATCH_NOT_FOUND", "Bulk batch not found"));
    }

    private BulkSalaryBatchSummaryResponse summary(Batch batch, String message) {
        return BulkSalaryBatchSummaryResponse.builder()
                .batchId(batch.getBatchId())
                .batchReference(batch.getBatchReference())
                .batchType(batch.getBatchType())
                .status(batch.getStatus())
                .transactionId(batch.getTransactionId())
                .totalRecords(nullToZero(batch.getTotalRecords()))
                .validRecords(nullToZero(batch.getValidRecords()))
                .failedRecords(nullToZero(batch.getFailedRecords()))
                .totalAmount(batch.getTotalAmount())
                .currency(batch.getCurrency())
                .debitorAccountId(batch.getDebitorAccountId())
                .debitorWalletType(batch.getDebitorWalletType())
                .debitorCurrency(batch.getDebitorCurrency())
                .createdOn(batch.getCreatedOn())
                .modifiedOn(batch.getModifiedOn())
                .message(message)
                .build();
    }

    private ApplicationException invalidState(String message, Batch batch) {
        return new ApplicationException(
                "BULK_BATCH_INVALID_STATUS",
                message + ". currentStatus=" + batch.getStatus()
        );
    }

    private String generateBatchId() {
        return IdGenerator.generateTransactionId(BATCH_ID_PREFIX, requiredServerInstance());
    }

    private int configInt(String enumCode, int defaultValue) {
        return enumerationRepository
                .findByEnumTypeIgnoreCaseAndEnumCodeIgnoreCaseAndIsActiveTrue(SYSTEM_CONFIG, enumCode)
                .map(value -> parseInt(value.getEnumValue(), defaultValue))
                .orElse(defaultValue);
    }

    private long configLong(String enumCode, long defaultValue) {
        return enumerationRepository
                .findByEnumTypeIgnoreCaseAndEnumCodeIgnoreCaseAndIsActiveTrue(SYSTEM_CONFIG, enumCode)
                .map(value -> parseLong(value.getEnumValue(), defaultValue))
                .orElse(defaultValue);
    }

    private String configString(String enumCode, String defaultValue) {
        return enumerationRepository
                .findByEnumTypeIgnoreCaseAndEnumCodeIgnoreCaseAndIsActiveTrue(SYSTEM_CONFIG, enumCode)
                .map(value -> defaultIfBlank(value.getEnumValue(), defaultValue))
                .orElse(defaultValue);
    }

    private boolean configBoolean(String enumCode, boolean defaultValue) {
        return enumerationRepository
                .findByEnumTypeIgnoreCaseAndEnumCodeIgnoreCaseAndIsActiveTrue(SYSTEM_CONFIG, enumCode)
                .map(value -> parseBoolean(value.getEnumValue(), defaultValue))
                .orElse(defaultValue);
    }

    private void ensureExecutionWindowOpen() {
        if (isExecutionWindowOpen()) {
            return;
        }

        LocalTime start = parseTime(configString(EXECUTION_WINDOW_START, DEFAULT_EXECUTION_WINDOW_START));
        LocalTime end = parseTime(configString(EXECUTION_WINDOW_END, DEFAULT_EXECUTION_WINDOW_END));
        throw new ApplicationException(
                "BULK_EXECUTION_WINDOW_CLOSED",
                "Bulk salary execution is allowed only between " + start + " and " + end
        );
    }

    private boolean isExecutionWindowOpen() {
        if (!configBoolean(EXECUTION_WINDOW_ENABLED, true)) {
            return true;
        }
        LocalTime start = parseTime(configString(EXECUTION_WINDOW_START, DEFAULT_EXECUTION_WINDOW_START));
        LocalTime end = parseTime(configString(EXECUTION_WINDOW_END, DEFAULT_EXECUTION_WINDOW_END));
        LocalTime now = TenantTime.now().toLocalTime();
        return isWithinWindow(now, start, end);
    }

    private boolean isWithinWindow(LocalTime now, LocalTime start, LocalTime end) {
        if (start.equals(end)) {
            return true;
        }
        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        }
        return !now.isBefore(start) || now.isBefore(end);
    }

    private LocalTime parseTime(String value) {
        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException ex) {
            throw new ApplicationException(
                    "BULK_EXECUTION_WINDOW_INVALID",
                    "Invalid bulk execution window time: " + value
            );
        }
    }

    private void sleepBetweenExecutorRuns() {
        long delay = configLong(EXECUTOR_DELAY_MS, DEFAULT_EXECUTOR_DELAY_MS);
        if (delay <= 0) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ApplicationException("BULK_EXECUTOR_INTERRUPTED", "Bulk executor interrupted");
        }
    }

    private BigDecimal currencyFactor() {
        return new BigDecimal(propertyReader.getPropertyValue("currency.factor"));
    }

    private String requiredServerInstance() {
        String serverInstance = propertyReader.getPropertyValue("server.instance");
        if (serverInstance == null || serverInstance.isBlank()) {
            throw new IllegalStateException("server.instance is not configured");
        }
        return serverInstance.trim();
    }

    private int parseInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private long parseLong(String value, long defaultValue) {
        try {
            return Long.parseLong(value);
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private boolean parseBoolean(String value, boolean defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    private String resolveErrorCode(Exception ex) {
        if (ex instanceof ApplicationException applicationException) {
            return applicationException.getErrorCode();
        }
        return "BULK_PROCESSING_ERROR";
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private String actor(BulkSalaryBatchActionRequest request) {
        if (request == null || request.getPerformedBy() == null || request.getPerformedBy().isBlank()) {
            return "SYSTEM";
        }
        return request.getPerformedBy();
    }

    private String mergeRemarks(String existing, String next) {
        if (next == null || next.isBlank()) {
            return existing;
        }
        if (existing == null || existing.isBlank()) {
            return next;
        }
        return existing + " | " + next;
    }

    @FunctionalInterface
    private interface DetailWorker {
        void accept(BatchDetail detail);
    }
}
