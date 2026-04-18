package com.paynest.statements.service;


import com.paynest.config.tenant.TenantTime;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paynest.common.Constants;
import com.paynest.common.ErrorCodes;
import com.paynest.config.PropertyReader;
import com.paynest.exception.ApplicationException;
import com.paynest.payments.entity.TransactionDetails;
import com.paynest.payments.entity.Transactions;
import com.paynest.payments.repository.TransactionDetailsRepository;
import com.paynest.payments.service.ServiceCatalogService;
import com.paynest.statements.dto.ReceiptDocument;
import com.paynest.statements.dto.ReceiptParty;
import com.paynest.users.entity.Account;
import com.paynest.users.entity.Wallet;
import com.paynest.users.repository.AccountRepository;
import com.paynest.users.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
public class ReceiptDocumentBuilder {

    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd-MMM-yy, hh:mm:ss a", Locale.ENGLISH);
    private static final DateTimeFormatter GENERATED_AT_FORMAT =
            DateTimeFormatter.ofPattern("dd-MMM-yy, hh:mm:ss a", Locale.ENGLISH);
    private static final TypeReference<Map<String, Object>> JSON_MAP_TYPE =
            new TypeReference<>() {
            };
    private static final List<String> SERVICE_CHARGE_KEYS = List.of(
            "serviceChargePaid",
            "serviceCharge",
            "serviceCharges",
            "chargeAmount",
            "feeAmount",
            "feesAmount",
            "totalFees",
            "totalFee",
            "totalCharge",
            "amount"
    );

    private final TransactionDetailsRepository transactionDetailsRepository;
    private final AccountRepository accountRepository;
    private final WalletRepository walletRepository;
    private final ServiceCatalogService serviceCatalogService;
    private final PropertyReader propertyReader;
    private final ObjectMapper objectMapper;

    public ReceiptDocument build(Transactions transaction, String accountId) {
        List<TransactionDetails> transactionDetails = transactionDetailsRepository
                .findByIdTransactionId(transaction.getTransactionId())
                .stream()
                .sorted(Comparator.comparing(detail -> detail.getId().getTxnSequenceNumber()))
                .toList();

        TransactionDetails accountDetail = transactionDetails.stream()
                .filter(detail -> accountId.equalsIgnoreCase(detail.getAccountId()))
                .findFirst()
                .orElseThrow(() -> new ApplicationException(
                        ErrorCodes.TRANSACTION_DETAIL_NOT_FOUND,
                        "Transaction detail not found for account",
                        HttpStatus.NOT_FOUND
                ));

        Map<String, Account> accountsById = loadAccounts(transaction, transactionDetails);
        Map<Long, Wallet> walletsById = loadWallets(transactionDetails);
        Account account = resolveAccount(accountsById, accountId, accountDetail);
        Wallet accountWallet = resolveWallet(accountDetail, walletsById);
        LocalDateTime now = TenantTime.now();
        BigDecimal transactionAmount = toCustomerAmount(accountDetail.getTransactionValue());
        BigDecimal serviceChargePaid = resolveServiceChargePaid(transaction, accountDetail.getEntryType());

        ReceiptDocument document = new ReceiptDocument();
        document.setTransactionId(transaction.getTransactionId());
        document.setTransferOn(formatDateTime(firstPresent(accountDetail.getTransferOn(), transaction.getTransferOn())));
        document.setServiceCode(transaction.getServiceCode());
        document.setServiceName(serviceCatalogService.resolveServiceName(transaction.getServiceCode()));
        document.setStatus(toDisplayStatus(firstPresent(accountDetail.getTransferStatus(), transaction.getTransferStatus())));
        document.setTransferStatus(firstPresent(accountDetail.getTransferStatus(), transaction.getTransferStatus()));
        document.setEntryType(accountDetail.getEntryType());
        document.setTransactionDirection(toTransactionDirection(accountDetail.getEntryType()));
        document.setTransactionAmount(transactionAmount);
        document.setServiceChargePaid(serviceChargePaid);
        document.setTotalAmountPaid(addAmounts(transactionAmount, serviceChargePaid));
        document.setTotalAmountLabel(totalAmountLabel(accountDetail.getEntryType()));
        document.setApprovedAmount(toCustomerAmount(accountDetail.getApprovedValue()));
        document.setRequestedAmount(toCustomerAmount(transaction.getTransactionValue()));
        document.setPreviousBalance(toCustomerAmount(accountDetail.getPreviousBalance()));
        document.setPostBalance(toCustomerAmount(accountDetail.getPostBalance()));
        document.setCurrency(accountWallet == null ? null : accountWallet.getCurrency());
        document.setAccountId(accountId);
        document.setAccountMobileNumber(account == null ? null : account.getMobileNumber());
        document.setPreferredLanguage(account == null ? null : account.getPreferredLang());
        document.setPaymentReference(transaction.getPaymentReference());
        document.setTraceId(transaction.getTraceId());
        document.setInitiatedBy(transaction.getCreatedBy());
        document.setRemarks(firstPresent(transaction.getComments(), transaction.getServiceCode()));
        document.setDebtor(buildParty(transaction.getDebitorAccountId(), Constants.TXN_TYPE_DR, transactionDetails, accountsById, walletsById));
        document.setCreditor(buildParty(transaction.getCreditorAccountId(), Constants.TXN_TYPE_CR, transactionDetails, accountsById, walletsById));
        document.setAdditionalInfo(parseAdditionalInfo(transaction));
        document.setGeneratedAt(now.format(GENERATED_AT_FORMAT));
        document.setCurrentYear(String.valueOf(now.getYear()));
        return document;
    }

