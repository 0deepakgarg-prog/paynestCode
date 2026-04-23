package com.paynest.service;

import com.paynest.entity.ErrorCatalog;
import com.paynest.repository.ErrorCatalogRepository;
import com.paynest.config.repository.SupportedLanguageRepository;
import com.paynest.tenant.RequestLanguageContext;
import com.paynest.config.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
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

        Optional<ErrorCatalog> errorCatalog = findActiveErrorCatalog(errorCode, requestedLanguage);
        if (errorCatalog.isEmpty()
                && defaultLanguage != null
                && (requestedLanguage == null || !defaultLanguage.equalsIgnoreCase(requestedLanguage))) {
            errorCatalog = findActiveErrorCatalog(errorCode, defaultLanguage);
        }

        if (errorCatalog.isPresent()) {
            ErrorCatalog catalog = errorCatalog.get();
            return new ResolvedError(
                    renderMessage(catalog.getMessageTemplate(), safeParams),
                    resolveHttpStatus(catalog.getHttpStatus(), fallbackStatus)
            );
        }

        return new ResolvedError(
                renderFallbackMessage(errorCode, fallbackMessage, safeParams),
                fallbackStatus
        );
    }

    @SuppressWarnings("unchecked")
    public Optional<ErrorCatalog> findActiveErrorCatalog(String errorCode, String languageCode) {
        if (errorCode == null || errorCode.isBlank() || languageCode == null || languageCode.isBlank()) {
            return Optional.empty();
        }

        try {
            Cache cache = cacheManager.getCache("errorCatalog");
            String cacheKey = buildCacheKey(errorCode, languageCode);
            if (cache != null) {
                Object cachedValue = cache.get(cacheKey, Object.class);
                if (cachedValue instanceof Optional<?>) {
                    return (Optional<ErrorCatalog>) cachedValue;
                }
            }

            Optional<ErrorCatalog> resolvedError = errorCatalogRepository.findByErrorCodeAndLanguageCodeIgnoreCaseAndIsActiveTrue(
                    errorCode,
                    languageCode
            );

            if (cache != null) {
                cache.put(cacheKey, resolvedError);
            }

            return resolvedError;
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public void clearCache() {
        Cache cache = cacheManager.getCache("errorCatalog");
        if (cache != null) {
            cache.clear();
        }
    }

    private String getDefaultActiveLanguageCodeOrNull() {
        try {
            return supportedLanguageRepository
                    .findFirstByIsDefaultTrueAndIsActiveTrueOrderByDisplayOrderAscIdAsc()
                    .map(language -> normalizeLanguage(language.getLanguageCode()))
                    .orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private HttpStatus resolveHttpStatus(Integer configuredStatus, HttpStatus fallbackStatus) {
        if (configuredStatus == null) {
            return fallbackStatus;
        }

        try {
            return HttpStatus.valueOf(configuredStatus);
        } catch (Exception ignored) {
            return fallbackStatus;
        }
    }

    private String renderMessage(String template, Map<String, Object> params) {
        if (template == null || template.isBlank()) {
            return "Unexpected error";
        }

        String resolved = template;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            resolved = resolved.replace(
                    "{" + entry.getKey() + "}",
                    String.valueOf(entry.getValue())
            );
        }
        return resolved;
    }

    private String renderFallbackMessage(String errorCode, String fallbackMessage, Map<String, Object> params) {
        if (fallbackMessage != null && !fallbackMessage.isBlank()) {
            return renderMessage(fallbackMessage, params);
        }

        if (errorCode != null && !errorCode.isBlank()) {
            return errorCode;
        }

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
