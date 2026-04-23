package com.paynest.statements.service;

import com.paynest.statements.dto.ReceiptDocument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReceiptFileNameServiceTest {

    private final ReceiptFileNameService fileNameService = new ReceiptFileNameService();

    @Test
    void buildFileName_shouldUseMsisdnAndTransactionId() {
        ReceiptDocument document = new ReceiptDocument();
        document.setAccountMobileNumber("76008354");
        document.setTransactionId("MP260417.1657.A00877");

        assertEquals(
                "76008354_MP260417.1657.A00877_statement.pdf",
                fileNameService.buildFileName(document)
        );
    }

    @Test
    void buildFileName_shouldSanitizeUnsafeCharacters() {
        ReceiptDocument document = new ReceiptDocument();
        document.setAccountMobileNumber("+373 76008354");
        document.setTransactionId("MP/260417");

        assertEquals(
                "_373_76008354_MP_260417_statement.pdf",
                fileNameService.buildFileName(document)
        );
    }
}
