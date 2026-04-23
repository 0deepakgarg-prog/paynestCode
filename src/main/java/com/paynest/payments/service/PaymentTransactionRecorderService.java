package com.paynest.payments.service;

import com.paynest.payments.enums.InitiatedBy;
import com.paynest.service.TransactionsService;
import com.paynest.users.entity.AccountIdentifier;
import com.paynest.users.entity.Wallet;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
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
                initiatedBy
        );

        if (metadata != null && !metadata.isEmpty()) {
            transactionsService.updateMetadata(
                    transactionId,
                    new JSONObject(metadata)
            );
        }

        if (additionalInfo != null && !additionalInfo.isEmpty()) {
            transactionsService.updateAdditionalInfo(
                    transactionId,
                    new JSONObject(additionalInfo)
            );
        }

        transactionsService.updatePaymentReference(transactionId, paymentReference);
        transactionsService.updateComments(transactionId, comments);
    }

    public void updateTransactionAdditionalInfo(
            String transactionId,
            Map<String, Object> additionalInfo
    ) {
        if (additionalInfo == null || additionalInfo.isEmpty()) {
            return;
        }

        transactionsService.updateAdditionalInfo(
                transactionId,
                new JSONObject(additionalInfo)
        );
    }
}
