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
import com.paynest.payments.dto.Identifier;
import com.paynest.payments.dto.MerchpayPaymentRequest;
import com.paynest.payments.dto.MerchpayPaymentResponse;
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
class MerchPayPaymentServiceTest {

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

    @InjectMocks
    private MerchPayPaymentService merchPayPaymentService;

    @Test
    void processPayment_shouldTransferFromSubscriberToMerchant() {
        MerchpayPaymentRequest request = validRequest();
        AccountIdentifier debitorIdentifier = identifier("acc-1", "9999999999", "MOBILE", 10L);
        AccountIdentifier creditorIdentifier = identifier("mer-1", "merchant-login", "LOGINID", 20L);
        Account debitorAccount = account("acc-1", "SUBSCRIBER");
        Account creditorAccount = account("mer-1", "MERCHANT");
        Wallet debitorWallet = wallet(101L, "acc-1", "USD", "MAIN");
        Wallet creditorWallet = wallet(202L, "mer-1", "USD", "MAIN");

        doNothing().when(basePaymentRequestValidator).validate(request);
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus("MOBILE", "9999999999", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(Optional.of(debitorIdentifier));
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus("LOGINID", "merchant-login", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(Optional.of(creditorIdentifier));
        when(accountRepository.findByAccountIdAndStatus("acc-1", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(List.of(debitorAccount));
        when(accountRepository.findByAccountIdAndStatus("mer-1", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(List.of(creditorAccount));
        when(walletRepository.findByAccountIdAndCurrencyAndWalletType("acc-1", "USD", "MAIN"))
                .thenReturn(Optional.of(debitorWallet));
        when(walletRepository.findByAccountIdAndCurrencyAndWalletType("mer-1", "USD", "MAIN"))
                .thenReturn(Optional.of(creditorWallet));
        when(propertyReader.getPropertyValue("server.instance")).thenReturn("A");

        TraceContext.setTraceId("trace-1");
        try (MockedStatic<JWTUtils> jwtUtils = org.mockito.Mockito.mockStatic(JWTUtils.class)) {
            jwtUtils.when(JWTUtils::getCurrentAccountId).thenReturn("acc-1");
            jwtUtils.when(JWTUtils::getCurrentAccountType).thenReturn("SUBSCRIBER");
            jwtUtils.when(JWTUtils::getCurrentAuthType).thenReturn("PIN");

            MerchpayPaymentResponse response = merchPayPaymentService.processPayment(request, true);

            assertEquals(TransactionStatus.SUCCESS, response.getResponseStatus());
            assertEquals("MERCHANTPAY", response.getOperationType());
            assertEquals("Merchant payment successful", response.getMessage());
            assertEquals("USD", response.getCurrency());
            assertNotNull(response.getTransactionId());

            verify(authService).validateAuthentication("1234", AuthType.PIN, debitorIdentifier);
            verify(transactionsService).generateTransactionRecord(
                    any(),
                    eq(new BigDecimal("10.50")),
                    eq(RequestGateway.MOBILE.name()),
                    eq("MERCHANTPAY"),
                    eq("en"),
                    eq(debitorIdentifier),
                    eq(creditorIdentifier),
                    eq("SUBSCRIBER"),
                    eq("MERCHANT"),
                    eq(debitorWallet),
                    eq(creditorWallet),
                    eq(InitiatedBy.DEBITOR)
            );
            verify(balanceService).transferWalletAmount(
                    debitorWallet,
                    creditorWallet,
                    new BigDecimal("10.50"),
                    "MERCHANTPAY",
                    InitiatedBy.DEBITOR,
                    response.getTransactionId()
            );
        } finally {
            TraceContext.clear();
        }
    }

    @Test
    void processPayment_shouldRejectWhenCreditorIsNotMerchant() {
        MerchpayPaymentRequest request = validRequest();
        request.getCreditor().setAccountType(AccountType.SUBSCRIBER);

        doNothing().when(basePaymentRequestValidator).validate(request);

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> merchPayPaymentService.processPayment(request, false)
        );

        assertEquals("INVALID_CREDITOR_USER_TYPE", exception.getErrorCode());
        verify(accountIdentifierRepository, never()).findByIdentifierTypeAndIdentifierValueAndStatus(any(), any(), any());
    }

    @Test
    void processPayment_shouldRejectCrossWalletTransfers() {
        MerchpayPaymentRequest request = validRequest();
        request.getCreditor().setWalletType(WalletType.BONUS);

        doNothing().when(basePaymentRequestValidator).validate(request);

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> merchPayPaymentService.processPayment(request, false)
        );

        assertEquals("CROSS_WALLET_TRANSFER_NOT_ALLOWED", exception.getErrorCode());
        verify(accountIdentifierRepository, never()).findByIdentifierTypeAndIdentifierValueAndStatus(any(), any(), any());
    }

    @Test
    void processPayment_shouldFailWhenMerchantWalletDoesNotExistForCurrency() {
        MerchpayPaymentRequest request = validRequest();
        AccountIdentifier debitorIdentifier = identifier("acc-1", "9999999999", "MOBILE", 10L);
        AccountIdentifier creditorIdentifier = identifier("mer-1", "merchant-login", "LOGINID", 20L);
        Account debitorAccount = account("acc-1", "SUBSCRIBER");
        Account creditorAccount = account("mer-1", "MERCHANT");
        Wallet debitorWallet = wallet(101L, "acc-1", "USD", "MAIN");

        doNothing().when(basePaymentRequestValidator).validate(request);
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus("MOBILE", "9999999999", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(Optional.of(debitorIdentifier));
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus("LOGINID", "merchant-login", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(Optional.of(creditorIdentifier));
        when(accountRepository.findByAccountIdAndStatus("acc-1", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(List.of(debitorAccount));
        when(accountRepository.findByAccountIdAndStatus("mer-1", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(List.of(creditorAccount));
        when(walletRepository.findByAccountIdAndCurrencyAndWalletType("acc-1", "USD", "MAIN"))
                .thenReturn(Optional.of(debitorWallet));
        when(walletRepository.findByAccountIdAndCurrencyAndWalletType("mer-1", "USD", "MAIN"))
                .thenReturn(Optional.empty());

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> merchPayPaymentService.processPayment(request, false)
        );

        assertEquals("WALLET_NOT_FOUND", exception.getErrorCode());
        verify(balanceService, never()).transferWalletAmount(any(), any(), any(), any(), any(), any());
    }

    @Test
    void processPayment_shouldRejectWhenJwtAccountDoesNotMatchDebitor() {
        MerchpayPaymentRequest request = validRequest();
        AccountIdentifier debitorIdentifier = identifier("acc-1", "9999999999", "MOBILE", 10L);
        AccountIdentifier creditorIdentifier = identifier("mer-1", "merchant-login", "LOGINID", 20L);

        doNothing().when(basePaymentRequestValidator).validate(request);
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus("MOBILE", "9999999999", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(Optional.of(debitorIdentifier));
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus("LOGINID", "merchant-login", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(Optional.of(creditorIdentifier));

        try (MockedStatic<JWTUtils> jwtUtils = org.mockito.Mockito.mockStatic(JWTUtils.class)) {
            jwtUtils.when(JWTUtils::getCurrentAccountId).thenReturn("acc-9");
            jwtUtils.when(JWTUtils::getCurrentAccountType).thenReturn("SUBSCRIBER");
            jwtUtils.when(JWTUtils::getCurrentAuthType).thenReturn("PIN");

            ApplicationException exception = assertThrows(
                    ApplicationException.class,
                    () -> merchPayPaymentService.processPayment(request, true)
            );

            assertEquals("INVALID_PRIVILEGES", exception.getErrorCode());
            verify(accountRepository, never()).findByAccountIdAndStatus(any(), any());
        }
    }

    @Test
    void processPayment_shouldRejectWhenJwtScopeDoesNotMatchSubscriberDebitor() {
        MerchpayPaymentRequest request = validRequest();
        AccountIdentifier debitorIdentifier = identifier("acc-1", "9999999999", "MOBILE", 10L);
        AccountIdentifier creditorIdentifier = identifier("mer-1", "merchant-login", "LOGINID", 20L);

        doNothing().when(basePaymentRequestValidator).validate(request);
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus("MOBILE", "9999999999", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(Optional.of(debitorIdentifier));
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus("LOGINID", "merchant-login", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(Optional.of(creditorIdentifier));

        try (MockedStatic<JWTUtils> jwtUtils = org.mockito.Mockito.mockStatic(JWTUtils.class)) {
            jwtUtils.when(JWTUtils::getCurrentAccountId).thenReturn("acc-1");
            jwtUtils.when(JWTUtils::getCurrentAccountType).thenReturn("MERCHANT");
            jwtUtils.when(JWTUtils::getCurrentAuthType).thenReturn("PIN");

            ApplicationException exception = assertThrows(
                    ApplicationException.class,
                    () -> merchPayPaymentService.processPayment(request, true)
            );

            assertEquals("INVALID_PRIVILEGES", exception.getErrorCode());
            verify(accountRepository, never()).findByAccountIdAndStatus(any(), any());
        }
    }

    @Test
    void processPayment_shouldRejectWhenJwtAuthTypeDoesNotMatchSubscriberAuthentication() {
        MerchpayPaymentRequest request = validRequest();
        AccountIdentifier debitorIdentifier = identifier("acc-1", "9999999999", "MOBILE", 10L);
        AccountIdentifier creditorIdentifier = identifier("mer-1", "merchant-login", "LOGINID", 20L);

        doNothing().when(basePaymentRequestValidator).validate(request);
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus("MOBILE", "9999999999", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(Optional.of(debitorIdentifier));
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus("LOGINID", "merchant-login", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(Optional.of(creditorIdentifier));

        try (MockedStatic<JWTUtils> jwtUtils = org.mockito.Mockito.mockStatic(JWTUtils.class)) {
            jwtUtils.when(JWTUtils::getCurrentAccountId).thenReturn("acc-1");
            jwtUtils.when(JWTUtils::getCurrentAccountType).thenReturn("SUBSCRIBER");
            jwtUtils.when(JWTUtils::getCurrentAuthType).thenReturn("PASSWORD");

            ApplicationException exception = assertThrows(
                    ApplicationException.class,
                    () -> merchPayPaymentService.processPayment(request, true)
            );

            assertEquals("INVALID_AUTH_TYPE", exception.getErrorCode());
            verify(accountRepository, never()).findByAccountIdAndStatus(any(), any());
        }
    }

    @Test
    void processPayment_shouldAllowMerchantMsisdnCreditor() {
        MerchpayPaymentRequest request = validRequest();
        request.getCreditor().getIdentifier().setType(IdentifierType.MSISDN);
        request.getCreditor().getIdentifier().setValue("6666666666");

        AccountIdentifier debitorIdentifier = identifier("acc-1", "9999999999", "MOBILE", 10L);
        AccountIdentifier creditorIdentifier = identifier("mer-1", "6666666666", "MOBILE", 20L);
        Account debitorAccount = account("acc-1", "SUBSCRIBER");
        Account creditorAccount = account("mer-1", "MERCHANT");
        Wallet debitorWallet = wallet(101L, "acc-1", "USD", "MAIN");
        Wallet creditorWallet = wallet(202L, "mer-1", "USD", "MAIN");

        doNothing().when(basePaymentRequestValidator).validate(request);
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus("MOBILE", "9999999999", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(Optional.of(debitorIdentifier));
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus("MOBILE", "6666666666", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(Optional.of(creditorIdentifier));
        when(accountRepository.findByAccountIdAndStatus("acc-1", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(List.of(debitorAccount));
        when(accountRepository.findByAccountIdAndStatus("mer-1", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(List.of(creditorAccount));
        when(walletRepository.findByAccountIdAndCurrencyAndWalletType("acc-1", "USD", "MAIN"))
                .thenReturn(Optional.of(debitorWallet));
        when(walletRepository.findByAccountIdAndCurrencyAndWalletType("mer-1", "USD", "MAIN"))
                .thenReturn(Optional.of(creditorWallet));
        when(propertyReader.getPropertyValue("server.instance")).thenReturn("A");

        TraceContext.setTraceId("trace-1");
        try (MockedStatic<JWTUtils> jwtUtils = org.mockito.Mockito.mockStatic(JWTUtils.class)) {
            jwtUtils.when(JWTUtils::getCurrentAccountId).thenReturn("acc-1");
            jwtUtils.when(JWTUtils::getCurrentAccountType).thenReturn("SUBSCRIBER");
            jwtUtils.when(JWTUtils::getCurrentAuthType).thenReturn("PIN");

            MerchpayPaymentResponse response = merchPayPaymentService.processPayment(request, true);

            assertEquals(TransactionStatus.SUCCESS, response.getResponseStatus());
            assertEquals("MERCHANTPAY", response.getOperationType());
            assertNotNull(response.getTransactionId());
        } finally {
            TraceContext.clear();
        }
    }

    private MerchpayPaymentRequest validRequest() {
        MerchpayPaymentRequest request = new MerchpayPaymentRequest();
        request.setOperationType("MERCHANTPAY");
        request.setRequestGateway(RequestGateway.MOBILE);
        request.setPreferredLang("en");
        request.setInitiatedBy(InitiatedBy.DEBITOR);
        request.setDebitor(subscriberParty("9999999999"));
        request.setCreditor(merchantParty("merchant-login"));

        TransactionInfo transactionInfo = new TransactionInfo();
        transactionInfo.setAmount(new BigDecimal("10.50"));
        transactionInfo.setCurrency("usd");
        request.setTransaction(transactionInfo);
        return request;
    }

    private Party subscriberParty(String identifierValue) {
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

    private Party merchantParty(String identifierValue) {
        Party party = new Party();
        party.setAccountType(AccountType.MERCHANT);
        party.setWalletType(WalletType.MAIN);

        Identifier identifier = new Identifier();
        identifier.setType(IdentifierType.LOGINID);
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
