package com.paynest.payments.service;

import com.paynest.common.Constants;
import com.paynest.config.PropertyReader;
import com.paynest.users.entity.Account;
import com.paynest.users.entity.AccountIdentifier;
import com.paynest.users.entity.Wallet;
import com.paynest.enums.AccountType;
import com.paynest.enums.RequestGateway;
import com.paynest.enums.WalletType;
import com.paynest.users.enums.AuthType;
import com.paynest.users.enums.IdentifierType;
import com.paynest.exception.ApplicationException;
import com.paynest.payments.dto.Authentication;
import com.paynest.payments.dto.CashInPaymentRequest;
import com.paynest.payments.dto.CashInPaymentResponse;
import com.paynest.payments.dto.Identifier;
import com.paynest.payments.dto.Party;
import com.paynest.payments.dto.TransactionInfo;
import com.paynest.payments.enums.InitiatedBy;
import com.paynest.payments.enums.TransactionStatus;
import com.paynest.payments.validation.BasePaymentRequestValidator;
import com.paynest.users.repository.AccountIdentifierRepository;
import com.paynest.users.repository.AccountRepository;
import com.paynest.users.repository.WalletRepository;
import com.paynest.config.security.JWTUtils;
import com.paynest.users.service.AuthService;
import com.paynest.payments.service.BalanceService;
import com.paynest.service.TransactionsService;
import com.paynest.config.tenant.TraceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class CashInPaymentServiceTest {

    @Mock
    private BasePaymentRequestValidator basePaymentRequestValidator;

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

    @Test
    void processPayment_shouldTransferFromAgentToSubscriber() {
        CashInPaymentService cashInPaymentService = new CashInPaymentService(
                basePaymentRequestValidator,
                accountIdentifierRepository,
                accountRepository,
                walletRepository,
                propertyReader,
                transactionsService,
                balanceService,
                authService
        );

        CashInPaymentRequest request = validRequest();
        AccountIdentifier debitorIdentifier = identifier("agent-1", "7777777777", "MOBILE", 10L);
        AccountIdentifier creditorIdentifier = identifier("sub-1", "9999999999", "MOBILE", 20L);
        Account debitorAccount = account("agent-1", "AGENT");
        Account creditorAccount = account("sub-1", "SUBSCRIBER");
        Wallet debitorWallet = wallet(101L, "agent-1", "USD", "MAIN");
        Wallet creditorWallet = wallet(202L, "sub-1", "USD", "MAIN");

        doNothing().when(basePaymentRequestValidator).validate(request);
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus("MOBILE", "7777777777", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(Optional.of(debitorIdentifier));
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus("MOBILE", "9999999999", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(Optional.of(creditorIdentifier));
        when(accountRepository.findByAccountIdAndStatus("agent-1", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(List.of(debitorAccount));
        when(accountRepository.findByAccountIdAndStatus("sub-1", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(List.of(creditorAccount));
        when(walletRepository.findByAccountIdAndCurrencyAndWalletType("agent-1", "USD", "MAIN"))
                .thenReturn(Optional.of(debitorWallet));
        when(walletRepository.findByAccountIdAndCurrencyAndWalletType("sub-1", "USD", "MAIN"))
                .thenReturn(Optional.of(creditorWallet));
        when(propertyReader.getPropertyValue("server.instance")).thenReturn("A");

        TraceContext.setTraceId("trace-1");
        try (MockedStatic<JWTUtils> jwtUtils = org.mockito.Mockito.mockStatic(JWTUtils.class)) {
            jwtUtils.when(JWTUtils::getCurrentAccountId).thenReturn("agent-1");
            jwtUtils.when(JWTUtils::getCurrentAccountType).thenReturn("AGENT");
            jwtUtils.when(JWTUtils::getCurrentAuthType).thenReturn("PIN");

            CashInPaymentResponse response = cashInPaymentService.processPayment(request, true);

            assertEquals(TransactionStatus.SUCCESS, response.getResponseStatus());
            assertEquals("CASHIN", response.getOperationType());
            assertEquals("Cash-in successful", response.getMessage());
            assertNotNull(response.getTransactionId());

            verify(authService).validateAuthentication("1234", AuthType.PIN, debitorIdentifier);
            verify(transactionsService).generateTransactionRecord(
                    any(),
                    eq(new BigDecimal("10.50")),
                    eq(RequestGateway.MOBILE.name()),
                    eq("CASHIN"),
                    eq("en"),
                    eq(debitorIdentifier),
                    eq(creditorIdentifier),
                    eq("AGENT"),
                    eq("SUBSCRIBER"),
                    eq(debitorWallet),
                    eq(creditorWallet),
                    eq(InitiatedBy.DEBITOR)
            );
        } finally {
            TraceContext.clear();
        }
    }

    @Test
    void processPayment_shouldRejectWhenDebitorIsNotAgent() {
        CashInPaymentService cashInPaymentService = new CashInPaymentService(
                basePaymentRequestValidator,
                accountIdentifierRepository,
                accountRepository,
                walletRepository,
                propertyReader,
                transactionsService,
                balanceService,
                authService
        );

        CashInPaymentRequest request = validRequest();
        request.getDebitor().setAccountType(AccountType.SUBSCRIBER);

        doNothing().when(basePaymentRequestValidator).validate(request);

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> cashInPaymentService.processPayment(request, false)
        );

        assertEquals("INVALID_DEBITOR_USER_TYPE", exception.getErrorCode());
        verify(accountIdentifierRepository, never()).findByIdentifierTypeAndIdentifierValueAndStatus(any(), any(), any());
    }

    @Test
    void processPayment_shouldAllowAgentLoginIdWithPassword() {
        CashInPaymentService cashInPaymentService = new CashInPaymentService(
                basePaymentRequestValidator,
                accountIdentifierRepository,
                accountRepository,
                walletRepository,
                propertyReader,
                transactionsService,
                balanceService,
                authService
        );

        CashInPaymentRequest request = validRequest();
        request.getDebitor().getIdentifier().setType(IdentifierType.LOGINID);
        request.getDebitor().getIdentifier().setValue("agent-login");
        request.getDebitor().getAuthentication().setType(AuthType.PASSWORD);
        request.getDebitor().getAuthentication().setValue("Agent@123");

        AccountIdentifier debitorIdentifier = identifier("agent-1", "agent-login", "LOGINID", 10L);
        AccountIdentifier creditorIdentifier = identifier("sub-1", "9999999999", "MOBILE", 20L);
        Account debitorAccount = account("agent-1", "AGENT");
        Account creditorAccount = account("sub-1", "SUBSCRIBER");
        Wallet debitorWallet = wallet(101L, "agent-1", "USD", "MAIN");
        Wallet creditorWallet = wallet(202L, "sub-1", "USD", "MAIN");

        doNothing().when(basePaymentRequestValidator).validate(request);
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus("LOGINID", "agent-login", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(Optional.of(debitorIdentifier));
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus("MOBILE", "9999999999", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(Optional.of(creditorIdentifier));
        when(accountRepository.findByAccountIdAndStatus("agent-1", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(List.of(debitorAccount));
        when(accountRepository.findByAccountIdAndStatus("sub-1", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(List.of(creditorAccount));
        when(walletRepository.findByAccountIdAndCurrencyAndWalletType("agent-1", "USD", "MAIN"))
                .thenReturn(Optional.of(debitorWallet));
        when(walletRepository.findByAccountIdAndCurrencyAndWalletType("sub-1", "USD", "MAIN"))
                .thenReturn(Optional.of(creditorWallet));
        when(propertyReader.getPropertyValue("server.instance")).thenReturn("A");

        TraceContext.setTraceId("trace-1");
        try (MockedStatic<JWTUtils> jwtUtils = org.mockito.Mockito.mockStatic(JWTUtils.class)) {
            jwtUtils.when(JWTUtils::getCurrentAccountId).thenReturn("agent-1");
            jwtUtils.when(JWTUtils::getCurrentAccountType).thenReturn("AGENT");
            jwtUtils.when(JWTUtils::getCurrentAuthType).thenReturn("PASSWORD");

            CashInPaymentResponse response = cashInPaymentService.processPayment(request, true);

            assertEquals(TransactionStatus.SUCCESS, response.getResponseStatus());
            assertEquals("CASHIN", response.getOperationType());
            assertNotNull(response.getTransactionId());

            verify(authService).validateAuthentication("Agent@123", AuthType.PASSWORD, debitorIdentifier);
        } finally {
            TraceContext.clear();
        }
    }

    @Test
    void processPayment_shouldAllowAgentMobileWithPassword() {
        CashInPaymentService cashInPaymentService = new CashInPaymentService(
                basePaymentRequestValidator,
                accountIdentifierRepository,
                accountRepository,
                walletRepository,
                propertyReader,
                transactionsService,
                balanceService,
                authService
        );

        CashInPaymentRequest request = validRequest();
        request.getDebitor().getAuthentication().setType(AuthType.PASSWORD);
        request.getDebitor().getAuthentication().setValue("Agent@123");

        AccountIdentifier debitorIdentifier = identifier("agent-1", "7777777777", "MOBILE", 10L);
        AccountIdentifier creditorIdentifier = identifier("sub-1", "9999999999", "MOBILE", 20L);
        Account debitorAccount = account("agent-1", "AGENT");
        Account creditorAccount = account("sub-1", "SUBSCRIBER");
        Wallet debitorWallet = wallet(101L, "agent-1", "USD", "MAIN");
        Wallet creditorWallet = wallet(202L, "sub-1", "USD", "MAIN");

        doNothing().when(basePaymentRequestValidator).validate(request);
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus("MOBILE", "7777777777", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(Optional.of(debitorIdentifier));
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus("MOBILE", "9999999999", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(Optional.of(creditorIdentifier));
        when(accountRepository.findByAccountIdAndStatus("agent-1", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(List.of(debitorAccount));
        when(accountRepository.findByAccountIdAndStatus("sub-1", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(List.of(creditorAccount));
        when(walletRepository.findByAccountIdAndCurrencyAndWalletType("agent-1", "USD", "MAIN"))
                .thenReturn(Optional.of(debitorWallet));
        when(walletRepository.findByAccountIdAndCurrencyAndWalletType("sub-1", "USD", "MAIN"))
                .thenReturn(Optional.of(creditorWallet));
        when(propertyReader.getPropertyValue("server.instance")).thenReturn("A");

        TraceContext.setTraceId("trace-1");
        try (MockedStatic<JWTUtils> jwtUtils = org.mockito.Mockito.mockStatic(JWTUtils.class)) {
            jwtUtils.when(JWTUtils::getCurrentAccountId).thenReturn("agent-1");
            jwtUtils.when(JWTUtils::getCurrentAccountType).thenReturn("AGENT");
            jwtUtils.when(JWTUtils::getCurrentAuthType).thenReturn("PASSWORD");

            CashInPaymentResponse response = cashInPaymentService.processPayment(request, true);

            assertEquals(TransactionStatus.SUCCESS, response.getResponseStatus());
            assertEquals("CASHIN", response.getOperationType());
            assertNotNull(response.getTransactionId());

            verify(authService).validateAuthentication("Agent@123", AuthType.PASSWORD, debitorIdentifier);
        } finally {
            TraceContext.clear();
        }
    }

    @Test
    void processPayment_shouldResolveMsisdnToMobileLookup() {
        CashInPaymentService cashInPaymentService = new CashInPaymentService(
                basePaymentRequestValidator,
                accountIdentifierRepository,
                accountRepository,
                walletRepository,
                propertyReader,
                transactionsService,
                balanceService,
                authService
        );

        CashInPaymentRequest request = validRequest();
        request.getDebitor().getIdentifier().setType(IdentifierType.MSISDN);

        AccountIdentifier debitorIdentifier = identifier("agent-1", "7777777777", "MOBILE", 10L);
        AccountIdentifier creditorIdentifier = identifier("sub-1", "9999999999", "MOBILE", 20L);
        Account debitorAccount = account("agent-1", "AGENT");
        Account creditorAccount = account("sub-1", "SUBSCRIBER");
        Wallet debitorWallet = wallet(101L, "agent-1", "USD", "MAIN");
        Wallet creditorWallet = wallet(202L, "sub-1", "USD", "MAIN");

        doNothing().when(basePaymentRequestValidator).validate(request);
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus("MOBILE", "7777777777", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(Optional.of(debitorIdentifier));
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus("MOBILE", "9999999999", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(Optional.of(creditorIdentifier));
        when(accountRepository.findByAccountIdAndStatus("agent-1", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(List.of(debitorAccount));
        when(accountRepository.findByAccountIdAndStatus("sub-1", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(List.of(creditorAccount));
        when(walletRepository.findByAccountIdAndCurrencyAndWalletType("agent-1", "USD", "MAIN"))
                .thenReturn(Optional.of(debitorWallet));
        when(walletRepository.findByAccountIdAndCurrencyAndWalletType("sub-1", "USD", "MAIN"))
                .thenReturn(Optional.of(creditorWallet));
        when(propertyReader.getPropertyValue("server.instance")).thenReturn("A");

        TraceContext.setTraceId("trace-1");
        try (MockedStatic<JWTUtils> jwtUtils = org.mockito.Mockito.mockStatic(JWTUtils.class)) {
            jwtUtils.when(JWTUtils::getCurrentAccountId).thenReturn("agent-1");
            jwtUtils.when(JWTUtils::getCurrentAccountType).thenReturn("AGENT");
            jwtUtils.when(JWTUtils::getCurrentAuthType).thenReturn("PIN");

            CashInPaymentResponse response = cashInPaymentService.processPayment(request, true);

            assertEquals(TransactionStatus.SUCCESS, response.getResponseStatus());
            assertNotNull(response.getTransactionId());
            verify(authService).validateAuthentication("1234", AuthType.PIN, debitorIdentifier);
        } finally {
            TraceContext.clear();
        }
    }

    @Test
    void processPayment_shouldRejectWhenJwtScopeDoesNotMatchAgentDebitor() {
        CashInPaymentService cashInPaymentService = new CashInPaymentService(
                basePaymentRequestValidator,
                accountIdentifierRepository,
                accountRepository,
                walletRepository,
                propertyReader,
                transactionsService,
                balanceService,
                authService
        );

        CashInPaymentRequest request = validRequest();
        AccountIdentifier debitorIdentifier = identifier("agent-1", "7777777777", "MOBILE", 10L);
        AccountIdentifier creditorIdentifier = identifier("sub-1", "9999999999", "MOBILE", 20L);

        doNothing().when(basePaymentRequestValidator).validate(request);
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus("MOBILE", "7777777777", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(Optional.of(debitorIdentifier));
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus("MOBILE", "9999999999", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(Optional.of(creditorIdentifier));

        try (MockedStatic<JWTUtils> jwtUtils = org.mockito.Mockito.mockStatic(JWTUtils.class)) {
            jwtUtils.when(JWTUtils::getCurrentAccountId).thenReturn("agent-1");
            jwtUtils.when(JWTUtils::getCurrentAccountType).thenReturn("SUBSCRIBER");
            jwtUtils.when(JWTUtils::getCurrentAuthType).thenReturn("PIN");

            ApplicationException exception = assertThrows(
                    ApplicationException.class,
                    () -> cashInPaymentService.processPayment(request, true)
            );

            assertEquals("INVALID_PRIVILEGES", exception.getErrorCode());
            verify(accountRepository, never()).findByAccountIdAndStatus(any(), any());
        }
    }

    @Test
    void processPayment_shouldRejectWhenJwtAuthTypeDoesNotMatchAgentAuthentication() {
        CashInPaymentService cashInPaymentService = new CashInPaymentService(
                basePaymentRequestValidator,
                accountIdentifierRepository,
                accountRepository,
                walletRepository,
                propertyReader,
                transactionsService,
                balanceService,
                authService
        );

        CashInPaymentRequest request = validRequest();
        AccountIdentifier debitorIdentifier = identifier("agent-1", "7777777777", "MOBILE", 10L);
        AccountIdentifier creditorIdentifier = identifier("sub-1", "9999999999", "MOBILE", 20L);

        doNothing().when(basePaymentRequestValidator).validate(request);
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus("MOBILE", "7777777777", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(Optional.of(debitorIdentifier));
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus("MOBILE", "9999999999", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(Optional.of(creditorIdentifier));

        try (MockedStatic<JWTUtils> jwtUtils = org.mockito.Mockito.mockStatic(JWTUtils.class)) {
            jwtUtils.when(JWTUtils::getCurrentAccountId).thenReturn("agent-1");
            jwtUtils.when(JWTUtils::getCurrentAccountType).thenReturn("AGENT");
            jwtUtils.when(JWTUtils::getCurrentAuthType).thenReturn("PASSWORD");

            ApplicationException exception = assertThrows(
                    ApplicationException.class,
                    () -> cashInPaymentService.processPayment(request, true)
            );

            assertEquals("INVALID_AUTH_TYPE", exception.getErrorCode());
            verify(accountRepository, never()).findByAccountIdAndStatus(any(), any());
        }
    }

    private CashInPaymentRequest validRequest() {
        CashInPaymentRequest request = new CashInPaymentRequest();
        request.setOperationType("CASHIN");
        request.setRequestGateway(RequestGateway.MOBILE);
        request.setPreferredLang("en");
        request.setInitiatedBy(InitiatedBy.DEBITOR);
        request.setDebitor(agentParty("7777777777"));
        request.setCreditor(subscriberParty("9999999999"));

        TransactionInfo transactionInfo = new TransactionInfo();
        transactionInfo.setAmount(new BigDecimal("10.50"));
        transactionInfo.setCurrency("usd");
        request.setTransaction(transactionInfo);
        return request;
    }

    private Party agentParty(String identifierValue) {
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

    private Party subscriberParty(String identifierValue) {
        Party party = new Party();
        party.setAccountType(AccountType.SUBSCRIBER);
        party.setWalletType(WalletType.MAIN);

        Identifier identifier = new Identifier();
        identifier.setType(IdentifierType.MOBILE);
        identifier.setValue(identifierValue);
        party.setIdentifier(identifier);
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
