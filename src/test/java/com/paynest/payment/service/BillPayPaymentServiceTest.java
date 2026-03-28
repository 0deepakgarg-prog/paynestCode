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
import com.paynest.payment.dto.BillPayPaymentRequest;
import com.paynest.payment.dto.BillPayPaymentResponse;
import com.paynest.payment.dto.Identifier;
import com.paynest.payment.dto.Party;
import com.paynest.payment.dto.TransactionInfo;
import com.paynest.payment.validation.BasePaymentRequestValidator;
import com.paynest.repository.AccountIdentifierRepository;
import com.paynest.repository.AccountRepository;
import com.paynest.repository.WalletRepository;
import com.paynest.security.JWTUtils;
import com.paynest.service.AuthService;
import com.paynest.service.BalanceService;
import com.paynest.service.TransactionsService;
import com.paynest.tenant.TraceContext;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillPayPaymentServiceTest {

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
    void processPayment_shouldParkFundsAndReturnPending() {
        BillPayPaymentService billPayPaymentService = billPayPaymentService();
        BillPayPaymentRequest request = validRequest();
        AccountIdentifier debitorIdentifier = identifier("sub-1", "9999999999", "MOBILE", 10L);
        AccountIdentifier creditorIdentifier = identifier("biller-1", "biller-login", "LOGINID", 20L);
        Account debitorAccount = account("sub-1", "SUBSCRIBER");
        Account creditorAccount = account("biller-1", "BILLER");
        Wallet debitorWallet = wallet(101L, "sub-1", "USD", "MAIN");
        Wallet creditorWallet = wallet(202L, "biller-1", "USD", "MAIN");

        doNothing().when(basePaymentRequestValidator).validate(request);
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus("MOBILE", "9999999999", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(Optional.of(debitorIdentifier));
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus("LOGINID", "biller-login", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(Optional.of(creditorIdentifier));
        when(accountRepository.findByAccountIdAndStatus("sub-1", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(List.of(debitorAccount));
        when(accountRepository.findByAccountIdAndStatus("biller-1", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(List.of(creditorAccount));
        when(walletRepository.findByAccountIdAndCurrencyAndWalletType("sub-1", "USD", "MAIN"))
                .thenReturn(Optional.of(debitorWallet));
        when(walletRepository.findByAccountIdAndCurrencyAndWalletType("biller-1", "USD", "MAIN"))
                .thenReturn(Optional.of(creditorWallet));
        when(propertyReader.getPropertyValue("server.instance")).thenReturn("A");

        TraceContext.setTraceId("trace-1");
        try (MockedStatic<JWTUtils> jwtUtils = org.mockito.Mockito.mockStatic(JWTUtils.class)) {
            jwtUtils.when(JWTUtils::getCurrentAccountId).thenReturn("sub-1");
            jwtUtils.when(JWTUtils::getCurrentAccountType).thenReturn("SUBSCRIBER");
            jwtUtils.when(JWTUtils::getCurrentAuthType).thenReturn("PIN");

            BillPayPaymentResponse response = billPayPaymentService.processPayment(request, true);

            assertEquals(TransactionStatus.PENDING, response.getResponseStatus());
            assertEquals("BILLPAY", response.getOperationType());
            assertEquals("SETTLEMENT_PENDING", response.getCode());
            assertNotNull(response.getTransactionId());

            verify(authService).validateAuthentication("1234", AuthType.PIN, debitorIdentifier);
            verify(transactionsService).generateTransactionRecord(
                    any(),
                    eq(new BigDecimal("10.50")),
                    eq(RequestGateway.MOBILE.name()),
                    eq("BILLPAY"),
                    eq("en"),
                    eq(debitorIdentifier),
                    eq(creditorIdentifier),
                    eq("SUBSCRIBER"),
                    eq("BILLER"),
                    eq(debitorWallet),
                    eq(creditorWallet),
                    eq(InitiatedBy.DEBITOR)
            );
            verify(balanceService).parkWalletAmountInFic(
                    debitorWallet,
                    creditorWallet,
                    new BigDecimal("10.50"),
                    "BILLPAY",
                    InitiatedBy.DEBITOR,
                    response.getTransactionId()
            );

            ArgumentCaptor<JSONObject> additionalInfoCaptor = ArgumentCaptor.forClass(JSONObject.class);
            verify(transactionsService).updateAdditionalInfo(any(), additionalInfoCaptor.capture());
            String additionalInfo = additionalInfoCaptor.getValue().toString();
            assertTrue(additionalInfo.contains("\"note\":\"customer initiated\""));
            assertTrue(additionalInfo.contains("\"meterNumber\":\"MTR-001\""));
        } finally {
            TraceContext.clear();
        }
    }

    @Test
    void processPayment_shouldRejectWhenCreditorIsNotBiller() {
        BillPayPaymentService billPayPaymentService = billPayPaymentService();
        BillPayPaymentRequest request = validRequest();
        request.getCreditor().setAccountType(AccountType.MERCHANT);

        doNothing().when(basePaymentRequestValidator).validate(request);

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> billPayPaymentService.processPayment(request, false)
        );

        assertEquals("INVALID_CREDITOR_USER_TYPE", exception.getErrorCode());
        verify(accountIdentifierRepository, never()).findByIdentifierTypeAndIdentifierValueAndStatus(any(), any(), any());
    }

    private BillPayPaymentService billPayPaymentService() {
        return new BillPayPaymentService(
                basePaymentRequestValidator,
                accountIdentifierRepository,
                accountRepository,
                walletRepository,
                propertyReader,
                transactionsService,
                balanceService,
                authService
        );
    }

    private BillPayPaymentRequest validRequest() {
        BillPayPaymentRequest request = new BillPayPaymentRequest();
        request.setOperationType("BILLPAY");
        request.setRequestGateway(RequestGateway.MOBILE);
        request.setPreferredLang("en");
        request.setInitiatedBy(InitiatedBy.DEBITOR);
        request.setDebitor(subscriberParty("9999999999"));
        request.setCreditor(billerParty("biller-login"));
        request.setPaymentReference("pay-ref-1");
        request.setComments("April electricity bill");

        Map<String, Object> additionalInfo = new LinkedHashMap<>();
        additionalInfo.put("note", "customer initiated");
        additionalInfo.put("meterNumber", "MTR-001");
        additionalInfo.put("region", "NORTH");
        request.setAdditionalInfo(additionalInfo);

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

    private Party billerParty(String identifierValue) {
        Party party = new Party();
        party.setAccountType(AccountType.BILLER);
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
