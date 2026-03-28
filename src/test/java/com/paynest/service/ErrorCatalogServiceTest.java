package com.paynest.service;

import com.paynest.entity.ErrorCatalog;
import com.paynest.config.entity.SupportedLanguage;
import com.paynest.config.repository.SupportedLanguageRepository;
import com.paynest.config.tenant.TenantContext;
import com.paynest.repository.ErrorCatalogRepository;
import com.paynest.tenant.RequestLanguageContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ErrorCatalogServiceTest {

    @Mock
    private ErrorCatalogRepository errorCatalogRepository;

    @Mock
    private SupportedLanguageRepository supportedLanguageRepository;

    private ErrorCatalogService errorCatalogService;

    @BeforeEach
    void setUp() {
        errorCatalogService = new ErrorCatalogService(
                errorCatalogRepository,
                supportedLanguageRepository,
                new ConcurrentMapCacheManager("errorCatalog")
        );
        TenantContext.setTenant("tenant_movii");
    }

    @AfterEach
    void tearDown() {
        RequestLanguageContext.clear();
        TenantContext.clear();
    }

    @Test
    void resolve_shouldFallbackToDefaultActiveLanguageWhenRequestedLanguageHasNoCatalogRow() {
        RequestLanguageContext.setLanguage("fr");
        when(errorCatalogRepository.findByErrorCodeAndLanguageCodeIgnoreCaseAndIsActiveTrue("WALLET_NOT_FOUND", "fr"))
                .thenReturn(Optional.empty());
        when(supportedLanguageRepository.findFirstByIsDefaultTrueAndIsActiveTrueOrderByDisplayOrderAscIdAsc())
                .thenReturn(Optional.of(language("en", true)));
        when(errorCatalogRepository.findByErrorCodeAndLanguageCodeIgnoreCaseAndIsActiveTrue("WALLET_NOT_FOUND", "en"))
                .thenReturn(Optional.of(errorCatalog(
                        "WALLET_NOT_FOUND",
                        "en",
                        "{role} wallet not found for currency {currency} and walletType {walletType}",
                        400
                )));

        ErrorCatalogService.ResolvedError resolvedError = errorCatalogService.resolve(
                "WALLET_NOT_FOUND",
                Map.of(
                        "role", "DEBITOR",
                        "currency", "USD",
                        "walletType", "MAIN"
                ),
                "wallet missing",
                HttpStatus.BAD_REQUEST
        );

        assertEquals("DEBITOR wallet not found for currency USD and walletType MAIN", resolvedError.message());
        assertEquals(HttpStatus.BAD_REQUEST, resolvedError.httpStatus());
    }

    @Test
    void findActiveErrorCatalog_shouldCacheByTenantErrorCodeAndLanguage() {
        when(errorCatalogRepository.findByErrorCodeAndLanguageCodeIgnoreCaseAndIsActiveTrue("INVALID_PIN", "en"))
                .thenReturn(Optional.of(errorCatalog(
                        "INVALID_PIN",
                        "en",
                        "Invalid transaction PIN.",
                        400
                )));

        errorCatalogService.findActiveErrorCatalog("INVALID_PIN", "en");
        errorCatalogService.findActiveErrorCatalog("INVALID_PIN", "en");

        verify(errorCatalogRepository, times(1))
                .findByErrorCodeAndLanguageCodeIgnoreCaseAndIsActiveTrue("INVALID_PIN", "en");
    }

    private SupportedLanguage language(String languageCode, boolean isDefault) {
        SupportedLanguage supportedLanguage = new SupportedLanguage();
        supportedLanguage.setLanguageCode(languageCode);
        supportedLanguage.setIsDefault(isDefault);
        supportedLanguage.setIsActive(true);
        return supportedLanguage;
    }

    private ErrorCatalog errorCatalog(String errorCode, String languageCode, String template, int httpStatus) {
        ErrorCatalog errorCatalog = new ErrorCatalog();
        errorCatalog.setErrorCode(errorCode);
        errorCatalog.setLanguageCode(languageCode);
        errorCatalog.setMessageTemplate(template);
        errorCatalog.setHttpStatus(httpStatus);
        errorCatalog.setIsActive(true);
        return errorCatalog;
    }
}
