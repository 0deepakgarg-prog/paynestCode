package com.paynest.notifications.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paynest.common.Constants;
import com.paynest.config.PropertyReader;
import com.paynest.config.tenant.TenantContext;
import com.paynest.config.tenant.TraceContext;
import com.paynest.notifications.event.TransactionNotificationEvent;
import com.paynest.payments.entity.TransactionDetails;
import com.paynest.payments.entity.Transactions;
import com.paynest.payments.repository.TransactionDetailsRepository;
import com.paynest.users.entity.Account;
import com.paynest.users.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionNotificationEventPublisher {

    private static final Set<String> NOTIFIABLE_STATUSES = Set.of(
            Constants.TRANSACTION_INITIATED,
            Constants.TRANSACTION_SUCCESS,
            Constants.TRANSACTION_FAILED
    );

    private final ApplicationEventPublisher applicationEventPublisher;
    private final AccountRepository accountRepository;
    private final TransactionDetailsRepository transactionDetailsRepository;
    private final ObjectMapper objectMapper;
    private final PropertyReader propertyReader;

    private static final List<String> SERVICE_CHARGE_KEYS = List.of(
            "serviceChargeAmount",
            "serviceChargePaid",
            "serviceCharge",
            "serviceCharges",
            "chargeAmount",
            "feeAmount",
            "feesAmount",
            "totalFees",
            "totalFee",
            "totalCharge"
    );
    private static final List<String> COMMISSION_KEYS = List.of("commissionAmount", "commission");
    private static final List<String> DISCOUNT_KEYS = List.of("discountAmount", "discount");
    private static final List<String> TAX_KEYS = List.of("taxAmount", "tax");
    private static final List<String> CASHBACK_KEYS = List.of("cashbackAmount", "cashBackAmount", "cashback", "cashBack");
    private static final List<String> SERVICE_CHARGE_DETAIL_CODES = List.of("SC", "SERVICE_CHARGE", "SERVICECHARGE");
    private static final List<String> COMMISSION_DETAIL_CODES = List.of("COMM", "COMMISSION");
    private static final List<String> DISCOUNT_DETAIL_CODES = List.of("DISC", "DISCOUNT");
    private static final List<String> TAX_DETAIL_CODES = List.of("TAX");
    private static final List<String> CASHBACK_DETAIL_CODES = List.of("CASHBACK", "CASH_BACK", "CASHBACKAMOUNT", "CB");

    public void publish(Transactions transaction) {
        if (transaction == null) {
            log.debug("Skipping transaction notification event publish because transaction is null");
            return;
        }

        if (transaction.getTransferStatus() == null) {
            log.debug(
                    "Skipping transaction notification event publish because status is null. transactionId={}",
                    transaction.getTransactionId()
            );
            return;
        }

        if (!NOTIFIABLE_STATUSES.contains(transaction.getTransferStatus())) {
            log.debug(
                    "Skipping transaction notification event publish for non-notifiable status. transactionId={}, status={}",
                    transaction.getTransactionId(),
                    transaction.getTransferStatus()
            );
            return;
        }

        Account senderAccount = findAccount(transaction.getDebitorAccountId()).orElse(null);
        Account receiverAccount = findAccount(transaction.getCreditorAccountId()).orElse(null);
        Map<String, BigDecimal> pricingAmounts = extractPricingAmounts(transaction);

        TransactionNotificationEvent event = TransactionNotificationEvent.builder()
                .transactionId(transaction.getTransactionId())
                .tenantSchema(TenantContext.getTenant())
                .tenantId(TenantContext.getTenantId())
                .tenantTimeZone(TenantContext.getTimeZone())
                .transferStatus(transaction.getTransferStatus())
                .previousStatus(transaction.getPreviousStatus())
                .serviceCode(transaction.getServiceCode())
                .requestGateway(transaction.getRequestGateway())
                .traceId(resolveTraceId(transaction))
                .transactionValue(transaction.getTransactionValue())
                .debitorAccountId(transaction.getDebitorAccountId())
                .creditorAccountId(transaction.getCreditorAccountId())
                .debitorWalletType(transaction.getDebitorWalletType())
                .debitorCurrency(transaction.getDebitorCurrency())
                .creditorWalletType(transaction.getCreditorWalletType())
                .creditorCurrency(transaction.getCreditorCurrency())
                .senderFirstName(senderAccount == null ? null : senderAccount.getFirstName())
                .senderLastName(senderAccount == null ? null : senderAccount.getLastName())
                .receiverFirstName(receiverAccount == null ? null : receiverAccount.getFirstName())
                .receiverLastName(receiverAccount == null ? null : receiverAccount.getLastName())
                .serviceChargeAmount(pricingAmounts.get("serviceChargeAmount"))
                .commissionAmount(pricingAmounts.get("commissionAmount"))
                .discountAmount(pricingAmounts.get("discountAmount"))
                .taxAmount(pricingAmounts.get("taxAmount"))
                .cashbackAmount(pricingAmounts.get("cashbackAmount"))
                .errorCode(transaction.getErrorCode())
                .paymentReference(transaction.getPaymentReference())
                .transferOn(transaction.getTransferOn())
                .attributes(extractAttributes(transaction))
                .build();

        log.info(
                "Transaction notification event prepared. transactionId={}, status={}, previousStatus={}, serviceCode={}, traceId={}, tenantId={}, tenantSchema={}, debitorAccountId={}, creditorAccountId={}, transactionActive={}",
                event.getTransactionId(),
                event.getTransferStatus(),
                event.getPreviousStatus(),
                event.getServiceCode(),
                event.getTraceId(),
                event.getTenantId(),
                event.getTenantSchema(),
                event.getDebitorAccountId(),
                event.getCreditorAccountId(),
                TransactionSynchronizationManager.isActualTransactionActive()
        );
        publishAfterCommit(event);
    }

    private void publishAfterCommit(TransactionNotificationEvent event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            log.debug(
                    "Dispatching transaction notification event immediately because no active transaction synchronization exists. transactionId={}, status={}, transactionActive={}, synchronizationActive={}",
                    event.getTransactionId(),
                    event.getTransferStatus(),
                    TransactionSynchronizationManager.isActualTransactionActive(),
                    TransactionSynchronizationManager.isSynchronizationActive()
            );
            dispatch(event);
            return;
        }

        log.debug(
                "Registering transaction notification event for after-commit dispatch. transactionId={}, status={}",
                event.getTransactionId(),
                event.getTransferStatus()
        );
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatch(event);
            }

            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    log.info(
                            "Transaction notification event discarded because transaction did not commit. transactionId={}, eventStatus={}, completionStatus={}",
                            event.getTransactionId(),
                            event.getTransferStatus(),
                            status
                    );
                }
            }
        });
    }

    private void dispatch(TransactionNotificationEvent event) {
        log.info(
                "Dispatching transaction notification event. transactionId={}, status={}, serviceCode={}, traceId={}, tenantId={}, tenantSchema={}",
                event.getTransactionId(),
                event.getTransferStatus(),
                event.getServiceCode(),
                event.getTraceId(),
                event.getTenantId(),
                event.getTenantSchema()
        );
        applicationEventPublisher.publishEvent(event);
    }

    private String resolveTraceId(Transactions transaction) {
        String traceId = TraceContext.getTraceId();
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }
        return transaction.getTraceId();
    }

    private Optional<Account> findAccount(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return Optional.empty();
        }
        return accountRepository.findById(accountId);
    }

    private Map<String, BigDecimal> extractPricingAmounts(Transactions transaction) {
        Map<String, BigDecimal> pricingAmounts = new LinkedHashMap<>();
        List<TransactionDetails> details = transactionDetailsRepository.findByIdTransactionId(transaction.getTransactionId());
        pricingAmounts.put("serviceChargeAmount", extractDetailAmount(details, SERVICE_CHARGE_DETAIL_CODES)
                .or(() -> extractAmount(transaction, SERVICE_CHARGE_KEYS))
                .orElse(BigDecimal.ZERO));
        pricingAmounts.put("commissionAmount", extractDetailAmount(details, COMMISSION_DETAIL_CODES)
                .or(() -> extractAmount(transaction, COMMISSION_KEYS))
                .orElse(BigDecimal.ZERO));
        pricingAmounts.put("discountAmount", extractDetailAmount(details, DISCOUNT_DETAIL_CODES)
                .or(() -> extractAmount(transaction, DISCOUNT_KEYS))
                .orElse(BigDecimal.ZERO));
        pricingAmounts.put("taxAmount", extractDetailAmount(details, TAX_DETAIL_CODES)
                .or(() -> extractAmount(transaction, TAX_KEYS))
                .orElse(BigDecimal.ZERO));
        pricingAmounts.put("cashbackAmount", extractDetailAmount(details, CASHBACK_DETAIL_CODES)
                .or(() -> extractAmount(transaction, CASHBACK_KEYS))
                .orElse(BigDecimal.ZERO));
        return pricingAmounts;
    }

    private Optional<BigDecimal> extractDetailAmount(List<TransactionDetails> details, List<String> detailCodes) {
        if (details == null || details.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal total = BigDecimal.ZERO;
        boolean found = false;
        for (TransactionDetails detail : details) {
            if (matchesPricingDetail(detail, detailCodes)) {
                BigDecimal amount = detail.getApprovedValue() == null
                        ? detail.getTransactionValue()
                        : detail.getApprovedValue();
                if (amount != null) {
                    total = total.add(amount);
                    found = true;
                }
            }
        }
        return found ? Optional.of(toDisplayAmount(total)) : Optional.empty();
    }

    private boolean matchesPricingDetail(TransactionDetails detail, List<String> detailCodes) {
        return matchesAnyCode(detail.getServiceCode(), detailCodes)
                || matchesAnyCode(detail.getAttr1Name(), detailCodes)
                || matchesAnyCode(detail.getAttr1Value(), detailCodes)
                || matchesAnyCode(detail.getAttr2Name(), detailCodes)
                || matchesAnyCode(detail.getAttr2Value(), detailCodes)
                || matchesAnyCode(detail.getAttr3Name(), detailCodes)
                || matchesAnyCode(detail.getAttr3Value(), detailCodes)
                || matchesAnyCode(detail.getAttr4Name(), detailCodes)
                || matchesAnyCode(detail.getAttr4Value(), detailCodes)
                || matchesAnyCode(detail.getAttr5Name(), detailCodes)
                || matchesAnyCode(detail.getAttr5Value(), detailCodes)
                || matchesAnyCode(detail.getAttr6Name(), detailCodes)
                || matchesAnyCode(detail.getAttr6Value(), detailCodes);
    }

    private boolean matchesAnyCode(String value, List<String> expectedCodes) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalizedValue = normalizeKey(value);
        return expectedCodes.stream()
                .map(this::normalizeKey)
                .anyMatch(normalizedValue::equals);
    }

    private Optional<BigDecimal> extractAmount(Transactions transaction, List<String> keys) {
        Optional<BigDecimal> feeAmount = extractAmountFromJson(transaction.getFeesDetails(), keys);
        if (feeAmount.isPresent()) {
            return feeAmount.map(this::toDisplayAmount);
        }
        return extractAmountFromJson(transaction.getAdditionalInfo(), keys);
    }

    private Optional<BigDecimal> extractAmountFromJson(String json, List<String> keys) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        Optional<BigDecimal> directAmount = parseAmountValue(json);
        if (directAmount.isPresent() && keys.equals(SERVICE_CHARGE_KEYS)) {
            return directAmount;
        }
        try {
            Object data = objectMapper.readValue(json, Object.class);
            return findAmount(data, keys);
        } catch (JsonProcessingException ex) {
            return Optional.empty();
        }
    }

    private Optional<BigDecimal> findAmount(Object value, List<String> keys) {
        if (value instanceof Map<?, ?> map) {
            for (String key : keys) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (matchesKey(entry.getKey(), key)) {
                        Optional<BigDecimal> amount = parseAmountValue(entry.getValue());
                        if (amount.isPresent()) {
                            return amount;
                        }
                        Optional<BigDecimal> nestedAmount = findAmount(entry.getValue(), keys);
                        if (nestedAmount.isPresent()) {
                            return nestedAmount;
                        }
                    }
                }
            }
            for (Object nestedValue : map.values()) {
                Optional<BigDecimal> nestedAmount = findAmount(nestedValue, keys);
                if (nestedAmount.isPresent()) {
                    return nestedAmount;
                }
            }
        }
        if (value instanceof List<?> list) {
            for (Object nestedValue : list) {
                Optional<BigDecimal> nestedAmount = findAmount(nestedValue, keys);
                if (nestedAmount.isPresent()) {
                    return nestedAmount;
                }
            }
        }
        return Optional.empty();
    }

    private boolean matchesKey(Object actualKey, String expectedKey) {
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

    private BigDecimal toDisplayAmount(BigDecimal storedAmount) {
        if (storedAmount == null) {
            return BigDecimal.ZERO;
        }
        String configuredFactor = propertyReader.getPropertyValue("currency.factor");
        BigDecimal currencyFactor = resolveCurrencyFactor(configuredFactor);
        return storedAmount.divide(currencyFactor, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveCurrencyFactor(String configuredFactor) {
        if (configuredFactor == null || configuredFactor.isBlank()) {
            return BigDecimal.ONE;
        }
        try {
            BigDecimal currencyFactor = new BigDecimal(configuredFactor.trim());
            return currencyFactor.compareTo(BigDecimal.ZERO) <= 0 ? BigDecimal.ONE : currencyFactor;
        } catch (NumberFormatException ex) {
            return BigDecimal.ONE;
        }
    }

    private Map<String, String> extractAttributes(Transactions transaction) {
        Map<String, String> attributes = new LinkedHashMap<>();
        putIfPresent(attributes, transaction.getAttr1Name(), transaction.getAttr1Value());
        putIfPresent(attributes, transaction.getAttr2Name(), transaction.getAttr2Value());
        putIfPresent(attributes, transaction.getAttr3Name(), transaction.getAttr3Value());
        putIfPresent(attributes, transaction.getAttr4Name(), transaction.getAttr4Value());
        putIfPresent(attributes, transaction.getAttr5Name(), transaction.getAttr5Value());
        putIfPresent(attributes, transaction.getAttr6Name(), transaction.getAttr6Value());
        return attributes;
    }

    private void putIfPresent(Map<String, String> attributes, String name, String value) {
        if (name != null && !name.isBlank() && value != null) {
            attributes.put(name, value);
        }
    }
}
