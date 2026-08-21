package com.paynest.limits.service;

import com.paynest.common.ErrorCodes;
import com.paynest.config.tenant.TenantTime;
import com.paynest.exception.ApplicationException;
import com.paynest.exception.PaymentErrorCode;
import com.paynest.limits.TransactionLimitConstants;
import com.paynest.limits.TransactionLimitErrorCode;
import com.paynest.limits.entity.TransactionLimitProfile;
import com.paynest.limits.entity.TransactionLimitProfileDetail;
import com.paynest.limits.entity.TransactionLimitProfilePeriod;
import com.paynest.limits.entity.TransactionLimitUsage;
import com.paynest.limits.repository.TransactionLimitProfileDetailRepository;
import com.paynest.limits.repository.TransactionLimitProfilePeriodRepository;
import com.paynest.limits.repository.TransactionLimitProfileRepository;
import com.paynest.limits.repository.TransactionLimitUsageRepository;
import com.paynest.payments.entity.Transactions;
import com.paynest.payments.repository.TransactionsRepository;
import com.paynest.tag.entity.Tag;
import com.paynest.tag.entity.UserTag;
import com.paynest.tag.repository.TagRepository;
import com.paynest.tag.repository.UserTagRepository;
import com.paynest.users.entity.Account;
import com.paynest.users.entity.Wallet;
import com.paynest.users.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionLimitValidator {

    private static final String SYSTEM_ACCOUNT_ID = "SYS0001";
    private static final String O2C_SERVICE_CODE = "O2C";

    private final AccountRepository accountRepository;
    private final UserTagRepository userTagRepository;
    private final TagRepository tagRepository;
    private final TransactionLimitProfileRepository profileRepository;
    private final TransactionLimitProfileDetailRepository detailRepository;
    private final TransactionLimitProfilePeriodRepository periodRepository;
    private final TransactionLimitUsageRepository usageRepository;
    private final TransactionsRepository transactionsRepository;
    private final TransactionLimitSubjectResolver subjectResolver;

    @Transactional
    public void validateAndReserve(
            Wallet debitorWallet,
            Wallet creditorWallet,
            BigDecimal debitAmount,
            BigDecimal creditAmount,
            BigDecimal debitorBalanceAfter,
            BigDecimal creditorBalanceAfter,
            String serviceCode,
            String transactionId
    ) {
        if (isLimitExemptService(serviceCode)) {
            log.debug("Skipping transaction limit for exempt service. serviceCode={}, transactionId={}",
                    serviceCode, transactionId);
            return;
        }
        validateAndReserve(
                debitorWallet,
                creditorWallet,
                debitAmount,
                creditAmount,
                debitorBalanceAfter,
                creditorBalanceAfter,
                serviceCode,
                resolveRequestGateway(transactionId),
                transactionId
        );
    }

    @Transactional
    public void validateAndReserve(
            Wallet debitorWallet,
            Wallet creditorWallet,
            BigDecimal debitAmount,
            BigDecimal creditAmount,
            BigDecimal debitorBalanceAfter,
            BigDecimal creditorBalanceAfter,
            String serviceCode,
            String requestGateway,
            String transactionId
    ) {
        if (isLimitExemptService(serviceCode)) {
            log.debug("Skipping transaction limit for exempt service. serviceCode={}, transactionId={}",
                    serviceCode, transactionId);
            return;
        }
        applyPartyLimit(
                TransactionLimitConstants.PARTY_DEBITOR,
                debitorWallet,
                debitAmount,
                debitorBalanceAfter,
                serviceCode,
                requestGateway,
                transactionId
        );
        applyPartyLimit(
                TransactionLimitConstants.PARTY_CREDITOR,
                creditorWallet,
                creditAmount,
                creditorBalanceAfter,
                serviceCode,
                requestGateway,
                transactionId
        );
    }

    private void applyPartyLimit(
            String partyType,
            Wallet wallet,
            BigDecimal amount,
            BigDecimal balanceAfter,
            String serviceCode,
            String requestGateway,
            String transactionId
    ) {
        if (wallet == null || wallet.getAccountId() == null || wallet.getAccountId().isBlank()) {
            return;
        }
        if (isSystemWallet(wallet)) {
            log.debug("Skipping transaction limit for system wallet. accountId={}, walletId={}, partyType={}",
                    wallet.getAccountId(), wallet.getWalletId(), partyType);
            return;
        }

        Account account = accountRepository.findById(wallet.getAccountId())
                .orElseThrow(() -> new ApplicationException(PaymentErrorCode.ACCOUNT_NOT_FOUND));
        List<Tag> activeTags = resolveActiveTags(account.getAccountId(), partyType);
        TransactionLimitProfile profile = resolveProfile(activeTags, wallet, partyType);

        if (profile.getSubjectKey() == null || profile.getSubjectKey().isBlank()) {
            throw new ApplicationException(
                    TransactionLimitErrorCode.LIMIT_SUBJECT_KEY_MISSING,
                    Map.of(
                            "limitId", profile.getLimitId(),
                            "partyType", partyType
                    )
            );
        }

        TransactionLimitProfileDetail detail = resolveDetail(profile, partyType, serviceCode, requestGateway);
        List<TransactionLimitProfilePeriod> periods = periodRepository
                .findByLimitDetailsIdAndStatusOrderByLimitPeriodIdAsc(
                        detail.getLimitDetailsId(),
                        TransactionLimitConstants.STATUS_ACTIVE
                );
        if (periods.isEmpty()) {
            throw new ApplicationException(
                    TransactionLimitErrorCode.LIMIT_PERIOD_NOT_CONFIGURED,
                    Map.of(
                            "periodType", "",
                            "limitDetailsId", detail.getLimitDetailsId()
                    )
            );
        }

        BigDecimal safeAmount = amount == null ? BigDecimal.ZERO : amount;
        validateTransactionAmount(detail, safeAmount);
        validateBalance(profile, partyType, balanceAfter);

        TransactionLimitSubjectResolver.ResolvedLimitSubject subject =
                subjectResolver.resolve(account, profile.getSubjectKey(), partyType);

        LocalDateTime now = TenantTime.now();
        List<UsageReservation> reservations = new ArrayList<>();
        for (TransactionLimitProfilePeriod period : periods) {
            reservations.add(preparePeriodUsageReservation(
                    account,
                    profile,
                    detail,
                    period,
                    subject,
                    partyType,
                    safeAmount,
                    transactionId,
                    now
            ));
        }
        reservations.forEach(this::applyPeriodUsageReservation);
    }

    private List<Tag> resolveActiveTags(String accountId, String partyType) {
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
                    Map.of("partyType", partyType)
            );
        }
        return activeTags;
    }

    private TransactionLimitProfile resolveProfile(List<Tag> activeTags, Wallet wallet, String partyType) {
        List<Long> tagIds = activeTags.stream()
                .map(Tag::getTagId)
                .toList();
        String walletType = normalize(wallet.getWalletType());
        String currency = normalize(wallet.getCurrency());

        List<TransactionLimitProfile> profiles = profileRepository
                .findByTagIdInAndLimitTypeAndWalletTypeAndCurrencyAndStatusOrderByCreatedOnDesc(
                        tagIds,
                        TransactionLimitConstants.LIMIT_TYPE_GLOBAL,
                        walletType,
                        currency,
                        TransactionLimitConstants.STATUS_ACTIVE
                );

        if (profiles.isEmpty()) {
            throw new ApplicationException(
                    TransactionLimitErrorCode.LIMIT_PROFILE_NOT_FOUND,
                    Map.of("partyType", partyType)
            );
        }

        return profiles.get(0);
    }

    private TransactionLimitProfileDetail resolveDetail(
            TransactionLimitProfile profile,
            String partyType,
            String serviceCode,
            String requestGateway
    ) {
        List<TransactionLimitProfileDetail> details = detailRepository
                .findByLimitIdAndPartyTypeAndStatusOrderByLimitDetailsIdAsc(
                        profile.getLimitId(),
                        partyType,
                        TransactionLimitConstants.STATUS_ACTIVE
                );
        if (details.isEmpty()) {
            throw new ApplicationException(
                    TransactionLimitErrorCode.LIMIT_PROFILE_DETAILS_NOT_FOUND,
                    Map.of("partyType", partyType)
            );
        }

        String normalizedServiceCode = normalizeOrAll(serviceCode);
        String normalizedRequestGateway = normalizeOrAll(requestGateway);

        return details.stream()
                .filter(detail -> matches(detail.getOperationType(), normalizedServiceCode))
                .filter(detail -> matches(detail.getRequestGateway(), normalizedRequestGateway))
                .max(Comparator.comparingInt(detail -> specificityScore(
                        detail,
                        normalizedServiceCode,
                        normalizedRequestGateway
                )))
                .orElseThrow(() -> new ApplicationException(
                        TransactionLimitErrorCode.LIMIT_PROFILE_DETAILS_NOT_FOUND,
                        Map.of("partyType", partyType)
                ));
    }

    private void validateTransactionAmount(TransactionLimitProfileDetail detail, BigDecimal amount) {
        if (detail.getMinTxnAmount() != null && amount.compareTo(detail.getMinTxnAmount()) < 0) {
            throw new ApplicationException(
                    TransactionLimitErrorCode.LIMIT_MIN_TRANSACTION_AMOUNT_NOT_MET,
                    Map.of("limitDetailsId", detail.getLimitDetailsId())
            );
        }
        if (detail.getMaxTxnAmount() != null && amount.compareTo(detail.getMaxTxnAmount()) > 0) {
            throw new ApplicationException(
                    TransactionLimitErrorCode.LIMIT_MAX_TRANSACTION_AMOUNT_EXCEEDED,
                    Map.of("limitDetailsId", detail.getLimitDetailsId())
            );
        }
    }

    private void validateBalance(TransactionLimitProfile profile, String partyType, BigDecimal balanceAfter) {
        BigDecimal safeBalanceAfter = balanceAfter == null ? BigDecimal.ZERO : balanceAfter;
        if (TransactionLimitConstants.PARTY_DEBITOR.equals(partyType)
                && profile.getMinResidualBalance() != null
                && safeBalanceAfter.compareTo(profile.getMinResidualBalance()) < 0) {
            throw new ApplicationException(
                    TransactionLimitErrorCode.LIMIT_MIN_RESIDUAL_BALANCE_NOT_MET,
                    Map.of("limitId", profile.getLimitId())
            );
        }
        if (TransactionLimitConstants.PARTY_CREDITOR.equals(partyType)
                && profile.getMaxBalance() != null
                && safeBalanceAfter.compareTo(profile.getMaxBalance()) > 0) {
            throw new ApplicationException(
                    TransactionLimitErrorCode.LIMIT_MAX_BALANCE_EXCEEDED,
                    Map.of("limitId", profile.getLimitId())
            );
        }
    }

    private UsageReservation preparePeriodUsageReservation(
            Account account,
            TransactionLimitProfile profile,
            TransactionLimitProfileDetail detail,
            TransactionLimitProfilePeriod period,
            TransactionLimitSubjectResolver.ResolvedLimitSubject subject,
            String partyType,
            BigDecimal amount,
            String transactionId,
            LocalDateTime now
    ) {
        TransactionLimitUsage usage = lockOrCreateUsage(
                account,
                profile,
                detail,
                period,
                subject
        );
        boolean resetRequired = !isInCurrentPeriod(usage.getLastTransactionDate(), period.getPeriodType(), now);

        int currentCount = TransactionLimitConstants.PARTY_CREDITOR.equals(partyType)
                ? resetRequired ? 0 : nullToZero(usage.getPayeeCount())
                : resetRequired ? 0 : nullToZero(usage.getPayerCount());
        BigDecimal currentAmount = TransactionLimitConstants.PARTY_CREDITOR.equals(partyType)
                ? resetRequired ? BigDecimal.ZERO : nullToZero(usage.getPayeeAmount())
                : resetRequired ? BigDecimal.ZERO : nullToZero(usage.getPayerAmount());

        int nextCount = currentCount + 1;
        BigDecimal nextAmount = currentAmount.add(amount);

        validatePeriodLimit(period, nextCount, nextAmount);

        return new UsageReservation(
                usage,
                account.getAccountId(),
                partyType,
                nextCount,
                nextAmount,
                resetRequired,
                transactionId,
                now
        );
    }

    private void applyPeriodUsageReservation(UsageReservation reservation) {
        TransactionLimitUsage usage = reservation.usage();
        if (reservation.resetRequired()) {
            usage.setPayerCount(0);
            usage.setPayerAmount(BigDecimal.ZERO);
            usage.setPayeeCount(0);
            usage.setPayeeAmount(BigDecimal.ZERO);
            usage.setLastTransactionId(null);
        }

        if (TransactionLimitConstants.PARTY_CREDITOR.equals(reservation.partyType())) {
            usage.setPayeeCount(reservation.nextCount());
            usage.setPayeeAmount(reservation.nextAmount());
        } else {
            usage.setPayerCount(reservation.nextCount());
            usage.setPayerAmount(reservation.nextAmount());
        }
        usage.setAccountId(reservation.accountId());
        usage.setLastTransactionId(reservation.transactionId());
        usage.setLastTransactionDate(reservation.now());
        usageRepository.save(usage);
    }

    private TransactionLimitUsage lockOrCreateUsage(
            Account account,
            TransactionLimitProfile profile,
            TransactionLimitProfileDetail detail,
            TransactionLimitProfilePeriod period,
            TransactionLimitSubjectResolver.ResolvedLimitSubject subject
    ) {
        Optional<TransactionLimitUsage> lockedUsage = findLockedUsage(profile, detail, period, subject);
        if (lockedUsage.isPresent()) {
            return lockedUsage.get();
        }

        usageRepository.insertIfAbsent(
                subject.subjectKey(),
                subject.subjectValue(),
                account.getAccountId(),
                profile.getLimitId(),
                detail.getLimitDetailsId(),
                profile.getTagId(),
                period.getPeriodType(),
                detail.getOperationType(),
                detail.getRequestGateway(),
                BigDecimal.ZERO
        );

        return findLockedUsage(profile, detail, period, subject)
                .orElseThrow(() -> new IllegalStateException("Unable to lock transaction limit usage bucket"));
    }

    private Optional<TransactionLimitUsage> findLockedUsage(
            TransactionLimitProfile profile,
            TransactionLimitProfileDetail detail,
            TransactionLimitProfilePeriod period,
            TransactionLimitSubjectResolver.ResolvedLimitSubject subject
    ) {
        return usageRepository
                .findBySubjectKeyAndSubjectValueAndLimitIdAndLimitDetailsIdAndPeriodTypeAndOperationTypeAndRequestGateway(
                        subject.subjectKey(),
                        subject.subjectValue(),
                        profile.getLimitId(),
                        detail.getLimitDetailsId(),
                        period.getPeriodType(),
                        detail.getOperationType(),
                        detail.getRequestGateway()
                );
    }

    private boolean isInCurrentPeriod(LocalDateTime lastTransactionDate, String periodType, LocalDateTime now) {
        if (lastTransactionDate == null) {
            return false;
        }
        if (TransactionLimitConstants.PERIOD_MONTHLY.equalsIgnoreCase(periodType)) {
            return lastTransactionDate.getYear() == now.getYear()
                    && lastTransactionDate.getMonth() == now.getMonth();
        }
        return lastTransactionDate.toLocalDate().equals(now.toLocalDate());
    }

    private void validatePeriodLimit(TransactionLimitProfilePeriod period, int nextCount, BigDecimal nextAmount) {
        if (period.getMaxCount() != null && nextCount > period.getMaxCount()) {
            throw new ApplicationException(countError(period.getPeriodType()), Map.of(
                    "periodType", period.getPeriodType(),
                    "limitDetailsId", period.getLimitDetailsId()
            ));
        }
        if (period.getMaxAmount() != null && nextAmount.compareTo(period.getMaxAmount()) > 0) {
            throw new ApplicationException(amountError(period.getPeriodType()), Map.of(
                    "periodType", period.getPeriodType(),
                    "limitDetailsId", period.getLimitDetailsId()
            ));
        }
    }

    private TransactionLimitErrorCode countError(String periodType) {
        if (TransactionLimitConstants.PERIOD_MONTHLY.equalsIgnoreCase(periodType)) {
            return TransactionLimitErrorCode.LIMIT_MONTHLY_COUNT_EXCEEDED;
        }
        return TransactionLimitErrorCode.LIMIT_DAILY_COUNT_EXCEEDED;
    }

    private TransactionLimitErrorCode amountError(String periodType) {
        if (TransactionLimitConstants.PERIOD_MONTHLY.equalsIgnoreCase(periodType)) {
            return TransactionLimitErrorCode.LIMIT_MONTHLY_AMOUNT_EXCEEDED;
        }
        return TransactionLimitErrorCode.LIMIT_DAILY_AMOUNT_EXCEEDED;
    }

    private boolean matches(String configuredValue, String actualValue) {
        String configured = normalizeOrAll(configuredValue);
        return TransactionLimitConstants.OPERATION_ALL.equals(configured)
                || TransactionLimitConstants.REQUEST_GATEWAY_ALL.equals(configured)
                || configured.equals(actualValue);
    }

    private int specificityScore(
            TransactionLimitProfileDetail detail,
            String normalizedServiceCode,
            String normalizedRequestGateway
    ) {
        int score = 0;
        if (normalizeOrAll(detail.getOperationType()).equals(normalizedServiceCode)) {
            score += 2;
        }
        if (normalizeOrAll(detail.getRequestGateway()).equals(normalizedRequestGateway)) {
            score += 1;
        }
        return score;
    }

    private String resolveRequestGateway(String transactionId) {
        if (transactionId == null || transactionId.isBlank()) {
            return TransactionLimitConstants.REQUEST_GATEWAY_ALL;
        }
        Transactions transaction = transactionsRepository.findByTransactionId(transactionId);
        if (transaction == null || transaction.getRequestGateway() == null || transaction.getRequestGateway().isBlank()) {
            return TransactionLimitConstants.REQUEST_GATEWAY_ALL;
        }
        return normalize(transaction.getRequestGateway());
    }

    private boolean isSystemWallet(Wallet wallet) {
        return SYSTEM_ACCOUNT_ID.equalsIgnoreCase(wallet.getAccountId());
    }

    private boolean isLimitExemptService(String serviceCode) {
        return O2C_SERVICE_CODE.equalsIgnoreCase(normalize(serviceCode));
    }

    private String normalizeOrAll(String value) {
        if (value == null || value.isBlank()) {
            return TransactionLimitConstants.OPERATION_ALL;
        }
        return normalize(value);
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private record UsageReservation(
            TransactionLimitUsage usage,
            String accountId,
            String partyType,
            int nextCount,
            BigDecimal nextAmount,
            boolean resetRequired,
            String transactionId,
            LocalDateTime now
    ) {
    }

}
