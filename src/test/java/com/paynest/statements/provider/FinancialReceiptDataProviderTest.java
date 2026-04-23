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

class FinancialReceiptDataProviderTest {

    @Test
    void providers_shouldSupportOnlyTheirOwnService() {
        ReceiptDocumentBuilder builder = mock(ReceiptDocumentBuilder.class);
        CashInReceiptDataProvider cashInProvider = new CashInReceiptDataProvider(builder);
        CashOutReceiptDataProvider cashOutProvider = new CashOutReceiptDataProvider(builder);
        BillPayReceiptDataProvider billPayProvider = new BillPayReceiptDataProvider(builder);

        assertTrue(cashInProvider.supports("CASHIN"));
        assertFalse(cashInProvider.supports("CASHOUT"));
        assertFalse(cashInProvider.supports("BILLPAY"));

        assertFalse(cashOutProvider.supports("CASHIN"));
        assertTrue(cashOutProvider.supports("CASHOUT"));
        assertFalse(cashOutProvider.supports("BILLPAY"));

        assertFalse(billPayProvider.supports("CASHIN"));
        assertFalse(billPayProvider.supports("CASHOUT"));
        assertTrue(billPayProvider.supports("BILLPAY"));
        assertFalse(billPayProvider.supports("ACCOUNT_DELETION"));
    }

    @Test
    void cashInProvider_shouldDelegateToReceiptDocumentBuilder() {
        ReceiptDocumentBuilder builder = mock(ReceiptDocumentBuilder.class);
        CashInReceiptDataProvider provider = new CashInReceiptDataProvider(builder);
        Transactions transaction = transaction("CASHIN");
        ReceiptDocument expectedDocument = new ReceiptDocument();
        when(builder.build(transaction, "agent-1")).thenReturn(expectedDocument);

        ReceiptDocument document = provider.buildReceiptDocument(transaction, "agent-1");

        assertSame(expectedDocument, document);
    }

    private Transactions transaction(String serviceCode) {
        Transactions transaction = new Transactions();
        transaction.setTransactionId("CI1");
        transaction.setServiceCode(serviceCode);
        return transaction;
    }
}
