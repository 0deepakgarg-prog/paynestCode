package com.paynest.payments.service;


import com.paynest.config.tenant.TenantTime;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paynest.common.Constants;
import com.paynest.common.ErrorCodes;
import com.paynest.config.security.JWTUtils;
import com.paynest.exception.ApplicationException;
import com.paynest.payments.dto.TransactionDetailResponse;
import com.paynest.payments.dto.TransactionEntryDetailResponse;
import com.paynest.payments.dto.TransactionPartyDetailResponse;
import com.paynest.payments.entity.TransactionDetails;
import com.paynest.payments.entity.Transactions;
import com.paynest.payments.repository.TransactionDetailsRepository;
import com.paynest.payments.repository.TransactionsRepository;
import com.paynest.users.entity.Account;
import com.paynest.users.entity.Wallet;
import com.paynest.users.repository.AccountRepository;
import com.paynest.users.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransactionDetailQueryService {

    private static final DateTimeFormatter RESPONSE_DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final TypeReference<Map<String, Object>> JSON_MAP_TYPE =
            new TypeReference<>() {
            };

    private final TransactionsRepository transactionsRepository;
    private final TransactionDetailsRepository transactionDetailsRepository;
    private final AccountRepository accountRepository;
    private final WalletRepository walletRepository;
    private final ServiceCatalogService serviceCatalogService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public TransactionDetailResponse getTransactionDetail(String accountId, String transactionId) {
        String resolvedAccountId = resolveAccountId(accountId);

        Transactions transaction = transactionsRepository.findById(transactionId)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCodes.TXN_NOT_FOUND,
                        "Transaction not found",
                        HttpStatus.NOT_FOUND
                ));

        List<TransactionDetails> transactionDetails = transactionDetailsRepository.findByIdTransactionId(transactionId)
                .stream()
                .sorted(Comparator.comparing(detail -> detail.getId().getTxnSequenceNumber()))
                .toList();

        TransactionDetails accountDetail = transactionDetails.stream()
                .filter(detail -> resolvedAccountId.equalsIgnoreCase(detail.getAccountId()))
                .findFirst()
                .orElseThrow(() -> new ApplicationException(
                        ErrorCodes.TRANSACTION_DETAIL_NOT_FOUND,
                        "Transaction detail not found for account",
                        HttpStatus.NOT_FOUND
                ));

        Map<String, Account> accountsById = loadAccounts(transaction);
        Map<Long, Wallet> walletsById = loadWallets(transactionDetails);

        return new TransactionDetailResponse(
                transaction.getTransactionId(),
                formatTransferDate(firstPresent(accountDetail.getTransferOn(), transaction.getTransferOn())),
                resolvedAccountId,
                transaction.getServiceCode(),
                serviceCatalogService.resolveServiceName(transaction.getServiceCode()),
                firstPresent(accountDetail.getTransferStatus(), transaction.getTransferStatus()),
                toDisplayStatus(firstPresent(accountDetail.getTransferStatus(), transaction.getTransferStatus())),
                transaction.getErrorCode(),
                accountDetail.getEntryType(),
                accountDetail.getTransactionValue(),
                accountDetail.getApprovedValue(),
                transaction.getTransactionValue(),
                accountDetail.getPreviousBalance(),
                accountDetail.getPostBalance(),
                accountDetail.getPreviousFicBalance(),
                accountDetail.getPostFicBalance(),
                accountDetail.getPreviousFrozenBalance(),
                accountDetail.getPostFrozenBalance(),
                transaction.getPaymentReference(),
                transaction.getRequestGateway(),
                transaction.getTraceId(),
                transaction.getCreatedBy(),
                firstPresent(transaction.getComments(), transaction.getServiceCode()),
                transaction.getReconciliationDone(),
                transaction.getReconciliationBy(),
                formatTransferDate(transaction.getReconciliationDate()),
                buildParty(transaction.getDebitorAccountId(), Constants.TXN_TYPE_DR, transactionDetails, accountsById, walletsById),
                buildParty(transaction.getCreditorAccountId(), Constants.TXN_TYPE_CR, transactionDetails, accountsById, walletsById),
                transactionDetails.stream()
                        .map(detail -> toEntryResponse(detail))
                        .toList(),
                parseAdditionalInfo(transaction),
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

    private Map<String, Account> loadAccounts(Transactions transaction) {
        Set<String> accountIds = new HashSet<>();
        addIfPresent(accountIds, transaction.getDebitorAccountId());
        addIfPresent(accountIds, transaction.getCreditorAccountId());
        addIfPresent(accountIds, transaction.getCreatedBy());

        if (accountIds.isEmpty()) {
            return Map.of();
        }

        return accountRepository.findAllById(accountIds)
                .stream()
                .collect(Collectors.toMap(Account::getAccountId, Function.identity()));
    }

    private Map<Long, Wallet> loadWallets(List<TransactionDetails> transactionDetails) {
        Set<Long> walletIds = transactionDetails.stream()
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

    private TransactionPartyDetailResponse buildParty(
            String accountId,
            String entryType,
            List<TransactionDetails> transactionDetails,
            Map<String, Account> accountsById,
            Map<Long, Wallet> walletsById
    ) {
        TransactionDetails detail = transactionDetails.stream()
                .filter(candidate -> accountId != null && accountId.equalsIgnoreCase(candidate.getAccountId()))
                .filter(candidate -> entryType.equalsIgnoreCase(candidate.getEntryType()))
                .findFirst()
                .orElse(null);
        Wallet wallet = resolveWallet(detail, walletsById);
        Account account = accountsById.get(accountId);

        return new TransactionPartyDetailResponse(
                accountId,
                account == null ? null : account.getAccountType(),
                fullName(account),
                account == null ? null : account.getMobileNumber(),
                wallet == null ? parseWalletId(detail == null ? null : detail.getWalletNumber()).orElse(null) : wallet.getWalletId(),
                wallet == null ? null : wallet.getWalletType(),
                wallet == null ? null : wallet.getCurrency(),
                detail == null ? entryType : detail.getEntryType()
        );
    }

    private Wallet resolveWallet(TransactionDetails detail, Map<Long, Wallet> walletsById) {
        if (detail == null) {
            return null;
        }
        return parseWalletId(detail.getWalletNumber())
                .map(walletsById::get)
                .orElse(null);
    }

    private TransactionEntryDetailResponse toEntryResponse(TransactionDetails detail) {
        return new TransactionEntryDetailResponse(
                detail.getId().getTxnSequenceNumber(),
                detail.getAccountId(),
                detail.getUserType(),
                detail.getEntryType(),
                detail.getIdentifierId(),
                detail.getSecondIdentifierId(),
                parseWalletId(detail.getWalletNumber()).orElse(null),
                formatTransferDate(detail.getTransferOn()),
                detail.getServiceCode(),
                detail.getTransferStatus(),
                detail.getTransactionValue(),
                detail.getApprovedValue(),
                detail.getPreviousBalance(),
                detail.getPostBalance(),
                detail.getPreviousFicBalance(),
                detail.getPostFicBalance(),
                detail.getPreviousFrozenBalance(),
                detail.getPostFrozenBalance()
        );
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
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("additionalInfo", additionalInfo);
            return fallback;
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

    private String firstPresent(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private void addIfPresent(Set<String> values, String value) {
        if (value != null && !value.isBlank()) {
            values.add(value);
        }
    }

    private LocalDateTime firstPresent(LocalDateTime value, LocalDateTime fallback) {
        return value == null ? fallback : value;
    }
}
