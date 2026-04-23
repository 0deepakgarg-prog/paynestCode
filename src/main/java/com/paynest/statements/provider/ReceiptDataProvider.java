package com.paynest.statements.provider;

import com.paynest.payments.entity.Transactions;
import com.paynest.statements.dto.ReceiptDocument;

public interface ReceiptDataProvider {

    boolean supports(String serviceCode);

    ReceiptDocument buildReceiptDocument(Transactions transaction, String accountId);
}
