package com.paynest.payments.service;

import com.paynest.payments.entity.RecentRecipient;
import com.paynest.payments.entity.Transactions;
import com.paynest.payments.repository.RecentRecipientRepository;
import com.paynest.users.entity.Account;
import com.paynest.users.repository.AccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecentRecipientServiceTest {

    @Mock
    private RecentRecipientRepository recentRecipientRepository;

    @Mock
    private AccountRepository accountRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recordSuccessfulPayment_shouldUpsertRecipientForSender() {
        RecentRecipientService service = new RecentRecipientService(recentRecipientRepository, accountRepository);
        Transactions transaction = new Transactions();
        transaction.setTransactionId("txn-1");
        transaction.setServiceCode("ipsp2p");
        transaction.setDebitorAccountId("sender-1");
        transaction.setCreditorAccountId("receiver-1");
        transaction.setCreditorCurrency("usd");
        transaction.setCreditorWalletType("main");
        transaction.setCreditorIdentifierType("MOBILE");
        transaction.setCreditorIdentifierValue("5550001");
        transaction.setTransferOn(LocalDateTime.of(2026, 5, 21, 10, 15));
        transaction.setField1("f1");
        transaction.setField5("f5");

        Account recipient = new Account();
        recipient.setAccountId("receiver-1");
        recipient.setAccountType("SUBSCRIBER");
        recipient.setFirstName("Jane");
        recipient.setLastName("Doe");
        when(accountRepository.findById("receiver-1")).thenReturn(Optional.of(recipient));

        service.recordSuccessfulPayment(transaction);

        verify(recentRecipientRepository).upsertRecentRecipient(
                eq("sender-1"),
                eq("receiver-1"),
                eq("IPSP2P"),
                eq("USD"),
                eq("MAIN"),
                eq("SUBSCRIBER"),
                eq("MOBILE"),
                eq("5550001"),
                eq("Jane Doe"),
                eq("txn-1"),
                eq(LocalDateTime.of(2026, 5, 21, 10, 15)),
                eq("f1"),
                eq(null),
                eq(null),
                eq(null),
                eq("f5"),
                any(LocalDateTime.class)
        );
    }

    @Test
    void getRecentRecipients_shouldUseAuthenticatedAccountWhenAccountIdIsOmitted() {
        RecentRecipientService service = new RecentRecipientService(recentRecipientRepository, accountRepository);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("sender-1", "pin", List.of())
        );
        RecentRecipient recipient = new RecentRecipient();
        recipient.setAccountId("sender-1");
        recipient.setRecipientAccountId("receiver-1");
        recipient.setServiceCode("IPSP2P");
        recipient.setCurrency("USD");
        recipient.setWalletType("MAIN");
        recipient.setPaymentCount(2L);
        when(recentRecipientRepository.findByAccountIdOrderByLastPaidAtDesc(eq("sender-1"), any(Pageable.class)))
                .thenReturn(List.of(recipient));

        var response = service.getRecentRecipients(null, null, 10);

        assertEquals(1, response.size());
        assertEquals("receiver-1", response.get(0).getRecipientAccountId());
        assertEquals(2L, response.get(0).getPaymentCount());
    }
}
