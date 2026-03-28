package com.paynest.payments.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paynest.common.Constants;
import com.paynest.common.ErrorCodes;
import com.paynest.config.PropertyReader;
import com.paynest.config.tenant.TraceContext;
import com.paynest.payments.entity.TransactionDetails;
import com.paynest.payments.entity.TransactionDetailsId;
import com.paynest.payments.entity.Transactions;
import com.paynest.exception.ApplicationException;
import com.paynest.payments.enums.InitiatedBy;
import com.paynest.payments.repository.TransactionDetailsRepository;
import com.paynest.payments.repository.TransactionsRepository;
import com.paynest.users.entity.AccountIdentifier;
import com.paynest.users.entity.Wallet;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionsService {
    private final PropertyReader propertyReader;
    private final TransactionsRepository transactionsRepository;
    private final TransactionDetailsRepository transactionDetailsRepository;
    private final ObjectMapper objectMapper;


    @org.springframework.transaction.annotation.Transactional(propagation = Propagation.REQUIRES_NEW)
    public void generateTransactionRecord(
            String transactionId,
            BigDecimal transactionValue,
            String requestGateway,
            String serviceCode,
            AccountIdentifier debitorAccountIdentifier,
            AccountIdentifier creditorAccountIdentifier,
            Wallet debitorWallet,
            Wallet creditorWallet,
            InitiatedBy initiatedBy
    ){
        saveTransactionRecord(
                transactionId,
                transactionValue,
                requestGateway,
                serviceCode,
                debitorAccountIdentifier,
                creditorAccountIdentifier,
                debitorWallet,
                creditorWallet,
                initiatedBy,
                Constants.TRANSACTION_INITIATED,
                null
        );
    }

    @org.springframework.transaction.annotation.Transactional(propagation = Propagation.REQUIRES_NEW)
    public void generateFailedTransactionRecord(
            String transactionId,
            BigDecimal transactionValue,
            String requestGateway,
            String serviceCode,
            AccountIdentifier debitorAccountIdentifier,
            AccountIdentifier creditorAccountIdentifier,
            Wallet debitorWallet,
            Wallet creditorWallet,
            InitiatedBy initiatedBy,
            String errorCode
    ) {
        saveTransactionRecord(
                transactionId,
                transactionValue,
                requestGateway,
                serviceCode,
                debitorAccountIdentifier,
                creditorAccountIdentifier,
                debitorWallet,
                creditorWallet,
                initiatedBy,
                Constants.TRANSACTION_FAILED,
                errorCode
        );
    }

    private void saveTransactionRecord(
            String transactionId,
            BigDecimal transactionValue,
            String requestGateway,
            String serviceCode,
            AccountIdentifier debitorAccountIdentifier,
            AccountIdentifier creditorAccountIdentifier,
            Wallet debitorWallet,
            Wallet creditorWallet,
            InitiatedBy initiatedBy,
            String transferStatus,
            String errorCode
    ) {
        LocalDateTime currentDateTime = LocalDateTime.now();
        Transactions transaction = new Transactions();
        String currencyFactor = propertyReader.getPropertyValue("currency.factor");
        BigDecimal safeAmount = transactionValue == null ? BigDecimal.ZERO : transactionValue;
        BigDecimal txnAmount = safeAmount.multiply(new BigDecimal(currencyFactor));
        String debtorAccountId = resolveAccountId(debitorAccountIdentifier);
        String creditorAccountId = resolveAccountId(creditorAccountIdentifier);
        transaction.setTransactionId(transactionId);
        transaction.setTransferOn(currentDateTime);
        transaction.setTransactionValue(txnAmount);
        transaction.setTransferStatus(transferStatus);
        transaction.setRequestGateway(defaultIfBlank(requestGateway, "SYSTEM"));
        transaction.setServiceCode(defaultIfBlank(serviceCode, "UNKNOWN"));
        transaction.setTraceId(TraceContext.getTraceId());
        String actorAccountId = resolveActorAccountId(initiatedBy, debtorAccountId, creditorAccountId);
        transaction.setCreatedBy(actorAccountId);
        transaction.setModifiedBy(actorAccountId);
        transaction.setCreatedOn(currentDateTime);
        transaction.setModifiedOn(currentDateTime);
        transaction.setDebitorAccountId(debtorAccountId);
        transaction.setCreditorAccountId(creditorAccountId);
        transaction.setErrorCode(errorCode);
        transactionsRepository.save(transaction);

        TransactionDetails debitDetail = new TransactionDetails();
        debitDetail.setId(new TransactionDetailsId(transactionId, 1L));
        debitDetail.setAccountId(debtorAccountId);
        debitDetail.setUserType(resolveIdentifierType(debitorAccountIdentifier));
        debitDetail.setEntryType(Constants.TXN_TYPE_DR);
        debitDetail.setTransactionValue(txnAmount);
        debitDetail.setApprovedValue(txnAmount);
        debitDetail.setTransferOn(currentDateTime);
        debitDetail.setServiceCode(defaultIfBlank(serviceCode, "UNKNOWN"));
        debitDetail.setTransferStatus(transferStatus);
        debitDetail.setIdentifierId(resolveIdentifierValue(debitorAccountIdentifier));
        debitDetail.setWalletNumber(resolveWalletNumber(debitorWallet));
        debitDetail.setSecondIdentifierId(resolveIdentifierValue(creditorAccountIdentifier));

        TransactionDetails creditDetail = new TransactionDetails();
        creditDetail.setId(new TransactionDetailsId(transactionId, 2L));
        creditDetail.setAccountId(creditorAccountId);
        creditDetail.setUserType(resolveIdentifierType(creditorAccountIdentifier));
        creditDetail.setEntryType(Constants.TXN_TYPE_CR);
        creditDetail.setTransactionValue(txnAmount);
        creditDetail.setApprovedValue(txnAmount);
        creditDetail.setTransferOn(currentDateTime);
        creditDetail.setServiceCode(defaultIfBlank(serviceCode, "UNKNOWN"));
        creditDetail.setTransferStatus(transferStatus);
        creditDetail.setIdentifierId(resolveIdentifierValue(creditorAccountIdentifier));
        creditDetail.setWalletNumber(resolveWalletNumber(creditorWallet));
        creditDetail.setSecondIdentifierId(resolveIdentifierValue(debitorAccountIdentifier));
        transactionDetailsRepository.saveAll(List.of(debitDetail, creditDetail));
    }

    @org.springframework.transaction.annotation.Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateFailedTransactionRecord(String transactionId,String errorCode, String accountId){
        Transactions transaction = transactionsRepository.findByTransactionId(transactionId);
        if (transaction == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        transaction.setTransferStatus(Constants.TRANSACTION_FAILED);
        transaction.setModifiedOn(now);
        transaction.setErrorCode(errorCode);
        transaction.setModifiedBy(defaultIfBlank(accountId, transaction.getModifiedBy()));
        transactionsRepository.save(transaction);
        transactionDetailsRepository.updateTransferStatusByTransactionId(transactionId, Constants.TRANSACTION_FAILED);
    }

    @org.springframework.transaction.annotation.Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateMetadata(String transactionId, JSONObject newMetadata) {

        Transactions txn = transactionsRepository.findById(transactionId)
                .orElseThrow(() -> new ApplicationException(ErrorCodes.TXN_NOT_FOUND,"Transaction not found"));

        JSONObject existingJson;

        if(txn.getMetadata() == null) {
            existingJson = new JSONObject();
        } else {
            existingJson = new JSONObject(txn.getMetadata());
        }

        for(String key : newMetadata.keySet()) {
            existingJson.put(key, newMetadata.get(key));
        }

        txn.setMetadata(existingJson.toString());
        transactionsRepository.save(txn);
    }

    @org.springframework.transaction.annotation.Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateAdditionalInfo(String transactionId, JSONObject additionalInfo) {

        if (additionalInfo == null || additionalInfo.isEmpty()) {
            return;
        }

        Transactions txn = transactionsRepository.findById(transactionId)
                .orElseThrow(() -> new ApplicationException(ErrorCodes.TXN_NOT_FOUND, "Transaction not found"));

        JSONObject existingAdditional = txn.getAdditionalInfo() == null
                ? new JSONObject()
                : new JSONObject(txn.getAdditionalInfo());

        for (String key : additionalInfo.keySet()) {
            existingAdditional.put(key, additionalInfo.get(key));
        }

        txn.setAdditionalInfo(existingAdditional.toString());

        transactionsRepository.save(txn);
    }

    @org.springframework.transaction.annotation.Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updatePaymentReference(String txnId, String paymentReference) {

        if (paymentReference == null || paymentReference.isBlank()) {
            return;
        }

        transactionsRepository.updatePaymentReference(txnId, paymentReference);
    }

    @org.springframework.transaction.annotation.Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateComments(String txnId, String comments) {

        if (comments == null || comments.isBlank()) {
            return;
        }

        transactionsRepository.updateComments(txnId, comments);
    }

    @org.springframework.transaction.annotation.Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateApproveOrRejectComments(String txnId, String comments) {

        if (comments == null || comments.isBlank()) {
            return;
        }

        transactionsRepository.updateApproveOrRejectComments(txnId, comments);
    }

    private String resolveActorAccountId(
            InitiatedBy initiatedBy,
            String debtorAccountId,
            String creditorAccountId
    ) {
        if (initiatedBy == InitiatedBy.CREDITOR) {
            return creditorAccountId;
        }
        return debtorAccountId;
    }

    private String resolveAccountId(AccountIdentifier identifier) {
        if (identifier == null) {
            return "UNKNOWN";
        }
        if (identifier.getAccountId() != null && !identifier.getAccountId().isBlank()) {
            return identifier.getAccountId();
        }
        return resolveIdentifierValue(identifier);
    }

    private String resolveIdentifierType(AccountIdentifier identifier) {
        if (identifier == null || identifier.getIdentifierType() == null || identifier.getIdentifierType().isBlank()) {
            return "UNKNOWN";
        }
        return identifier.getIdentifierType();
    }

    private String resolveIdentifierValue(AccountIdentifier identifier) {
        if (identifier == null || identifier.getIdentifierValue() == null || identifier.getIdentifierValue().isBlank()) {
            return "UNKNOWN";
        }
        return identifier.getIdentifierValue();
    }

    private String resolveWalletNumber(Wallet wallet) {
        if (wallet == null || wallet.getWalletId() == null) {
            return null;
        }
        return wallet.getWalletId().toString();
    }

    private String defaultIfBlank(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }


}