    private Map<String, Account> loadAccounts(Transactions transaction, List<TransactionDetails> transactionDetails) {
        Set<String> accountIds = new HashSet<>();
        Set<String> identifierValues = new HashSet<>();
        addIfPresent(accountIds, transaction.getDebitorAccountId());
        addIfPresent(accountIds, transaction.getCreditorAccountId());
        addIfPresent(accountIds, transaction.getCreatedBy());
        addIfPresent(identifierValues, transaction.getDebitorIdentifierValue());
        addIfPresent(identifierValues, transaction.getCreditorIdentifierValue());

        for (TransactionDetails detail : transactionDetails) {
            addIfPresent(accountIds, detail.getAccountId());
            addIfPresent(identifierValues, detail.getIdentifierId());
            addIfPresent(identifierValues, detail.getSecondIdentifierId());
        }

        Map<String, Account> accountsByLookup = new LinkedHashMap<>();
        if (!accountIds.isEmpty()) {
            accountRepository.findAllById(accountIds)
                    .forEach(account -> addAccountLookups(accountsByLookup, account));
        }

        for (String identifierValue : identifierValues) {
            if (!accountsByLookup.containsKey(identifierValue)) {
                accountRepository.findByMobileNumber(identifierValue)
                        .ifPresent(account -> addAccountLookups(accountsByLookup, account));
            }
        }

        return accountsByLookup;
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

    private ReceiptParty buildParty(
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
                .orElseGet(() -> transactionDetails.stream()
                        .filter(candidate -> entryType.equalsIgnoreCase(candidate.getEntryType()))
                        .findFirst()
                        .orElse(null));
        Wallet wallet = resolveWallet(detail, walletsById);
        Account account = resolveAccount(accountsById, accountId, detail);
        String resolvedAccountId = firstNonBlank(account == null ? null : account.getAccountId(), accountId, detail == null ? null : detail.getAccountId());
        String mobileNumber = firstNonBlank(account == null ? null : account.getMobileNumber(), detail == null ? null : detail.getIdentifierId());

        return new ReceiptParty(
                resolvedAccountId,
                account == null ? null : account.getAccountType(),
                fullName(account),
                mobileNumber,
                wallet == null ? parseWalletId(detail == null ? null : detail.getWalletNumber()).orElse(null) : wallet.getWalletId(),
                wallet == null ? null : wallet.getWalletType(),
                wallet == null ? null : wallet.getCurrency()
        );
    }

    private void addAccountLookups(Map<String, Account> accountsByLookup, Account account) {
        if (account == null) {
            return;
        }
        if (account.getAccountId() != null && !account.getAccountId().isBlank()) {
            accountsByLookup.putIfAbsent(account.getAccountId(), account);
        }
        if (account.getMobileNumber() != null && !account.getMobileNumber().isBlank()) {
            accountsByLookup.putIfAbsent(account.getMobileNumber(), account);
        }
    }

    private Account resolveAccount(
            Map<String, Account> accountsByLookup,
            String accountId,
            TransactionDetails detail
    ) {
        String[] lookupKeys = {
                accountId,
                detail == null ? null : detail.getAccountId(),
                detail == null ? null : detail.getIdentifierId(),
                detail == null ? null : detail.getSecondIdentifierId()
        };
        for (String lookupKey : lookupKeys) {
            if (lookupKey != null && !lookupKey.isBlank() && accountsByLookup.containsKey(lookupKey)) {
                return accountsByLookup.get(lookupKey);
            }
        }
        return null;
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
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("additionalInfo", additionalInfo);
            return fallback;
        }
    }

