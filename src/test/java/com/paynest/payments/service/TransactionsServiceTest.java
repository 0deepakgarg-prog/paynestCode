package com.paynest.payments.service;

import com.paynest.config.PropertyReader;
import com.paynest.notifications.service.TransactionNotificationEventPublisher;
import com.paynest.payments.entity.TransactionDetails;
import com.paynest.payments.entity.Transactions;
import com.paynest.payments.enums.InitiatedBy;
import com.paynest.payments.repository.TransactionDetailsRepository;
import com.paynest.payments.repository.TransactionsRepository;
import com.paynest.config.tenant.TraceContext;
import com.paynest.users.entity.AccountIdentifier;
import com.paynest.users.entity.Wallet;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionsServiceTest {

    @Mock
    private PropertyReader propertyReader;

    @Mock
    private TransactionsRepository transactionsRepository;

    @Mock
    private TransactionDetailsRepository transactionDetailsRepository;

    @Mock
    private TransactionNotificationEventPublisher transactionNotificationEventPublisher;

    @Test
    void generateTransactionRecord_shouldPersistUserTypeFromAccountType() {
        TransactionsService transactionsService = new TransactionsService(
                propertyReader,
                transactionsRepository,
                transactionDetailsRepository,
                transactionNotificationEventPublisher
        );

        AccountIdentifier debitorIdentifier = identifier("agent-1", "MOBILE", "7777777777");
        AccountIdentifier creditorIdentifier = identifier("sub-1", "LOGINID", "subscriber-login");
        Wallet debitorWallet = wallet(101L, "agent-1");
        Wallet creditorWallet = wallet(202L, "sub-1");

        when(propertyReader.getPropertyValue("currency.factor")).thenReturn("100");

        TraceContext.setTraceId("trace-1");
        try {
            transactionsService.generateTransactionRecord(
                    "txn-1",
                    new BigDecimal("10.50"),
                    "MOBILE",
                    "CASHIN",
                    "en",
                    debitorIdentifier,
                    creditorIdentifier,
                    "AGENT",
                    "SUBSCRIBER",
                    debitorWallet,
                    creditorWallet,
                    InitiatedBy.DEBITOR
            );
        } finally {
            TraceContext.clear();
        }

        ArgumentCaptor<List<TransactionDetails>> transactionDetailsCaptor = ArgumentCaptor.forClass(List.class);
        verify(transactionDetailsRepository).saveAll(transactionDetailsCaptor.capture());

        List<TransactionDetails> transactionDetails = transactionDetailsCaptor.getValue();
        assertEquals(2, transactionDetails.size());
        assertEquals("AGENT", transactionDetails.get(0).getUserType());
        assertEquals("SUBSCRIBER", transactionDetails.get(1).getUserType());
        assertEquals("7777777777", transactionDetails.get(0).getIdentifierId());
        assertEquals("subscriber-login", transactionDetails.get(1).getIdentifierId());
        assertEquals("MAIN", transactionDetails.get(0).getWalletType());
        assertEquals("USD", transactionDetails.get(0).getCurrency());
        assertEquals("MAIN", transactionDetails.get(1).getWalletType());
        assertEquals("USD", transactionDetails.get(1).getCurrency());

        ArgumentCaptor<Transactions> transactionCaptor = ArgumentCaptor.forClass(Transactions.class);
        verify(transactionsRepository).save(transactionCaptor.capture());
        Transactions transaction = transactionCaptor.getValue();
        assertEquals("MAIN", transaction.getDebitorWalletType());
        assertEquals("USD", transaction.getDebitorCurrency());
        assertEquals("MAIN", transaction.getCreditorWalletType());
        assertEquals("USD", transaction.getCreditorCurrency());
        assertEquals(false, transaction.getPaymentViaQr());
    }

    @Test
    void markPaymentViaQr_shouldUpdateTransactionIndicator() {
        TransactionsService transactionsService = new TransactionsService(
                propertyReader,
                transactionsRepository,
                transactionDetailsRepository,
                transactionNotificationEventPublisher
        );

        transactionsService.markPaymentViaQr("txn-qr-1");

        verify(transactionsRepository).markPaymentViaQr("txn-qr-1");
    }

    @Test
    void generateTransactionRecord_shouldPersistQrPaymentIndicatorWhenRequested() {
        TransactionsService transactionsService = new TransactionsService(
                propertyReader,
                transactionsRepository,
                transactionDetailsRepository,
                transactionNotificationEventPublisher
        );

        AccountIdentifier debitorIdentifier = identifier("sub-1", "MOBILE", "7777777777");
        AccountIdentifier creditorIdentifier = identifier("merchant-1", "LOGINID", "merchant-login");
        Wallet debitorWallet = wallet(101L, "sub-1");
        Wallet creditorWallet = wallet(202L, "merchant-1");

        when(propertyReader.getPropertyValue("currency.factor")).thenReturn("100");

        transactionsService.generateTransactionRecord(
                "txn-qr-2",
                new BigDecimal("10.50"),
                "MOBILE",
                "MERCHANTPAY",
                "en",
                debitorIdentifier,
                creditorIdentifier,
                "SUBSCRIBER",
                "MERCHANT",
                debitorWallet,
                creditorWallet,
                InitiatedBy.DEBITOR,
                true
        );

        ArgumentCaptor<Transactions> transactionCaptor = ArgumentCaptor.forClass(Transactions.class);
        verify(transactionsRepository).save(transactionCaptor.capture());
        assertEquals(true, transactionCaptor.getValue().getPaymentViaQr());
    }

    @Test
    void generateTransactionRecord_shouldPersistOptionalJsonFieldsOnInitialSave() throws Exception {
        TransactionsService transactionsService = new TransactionsService(
                propertyReader,
                transactionsRepository,
                transactionDetailsRepository,
                transactionNotificationEventPublisher
        );

        AccountIdentifier debitorIdentifier = identifier("sub-1", "MOBILE", "7777777777");
        AccountIdentifier creditorIdentifier = identifier("sub-2", "MOBILE", "8888888888");
        Wallet debitorWallet = wallet(101L, "sub-1");
        Wallet creditorWallet = wallet(202L, "sub-2");
        Map<String, Object> metadata = Map.of(
                "channel", "MOBILE_APP",
                "deviceId", "ANDROID-SAMSUNG-S25"
        );
        Map<String, Object> additionalInfo = Map.of(
                "clientId", "123445",
                "clientName", "Jio"
        );

        when(propertyReader.getPropertyValue("currency.factor")).thenReturn("100");

        transactionsService.generateTransactionRecord(
                "txn-json-1",
                new BigDecimal("10.50"),
                "MOBILE",
                "U2U",
                "en",
                debitorIdentifier,
                creditorIdentifier,
                "SUBSCRIBER",
                "SUBSCRIBER",
                debitorWallet,
                creditorWallet,
                InitiatedBy.DEBITOR,
                "TXN-REF-1",
                "Dinner payment",
                false,
                metadata,
                additionalInfo
        );

        ArgumentCaptor<Transactions> transactionCaptor = ArgumentCaptor.forClass(Transactions.class);
        verify(transactionsRepository).save(transactionCaptor.capture());

        Transactions transaction = transactionCaptor.getValue();
        JSONObject persistedMetadata = new JSONObject(transaction.getMetadata());
        JSONObject persistedAdditionalInfo = new JSONObject(transaction.getAdditionalInfo());
        assertEquals("MOBILE_APP", persistedMetadata.getString("channel"));
        assertEquals("ANDROID-SAMSUNG-S25", persistedMetadata.getString("deviceId"));
        assertEquals("123445", persistedAdditionalInfo.getString("clientId"));
        assertEquals("Jio", persistedAdditionalInfo.getString("clientName"));
    }

    private AccountIdentifier identifier(String accountId, String identifierType, String identifierValue) {
        AccountIdentifier identifier = new AccountIdentifier();
        identifier.setAccountId(accountId);
        identifier.setIdentifierType(identifierType);
        identifier.setIdentifierValue(identifierValue);
        return identifier;
    }

    private Wallet wallet(Long walletId, String accountId) {
        Wallet wallet = new Wallet();
        wallet.setWalletId(walletId);
        wallet.setAccountId(accountId);
        wallet.setWalletType("MAIN");
        wallet.setCurrency("USD");
        return wallet;
    }
}
