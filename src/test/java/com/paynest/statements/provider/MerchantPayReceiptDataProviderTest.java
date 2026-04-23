package com.paynest.statements.provider;

import com.paynest.payments.entity.Transactions;
import com.paynest.statements.dto.ReceiptDocument;
import com.paynest.statements.service.ReceiptDocumentBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MerchantPayReceiptDataProviderTest {

    @Test
    void supports_shouldOnlyMatchMerchantPay() {
        MerchantPayReceiptDataProvider provider = new MerchantPayReceiptDataProvider(mock(ReceiptDocumentBuilder.class));

        assertTrue(provider.supports("MERCHANTPAY"));
        assertTrue(provider.supports("merchantpay"));
        assertFalse(provider.supports("U2U"));
    }

    @Test
    void buildReceiptDocument_shouldDelegateToReceiptDocumentBuilder() {
        ReceiptDocumentBuilder builder = mock(ReceiptDocumentBuilder.class);
        MerchantPayReceiptDataProvider provider = new MerchantPayReceiptDataProvider(builder);
        Transactions transaction = transaction("MERCHANTPAY");
        ReceiptDocument expectedDocument = new ReceiptDocument();
        when(builder.build(transaction, "sub-1")).thenReturn(expectedDocument);

        ReceiptDocument document = provider.buildReceiptDocument(transaction, "sub-1");

        assertSame(expectedDocument, document);
    }

    private Transactions transaction(String serviceCode) {
        Transactions transaction = new Transactions();
        transaction.setTransactionId("MP1");
        transaction.setServiceCode(serviceCode);
        return transaction;
    }
}