    private BigDecimal resolveServiceChargePaid(Transactions transaction, String entryType) {
        if (!Constants.TXN_TYPE_DR.equalsIgnoreCase(entryType)) {
            return zeroAmount();
        }
        return resolveRawServiceCharge(transaction)
                .map(this::toCustomerAmount)
                .orElseGet(this::zeroAmount);
    }

    private Optional<BigDecimal> resolveRawServiceCharge(Transactions transaction) {
        String feesDetails = transaction.getFeesDetails();
        if (feesDetails == null || feesDetails.isBlank()) {
            return Optional.empty();
        }
        Optional<BigDecimal> directAmount = parseAmountValue(feesDetails);
        if (directAmount.isPresent()) {
            return directAmount;
        }
        try {
            Object feeData = objectMapper.readValue(feesDetails, Object.class);
            return findChargeAmount(feeData);
        } catch (JsonProcessingException ex) {
            return Optional.empty();
        }
    }

    private Optional<BigDecimal> findChargeAmount(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (String chargeKey : SERVICE_CHARGE_KEYS) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (matchesChargeKey(entry.getKey(), chargeKey)) {
                        Optional<BigDecimal> amount = parseAmountValue(entry.getValue());
                        if (amount.isPresent()) {
                            return amount;
                        }
                        Optional<BigDecimal> nestedAmount = findChargeAmount(entry.getValue());
                        if (nestedAmount.isPresent()) {
                            return nestedAmount;
                        }
                    }
                }
            }
            return sumNestedChargeAmounts(map.values());
        }
        if (value instanceof List<?> list) {
            return sumNestedChargeAmounts(list);
        }
        return parseAmountValue(value);
    }

    private Optional<BigDecimal> sumNestedChargeAmounts(Iterable<?> values) {
        BigDecimal total = BigDecimal.ZERO;
        boolean foundAmount = false;
        for (Object value : values) {
            Optional<BigDecimal> nestedAmount = findChargeAmount(value);
            if (nestedAmount.isPresent()) {
                total = total.add(nestedAmount.get());
                foundAmount = true;
            }
        }
        return foundAmount ? Optional.of(total) : Optional.empty();
    }

    private boolean matchesChargeKey(Object actualKey, String expectedKey) {
        if (actualKey == null) {
            return false;
        }
        return normalizeKey(actualKey.toString()).equals(normalizeKey(expectedKey));
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.replace("_", "")
                .replace("-", "")
                .replace(" ", "")
                .toLowerCase(Locale.ROOT);
    }

    private Optional<BigDecimal> parseAmountValue(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof Number number) {
            return Optional.of(new BigDecimal(number.toString()));
        }
        String text = value.toString().trim().replace(",", "");
        if (text.matches("-?\\d+(\\.\\d+)?")) {
            return Optional.of(new BigDecimal(text));
        }
        return Optional.empty();
    }

    private BigDecimal toCustomerAmount(BigDecimal dbAmount) {
        if (dbAmount == null) {
            return null;
        }
        return dbAmount.divide(resolveCurrencyFactor(), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal addAmounts(BigDecimal firstAmount, BigDecimal secondAmount) {
        return zeroIfNull(firstAmount).add(zeroIfNull(secondAmount)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal zeroIfNull(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private BigDecimal zeroAmount() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveCurrencyFactor() {
        String configuredFactor = propertyReader.getPropertyValue("currency.factor");
        if (configuredFactor == null || configuredFactor.isBlank()) {
            return BigDecimal.ONE;
        }
        try {
            BigDecimal factor = new BigDecimal(configuredFactor.trim());
            return factor.compareTo(BigDecimal.ZERO) <= 0 ? BigDecimal.ONE : factor;
        } catch (NumberFormatException ex) {
            return BigDecimal.ONE;
        }
    }

    private String totalAmountLabel(String entryType) {
        if (Constants.TXN_TYPE_CR.equalsIgnoreCase(entryType)) {
            return "Total Amount Received";
        }
        return "Total Amount Paid";
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

    private String toTransactionDirection(String entryType) {
        if (entryType == null) {
            return null;
        }
        if (Constants.TXN_TYPE_DR.equalsIgnoreCase(entryType)) {
            return "Debit";
        }
        if (Constants.TXN_TYPE_CR.equalsIgnoreCase(entryType)) {
            return "Credit";
        }
        return entryType;
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(DATE_TIME_FORMAT);
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

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private void addIfPresent(Set<String> values, String value) {
        if (value != null && !value.isBlank()) {
            values.add(value);
        }
    }

    private String firstPresent(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private LocalDateTime firstPresent(LocalDateTime value, LocalDateTime fallback) {
        return value == null ? fallback : value;
    }
}
