package com.paynest.payments.service;


import com.paynest.config.tenant.TenantTime;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paynest.common.Constants;
import com.paynest.common.ErrorCodes;
import com.paynest.config.security.JWTUtils;
import com.paynest.config.tenant.TraceContext;
import com.paynest.exception.ApplicationException;
import com.paynest.payments.dto.PaymentHistoryResponse;
import com.paynest.payments.dto.PaymentHistoryTransactionResponse;
import com.paynest.payments.entity.TransactionDetails;
import com.paynest.payments.entity.Transactions;
import com.paynest.payments.repository.TransactionDetailsRepository;
import com.paynest.payments.repository.TransactionsRepository;
import com.paynest.users.entity.Account;
import com.paynest.users.entity.Wallet;
import com.paynest.users.repository.AccountRepository;
import com.paynest.users.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentHistoryService {

    private static final DateTimeFormatter REQUEST_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter RESPONSE_DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final TypeReference<Map<String, Object>> JSON_MAP_TYPE =
            new TypeReference<>() {
            };
    private static final String PAYMENT_METHOD_WALLET = "WALLET";

    private final TransactionDetailsRepository transactionDetailsRepository;
    private final TransactionsRepository transactionsRepository;
    private final AccountRepository accountRepository;
    private final WalletRepository walletRepository;
    private final ServiceCatalogService serviceCatalogService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PaymentHistoryResponse getPaymentHistory(
            String accountId,
            String fromDate,
            String toDate,
            Integer offset,
            Integer limit,
            String paymentMethodType,
            String order,
            String status
    ) {
        String resolvedAccountId = resolveAccountId(accountId);
        validatePaymentMethodType(paymentMethodType);

        LocalDateTime fromDateTime = parseFromDate(fromDate);
        LocalDateTime toDateTime = parseToDate(toDate);
        validateDateRange(fromDateTime, toDateTime);

        Set<String> statuses = parseStatuses(status);
        Sort sort = Sort.by(parseDirection(order), "transferOn");
        Specification<TransactionDetails> specification =
                buildSpecification(resolvedAccountId, fromDateTime, toDateTime, statuses);

        List<TransactionDetails> historyRows;
        long totalRecords;
        if (limit == null) {
            historyRows = transactionDetailsRepository.findAll(specification, sort);
            totalRecords = historyRows.size();
        } else {
            validateLimitAndOffset(limit, offset);
            Page<TransactionDetails> page = transactionDetailsRepository.findAll(
                    specification,
                    PageRequest.of(resolvePage(offset), limit, sort)
            );
            historyRows = page.getContent();
            totalRecords = page.getTotalElements();
        }

        List<PaymentHistoryTransactionResponse> transactions =
                buildTransactions(resolvedAccountId, historyRows);

        return new PaymentHistoryResponse(
                totalRecords,
                transactions,
                resolveTraceId(),
                TenantTime.now()
        );
    }

    private String resolveAccountId(String requestedAccountId) {
        String currentAccountId = JWTUtils.getCurrentAccountId();
        if (requestedAccountId == null || requestedAccountId.isBlank()
                || requestedAccountId.equalsIgnoreCase(currentAccountId)) {
            return currentAccountId;
        }

        if ("ADMIN".equalsIgnoreCase(JWTUtils.getCurrentAccountType())) {
            return requestedAccountId;
        }

        throw new ApplicationException(
                ErrorCodes.INVALID_PRIVILEGES,
                "Token does not have necessary access",
                HttpStatus.FORBIDDEN
        );
    }

    private void validatePaymentMethodType(String paymentMethodType) {
        if (paymentMethodType == null || paymentMethodType.isBlank()) {
            return;
        }
        if (PAYMENT_METHOD_WALLET.equalsIgnoreCase(paymentMethodType)) {
            return;
        }

        throw new ApplicationException(
                ErrorCodes.INVALID_REQUEST,
                "Only WALLET paymentMethodType is supported"
        );
    }

    private LocalDateTime parseFromDate(String fromDate) {
        return parseDate(fromDate, "fromDate")
                .map(LocalDate::atStartOfDay)
                .orElse(null);
    }

    private LocalDateTime parseToDate(String toDate) {
        return parseDate(toDate, "toDate")
                .map(date -> LocalDateTime.of(date, LocalTime.MAX))
                .orElse(null);
    }

    private Optional<LocalDate> parseDate(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String trimmedValue = value.trim();
        for (DateTimeFormatter formatter : List.of(REQUEST_DATE_FORMAT, DateTimeFormatter.ISO_LOCAL_DATE)) {
            try {
                return Optional.of(LocalDate.parse(trimmedValue, formatter));
            } catch (DateTimeParseException ignored) {
                // Try the next supported format.
            }
        }

        throw new ApplicationException(
                ErrorCodes.INVALID_REQUEST,
                fieldName + " must be in dd/MM/yyyy or yyyy-MM-dd format"
        );
    }

    private void validateDateRange(LocalDateTime fromDateTime, LocalDateTime toDateTime) {
        if (fromDateTime != null && toDateTime != null && fromDateTime.isAfter(toDateTime)) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_REQUEST,
                    "fromDate must be before or equal to toDate"
            );
        }
    }

    private Set<String> parseStatuses(String status) {
        if (status == null || status.isBlank()) {
            return Collections.emptySet();
        }

        return Arrays.stream(status.split(","))
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .map(this::toStatusCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String toStatusCode(String status) {
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "SUCCESS", "SUCCEEDED", "TS" -> Constants.TRANSACTION_SUCCESS;
            case "FAILED", "FAILURE", "TF" -> Constants.TRANSACTION_FAILED;
            case "PENDING", "TP" -> Constants.TRANSACTION_PENDING;
            case "INITIATED", "TI" -> Constants.TRANSACTION_INITIATED;
            case "AMBIGUOUS", "TA" -> Constants.TRANSACTION_AMBIGUOUS;
            default -> throw new ApplicationException(
                    ErrorCodes.INVALID_STATUS,
                    "Unsupported transaction status: " + status
            );
        };
    }

    private Sort.Direction parseDirection(String order) {
        if (order == null || order.isBlank()) {
            return Sort.Direction.DESC;
        }
        return switch (order.toUpperCase(Locale.ROOT)) {
            case "ASC" -> Sort.Direction.ASC;
            case "DESC" -> Sort.Direction.DESC;
            default -> throw new ApplicationException(
                    ErrorCodes.INVALID_REQUEST,
                    "order must be ASC or DESC"
            );
        };
    }

    private void validateLimitAndOffset(Integer limit, Integer offset) {
        if (limit < 1) {
            throw new ApplicationException(ErrorCodes.INVALID_REQUEST, "limit must be greater than 0");
        }
        if (offset != null && offset < 1) {
            throw new ApplicationException(ErrorCodes.INVALID_REQUEST, "offset must be greater than 0");
        }
    }

    private int resolvePage(Integer offset) {
        if (offset == null) {
            return 0;
        }
        return offset - 1;
    }

    private Specification<TransactionDetails> buildSpecification(
            String accountId,
            LocalDateTime fromDateTime,
            LocalDateTime toDateTime,
            Set<String> statuses
    ) {
        return (root, query, criteriaBuilder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("accountId"), accountId));

            if (fromDateTime != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("transferOn"), fromDateTime));
            }
            if (toDateTime != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("transferOn"), toDateTime));
            }
            if (!statuses.isEmpty()) {
                predicates.add(root.get("transferStatus").in(statuses));
            }

            return criteriaBuilder.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private List<PaymentHistoryTransactionResponse> buildTransactions(
            String accountId,
            List<TransactionDetails> historyRows
    ) {
        if (historyRows.isEmpty()) {
            return List.of();
        }

        Set<String> transactionIds = historyRows.stream()
                .map(detail -> detail.getId().getTransactionId())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, Transactions> transactionsById = transactionsRepository.findAllById(transactionIds)
                .stream()
                .collect(Collectors.toMap(Transactions::getTransactionId, Function.identity()));

        Map<String, List<TransactionDetails>> detailsByTransactionId = transactionDetailsRepository
                .findByIdTransactionIdIn(transactionIds)
                .stream()
                .collect(Collectors.groupingBy(
                        detail -> detail.getId().getTransactionId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        Map<String, Account> accountsById = loadAccounts(accountId, transactionsById.values());
        Map<Long, Wallet> walletsById = loadWallets(detailsByTransactionId.values());

        return historyRows.stream()
                .map(detail -> toHistoryTransaction(
                        accountId,
                        detail,
                        transactionsById.get(detail.getId().getTransactionId()),
                        detailsByTransactionId.getOrDefault(detail.getId().getTransactionId(), List.of()),
                        accountsById,
                        walletsById
                ))
                .filter(Objects::nonNull)
                .toList();
    }

    private Map<String, Account> loadAccounts(
            String accountId,
            Collection<Transactions> transactions
    ) {
        Set<String> accountIds = new HashSet<>();
        accountIds.add(accountId);
        transactions.forEach(transaction -> {
            addIfPresent(accountIds, transaction.getDebitorAccountId());
            addIfPresent(accountIds, transaction.getCreditorAccountId());
            addIfPresent(accountIds, transaction.getCreatedBy());
        });

        return accountRepository.findAllById(accountIds)
                .stream()
                .collect(Collectors.toMap(Account::getAccountId, Function.identity()));
    }

    private Map<Long, Wallet> loadWallets(Collection<List<TransactionDetails>> groupedDetails) {
        Set<Long> walletIds = groupedDetails.stream()
                .flatMap(Collection::stream)
                .map(TransactionDetails::getWalletNumber)
                .map(this::parseWalletId)
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());

        if (walletIds.isEmpty()) {
            return Map.of();
        }

        return walletRepository.findAllById(walletIds)
                .stream()
                .collect(Collectors.toMap(Wallet::getWalletId, Function.identity()));
    }

    private PaymentHistoryTransactionResponse toHistoryTransaction(
            String accountId,
            TransactionDetails currentDetail,
            Transactions transaction,
            List<TransactionDetails> transactionDetails,
            Map<String, Account> accountsById,
            Map<Long, Wallet> walletsById
    ) {
        if (transaction == null) {
            return null;
        }

        TransactionDetails counterpartyDetail = resolveCounterpartyDetail(currentDetail, transactionDetails);
        String counterpartyAccountId = resolveCounterpartyAccountId(accountId, transaction, counterpartyDetail);
        Account currentAccount = accountsById.get(accountId);
        Account counterpartyAccount = accountsById.get(counterpartyAccountId);
        Wallet currentWallet = resolveWallet(currentDetail, walletsById);
        Wallet counterpartyWallet = resolveWallet(counterpartyDetail, walletsById);

        BigDecimal requestedAmount = transaction.getTransactionValue();
        BigDecimal transactionAmount = valueOrFallback(currentDetail.getTransactionValue(), requestedAmount);
        String comments = firstPresent(transaction.getComments(), transaction.getServiceCode());

        return new PaymentHistoryTransactionResponse(
                transaction.getTransactionId(),
                formatTransferDate(firstPresent(currentDetail.getTransferOn(), transaction.getTransferOn())),
                transaction.getServiceCode(),
                serviceCatalogService.resolveServiceName(transaction.getServiceCode()),
                toDisplayStatus(firstPresent(currentDetail.getTransferStatus(), transaction.getTransferStatus())),
                transaction.getErrorCode(),
                currentDetail.getEntryType(),
                transactionAmount,
                currentDetail.getApprovedValue(),
                requestedAmount,
                currentDetail.getPreviousBalance(),
                currentDetail.getPostBalance(),
                parseWalletId(currentDetail.getWalletNumber()).orElse(null),
                currentWallet == null ? null : currentWallet.getWalletType(),
                currentWallet == null ? null : currentWallet.getCurrency(),
                accountId,
                currentAccount == null ? null : currentAccount.getAccountType(),
                counterpartyAccountId,
                counterpartyAccount == null ? null : counterpartyAccount.getAccountType(),
                fullName(counterpartyAccount),
                counterpartyWallet == null ? null : counterpartyWallet.getWalletType(),
                counterpartyWallet == null ? null : counterpartyWallet.getCurrency(),
                transaction.getPaymentReference(),
                transaction.getRequestGateway(),
                transaction.getTraceId(),
                transaction.getCreatedBy(),
                comments,
                parseAdditionalInfo(transaction)
        );
    }

    private TransactionDetails resolveCounterpartyDetail(
            TransactionDetails currentDetail,
            List<TransactionDetails> transactionDetails
    ) {
        return transactionDetails.stream()
                .filter(detail -> !detail.getId().getTxnSequenceNumber()
                        .equals(currentDetail.getId().getTxnSequenceNumber()))
                .findFirst()
                .orElse(null);
    }

    private String resolveCounterpartyAccountId(
            String accountId,
            Transactions transaction,
            TransactionDetails counterpartyDetail
    ) {
        if (counterpartyDetail != null && counterpartyDetail.getAccountId() != null) {
            return counterpartyDetail.getAccountId();
        }

        if (!accountId.equalsIgnoreCase(transaction.getDebitorAccountId())) {
            return transaction.getDebitorAccountId();
        }
        return transaction.getCreditorAccountId();
    }

    private Wallet resolveWallet(TransactionDetails detail, Map<Long, Wallet> walletsById) {
        if (detail == null) {
            return null;
        }
        return parseWalletId(detail.getWalletNumber())
                .map(walletsById::get)
                .orElse(null);
    }

    private Optional<Long> parseWalletId(String walletNumber) {
        if (walletNumber == null || walletNumber.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(walletNumber));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private Map<String, Object> parseAdditionalInfo(Transactions transaction) {
        String additionalInfo = transaction.getAdditionalInfo();
        if (additionalInfo == null || additionalInfo.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(additionalInfo, JSON_MAP_TYPE);
        } catch (JsonProcessingException ex) {
            return Map.of("additionalInfo", additionalInfo);
        }
    }

    private String toDisplayStatus(String status) {
        if (status == null) {
            return null;
        }
        return switch (status.toUpperCase(Locale.ROOT)) {
            case Constants.TRANSACTION_SUCCESS -> "Success";
            case Constants.TRANSACTION_FAILED -> "Failed";
            case Constants.TRANSACTION_PENDING -> "Pending";
            case Constants.TRANSACTION_INITIATED -> "Initiated";
            case Constants.TRANSACTION_AMBIGUOUS -> "Ambiguous";
            default -> status;
        };
    }

    private String formatTransferDate(LocalDateTime transferDate) {
        return transferDate == null ? null : transferDate.format(RESPONSE_DATE_TIME_FORMAT);
    }

    private String fullName(Account account) {
        if (account == null) {
            return null;
        }
        String name = String.join(
                " ",
                Optional.ofNullable(account.getFirstName()).orElse(""),
                Optional.ofNullable(account.getLastName()).orElse("")
        ).trim();
        return name.isBlank() ? null : name;
    }

    private void addIfPresent(Set<String> values, String value) {
        if (value != null && !value.isBlank()) {
            values.add(value);
        }
    }

    private BigDecimal valueOrFallback(BigDecimal value, BigDecimal fallback) {
        return value == null ? fallback : value;
    }

    private String firstPresent(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private LocalDateTime firstPresent(LocalDateTime value, LocalDateTime fallback) {
        return value == null ? fallback : value;
    }

    private String resolveTraceId() {
        String traceId = TraceContext.getTraceId();
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }
        return UUID.randomUUID().toString();
    }
}
