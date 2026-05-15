package com.paynest.payments.qr.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.paynest.config.tenant.TenantTime;
import com.paynest.common.Constants;
import com.paynest.config.security.JWTUtils;
import com.paynest.enums.AccountType;
import com.paynest.exception.ApplicationException;
import com.paynest.exception.PaymentErrorCode;
import com.paynest.payments.dto.Identifier;
import com.paynest.payments.dto.MerchpayPaymentRequest;
import com.paynest.payments.dto.Party;
import com.paynest.payments.dto.TransactionInfo;
import com.paynest.payments.dto.U2UPaymentRequest;
import com.paynest.payments.enums.InitiatedBy;
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
import com.paynest.users.enums.IdentifierType;
import com.paynest.users.enums.WalletType;
import com.paynest.users.repository.AccountIdentifierRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class QrPaymentService {

    private static final String VERSION = "1";
    private static final String MERCHANT_PAY = "MERCHANTPAY";
    private static final String U2U = "U2U";
    private static final int QR_IMAGE_SIZE = 300;
    private static final int DEFAULT_EXPIRY_MINUTES = 15;
    private static final int MAX_EXPIRY_MINUTES = 1440;

    private final QrPaymentIntentRepository qrPaymentIntentRepository;
    private final AccountIdentifierRepository accountIdentifierRepository;
    private final MerchPayPaymentService merchPayPaymentService;
    private final U2UPaymentService u2uPaymentService;
    private final ObjectMapper objectMapper;
    private final String signingSecret;
    private final SecureRandom secureRandom = new SecureRandom();

    public QrPaymentService(
            QrPaymentIntentRepository qrPaymentIntentRepository,
            AccountIdentifierRepository accountIdentifierRepository,
            MerchPayPaymentService merchPayPaymentService,
            U2UPaymentService u2uPaymentService,
            ObjectMapper objectMapper,
            @Value("${paynest.qr.signing-secret:paynest-qr-dev-secret}") String signingSecret
    ) {
        this.qrPaymentIntentRepository = qrPaymentIntentRepository;
        this.accountIdentifierRepository = accountIdentifierRepository;
        this.merchPayPaymentService = merchPayPaymentService;
        this.u2uPaymentService = u2uPaymentService;
        this.objectMapper = objectMapper;
        this.signingSecret = signingSecret;
    }

    @Transactional
    public QrGenerateResponse generate(QrGenerateRequest request) {
        String operationType = normalizeOperationType(request.getOperationType());
        validateSupportedOperation(operationType);

        LocalDateTime expiresAt = null;
        String qrIntentId = null;
        if (request.getQrType() == QrType.DYNAMIC) {
            if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ApplicationException(PaymentErrorCode.QR_AMOUNT_REQUIRED);
            }
            QrPaymentIntent intent = createIntent(request, operationType);
            qrIntentId = intent.getQrIntentId();
            expiresAt = intent.getExpiresAt();
        }

        String payload = buildSignedPayload(request, operationType, qrIntentId, expiresAt);
        return QrGenerateResponse.builder()
                .qrType(request.getQrType())
                .qrIntentId(qrIntentId)
                .operationType(operationType)
                .payload(payload)
                .qrImageBase64(generateQrImage(payload))
                .expiresAt(expiresAt)
                .build();
    }

    @Transactional(readOnly = true)
    public QrGenerateResponse generateMyStaticQr(String currency, WalletType walletType) {
        String currentAccountId = JWTUtils.getCurrentAccountId();
        AccountType currentAccountType = resolveCurrentAccountType();
        AccountIdentifier identifier = resolvePreferredIdentifier(currentAccountId, currentAccountType);

        QrCreditor creditor = new QrCreditor();
        creditor.setIdentifierType(toQrIdentifierType(identifier.getIdentifierType(), currentAccountType));
        creditor.setIdentifierValue(identifier.getIdentifierValue());
        creditor.setAccountType(currentAccountType);
        creditor.setWalletType(walletType == null ? WalletType.MAIN : walletType);

        QrGenerateRequest request = new QrGenerateRequest();
        request.setQrType(QrType.STATIC);
        request.setOperationType(resolveMyStaticOperationType(currentAccountType));
        request.setCreditor(creditor);
        request.setCurrency(currency);
        return generate(request);
    }

    @Transactional
    public QrScanResponse scan(QrScanRequest request) {
        ResolvedQrPayment resolved = resolve(request.getPayload(), null);
        return toScanResponse(resolved);
    }

    @Transactional
    public Object pay(QrPayRequest request) {
        ResolvedQrPayment resolved = resolve(request.getPayload(), request.getQrIntentId());
        BigDecimal amount = resolved.amount();
        String currency = resolved.currency();

        if (resolved.qrType() == QrType.STATIC) {
            if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ApplicationException(PaymentErrorCode.QR_AMOUNT_REQUIRED);
            }
            amount = request.getAmount();
            if (request.getCurrency() != null && !request.getCurrency().isBlank()) {
                currency = request.getCurrency().trim().toUpperCase(Locale.ROOT);
            }
        }

        Object response = executePayment(request, resolved, amount, currency);
        String transactionId = extractTransactionId(response);

        if (resolved.intent() != null) {
            QrPaymentIntent intent = resolved.intent();
            intent.setStatus(QrIntentStatus.PAID);
            intent.setTransactionId(transactionId);
            qrPaymentIntentRepository.save(intent);
        }

        return response;
    }

    private Object executePayment(
            QrPayRequest request,
            ResolvedQrPayment resolved,
            BigDecimal amount,
            String currency
    ) {
        Party debitor = request.getDebitor();
        if (debitor.getAuthentication() == null && request.getAuthentication() != null) {
            debitor.setAuthentication(request.getAuthentication());
        }

        Party creditor = toCreditorParty(resolved);
        TransactionInfo transactionInfo = new TransactionInfo();
        transactionInfo.setAmount(amount);
        transactionInfo.setCurrency(currency);

        if (MERCHANT_PAY.equals(resolved.operationType())) {
            MerchpayPaymentRequest paymentRequest = new MerchpayPaymentRequest();
            paymentRequest.setOperationType(MERCHANT_PAY);
            paymentRequest.setRequestGateway(request.getRequestGateway());
            paymentRequest.setPreferredLang(request.getPreferredLang());
            paymentRequest.setInitiatedBy(resolveInitiatedBy(request));
            paymentRequest.setDebitor(debitor);
            paymentRequest.setCreditor(creditor);
            paymentRequest.setTransaction(transactionInfo);
            paymentRequest.setPaymentReference(request.getPaymentReference());
            paymentRequest.setComments(request.getComments());
            paymentRequest.setMetadata(withQrMetadata(request.getMetadata(), resolved));
            paymentRequest.setAdditionalInfo(request.getAdditionalInfo());
            return merchPayPaymentService.processPayment(paymentRequest, true);
        }

        U2UPaymentRequest paymentRequest = new U2UPaymentRequest();
        paymentRequest.setOperationType(U2U);
        paymentRequest.setRequestGateway(request.getRequestGateway());
        paymentRequest.setPreferredLang(request.getPreferredLang());
        paymentRequest.setInitiatedBy(resolveInitiatedBy(request));
        paymentRequest.setDebitor(debitor);
        paymentRequest.setCreditor(creditor);
        paymentRequest.setTransaction(transactionInfo);
        paymentRequest.setPaymentReference(request.getPaymentReference());
        paymentRequest.setComments(request.getComments());
        paymentRequest.setMetadata(withQrMetadata(request.getMetadata(), resolved));
        paymentRequest.setAdditionalInfo(request.getAdditionalInfo());
        return u2uPaymentService.processPayment(paymentRequest, true);
    }

    private QrPaymentIntent createIntent(QrGenerateRequest request, String operationType) {
        LocalDateTime expiresAt = TenantTime.now().plusMinutes(resolveExpiryMinutes(request.getExpiresInMinutes()));
        QrCreditor creditor = request.getCreditor();

        QrPaymentIntent intent = new QrPaymentIntent();
        intent.setQrIntentId(generateIntentId());
        intent.setOperationType(operationType);
        intent.setCreditorIdentifierType(creditor.getIdentifierType().name());
        intent.setCreditorIdentifierValue(creditor.getIdentifierValue().trim());
        intent.setCreditorAccountType(creditor.getAccountType().name());
        intent.setCreditorWalletType(creditor.getWalletType().name());
        intent.setCurrency(request.getCurrency().trim().toUpperCase(Locale.ROOT));
        intent.setAmount(request.getAmount());
        intent.setExpiresAt(expiresAt);
        return qrPaymentIntentRepository.save(intent);
    }

    private ResolvedQrPayment resolve(String payload, String requestedIntentId) {
        if ((payload == null || payload.isBlank()) && (requestedIntentId == null || requestedIntentId.isBlank())) {
            throw new ApplicationException(PaymentErrorCode.QR_PAYLOAD_MISSING);
        }

        if (payload != null && !payload.isBlank()) {
            return resolvePayload(payload);
        }

        QrPaymentIntent intent = getUsableIntent(requestedIntentId);
        return fromIntent(intent);
    }

    private ResolvedQrPayment resolvePayload(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode data = root.get("data");
            String signature = root.path("signature").asText(null);
            if (data == null || signature == null || !signature.equals(sign(data))) {
                throw new ApplicationException(PaymentErrorCode.QR_PAYLOAD_INVALID);
            }

            String qrType = data.path("qrType").asText();
            if (QrType.DYNAMIC.name().equals(qrType)) {
                return fromIntent(getUsableIntent(data.path("qrIntentId").asText()));
            }

            String operationType = normalizeOperationType(data.path("operationType").asText());
            validateSupportedOperation(operationType);
            return new ResolvedQrPayment(
                    QrType.STATIC,
                    null,
                    operationType,
                    IdentifierType.valueOf(data.path("creditorIdentifierType").asText()),
                    data.path("creditorIdentifierValue").asText(),
                    AccountType.valueOf(data.path("creditorAccountType").asText()),
                    WalletType.valueOf(data.path("creditorWalletType").asText()),
                    data.path("currency").asText(),
                    data.hasNonNull("amount") ? new BigDecimal(data.path("amount").asText()) : null,
                    null,
                    null
            );
        } catch (ApplicationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApplicationException(PaymentErrorCode.QR_PAYLOAD_INVALID);
        }
    }

    private ResolvedQrPayment fromIntent(QrPaymentIntent intent) {
        return new ResolvedQrPayment(
                QrType.DYNAMIC,
                intent.getQrIntentId(),
                intent.getOperationType(),
                IdentifierType.valueOf(intent.getCreditorIdentifierType()),
                intent.getCreditorIdentifierValue(),
                AccountType.valueOf(intent.getCreditorAccountType()),
                WalletType.valueOf(intent.getCreditorWalletType()),
                intent.getCurrency(),
                intent.getAmount(),
                intent.getExpiresAt(),
                intent
        );
    }

    private QrPaymentIntent getUsableIntent(String qrIntentId) {
        QrPaymentIntent intent = qrPaymentIntentRepository.findFirstByQrIntentId(qrIntentId)
                .orElseThrow(() -> new ApplicationException(PaymentErrorCode.QR_INTENT_NOT_FOUND));
        if (intent.getStatus() == QrIntentStatus.PAID) {
            throw new ApplicationException(PaymentErrorCode.QR_INTENT_ALREADY_USED);
        }
        if (intent.getStatus() != QrIntentStatus.ACTIVE) {
            throw new ApplicationException(PaymentErrorCode.QR_PAYLOAD_INVALID);
        }
        if (intent.getExpiresAt().isBefore(TenantTime.now())) {
            intent.setStatus(QrIntentStatus.EXPIRED);
            qrPaymentIntentRepository.save(intent);
            throw new ApplicationException(PaymentErrorCode.QR_INTENT_EXPIRED);
        }
        return intent;
    }

    private String buildSignedPayload(
            QrGenerateRequest request,
            String operationType,
            String qrIntentId,
            LocalDateTime expiresAt
    ) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("version", VERSION);
            data.put("qrType", request.getQrType().name());
            data.put("operationType", operationType);

            if (request.getQrType() == QrType.DYNAMIC) {
                data.put("qrIntentId", qrIntentId);
                data.put("expiresAt", expiresAt.toString());
            } else {
                QrCreditor creditor = request.getCreditor();
                data.put("creditorIdentifierType", creditor.getIdentifierType().name());
                data.put("creditorIdentifierValue", creditor.getIdentifierValue().trim());
                data.put("creditorAccountType", creditor.getAccountType().name());
                data.put("creditorWalletType", creditor.getWalletType().name());
                data.put("currency", request.getCurrency().trim().toUpperCase(Locale.ROOT));
                data.put("amount", request.getAmount());
            }

            JsonNode dataNode = objectMapper.valueToTree(data);
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("data", dataNode);
            envelope.put("signature", sign(dataNode));
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException ex) {
            throw new ApplicationException(PaymentErrorCode.QR_GENERATION_FAILED);
        }
    }

    private String sign(JsonNode data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signature = mac.doFinal(objectMapper.writeValueAsBytes(data));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (GeneralSecurityException | JsonProcessingException ex) {
            throw new ApplicationException(PaymentErrorCode.QR_GENERATION_FAILED);
        }
    }

    private String generateQrImage(String payload) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(payload, BarcodeFormat.QR_CODE, QR_IMAGE_SIZE, QR_IMAGE_SIZE);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (WriterException | java.io.IOException ex) {
            throw new ApplicationException(PaymentErrorCode.QR_GENERATION_FAILED);
        }
    }

    private Party toCreditorParty(ResolvedQrPayment resolved) {
        Party creditor = new Party();
        creditor.setAccountType(resolved.creditorAccountType());
        creditor.setWalletType(resolved.creditorWalletType());
        Identifier identifier = new Identifier();
        identifier.setType(resolved.creditorIdentifierType());
        identifier.setValue(resolved.creditorIdentifierValue());
        creditor.setIdentifier(identifier);
        return creditor;
    }

    private QrScanResponse toScanResponse(ResolvedQrPayment resolved) {
        return QrScanResponse.builder()
                .qrType(resolved.qrType())
                .qrIntentId(resolved.qrIntentId())
                .operationType(resolved.operationType())
                .creditorIdentifierType(resolved.creditorIdentifierType())
                .creditorIdentifierValue(resolved.creditorIdentifierValue())
                .creditorAccountType(resolved.creditorAccountType())
                .creditorWalletType(resolved.creditorWalletType())
                .currency(resolved.currency())
                .amount(resolved.amount())
                .expiresAt(resolved.expiresAt())
                .build();
    }

    private Map<String, Object> withQrMetadata(Map<String, Object> metadata, ResolvedQrPayment resolved) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (metadata != null) {
            merged.putAll(metadata);
        }
        merged.put("paymentViaQr", true);
        merged.put("qrType", resolved.qrType().name());
        if (resolved.qrIntentId() != null) {
            merged.put("qrIntentId", resolved.qrIntentId());
        }
        return merged;
    }

    private InitiatedBy resolveInitiatedBy(QrPayRequest request) {
        return request.getInitiatedBy() == null ? InitiatedBy.DEBITOR : request.getInitiatedBy();
    }

    private String extractTransactionId(Object response) {
        try {
            Object value = response.getClass().getMethod("getTransactionId").invoke(response);
            return value == null ? null : value.toString();
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    private String normalizeOperationType(String operationType) {
        return operationType == null ? null : operationType.trim().toUpperCase(Locale.ROOT);
    }

    private void validateSupportedOperation(String operationType) {
        if (!MERCHANT_PAY.equals(operationType) && !U2U.equals(operationType)) {
            throw new ApplicationException(PaymentErrorCode.QR_PAYMENT_TYPE_NOT_SUPPORTED);
        }
    }

    private int resolveExpiryMinutes(Integer requestedMinutes) {
        if (requestedMinutes == null) {
            return DEFAULT_EXPIRY_MINUTES;
        }
        if (requestedMinutes <= 0) {
            return DEFAULT_EXPIRY_MINUTES;
        }
        return Math.min(requestedMinutes, MAX_EXPIRY_MINUTES);
    }

    private String generateIntentId() {
        long randomValue = secureRandom.nextLong() & Long.MAX_VALUE;
        return "QR" + System.currentTimeMillis() + Long.toString(randomValue, 36).toUpperCase(Locale.ROOT);
    }

    private AccountType resolveCurrentAccountType() {
        try {
            return AccountType.valueOf(JWTUtils.getCurrentAccountType().trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw new ApplicationException(PaymentErrorCode.INVALID_PRIVILEGES);
        }
    }

    private String resolveMyStaticOperationType(AccountType accountType) {
        return accountType == AccountType.MERCHANT ? MERCHANT_PAY : U2U;
    }

    private AccountIdentifier resolvePreferredIdentifier(String accountId, AccountType accountType) {
        List<AccountIdentifier> identifiers = accountIdentifierRepository.findByAccountIdAndStatus(
                accountId,
                Constants.ACCOUNT_STATUS_ACTIVE
        );

        return identifiers.stream()
                .filter(identifier -> identifier.getIdentifierType() != null)
                .filter(identifier -> isSupportedQrIdentifier(identifier.getIdentifierType()))
                .min(Comparator.comparingInt(identifier -> identifierPreference(identifier.getIdentifierType(), accountType)))
                .orElseThrow(() -> new ApplicationException(
                        PaymentErrorCode.ACCOUNT_IDENTIFIER_NOT_FOUND,
                        null,
                        Map.of("accountId", accountId)
                ));
    }

    private boolean isSupportedQrIdentifier(String identifierType) {
        try {
            IdentifierType.valueOf(identifierType);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private int identifierPreference(String identifierType, AccountType accountType) {
        IdentifierType type = IdentifierType.valueOf(identifierType);
        if (accountType == AccountType.MERCHANT) {
            return switch (type) {
                case LOGINID -> 0;
                case MOBILE, MSISDN -> 1;
                case ACCOUNT_ID -> 2;
            };
        }
        return switch (type) {
            case MOBILE -> 0;
            case MSISDN -> 1;
            case LOGINID -> 2;
            case ACCOUNT_ID -> 3;
        };
    }

    private IdentifierType toQrIdentifierType(String identifierType, AccountType accountType) {
        IdentifierType type = IdentifierType.valueOf(identifierType);
        if (accountType == AccountType.MERCHANT && type == IdentifierType.MOBILE) {
            return IdentifierType.MSISDN;
        }
        return type;
    }

    private record ResolvedQrPayment(
            QrType qrType,
            String qrIntentId,
            String operationType,
            IdentifierType creditorIdentifierType,
            String creditorIdentifierValue,
            AccountType creditorAccountType,
            WalletType creditorWalletType,
            String currency,
            BigDecimal amount,
            LocalDateTime expiresAt,
            QrPaymentIntent intent
    ) {
    }
}
