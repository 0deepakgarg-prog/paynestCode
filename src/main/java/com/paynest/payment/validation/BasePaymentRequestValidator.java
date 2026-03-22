package com.paynest.payment.validation;

import com.paynest.config.PropertyReader;
import com.paynest.entity.SupportedLanguage;
import com.paynest.enums.IdentifierType;
import com.paynest.enums.InitiatedBy;
import com.paynest.exception.ApplicationException;
import com.paynest.exception.CommonErrorCode;
import com.paynest.exception.PaymentErrorCode;
import com.paynest.payment.dto.*;
import com.paynest.repository.EnumerationRepository;
import com.paynest.repository.SupportedLanguageRepository;
import com.paynest.tenant.RequestLanguageContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class BasePaymentRequestValidator {
    private final PropertyReader propertyReader;
    private final EnumerationRepository enumerationRepository;
    private final SupportedLanguageRepository supportedLanguageRepository;

    public BasePaymentRequestValidator(
            PropertyReader propertyReader,
            EnumerationRepository enumerationRepository,
            SupportedLanguageRepository supportedLanguageRepository
    ) {
        this.propertyReader = propertyReader;
        this.enumerationRepository = enumerationRepository;
        this.supportedLanguageRepository = supportedLanguageRepository;
    }

    public void validate(BasePaymentRequest request) {

        if (request == null) {
            throw new ApplicationException(PaymentErrorCode.TRANSACTION_MISSING);
        }

        validateOperationType(request);
        validateRequestGateway(request);
        validateLanguage(request);
        validateTextLengths(request);
        InitiatedBy initiatedBy = validateInitiatedBy(request);
        validateDebtor(request.getDebitor(), initiatedBy);
        validateCreditor(request.getCreditor(), initiatedBy);
        validateTransaction(request.getTransaction());
        validateDifferentParties(request.getDebitor(), request.getCreditor());
    }

    private void validateOperationType(BasePaymentRequest request) {
        if (request.getOperationType() == null) {
            throw new ApplicationException(PaymentErrorCode.OPERATION_TYPE_MISSING);
        }

        String allowedOperationType = propertyReader.getPropertyValue("operations.allowed");
        if (allowedOperationType == null || allowedOperationType.isBlank()) {
            throw new IllegalStateException("operations.allowed is not configured");
        }

        List<String> allowedOperations = Arrays.stream(allowedOperationType.split(","))
                .map(String::trim)
                .toList();

        if (!allowedOperations.contains(request.getOperationType())) {
            throw new ApplicationException(
                    PaymentErrorCode.OPERATION_NOT_ALLOWED,
                    null,
                    Map.of("operationType", request.getOperationType())
            );
        }
    }

    private void validateLanguage(BasePaymentRequest request) {
        String preferredLang = request.getPreferredLang();
        String normalizedLanguage = preferredLang == null ? null : preferredLang.trim().toLowerCase(Locale.ROOT);

        SupportedLanguage resolvedLanguage = normalizedLanguage == null || normalizedLanguage.isBlank()
                ? getDefaultActiveLanguage()
                : supportedLanguageRepository.findByLanguageCodeIgnoreCaseAndIsActiveTrue(normalizedLanguage)
                .orElseGet(this::getDefaultActiveLanguage);

        String resolvedLanguageCode = resolvedLanguage.getLanguageCode().trim().toLowerCase(Locale.ROOT);
        request.setPreferredLang(resolvedLanguageCode);
        RequestLanguageContext.setLanguage(resolvedLanguageCode);
    }

    private SupportedLanguage getDefaultActiveLanguage() {
        return supportedLanguageRepository
                .findFirstByIsDefaultTrueAndIsActiveTrueOrderByDisplayOrderAscIdAsc()
                .orElseThrow(() -> new ApplicationException(
                        CommonErrorCode.DEFAULT_LANGUAGE_NOT_CONFIGURED
                ));
    }

    private void validateRequestGateway(BasePaymentRequest request) {
        if (request.getRequestGateway() == null) {
            throw new ApplicationException(PaymentErrorCode.REQUEST_GATEWAY_MISSING);
        }
    }

    private void validateTextLengths(BasePaymentRequest request) {
        validateMaxLength(
                request.getPaymentReference(),
                100,
                PaymentErrorCode.PAYMENT_REFERENCE_TOO_LONG
        );
        validateMaxLength(
                request.getComments(),
                300,
                PaymentErrorCode.COMMENTS_TOO_LONG
        );
    }

    private void validateMaxLength(String value, int maxLength, PaymentErrorCode errorCode) {
        if (value == null) {
            return;
        }

        String normalized = value.trim();
        if (!normalized.isEmpty() && normalized.length() > maxLength) {
            throw new ApplicationException(errorCode);
        }
    }

    private InitiatedBy validateInitiatedBy(BasePaymentRequest request) {

        if (request.getInitiatedBy() == null) {

            throw new ApplicationException(PaymentErrorCode.INITIATED_BY_MISSING);
        }

        return request.getInitiatedBy();
    }

    private void validateDebtor(Party debtor, InitiatedBy initiatedBy) {

        if (debtor == null) {
            throw new ApplicationException(PaymentErrorCode.DEBTOR_MISSING);
        }

        validateIdentifier(debtor.getIdentifier());
        validateWalletType(debtor);

        if (initiatedBy == InitiatedBy.DEBITOR) {
            validateAuthentication(debtor.getAuthentication());
        }
    }

    private void validateCreditor(Party creditor, InitiatedBy initiatedBy) {

        if (creditor == null) {
            throw new ApplicationException(PaymentErrorCode.CREDITOR_MISSING);
        }

        validateIdentifier(creditor.getIdentifier());
        validateWalletType(creditor);

        if (initiatedBy == InitiatedBy.CREDITOR) {
            validateAuthentication(creditor.getAuthentication());
        }
    }

    private void validateWalletType(Party party) {
        if (party.getWalletType() == null) {
            throw new ApplicationException(PaymentErrorCode.WALLET_TYPE_MISSING);
        }
    }

    private void validateIdentifier(Identifier identifier) {

        if (identifier == null) {
            throw new ApplicationException(PaymentErrorCode.IDENTIFIER_MISSING);
        }

        if (identifier.getType() == null) {
            throw new ApplicationException(PaymentErrorCode.IDENTIFIER_TYPE_MISSING);
        }

        if (identifier.getValue() == null || identifier.getValue().isBlank()) {

            throw new ApplicationException(PaymentErrorCode.IDENTIFIER_VALUE_MISSING);
        }
    }

    private void validateAuthentication(Authentication authentication) {

        if (authentication == null) {

            throw new ApplicationException(PaymentErrorCode.AUTHENTICATION_REQUIRED);
        }

        if (authentication.getType() == null) {

            throw new ApplicationException(PaymentErrorCode.AUTH_TYPE_MISSING);
        }

        if (authentication.getValue() == null || authentication.getValue().isBlank()) {

            throw new ApplicationException(PaymentErrorCode.AUTH_VALUE_MISSING);
        }
    }

    private void validateTransaction(TransactionInfo transaction) {

        if (transaction == null) {

            throw new ApplicationException(PaymentErrorCode.TRANSACTION_MISSING);
        }

        if (transaction.getAmount() == null ||
                transaction.getAmount().compareTo(BigDecimal.ZERO) <= 0) {

            throw new ApplicationException(PaymentErrorCode.INVALID_AMOUNT);
        }

        if (transaction.getAmount().scale() > 2) {
            throw new ApplicationException(PaymentErrorCode.INVALID_AMOUNT_SCALE);
        }

        if (transaction.getCurrency() == null || transaction.getCurrency().isBlank()) {

            throw new ApplicationException(PaymentErrorCode.CURRENCY_MISSING);
        }

        String normalizedCurrency = transaction.getCurrency().trim().toUpperCase(Locale.ROOT);
        if (!enumerationRepository.existsByEnumTypeIgnoreCaseAndEnumCodeIgnoreCaseAndIsActiveTrue(
                "CURRENCY",
                normalizedCurrency
        )) {
            throw new ApplicationException(
                    PaymentErrorCode.INVALID_CURRENCY,
                    null,
                    Map.of("currency", transaction.getCurrency())
            );
        }
    }

    private void validateDifferentParties(Party debitor, Party creditor) {

        Identifier debitorIdentifier = debitor.getIdentifier();
        Identifier creditorIdentifier = creditor.getIdentifier();

        if (debitorIdentifier == null || creditorIdentifier == null) {
            return;
        }

        IdentifierType debitorType = debitorIdentifier.getType();
        IdentifierType creditorType = creditorIdentifier.getType();

        String debtorValue = debitorIdentifier.getValue();
        String creditorValue = creditorIdentifier.getValue();

        if (debitorType == creditorType &&
                debtorValue.equalsIgnoreCase(creditorValue)) {

            throw new ApplicationException(
                    PaymentErrorCode.SELF_TRANSFER_NOT_ALLOWED
            );
        }
    }
}
