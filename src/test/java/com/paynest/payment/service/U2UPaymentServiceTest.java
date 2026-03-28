package com.paynest.payment.service;

import com.paynest.common.Constants;
import com.paynest.config.PropertyReader;
import com.paynest.entity.Account;
import com.paynest.entity.AccountIdentifier;
import com.paynest.entity.Wallet;
import com.paynest.enums.AccountType;
import com.paynest.enums.AuthType;
import com.paynest.enums.IdentifierType;
import com.paynest.enums.InitiatedBy;
import com.paynest.enums.RequestGateway;
import com.paynest.enums.TransactionStatus;
import com.paynest.enums.WalletType;
import com.paynest.exception.ApplicationException;
import com.paynest.payment.dto.Authentication;
import com.paynest.payment.dto.Identifier;
import com.paynest.payment.dto.Party;
import com.paynest.payment.dto.TransactionInfo;
import com.paynest.payment.dto.U2UPaymentRequest;
import com.paynest.payment.dto.U2UPaymentResponse;
import com.paynest.payment.validation.BasePaymentRequestValidator;
import com.paynest.repository.AccountIdentifierRepository;
import com.paynest.repository.AccountRepository;
import com.paynest.repository.WalletRepository;
import com.paynest.security.JWTUtils;
import com.paynest.service.AuthService;
import com.paynest.service.BalanceService;
import com.paynest.service.TransactionsService;
import com.paynest.tenant.TraceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class U2UPaymentServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountIdentifierRepository accountIdentifierRepository;

    @Mock
    private BasePaymentRequestValidator basePaymentRequestValidator;

    @Mock
    private PropertyReader propertyReader;

    @Mock
    private TransactionsService transactionsService;

    @Mock
    private BalanceService balanceService;

    @Mock
    private AuthService authService;

    @InjectMocks
    private U2UPaymentService u2uPaymentService;

    @Test
    void processPayment_shouldUseWalletMatchingCurrencyAndWalletType() {
        U2UPaymentRequest request = validRequest();
        AccountIdentifier debitorIdentifier = identifier("acc-1", "9999999999", "MOBILE", 10L);
        AccountIdentifier creditorIdentifier = identifier("acc-2", "8888888888", "MOBILE", 20L);
        Account debitorAccount = account("acc-1", "SUBSCRIBER");
        Account creditorAccount = account("acc-2", "SUBSCRIBER");
        Wallet debitorWallet = wallet(101L, "acc-1", "USD", "MAIN");
        Wallet creditorWallet = wallet(202L, "acc-2", "USD", "MAIN");

        doNothing().when(basePaymentRequestValidator).validate(request);
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus("MOBILE", "9999999999", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(Optional.of(debitorIdentifier));
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus("MOBILE", "8888888888", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(Optional.of(creditorIdentifier));
        when(accountRepository.findByAccountIdAndStatus("acc-1", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(List.of(debitorAccount));
        when(accountRepository.findByAccountIdAndStatus("acc-2", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(List.of(creditorAccount));
        when(walletRepository.findByAccountIdAndCurrencyAndWalletType("acc-1", "USD", "MAIN"))
                .thenReturn(Optional.of(debitorWallet));
        when(walletRepository.findByAccountIdAndCurrencyAndWalletType("acc-2", "USD", "MAIN"))
                .thenReturn(Optional.of(creditorWallet));
        when(propertyReader.getPropertyValue("server.instance")).thenReturn("A");

        TraceContext.setTraceId("trace-1");
        try (MockedStatic<JWTUtils> jwtUtils = org.mockito.Mockito.mockStatic(JWTUtils.class)) {
            jwtUtils.when(JWTUtils::getCurrentAccountId).thenReturn("acc-1");
            jwtUtils.when(JWTUtils::getCurrentAccountType).thenReturn("SUBSCRIBER");
            jwtUtils.when(JWTUtils::getCurrentAuthType).thenReturn("PIN");

            U2UPaymentResponse response = u2uPaymentService.processPayment(request, true);

            assertEquals(TransactionStatus.SUCCESS, response.getResponseStatus());
            assertEquals("USD", response.getCurrency());
            assertEquals("U2U", response.getOperationType());
            assertNotNull(response.getTransactionId());

            verify(authService).validateAuthentication("1234", AuthType.PIN, debitorIdentifier);
            verify(transactionsService).generateTransactionRecord(
                    any(),
                    eq(new BigDecimal("10.50")),
                    eq(RequestGateway.MOBILE.name()),
                    eq("U2U"),
                    eq("en"),
                    eq(debitorIdentifier),
                    eq(creditorIdentifier),
                    eq("SUBSCRIBER"),
                    eq("SUBSCRIBER"),
                    eq(debitorWallet),
                    eq(creditorWallet),
                    eq(InitiatedBy.DEBITOR)
            );
            verify(balanceService).transferWalletAmount(
                    debitorWallet,
                    creditorWallet,
                    new BigDecimal("10.50"),
                    "U2U",
                    InitiatedBy.DEBITOR,
                    response.getTransactionId()
            );
        } finally {
            TraceContext.clear();
        }
    }

    @Test
    void processPayment_shouldRejectTransfersToSameAccountEvenWithDifferentIdentifiers() {
        U2UPaymentRequest request = validRequest();
        request.getCreditor().getIdentifier().setType(IdentifierType.LOGINID);
        request.getCreditor().getIdentifier().setValue("same-login-id");

        AccountIdentifier debitorIdentifier = identifier("acc-1", "9999999999", "MOBILE", 10L);
        AccountIdentifier creditorIdentifier = identifier("acc-1", "same-login-id", "LOGINID", 10L);

        doNothing().when(basePaymentRequestValidator).validate(request);
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus("MOBILE", "9999999999", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(Optional.of(debitorIdentifier));
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus("LOGINID", "same-login-id", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(Optional.of(creditorIdentifier));

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> u2uPaymentService.processPayment(request, false)
        );

        assertEquals("SELF_TRANSFER_NOT_ALLOWED", exception.getErrorCode());
        verify(balanceService, never()).transferWalletAmount(any(), any(), any(), any(), any(), any());
    }

    @Test
    void processPayment_shouldRejectCrossWalletTransfers() {
        U2UPaymentRequest request = validRequest();
        request.getCreditor().setWalletType(WalletType.BONUS);

        doNothing().when(basePaymentRequestValidator).validate(request);

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> u2uPaymentService.processPayment(request, false)
        );

        assertEquals("CROSS_WALLET_TRANSFER_NOT_ALLOWED", exception.getErrorCode());
        verify(accountIdentifierRepository, never()).findByIdentifierTypeAndIdentifierValueAndStatus(any(), any(), any());
    }

    @Test
    void processPayment_shouldFailWhenPartyDoesNotHaveWalletForTransactionCurrency() {
        U2UPaymentRequest request = validRequest();
        AccountIdentifier debitorIdentifier = identifier("acc-1", "9999999999", "MOBILE", 10L);
        AccountIdentifier creditorIdentifier = identifier("acc-2", "8888888888", "MOBILE", 20L);
        Account debitorAccount = account("acc-1", "SUBSCRIBER");
        Account creditorAccount = account("acc-2", "SUBSCRIBER");
        Wallet debitorWallet = wallet(101L, "acc-1", "USD", "MAIN");

        doNothing().when(basePaymentRequestValidator).validate(request);
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus("MOBILE", "9999999999", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(Optional.of(debitorIdentifier));
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus("MOBILE", "8888888888", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(Optional.of(creditorIdentifier));
        when(accountRepository.findByAccountIdAndStatus("acc-1", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(List.of(debitorAccount));
        when(accountRepository.findByAccountIdAndStatus("acc-2", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(List.of(creditorAccount));
        when(walletRepository.findByAccountIdAndCurrencyAndWalletType("acc-1", "USD", "MAIN"))
                .thenReturn(Optional.of(debitorWallet));
        when(walletRepository.findByAccountIdAndCurrencyAndWalletType("acc-2", "USD", "MAIN"))
                .thenReturn(Optional.empty());

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> u2uPaymentService.processPayment(request, false)
        );

        assertEquals("WALLET_NOT_FOUND", exception.getErrorCode());
        verify(balanceService, never()).transferWalletAmount(any(), any(), any(), any(), any(), any());
    }

    @Test
    void processPayment_shouldResolveMsisdnToMobileLookup() {
        U2UPaymentRequest request = validRequest();
        request.getCreditor().getIdentifier().setType(IdentifierType.MSISDN);

        AccountIdentifier debitorIdentifier = identifier("acc-1", "9999999999", "MOBILE", 10L);
        AccountIdentifier creditorIdentifier = identifier("acc-2", "8888888888", "MOBILE", 20L);
        Account debitorAccount = account("acc-1", "SUBSCRIBER");
        Account creditorAccount = account("acc-2", "SUBSCRIBER");
        Wallet debitorWallet = wallet(101L, "acc-1", "USD", "MAIN");
        Wallet creditorWallet = wallet(202L, "acc-2", "USD", "MAIN");

        doNothing().when(basePaymentRequestValidator).validate(request);
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus("MOBILE", "9999999999", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(Optional.of(debitorIdentifier));
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus("MOBILE", "8888888888", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(Optional.of(creditorIdentifier));
        when(accountRepository.findByAccountIdAndStatus("acc-1", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(List.of(debitorAccount));
        when(accountRepository.findByAccountIdAndStatus("acc-2", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(List.of(creditorAccount));
        when(walletRepository.findByAccountIdAndCurrencyAndWalletType("acc-1", "USD", "MAIN"))
                .thenReturn(Optional.of(debitorWallet));
        when(walletRepository.findByAccountIdAndCurrencyAndWalletType("acc-2", "USD", "MAIN"))
                .thenReturn(Optional.of(creditorWallet));
        when(propertyReader.getPropertyValue("server.instance")).thenReturn("A");

        TraceContext.setTraceId("trace-1");
        try (MockedStatic<JWTUtils> jwtUtils = org.mockito.Mockito.mockStatic(JWTUtils.class)) {
            jwtUtils.when(JWTUtils::getCurrentAccountId).thenReturn("acc-1");
            jwtUtils.when(JWTUtils::getCurrentAccountType).thenReturn("SUBSCRIBER");
            jwtUtils.when(JWTUtils::getCurrentAuthType).thenReturn("PIN");

            U2UPaymentResponse response = u2uPaymentService.processPayment(request, true);

            assertEquals(TransactionStatus.SUCCESS, response.getResponseStatus());
            assertNotNull(response.getTransactionId());
            verify(authService).validateAuthentication("1234", AuthType.PIN, debitorIdentifier);
        } finally {
            TraceContext.clear();
        }
    }

    @Test
    void processPayment_shouldRejectWhenJwtAccountDoesNotMatchDebitor() {
        U2UPaymentRequest request = validRequest();
        AccountIdentifier debitorIdentifier = identifier("acc-1", "9999999999", "MOBILE", 10L);
        AccountIdentifier creditorIdentifier = identifier("acc-2", "8888888888", "MOBILE", 20L);

        doNothing().when(basePaymentRequestValidator).validate(request);
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus("MOBILE", "9999999999", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(Optional.of(debitorIdentifier));
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus("MOBILE", "8888888888", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(Optional.of(creditorIdentifier));

        try (MockedStatic<JWTUtils> jwtUtils = org.mockito.Mockito.mockStatic(JWTUtils.class)) {
            jwtUtils.when(JWTUtils::getCurrentAccountId).thenReturn("acc-9");
            jwtUtils.when(JWTUtils::getCurrentAccountType).thenReturn("SUBSCRIBER");
            jwtUtils.when(JWTUtils::getCurrentAuthType).thenReturn("PIN");

            ApplicationException exception = assertThrows(
                    ApplicationException.class,
                    () -> u2uPaymentService.processPayment(request, true)
            );

            assertEquals("INVALID_PRIVILEGES", exception.getErrorCode());
            verify(accountRepository, never()).findByAccountIdAndStatus(any(), any());
            verify(walletRepository, never()).findByAccountIdAndCurrencyAndWalletType(any(), any(), any());
        }
    }

    @Test
    void processPayment_shouldRejectWhenJwtScopeDoesNotMatchDebitorType() {
        U2UPaymentRequest request = validRequest();
        AccountIdentifier debitorIdentifier = identifier("acc-1", "9999999999", "MOBILE", 10L);
        AccountIdentifier creditorIdentifier = identifier("acc-2", "8888888888", "MOBILE", 20L);

        doNothing().when(basePaymentRequestValidator).validate(request);
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus("MOBILE", "9999999999", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(Optional.of(debitorIdentifier));
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus("MOBILE", "8888888888", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(Optional.of(creditorIdentifier));

        try (MockedStatic<JWTUtils> jwtUtils = org.mockito.Mockito.mockStatic(JWTUtils.class)) {
            jwtUtils.when(JWTUtils::getCurrentAccountId).thenReturn("acc-1");
            jwtUtils.when(JWTUtils::getCurrentAccountType).thenReturn("MERCHANT");
            jwtUtils.when(JWTUtils::getCurrentAuthType).thenReturn("PIN");

            ApplicationException exception = assertThrows(
                    ApplicationException.class,
                    () -> u2uPaymentService.processPayment(request, true)
            );

            assertEquals("INVALID_PRIVILEGES", exception.getErrorCode());
            verify(accountRepository, never()).findByAccountIdAndStatus(any(), any());
        }
    }

    @Test
    void processPayment_shouldRejectWhenJwtAuthTypeDoesNotMatchRequestAuthentication() {
        U2UPaymentRequest request = validRequest();
        AccountIdentifier debitorIdentifier = identifier("acc-1", "9999999999", "MOBILE", 10L);
        AccountIdentifier creditorIdentifier = identifier("acc-2", "8888888888", "MOBILE", 20L);

        doNothing().when(basePaymentRequestValidator).validate(request);
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus("MOBILE", "9999999999", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(Optional.of(debitorIdentifier));
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus("MOBILE", "8888888888", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(Optional.of(creditorIdentifier));

        try (MockedStatic<JWTUtils> jwtUtils = org.mockito.Mockito.mockStatic(JWTUtils.class)) {
            jwtUtils.when(JWTUtils::getCurrentAccountId).thenReturn("acc-1");
            jwtUtils.when(JWTUtils::getCurrentAccountType).thenReturn("SUBSCRIBER");
            jwtUtils.when(JWTUtils::getCurrentAuthType).thenReturn("PASSWORD");

            ApplicationException exception = assertThrows(
                    ApplicationException.class,
                    () -> u2uPaymentService.processPayment(request, true)
            );

            assertEquals("INVALID_AUTH_TYPE", exception.getErrorCode());
            verify(accountRepository, never()).findByAccountIdAndStatus(any(), any());
        }
    }

    private U2UPaymentRequest validRequest() {
        U2UPaymentRequest request = new U2UPaymentRequest();
        request.setOperationType("U2U");
        request.setRequestGateway(RequestGateway.MOBILE);
        request.setPreferredLang("en");
        request.setInitiatedBy(InitiatedBy.DEBITOR);
        request.setDebitor(party("9999999999"));
        request.setCreditor(party("8888888888"));

        TransactionInfo transactionInfo = new TransactionInfo();
        transactionInfo.setAmount(new BigDecimal("10.50"));
        transactionInfo.setCurrency("usd");
        request.setTransaction(transactionInfo);
        return request;
    }

    private Party party(String identifierValue) {
        Party party = new Party();
        party.setAccountType(AccountType.SUBSCRIBER);
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

    private AccountIdentifier identifier(String accountId, String value, String type, Long authId) {
        AccountIdentifier identifier = new AccountIdentifier();
        identifier.setAccountId(accountId);
        identifier.setIdentifierValue(value);
        identifier.setIdentifierType(type);
        identifier.setStatus(Constants.ACCOUNT_STATUS_ACTIVE);
        identifier.setAuthId(authId);
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
