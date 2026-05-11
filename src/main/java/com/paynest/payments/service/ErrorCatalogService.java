package com.paynest.payments.service;

import com.paynest.users.entity.ErrorCatalog;
import com.paynest.users.repository.ErrorCatalogRepository;
import com.paynest.config.repository.SupportedLanguageRepository;
import com.paynest.tenant.RequestLanguageContext;
import com.paynest.config.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ErrorCatalogService {

    private final ErrorCatalogRepository errorCatalogRepository;
    private final SupportedLanguageRepository supportedLanguageRepository;
    private final CacheManager cacheManager;

    public ResolvedError resolve(
            String errorCode,
            Map<String, Object> params,
            String fallbackMessage,
            HttpStatus fallbackStatus
    ) {
        Map<String, Object> safeParams = params == null ? Map.of() : params;
        String requestedLanguage = normalizeLanguage(RequestLanguageContext.getLanguage());
        String defaultLanguage = getDefaultActiveLanguageCodeOrNull();
        log.debug("Resolving error catalog. tenant={}, errorCode={}, requestedLanguage={}, defaultLanguage={}, fallbackStatus={}, hasFallbackMessage={}, paramKeys={}",
                TenantContext.getTenant(),
                errorCode,
                requestedLanguage,
                defaultLanguage,
                fallbackStatus,
                fallbackMessage != null && !fallbackMessage.isBlank(),
                safeParams.keySet());

        Optional<ErrorCatalog> errorCatalog = findActiveErrorCatalog(errorCode, requestedLanguage);
        if (errorCatalog.isEmpty()
                && defaultLanguage != null
                && (requestedLanguage == null || !defaultLanguage.equalsIgnoreCase(requestedLanguage))) {
            log.debug("Error catalog not found for requested language. Trying default language. tenant={}, errorCode={}, requestedLanguage={}, defaultLanguage={}",
                    TenantContext.getTenant(), errorCode, requestedLanguage, defaultLanguage);
            errorCatalog = findActiveErrorCatalog(errorCode, defaultLanguage);
        }

        if (errorCatalog.isPresent()) {
            ErrorCatalog catalog = errorCatalog.get();
            HttpStatus resolvedStatus = resolveHttpStatus(catalog.getHttpStatus(), fallbackStatus);
            log.debug("Resolved error catalog entry. tenant={}, errorCode={}, languageCode={}, configuredStatus={}, resolvedStatus={}",
                    TenantContext.getTenant(),
                    errorCode,
                    catalog.getLanguageCode(),
                    catalog.getHttpStatus(),
                    resolvedStatus);
            return new ResolvedError(
                    renderMessage(catalog.getMessageTemplate(), safeParams),
                    resolvedStatus
            );
        }

        log.warn("Error catalog entry not found. Using fallback. tenant={}, errorCode={}, requestedLanguage={}, defaultLanguage={}, fallbackStatus={}",
                TenantContext.getTenant(), errorCode, requestedLanguage, defaultLanguage, fallbackStatus);
        return new ResolvedError(
                renderFallbackMessage(errorCode, fallbackMessage, safeParams),
                fallbackStatus
        );
    }

    @SuppressWarnings("unchecked")
    public Optional<ErrorCatalog> findActiveErrorCatalog(String errorCode, String languageCode) {
        if (errorCode == null || errorCode.isBlank() || languageCode == null || languageCode.isBlank()) {
            log.debug("Skipping error catalog lookup due to blank input. tenant={}, errorCode={}, languageCode={}",
                    TenantContext.getTenant(), errorCode, languageCode);
            return Optional.empty();
        }

        try {
            Cache cache = cacheManager.getCache("errorCatalog");
            String cacheKey = buildCacheKey(errorCode, languageCode);
            if (cache != null) {
                log.debug("Checking error catalog cache. tenant={}, cacheKey={}", TenantContext.getTenant(), cacheKey);
                Object cachedValue = cache.get(cacheKey, Object.class);
                if (cachedValue instanceof Optional<?>) {
                    Optional<?> cachedOptional = (Optional<?>) cachedValue;
                    log.debug("Error catalog cache hit. tenant={}, cacheKey={}, present={}",
                            TenantContext.getTenant(), cacheKey, cachedOptional.isPresent());
                    return (Optional<ErrorCatalog>) cachedValue;
                }
            } else {
                log.debug("Error catalog cache is not configured. tenant={}, errorCode={}, languageCode={}",
                        TenantContext.getTenant(), errorCode, languageCode);
            }

            log.debug("Querying error catalog repository. tenant={}, errorCode={}, languageCode={}",
                    TenantContext.getTenant(), errorCode, languageCode);
            Optional<ErrorCatalog> resolvedError = errorCatalogRepository.findByErrorCodeAndLanguageCodeIgnoreCaseAndIsActiveTrue(
                    errorCode,
                    languageCode
            );
            log.debug("Error catalog repository lookup completed. tenant={}, errorCode={}, languageCode={}, present={}",
                    TenantContext.getTenant(), errorCode, languageCode, resolvedError.isPresent());

            if (cache != null) {
                cache.put(cacheKey, resolvedError);
                log.debug("Stored error catalog lookup in cache. tenant={}, cacheKey={}, present={}",
                        TenantContext.getTenant(), cacheKey, resolvedError.isPresent());
            }

            return resolvedError;
        } catch (Exception ex) {
            log.warn("Error catalog lookup failed. tenant={}, errorCode={}, languageCode={}",
                    TenantContext.getTenant(), errorCode, languageCode, ex);
            return Optional.empty();
        }
    }

    public void clearCache() {
        Cache cache = cacheManager.getCache("errorCatalog");
        if (cache != null) {
            cache.clear();
            log.info("Error catalog cache cleared. tenant={}", TenantContext.getTenant());
        } else {
            log.debug("Error catalog cache clear skipped because cache is not configured. tenant={}", TenantContext.getTenant());
        }
    }

    private String getDefaultActiveLanguageCodeOrNull() {
        try {
            String languageCode = supportedLanguageRepository
                    .findFirstByIsDefaultTrueAndIsActiveTrueOrderByDisplayOrderAscIdAsc()
                    .map(language -> normalizeLanguage(language.getLanguageCode()))
                    .orElse(null);
            log.debug("Resolved default active language for error catalog. tenant={}, defaultLanguage={}",
                    TenantContext.getTenant(), languageCode);
            return languageCode;
        } catch (Exception ex) {
            log.warn("Failed to resolve default active language for error catalog. tenant={}", TenantContext.getTenant(), ex);
            return null;
        }
    }

    private HttpStatus resolveHttpStatus(Integer configuredStatus, HttpStatus fallbackStatus) {
        if (configuredStatus == null) {
            return fallbackStatus;
        }

        try {
            return HttpStatus.valueOf(configuredStatus);
        } catch (Exception ex) {
            log.warn("Invalid configured error catalog HTTP status. tenant={}, configuredStatus={}, fallbackStatus={}",
                    TenantContext.getTenant(), configuredStatus, fallbackStatus);
            return fallbackStatus;
        }
    }

    private String renderMessage(String template, Map<String, Object> params) {
        if (template == null || template.isBlank()) {
            log.warn("Error catalog message template is blank. tenant={}", TenantContext.getTenant());
            return "Unexpected error";
        }

        String resolved = template;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            resolved = resolved.replace(
                    "{" + entry.getKey() + "}",
                    String.valueOf(entry.getValue())
            );
        }
        log.debug("Rendered error catalog message. tenant={}, paramKeys={}", TenantContext.getTenant(), params.keySet());
        return resolved;
    }

    private String renderFallbackMessage(String errorCode, String fallbackMessage, Map<String, Object> params) {
        if (fallbackMessage != null && !fallbackMessage.isBlank()) {
            log.debug("Rendering fallback error message from fallback template. tenant={}, errorCode={}, paramKeys={}",
                    TenantContext.getTenant(), errorCode, params.keySet());
            return renderMessage(fallbackMessage, params);
        }

        if (errorCode != null && !errorCode.isBlank()) {
            log.debug("Rendering fallback error message from error code. tenant={}, errorCode={}",
                    TenantContext.getTenant(), errorCode);
            return errorCode;
        }

        log.warn("Rendering generic fallback error message because error code and fallback message are blank. tenant={}",
                TenantContext.getTenant());
        return "Unexpected error";
    }

    private String normalizeLanguage(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) {
            return null;
        }

        return languageCode.trim().toLowerCase(Locale.ROOT);
    }

    private String buildCacheKey(String errorCode, String languageCode) {
        return TenantContext.getTenant() + "|" + errorCode + "|" + languageCode;
    }

    public record ResolvedError(String message, HttpStatus httpStatus) {
    }
}
