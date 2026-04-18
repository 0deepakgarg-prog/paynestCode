package com.paynest.statements.service;

import com.paynest.statements.dto.ReceiptDocument;
import org.springframework.stereotype.Service;

@Service
public class ReceiptFileNameService {

    public String buildFileName(ReceiptDocument document) {
        String accountHandle = document.getAccountMobileNumber();
        if (accountHandle == null || accountHandle.isBlank()) {
            accountHandle = document.getAccountId();
        }

        return sanitize(accountHandle) + "_"
                + sanitize(document.getTransactionId())
                + "_statement.pdf";
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
