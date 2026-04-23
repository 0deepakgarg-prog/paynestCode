package com.paynest.statements.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paynest.common.ErrorCodes;
import com.paynest.exception.ApplicationException;
import com.paynest.statements.dto.ReceiptTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ReceiptTemplateResolver {

    private static final String DEFAULT_LANGUAGE = "en";
    private static final Pattern SAFE_TOKEN = Pattern.compile("[A-Za-z0-9_-]+");

    private final ObjectMapper objectMapper;

    public ReceiptTemplate resolve(String serviceCode, String requestedLanguage, String preferredLanguage) {
        String normalizedServiceCode = normalizeToken(serviceCode, "serviceCode").toLowerCase(Locale.ROOT);

        if (requestedLanguage != null && !requestedLanguage.isBlank()) {
            String normalizedLanguage = normalizeToken(requestedLanguage, "language").toLowerCase(Locale.ROOT);
            return findTemplate(normalizedServiceCode, normalizedLanguage)
                    .orElseThrow(() -> templateNotFound(serviceCode, normalizedLanguage));
        }

        if (preferredLanguage != null && !preferredLanguage.isBlank()) {
            String normalizedPreferredLanguage = normalizeToken(preferredLanguage, "preferredLanguage")
                    .toLowerCase(Locale.ROOT);
            Optional<ReceiptTemplate> preferredTemplate =
                    findTemplate(normalizedServiceCode, normalizedPreferredLanguage);
            if (preferredTemplate.isPresent()) {
                return preferredTemplate.get();
            }
        }

        return findTemplate(normalizedServiceCode, DEFAULT_LANGUAGE)
                .orElseThrow(() -> templateNotFound(serviceCode, DEFAULT_LANGUAGE));
    }

    private Optional<ReceiptTemplate> findTemplate(String serviceCode, String language) {
        ClassPathResource resource = new ClassPathResource(
                "statements/templates/" + serviceCode + "/" + language + ".json"
        );
        if (!resource.exists()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(resource.getInputStream(), ReceiptTemplate.class));
        } catch (IOException ex) {
            throw new ApplicationException(
                    ErrorCodes.STATEMENT_TEMPLATE_NOT_FOUND,
                    "Unable to load receipt template for service " + serviceCode + " and language " + language
            );
        }
    }

    private ApplicationException templateNotFound(String serviceCode, String language) {
        return new ApplicationException(
                ErrorCodes.STATEMENT_TEMPLATE_NOT_FOUND,
                "Receipt template not found for service " + serviceCode + " and language " + language
        );
    }

    private String normalizeToken(String token, String fieldName) {
        if (token == null || token.isBlank()) {
            throw new ApplicationException(ErrorCodes.INVALID_REQUEST, fieldName + " is required");
        }
        String normalizedToken = token.trim();
        if (!SAFE_TOKEN.matcher(normalizedToken).matches()) {
            throw new ApplicationException(ErrorCodes.INVALID_REQUEST, fieldName + " contains unsupported characters");
        }
        return normalizedToken;
    }
}
