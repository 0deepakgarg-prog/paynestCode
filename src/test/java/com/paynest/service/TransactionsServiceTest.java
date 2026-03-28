package com.paynest.service;

import com.paynest.config.PropertyReader;
import com.paynest.entity.AccountIdentifier;
import com.paynest.entity.TransactionDetails;
import com.paynest.entity.Wallet;
import com.paynest.enums.InitiatedBy;
import com.paynest.repository.TransactionDetailsRepository;
import com.paynest.repository.TransactionsRepository;
import com.paynest.tenant.TraceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

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

    @Test
    void generateTransactionRecord_shouldPersistUserTypeFromAccountType() {
        TransactionsService transactionsService = new TransactionsService(
                propertyReader,
                transactionsRepository,
                transactionDetailsRepository
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
        return wallet;
    }
}
