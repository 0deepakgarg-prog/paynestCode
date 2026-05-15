package com.paynest.payments.service;

import com.paynest.common.Constants;
import com.paynest.config.PropertyReader;
import com.paynest.enums.AccountType;
import com.paynest.enums.RequestGateway;
import com.paynest.exception.ApplicationException;
import com.paynest.payments.dto.Authentication;
import com.paynest.payments.dto.Identifier;
import com.paynest.payments.dto.Party;
import com.paynest.payments.dto.RegisteredToUnregisteredPaymentRequest;
import com.paynest.payments.dto.RegisteredToUnregisteredPaymentResponse;
import com.paynest.payments.dto.TransactionInfo;
import com.paynest.payments.entity.Passcode;
import com.paynest.payments.enums.InitiatedBy;
import com.paynest.payments.enums.PasscodeStatus;
import com.paynest.payments.enums.TransactionStatus;
import com.paynest.payments.repository.PasscodeRepository;
import com.paynest.users.entity.Account;
import com.paynest.users.entity.AccountIdentifier;
import com.paynest.users.entity.Wallet;
import com.paynest.users.enums.AuthType;
import com.paynest.users.enums.IdentifierType;
import com.paynest.users.enums.WalletType;
import com.paynest.users.repository.AccountIdentifierRepository;
import com.paynest.users.repository.AccountRepository;
import com.paynest.users.repository.WalletRepository;
import com.paynest.users.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisteredToUnregisteredPaymentServiceTest {

    @Mock
    private AccountIdentifierRepository accountIdentifierRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private WalletRepository walletRepository;
    @Mock
    private PropertyReader propertyReader;
    @Mock
    private TransactionsService transactionsService;
    @Mock
    private BalanceService balanceService;
    @Mock
    private AuthService authService;
    @Mock
    private PasscodeRepository passcodeRepository;
    @Mock
    private PasscodeGenerator passcodeGenerator;
    @Mock
    private PasscodeSmsNotificationService passcodeSmsNotificationService;

    @InjectMocks
    private RegisteredToUnregisteredPaymentService service;

    @Test
    void processPayment_shouldMoveFundsToHoldingWalletAndStorePasscode() {
        RegisteredToUnregisteredPaymentRequest request = validRequest();
        AccountIdentifier senderIdentifier = identifier("sub-1", "9999999999");
        Account senderAccount = account("sub-1", "SUBSCRIBER");
        Wallet senderWallet = wallet(101L, "sub-1", "USD", "MAIN");
        Wallet holdingWallet = wallet(900L, "SYS0001", "USD", "HOLDING");

        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus(
                "MOBILE", "9999999999", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(Optional.of(senderIdentifier));
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus(
                "MOBILE", "7777777777", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(Optional.empty());
        when(accountRepository.findByAccountIdAndStatus("sub-1", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(List.of(senderAccount));
        when(walletRepository.findByAccountIdAndCurrencyAndWalletType("sub-1", "USD", "MAIN"))
                .thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByAccountIdAndCurrencyAndWalletType("SYS0001", "USD", "HOLDING"))
                .thenReturn(Optional.of(holdingWallet));
        when(propertyReader.getPropertyValue("server.instance")).thenReturn("A");
        when(propertyReader.getPropertyValue("currency.factor")).thenReturn("100");
        when(passcodeGenerator.generate()).thenReturn("1234567890");
        when(passcodeRepository.existsByPasscode("1234567890")).thenReturn(false);

        RegisteredToUnregisteredPaymentResponse response = service.processPayment(request, false);

        assertEquals(TransactionStatus.SUCCESS, response.getResponseStatus());
        assertEquals("R2U", response.getOperationType());
        assertEquals(new BigDecimal("25.00"), response.getAmount());
        assertNotNull(response.getTransactionId());

        verify(balanceService).transferWalletAmount(
                senderWallet,
                holdingWallet,
                new BigDecimal("25.00"),
                "R2U",
                InitiatedBy.DEBITOR,
                response.getTransactionId()
        );

        ArgumentCaptor<Passcode> passcodeCaptor = ArgumentCaptor.forClass(Passcode.class);
        verify(passcodeRepository).save(passcodeCaptor.capture());
        Passcode savedPasscode = passcodeCaptor.getValue();
        assertEquals("1234567890", savedPasscode.getPasscode());
        assertEquals(PasscodeStatus.PENDING, savedPasscode.getStatus());
        assertEquals("7777777777", savedPasscode.getUnregisteredMsisdn());
        assertEquals("Ravi", savedPasscode.getFirstName());
        assertEquals("Kumar", savedPasscode.getLastName());
        assertEquals("KYC-777", savedPasscode.getKycDocumentId());
        assertEquals(new BigDecimal("2500.00"), savedPasscode.getAmount());
        assertEquals("extra-1", savedPasscode.getField1());

        verify(passcodeSmsNotificationService).sendPasscode(
                "9999999999",
                "7777777777",
                "1234567890"
        );
    }

    @Test
    void processPayment_shouldRejectWhenReceiverIsRegistered() {
        RegisteredToUnregisteredPaymentRequest request = validRequest();
        AccountIdentifier senderIdentifier = identifier("sub-1", "9999999999");
        AccountIdentifier receiverIdentifier = identifier("sub-2", "7777777777");

        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus(
                "MOBILE", "9999999999", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(Optional.of(senderIdentifier));
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus(
                "MOBILE", "7777777777", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(Optional.of(receiverIdentifier));

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> service.processPayment(request, false)
        );

        assertEquals("UNREGISTERED_PAYEE_ALREADY_EXISTS", exception.getErrorCode());
    }

    private RegisteredToUnregisteredPaymentRequest validRequest() {
        RegisteredToUnregisteredPaymentRequest request = new RegisteredToUnregisteredPaymentRequest();
        request.setOperationType("R2U");
        request.setRequestGateway(RequestGateway.MOBILE);
        request.setPreferredLang("en");
        request.setInitiatedBy(InitiatedBy.DEBITOR);
        request.setDebitor(party(AccountType.SUBSCRIBER, "9999999999"));
        request.setReceiverMsisdn("7777777777");
        request.setReceiverFirstName(" Ravi ");
        request.setReceiverLastName(" Kumar ");
        request.setReceiverKycDocumentId(" KYC-777 ");
        request.setField1(" extra-1 ");

        TransactionInfo transactionInfo = new TransactionInfo();
        transactionInfo.setAmount(new BigDecimal("25.00"));
        transactionInfo.setCurrency("usd");
        request.setTransaction(transactionInfo);
        return request;
    }

    private Party party(AccountType accountType, String identifierValue) {
        Party party = new Party();
        party.setAccountType(accountType);
        party.setWalletType(WalletType.MAIN);

        Identifier identifier = new Identifier();
        identifier.setType(IdentifierType.MOBILE);
        identifier.setValue(identifierValue);
        party.setIdentifier(identifier);

        Authentication authentication = new Authentication();
        authentication.setType(AuthType.PIN);
        authentication.setValue("1234");
        party.setAuthentication(authentication);
        return party;
    }

    private AccountIdentifier identifier(String accountId, String value) {
        AccountIdentifier identifier = new AccountIdentifier();
        identifier.setAccountId(accountId);
        identifier.setIdentifierValue(value);
        identifier.setIdentifierType("MOBILE");
        identifier.setStatus(Constants.ACCOUNT_STATUS_ACTIVE);
        return identifier;
    }

    private Account account(String accountId, String accountType) {
        Account account = new Account();
        account.setAccountId(accountId);
        account.setAccountType(accountType);
        account.setStatus(Constants.ACCOUNT_STATUS_ACTIVE);
        return account;
    }

    private Wallet wallet(Long walletId, String accountId, String currency, String walletType) {
        Wallet wallet = new Wallet();
        wallet.setWalletId(walletId);
        wallet.setAccountId(accountId);
        wallet.setCurrency(currency);
        wallet.setWalletType(walletType);
        wallet.setStatus(Constants.ACCOUNT_STATUS_ACTIVE);
        wallet.setIsLocked(false);
        return wallet;
    }
}
