package com.paynest.payments.service;

import com.paynest.config.PropertyReader;
import com.paynest.exception.ApplicationException;
import com.paynest.payments.dto.PasscodeDetailsResponse;
import com.paynest.payments.entity.Passcode;
import com.paynest.payments.enums.PasscodeStatus;
import com.paynest.payments.repository.PasscodeRepository;
import com.paynest.users.entity.Account;
import com.paynest.users.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasscodeQueryServiceTest {

    @Mock
    private PasscodeRepository passcodeRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private PropertyReader propertyReader;

    @InjectMocks
    private PasscodeQueryService service;

    @Test
    void getPasscodeDetails_shouldReturnPasscodeSenderReceiverAndDisplayAmount() {
        Passcode passcode = passcode();
        Account sender = senderAccount();

        when(passcodeRepository.findByPasscode("1234567890")).thenReturn(Optional.of(passcode));
        when(accountRepository.findById("sub-1")).thenReturn(Optional.of(sender));
        when(propertyReader.getPropertyValue("currency.factor")).thenReturn("100");

        PasscodeDetailsResponse response = service.getPasscodeDetails("1234567890");

        assertEquals("1234567890", response.getPasscode());
        assertEquals(PasscodeStatus.PENDING, response.getStatus());
        assertEquals(new BigDecimal("25.00"), response.getAmount());
        assertEquals("USD", response.getCurrency());
        assertEquals("sub-1", response.getSender().getAccountId());
        assertEquals("SUBSCRIBER", response.getSender().getAccountType());
        assertEquals("9999999999", response.getSender().getMsisdn());
        assertEquals("Jane", response.getSender().getFirstName());
        assertEquals("7777777777", response.getReceiver().getMsisdn());
        assertEquals("John", response.getReceiver().getFirstName());
        assertEquals("KYC123", response.getReceiver().getKycDocumentId());
    }

    @Test
    void getPasscodeDetails_shouldRejectMissingPasscode() {
        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> service.getPasscodeDetails("abc")
        );

        assertEquals("PASSCODE_NOT_FOUND", exception.getErrorCode());
    }

    private Passcode passcode() {
        Passcode passcode = new Passcode();
        passcode.setTransactionId("RU123");
        passcode.setAmount(new BigDecimal("2500.00"));
        passcode.setCurrency("USD");
        passcode.setUnregisteredMsisdn("7777777777");
        passcode.setFirstName("John");
        passcode.setLastName("Receiver");
        passcode.setKycDocumentId("KYC123");
        passcode.setSenderMsisdn("9999999999");
        passcode.setSenderAccountId("sub-1");
        passcode.setPasscode("1234567890");
        passcode.setStatus(PasscodeStatus.PENDING);
        passcode.setCreatedOn(LocalDateTime.now());
        passcode.setModifiedOn(LocalDateTime.now());
        return passcode;
    }

    private Account senderAccount() {
        Account account = new Account();
        account.setAccountId("sub-1");
        account.setAccountType("SUBSCRIBER");
        account.setFirstName("Jane");
        account.setLastName("Sender");
        return account;
    }
}
