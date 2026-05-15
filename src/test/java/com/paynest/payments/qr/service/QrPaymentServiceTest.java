package com.paynest.payments.qr.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paynest.enums.AccountType;
import com.paynest.enums.RequestGateway;
import com.paynest.payments.dto.Authentication;
import com.paynest.payments.dto.Identifier;
import com.paynest.payments.dto.MerchpayPaymentRequest;
import com.paynest.payments.dto.MerchpayPaymentResponse;
import com.paynest.payments.dto.Party;
import com.paynest.payments.enums.InitiatedBy;
import com.paynest.payments.enums.TransactionStatus;
import com.paynest.payments.qr.dto.QrCreditor;
import com.paynest.payments.qr.dto.QrGenerateRequest;
import com.paynest.payments.qr.dto.QrGenerateResponse;
import com.paynest.payments.qr.dto.QrPayRequest;
import com.paynest.payments.qr.dto.QrScanRequest;
import com.paynest.payments.qr.dto.QrScanResponse;
import com.paynest.payments.qr.entity.QrPaymentIntent;
import com.paynest.payments.qr.enums.QrIntentStatus;
import com.paynest.payments.qr.enums.QrType;
import com.paynest.payments.qr.repository.QrPaymentIntentRepository;
import com.paynest.payments.service.MerchPayPaymentService;
import com.paynest.payments.service.U2UPaymentService;
import com.paynest.users.entity.AccountIdentifier;
import com.paynest.users.enums.AuthType;
import com.paynest.users.enums.IdentifierType;
import com.paynest.users.enums.WalletType;
import com.paynest.users.repository.AccountIdentifierRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QrPaymentServiceTest {

    @Mock
    private QrPaymentIntentRepository qrPaymentIntentRepository;

    @Mock
    private AccountIdentifierRepository accountIdentifierRepository;

    @Mock
    private MerchPayPaymentService merchPayPaymentService;

    @Mock
    private U2UPaymentService u2uPaymentService;

    @Test
    void generateAndScanStaticQr_shouldReturnSignedPaymentPreview() {
        QrPaymentService service = service();

        QrGenerateResponse generated = service.generate(staticGenerateRequest(null));
        QrScanResponse scanned = service.scan(scanRequest(generated.getPayload()));

        assertEquals(QrType.STATIC, scanned.getQrType());
        assertEquals("MERCHANTPAY", scanned.getOperationType());
        assertEquals(IdentifierType.LOGINID, scanned.getCreditorIdentifierType());
        assertEquals("merchant-login", scanned.getCreditorIdentifierValue());
        assertEquals(AccountType.MERCHANT, scanned.getCreditorAccountType());
        assertEquals(WalletType.MAIN, scanned.getCreditorWalletType());
        assertEquals("USD", scanned.getCurrency());
        assertNotNull(generated.getQrImageBase64());
        assertFalse(Base64.getDecoder().decode(generated.getQrImageBase64()).length == 0);
    }

    @Test
    void scan_shouldRejectTamperedPayload() {
        QrPaymentService service = service();

        QrGenerateResponse generated = service.generate(staticGenerateRequest(null));
        String tampered = generated.getPayload().replace("merchant-login", "other-merchant");

        assertThrows(RuntimeException.class, () -> service.scan(scanRequest(tampered)));
    }

    @Test
    void payStaticMerchantQr_shouldDelegateToMerchantPayAndMarkTransaction() {
        QrPaymentService service = service();
        QrGenerateResponse generated = service.generate(staticGenerateRequest(null));

        when(merchPayPaymentService.processPayment(org.mockito.ArgumentMatchers.any(MerchpayPaymentRequest.class), eq(true)))
                .thenReturn(MerchpayPaymentResponse.builder()
                        .responseStatus(TransactionStatus.SUCCESS)
                        .operationType("MERCHANTPAY")
                        .transactionId("MP123")
                        .amount(new BigDecimal("12.25"))
                        .currency("USD")
                        .build());

        Object response = service.pay(payRequest(generated.getPayload()));

        assertNotNull(response);
        ArgumentCaptor<MerchpayPaymentRequest> requestCaptor = ArgumentCaptor.forClass(MerchpayPaymentRequest.class);
        verify(merchPayPaymentService).processPayment(requestCaptor.capture(), eq(true));
        MerchpayPaymentRequest paymentRequest = requestCaptor.getValue();
        assertEquals("MERCHANTPAY", paymentRequest.getOperationType());
        assertEquals(new BigDecimal("12.25"), paymentRequest.getTransaction().getAmount());
        assertEquals("USD", paymentRequest.getTransaction().getCurrency());
        assertEquals("merchant-login", paymentRequest.getCreditor().getIdentifier().getValue());
        assertEquals(Boolean.TRUE, paymentRequest.getMetadata().get("paymentViaQr"));
    }

    @Test
    void payDynamicQr_shouldUseIntentAmountAndMarkIntentPaid() {
        QrPaymentService service = service();
        QrPaymentIntent intent = activeIntent();
        when(qrPaymentIntentRepository.findFirstByQrIntentId("QR123")).thenReturn(Optional.of(intent));
        when(merchPayPaymentService.processPayment(any(MerchpayPaymentRequest.class), eq(true)))
                .thenReturn(MerchpayPaymentResponse.builder()
                        .responseStatus(TransactionStatus.SUCCESS)
                        .operationType("MERCHANTPAY")
                        .transactionId("MP456")
                        .amount(new BigDecimal("20.00"))
                        .currency("USD")
                        .build());

        QrPayRequest request = payRequest(null);
        request.setQrIntentId("QR123");
        request.setAmount(new BigDecimal("99.00"));

        service.pay(request);

        ArgumentCaptor<MerchpayPaymentRequest> requestCaptor = ArgumentCaptor.forClass(MerchpayPaymentRequest.class);
        verify(merchPayPaymentService).processPayment(requestCaptor.capture(), eq(true));
        assertEquals(new BigDecimal("20.00"), requestCaptor.getValue().getTransaction().getAmount());
        assertEquals(QrIntentStatus.PAID, intent.getStatus());
        assertEquals("MP456", intent.getTransactionId());
        verify(qrPaymentIntentRepository).save(intent);
    }

    @Test
    void generateMyStaticQr_shouldUseAuthenticatedAccountIdentifierWithoutCreatingIntent() {
        QrPaymentService service = service();
        setAuthentication("sub-1", "SUBSCRIBER");
        when(accountIdentifierRepository.findByAccountIdAndStatus("sub-1", "ACTIVE"))
                .thenReturn(List.of(accountIdentifier("sub-1", "MOBILE", "9999999999")));

        try {
            QrGenerateResponse response = service.generateMyStaticQr("usd", WalletType.MAIN);
            QrScanResponse scanned = service.scan(scanRequest(response.getPayload()));

            assertEquals(QrType.STATIC, scanned.getQrType());
            assertEquals("U2U", scanned.getOperationType());
            assertEquals(AccountType.SUBSCRIBER, scanned.getCreditorAccountType());
            assertEquals(IdentifierType.MOBILE, scanned.getCreditorIdentifierType());
            assertEquals("9999999999", scanned.getCreditorIdentifierValue());
            assertEquals("USD", scanned.getCurrency());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void generateMyStaticQr_shouldUseMerchantPayForAuthenticatedMerchant() {
        QrPaymentService service = service();
        setAuthentication("merchant-1", "MERCHANT");
        when(accountIdentifierRepository.findByAccountIdAndStatus("merchant-1", "ACTIVE"))
                .thenReturn(List.of(accountIdentifier("merchant-1", "MOBILE", "8888888888")));

        try {
            QrGenerateResponse response = service.generateMyStaticQr("usd", WalletType.MAIN);
            QrScanResponse scanned = service.scan(scanRequest(response.getPayload()));

            assertEquals("MERCHANTPAY", scanned.getOperationType());
            assertEquals(AccountType.MERCHANT, scanned.getCreditorAccountType());
            assertEquals(IdentifierType.MSISDN, scanned.getCreditorIdentifierType());
            assertEquals("8888888888", scanned.getCreditorIdentifierValue());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private QrPaymentService service() {
        return new QrPaymentService(
                qrPaymentIntentRepository,
                accountIdentifierRepository,
                merchPayPaymentService,
                u2uPaymentService,
                new ObjectMapper(),
                "test-secret"
        );
    }

    private QrGenerateRequest staticGenerateRequest(BigDecimal amount) {
        QrGenerateRequest request = new QrGenerateRequest();
        request.setQrType(QrType.STATIC);
        request.setOperationType("MERCHANTPAY");
        request.setCreditor(merchantCreditor());
        request.setCurrency("usd");
        request.setAmount(amount);
        return request;
    }

    private QrCreditor merchantCreditor() {
        QrCreditor creditor = new QrCreditor();
        creditor.setIdentifierType(IdentifierType.LOGINID);
        creditor.setIdentifierValue("merchant-login");
        creditor.setAccountType(AccountType.MERCHANT);
        creditor.setWalletType(WalletType.MAIN);
        return creditor;
    }

    private QrScanRequest scanRequest(String payload) {
        QrScanRequest request = new QrScanRequest();
        request.setPayload(payload);
        return request;
    }

    private QrPayRequest payRequest(String payload) {
        QrPayRequest request = new QrPayRequest();
        request.setPayload(payload);
        request.setRequestGateway(RequestGateway.MOBILE);
        request.setPreferredLang("en");
        request.setInitiatedBy(InitiatedBy.DEBITOR);
        request.setDebitor(debitor());
        request.setAmount(new BigDecimal("12.25"));
        return request;
    }

    private QrPaymentIntent activeIntent() {
        QrPaymentIntent intent = new QrPaymentIntent();
        intent.setQrIntentId("QR123");
        intent.setOperationType("MERCHANTPAY");
        intent.setCreditorIdentifierType("LOGINID");
        intent.setCreditorIdentifierValue("merchant-login");
        intent.setCreditorAccountType("MERCHANT");
        intent.setCreditorWalletType("MAIN");
        intent.setCurrency("USD");
        intent.setAmount(new BigDecimal("20.00"));
        intent.setStatus(QrIntentStatus.ACTIVE);
        intent.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        return intent;
    }

    private AccountIdentifier accountIdentifier(String accountId, String identifierType, String identifierValue) {
        AccountIdentifier identifier = new AccountIdentifier();
        identifier.setAccountId(accountId);
        identifier.setIdentifierType(identifierType);
        identifier.setIdentifierValue(identifierValue);
        identifier.setStatus("ACTIVE");
        return identifier;
    }

    private void setAuthentication(String accountId, String scope) {
        org.springframework.security.core.Authentication authentication =
                org.mockito.Mockito.mock(org.springframework.security.core.Authentication.class);
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        org.mockito.Mockito.when(authentication.getName()).thenReturn(accountId);
        org.mockito.Mockito.when(authentication.getDetails()).thenReturn(claims);
        org.mockito.Mockito.when(claims.get("scope")).thenReturn(scope);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private Party debitor() {
        Party debitor = new Party();
        debitor.setAccountType(AccountType.SUBSCRIBER);
        debitor.setWalletType(WalletType.MAIN);
        Identifier identifier = new Identifier();
        identifier.setType(IdentifierType.MOBILE);
        identifier.setValue("9999999999");
        debitor.setIdentifier(identifier);
        Authentication authentication = new Authentication();
        authentication.setType(AuthType.PIN);
        authentication.setValue("1234");
        debitor.setAuthentication(authentication);
        return debitor;
    }
}
