package com.paynest.payments.service;

import com.paynest.payments.enums.InitiatedBy;
import com.paynest.payments.service.TransactionsService;
import com.paynest.users.entity.AccountIdentifier;
import com.paynest.users.entity.Wallet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PaymentTransactionRecorderService {

    private final TransactionsService transactionsService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordTransaction(
            String transactionId,
            BigDecimal transactionValue,
            String requestGateway,
            String serviceCode,
            String language,
            AccountIdentifier debitorAccountIdentifier,
            AccountIdentifier creditorAccountIdentifier,
            String debitorAccountType,
            String creditorAccountType,
            Wallet debitorWallet,
            Wallet creditorWallet,
            InitiatedBy initiatedBy,
            Map<String, Object> metadata,
            Map<String, Object> additionalInfo,
            String paymentReference,
            String comments
    ) {
        transactionsService.generateTransactionRecord(
                transactionId,
                transactionValue,
                requestGateway,
                serviceCode,
                language,
                debitorAccountIdentifier,
                creditorAccountIdentifier,
                debitorAccountType,
                creditorAccountType,
                debitorWallet,
                creditorWallet,
                initiatedBy,
                paymentReference,
                comments
        );

        transactionsService.updateOptionalTransactionFields(
                transactionId,
                metadata,
                additionalInfo,
                paymentReference,
                comments
        );
    }

    public void updateTransactionAdditionalInfo(
            String transactionId,
            Map<String, Object> additionalInfo
    ) {
        if (additionalInfo == null || additionalInfo.isEmpty()) {
            return;
        }

        transactionsService.updateOptionalTransactionFields(
                transactionId,
                null,
                additionalInfo,
                null,
                null
        );
    }
}
