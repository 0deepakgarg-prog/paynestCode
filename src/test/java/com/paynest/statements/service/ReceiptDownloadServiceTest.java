package com.paynest.statements.service;

import com.paynest.common.ErrorCodes;
import com.paynest.config.security.JWTUtils;
import com.paynest.exception.ApplicationException;
import com.paynest.payments.entity.Transactions;
import com.paynest.payments.repository.TransactionsRepository;
import com.paynest.statements.dto.ReceiptDocument;
import com.paynest.statements.dto.ReceiptFile;
import com.paynest.statements.dto.ReceiptTemplate;
import com.paynest.statements.provider.ReceiptDataProvider;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class ReceiptDownloadServiceTest {

    @Test
    void downloadReceipt_shouldBuildTemplateAndPdfForSupportedService() {
        TransactionsRepository transactionsRepository = mock(TransactionsRepository.class);
        ReceiptDataProvider dataProvider = mock(ReceiptDataProvider.class);
        ReceiptTemplateResolver templateResolver = mock(ReceiptTemplateResolver.class);
        ReceiptPdfRenderer pdfRenderer = mock(ReceiptPdfRenderer.class);
        ReceiptFileNameService fileNameService = new ReceiptFileNameService();
        ReceiptDownloadService service = new ReceiptDownloadService(
                transactionsRepository,
                List.of(dataProvider),
                templateResolver,
                pdfRenderer,
                fileNameService
        );

        Transactions transaction = new Transactions();
        transaction.setTransactionId("TXN1");
        transaction.setServiceCode("U2U");
        ReceiptDocument document = new ReceiptDocument();
        document.setTransactionId("TXN1");
        document.setAccountId("acc-1");
        document.setAccountMobileNumber("76008354");
        document.setPreferredLanguage("ro");
        ReceiptTemplate template = new ReceiptTemplate();
        template.setLanguage("en");
        template.setTemplateVersion("1.0");

        when(transactionsRepository.findById("TXN1")).thenReturn(Optional.of(transaction));
        when(dataProvider.supports("U2U")).thenReturn(true);
        when(dataProvider.buildReceiptDocument(transaction, "acc-1")).thenReturn(document);
        when(templateResolver.resolve("U2U", null, "ro")).thenReturn(template);
        when(pdfRenderer.render(document, template)).thenReturn(new byte[]{1, 2, 3});

        try (MockedStatic<JWTUtils> jwtUtils = mockStatic(JWTUtils.class)) {
            jwtUtils.when(JWTUtils::getCurrentAccountId).thenReturn("acc-1");

            ReceiptFile receipt = service.downloadReceipt("TXN1", null, null);

            assertEquals("76008354_TXN1_statement.pdf", receipt.getFileName());
            assertArrayEquals(new byte[]{1, 2, 3}, receipt.getContent());
            assertEquals("en", document.getLanguage());
            assertEquals("1.0", document.getTemplateVersion());
        }
    }

    @Test
    void downloadReceipt_whenServiceIsUnsupported_throwsStatementServiceNotSupported() {
        TransactionsRepository transactionsRepository = mock(TransactionsRepository.class);
        ReceiptDataProvider dataProvider = mock(ReceiptDataProvider.class);
        ReceiptDownloadService service = new ReceiptDownloadService(
                transactionsRepository,
                List.of(dataProvider),
                mock(ReceiptTemplateResolver.class),
                mock(ReceiptPdfRenderer.class),
                new ReceiptFileNameService()
        );

        Transactions transaction = new Transactions();
        transaction.setTransactionId("TXN1");
        transaction.setServiceCode("BILLPAY");
        when(transactionsRepository.findById("TXN1")).thenReturn(Optional.of(transaction));
        when(dataProvider.supports("BILLPAY")).thenReturn(false);

        try (MockedStatic<JWTUtils> jwtUtils = mockStatic(JWTUtils.class)) {
            jwtUtils.when(JWTUtils::getCurrentAccountId).thenReturn("acc-1");

            ApplicationException exception = assertThrows(
                    ApplicationException.class,
                    () -> service.downloadReceipt("TXN1", null, null)
            );

            assertEquals(ErrorCodes.STATEMENT_SERVICE_NOT_SUPPORTED, exception.getErrorCode());
        }
    }

    @Test
    void downloadReceipt_shouldSelectMerchantPayProvider() {
        TransactionsRepository transactionsRepository = mock(TransactionsRepository.class);
        ReceiptDataProvider u2uProvider = mock(ReceiptDataProvider.class);
        ReceiptDataProvider merchantProvider = mock(ReceiptDataProvider.class);
        ReceiptTemplateResolver templateResolver = mock(ReceiptTemplateResolver.class);
        ReceiptPdfRenderer pdfRenderer = mock(ReceiptPdfRenderer.class);
        ReceiptDownloadService service = new ReceiptDownloadService(
                transactionsRepository,
                List.of(u2uProvider, merchantProvider),
                templateResolver,
                pdfRenderer,
                new ReceiptFileNameService()
        );

        Transactions transaction = new Transactions();
        transaction.setTransactionId("MP1");
        transaction.setServiceCode("MERCHANTPAY");
        ReceiptDocument document = new ReceiptDocument();
        document.setTransactionId("MP1");
        document.setAccountId("sub-1");
        document.setAccountMobileNumber("76008354");
        ReceiptTemplate template = new ReceiptTemplate();
        template.setLanguage("en");
        template.setTemplateVersion("1.0");

        when(transactionsRepository.findById("MP1")).thenReturn(Optional.of(transaction));
        when(u2uProvider.supports("MERCHANTPAY")).thenReturn(false);
        when(merchantProvider.supports("MERCHANTPAY")).thenReturn(true);
        when(merchantProvider.buildReceiptDocument(transaction, "sub-1")).thenReturn(document);
        when(templateResolver.resolve("MERCHANTPAY", "en", null)).thenReturn(template);
        when(pdfRenderer.render(document, template)).thenReturn(new byte[]{4, 5, 6});

        try (MockedStatic<JWTUtils> jwtUtils = mockStatic(JWTUtils.class)) {
            jwtUtils.when(JWTUtils::getCurrentAccountId).thenReturn("sub-1");

            ReceiptFile receipt = service.downloadReceipt("MP1", "en", null);

            assertEquals("76008354_MP1_statement.pdf", receipt.getFileName());
            assertArrayEquals(new byte[]{4, 5, 6}, receipt.getContent());
        }
    }
}
