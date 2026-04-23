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
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReceiptDownloadService {

    private final TransactionsRepository transactionsRepository;
    private final List<ReceiptDataProvider> receiptDataProviders;
    private final ReceiptTemplateResolver receiptTemplateResolver;
    private final ReceiptPdfRenderer receiptPdfRenderer;
    private final ReceiptFileNameService receiptFileNameService;

    @Transactional(readOnly = true)
    public ReceiptFile downloadReceipt(String transactionId, String language, String accountId) {
        if (transactionId == null || transactionId.isBlank()) {
            throw new ApplicationException(ErrorCodes.TXN_ID_MISSING, "transactionId is required");
        }

        String resolvedAccountId = resolveAccountId(accountId);
        Transactions transaction = transactionsRepository.findById(transactionId)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCodes.TXN_NOT_FOUND,
                        "Transaction not found",
                        HttpStatus.NOT_FOUND
                ));

        ReceiptDataProvider dataProvider = resolveDataProvider(transaction.getServiceCode());
        ReceiptDocument document = dataProvider.buildReceiptDocument(transaction, resolvedAccountId);
        ReceiptTemplate template = receiptTemplateResolver.resolve(
                transaction.getServiceCode(),
                language,
                document.getPreferredLanguage()
        );
        document.setLanguage(template.getLanguage());
        document.setTemplateVersion(template.getTemplateVersion());

        byte[] pdfContent = receiptPdfRenderer.render(document, template);
        return new ReceiptFile(receiptFileNameService.buildFileName(document), pdfContent);
    }

    private String resolveAccountId(String requestedAccountId) {
        String currentAccountId = JWTUtils.getCurrentAccountId();
        if (requestedAccountId == null || requestedAccountId.isBlank()
                || requestedAccountId.equalsIgnoreCase(currentAccountId)) {
            return currentAccountId;
        }

        if ("ADMIN".equalsIgnoreCase(JWTUtils.getCurrentAccountType())) {
            return requestedAccountId;
        }

        throw new ApplicationException(
                ErrorCodes.INVALID_PRIVILEGES,
                "Token does not have necessary access",
                HttpStatus.FORBIDDEN
        );
    }

    private ReceiptDataProvider resolveDataProvider(String serviceCode) {
        return receiptDataProviders.stream()
                .filter(provider -> provider.supports(serviceCode))
                .findFirst()
                .orElseThrow(() -> new ApplicationException(
                        ErrorCodes.STATEMENT_SERVICE_NOT_SUPPORTED,
                        "Receipt generation is not supported for service " + serviceCode
                ));
    }
}
