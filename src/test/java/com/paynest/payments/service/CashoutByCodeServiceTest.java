package com.paynest.payments.service;

import com.paynest.common.Constants;
import com.paynest.config.PropertyReader;
import com.paynest.enums.AccountType;
import com.paynest.enums.RequestGateway;
import com.paynest.exception.ApplicationException;
import com.paynest.payments.dto.Authentication;
import com.paynest.payments.dto.CashoutByCodeRequest;
import com.paynest.payments.dto.CashoutByCodeResponse;
import com.paynest.payments.dto.Identifier;
import com.paynest.payments.dto.Party;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CashoutByCodeServiceTest {

    @Mock
    private PasscodeRepository passcodeRepository;
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

    @InjectMocks
    private CashoutByCodeService service;

    @Test
    void processCashout_shouldDebitHoldingWalletCreditAgentAndRedeemPasscode() {
        CashoutByCodeRequest request = validRequest();
        Passcode passcode = passcode();
        AccountIdentifier agentIdentifier = identifier("agent-1", "8888888888");
        Account agentAccount = account("agent-1", "AGENT");
        Wallet holdingWallet = wallet(900L, "SYS0001", "USD", "HOLDING");
        Wallet agentWallet = wallet(301L, "agent-1", "USD", "MAIN");

        when(passcodeRepository.findByPasscodeAndUnregisteredMsisdnAndStatus(
                "1234567890", "7777777777", PasscodeStatus.PENDING))
                .thenReturn(Optional.of(passcode));
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus(
                "MOBILE", "8888888888", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(Optional.of(agentIdentifier));
        when(accountRepository.findByAccountIdAndStatus("agent-1", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(List.of(agentAccount));
        when(walletRepository.findByAccountIdAndCurrencyAndWalletType("SYS0001", "USD", "HOLDING"))
                .thenReturn(Optional.of(holdingWallet));
        when(walletRepository.findByAccountIdAndCurrencyAndWalletType("agent-1", "USD", "MAIN"))
                .thenReturn(Optional.of(agentWallet));
        when(propertyReader.getPropertyValue("server.instance")).thenReturn("A");
        when(propertyReader.getPropertyValue("currency.factor")).thenReturn("100");

        CashoutByCodeResponse response = service.processCashout(request, false);

        assertEquals(TransactionStatus.SUCCESS, response.getResponseStatus());
        assertEquals("CASHOUT_BY_CODE", response.getOperationType());
        assertEquals(new BigDecimal("25.00"), response.getAmount());
        assertNotNull(response.getTransactionId());

        verify(balanceService).transferWalletAmount(
                holdingWallet,
                agentWallet,
                new BigDecimal("25.00"),
                "CASHOUT_BY_CODE",
                InitiatedBy.CREDITOR,
                response.getTransactionId()
        );

        ArgumentCaptor<Passcode> passcodeCaptor = ArgumentCaptor.forClass(Passcode.class);
        verify(passcodeRepository).save(passcodeCaptor.capture());
        Passcode redeemedPasscode = passcodeCaptor.getValue();
        assertEquals(PasscodeStatus.REDEEMED, redeemedPasscode.getStatus());
        assertEquals(response.getTransactionId(), redeemedPasscode.getCashoutTransactionId());
        assertNotNull(redeemedPasscode.getRedeemedOn());
    }

    @Test
    void processCashout_shouldRejectInvalidPasscode() {
        CashoutByCodeRequest request = validRequest();
        when(passcodeRepository.findByPasscodeAndUnregisteredMsisdnAndStatus(
                "1234567890", "7777777777", PasscodeStatus.PENDING))
                .thenReturn(Optional.empty());

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> service.processCashout(request, false)
        );

        assertEquals("PASSCODE_NOT_FOUND", exception.getErrorCode());
    }

    private CashoutByCodeRequest validRequest() {
        CashoutByCodeRequest request = new CashoutByCodeRequest();
        request.setOperationType("CASHOUT_BY_CODE");
        request.setRequestGateway(RequestGateway.WEB);
        request.setPreferredLang("en");
        request.setInitiatedBy(InitiatedBy.CREDITOR);
        request.setAgent(party("8888888888"));
        request.setMsisdn("7777777777");
        request.setPasscode("1234567890");
        return request;
    }

    private Party party(String identifierValue) {
        Party party = new Party();
        party.setAccountType(AccountType.AGENT);
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

    private Passcode passcode() {
        Passcode passcode = new Passcode();
        passcode.setTransactionId("RU123");
        passcode.setAmount(new BigDecimal("2500.00"));
        passcode.setCurrency("USD");
        passcode.setUnregisteredMsisdn("7777777777");
        passcode.setSenderAccountId("sub-1");
        passcode.setPasscode("1234567890");
        passcode.setStatus(PasscodeStatus.PENDING);
        return passcode;
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
