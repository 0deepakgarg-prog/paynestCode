package com.paynest.limits.service;

import com.paynest.exception.ApplicationException;
import com.paynest.limits.TransactionLimitConstants;
import com.paynest.limits.TransactionLimitErrorCode;
import com.paynest.users.entity.Account;
import com.paynest.users.entity.KycDocument;
import com.paynest.users.repository.KycDocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionLimitSubjectResolverTest {

    @Mock
    private KycDocumentRepository kycDocumentRepository;

    @Test
    void resolve_shouldUseApprovedActivePanDocumentAsRawSubjectValue() {
        TransactionLimitSubjectResolver resolver = new TransactionLimitSubjectResolver(
                kycDocumentRepository
        );
        Account account = account("acc-1");

        KycDocument pan = new KycDocument();
        pan.setAccountId("acc-1");
        pan.setDocumentType("PAN");
        pan.setDocumentNumber("abcde1234f");
        pan.setVerificationStatus("APPROVED");
        pan.setIsActive(true);

        when(kycDocumentRepository.findByAccountId("acc-1")).thenReturn(List.of(pan));

        TransactionLimitSubjectResolver.ResolvedLimitSubject subject =
                resolver.resolve(account, TransactionLimitConstants.SUBJECT_PAN, TransactionLimitConstants.PARTY_DEBITOR);

        assertEquals(TransactionLimitConstants.SUBJECT_PAN, subject.subjectKey());
        assertEquals("ABCDE1234F", subject.subjectValue());
    }

    @Test
    void resolve_shouldBlockWhenConfiguredSubjectValueIsMissing() {
        TransactionLimitSubjectResolver resolver = new TransactionLimitSubjectResolver(
                kycDocumentRepository
        );
        Account account = account("acc-1");

        when(kycDocumentRepository.findByAccountId("acc-1")).thenReturn(List.of());

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> resolver.resolve(account, TransactionLimitConstants.SUBJECT_PAN, TransactionLimitConstants.PARTY_DEBITOR)
        );

        assertEquals(TransactionLimitErrorCode.LIMIT_SUBJECT_VALUE_NOT_FOUND.code(), exception.getErrorCode());
    }

    private Account account(String accountId) {
        Account account = new Account();
        account.setAccountId(accountId);
        return account;
    }
}
