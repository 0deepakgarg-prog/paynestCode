package com.paynest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paynest.common.Constants;
import com.paynest.config.PropertyReader;
import com.paynest.entity.*;
import com.paynest.enums.InitiatedBy;
import com.paynest.exception.ApplicationException;
import com.paynest.repository.TransactionDetailsRepository;
import com.paynest.repository.TransactionsRepository;
import com.paynest.tenant.TraceContext;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionsService {
    private final PropertyReader propertyReader;
    private final TransactionsRepository transactionsRepository;
    private final TransactionDetailsRepository transactionDetailsRepository;
    private final ObjectMapper objectMapper;


    @Transactional
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
        LocalDateTime currentDateTime = LocalDateTime.now();
        Transactions transaction = new Transactions();
        String currencyFactor = propertyReader.getPropertyValue("currency.factor");
        BigDecimal txnAmount = transactionValue.multiply(new BigDecimal(currencyFactor));
        transaction.setTransactionId(transactionId);
        transaction.setTransferOn(currentDateTime);
        transaction.setTransactionValue(txnAmount);
        transaction.setTransferStatus(Constants.TRANSACTION_INITIATED);
        transaction.setRequestGateway(requestGateway);
        transaction.setServiceCode(serviceCode);
        transaction.setTraceId(TraceContext.getTraceId());
        if(initiatedBy == InitiatedBy.DEBITOR ){
            transaction.setCreatedBy(debitorAccountIdentifier.getAccountId());
            transaction.setModifiedBy(debitorAccountIdentifier.getAccountId());
        } else if (initiatedBy == InitiatedBy.CREDITOR) {
            transaction.setCreatedBy(creditorAccountIdentifier.getAccountId());
            transaction.setModifiedBy(creditorAccountIdentifier.getAccountId());
        }
        transaction.setCreatedOn(currentDateTime);
        transaction.setModifiedOn(currentDateTime);
        transaction.setDebtorAccountId(debitorAccountIdentifier.getAccountId());
        transaction.setCreditorAccountId(creditorAccountIdentifier.getAccountId());
        transactionsRepository.save(transaction);

        TransactionDetails debitDetail = new TransactionDetails();
        debitDetail.setId(new TransactionDetailsId(transactionId, 1L));
        debitDetail.setAccountId(debitorAccountIdentifier.getAccountId());
        debitDetail.setUserType(debitorAccountIdentifier.getIdentifierType());
        debitDetail.setEntryType(Constants.TXN_TYPE_DR);
        debitDetail.setTransactionValue(txnAmount);
        debitDetail.setApprovedValue(txnAmount);
        debitDetail.setTransferOn(currentDateTime);
        debitDetail.setServiceCode(serviceCode);
        debitDetail.setTransferStatus(Constants.TRANSACTION_INITIATED);
        debitDetail.setIdentifierId(debitorAccountIdentifier.getIdentifierValue());
        debitDetail.setWalletNumber(debitorWallet.getWalletId().toString());
        debitDetail.setSecondIdentifierId(creditorAccountIdentifier.getIdentifierValue());

        TransactionDetails creditDetail = new TransactionDetails();
        creditDetail.setId(new TransactionDetailsId(transactionId, 2L));
        creditDetail.setAccountId(creditorAccountIdentifier.getAccountId());
        creditDetail.setUserType(creditorAccountIdentifier.getIdentifierType());
        creditDetail.setEntryType(Constants.TXN_TYPE_CR);
        creditDetail.setTransactionValue(txnAmount);
        creditDetail.setApprovedValue(txnAmount);
        creditDetail.setTransferOn(currentDateTime);
        creditDetail.setServiceCode(serviceCode);
        creditDetail.setTransferStatus(Constants.TRANSACTION_INITIATED);
        creditDetail.setIdentifierId(creditorAccountIdentifier.getIdentifierValue());
        creditDetail.setWalletNumber(creditorWallet.getWalletId().toString());
        creditDetail.setSecondIdentifierId(debitorAccountIdentifier.getIdentifierValue());
        transactionDetailsRepository.saveAll(List.of(debitDetail, creditDetail));
    }

    public void updateFailedTransactionRecord(String transactionId,String errorCode, String accountId){
        Transactions transaction = transactionsRepository.findByTransactionId(transactionId);
        List<TransactionDetails> transactionDetail = transactionDetailsRepository.findByIdTransactionId(transactionId);
        LocalDateTime now = LocalDateTime.now();
        transaction.setTransferStatus(Constants.TRANSACTION_FAILED);
        transaction.setModifiedOn(now);
        transaction.setModifiedOn(now);
        transaction.setErrorCode(errorCode);
        transaction.setModifiedBy(accountId);
        transactionsRepository.save(transaction);
        transactionDetailsRepository.updateTransferStatusByTransactionId(transactionId, Constants.TRANSACTION_FAILED);
    }

    public void updateMetadata(String transactionId, JSONObject newMetadata) {

        Transactions txn = transactionsRepository.findById(transactionId)
                .orElseThrow(() -> new ApplicationException("TXN_NOT_FOUND","Transaction not found"));

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

    public void updateAdditionalInfo(String transactionId, JSONObject additionalInfo) {

        if (additionalInfo == null || additionalInfo.isEmpty()) {
            return;
        }

        Transactions txn = transactionsRepository.findById(transactionId)
                .orElseThrow(() -> new ApplicationException("TXN_NOT_FOUND", "Transaction not found"));

        JSONObject existingAdditional = txn.getAdditionalInfo() == null
                ? new JSONObject()
                : new JSONObject(txn.getAdditionalInfo());

        for (String key : additionalInfo.keySet()) {
            existingAdditional.put(key, additionalInfo.get(key));
        }

        txn.setAdditionalInfo(existingAdditional.toString());

        transactionsRepository.save(txn);
    }

    public void updatePaymentReference(String txnId, String paymentReference) {

        if (paymentReference == null || paymentReference.isBlank()) {
            return;
        }

        transactionsRepository.updatePaymentReference(txnId, paymentReference);
    }

    public void updateComments(String txnId, String comments) {

        if (comments == null || comments.isBlank()) {
            return;
        }

        transactionsRepository.updateComments(txnId, comments);
    }


}
