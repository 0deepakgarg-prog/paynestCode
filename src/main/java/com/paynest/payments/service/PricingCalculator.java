package com.paynest.payments.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.paynest.payments.repository.TransactionsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class PricingCalculator {

    private static final String BASED_ON_TXN_AMOUNT = "TXNAMOUNT";
    private static final String BASED_ON_DAILY_TXN_COUNT = "DAILYTXNCOUNT";
    private static final String BASED_ON_MONTHLY_TXN_COUNT = "MONTHLYTXNCOUNT";
    private static final String BASED_ON_MONTHLY_TXN_AMOUNT = "MONTHLYTXNAMOUNT";
    private static final String RULE_TYPE_SERVICE_CHARGE = "SERVICE_CHARGE";
    private static final String RULE_TYPE_COMMISSION = "COMMISSION";
    private static final String RULE_TYPE_DISCOUNT = "DISCOUNT";
    private static final String RULE_TYPE_CASHBACK = "CASHBACK";

    private final TransactionsRepository transactionsRepository;

    public BigDecimal calculate(JsonNode config,
                                BigDecimal amount,
                                String serviceCode,
                                String payerUserId,
                                String payeeUserId,
                                String ruleType) {
        String normalizedRuleType = normalizeRuleType(ruleType);
        String basedOn = config.has("basedOn")
                ? config.get("basedOn").asText("txnAmount")
                : "txnAmount";
        String strategy = config.get("charging_strategy").asText();

        BigDecimal charge;

        switch (strategy) {
            case "FLAT":
                charge = calculateCalc(config.get("calc"), amount);
                break;

            case "SLAB":
                if (config.has("telescopic") && config.get("telescopic").asBoolean()) {
                    BigDecimal previousUsage = resolvePreviousUsage(
                            basedOn,
                            serviceCode,
                            payerUserId,
                            payeeUserId,
                            normalizedRuleType
                    );
                    charge = calculateConsumptionTelescopic(config.get("slabs"), previousUsage, amount, basedOn);
                } else {
                    charge = calculateSlab(
                            config.get("slabs"),
                            amount,
                            basedOn,
                            serviceCode,
                            payerUserId,
                            payeeUserId,
                            normalizedRuleType
                    );
                }
                break;

            default:
                throw new IllegalArgumentException("Invalid strategy: " + strategy);
        }

        return applyLimits(charge, config.get("limits"));
    }

    private BigDecimal calculateCalc(JsonNode calc, BigDecimal amount) {
        String type = calc.get("type").asText();

        switch (type) {
            case "FLAT":
                return calc.get("value").decimalValue();

            case "PERCENT":
                return amount
                        .multiply(calc.get("value").decimalValue())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            case "HYBRID":
                return calculateHybrid(calc, amount);

            default:
                throw new IllegalArgumentException("Invalid calc type: " + type);
        }
    }

    private BigDecimal calculateHybrid(JsonNode calc, BigDecimal amount) {
        log.info("calculating hybrid");
        Iterator<JsonNode> components = calc.get("components").elements();
        BigDecimal result = null;

        while (components.hasNext()) {
            JsonNode comp = components.next();
            BigDecimal value = calculateCalc(comp, amount);

            if (result == null) {
                result = value;
            } else {
                result = applyOperator(result, value, calc.get("operator").asText());
            }
        }

        return result;
    }

    private BigDecimal applyOperator(BigDecimal a, BigDecimal b, String operator) {
        switch (operator) {
            case "MAX":
                return a.max(b);

            case "MIN":
                return a.min(b);

            case "ADD":
                return a.add(b);

            default:
                throw new IllegalArgumentException("Invalid operator: " + operator);
        }
    }

    private BigDecimal calculateSlab(
            JsonNode slabs,
            BigDecimal txnAmount,
            String basedOn,
            String serviceCode,
            String payerUserId,
            String payeeUserId,
            String ruleType
    ) {
        BigDecimal slabMetric = resolveCurrentSlabMetric(
                basedOn,
                txnAmount,
                serviceCode,
                payerUserId,
                payeeUserId,
                ruleType
        );

        for (JsonNode slab : slabs) {
            BigDecimal min = slab.get("min").decimalValue();
            BigDecimal max = slab.get("max").isNull() ? null : slab.get("max").decimalValue();

            boolean matches =
                    slabMetric.compareTo(min) >= 0 &&
                            (max == null || slabMetric.compareTo(max) <= 0);

            if (matches) {
                return calculateCalc(slab.get("calc"), txnAmount);
            }
        }

        throw new IllegalArgumentException("No slab matched");
    }

    private BigDecimal applyLimits(BigDecimal charge, JsonNode limits) {
        if (limits == null) {
            return charge;
        }

        if (limits.has("min_charge")) {
            BigDecimal min = limits.get("min_charge").decimalValue();
            charge = charge.max(min);
        }

        if (limits.has("max_charge")) {
            BigDecimal max = limits.get("max_charge").decimalValue();
            charge = charge.min(max);
        }

        return charge;
    }

    public BigDecimal calculateConsumptionTelescopic(
            JsonNode slabs,
            BigDecimal previousUsage,
            BigDecimal txnAmount,
            String basedOn
    ) {
        BigDecimal totalCharge = BigDecimal.ZERO;
        BigDecimal remainingUsage = usageIncrement(basedOn, txnAmount);
        BigDecimal currentUsage = previousUsage;

        for (JsonNode slab : slabs) {
            BigDecimal slabMax = slab.get("max").isNull()
                    ? null
                    : slab.get("max").decimalValue();

            if (slabMax != null && currentUsage.compareTo(slabMax) >= 0) {
                continue;
            }

            BigDecimal upperBound = (slabMax == null)
                    ? currentUsage.add(remainingUsage)
                    : slabMax;

            BigDecimal availableInSlab = upperBound.subtract(currentUsage);
            if (availableInSlab.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal applicableUsage = remainingUsage.min(availableInSlab);
            BigDecimal chargeBase = isAmountBased(basedOn) ? applicableUsage : txnAmount;
            BigDecimal slabCharge = calculateCalc(slab.get("calc"), chargeBase);

            totalCharge = totalCharge.add(slabCharge);
            remainingUsage = remainingUsage.subtract(applicableUsage);
            currentUsage = currentUsage.add(applicableUsage);

            if (remainingUsage.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
        }

        return totalCharge;
    }

    private BigDecimal resolveCurrentSlabMetric(
            String basedOn,
            BigDecimal txnAmount,
            String serviceCode,
            String payerUserId,
            String payeeUserId,
            String ruleType
    ) {
        return resolvePreviousUsage(basedOn, serviceCode, payerUserId, payeeUserId, ruleType)
                .add(usageIncrement(basedOn, txnAmount));
    }

    private BigDecimal resolvePreviousUsage(
            String basedOn,
            String serviceCode,
            String payerUserId,
            String payeeUserId,
            String ruleType
    ) {
        return switch (normalizeBasedOn(basedOn)) {
            case BASED_ON_TXN_AMOUNT -> BigDecimal.ZERO;
            case BASED_ON_DAILY_TXN_COUNT -> getPreviousTxnCountForLastDay(
                    serviceCode,
                    payerUserId,
                    payeeUserId,
                    ruleType
            );
            case BASED_ON_MONTHLY_TXN_COUNT -> getPreviousTxnCountForLastMonth(
                    serviceCode,
                    payerUserId,
                    payeeUserId,
                    ruleType
            );
            case BASED_ON_MONTHLY_TXN_AMOUNT -> getPreviousTotalTxnForLastMonth(
                    serviceCode,
                    payerUserId,
                    payeeUserId,
                    ruleType
            );
            default -> throw new IllegalArgumentException("Invalid basedOn: " + basedOn);
        };
    }

    private BigDecimal usageIncrement(String basedOn, BigDecimal txnAmount) {
        return switch (normalizeBasedOn(basedOn)) {
            case BASED_ON_TXN_AMOUNT, BASED_ON_MONTHLY_TXN_AMOUNT -> txnAmount;
            case BASED_ON_DAILY_TXN_COUNT, BASED_ON_MONTHLY_TXN_COUNT -> BigDecimal.ONE;
            default -> throw new IllegalArgumentException("Invalid basedOn: " + basedOn);
        };
    }

    private boolean isAmountBased(String basedOn) {
        String normalizedBasedOn = normalizeBasedOn(basedOn);
        return BASED_ON_TXN_AMOUNT.equals(normalizedBasedOn)
                || BASED_ON_MONTHLY_TXN_AMOUNT.equals(normalizedBasedOn);
    }

    private String normalizeBasedOn(String basedOn) {
        if (basedOn == null || basedOn.isBlank()) {
            return BASED_ON_TXN_AMOUNT;
        }
        return basedOn.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeRuleType(String ruleType) {
        if (ruleType == null || ruleType.isBlank()) {
            throw new IllegalArgumentException("Rule type is required");
        }

        String normalizedRuleType = ruleType.trim().toUpperCase(Locale.ROOT);
        return switch (normalizedRuleType) {
            case RULE_TYPE_SERVICE_CHARGE, RULE_TYPE_COMMISSION, RULE_TYPE_DISCOUNT, RULE_TYPE_CASHBACK ->
                    normalizedRuleType;
            default -> throw new IllegalArgumentException("Invalid rule type: " + ruleType);
        };
    }

    @Transactional(readOnly = true)
    public BigDecimal getPreviousTotalTxnForLastDay(
            String serviceCode,
            String payerUserId,
            String payeeUserId,
            String ruleType
    ) {
        return getPreviousTotalTxn(serviceCode, payerUserId, payeeUserId, ruleType, LocalDateTime.now().minusDays(1));
    }

    @Transactional(readOnly = true)
    public BigDecimal getPreviousTotalTxnForLastMonth(
            String serviceCode,
            String payerUserId,
            String payeeUserId,
            String ruleType
    ) {
        return getPreviousTotalTxn(
                serviceCode,
                payerUserId,
                payeeUserId,
                ruleType,
                LocalDateTime.now().minusMonths(1)
        );
    }

    @Transactional(readOnly = true)
    public BigDecimal getPreviousTotalTxn(
            String serviceCode,
            String payerUserId,
            String payeeUserId,
            String ruleType,
            LocalDateTime fromDateTime
    ) {
        if (RULE_TYPE_COMMISSION.equals(ruleType)) {
            return transactionsRepository.sumTransactionValueByServiceCodeSinceForCreditor(
                    serviceCode,
                    payeeUserId,
                    fromDateTime
            );
        }
        return transactionsRepository.sumTransactionValueByServiceCodeSince(serviceCode, payerUserId, fromDateTime);
    }

    @Transactional(readOnly = true)
    public BigDecimal getPreviousTxnCountForLastDay(
            String serviceCode,
            String payerUserId,
            String payeeUserId,
            String ruleType
    ) {
        return getPreviousTxnCount(serviceCode, payerUserId, payeeUserId, ruleType, LocalDateTime.now().minusDays(1));
    }

    @Transactional(readOnly = true)
    public BigDecimal getPreviousTxnCountForLastMonth(
            String serviceCode,
            String payerUserId,
            String payeeUserId,
            String ruleType
    ) {
        return getPreviousTxnCount(
                serviceCode,
                payerUserId,
                payeeUserId,
                ruleType,
                LocalDateTime.now().minusMonths(1)
        );
    }

    @Transactional(readOnly = true)
    public BigDecimal getPreviousTxnCount(
            String serviceCode,
            String payerUserId,
            String payeeUserId,
            String ruleType,
            LocalDateTime fromDateTime
    ) {
        if (RULE_TYPE_COMMISSION.equals(ruleType)) {
            return BigDecimal.valueOf(
                    transactionsRepository.countTransactionsByServiceCodeSinceForCreditor(
                            serviceCode,
                            payeeUserId,
                            fromDateTime
                    )
            );
        }
        return BigDecimal.valueOf(
                transactionsRepository.countTransactionsByServiceCodeSince(
                        serviceCode,
                        payerUserId,
                        fromDateTime
                )
        );
    }
}
