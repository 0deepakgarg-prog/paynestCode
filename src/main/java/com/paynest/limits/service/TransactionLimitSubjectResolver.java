package com.paynest.limits.service;

import com.paynest.exception.ApplicationException;
import com.paynest.limits.TransactionLimitConstants;
import com.paynest.limits.TransactionLimitErrorCode;
import com.paynest.users.entity.Account;
import com.paynest.users.entity.KycDocument;
import com.paynest.users.repository.KycDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TransactionLimitSubjectResolver {

    private static final String APPROVED_STATUS = "APPROVED";

    private final KycDocumentRepository kycDocumentRepository;

    public ResolvedLimitSubject resolve(Account account, String subjectKey, String partyType) {
        String normalizedSubjectKey = normalizeSubjectKey(subjectKey);
        if (normalizedSubjectKey == null) {
            throw new ApplicationException(
                    TransactionLimitErrorCode.LIMIT_SUBJECT_KEY_MISSING,
                    Map.of("limitId", "", "partyType", safe(partyType))
            );
        }

        String subjectValue = switch (normalizedSubjectKey) {
            case TransactionLimitConstants.SUBJECT_ACCOUNT_ID -> account == null ? null : account.getAccountId();
            case TransactionLimitConstants.SUBJECT_MSISDN, TransactionLimitConstants.SUBJECT_MOBILE ->
                    account == null ? null : account.getMobileNumber();
            case TransactionLimitConstants.SUBJECT_SSN -> account == null ? null : account.getSsn();
            case TransactionLimitConstants.SUBJECT_PAN -> resolveKycDocumentValue(account, "PAN");
            case TransactionLimitConstants.SUBJECT_NATIONAL_ID -> resolveKycDocumentValue(account, "NATIONAL_ID");
            case TransactionLimitConstants.SUBJECT_AADHAAR -> resolveKycDocumentValue(account, "AADHAAR");
            default -> null;
        };

        if (subjectValue == null || subjectValue.isBlank()) {
            throw new ApplicationException(
                    TransactionLimitErrorCode.LIMIT_SUBJECT_VALUE_NOT_FOUND,
                    Map.of(
                            "subjectKey", normalizedSubjectKey,
                            "partyType", safe(partyType)
                    )
            );
        }

        String normalizedSubjectValue = normalizeSubjectValue(normalizedSubjectKey, subjectValue);
        return new ResolvedLimitSubject(
                normalizedSubjectKey,
                normalizedSubjectValue
        );
    }

    private String resolveKycDocumentValue(Account account, String documentType) {
        if (account == null || account.getAccountId() == null || account.getAccountId().isBlank()) {
            return null;
        }

        return kycDocumentRepository.findByAccountId(account.getAccountId()).stream()
                .filter(document -> Boolean.TRUE.equals(document.getIsActive()))
                .filter(document -> documentType.equalsIgnoreCase(document.getDocumentType()))
                .filter(document -> APPROVED_STATUS.equalsIgnoreCase(document.getVerificationStatus()))
                .findFirst()
                .map(KycDocument::getDocumentNumber)
                .orElse(null);
    }

    private String normalizeSubjectKey(String subjectKey) {
        if (subjectKey == null || subjectKey.isBlank()) {
            return null;
        }
        String normalized = subjectKey.trim().toUpperCase(Locale.ROOT);
        if (TransactionLimitConstants.SUBJECT_MOBILE.equals(normalized)) {
            return TransactionLimitConstants.SUBJECT_MSISDN;
        }
        return normalized;
    }

    private String normalizeSubjectValue(String subjectKey, String subjectValue) {
        String trimmed = subjectValue.trim();
        if (TransactionLimitConstants.SUBJECT_MSISDN.equals(subjectKey)) {
            return trimmed.replace(" ", "");
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public record ResolvedLimitSubject(
            String subjectKey,
            String subjectValue
    ) {
    }
}
