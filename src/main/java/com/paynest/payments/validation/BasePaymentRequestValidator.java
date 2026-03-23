package com.paynest.payments.validation;

import com.paynest.config.PropertyReader;
import com.paynest.exception.ApplicationException;
import com.paynest.payments.dto.Authentication;
import com.paynest.payments.dto.BasePaymentRequest;
import com.paynest.payments.dto.Identifier;
import com.paynest.payments.dto.Party;
import com.paynest.payments.dto.TransactionInfo;
import com.paynest.payments.enums.InitiatedBy;
import com.paynest.users.enums.IdentifierType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

@Component
public class BasePaymentRequestValidator {
    private final PropertyReader propertyReader;

    public BasePaymentRequestValidator(PropertyReader propertyReader) {
        this.propertyReader = propertyReader;
    }

    public void validate(BasePaymentRequest request) {

        if (request == null) {
            throw new ApplicationException(
                    "INVALID_REQUEST", "Request body cannot be null"
            );
        }

        validateOperationType(request);
        validateOperationType(request);
        InitiatedBy initiatedBy = validateInitiatedBy(request);
        validateDebtor(request.getDebitor(), initiatedBy);
        validateCreditor(request.getCreditor(), initiatedBy);
        validateTransaction(request.getTransaction());
        validateDifferentParties(request.getDebitor(), request.getCreditor());
    }

    private void validateOperationType(BasePaymentRequest request) {
        if (request.getOperationType() == null) {
            throw new ApplicationException(
                    "OPERATION_TYPE_MISSING",
                    "Operation type is required"
            );
        }

        String allowedOperationType= propertyReader.getPropertyValue("operations.allowed");

        List<String> allowedOperations = Arrays.stream(allowedOperationType.split(","))
                .map(String::trim)
                .toList();

        if (!allowedOperations.contains(request.getOperationType())) {
            throw new ApplicationException(
                    "OPERATION_NOT_ALLOWED",
                    request.getOperationType()+" Operation is not allowed"
            );
        }
    }

    private InitiatedBy validateInitiatedBy(BasePaymentRequest request) {

        if (request.getInitiatedBy() == null) {

            throw new ApplicationException(
                    "INITIATED_BY_MISSING",
                    "initiatedBy is required"
            );
        }

        return request.getInitiatedBy();
    }

    private void validateDebtor(Party debtor, InitiatedBy initiatedBy) {

        if (debtor == null) {
            throw new ApplicationException(
                    "DEBTOR_MISSING",
                    "Debtor details are required"
            );
        }

        validateIdentifier(debtor.getIdentifier());

        if (initiatedBy == InitiatedBy.DEBITOR) {
            validateAuthentication(debtor.getAuthentication());
        }
    }

    private void validateCreditor(Party creditor, InitiatedBy initiatedBy) {

        if (creditor == null) {
            throw new ApplicationException(
                    "CREDITOR_MISSING",
                    "Creditor details are required"
            );
        }

        validateIdentifier(creditor.getIdentifier());

        if (initiatedBy == InitiatedBy.CREDITOR) {
            validateAuthentication(creditor.getAuthentication());
        }
    }

    private void validateIdentifier(Identifier identifier) {

        if (identifier == null) {
            throw new ApplicationException(
                    "IDENTIFIER_MISSING",
                    "Identifier is required"
            );
        }

        if (identifier.getType() == null) {
            throw new ApplicationException(
                    "IDENTIFIER_TYPE_MISSING",
                    "Identifier type is required"
            );
        }

        if (identifier.getValue() == null || identifier.getValue().isBlank()) {

            throw new ApplicationException(
                    "IDENTIFIER_VALUE_MISSING",
                    "Identifier value is required"
            );
        }
    }

    private void validateAuthentication(Authentication authentication) {

        if (authentication == null) {

            throw new ApplicationException(
                    "AUTHENTICATION_REQUIRED",
                    "Authentication required for initiator"
            );
        }

        if (authentication.getType() == null) {

            throw new ApplicationException(
                    "AUTH_TYPE_MISSING",
                    "Authentication type is required"
            );
        }

        if (authentication.getValue() == null || authentication.getValue().isBlank()) {

            throw new ApplicationException(
                    "AUTH_VALUE_MISSING",
                    "Authentication value is required"
            );
        }
    }

    private void validateTransaction(TransactionInfo transaction) {

        if (transaction == null) {

            throw new ApplicationException(
                    "TRANSACTION_MISSING",
                    "Transaction details required"
            );
        }

        if (transaction.getAmount() == null ||
                transaction.getAmount().compareTo(BigDecimal.ZERO) <= 0) {

            throw new ApplicationException(
                    "INVALID_AMOUNT",
                    "Transaction amount must be greater than zero"
            );
        }

        if (transaction.getCurrency() == null) {

            throw new ApplicationException(
                    "CURRENCY_MISSING",
                    "Currency is required"
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
                    "SELF_TRANSFER_NOT_ALLOWED",
                    "Debtor and creditor cannot be the same"
            );
        }
    }
}

