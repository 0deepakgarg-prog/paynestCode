package com.paynest.limits.service;

import com.paynest.exception.ApplicationException;
import com.paynest.limits.TransactionLimitConstants;
import com.paynest.limits.TransactionLimitErrorCode;
import com.paynest.limits.dto.request.TransactionLimitDetailRequest;
import com.paynest.limits.dto.request.TransactionLimitPeriodRequest;
import com.paynest.limits.dto.request.UpsertTransactionLimitProfileRequest;
import com.paynest.limits.dto.response.TransactionLimitUsageResponse;
import com.paynest.limits.entity.TransactionLimitProfile;
import com.paynest.limits.entity.TransactionLimitProfileDetail;
import com.paynest.limits.entity.TransactionLimitProfilePeriod;
import com.paynest.limits.entity.TransactionLimitUsage;
import com.paynest.limits.repository.TransactionLimitProfileDetailRepository;
import com.paynest.limits.repository.TransactionLimitProfilePeriodRepository;
import com.paynest.limits.repository.TransactionLimitProfileRepository;
import com.paynest.limits.repository.TransactionLimitUsageRepository;
import com.paynest.tag.entity.Tag;
import com.paynest.tag.entity.UserTag;
import com.paynest.tag.repository.TagRepository;
import com.paynest.tag.repository.UserTagRepository;
import com.paynest.users.entity.Account;
import com.paynest.users.repository.AccountIdentifierRepository;
import com.paynest.users.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionLimitServiceTest {

    @Mock
    private TransactionLimitProfileRepository profileRepository;

    @Mock
    private TransactionLimitProfileDetailRepository detailRepository;

    @Mock
    private TransactionLimitProfilePeriodRepository periodRepository;

    @Mock
    private TransactionLimitUsageRepository usageRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private AccountIdentifierRepository accountIdentifierRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private UserTagRepository userTagRepository;

    @Mock
    private TransactionLimitSubjectResolver subjectResolver;

    private TransactionLimitService service;

    @BeforeEach
    void setUp() {
        service = new TransactionLimitService(
                profileRepository,
                detailRepository,
                periodRepository,
                usageRepository,
                tagRepository,
                accountIdentifierRepository,
                accountRepository,
                userTagRepository,
                subjectResolver
        );
    }

    @Test
    void getUtilization_shouldResolveCurrentSubjectAndReturnSharedUsageRows() {
        Account account = account("acc-1");
        UserTag userTag = userTag("acc-1", 100L);
        Tag tag = tag(100L);
        TransactionLimitProfile profile = profile(1L, 100L, TransactionLimitConstants.SUBJECT_PAN);
        TransactionLimitProfileDetail detail = detail(2L, 1L, TransactionLimitConstants.PARTY_DEBITOR);
        TransactionLimitProfilePeriod period = period(3L, 2L);
        TransactionLimitUsage usage = usage(101L, "acc-2", 1L, 2L, 100L);

        when(accountRepository.findById("acc-1")).thenReturn(Optional.of(account));
        when(userTagRepository.findByAccountId("acc-1")).thenReturn(List.of(userTag));
        when(tagRepository.findById(100L)).thenReturn(Optional.of(tag));
        when(profileRepository.findAllByOrderByCreatedOnDesc()).thenReturn(List.of(profile));
        when(subjectResolver.resolve(account, TransactionLimitConstants.SUBJECT_PAN, "CUSTOMER"))
                .thenReturn(new TransactionLimitSubjectResolver.ResolvedLimitSubject(
                        TransactionLimitConstants.SUBJECT_PAN,
                        "ABCDE1234F"
                ));
        when(usageRepository.findBySubjectKeyAndSubjectValueAndPeriodTypeOrderByLastTransactionDateDescUsageIdDesc(
                TransactionLimitConstants.SUBJECT_PAN,
                "ABCDE1234F",
                TransactionLimitConstants.PERIOD_DAILY
        )).thenReturn(List.of(usage));
        when(profileRepository.findById(1L)).thenReturn(Optional.of(profile));
        when(detailRepository.findById(2L)).thenReturn(Optional.of(detail));
        when(periodRepository.findByLimitDetailsIdAndStatusOrderByLimitPeriodIdAsc(
                2L,
                TransactionLimitConstants.STATUS_ACTIVE
        )).thenReturn(List.of(period));

        List<TransactionLimitUsageResponse> response = service.getUtilization(
                "acc-1",
                null,
                null,
                "DAILY"
        );

        assertEquals(1, response.size());
        assertEquals("acc-2", response.get(0).getAccountId());
        assertEquals(TransactionLimitConstants.SUBJECT_PAN, response.get(0).getSubjectKey());
        assertEquals("ABCDE1234F", response.get(0).getSubjectValue());
        assertEquals(3L, response.get(0).getLimitPeriodId());
        assertEquals(new BigDecimal("250.00"), response.get(0).getUsedAmount());
        assertEquals(new BigDecimal("750.00"), response.get(0).getRemainingAmount());
        verify(usageRepository, never()).findByAccountIdAndPeriodTypeOrderByLastTransactionDateDescUsageIdDesc(
                "acc-1",
                TransactionLimitConstants.PERIOD_DAILY
        );
    }

    @Test
    void getUtilization_shouldReturnEmptyWhenCurrentSubjectBucketDoesNotExist() {
        Account account = account("acc-1");
        UserTag userTag = userTag("acc-1", 100L);
        Tag tag = tag(100L);
        TransactionLimitProfile profile = profile(1L, 100L, TransactionLimitConstants.SUBJECT_PAN);

        when(accountRepository.findById("acc-1")).thenReturn(Optional.of(account));
        when(userTagRepository.findByAccountId("acc-1")).thenReturn(List.of(userTag));
        when(tagRepository.findById(100L)).thenReturn(Optional.of(tag));
        when(profileRepository.findAllByOrderByCreatedOnDesc()).thenReturn(List.of(profile));
        when(subjectResolver.resolve(account, TransactionLimitConstants.SUBJECT_PAN, "CUSTOMER"))
                .thenReturn(new TransactionLimitSubjectResolver.ResolvedLimitSubject(
                        TransactionLimitConstants.SUBJECT_PAN,
                        "ZZZZZ9999"
                ));
        when(usageRepository.findBySubjectKeyAndSubjectValueAndPeriodTypeOrderByLastTransactionDateDescUsageIdDesc(
                TransactionLimitConstants.SUBJECT_PAN,
                "ZZZZZ9999",
                TransactionLimitConstants.PERIOD_DAILY
        )).thenReturn(List.of());

        List<TransactionLimitUsageResponse> response = service.getUtilization(
                "acc-1",
                null,
                null,
                "DAILY"
        );

        assertTrue(response.isEmpty());
        verify(usageRepository, never()).findByAccountIdAndPeriodTypeOrderByLastTransactionDateDescUsageIdDesc(
                "acc-1",
                TransactionLimitConstants.PERIOD_DAILY
        );
        verify(usageRepository, never()).findByAccountIdOrderByLastTransactionDateDescUsageIdDesc("acc-1");
    }

    @Test
    void getUtilization_shouldBlockWhenAccountHasNoActiveTag() {
        Account account = account("acc-1");

        when(accountRepository.findById("acc-1")).thenReturn(Optional.of(account));
        when(userTagRepository.findByAccountId("acc-1")).thenReturn(List.of());

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> service.getUtilization("acc-1", null, null, "DAILY")
        );

        assertEquals(TransactionLimitErrorCode.LIMIT_TAG_NOT_FOUND.code(), exception.getErrorCode());
    }

    @Test
    void createProfile_shouldRejectFractionalStoredPeriodAmount() {
        Tag tag = tag(100L);
        UpsertTransactionLimitProfileRequest request = new UpsertTransactionLimitProfileRequest();
        request.setLimitName("Premium Customer MAIN USD Global Limit");
        request.setTagId(100L);
        request.setLimitType(TransactionLimitConstants.LIMIT_TYPE_GLOBAL);
        request.setSubjectKey(TransactionLimitConstants.SUBJECT_ACCOUNT_ID);
        request.setStatus(TransactionLimitConstants.STATUS_ACTIVE);
        request.setWalletType("MAIN");
        request.setCurrency("USD");
        request.setMaxBalance(new BigDecimal("100000"));

        TransactionLimitDetailRequest detailRequest = new TransactionLimitDetailRequest();
        detailRequest.setPartyType(TransactionLimitConstants.PARTY_DEBITOR);
        detailRequest.setStatus(TransactionLimitConstants.STATUS_ACTIVE);
        detailRequest.setOperationType(TransactionLimitConstants.OPERATION_ALL);
        detailRequest.setRequestGateway(TransactionLimitConstants.REQUEST_GATEWAY_ALL);
        detailRequest.setMaxTxnAmount(new BigDecimal("10000"));

        TransactionLimitPeriodRequest periodRequest = new TransactionLimitPeriodRequest();
        periodRequest.setPeriodType(TransactionLimitConstants.PERIOD_DAILY);
        periodRequest.setStatus(TransactionLimitConstants.STATUS_ACTIVE);
        periodRequest.setMaxAmount(new BigDecimal("10000.50"));
        detailRequest.setPeriods(List.of(periodRequest));
        request.setLimitDetails(List.of(detailRequest));

        when(tagRepository.findById(100L)).thenReturn(Optional.of(tag));
        when(profileRepository.save(any(TransactionLimitProfile.class))).thenAnswer(invocation -> {
            TransactionLimitProfile profile = invocation.getArgument(0);
            profile.setLimitId(1L);
            return profile;
        });
        when(detailRepository.save(any(TransactionLimitProfileDetail.class))).thenAnswer(invocation -> {
            TransactionLimitProfileDetail detail = invocation.getArgument(0);
            detail.setLimitDetailsId(2L);
            return detail;
        });

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> service.createProfile(request)
        );

        assertEquals("INVALID_REQUEST", exception.getErrorCode());
        verify(periodRepository, never()).save(any(TransactionLimitProfilePeriod.class));
    }

    private Account account(String accountId) {
        Account account = new Account();
        account.setAccountId(accountId);
        return account;
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

    private TransactionLimitProfile profile(Long limitId, Long tagId, String subjectKey) {
        TransactionLimitProfile profile = new TransactionLimitProfile();
        profile.setLimitId(limitId);
        profile.setLimitName("Full KYC Global");
        profile.setTagId(tagId);
        profile.setLimitType(TransactionLimitConstants.LIMIT_TYPE_GLOBAL);
        profile.setSubjectKey(subjectKey);
        profile.setWalletType("MAIN");
        profile.setCurrency("USD");
        profile.setStatus(TransactionLimitConstants.STATUS_ACTIVE);
        return profile;
    }

    private TransactionLimitProfileDetail detail(Long limitDetailsId, Long limitId, String partyType) {
        TransactionLimitProfileDetail detail = new TransactionLimitProfileDetail();
        detail.setLimitDetailsId(limitDetailsId);
        detail.setLimitId(limitId);
        detail.setPartyType(partyType);
        detail.setOperationType(TransactionLimitConstants.OPERATION_ALL);
        detail.setRequestGateway(TransactionLimitConstants.REQUEST_GATEWAY_ALL);
        detail.setStatus(TransactionLimitConstants.STATUS_ACTIVE);
        return detail;
    }

    private TransactionLimitProfilePeriod period(Long limitPeriodId, Long limitDetailsId) {
        TransactionLimitProfilePeriod period = new TransactionLimitProfilePeriod();
        period.setLimitPeriodId(limitPeriodId);
        period.setLimitDetailsId(limitDetailsId);
        period.setPeriodType(TransactionLimitConstants.PERIOD_DAILY);
        period.setMaxCount(5);
        period.setMaxAmount(new BigDecimal("1000.00"));
        period.setStatus(TransactionLimitConstants.STATUS_ACTIVE);
        return period;
    }

    private TransactionLimitUsage usage(
            Long usageId,
            String accountId,
            Long limitId,
            Long limitDetailsId,
            Long tagId
    ) {
        TransactionLimitUsage usage = new TransactionLimitUsage();
        usage.setUsageId(usageId);
        usage.setAccountId(accountId);
        usage.setLimitId(limitId);
        usage.setLimitDetailsId(limitDetailsId);
        usage.setTagId(tagId);
        usage.setSubjectKey(TransactionLimitConstants.SUBJECT_PAN);
        usage.setSubjectValue("ABCDE1234F");
        usage.setPeriodType(TransactionLimitConstants.PERIOD_DAILY);
        usage.setOperationType(TransactionLimitConstants.OPERATION_ALL);
        usage.setRequestGateway(TransactionLimitConstants.REQUEST_GATEWAY_ALL);
        usage.setPayerCount(2);
        usage.setPayerAmount(new BigDecimal("250.00"));
        usage.setPayeeCount(0);
        usage.setPayeeAmount(BigDecimal.ZERO);
        return usage;
    }
}
