package com.paynest.statements.provider;

import com.paynest.common.ErrorCodes;
import com.paynest.exception.ApplicationException;
import com.paynest.payments.entity.Transactions;
import com.paynest.statements.dto.ReceiptDocument;
import com.paynest.statements.service.ReceiptDocumentBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CashOutReceiptDataProvider implements ReceiptDataProvider {

    private static final String SERVICE_CODE = "CASHOUT";

    private final ReceiptDocumentBuilder receiptDocumentBuilder;

    @Override
    public boolean supports(String serviceCode) {
        return SERVICE_CODE.equalsIgnoreCase(serviceCode);
    }

    @Override
    public ReceiptDocument buildReceiptDocument(Transactions transaction, String accountId) {
        if (!supports(transaction.getServiceCode())) {
            throw new ApplicationException(
                    ErrorCodes.STATEMENT_SERVICE_NOT_SUPPORTED,
                    "Receipt generation is not supported for service " + transaction.getServiceCode()
            );
        }
        return receiptDocumentBuilder.build(transaction, accountId);
    }
}
