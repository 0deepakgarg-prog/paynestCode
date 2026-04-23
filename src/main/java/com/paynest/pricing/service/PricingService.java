package com.paynest.pricing.service;

import com.paynest.common.ErrorCodes;
import com.paynest.config.security.JWTUtils;
import com.paynest.exception.ApplicationException;
import com.paynest.payments.dto.U2UPaymentRequest;
import com.paynest.payments.service.PricingCalculator;
import com.paynest.pricing.dto.request.CreatePricingRuleRequest;
import com.paynest.pricing.dto.response.PricingComputationResponse;
import com.paynest.pricing.dto.request.UpdatePricingStatusRequest;
import com.paynest.pricing.dto.response.PricingRuleResponse;
import com.paynest.pricing.entity.PricingRule;
import com.paynest.pricing.repository.PricingRuleRepository;
import com.paynest.tag.entity.Tag;
import com.paynest.tag.entity.UserTag;
import com.paynest.tag.repository.TagRepository;
import com.paynest.tag.repository.UserTagRepository;
import com.paynest.users.entity.AccountIdentifier;
import com.paynest.users.repository.AccountIdentifierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class PricingService {

    private static final List<String> ALLOWED_RULE_TYPES = List.of(
            "SERVICE_CHARGE", "COMMISSION", "DISCOUNT", "CASHBACK"
    );
    private static final List<String> ALLOWED_PRICING_TYPES = List.of(
            "STATIC", "CAMPAIGN"
    );
    private static final List<String> SERVICE_CHARGE_RULE_TYPES = List.of("SERVICE_CHARGE", "CASHBACK");
    private static final List<String> COMMISSION_RULE_TYPES = List.of("COMMISSION");
    private static final List<String> DISCOUNT_RULE_TYPES = List.of("DISCOUNT");
    private static final List<String> CASHBACK_RULE_TYPES = List.of("CASHBACK");

    private static final List<String> ALLOWED_PAYERS = List.of(
            "SENDER", "RECEIVER", "SYSTEM", "SPLIT"
    );

    private static final List<String> ALLOWED_STATUSES = List.of(
            "ACTIVE", "INACTIVE"
    );

    private final PricingRuleRepository pricingRuleRepository;
    private final PricingCalculator pricingCalculator;
    private final AccountIdentifierRepository accountIdentifierRepository;
    private final UserTagRepository userTagRepository;
    private final TagRepository tagRepository;

    @Transactional
    public PricingRuleResponse addPricingRule(CreatePricingRuleRequest request) {
        String normalizedRuleType = normalizeAndValidateRuleType(request.getRuleType());
        String normalizedPricingType = normalizeAndValidatePricingType(request.getPricingType());
        String normalizedPayer = normalizeAndValidatePayer(request.getPayer());
        String normalizedPayBy = normalizeAndValidatePayBy(request.getPayBy(), normalizedRuleType);
        String normalizedSenderTagKey = normalizeAndValidateTagKey(request.getSenderTagKey(), "Sender tag key");
        String normalizedReceiverTagKey = normalizeAndValidateTagKey(request.getReceiverTagKey(), "Receiver tag key");

        validateCampaignTagKeys(normalizedPricingType, normalizedSenderTagKey, normalizedReceiverTagKey);

        PricingRule pricingRule = new PricingRule();
        pricingRule.setPricingName(request.getPricingName().trim());
        pricingRule.setServiceCode(request.getServiceCode().trim());
        pricingRule.setRuleType(normalizedRuleType);
        pricingRule.setPricingType(normalizedPricingType);
        pricingRule.setPayer(normalizedPayer);
        pricingRule.setPayBy(normalizedPayBy);
        pricingRule.setPayerSplit(request.getPayerSplit());
        pricingRule.setSenderTagKey(normalizedSenderTagKey);
        pricingRule.setReceiverTagKey(normalizedReceiverTagKey);
        pricingRule.setCurrency(request.getCurrency().trim().toUpperCase(Locale.ROOT));
        pricingRule.setPricingConfig(request.getPricingConfig());
        pricingRule.setStatus(normalizeAndValidateStatus(request.getStatus()));
        pricingRule.setValidFrom(request.getValidFrom());
        pricingRule.setValidTo(request.getValidTo());
        pricingRule.setCreatedBy(resolveCurrentAccountId());
        pricingRule.setUpdatedBy(resolveCurrentAccountId());

        validatePayerSplit(pricingRule);

        return new PricingRuleResponse(pricingRuleRepository.save(pricingRule));
    }

    @Transactional(readOnly = true)
    public List<PricingRuleResponse> getAllPricingRules() {
        return pricingRuleRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(PricingRuleResponse::new)
                .toList();
    }

    @Transactional(readOnly = true)
    public PricingRuleResponse getPricingRule(Long id) {
        return new PricingRuleResponse(getPricingRuleEntity(id));
    }

    @Transactional
    public PricingRuleResponse updatePricingStatus(Long id, UpdatePricingStatusRequest request) {
        PricingRule pricingRule = getPricingRuleEntity(id);
        pricingRule.setStatus(normalizeAndValidateStatus(request.getStatus()));
        pricingRule.setUpdatedBy(resolveCurrentAccountId());
        return new PricingRuleResponse(pricingRuleRepository.save(pricingRule));
    }

    @Transactional(readOnly = true)
    public PricingComputationResponse calculatePricingAmounts(
            String serviceCode,
            String senderTagKey,
            String receiverTagKey,
            String currency,
            BigDecimal txnAmount,
            String payerUserId,
            String payeeUserId
    ) {
        log.debug(
                "Calculating pricing for serviceCode={}, senderTagKey={}, receiverTagKey={}, currency={}, txnAmount={}, payerUserId={}, payeeUserId={}",
                serviceCode,
                senderTagKey,
                receiverTagKey,
                currency,
                txnAmount,
                payerUserId,
                payeeUserId
        );
        List<PricingRule> pricingRules = getApplicableRules(
                serviceCode.trim(),
                senderTagKey.trim(),
                receiverTagKey.trim(),
                currency.trim().toUpperCase(Locale.ROOT),
                null
        );

        if (pricingRules.isEmpty()) {
            log.debug(
                    "No applicable pricing rules found for serviceCode={}, senderTagKey={}, receiverTagKey={}, currency={}",
                    serviceCode,
                    senderTagKey,
                    receiverTagKey,
                    currency
            );
            return null;
        }

        PricingComputationResponse response = new PricingComputationResponse();

        for (PricingRule pricingRule : pricingRules) {
            BigDecimal amount = pricingCalculator.calculate(
                    pricingRule.getPricingConfig(),
                    txnAmount,
                    pricingRule.getServiceCode(),
                    payerUserId,
                    payeeUserId,
                    pricingRule.getRuleType()
            );
            applyRuleAmount(response, pricingRule, amount);
        }

        log.debug(
                "Calculated pricing totals for serviceCode={}, senderTagKey={}, receiverTagKey={}, pricingTypes={}: serviceCharge={}, commission={}, discount={}, cashback={}",
                serviceCode,
                senderTagKey,
                receiverTagKey,
                extractPricingTypes(pricingRules),
                response.getServiceChargeAmount(),
                response.getCommissionAmount(),
                response.getDiscountAmount(),
                response.getCashbackAmount()
        );

        return response;
    }

    @Transactional(readOnly = true)
    public PricingComputationResponse calculatePricingAmounts(U2UPaymentRequest request) {
        if (request == null || request.getDebitor() == null || request.getCreditor() == null
                || request.getTransaction() == null) {
            throw new ApplicationException(ErrorCodes.INVALID_REQUEST, "Invalid financial request");
        }

        AccountIdentifier payerIdentifier = resolveAccountIdentifier(
                request.getDebitor().getIdentifier().getType().name(),
                request.getDebitor().getIdentifier().getValue()
        );
        AccountIdentifier payeeIdentifier = resolveAccountIdentifier(
                request.getCreditor().getIdentifier().getType().name(),
                request.getCreditor().getIdentifier().getValue()
        );

        List<String> payerTagKeys = new ArrayList<>(resolveActiveTagKeys(payerIdentifier.getAccountId()));
        List<String> payeeTagKeys = new ArrayList<>(resolveActiveTagKeys(payeeIdentifier.getAccountId()));

        log.info(
                "Starting pricing calculation for operationType={}, payerAccountId={}, payeeAccountId={}, payerTags={}, payeeTags={}, txnAmount={}, currency={}",
                request.getOperationType(),
                payerIdentifier.getAccountId(),
                payeeIdentifier.getAccountId(),
                payerTagKeys,
                payeeTagKeys,
                request.getTransaction().getAmount(),
                request.getTransaction().getCurrency()
        );

        List<PricingRule> campaignRules = pricingRuleRepository.findApplicableCampaignRules(
                request.getOperationType(),
                request.getTransaction().getCurrency(),
                LocalDateTime.now()
        );

        if (!campaignRules.isEmpty()) {
            if (!payerTagKeys.contains("ALL")) {
                payerTagKeys.add("ALL");
            }
            if (!payeeTagKeys.contains("ALL")) {
                payeeTagKeys.add("ALL");
            }
        }

        PricingComputationResponse selectedResponse = null;

        for (String payerTagKey : payerTagKeys) {
            for (String payeeTagKey : payeeTagKeys) {
                long combinationStartNanos = System.nanoTime();
                PricingComputationResponse currentResponse = calculatePricingAmountsForRuleTypes(
                        request.getOperationType(),
                        payerTagKey,
                        payeeTagKey,
                        request.getTransaction().getCurrency(),
                        request.getTransaction().getAmount(),
                        payerIdentifier.getAccountId(),
                        payeeIdentifier.getAccountId(),
                        SERVICE_CHARGE_RULE_TYPES
                );
                long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - combinationStartNanos);

                if (currentResponse == null) {
                    log.info(
                            "Service charge combination senderTagKey={}, receiverTagKey={} skipped, no applicable rule found, took={}ms",
                            payerTagKey,
                            payeeTagKey,
                            elapsedMillis
                    );
                    continue;
                }

                if (currentResponse.getSenderTagKey() == null) {
                    currentResponse.setSenderTagKey(payerTagKey);
                }
                if (currentResponse.getReceiverTagKey() == null) {
                    currentResponse.setReceiverTagKey(payeeTagKey);
                }

                log.info(
                        "Service charge combination requestedSenderTagKey={}, requestedReceiverTagKey={}, effectiveSenderTagKey={}, effectiveReceiverTagKey={}, serviceCharge={}, commission={}, cashback={}, took={}ms",
                        payerTagKey,
                        payeeTagKey,
                        currentResponse.getSenderTagKey(),
                        currentResponse.getReceiverTagKey(),
                        currentResponse.getServiceChargeAmount(),
                        currentResponse.getCommissionAmount(),
                        currentResponse.getCashbackAmount(),
                        elapsedMillis
                );

                if (selectedResponse == null || isLowerServiceCharge(currentResponse, selectedResponse)) {
                    selectedResponse = currentResponse;
                }
            }
        }

        if (selectedResponse != null) {
            if (isCampaignServiceChargeSelection(selectedResponse)) {
                applyMaximumComponentAmounts(
                        selectedResponse,
                        request.getOperationType(),
                        payerTagKeys,
                        payeeTagKeys,
                        request.getTransaction().getCurrency(),
                        request.getTransaction().getAmount(),
                        payerIdentifier.getAccountId(),
                        payeeIdentifier.getAccountId()
                );
            } else {
                PricingComputationResponse commissionResponse = calculatePricingAmountsForRuleTypes(
                        request.getOperationType(),
                        selectedResponse.getSenderTagKey(),
                        selectedResponse.getReceiverTagKey(),
                        request.getTransaction().getCurrency(),
                        request.getTransaction().getAmount(),
                        payerIdentifier.getAccountId(),
                        payeeIdentifier.getAccountId(),
                        COMMISSION_RULE_TYPES
                );

                if (commissionResponse != null) {
                    selectedResponse.setCommissionAmount(commissionResponse.getCommissionAmount());
                    selectedResponse.setCommissionAffectedParty(commissionResponse.getCommissionAffectedParty());
                }

                PricingComputationResponse discountResponse = calculatePricingAmountsForRuleTypes(
                        request.getOperationType(),
                        selectedResponse.getSenderTagKey(),
                        selectedResponse.getReceiverTagKey(),
                        request.getTransaction().getCurrency(),
                        request.getTransaction().getAmount(),
                        payerIdentifier.getAccountId(),
                        payeeIdentifier.getAccountId(),
                        DISCOUNT_RULE_TYPES
                );

                if (discountResponse != null) {
                    selectedResponse.setDiscountAmount(discountResponse.getDiscountAmount());
                    selectedResponse.setDiscountAffectedParty(discountResponse.getDiscountAffectedParty());
                }

                PricingComputationResponse cashbackResponse = calculatePricingAmountsForRuleTypes(
                        request.getOperationType(),
                        selectedResponse.getSenderTagKey(),
                        selectedResponse.getReceiverTagKey(),
                        request.getTransaction().getCurrency(),
                        request.getTransaction().getAmount(),
                        payerIdentifier.getAccountId(),
                        payeeIdentifier.getAccountId(),
                        CASHBACK_RULE_TYPES
                );

                if (cashbackResponse != null) {
                    selectedResponse.setCashbackAmount(cashbackResponse.getCashbackAmount());
                    selectedResponse.setCashbackAffectedParty(cashbackResponse.getCashbackAffectedParty());
                }
            }

            log.info(
                    "Selected service charge tags senderTagKey={}, receiverTagKey={} with final serviceCharge={}, commission={}, discount={}, cashback={}",
                    selectedResponse.getSenderTagKey(),
                    selectedResponse.getReceiverTagKey(),
                    selectedResponse.getServiceChargeAmount(),
                    selectedResponse.getCommissionAmount(),
                    selectedResponse.getDiscountAmount(),
                    selectedResponse.getCashbackAmount()
            );

            return selectedResponse;
        }

        PricingComputationResponse maxDiscountResponse = selectMaximumAmountCombination(
                request.getOperationType(),
                payerTagKeys,
                payeeTagKeys,
                request.getTransaction().getCurrency(),
                request.getTransaction().getAmount(),
                payerIdentifier.getAccountId(),
                payeeIdentifier.getAccountId(),
                DISCOUNT_RULE_TYPES
        );

        PricingComputationResponse maxCommissionResponse = selectMaximumAmountCombination(
                request.getOperationType(),
                payerTagKeys,
                payeeTagKeys,
                request.getTransaction().getCurrency(),
                request.getTransaction().getAmount(),
                payerIdentifier.getAccountId(),
                payeeIdentifier.getAccountId(),
                COMMISSION_RULE_TYPES
        );

        PricingComputationResponse maxCashbackResponse = selectMaximumAmountCombination(
                request.getOperationType(),
                payerTagKeys,
                payeeTagKeys,
                request.getTransaction().getCurrency(),
                request.getTransaction().getAmount(),
                payerIdentifier.getAccountId(),
                payeeIdentifier.getAccountId(),
                CASHBACK_RULE_TYPES
        );

        PricingComputationResponse fallbackResponse = firstNonNullResponse(
                maxDiscountResponse,
                maxCommissionResponse,
                maxCashbackResponse
        );

        if (fallbackResponse == null) {
            PricingComputationResponse emptyResponse = new PricingComputationResponse();
            log.info(
                    "No pricing rules configured for operationType={}, payerAccountId={}, payeeAccountId={}. Returning zero-value pricing response.",
                    request.getOperationType(),
                    payerIdentifier.getAccountId(),
                    payeeIdentifier.getAccountId()
            );
            return emptyResponse;
        }

        if (maxDiscountResponse != null) {
            fallbackResponse.setDiscountAmount(maxDiscountResponse.getDiscountAmount());
            fallbackResponse.setDiscountAffectedParty(maxDiscountResponse.getDiscountAffectedParty());
        }
        if (maxCommissionResponse != null) {
            fallbackResponse.setCommissionAmount(maxCommissionResponse.getCommissionAmount());
            fallbackResponse.setCommissionAffectedParty(maxCommissionResponse.getCommissionAffectedParty());
        }
        if (maxCashbackResponse != null) {
            fallbackResponse.setCashbackAmount(maxCashbackResponse.getCashbackAmount());
            fallbackResponse.setCashbackAffectedParty(maxCashbackResponse.getCashbackAffectedParty());
        }

        log.info(
                "No service charge tags found. Selected maximum discount combination senderTagKey={}, receiverTagKey={} with serviceCharge={}, commission={}, discount={}, cashback={}",
                fallbackResponse.getSenderTagKey(),
                fallbackResponse.getReceiverTagKey(),
                fallbackResponse.getServiceChargeAmount(),
                fallbackResponse.getCommissionAmount(),
                fallbackResponse.getDiscountAmount(),
                fallbackResponse.getCashbackAmount()
        );

        return fallbackResponse;
    }

    private PricingRule getPricingRuleEntity(Long id) {
        return pricingRuleRepository.findById(id)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCodes.INVALID_REQUEST,
                        "Pricing rule not found"
                ));
    }

    private String normalizeAndValidateRuleType(String ruleType) {
        String normalizedRuleType = ruleType.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_RULE_TYPES.contains(normalizedRuleType)) {
            throw new ApplicationException(ErrorCodes.INVALID_REQUEST, "Invalid rule type");
        }
        return normalizedRuleType;
    }

    private String normalizeAndValidatePayer(String payer) {
        String normalizedPayer = payer.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_PAYERS.contains(normalizedPayer)) {
            throw new ApplicationException(ErrorCodes.INVALID_REQUEST, "Invalid payer");
        }
        return normalizedPayer;
    }

    private String normalizeAndValidatePricingType(String pricingType) {
        if (pricingType == null || pricingType.isBlank()) {
            return "STATIC";
        }

        String normalizedPricingType = pricingType.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_PRICING_TYPES.contains(normalizedPricingType)) {
            throw new ApplicationException(ErrorCodes.INVALID_REQUEST, "Invalid pricing type");
        }
        return normalizedPricingType;
    }

    private String normalizeAndValidatePayBy(String payBy, String ruleType) {
        if (!"CASHBACK".equals(ruleType)) {
            if (payBy != null && !payBy.isBlank()) {
                throw new ApplicationException(ErrorCodes.INVALID_REQUEST, "payBy is only allowed for cashback rules");
            }
            return null;
        }

        return normalizeOptionalValue(payBy);
    }

    private String normalizeAndValidateTagKey(String tagKey, String fieldName) {
        if (tagKey == null || tagKey.isBlank()) {
            throw new ApplicationException(ErrorCodes.INVALID_REQUEST, fieldName + " is required");
        }
        return tagKey.trim().toUpperCase(Locale.ROOT);
    }

    private void validateCampaignTagKeys(String pricingType, String senderTagKey, String receiverTagKey) {
        if ("CAMPAIGN".equals(pricingType)
                && (!"ALL".equals(senderTagKey) || !"ALL".equals(receiverTagKey))) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_REQUEST,
                    "For CAMPAIGN pricingType, senderTagKey and receiverTagKey must be ALL"
            );
        }
    }

    private void validatePayerSplit(PricingRule pricingRule) {
        if ("SPLIT".equals(pricingRule.getPayer()) && pricingRule.getPayerSplit() == null) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_REQUEST,
                    "payerSplit is required when payer is SPLIT"
            );
        }
    }

    private String normalizeAndValidateStatus(String status) {
        if (status == null || status.isBlank()) {
            return "ACTIVE";
        }

        String normalizedStatus = status.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_STATUSES.contains(normalizedStatus)) {
            throw new ApplicationException(ErrorCodes.INVALID_REQUEST, "Invalid status");
        }
        return normalizedStatus;
    }

    private String normalizeOptionalValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private void applyRuleAmount(PricingComputationResponse response, PricingRule pricingRule, BigDecimal amount) {
        String affectedParty = resolveAffectedParty(pricingRule.getPayer());
        switch (pricingRule.getRuleType()) {
            case "SERVICE_CHARGE" -> {
                response.addServiceCharge(amount);
                response.markServiceChargeAffectedParty(affectedParty);
                markServiceChargeTagSelection(response, pricingRule);
            }
            case "COMMISSION" -> {
                response.addCommission(amount);
                response.markCommissionAffectedParty(affectedParty);
            }
            case "DISCOUNT" -> {
                response.addDiscount(amount);
                response.markDiscountAffectedParty(affectedParty);
            }
            case "CASHBACK" -> {
                response.addCashback(amount);
                response.markCashbackAffectedParty(affectedParty);
            }
            default -> throw new ApplicationException(ErrorCodes.INVALID_REQUEST, "Invalid rule type");
        }
    }

    private PricingComputationResponse calculatePricingAmountsForRuleTypes(
            String serviceCode,
            String senderTagKey,
            String receiverTagKey,
            String currency,
            BigDecimal txnAmount,
            String payerUserId,
            String payeeUserId,
            List<String> ruleTypes
    ) {
        log.debug(
                "Calculating pricing for serviceCode={}, senderTagKey={}, receiverTagKey={}, currency={}, txnAmount={}, payerUserId={}, payeeUserId={}, ruleTypes={}",
                serviceCode,
                senderTagKey,
                receiverTagKey,
                currency,
                txnAmount,
                payerUserId,
                payeeUserId,
                ruleTypes
        );

        List<PricingRule> pricingRules = getApplicableRules(
                serviceCode.trim(),
                senderTagKey.trim(),
                receiverTagKey.trim(),
                currency.trim().toUpperCase(Locale.ROOT),
                ruleTypes
        );

        if (pricingRules.isEmpty()) {
            log.debug(
                    "No applicable pricing rules found for serviceCode={}, senderTagKey={}, receiverTagKey={}, currency={}, ruleTypes={}",
                    serviceCode,
                    senderTagKey,
                    receiverTagKey,
                    currency,
                    ruleTypes
            );
            return null;
        }

        PricingComputationResponse response = new PricingComputationResponse();

        for (PricingRule pricingRule : pricingRules) {
            BigDecimal amount = pricingCalculator.calculate(
                    pricingRule.getPricingConfig(),
                    txnAmount,
                    pricingRule.getServiceCode(),
                    payerUserId,
                    payeeUserId,
                    pricingRule.getRuleType()
            );
            applyRuleAmount(response, pricingRule, amount);
        }

        log.debug(
                "Calculated pricing totals for serviceCode={}, senderTagKey={}, receiverTagKey={}, ruleTypes={}, pricingTypes={}: serviceCharge={}, commission={}, discount={}, cashback={}",
                serviceCode,
                senderTagKey,
                receiverTagKey,
                ruleTypes,
                extractPricingTypes(pricingRules),
                response.getServiceChargeAmount(),
                response.getCommissionAmount(),
                response.getDiscountAmount(),
                response.getCashbackAmount()
        );

        return response;
    }

    private PricingComputationResponse selectMaximumAmountCombination(
            String serviceCode,
            List<String> payerTagKeys,
            List<String> payeeTagKeys,
            String currency,
            BigDecimal txnAmount,
            String payerUserId,
            String payeeUserId,
            List<String> ruleTypes
    ) {
        PricingComputationResponse selectedResponse = null;

        for (String payerTagKey : payerTagKeys) {
            for (String payeeTagKey : payeeTagKeys) {
                long combinationStartNanos = System.nanoTime();
                PricingComputationResponse currentResponse = calculatePricingAmountsForRuleTypes(
                        serviceCode,
                        payerTagKey,
                        payeeTagKey,
                        currency,
                        txnAmount,
                        payerUserId,
                        payeeUserId,
                        ruleTypes
                );
                long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - combinationStartNanos);

                if (currentResponse == null) {
                    log.info(
                            "Combination senderTagKey={}, receiverTagKey={} skipped, no applicable rules found for ruleTypes={}, took={}ms",
                            payerTagKey,
                            payeeTagKey,
                            ruleTypes,
                            elapsedMillis
                    );
                    continue;
                }

                currentResponse.setSenderTagKey(payerTagKey);
                currentResponse.setReceiverTagKey(payeeTagKey);

                log.info(
                        "Combination senderTagKey={}, receiverTagKey={} produced commission={}, discount={}, cashback={}, took={}ms",
                        payerTagKey,
                        payeeTagKey,
                        currentResponse.getCommissionAmount(),
                        currentResponse.getDiscountAmount(),
                        currentResponse.getCashbackAmount(),
                        elapsedMillis
                );

                if (selectedResponse == null || isHigherAmount(currentResponse, selectedResponse, ruleTypes)) {
                    selectedResponse = currentResponse;
                }
            }
        }

        return selectedResponse;
    }

    private AccountIdentifier resolveAccountIdentifier(String identifierType, String identifierValue) {
        return accountIdentifierRepository
                .findByIdentifierTypeAndIdentifierValueAndStatus(identifierType, identifierValue, "ACTIVE")
                .orElseThrow(() -> new ApplicationException(
                        ErrorCodes.ACCOUNT_IDENTIFIER_NOT_FOUND,
                        "Active account identifier not found"
                ));
    }

    private List<String> resolveActiveTagKeys(String accountId) {
        List<UserTag> activeUserTags = userTagRepository.findByAccountId(accountId).stream()
                .filter(userTag -> "ACTIVE".equalsIgnoreCase(userTag.getStatus()))
                .toList();

        List<String> tagKeys = activeUserTags.stream()
                .map(UserTag::getTagId)
                .map(tagRepository::findById)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .filter(tag -> "ACTIVE".equalsIgnoreCase(tag.getStatus()))
                .map(Tag::getTagCode)
                .distinct()
                .toList();

        if (tagKeys.isEmpty()) {
            throw new ApplicationException(ErrorCodes.TAG_NOT_FOUND, "Active tag not found for account: " + accountId);
        }

        return tagKeys;
    }

    private List<PricingRule> getApplicableRules(
            String serviceCode,
            String senderTagKey,
            String receiverTagKey,
            String currency,
            List<String> ruleTypes
    ) {
        List<PricingRule> pricingRules = new ArrayList<>();
        List<PricingRule> staticRules = pricingRuleRepository.findApplicableStaticRules(
                serviceCode,
                senderTagKey,
                receiverTagKey,
                currency,
                LocalDateTime.now()
        );


        pricingRules.addAll(staticRules);
     //   pricingRules.addAll(campaignRules);

        log.debug(
                "Resolved pricing rules for serviceCode={}, senderTagKey={}, receiverTagKey={}, currency={}: staticRules={}",
                serviceCode,
                senderTagKey,
                receiverTagKey,
                currency,
                staticRules.size()
        );

        if (ruleTypes == null || ruleTypes.isEmpty()) {
            return pricingRules;
        }

        List<String> normalizedRuleTypes = new ArrayList<>(ruleTypes.size());
        for (String ruleType : ruleTypes) {
            normalizedRuleTypes.add(ruleType.trim().toUpperCase(Locale.ROOT));
        }

        return pricingRules.stream()
                .filter(pricingRule -> normalizedRuleTypes.contains(pricingRule.getRuleType()))
                .toList();
    }

    private boolean isLowerServiceCharge(
            PricingComputationResponse currentResponse,
            PricingComputationResponse selectedResponse
    ) {
        return currentResponse.getServiceChargeAmount().compareTo(selectedResponse.getServiceChargeAmount()) < 0;
    }

    private boolean isHigherDiscount(
            PricingComputationResponse currentResponse,
            PricingComputationResponse selectedResponse
    ) {
        return currentResponse.getDiscountAmount().compareTo(selectedResponse.getDiscountAmount()) > 0;
    }

    private boolean isHigherCommission(
            PricingComputationResponse currentResponse,
            PricingComputationResponse selectedResponse
    ) {
        return currentResponse.getCommissionAmount().compareTo(selectedResponse.getCommissionAmount()) > 0;
    }

    private boolean isHigherCashback(
            PricingComputationResponse currentResponse,
            PricingComputationResponse selectedResponse
    ) {
        return currentResponse.getCashbackAmount().compareTo(selectedResponse.getCashbackAmount()) > 0;
    }

    private boolean isHigherAmount(
            PricingComputationResponse currentResponse,
            PricingComputationResponse selectedResponse,
            List<String> ruleTypes
    ) {
        if (DISCOUNT_RULE_TYPES.equals(ruleTypes)) {
            return isHigherDiscount(currentResponse, selectedResponse);
        }
        if (COMMISSION_RULE_TYPES.equals(ruleTypes)) {
            return isHigherCommission(currentResponse, selectedResponse);
        }
        if (CASHBACK_RULE_TYPES.equals(ruleTypes)) {
            return isHigherCashback(currentResponse, selectedResponse);
        }
        throw new ApplicationException(ErrorCodes.INVALID_REQUEST, "Unsupported rule type selection");
    }

    private boolean isCampaignServiceChargeSelection(PricingComputationResponse response) {
        return response.getServiceChargeAmount().compareTo(BigDecimal.ZERO) > 0
                && "ALL".equalsIgnoreCase(response.getSenderTagKey())
                && "ALL".equalsIgnoreCase(response.getReceiverTagKey());
    }

    private void applyMaximumComponentAmounts(
            PricingComputationResponse targetResponse,
            String serviceCode,
            List<String> payerTagKeys,
            List<String> payeeTagKeys,
            String currency,
            BigDecimal txnAmount,
            String payerUserId,
            String payeeUserId
    ) {
        PricingComputationResponse maxDiscountResponse = selectMaximumAmountCombination(
                serviceCode,
                payerTagKeys,
                payeeTagKeys,
                currency,
                txnAmount,
                payerUserId,
                payeeUserId,
                DISCOUNT_RULE_TYPES
        );
        PricingComputationResponse maxCommissionResponse = selectMaximumAmountCombination(
                serviceCode,
                payerTagKeys,
                payeeTagKeys,
                currency,
                txnAmount,
                payerUserId,
                payeeUserId,
                COMMISSION_RULE_TYPES
        );
        PricingComputationResponse maxCashbackResponse = selectMaximumAmountCombination(
                serviceCode,
                payerTagKeys,
                payeeTagKeys,
                currency,
                txnAmount,
                payerUserId,
                payeeUserId,
                CASHBACK_RULE_TYPES
        );

        if (maxDiscountResponse != null) {
            targetResponse.setDiscountAmount(maxDiscountResponse.getDiscountAmount());
            targetResponse.setDiscountAffectedParty(maxDiscountResponse.getDiscountAffectedParty());
        }
        if (maxCommissionResponse != null) {
            targetResponse.setCommissionAmount(maxCommissionResponse.getCommissionAmount());
            targetResponse.setCommissionAffectedParty(maxCommissionResponse.getCommissionAffectedParty());
        }
        if (maxCashbackResponse != null) {
            targetResponse.setCashbackAmount(maxCashbackResponse.getCashbackAmount());
            targetResponse.setCashbackAffectedParty(maxCashbackResponse.getCashbackAffectedParty());
        }
    }

    private void markServiceChargeTagSelection(PricingComputationResponse response, PricingRule pricingRule) {
        if ("CAMPAIGN".equalsIgnoreCase(pricingRule.getPricingType())
                && "ALL".equalsIgnoreCase(pricingRule.getSenderTagKey())
                && "ALL".equalsIgnoreCase(pricingRule.getReceiverTagKey())) {
            response.setSenderTagKey("ALL");
            response.setReceiverTagKey("ALL");
            return;
        }

        if (response.getSenderTagKey() == null && response.getReceiverTagKey() == null) {
            response.setSenderTagKey(pricingRule.getSenderTagKey());
            response.setReceiverTagKey(pricingRule.getReceiverTagKey());
        }
    }

    private String resolveAffectedParty(String payer) {
        if (payer == null || payer.isBlank()) {
            return null;
        }
        return payer.trim().toUpperCase(Locale.ROOT);
    }

    private PricingComputationResponse firstNonNullResponse(PricingComputationResponse... responses) {
        for (PricingComputationResponse response : responses) {
            if (response != null) {
                return response;
            }
        }
        return null;
    }

    private List<String> extractPricingTypes(List<PricingRule> pricingRules) {
        return pricingRules.stream()
                .map(PricingRule::getPricingType)
                .map(pricingType -> pricingType == null || pricingType.isBlank() ? "STATIC" : pricingType)
                .distinct()
                .toList();
    }

    private String resolveCurrentAccountId() {
        try {
            return JWTUtils.getCurrentAccountId();
        } catch (Exception ex) {
            return null;
        }
    }
}
