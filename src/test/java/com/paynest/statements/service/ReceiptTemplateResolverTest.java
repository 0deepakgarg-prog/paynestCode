package com.paynest.statements.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paynest.common.ErrorCodes;
import com.paynest.exception.ApplicationException;
import com.paynest.statements.dto.ReceiptTemplate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReceiptTemplateResolverTest {

    private final ReceiptTemplateResolver resolver = new ReceiptTemplateResolver(new ObjectMapper());

    @Test
    void resolve_whenExplicitLanguageTemplateExists_returnsRequestedLanguageTemplate() {
        ReceiptTemplate template = resolver.resolve("U2U", "en", "zz");

        assertEquals("U2U", template.getServiceCode());
        assertEquals("en", template.getLanguage());
    }

    @Test
    void resolve_whenExplicitLanguageTemplateMissing_throwsTemplateNotFound() {
        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> resolver.resolve("U2U", "ro", "en")
        );

        assertEquals(ErrorCodes.STATEMENT_TEMPLATE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void resolve_whenLanguageMissingAndPreferredTemplateMissing_fallsBackToEnglish() {
        ReceiptTemplate template = resolver.resolve("U2U", null, "ro");

        assertEquals("en", template.getLanguage());
    }

    @Test
    void resolve_shouldLoadMerchantPayTemplate() {
        ReceiptTemplate template = resolver.resolve("MERCHANTPAY", "en", null);

        assertEquals("MERCHANTPAY", template.getServiceCode());
        assertEquals("en", template.getLanguage());
    }

    @Test
    void resolve_shouldLoadAllFinancialServiceTemplates() {
        assertEquals("CASHIN", resolver.resolve("CASHIN", "en", null).getServiceCode());
        assertEquals("CASHOUT", resolver.resolve("CASHOUT", "en", null).getServiceCode());
        assertEquals("BILLPAY", resolver.resolve("BILLPAY", "en", null).getServiceCode());
    }
}
