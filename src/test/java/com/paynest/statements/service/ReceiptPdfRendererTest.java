package com.paynest.statements.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paynest.statements.dto.ReceiptDocument;
import com.paynest.statements.dto.ReceiptTemplate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReceiptPdfRendererTest {

    @Test
    void render_shouldReturnPdfBytes() {
        ReceiptTemplate template = new ReceiptTemplateResolver(new ObjectMapper())
                .resolve("U2U", "en", null);
        ReceiptDocument document = new ReceiptDocument();
        document.setTransactionId("TXN1");
        document.setTransferOn("2026-04-17 16:57:13.995");
        document.setServiceCode("U2U");
        document.setServiceName("User Transfer");
        document.setStatus("Success");
        document.setTransactionAmount(new BigDecimal("500.00"));
        document.setCurrency("USD");
        document.setTraceId("trace-1");
        document.setGeneratedAt("2026-04-18 10:00:00");
        document.setCurrentYear("2026");

        byte[] pdf = new ReceiptPdfRenderer(new ObjectMapper()).render(document, template);

        assertTrue(pdf.length > 100);
        assertEquals("%PDF", new String(pdf, 0, 4));
    }

    @Test
    void render_shouldReturnPdfBytesForMerchantPayTemplate() {
        ReceiptTemplate template = new ReceiptTemplateResolver(new ObjectMapper())
                .resolve("MERCHANTPAY", "en", null);
        ReceiptDocument document = new ReceiptDocument();
        document.setTransactionId("MP1");
        document.setTransferOn("2026-04-17 16:57:13.995");
        document.setServiceCode("MERCHANTPAY");
        document.setServiceName("Merchant Payment");
        document.setStatus("Success");
        document.setTransactionAmount(new BigDecimal("500.00"));
        document.setCurrency("USD");
        document.setPaymentReference("merchant-ref-1");
        document.setTraceId("trace-1");
        document.setGeneratedAt("2026-04-18 10:00:00");
        document.setCurrentYear("2026");

        byte[] pdf = new ReceiptPdfRenderer(new ObjectMapper()).render(document, template);

        assertTrue(pdf.length > 100);
        assertEquals("%PDF", new String(pdf, 0, 4));
    }

    @Test
    void render_shouldReturnPdfBytesForAllFinancialServiceTemplates() {
        assertRendersFinancialTemplate("CASHIN");
        assertRendersFinancialTemplate("CASHOUT");
        assertRendersFinancialTemplate("BILLPAY");
    }

    private void assertRendersFinancialTemplate(String serviceCode) {
        ReceiptTemplate template = new ReceiptTemplateResolver(new ObjectMapper())
                .resolve(serviceCode, "en", null);
        ReceiptDocument document = new ReceiptDocument();
        document.setTransactionId(serviceCode + "1");
        document.setTransferOn("18-Apr-26, 12:35:30 PM");
        document.setServiceCode(serviceCode);
        document.setServiceName(serviceCode);
        document.setStatus("Success");
        document.setTransactionDirection("Debit");
        document.setTransactionAmount(new BigDecimal("500.00"));
        document.setServiceChargePaid(new BigDecimal("2.50"));
        document.setTotalAmountLabel("Total Amount Paid");
        document.setTotalAmountPaid(new BigDecimal("502.50"));
        document.setCurrency("USD");
        document.setRemarks("Receipt test");
        document.setTraceId("trace-1");
        document.setGeneratedAt("18-Apr-26, 12:35:30 PM");
        document.setCurrentYear("2026");

        byte[] pdf = new ReceiptPdfRenderer(new ObjectMapper()).render(document, template);

        assertTrue(pdf.length > 100);
        assertEquals("%PDF", new String(pdf, 0, 4));
    }
}
