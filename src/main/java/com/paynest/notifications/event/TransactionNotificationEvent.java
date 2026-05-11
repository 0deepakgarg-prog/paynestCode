package com.paynest.notifications.event;

import lombok.Builder;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Getter
public class TransactionNotificationEvent extends ApplicationEvent {

    private final String transactionId;
    private final String tenantSchema;
    private final String tenantId;
    private final String tenantTimeZone;
    private final String transferStatus;
    private final String previousStatus;
    private final String serviceCode;
    private final String requestGateway;
    private final String traceId;
    private final BigDecimal transactionValue;
    private final String debitorAccountId;
    private final String creditorAccountId;
    private final String debitorWalletType;
    private final String debitorCurrency;
    private final String creditorWalletType;
    private final String creditorCurrency;
    private final String senderFirstName;
    private final String senderLastName;
    private final String receiverFirstName;
    private final String receiverLastName;
    private final BigDecimal serviceChargeAmount;
    private final BigDecimal commissionAmount;
    private final BigDecimal discountAmount;
    private final BigDecimal taxAmount;
    private final BigDecimal cashbackAmount;
    private final String errorCode;
    private final String paymentReference;
    private final LocalDateTime transferOn;
    private final Map<String, String> attributes;
    private final transient AtomicBoolean listenerDeliveryStarted = new AtomicBoolean(false);

    @Builder
    public TransactionNotificationEvent(
            String transactionId,
            String tenantSchema,
            String tenantId,
            String tenantTimeZone,
            String transferStatus,
            String previousStatus,
            String serviceCode,
            String requestGateway,
            String traceId,
            BigDecimal transactionValue,
            String debitorAccountId,
            String creditorAccountId,
            String debitorWalletType,
            String debitorCurrency,
            String creditorWalletType,
            String creditorCurrency,
            String senderFirstName,
            String senderLastName,
            String receiverFirstName,
            String receiverLastName,
            BigDecimal serviceChargeAmount,
            BigDecimal commissionAmount,
            BigDecimal discountAmount,
            BigDecimal taxAmount,
            BigDecimal cashbackAmount,
            String errorCode,
            String paymentReference,
            LocalDateTime transferOn,
            Map<String, String> attributes
    ) {
        super(resolveSource(transactionId));
        this.transactionId = transactionId;
        this.tenantSchema = tenantSchema;
        this.tenantId = tenantId;
        this.tenantTimeZone = tenantTimeZone;
        this.transferStatus = transferStatus;
        this.previousStatus = previousStatus;
        this.serviceCode = serviceCode;
        this.requestGateway = requestGateway;
        this.traceId = traceId;
        this.transactionValue = transactionValue;
        this.debitorAccountId = debitorAccountId;
        this.creditorAccountId = creditorAccountId;
        this.debitorWalletType = debitorWalletType;
        this.debitorCurrency = debitorCurrency;
        this.creditorWalletType = creditorWalletType;
        this.creditorCurrency = creditorCurrency;
        this.senderFirstName = senderFirstName;
        this.senderLastName = senderLastName;
        this.receiverFirstName = receiverFirstName;
        this.receiverLastName = receiverLastName;
        this.serviceChargeAmount = serviceChargeAmount;
        this.commissionAmount = commissionAmount;
        this.discountAmount = discountAmount;
        this.taxAmount = taxAmount;
        this.cashbackAmount = cashbackAmount;
        this.errorCode = errorCode;
        this.paymentReference = paymentReference;
        this.transferOn = transferOn;
        this.attributes = attributes;
    }

    private static Object resolveSource(String transactionId) {
        return transactionId == null || transactionId.isBlank()
                ? TransactionNotificationEvent.class.getName()
                : transactionId;
    }

    public boolean markListenerDeliveryStarted() {
        return listenerDeliveryStarted.compareAndSet(false, true);
    }
}
