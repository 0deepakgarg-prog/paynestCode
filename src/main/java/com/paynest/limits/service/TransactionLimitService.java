package com.paynest.limits.service;

import com.paynest.common.ErrorCodes;
import com.paynest.config.security.JWTUtils;
import com.paynest.exception.ApplicationException;
import com.paynest.limits.TransactionLimitConstants;
import com.paynest.limits.TransactionLimitErrorCode;
import com.paynest.limits.dto.request.TransactionLimitDetailRequest;
import com.paynest.limits.dto.request.TransactionLimitPeriodRequest;
import com.paynest.limits.dto.request.UpdateTransactionLimitStatusRequest;
import com.paynest.limits.dto.request.UpsertTransactionLimitProfileRequest;
import com.paynest.limits.dto.response.TransactionLimitDetailResponse;
import com.paynest.limits.dto.response.TransactionLimitPeriodResponse;
import com.paynest.limits.dto.response.TransactionLimitProfileResponse;
import com.paynest.limits.dto.response.TransactionLimitProfileSummaryResponse;
import com.paynest.limits.dto.response.TransactionLimitReferenceDataResponse;
import com.paynest.limits.dto.response.TransactionLimitUsageResponse;
import com.paynest.limits.entity.TransactionLimitProfile;
import com.paynest.limits.entity.TransactionLimitProfileDetail;
import com.paynest.limits.entity.TransactionLimitProfilePeriod;
import com.paynest.limits.entity.TransactionLimitUsage;
import com.paynest.limits.repository.TransactionLimitProfileDetailRepository;
import com.paynest.limits.repository.TransactionLimitProfilePeriodRepository;
import com.paynest.limits.repository.TransactionLimitProfileRepository;
import com.paynest.limits.repository.TransactionLimitUsageRepository;
import com.paynest.tag.dto.response.TagResponse;
import com.paynest.tag.entity.Tag;
import com.paynest.tag.entity.UserTag;
import com.paynest.tag.repository.TagRepository;
import com.paynest.tag.repository.UserTagRepository;
import com.paynest.users.entity.Account;
import com.paynest.users.entity.AccountIdentifier;
import com.paynest.users.repository.AccountIdentifierRepository;
import com.paynest.users.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransactionLimitService {

    private static final List<String> REQUEST_GATEWAYS = List.of("ALL", "MOBILE", "WEB", "API", "USSD", "PORTAL");

    private final TransactionLimitProfileRepository profileRepository;
    private final TransactionLimitProfileDetailRepository detailRepository;
    private final TransactionLimitProfilePeriodRepository periodRepository;
    private final TransactionLimitUsageRepository usageRepository;
    private final TagRepository tagRepository;
    private final AccountIdentifierRepository accountIdentifierRepository;
    private final AccountRepository accountRepository;
    private final UserTagRepository userTagRepository;
    private final TransactionLimitSubjectResolver subjectResolver;

    @Transactional
    public TransactionLimitProfileResponse createProfile(UpsertTransactionLimitProfileRequest request) {
        Tag tag = getActiveTag(request.getTagId());

        TransactionLimitProfile profile = new TransactionLimitProfile();
        applyProfileRequest(profile, request);
        profile.setCreatedBy(resolveCurrentAccountId());
        profile.setModifiedBy(resolveCurrentAccountId());

        TransactionLimitProfile savedProfile = profileRepository.save(profile);
        upsertDetails(savedProfile.getLimitId(), request.getLimitDetails());
        return getProfile(savedProfile.getLimitId());
    }

    @Transactional(readOnly = true)
    public List<TransactionLimitProfileSummaryResponse> listProfiles(
            String limitType,
            String status,
            Long tagId,
            String walletType,
            String currency,
            String subjectKey
    ) {
        List<String> limitTypes = parseCsv(limitType);
        String normalizedStatus = normalizeOptional(status);
        String normalizedWalletType = normalizeOptional(walletType);
        String normalizedCurrency = normalizeOptional(currency);
        String normalizedSubjectKey = normalizeOptional(subjectKey);

        return profileRepository.findAllByOrderByCreatedOnDesc().stream()
                .filter(profile -> limitTypes.isEmpty() || limitTypes.contains(profile.getLimitType()))
                .filter(profile -> normalizedStatus == null || normalizedStatus.equals(profile.getStatus()))
                .filter(profile -> tagId == null || tagId.equals(profile.getTagId()))
                .filter(profile -> normalizedWalletType == null || normalizedWalletType.equals(profile.getWalletType()))
                .filter(profile -> normalizedCurrency == null || normalizedCurrency.equals(profile.getCurrency()))
                .filter(profile -> normalizedSubjectKey == null || normalizedSubjectKey.equals(profile.getSubjectKey()))
                .map(profile -> new TransactionLimitProfileSummaryResponse(profile, findTag(profile.getTagId()).orElse(null)))
                .toList();
    }

    @Transactional(readOnly = true)
    public TransactionLimitProfileResponse getProfile(Long limitId) {
        TransactionLimitProfile profile = getProfileEntity(limitId);
        Tag tag = findTag(profile.getTagId()).orElse(null);

        List<TransactionLimitDetailResponse> detailResponses = detailRepository
                .findByLimitIdOrderByLimitDetailsIdAsc(profile.getLimitId())
                .stream()
                .map(detail -> new TransactionLimitDetailResponse(
                        detail,
                        periodRepository.findByLimitDetailsIdOrderByLimitPeriodIdAsc(detail.getLimitDetailsId())
                                .stream()
                                .map(TransactionLimitPeriodResponse::new)
                                .toList()
                ))
                .toList();

        return new TransactionLimitProfileResponse(profile, tag, detailResponses);
    }

    @Transactional
    public TransactionLimitProfileResponse updateProfile(Long limitId, UpsertTransactionLimitProfileRequest request) {
        TransactionLimitProfile profile = getProfileEntity(limitId);
        getActiveTag(request.getTagId());
        applyProfileRequest(profile, request);
        profile.setModifiedBy(resolveCurrentAccountId());
        profileRepository.save(profile);
        upsertDetails(profile.getLimitId(), request.getLimitDetails());
        return getProfile(profile.getLimitId());
    }

    @Transactional
    public TransactionLimitProfileResponse updateStatus(Long limitId, UpdateTransactionLimitStatusRequest request) {
        TransactionLimitProfile profile = getProfileEntity(limitId);
        profile.setStatus(normalizeAndValidateStatus(request.getStatus()));
        profile.setModifiedBy(resolveCurrentAccountId());
        profileRepository.save(profile);
        return getProfile(profile.getLimitId());
    }

    @Transactional
    public void deleteProfile(Long limitId) {
        TransactionLimitProfile profile = getProfileEntity(limitId);
        profile.setStatus(TransactionLimitConstants.STATUS_DELETED);
        profile.setModifiedBy(resolveCurrentAccountId());
        profileRepository.save(profile);

        for (TransactionLimitProfileDetail detail : detailRepository.findByLimitIdOrderByLimitDetailsIdAsc(limitId)) {
            detail.setStatus(TransactionLimitConstants.STATUS_DELETED);
            detailRepository.save(detail);
            for (TransactionLimitProfilePeriod period : periodRepository.findByLimitDetailsIdOrderByLimitPeriodIdAsc(
                    detail.getLimitDetailsId())) {
                period.setStatus(TransactionLimitConstants.STATUS_DELETED);
                periodRepository.save(period);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<TransactionLimitUsageResponse> getUtilization(
            String accountId,
            String identifierType,
            String identifierValue,
            String periodType
    ) {
        String resolvedAccountId = resolveAccountId(accountId, identifierType, identifierValue);
        String normalizedPeriodType = normalizeOptional(periodType);
        List<TransactionLimitUsage> usages = resolveSubjectBasedUtilization(resolvedAccountId, normalizedPeriodType);

        return usages.stream()
                .map(this::toUsageResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TransactionLimitReferenceDataResponse getReferenceData() {
        return TransactionLimitReferenceDataResponse.builder()
                .limitTypes(TransactionLimitConstants.ALLOWED_LIMIT_TYPES.stream().sorted().toList())
                .subjectKeys(TransactionLimitConstants.ALLOWED_SUBJECT_KEYS.stream().sorted().toList())
                .walletTypes(TransactionLimitConstants.REFERENCE_WALLET_TYPES)
                .partyTypes(TransactionLimitConstants.ALLOWED_PARTY_TYPES.stream().sorted().toList())
                .periodTypes(TransactionLimitConstants.ALLOWED_PERIOD_TYPES.stream().sorted().toList())
                .requestGateways(REQUEST_GATEWAYS)
                .statuses(TransactionLimitConstants.ALLOWED_STATUSES.stream().sorted().toList())
                .tags(tagRepository.findAll().stream()
                        .sorted(Comparator.comparing(Tag::getTagCode, Comparator.nullsLast(String::compareTo)))
                        .map(TagResponse::new)
                        .toList())
                .build();
    }

    private void applyProfileRequest(TransactionLimitProfile profile, UpsertTransactionLimitProfileRequest request) {
        String limitType = normalizeAndValidate(
                request.getLimitType(),
                TransactionLimitConstants.ALLOWED_LIMIT_TYPES,
                "Invalid limitType"
        );
        String subjectKey = normalizeAndValidate(
                request.getSubjectKey(),
                TransactionLimitConstants.ALLOWED_SUBJECT_KEYS,
                "Invalid subjectKey"
        );
        if (TransactionLimitConstants.SUBJECT_MOBILE.equals(subjectKey)) {
            subjectKey = TransactionLimitConstants.SUBJECT_MSISDN;
        }

        profile.setLimitName(request.getLimitName().trim());
        profile.setTagId(request.getTagId());
        profile.setLimitType(limitType);
        profile.setSubjectKey(subjectKey);
        profile.setDetails(request.getDetails());
        profile.setStatus(normalizeAndValidateStatus(request.getStatus()));
        profile.setWalletType(requiredUpper(request.getWalletType(), "walletType is required"));
        profile.setCurrency(requiredUpper(request.getCurrency(), "currency is required"));
        profile.setMinResidualBalance(storedAmount(request.getMinResidualBalance(), "minResidualBalance"));
        profile.setMaxBalance(storedAmount(request.getMaxBalance(), "maxBalance"));
    }

    private void upsertDetails(Long limitId, List<TransactionLimitDetailRequest> details) {
        if (details == null) {
            return;
        }

        for (TransactionLimitDetailRequest detailRequest : details) {
            TransactionLimitProfileDetail detail = detailRequest.getLimitDetailsId() == null
                    ? new TransactionLimitProfileDetail()
                    : detailRepository.findById(detailRequest.getLimitDetailsId())
                            .orElseThrow(() -> new ApplicationException(
                                    ErrorCodes.INVALID_REQUEST,
                                    "Limit detail not found"
                            ));
            if (detail.getLimitId() != null && !detail.getLimitId().equals(limitId)) {
                throw new ApplicationException(ErrorCodes.INVALID_REQUEST, "Limit detail does not belong to profile");
            }

            detail.setLimitId(limitId);
            detail.setPartyType(normalizeAndValidate(
                    detailRequest.getPartyType(),
                    TransactionLimitConstants.ALLOWED_PARTY_TYPES,
                    "Invalid partyType"
            ));
            detail.setStatus(normalizeAndValidateStatus(detailRequest.getStatus()));
            detail.setOperationType(defaultUpper(detailRequest.getOperationType(), TransactionLimitConstants.OPERATION_ALL));
            detail.setRequestGateway(defaultUpper(detailRequest.getRequestGateway(), TransactionLimitConstants.REQUEST_GATEWAY_ALL));
            detail.setMinTxnAmount(storedAmount(detailRequest.getMinTxnAmount(), "minTxnAmount"));
            detail.setMaxTxnAmount(storedAmount(detailRequest.getMaxTxnAmount(), "maxTxnAmount"));
            validateMinMax(detail.getMinTxnAmount(), detail.getMaxTxnAmount(), "minTxnAmount", "maxTxnAmount");

            TransactionLimitProfileDetail savedDetail = detailRepository.save(detail);
            upsertPeriods(savedDetail.getLimitDetailsId(), detailRequest.getPeriods());
        }
    }

    private void upsertPeriods(Long limitDetailsId, List<TransactionLimitPeriodRequest> periods) {
        if (periods == null) {
            return;
        }

        for (TransactionLimitPeriodRequest periodRequest : periods) {
            TransactionLimitProfilePeriod period = periodRequest.getLimitPeriodId() == null
                    ? new TransactionLimitProfilePeriod()
                    : periodRepository.findById(periodRequest.getLimitPeriodId())
                            .orElseThrow(() -> new ApplicationException(
                                    ErrorCodes.INVALID_REQUEST,
                                    "Limit period not found"
                            ));
            if (period.getLimitDetailsId() != null && !period.getLimitDetailsId().equals(limitDetailsId)) {
                throw new ApplicationException(ErrorCodes.INVALID_REQUEST, "Limit period does not belong to detail");
            }

            period.setLimitDetailsId(limitDetailsId);
            period.setPeriodType(normalizeAndValidate(
                    periodRequest.getPeriodType(),
                    TransactionLimitConstants.ALLOWED_PERIOD_TYPES,
                    "Invalid periodType"
            ));
            period.setMaxCount(periodRequest.getMaxCount());
            period.setMaxAmount(storedAmount(periodRequest.getMaxAmount(), "maxAmount"));
            period.setStatus(normalizeAndValidateStatus(periodRequest.getStatus()));

            if (period.getMaxCount() == null && period.getMaxAmount() == null) {
                throw new ApplicationException(ErrorCodes.INVALID_REQUEST, "maxCount or maxAmount is required");
            }

            periodRepository.save(period);
        }
    }

    private TransactionLimitUsageResponse toUsageResponse(TransactionLimitUsage usage) {
        TransactionLimitUsageResponse response = new TransactionLimitUsageResponse(usage);
        TransactionLimitProfile profile = profileRepository.findById(usage.getLimitId()).orElse(null);
        TransactionLimitProfileDetail detail = detailRepository.findById(usage.getLimitDetailsId()).orElse(null);
        TransactionLimitProfilePeriod period = periodRepository
                .findByLimitDetailsIdAndStatusOrderByLimitPeriodIdAsc(
                        usage.getLimitDetailsId(),
                        TransactionLimitConstants.STATUS_ACTIVE
                )
                .stream()
                .filter(activePeriod -> usage.getPeriodType().equalsIgnoreCase(activePeriod.getPeriodType()))
                .findFirst()
                .orElse(null);
        Tag tag = findTag(usage.getTagId()).orElse(null);

        response.setLimitName(profile == null ? null : profile.getLimitName());
        response.setWalletType(profile == null ? null : profile.getWalletType());
        response.setCurrency(profile == null ? null : profile.getCurrency());
        response.setLimitPeriodId(period == null ? null : period.getLimitPeriodId());
        response.setTagCode(tag == null ? null : tag.getTagCode());
        response.setPartyType(detail == null ? null : detail.getPartyType());
        response.setMaxCount(period == null ? null : period.getMaxCount());
        response.setMaxAmount(period == null ? null : period.getMaxAmount());

        if (TransactionLimitConstants.PARTY_CREDITOR.equalsIgnoreCase(response.getPartyType())) {
            response.setUsedCount(usage.getPayeeCount());
            response.setUsedAmount(usage.getPayeeAmount());
        } else {
            response.setUsedCount(usage.getPayerCount());
            response.setUsedAmount(usage.getPayerAmount());
        }

        if (response.getMaxCount() != null && response.getUsedCount() != null) {
            response.setRemainingCount(Math.max(response.getMaxCount() - response.getUsedCount(), 0));
        }
        if (response.getMaxAmount() != null && response.getUsedAmount() != null) {
            response.setRemainingAmount(response.getMaxAmount().subtract(response.getUsedAmount()).max(BigDecimal.ZERO));
        }
        return response;
    }

    private String resolveAccountId(String accountId, String identifierType, String identifierValue) {
        if (accountId != null && !accountId.isBlank()) {
            return accountId.trim();
        }

        if (identifierType == null || identifierType.isBlank()
                || identifierValue == null || identifierValue.isBlank()) {
            throw new ApplicationException(ErrorCodes.INVALID_REQUEST, "accountId or identifierType/identifierValue is required");
        }

        String requestedIdentifierType = normalizeOptional(identifierType);
        String normalizedIdentifierType = "MSISDN".equals(requestedIdentifierType)
                ? "MOBILE"
                : requestedIdentifierType;

        return accountIdentifierRepository
                .findByIdentifierTypeAndIdentifierValueAndStatus(
                        normalizedIdentifierType,
                        identifierValue.trim(),
                        TransactionLimitConstants.STATUS_ACTIVE
                )
                .map(AccountIdentifier::getAccountId)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCodes.ACCOUNT_IDENTIFIER_NOT_FOUND,
                        "Active account identifier not found",
                        Map.of("identifierType", normalizedIdentifierType)
                ));
    }

    private List<TransactionLimitUsage> resolveSubjectBasedUtilization(String accountId, String normalizedPeriodType) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ApplicationException(ErrorCodes.INVALID_ACCOUNT, "Account not found"));

        List<Tag> activeTags = resolveActiveTags(accountId);
        Set<Long> activeTagIds = activeTags.stream()
                .map(Tag::getTagId)
                .collect(Collectors.toSet());

        List<TransactionLimitProfile> activeProfiles = profileRepository.findAllByOrderByCreatedOnDesc().stream()
                .filter(profile -> TransactionLimitConstants.STATUS_ACTIVE.equalsIgnoreCase(profile.getStatus()))
                .filter(profile -> TransactionLimitConstants.LIMIT_TYPE_GLOBAL.equalsIgnoreCase(profile.getLimitType()))
                .filter(profile -> activeTagIds.contains(profile.getTagId()))
                .toList();
        activeProfiles = latestProfilesByWalletAndCurrency(activeProfiles);

        if (activeProfiles.isEmpty()) {
            throw new ApplicationException(
                    TransactionLimitErrorCode.LIMIT_PROFILE_NOT_FOUND,
                    Map.of("partyType", "CUSTOMER")
            );
        }

        Set<Long> activeProfileIds = activeProfiles.stream()
                .map(TransactionLimitProfile::getLimitId)
                .collect(Collectors.toSet());
        Map<Long, TransactionLimitUsage> usageById = new LinkedHashMap<>();

        for (TransactionLimitProfile profile : activeProfiles) {
            TransactionLimitSubjectResolver.ResolvedLimitSubject subject =
                    subjectResolver.resolve(account, profile.getSubjectKey(), "CUSTOMER");
            List<TransactionLimitUsage> usages = normalizedPeriodType == null
                    ? usageRepository.findBySubjectKeyAndSubjectValueOrderByLastTransactionDateDescUsageIdDesc(
                            subject.subjectKey(),
                            subject.subjectValue()
                    )
                    : usageRepository.findBySubjectKeyAndSubjectValueAndPeriodTypeOrderByLastTransactionDateDescUsageIdDesc(
                            subject.subjectKey(),
                            subject.subjectValue(),
                            normalizedPeriodType
                    );

            usages.stream()
                    .filter(usage -> activeProfileIds.contains(usage.getLimitId()))
                    .forEach(usage -> usageById.putIfAbsent(usage.getUsageId(), usage));
        }

        return new ArrayList<>(usageById.values());
    }

    private List<TransactionLimitProfile> latestProfilesByWalletAndCurrency(List<TransactionLimitProfile> profiles) {
        Map<String, TransactionLimitProfile> latestProfiles = new LinkedHashMap<>();
        profiles.stream()
                .sorted(Comparator.comparing(
                        TransactionLimitProfile::getCreatedOn,
                        Comparator.nullsFirst(Comparator.naturalOrder())
                ).reversed())
                .forEach(profile -> latestProfiles.putIfAbsent(
                        profile.getLimitType() + "|" + profile.getWalletType() + "|" + profile.getCurrency(),
                        profile
                ));
        return new ArrayList<>(latestProfiles.values());
    }

    private List<Tag> resolveActiveTags(String accountId) {
        List<Tag> activeTags = userTagRepository.findByAccountId(accountId).stream()
                .filter(userTag -> TransactionLimitConstants.STATUS_ACTIVE.equalsIgnoreCase(userTag.getStatus()))
                .map(UserTag::getTagId)
                .map(tagRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(tag -> TransactionLimitConstants.STATUS_ACTIVE.equalsIgnoreCase(tag.getStatus()))
                .toList();

        if (activeTags.isEmpty()) {
            throw new ApplicationException(
                    TransactionLimitErrorCode.LIMIT_TAG_NOT_FOUND,
                    Map.of("partyType", "CUSTOMER")
            );
        }
        return activeTags;
    }

    private TransactionLimitProfile getProfileEntity(Long limitId) {
        if (limitId == null) {
            throw new ApplicationException(ErrorCodes.INVALID_REQUEST, "limitId is required");
        }
        return profileRepository.findById(limitId)
                .orElseThrow(() -> new ApplicationException(ErrorCodes.INVALID_REQUEST, "Limit profile not found"));
    }

    private Tag getActiveTag(Long tagId) {
        return findTag(tagId)
                .filter(tag -> TransactionLimitConstants.STATUS_ACTIVE.equalsIgnoreCase(tag.getStatus()))
                .orElseThrow(() -> new ApplicationException(ErrorCodes.TAG_NOT_FOUND, "Active tag not found"));
    }

    private Optional<Tag> findTag(Long tagId) {
        if (tagId == null) {
            return Optional.empty();
        }
        return tagRepository.findById(tagId);
    }

    private String normalizeAndValidateStatus(String status) {
        if (status == null || status.isBlank()) {
            return TransactionLimitConstants.STATUS_ACTIVE;
        }
        return normalizeAndValidate(status, TransactionLimitConstants.ALLOWED_STATUSES, "Invalid status");
    }

    private String normalizeAndValidate(String value, java.util.Set<String> allowedValues, String errorMessage) {
        if (value == null || value.isBlank()) {
            throw new ApplicationException(ErrorCodes.INVALID_REQUEST, errorMessage);
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowedValues.contains(normalized)) {
            throw new ApplicationException(ErrorCodes.INVALID_REQUEST, errorMessage);
        }
        return normalized;
    }

    private String requiredUpper(String value, String errorMessage) {
        if (value == null || value.isBlank()) {
            throw new ApplicationException(ErrorCodes.INVALID_REQUEST, errorMessage);
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String defaultUpper(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private List<String> parseCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String token : value.split(",")) {
            String normalized = normalizeOptional(token);
            if (normalized != null) {
                values.add(normalized);
            }
        }
        return values;
    }

    private BigDecimal nonNegative(BigDecimal value, String fieldName) {
        if (value != null && value.compareTo(BigDecimal.ZERO) < 0) {
            throw new ApplicationException(ErrorCodes.INVALID_REQUEST, fieldName + " cannot be negative");
        }
        return value;
    }

    private BigDecimal storedAmount(BigDecimal value, String fieldName) {
        BigDecimal nonNegativeValue = nonNegative(value, fieldName);
        if (nonNegativeValue == null) {
            return null;
        }
        if (nonNegativeValue.stripTrailingZeros().scale() > 0) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_REQUEST,
                    fieldName + " must be a whole stored currency-factor amount"
            );
        }
        return nonNegativeValue.setScale(0, RoundingMode.UNNECESSARY);
    }

    private void validateMinMax(BigDecimal min, BigDecimal max, String minName, String maxName) {
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new ApplicationException(ErrorCodes.INVALID_REQUEST, minName + " cannot be greater than " + maxName);
        }
    }

    private String resolveCurrentAccountId() {
        try {
            return JWTUtils.getCurrentAccountId();
        } catch (Exception ex) {
            return null;
        }
    }
}
