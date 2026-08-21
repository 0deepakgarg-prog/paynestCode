package com.paynest.limits.service;

import com.paynest.exception.ApplicationException;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionLimitValidatorTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private UserTagRepository userTagRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private TransactionLimitProfileRepository profileRepository;

    @Mock
    private TransactionLimitProfileDetailRepository detailRepository;

    @Mock
    private TransactionLimitProfilePeriodRepository periodRepository;

    @Mock
    private TransactionLimitUsageRepository usageRepository;

    @Mock
    private TransactionsRepository transactionsRepository;

    @Mock
    private TransactionLimitSubjectResolver subjectResolver;

    private TransactionLimitValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TransactionLimitValidator(
                accountRepository,
                userTagRepository,
                tagRepository,
                profileRepository,
                detailRepository,
                periodRepository,
                usageRepository,
                transactionsRepository,
                subjectResolver
        );
    }

    @Test
    void validateAndReserve_shouldSkipO2CTransactions() {
        Wallet debitorWallet = wallet(10L, "operator-1");
        Wallet creditorWallet = wallet(20L, "agent-1");

        validator.validateAndReserve(
                debitorWallet,
                creditorWallet,
                new BigDecimal("500.00"),
                new BigDecimal("500.00"),
                new BigDecimal("9500.00"),
                new BigDecimal("1500.00"),
                "O2C",
                "txn-o2c-1"
        );

        verifyNoInteractions(
                accountRepository,
                userTagRepository,
                tagRepository,
                profileRepository,
                detailRepository,
                periodRepository,
                usageRepository,
                transactionsRepository,
                subjectResolver
        );
    }

    @Test
    void validateAndReserve_shouldBlockWhenAccountHasNoActiveLimitTag() {
        Wallet debitorWallet = wallet(10L, "acc-1");
        Wallet systemCreditorWallet = wallet(20L, "SYS0001");
        Account account = account("acc-1");

        when(accountRepository.findById("acc-1")).thenReturn(Optional.of(account));
        when(userTagRepository.findByAccountId("acc-1")).thenReturn(List.of());

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> validator.validateAndReserve(
                        debitorWallet,
                        systemCreditorWallet,
                        new BigDecimal("50.00"),
                        BigDecimal.ZERO,
                        new BigDecimal("950.00"),
                        BigDecimal.ZERO,
                        "U2U",
                        "txn-1"
                )
        );

        assertEquals(TransactionLimitErrorCode.LIMIT_TAG_NOT_FOUND.code(), exception.getErrorCode());
        verify(usageRepository, never()).save(any(TransactionLimitUsage.class));
    }

    @Test
    void validateAndReserve_shouldIncrementExistingDailyDebitorUsage() {
        Wallet debitorWallet = wallet(10L, "acc-1");
        Wallet systemCreditorWallet = wallet(20L, "SYS0001");
        Account account = account("acc-1");
        UserTag userTag = userTag("acc-1", 100L);
        Tag tag = tag(100L);
        TransactionLimitProfile profile = profile(1L, 100L);
        TransactionLimitProfileDetail detail = detail(2L, 1L);
        TransactionLimitProfilePeriod dailyPeriod = period(3L, 2L);
        TransactionLimitUsage usage = usage(1L, 2L, 100L);

        Transactions transaction = new Transactions();
        transaction.setTransactionId("txn-1");
        transaction.setRequestGateway("MOBILE");

        when(transactionsRepository.findByTransactionId("txn-1")).thenReturn(transaction);
        when(accountRepository.findById("acc-1")).thenReturn(Optional.of(account));
        when(userTagRepository.findByAccountId("acc-1")).thenReturn(List.of(userTag));
        when(tagRepository.findById(100L)).thenReturn(Optional.of(tag));
        when(profileRepository.findByTagIdInAndLimitTypeAndWalletTypeAndCurrencyAndStatusOrderByCreatedOnDesc(
                anyList(),
                eq(TransactionLimitConstants.LIMIT_TYPE_GLOBAL),
                eq("MAIN"),
                eq("USD"),
                eq(TransactionLimitConstants.STATUS_ACTIVE)
        )).thenReturn(List.of(profile));
        when(detailRepository.findByLimitIdAndPartyTypeAndStatusOrderByLimitDetailsIdAsc(
                1L,
                TransactionLimitConstants.PARTY_DEBITOR,
                TransactionLimitConstants.STATUS_ACTIVE
        )).thenReturn(List.of(detail));
        when(periodRepository.findByLimitDetailsIdAndStatusOrderByLimitPeriodIdAsc(
                2L,
                TransactionLimitConstants.STATUS_ACTIVE
        )).thenReturn(List.of(dailyPeriod));
        when(subjectResolver.resolve(account, TransactionLimitConstants.SUBJECT_ACCOUNT_ID, TransactionLimitConstants.PARTY_DEBITOR))
                .thenReturn(new TransactionLimitSubjectResolver.ResolvedLimitSubject(
                        TransactionLimitConstants.SUBJECT_ACCOUNT_ID,
                        "acc-1"
                ));
        when(usageRepository.findBySubjectKeyAndSubjectValueAndLimitIdAndLimitDetailsIdAndPeriodTypeAndOperationTypeAndRequestGateway(
                eq(TransactionLimitConstants.SUBJECT_ACCOUNT_ID),
                eq("acc-1"),
                eq(1L),
                eq(2L),
                eq(TransactionLimitConstants.PERIOD_DAILY),
                eq(TransactionLimitConstants.OPERATION_ALL),
                eq(TransactionLimitConstants.REQUEST_GATEWAY_ALL)
        )).thenReturn(Optional.of(usage));

        validator.validateAndReserve(
                debitorWallet,
                systemCreditorWallet,
                new BigDecimal("50.00"),
                BigDecimal.ZERO,
                new BigDecimal("950.00"),
                BigDecimal.ZERO,
                "U2U",
                "txn-1"
        );

        ArgumentCaptor<TransactionLimitUsage> usageCaptor = ArgumentCaptor.forClass(TransactionLimitUsage.class);
        verify(usageRepository).save(usageCaptor.capture());

        TransactionLimitUsage savedUsage = usageCaptor.getValue();
        assertEquals(2, savedUsage.getPayerCount());
        assertEquals(new BigDecimal("150.00"), savedUsage.getPayerAmount());
        assertEquals("txn-1", savedUsage.getLastTransactionId());
    }

    @Test
    void validateAndReserve_shouldResetExpiredDailyUsageOnSameRow() {
        Wallet debitorWallet = wallet(10L, "acc-1");
        Wallet systemCreditorWallet = wallet(20L, "SYS0001");
        Account account = account("acc-1");
        UserTag userTag = userTag("acc-1", 100L);
        Tag tag = tag(100L);
        TransactionLimitProfile profile = profile(1L, 100L);
        TransactionLimitProfileDetail detail = detail(2L, 1L);
        TransactionLimitProfilePeriod dailyPeriod = period(3L, 2L);
        TransactionLimitUsage usage = usage(1L, 2L, 100L);

        usage.setLastTransactionDate(LocalDateTime.now().minusDays(1));
        usage.setPayerCount(3);
        usage.setPayerAmount(new BigDecimal("490.00"));

        when(accountRepository.findById("acc-1")).thenReturn(Optional.of(account));
        when(userTagRepository.findByAccountId("acc-1")).thenReturn(List.of(userTag));
        when(tagRepository.findById(100L)).thenReturn(Optional.of(tag));
        when(profileRepository.findByTagIdInAndLimitTypeAndWalletTypeAndCurrencyAndStatusOrderByCreatedOnDesc(
                anyList(),
                eq(TransactionLimitConstants.LIMIT_TYPE_GLOBAL),
                eq("MAIN"),
                eq("USD"),
                eq(TransactionLimitConstants.STATUS_ACTIVE)
        )).thenReturn(List.of(profile));
        when(detailRepository.findByLimitIdAndPartyTypeAndStatusOrderByLimitDetailsIdAsc(
                1L,
                TransactionLimitConstants.PARTY_DEBITOR,
                TransactionLimitConstants.STATUS_ACTIVE
        )).thenReturn(List.of(detail));
        when(periodRepository.findByLimitDetailsIdAndStatusOrderByLimitPeriodIdAsc(
                2L,
                TransactionLimitConstants.STATUS_ACTIVE
        )).thenReturn(List.of(dailyPeriod));
        when(subjectResolver.resolve(account, TransactionLimitConstants.SUBJECT_ACCOUNT_ID, TransactionLimitConstants.PARTY_DEBITOR))
                .thenReturn(new TransactionLimitSubjectResolver.ResolvedLimitSubject(
                        TransactionLimitConstants.SUBJECT_ACCOUNT_ID,
                        "acc-1"
                ));
        when(usageRepository.findBySubjectKeyAndSubjectValueAndLimitIdAndLimitDetailsIdAndPeriodTypeAndOperationTypeAndRequestGateway(
                eq(TransactionLimitConstants.SUBJECT_ACCOUNT_ID),
                eq("acc-1"),
                eq(1L),
                eq(2L),
                eq(TransactionLimitConstants.PERIOD_DAILY),
                eq(TransactionLimitConstants.OPERATION_ALL),
                eq(TransactionLimitConstants.REQUEST_GATEWAY_ALL)
        )).thenReturn(Optional.of(usage));

        validator.validateAndReserve(
                debitorWallet,
                systemCreditorWallet,
                new BigDecimal("20.00"),
                BigDecimal.ZERO,
                new BigDecimal("950.00"),
                BigDecimal.ZERO,
                "U2U",
                "txn-2"
        );

        ArgumentCaptor<TransactionLimitUsage> usageCaptor = ArgumentCaptor.forClass(TransactionLimitUsage.class);
        verify(usageRepository).save(usageCaptor.capture());

        TransactionLimitUsage savedUsage = usageCaptor.getValue();
        assertEquals(1, savedUsage.getPayerCount());
        assertEquals(new BigDecimal("20.00"), savedUsage.getPayerAmount());
        assertEquals("txn-2", savedUsage.getLastTransactionId());
    }

    @Test
    void validateAndReserve_shouldBlockWhenDailyDebitorAmountWouldExceedLimit() {
        Wallet debitorWallet = wallet(10L, "acc-1");
        Wallet systemCreditorWallet = wallet(20L, "SYS0001");
        Account account = account("acc-1");
        UserTag userTag = userTag("acc-1", 100L);
        Tag tag = tag(100L);
        TransactionLimitProfile profile = profile(1L, 100L);
        TransactionLimitProfileDetail detail = detail(2L, 1L);
        TransactionLimitProfilePeriod dailyPeriod = period(3L, 2L);
        TransactionLimitUsage usage = usage(1L, 2L, 100L);

        usage.setPayerAmount(new BigDecimal("490.00"));

        when(accountRepository.findById("acc-1")).thenReturn(Optional.of(account));
        when(userTagRepository.findByAccountId("acc-1")).thenReturn(List.of(userTag));
        when(tagRepository.findById(100L)).thenReturn(Optional.of(tag));
        when(profileRepository.findByTagIdInAndLimitTypeAndWalletTypeAndCurrencyAndStatusOrderByCreatedOnDesc(
                anyList(),
                eq(TransactionLimitConstants.LIMIT_TYPE_GLOBAL),
                eq("MAIN"),
                eq("USD"),
                eq(TransactionLimitConstants.STATUS_ACTIVE)
        )).thenReturn(List.of(profile));
        when(detailRepository.findByLimitIdAndPartyTypeAndStatusOrderByLimitDetailsIdAsc(
                1L,
                TransactionLimitConstants.PARTY_DEBITOR,
                TransactionLimitConstants.STATUS_ACTIVE
        )).thenReturn(List.of(detail));
        when(periodRepository.findByLimitDetailsIdAndStatusOrderByLimitPeriodIdAsc(
                2L,
                TransactionLimitConstants.STATUS_ACTIVE
        )).thenReturn(List.of(dailyPeriod));
        when(subjectResolver.resolve(account, TransactionLimitConstants.SUBJECT_ACCOUNT_ID, TransactionLimitConstants.PARTY_DEBITOR))
                .thenReturn(new TransactionLimitSubjectResolver.ResolvedLimitSubject(
                        TransactionLimitConstants.SUBJECT_ACCOUNT_ID,
                        "acc-1"
                ));
        when(usageRepository.findBySubjectKeyAndSubjectValueAndLimitIdAndLimitDetailsIdAndPeriodTypeAndOperationTypeAndRequestGateway(
                eq(TransactionLimitConstants.SUBJECT_ACCOUNT_ID),
                eq("acc-1"),
                eq(1L),
                eq(2L),
                eq(TransactionLimitConstants.PERIOD_DAILY),
                eq(TransactionLimitConstants.OPERATION_ALL),
                eq(TransactionLimitConstants.REQUEST_GATEWAY_ALL)
        )).thenReturn(Optional.of(usage));

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> validator.validateAndReserve(
                        debitorWallet,
                        systemCreditorWallet,
                        new BigDecimal("20.00"),
                        BigDecimal.ZERO,
                        new BigDecimal("950.00"),
                        BigDecimal.ZERO,
                        "U2U",
                        "txn-1"
                )
        );

        assertEquals(TransactionLimitErrorCode.LIMIT_DAILY_AMOUNT_EXCEEDED.code(), exception.getErrorCode());
        verify(usageRepository, never()).save(any(TransactionLimitUsage.class));
    }

    private Account account(String accountId) {
        Account account = new Account();
        account.setAccountId(accountId);
        return account;
    }

    private Wallet wallet(Long walletId, String accountId) {
        Wallet wallet = new Wallet();
        wallet.setWalletId(walletId);
        wallet.setAccountId(accountId);
        wallet.setWalletType("MAIN");
        wallet.setCurrency("USD");
        return wallet;
    }

    private UserTag userTag(String accountId, Long tagId) {
        UserTag userTag = new UserTag();
        userTag.setAccountId(accountId);
        userTag.setTagId(tagId);
        userTag.setStatus(TransactionLimitConstants.STATUS_ACTIVE);
        return userTag;
    }

    private Tag tag(Long tagId) {
        Tag tag = new Tag();
        tag.setTagId(tagId);
        tag.setTagCode("FULL_KYC");
        tag.setTagName("Full KYC");
        tag.setStatus(TransactionLimitConstants.STATUS_ACTIVE);
        return tag;
    }

    private TransactionLimitProfile profile(Long limitId, Long tagId) {
        TransactionLimitProfile profile = new TransactionLimitProfile();
        profile.setLimitId(limitId);
        profile.setLimitName("Full KYC Global");
        profile.setTagId(tagId);
        profile.setLimitType(TransactionLimitConstants.LIMIT_TYPE_GLOBAL);
        profile.setSubjectKey(TransactionLimitConstants.SUBJECT_ACCOUNT_ID);
        profile.setWalletType("MAIN");
        profile.setCurrency("USD");
        profile.setStatus(TransactionLimitConstants.STATUS_ACTIVE);
        return profile;
    }

    private TransactionLimitProfileDetail detail(Long limitDetailsId, Long limitId) {
        TransactionLimitProfileDetail detail = new TransactionLimitProfileDetail();
        detail.setLimitDetailsId(limitDetailsId);
        detail.setLimitId(limitId);
        detail.setPartyType(TransactionLimitConstants.PARTY_DEBITOR);
        detail.setOperationType(TransactionLimitConstants.OPERATION_ALL);
        detail.setRequestGateway(TransactionLimitConstants.REQUEST_GATEWAY_ALL);
        detail.setMinTxnAmount(BigDecimal.ONE);
        detail.setMaxTxnAmount(new BigDecimal("100.00"));
        detail.setStatus(TransactionLimitConstants.STATUS_ACTIVE);
        return detail;
    }

    private TransactionLimitProfilePeriod period(Long limitPeriodId, Long limitDetailsId) {
        TransactionLimitProfilePeriod period = new TransactionLimitProfilePeriod();
        period.setLimitPeriodId(limitPeriodId);
        period.setLimitDetailsId(limitDetailsId);
        period.setPeriodType(TransactionLimitConstants.PERIOD_DAILY);
        period.setMaxCount(3);
        period.setMaxAmount(new BigDecimal("500.00"));
        period.setStatus(TransactionLimitConstants.STATUS_ACTIVE);
        return period;
    }

    private TransactionLimitUsage usage(Long limitId, Long limitDetailsId, Long tagId) {
        TransactionLimitUsage usage = new TransactionLimitUsage();
        usage.setLimitId(limitId);
        usage.setLimitDetailsId(limitDetailsId);
        usage.setTagId(tagId);
        usage.setSubjectKey(TransactionLimitConstants.SUBJECT_ACCOUNT_ID);
        usage.setSubjectValue("acc-1");
        usage.setAccountId("acc-1");
        usage.setPeriodType(TransactionLimitConstants.PERIOD_DAILY);
        usage.setOperationType(TransactionLimitConstants.OPERATION_ALL);
        usage.setRequestGateway(TransactionLimitConstants.REQUEST_GATEWAY_ALL);
        usage.setPayerCount(1);
        usage.setPayerAmount(new BigDecimal("100.00"));
        usage.setPayeeCount(0);
        usage.setPayeeAmount(BigDecimal.ZERO);
        usage.setLastTransactionDate(LocalDateTime.now());
        return usage;
    }
}
