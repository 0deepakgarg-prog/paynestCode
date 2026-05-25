package com.paynest.payments.service;


import com.paynest.config.tenant.TenantTime;
import com.paynest.common.Constants;
import com.paynest.config.PropertyReader;
import com.paynest.notifications.service.TransactionNotificationEventPublisher;
import com.paynest.payments.entity.TransactionDetails;
import com.paynest.payments.entity.TransactionDetailsId;
import com.paynest.payments.entity.Transactions;
import com.paynest.payments.enums.InitiatedBy;
import com.paynest.payments.repository.TransactionDetailsRepository;
import com.paynest.payments.repository.TransactionsRepository;
import com.paynest.config.tenant.TraceContext;
import com.paynest.users.entity.AccountIdentifier;
import com.paynest.users.entity.Wallet;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionsService {
    private static final int PAYMENT_REFERENCE_MAX_LENGTH = 100;
    private static final int COMMENTS_MAX_LENGTH = 300;
    private static final int OPTIONAL_JSON_MAX_LENGTH = 4000;

    private final PropertyReader propertyReader;
    private final TransactionsRepository transactionsRepository;
    private final TransactionDetailsRepository transactionDetailsRepository;
    private final TransactionNotificationEventPublisher transactionNotificationEventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
    ) {
        generateTransactionRecord(
                transactionId,
                transactionValue,
                requestGateway,
                serviceCode,
                null,
                debitorAccountIdentifier,
                creditorAccountIdentifier,
                resolveIdentifierType(debitorAccountIdentifier),
                resolveIdentifierType(creditorAccountIdentifier),
                debitorWallet,
                creditorWallet,
                initiatedBy
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
        generateTransactionRecord(
                transactionId,
                transactionValue,
                requestGateway,
                serviceCode,
                debitorAccountIdentifier,
                creditorAccountIdentifier,
                debitorWallet,
                creditorWallet,
                initiatedBy
        );
        updateFailedTransactionRecord(
                transactionId,
                errorCode,
                resolveActorAccountId(
                        initiatedBy,
                        resolveAccountId(debitorAccountIdentifier),
                        resolveAccountId(creditorAccountIdentifier)
                )
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void generateTransactionRecord(
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
            InitiatedBy initiatedBy
    ) {
        generateTransactionRecord(
                transactionId,
                transactionValue,
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
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void generateTransactionRecord(
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
            boolean paymentViaQr
    ) {
        generateTransactionRecord(
                transactionId,
                transactionValue,
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
                paymentViaQr
        );
    }

    @Transactional
    public void generateTransactionRecord(
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
            String paymentReference,
            String comments
    ){
        generateTransactionRecord(
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
                comments,
                false
        );
    }

    @Transactional
    public void generateTransactionRecord(
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
            String paymentReference,
            String comments,
            boolean paymentViaQr
    ){
        LocalDateTime currentDateTime = TenantTime.now();
        Transactions transaction = new Transactions();
        String currencyFactor = propertyReader.getPropertyValue("currency.factor");
        BigDecimal txnAmount = transactionValue
                .multiply(new BigDecimal(currencyFactor))
                .setScale(2, RoundingMode.HALF_UP);
        transaction.setTransactionId(transactionId);
        transaction.setTransferOn(currentDateTime);
        transaction.setTransactionValue(txnAmount);
        transaction.setTransferStatus(Constants.TRANSACTION_INITIATED);
        transaction.setRequestGateway(requestGateway);
        transaction.setServiceCode(serviceCode);
        transaction.setLanguage(language);
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
        transaction.setDebitorAccountId(debitorAccountIdentifier.getAccountId());
        transaction.setCreditorAccountId(creditorAccountIdentifier.getAccountId());
        transaction.setDebitorWalletType(resolveWalletType(debitorWallet));
        transaction.setDebitorCurrency(resolveWalletCurrency(debitorWallet));
        transaction.setCreditorWalletType(resolveWalletType(creditorWallet));
        transaction.setCreditorCurrency(resolveWalletCurrency(creditorWallet));
        transaction.setDebitorIdentifierValue(debitorAccountIdentifier.getIdentifierValue());
        transaction.setDebitorIdentifierType(debitorAccountIdentifier.getIdentifierType());
        transaction.setCreditorIdentifierType(creditorAccountIdentifier.getIdentifierType());
        transaction.setCreditorIdentifierValue(creditorAccountIdentifier.getIdentifierValue());
        transaction.setPaymentReference(normalizeOptionalText(paymentReference));
        transaction.setComments(normalizeOptionalText(comments));
        transaction.setPaymentViaQr(paymentViaQr);
        transactionsRepository.save(transaction);

        TransactionDetails debitDetail = new TransactionDetails();
        debitDetail.setId(new TransactionDetailsId(transactionId, 1L));
        debitDetail.setAccountId(debitorAccountIdentifier.getAccountId());
        debitDetail.setUserType(debitorAccountType);
        debitDetail.setEntryType(Constants.TXN_TYPE_DR);
        debitDetail.setTransactionType(Constants.TXN_DETAIL_TYPE_MONEY_PAID);
        debitDetail.setTransactionValue(txnAmount);
        debitDetail.setApprovedValue(txnAmount);
        debitDetail.setTransferOn(currentDateTime);
        debitDetail.setServiceCode(serviceCode);
        debitDetail.setTransferStatus(Constants.TRANSACTION_INITIATED);
        debitDetail.setIdentifierId(debitorAccountIdentifier.getIdentifierValue());
        debitDetail.setWalletNumber(debitorWallet.getWalletId().toString());
        debitDetail.setWalletType(resolveWalletType(debitorWallet));
        debitDetail.setCurrency(resolveWalletCurrency(debitorWallet));
        debitDetail.setSecondIdentifierId(creditorAccountIdentifier.getIdentifierValue());

        TransactionDetails creditDetail = new TransactionDetails();
        creditDetail.setId(new TransactionDetailsId(transactionId, 2L));
        creditDetail.setAccountId(creditorAccountIdentifier.getAccountId());
        creditDetail.setUserType(creditorAccountType);
        creditDetail.setEntryType(Constants.TXN_TYPE_CR);
        creditDetail.setTransactionType(Constants.TXN_DETAIL_TYPE_MONEY_RECEIVED);
        creditDetail.setTransactionValue(txnAmount);
        creditDetail.setApprovedValue(txnAmount);
        creditDetail.setTransferOn(currentDateTime);
        creditDetail.setServiceCode(serviceCode);
        creditDetail.setTransferStatus(Constants.TRANSACTION_INITIATED);
        creditDetail.setIdentifierId(creditorAccountIdentifier.getIdentifierValue());
        creditDetail.setWalletNumber(creditorWallet.getWalletId().toString());
        creditDetail.setWalletType(resolveWalletType(creditorWallet));
        creditDetail.setCurrency(resolveWalletCurrency(creditorWallet));
        creditDetail.setSecondIdentifierId(debitorAccountIdentifier.getIdentifierValue());
        transactionDetailsRepository.saveAll(List.of(debitDetail, creditDetail));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void generateTransactionRecord(
            String transactionId,
            BigDecimal debitorTransactionValue,
            BigDecimal creditorTransactionValue,
            String requestGateway,
            String serviceCode,
            String language,
            AccountIdentifier debitorAccountIdentifier,
            AccountIdentifier creditorAccountIdentifier,
            String debitorAccountType,
            String creditorAccountType,
            Wallet debitorWallet,
            Wallet creditorWallet,
            InitiatedBy initiatedBy
    ) {
        generateTransactionRecord(
                transactionId,
                debitorTransactionValue,
                creditorTransactionValue,
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
                false
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void generateTransactionRecord(
            String transactionId,
            BigDecimal debitorTransactionValue,
            BigDecimal creditorTransactionValue,
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
            boolean paymentViaQr
    ) {
        LocalDateTime currentDateTime = TenantTime.now();
        Transactions transaction = new Transactions();
        String currencyFactor = propertyReader.getPropertyValue("currency.factor");
        BigDecimal debitTxnAmount = debitorTransactionValue
                .multiply(new BigDecimal(currencyFactor))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal creditTxnAmount = creditorTransactionValue
                .multiply(new BigDecimal(currencyFactor))
                .setScale(2, RoundingMode.HALF_UP);
        transaction.setTransactionId(transactionId);
        transaction.setTransferOn(currentDateTime);
        transaction.setTransactionValue(debitTxnAmount);
        transaction.setTransferStatus(Constants.TRANSACTION_INITIATED);
        transaction.setRequestGateway(requestGateway);
        transaction.setServiceCode(serviceCode);
        transaction.setLanguage(language);
        transaction.setTraceId(TraceContext.getTraceId());
        if (initiatedBy == InitiatedBy.DEBITOR) {
            transaction.setCreatedBy(debitorAccountIdentifier.getAccountId());
            transaction.setModifiedBy(debitorAccountIdentifier.getAccountId());
        } else if (initiatedBy == InitiatedBy.CREDITOR) {
            transaction.setCreatedBy(creditorAccountIdentifier.getAccountId());
            transaction.setModifiedBy(creditorAccountIdentifier.getAccountId());
        }
        transaction.setCreatedOn(currentDateTime);
        transaction.setModifiedOn(currentDateTime);
        transaction.setDebitorAccountId(debitorAccountIdentifier.getAccountId());
        transaction.setCreditorAccountId(creditorAccountIdentifier.getAccountId());
        transaction.setDebitorWalletType(resolveWalletType(debitorWallet));
        transaction.setDebitorCurrency(resolveWalletCurrency(debitorWallet));
        transaction.setCreditorWalletType(resolveWalletType(creditorWallet));
        transaction.setCreditorCurrency(resolveWalletCurrency(creditorWallet));
        transaction.setDebitorIdentifierValue(debitorAccountIdentifier.getIdentifierValue());
        transaction.setDebitorIdentifierType(debitorAccountIdentifier.getIdentifierType());
        transaction.setCreditorIdentifierType(creditorAccountIdentifier.getIdentifierType());
        transaction.setCreditorIdentifierValue(creditorAccountIdentifier.getIdentifierValue());
        transaction.setPaymentViaQr(paymentViaQr);
        transactionsRepository.save(transaction);

        TransactionDetails debitDetail = new TransactionDetails();
        debitDetail.setId(new TransactionDetailsId(transactionId, 1L));
        debitDetail.setAccountId(debitorAccountIdentifier.getAccountId());
        debitDetail.setUserType(debitorAccountType);
        debitDetail.setEntryType(Constants.TXN_TYPE_DR);
        debitDetail.setTransactionValue(debitTxnAmount);
        debitDetail.setApprovedValue(debitTxnAmount);
        debitDetail.setTransferOn(currentDateTime);
        debitDetail.setServiceCode(serviceCode);
        debitDetail.setTransferStatus(Constants.TRANSACTION_INITIATED);
        debitDetail.setIdentifierId(debitorAccountIdentifier.getIdentifierValue());
        debitDetail.setWalletNumber(debitorWallet.getWalletId().toString());
        debitDetail.setWalletType(resolveWalletType(debitorWallet));
        debitDetail.setCurrency(resolveWalletCurrency(debitorWallet));
        debitDetail.setSecondIdentifierId(creditorAccountIdentifier.getIdentifierValue());

        TransactionDetails creditDetail = new TransactionDetails();
        creditDetail.setId(new TransactionDetailsId(transactionId, 2L));
        creditDetail.setAccountId(creditorAccountIdentifier.getAccountId());
        creditDetail.setUserType(creditorAccountType);
        creditDetail.setEntryType(Constants.TXN_TYPE_CR);
        creditDetail.setTransactionValue(creditTxnAmount);
        creditDetail.setApprovedValue(creditTxnAmount);
        creditDetail.setTransferOn(currentDateTime);
        creditDetail.setServiceCode(serviceCode);
        creditDetail.setTransferStatus(Constants.TRANSACTION_INITIATED);
        creditDetail.setIdentifierId(creditorAccountIdentifier.getIdentifierValue());
        creditDetail.setWalletNumber(creditorWallet.getWalletId().toString());
        creditDetail.setWalletType(resolveWalletType(creditorWallet));
        creditDetail.setCurrency(resolveWalletCurrency(creditorWallet));
        creditDetail.setSecondIdentifierId(debitorAccountIdentifier.getIdentifierValue());
        transactionDetailsRepository.saveAll(List.of(debitDetail, creditDetail));
        transactionNotificationEventPublisher.publish(transaction);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void generateCurrencyExchangeTransactionRecord(
            String transactionId,
            BigDecimal debitorTransactionValue,
            BigDecimal creditorTransactionValue,
            String requestGateway,
            String serviceCode,
            String language,
            AccountIdentifier accountIdentifier,
            String accountType,
            Wallet debitorWallet,
            Wallet systemSourceWallet,
            Wallet systemTargetWallet,
            Wallet creditorWallet,
            InitiatedBy initiatedBy,
            BigDecimal exchangeRate,
            BigDecimal bonusToMainPercentage
    ) {
        LocalDateTime currentDateTime = TenantTime.now();
        String currencyFactor = propertyReader.getPropertyValue("currency.factor");
        BigDecimal debitTxnAmount = debitorTransactionValue
                .multiply(new BigDecimal(currencyFactor))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal creditTxnAmount = creditorTransactionValue
                .multiply(new BigDecimal(currencyFactor))
                .setScale(2, RoundingMode.HALF_UP);

        Transactions transaction = new Transactions();
        transaction.setTransactionId(transactionId);
        transaction.setTransferOn(currentDateTime);
        transaction.setTransactionValue(debitTxnAmount);
        transaction.setTransferStatus(Constants.TRANSACTION_INITIATED);
        transaction.setRequestGateway(requestGateway);
        transaction.setServiceCode(serviceCode);
        transaction.setLanguage(language);
        transaction.setTraceId(TraceContext.getTraceId());
        transaction.setCreatedBy(accountIdentifier.getAccountId());
        transaction.setModifiedBy(accountIdentifier.getAccountId());
        transaction.setCreatedOn(currentDateTime);
        transaction.setModifiedOn(currentDateTime);
        transaction.setDebitorAccountId(accountIdentifier.getAccountId());
        transaction.setCreditorAccountId(accountIdentifier.getAccountId());
        transaction.setDebitorWalletType(resolveWalletType(debitorWallet));
        transaction.setDebitorCurrency(resolveWalletCurrency(debitorWallet));
        transaction.setCreditorWalletType(resolveWalletType(creditorWallet));
        transaction.setCreditorCurrency(resolveWalletCurrency(creditorWallet));
        transaction.setDebitorIdentifierValue(accountIdentifier.getIdentifierValue());
        transaction.setDebitorIdentifierType(accountIdentifier.getIdentifierType());
        transaction.setCreditorIdentifierType(accountIdentifier.getIdentifierType());
        transaction.setCreditorIdentifierValue(accountIdentifier.getIdentifierValue());
        setFxAttributes(
                transaction,
                exchangeRate,
                bonusToMainPercentage,
                debitorTransactionValue,
                creditorTransactionValue,
                resolveWalletCurrency(debitorWallet),
                resolveWalletCurrency(creditorWallet)
        );
        transactionsRepository.save(transaction);

        TransactionDetails userDebitDetail = buildTransactionDetail(
                transactionId,
                1L,
                accountIdentifier.getAccountId(),
                accountType,
                Constants.TXN_TYPE_DR,
                accountIdentifier.getIdentifierValue(),
                systemSourceWallet.getAccountId(),
                debitTxnAmount,
                currentDateTime,
                serviceCode,
                debitorWallet
        );

        TransactionDetails systemSourceCreditDetail = buildTransactionDetail(
                transactionId,
                2L,
                systemSourceWallet.getAccountId(),
                "SYSTEM",
                Constants.TXN_TYPE_CR,
                systemSourceWallet.getAccountId(),
                accountIdentifier.getIdentifierValue(),
                debitTxnAmount,
                currentDateTime,
                serviceCode,
                systemSourceWallet
        );

        TransactionDetails systemTargetDebitDetail = buildTransactionDetail(
                transactionId,
                3L,
                systemTargetWallet.getAccountId(),
                "SYSTEM",
                Constants.TXN_TYPE_DR,
                systemTargetWallet.getAccountId(),
                accountIdentifier.getIdentifierValue(),
                creditTxnAmount,
                currentDateTime,
                serviceCode,
                systemTargetWallet
        );

        TransactionDetails userCreditDetail = buildTransactionDetail(
                transactionId,
                4L,
                accountIdentifier.getAccountId(),
                accountType,
                Constants.TXN_TYPE_CR,
                accountIdentifier.getIdentifierValue(),
                systemTargetWallet.getAccountId(),
                creditTxnAmount,
                currentDateTime,
                serviceCode,
                creditorWallet
        );

        List<TransactionDetails> transactionDetails = List.of(
                userDebitDetail,
                systemSourceCreditDetail,
                systemTargetDebitDetail,
                userCreditDetail
        );
        transactionDetails.forEach(detail -> setFxAttributes(
                detail,
                exchangeRate,
                bonusToMainPercentage,
                debitorTransactionValue,
                creditorTransactionValue,
                resolveWalletCurrency(debitorWallet),
                resolveWalletCurrency(creditorWallet)
        ));
        transactionDetailsRepository.saveAll(transactionDetails);
        transactionNotificationEventPublisher.publish(transaction);
    }

    private TransactionDetails buildTransactionDetail(
            String transactionId,
            Long sequenceNumber,
            String accountId,
            String userType,
            String entryType,
            String identifierId,
            String secondIdentifierId,
            BigDecimal transactionValue,
            LocalDateTime transferOn,
            String serviceCode,
            Wallet wallet
    ) {
        TransactionDetails detail = new TransactionDetails();
        detail.setId(new TransactionDetailsId(transactionId, sequenceNumber));
        detail.setAccountId(accountId);
        detail.setUserType(userType);
        detail.setEntryType(entryType);
        detail.setTransactionType(Constants.TXN_TYPE_DR.equalsIgnoreCase(entryType)
                ? Constants.TXN_DETAIL_TYPE_MONEY_PAID
                : Constants.TXN_DETAIL_TYPE_MONEY_RECEIVED);
        detail.setTransactionValue(transactionValue);
        detail.setApprovedValue(transactionValue);
        detail.setTransferOn(transferOn);
        detail.setServiceCode(serviceCode);
        detail.setTransferStatus(Constants.TRANSACTION_INITIATED);
        detail.setIdentifierId(identifierId);
        detail.setWalletNumber(wallet.getWalletId().toString());
        detail.setWalletType(resolveWalletType(wallet));
        detail.setCurrency(resolveWalletCurrency(wallet));
        detail.setSecondIdentifierId(secondIdentifierId);
        return detail;
    }

    private void setFxAttributes(
            Transactions transaction,
            BigDecimal exchangeRate,
            BigDecimal bonusToMainPercentage,
            BigDecimal sourceAmount,
            BigDecimal targetAmount,
            String sourceCurrency,
            String targetCurrency
    ) {
        transaction.setAttr1Name("exchange_rate");
        transaction.setAttr1Value(formatDecimal(exchangeRate));
        transaction.setAttr2Name("bonus_to_main_percentage");
        transaction.setAttr2Value(formatDecimal(bonusToMainPercentage));
        transaction.setAttr3Name("source_amount");
        transaction.setAttr3Value(formatDecimal(sourceAmount));
        transaction.setAttr4Name("target_amount");
        transaction.setAttr4Value(formatDecimal(targetAmount));
        transaction.setAttr5Name("source_currency");
        transaction.setAttr5Value(sourceCurrency);
        transaction.setAttr6Name("target_currency");
        transaction.setAttr6Value(targetCurrency);
    }

    private void setFxAttributes(
            TransactionDetails detail,
            BigDecimal exchangeRate,
            BigDecimal bonusToMainPercentage,
            BigDecimal sourceAmount,
            BigDecimal targetAmount,
            String sourceCurrency,
            String targetCurrency
    ) {
        detail.setAttr1Name("exchange_rate");
        detail.setAttr1Value(formatDecimal(exchangeRate));
        detail.setAttr2Name("bonus_to_main_percentage");
        detail.setAttr2Value(formatDecimal(bonusToMainPercentage));
        detail.setAttr3Name("source_amount");
        detail.setAttr3Value(formatDecimal(sourceAmount));
        detail.setAttr4Name("target_amount");
        detail.setAttr4Value(formatDecimal(targetAmount));
        detail.setAttr5Name("source_currency");
        detail.setAttr5Value(sourceCurrency);
        detail.setAttr6Name("target_currency");
        detail.setAttr6Value(targetCurrency);
    }

    private String formatDecimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private String resolveWalletType(Wallet wallet) {
        return wallet == null ? null : wallet.getWalletType();
    }

    private String resolveWalletCurrency(Wallet wallet) {
        return wallet == null ? null : wallet.getCurrency();
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateFailedTransactionRecord(String transactionId, String errorCode, String accountId) {
        Transactions transaction = transactionsRepository.findByTransactionId(transactionId);
        List<TransactionDetails> transactionDetails = transactionDetailsRepository.findByIdTransactionId(transactionId);
        if (transaction == null) {
            log.warn(
                    "Transaction not found for failed status update. transactionId={}, errorCode={}, accountId={}",
                    transactionId,
                    errorCode,
                    accountId
            );
            return;
        }
        LocalDateTime now = TenantTime.now();
        transaction.setTransferStatus(Constants.TRANSACTION_FAILED);
        transaction.setModifiedOn(now);
        transaction.setErrorCode(errorCode);
        transaction.setModifiedBy(accountId);
        transactionsRepository.save(transaction);

        for (TransactionDetails transactionDetail : transactionDetails) {
            transactionDetail.setTransferOn(now);
            transactionDetail.setTransferStatus(Constants.TRANSACTION_FAILED);
        }
        transactionDetailsRepository.saveAll(transactionDetails);
        transactionNotificationEventPublisher.publish(transaction);
    }

    public void updateMetadata(String transactionId, JSONObject newMetadata) {

        Transactions txn = transactionsRepository.findById(transactionId).orElse(null);
        if (txn == null) {
            log.info("Transaction not found for metadata update, transactionId={}", transactionId);
            return;
        }

        JSONObject existingJson;

        if (txn.getMetadata() == null) {
            existingJson = new JSONObject();
        } else {
            existingJson = new JSONObject(txn.getMetadata());
        }

        for (String key : newMetadata.keySet()) {
            existingJson.put(key, newMetadata.get(key));
        }

        txn.setMetadata(existingJson.toString());
        transactionsRepository.save(txn);
    }

    public void updateAdditionalInfo(String transactionId, JSONObject additionalInfo) {

        if (additionalInfo == null || additionalInfo.isEmpty()) {
            return;
        }

        Transactions txn = transactionsRepository.findById(transactionId).orElse(null);
        if (txn == null) {
            log.info("Transaction not found for additional info update, transactionId={}", transactionId);
            return;
        }

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

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = Exception.class)
    public void updateOptionalTransactionFields(
            String transactionId,
            Map<String, Object> metadata,
            Map<String, Object> additionalInfo,
            String paymentReference,
            String comments) {
        try {
            Transactions txn = transactionsRepository.findFirstByTransactionId(transactionId).orElse(null);
            if (txn == null) {
                log.warn("Transaction not found for optional field update, transactionId={}", transactionId);
                return;
            }

            boolean updated = false;

            JSONObject metadataJson = toJsonObject(metadata, "metadata", transactionId);
            if (metadataJson != null) {
                String mergedMetadata = mergeJson(
                        txn.getMetadata(),
                        metadataJson,
                        "metadata",
                        transactionId
                ).toString();
                if (isWithinMaxLength(mergedMetadata, OPTIONAL_JSON_MAX_LENGTH, "metadata", transactionId)) {
                    txn.setMetadata(mergedMetadata);
                    updated = true;
                }
            }

            JSONObject additionalInfoJson = toJsonObject(additionalInfo, "additionalInfo", transactionId);
            if (additionalInfoJson != null) {
                String mergedAdditionalInfo = mergeJson(
                        txn.getAdditionalInfo(),
                        additionalInfoJson,
                        "additionalInfo",
                        transactionId
                ).toString();
                if (isWithinMaxLength(mergedAdditionalInfo, OPTIONAL_JSON_MAX_LENGTH, "additionalInfo", transactionId)) {
                    txn.setAdditionalInfo(mergedAdditionalInfo);
                    updated = true;
                }
            }

            String normalizedPaymentReference = normalizeOptionalText(
                    paymentReference,
                    PAYMENT_REFERENCE_MAX_LENGTH,
                    "paymentReference",
                    transactionId
            );
            if (normalizedPaymentReference != null) {
                txn.setPaymentReference(normalizedPaymentReference);
                updated = true;
            }

            String normalizedComments = normalizeOptionalText(
                    comments,
                    COMMENTS_MAX_LENGTH,
                    "comments",
                    transactionId
            );
            if (normalizedComments != null) {
                txn.setComments(normalizedComments);
                updated = true;
            }

            if (updated) {
                transactionsRepository.saveAndFlush(txn);
            }
        } catch (Exception ex) {
            log.warn(
                    "Optional transaction field update skipped. transactionId={}, reason={}",
                    transactionId,
                    ex.getMessage(),
                    ex
            );
        }
    }

    private JSONObject mergeJson(String existingValue, JSONObject newValue, String fieldName, String transactionId) {
        JSONObject mergedValue = new JSONObject();
        if (existingValue != null && !existingValue.isBlank()) {
            try {
                mergedValue = new JSONObject(existingValue);
            } catch (Exception ex) {
                log.warn("Existing optional JSON field is invalid and will be replaced, transactionId={}, fieldName={}",
                        transactionId, fieldName);
            }
        }

        for (String key : newValue.keySet()) {
            mergedValue.put(key, newValue.get(key));
        }
        return mergedValue;
    }

    private JSONObject toJsonObject(Map<String, Object> value, String fieldName, String transactionId) {
        if (value == null || value.isEmpty()) {
            return null;
        }

        try {
            return new JSONObject(value);
        } catch (Exception ex) {
            log.warn(
                    "Optional JSON field is invalid and will be skipped. transactionId={}, fieldName={}, reason={}",
                    transactionId,
                    fieldName,
                    ex.getMessage()
            );
            return null;
        }
    }

    private boolean isWithinMaxLength(String value, int maxLength, String fieldName, String transactionId) {
        if (value == null || value.length() <= maxLength) {
            return true;
        }

        log.warn(
                "Optional transaction field exceeds max length and will be skipped. transactionId={}, fieldName={}, length={}, maxLength={}",
                transactionId,
                fieldName,
                value.length(),
                maxLength
        );
        return false;
    }

    private String normalizeOptionalText(String value, int maxLength, String fieldName, String transactionId) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }

        if (!isWithinMaxLength(normalized, maxLength, fieldName, transactionId)) {
            return null;
        }

        return normalized;
    }

    @Transactional
    public void updateApproveOrRejectComments(String txnId, String comments) {
        if (comments == null || comments.isBlank()) {
            return;
        }

        transactionsRepository.updateApproveOrRejectComments(txnId, comments);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPaymentViaQr(String transactionId) {
        if (transactionId == null || transactionId.isBlank()) {
            return;
        }
        transactionsRepository.markPaymentViaQr(transactionId);
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

}
