package com.paynest.statements.provider;

import com.paynest.payments.entity.Transactions;
import com.paynest.statements.dto.ReceiptDocument;
import com.paynest.statements.service.ReceiptDocumentBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class U2UReceiptDataProviderTest {

    @Test
    void supports_shouldOnlyMatchU2U() {
        U2UReceiptDataProvider provider = new U2UReceiptDataProvider(mock(ReceiptDocumentBuilder.class));

        assertTrue(provider.supports("U2U"));
        assertTrue(provider.supports("u2u"));
        assertFalse(provider.supports("MERCHANTPAY"));
    }

    @Test
    void buildReceiptDocument_shouldDelegateToReceiptDocumentBuilder() {
        ReceiptDocumentBuilder builder = mock(ReceiptDocumentBuilder.class);
        U2UReceiptDataProvider provider = new U2UReceiptDataProvider(builder);
        Transactions transaction = transaction("U2U");
        ReceiptDocument expectedDocument = new ReceiptDocument();
        when(builder.build(transaction, "acc-1")).thenReturn(expectedDocument);

        ReceiptDocument document = provider.buildReceiptDocument(transaction, "acc-1");

        assertSame(expectedDocument, document);
        assertEquals("U2U", transaction.getServiceCode());
    }

    private Transactions transaction(String serviceCode) {
        Transactions transaction = new Transactions();
        transaction.setTransactionId("TXN1");
        transaction.setServiceCode(serviceCode);
        return transaction;
    }
}
