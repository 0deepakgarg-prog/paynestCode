package com.paynest.notifications.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.paynest.common.Constants;
import com.paynest.config.AsyncEventConfig;
import com.paynest.config.PropertyReader;
import com.paynest.config.tenant.TenantContext;
import com.paynest.config.tenant.TraceContext;
import com.paynest.notifications.dto.NotificationOutboxRequest;
import com.paynest.notifications.event.TransactionNotificationEvent;
import com.paynest.users.entity.AccountNotificationEndpoint;
import com.paynest.users.entity.NotificationTemplate;
import com.paynest.users.repository.AccountNotificationEndpointRepository;
import com.paynest.users.repository.NotificationTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncTransactionNotificationProcessor {

    private final NotificationTemplateRepository notificationTemplateRepository;
    private final AccountNotificationEndpointRepository accountNotificationEndpointRepository;
    private final ObjectMapper objectMapper;
    private final PropertyReader propertyReader;
    private final NotificationOutboxService notificationOutboxService;
    private static final List<String> TEXT_FIELD_NAMES = List.of(
            "notificationText",
            "message",
            "body",
            "content",
            "text"
    );

    @Async(AsyncEventConfig.NOTIFICATION_EVENT_EXECUTOR)
    public void process(TransactionNotificationEvent event) {
        try {
            restoreAsyncContext(event);
            Map<String, Object> context = buildContext(event);
            generateNotifications(event, context);
        } catch (Exception ex) {
            log.error(
                    "Failed to process transaction notification event. transactionId={}, status={}",
                    event.getTransactionId(),
                    event.getTransferStatus(),
                    ex
            );
        } finally {
            clearAsyncContext();
        }
    }

    private void restoreAsyncContext(TransactionNotificationEvent event) {
        TenantContext.setTenant(event.getTenantSchema());
        TenantContext.setTenantId(event.getTenantId());
        TenantContext.setTimeZone(event.getTenantTimeZone());
        TraceContext.setTraceId(event.getTraceId());
    }

    private void clearAsyncContext() {
        TenantContext.clear();
        TraceContext.clear();
    }

    private Optional<NotificationTemplate> findTemplate(TransactionNotificationEvent event, String partyRole) {
        String statusName = statusTemplateName(event.getTransferStatus());
        return findTemplateByLikeCode(event.getServiceCode(), statusName, partyRole)
                .or(() -> findTemplateByLikeCode("TRANSACTION", statusName, partyRole));
    }

    private Optional<NotificationTemplate> findTemplateByLikeCode(String serviceCode, String statusName, String partyRole) {
        String safeServiceCode = serviceCode == null || serviceCode.isBlank() ? "TRANSACTION" : serviceCode.trim();
        String pattern = safeServiceCode.toUpperCase() + ".%" + statusName + "%." + partyRole.toUpperCase();
        List<NotificationTemplate> templates =
                notificationTemplateRepository.findByTemplateCodeLikeAndStatusOrderByTemplateCodeAsc(
                        pattern,
                        Constants.ACCOUNT_STATUS_ACTIVE
                );
        return templates.stream().findFirst();
    }

    private String statusTemplateName(String transferStatus) {
        if (Constants.TRANSACTION_SUCCESS.equalsIgnoreCase(transferStatus)) {
            return "SUCCESS";
        }
        if (Constants.TRANSACTION_FAILED.equalsIgnoreCase(transferStatus)) {
            return "FAILED";
        }
        if (Constants.TRANSACTION_INITIATED.equalsIgnoreCase(transferStatus)) {
            return "INITIATED";
        }
        return transferStatus == null || transferStatus.isBlank() ? "UNKNOWN" : transferStatus.trim().toUpperCase();
    }

    private Map<String, Object> buildContext(TransactionNotificationEvent event) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("transactionId", event.getTransactionId());
        context.put("tenantId", event.getTenantId());
        context.put("tenantSchema", event.getTenantSchema());
        context.put("transferStatus", event.getTransferStatus());
        context.put("previousStatus", event.getPreviousStatus());
        context.put("serviceCode", event.getServiceCode());
        context.put("requestGateway", event.getRequestGateway());
        context.put("traceId", event.getTraceId());
        context.put("transactionValue", event.getTransactionValue());
        context.put("amount", toDisplayAmount(event.getTransactionValue()));
        context.put("debitorAccountId", event.getDebitorAccountId());
        context.put("creditorAccountId", event.getCreditorAccountId());
        context.put("debitorWalletType", event.getDebitorWalletType());
        context.put("debitorCurrency", event.getDebitorCurrency());
        context.put("creditorWalletType", event.getCreditorWalletType());
        context.put("creditorCurrency", event.getCreditorCurrency());
        context.put("senderFirstName", event.getSenderFirstName());
        context.put("senderLastName", event.getSenderLastName());
        context.put("receiverFirstName", event.getReceiverFirstName());
        context.put("receiverLastName", event.getReceiverLastName());
        context.put("serviceChargeAmount", formatAmount(event.getServiceChargeAmount()));
        context.put("commissionAmount", formatAmount(event.getCommissionAmount()));
        context.put("discountAmount", formatAmount(event.getDiscountAmount()));
        context.put("taxAmount", formatAmount(event.getTaxAmount()));
        context.put("cashbackAmount", formatAmount(event.getCashbackAmount()));
        context.put("serviceCharge", formatAmount(event.getServiceChargeAmount()));
        context.put("commission", formatAmount(event.getCommissionAmount()));
        context.put("discount", formatAmount(event.getDiscountAmount()));
        context.put("tax", formatAmount(event.getTaxAmount()));
        context.put("cashback", formatAmount(event.getCashbackAmount()));
        context.put("currency", firstPresent(event.getCreditorCurrency(), event.getDebitorCurrency()));
        context.put("senderName", fullName(event.getSenderFirstName(), event.getSenderLastName(), event.getDebitorAccountId()));
        context.put("receiverName", fullName(event.getReceiverFirstName(), event.getReceiverLastName(), event.getCreditorAccountId()));
        context.put("errorCode", event.getErrorCode());
        context.put("paymentReference", event.getPaymentReference());
        context.put("transferOn", event.getTransferOn());
        if (event.getAttributes() != null) {
            context.putAll(event.getAttributes());
        }
        return context;
    }

    private JsonNode renderTemplate(JsonNode templateDefinition, Map<String, Object> context) throws Exception {
        if (templateDefinition == null || templateDefinition.isNull()) {
            return objectMapper.createObjectNode();
        }
        String rendered = templateDefinition.toString();
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            String value = entry.getValue() == null ? "" : entry.getValue().toString();
            rendered = rendered.replace("{{" + entry.getKey() + "}}", value);
        }
        rendered = rendered.replaceAll("\\{\\{[^}]+}}", "");
        return objectMapper.readTree(rendered);
    }

    private String toDisplayAmount(BigDecimal storedAmount) {
        if (storedAmount == null) {
            return "";
        }
        String configuredFactor = propertyReader.getPropertyValue("currency.factor");
        BigDecimal currencyFactor = configuredFactor == null || configuredFactor.isBlank()
                ? BigDecimal.ONE
                : new BigDecimal(configuredFactor);
        return storedAmount.divide(currencyFactor, 2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "0";
        }
        return amount.stripTrailingZeros().toPlainString();
    }

    private String fullName(String firstName, String lastName, String fallback) {
        StringBuilder name = new StringBuilder();
        if (firstName != null && !firstName.isBlank()) {
            name.append(firstName.trim());
        }
        if (lastName != null && !lastName.isBlank()) {
            if (name.length() > 0) {
                name.append(" ");
            }
            name.append(lastName.trim());
        }
        return name.length() == 0 ? fallback : name.toString();
    }

    private String firstPresent(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private void generateNotifications(TransactionNotificationEvent event, Map<String, Object> context) throws Exception {
        notifyAccount(event.getDebitorAccountId(), "SENDER", event, context);
        if (event.getCreditorAccountId() != null && !event.getCreditorAccountId().equals(event.getDebitorAccountId())) {
            notifyAccount(event.getCreditorAccountId(), "RECEIVER", event, context);
        }
    }

    private void notifyAccount(
            String accountId,
            String partyRole,
            TransactionNotificationEvent event,
            Map<String, Object> context
    ) throws Exception {
        if (accountId == null || accountId.isBlank()) {
            return;
        }

        Optional<NotificationTemplate> template = findTemplate(event, partyRole);
        if (template.isEmpty()) {
            return;
        }

        List<AccountNotificationEndpoint> endpoints =
                accountNotificationEndpointRepository.findByAccountIdAndStatusAndIsPrimaryTrue(
                        accountId,
                        Constants.ACCOUNT_STATUS_ACTIVE
                );

        for (AccountNotificationEndpoint endpoint : endpoints) {
            Map<String, Object> replacementValues = buildEndpointReplacementValues(context, accountId, partyRole, endpoint);
            JsonNode renderedTemplate = renderTemplate(template.get().getTemplateDefinition(), replacementValues);
            String channel = resolveChannel(endpoint);
            JsonNode deliveryPayload = selectDeliveryPayload(renderedTemplate, channel);
            Map<String, Object> finalNotification = buildFinalNotification(
                    endpoint,
                    template.get(),
                    channel,
                    deliveryPayload
            );
            var queuedNotification = notificationOutboxService.enqueue(NotificationOutboxRequest.builder()
                    .transactionId(event.getTransactionId())
                    .accountId(accountId)
                    .partyRole(partyRole)
                    .channel(channel)
                    .recipient(endpoint.getEndpointValue())
                    .recipientMasked(maskEndpoint(endpoint))
                    .templateCode(template.get().getTemplateCode())
                    .subject((String) finalNotification.get("subject"))
                    .title((String) finalNotification.get("title"))
                    .notificationText((String) finalNotification.get("text"))
                    .payload(deliveryPayload)
                    .serviceCode(event.getServiceCode())
                    .transferStatus(event.getTransferStatus())
                    .traceId(event.getTraceId())
                    .build());

            log.info(
                    "Queued notification. notificationId={}, transactionId={}, accountId={}, channel={}, recipient={}, templateCode={}",
                    queuedNotification.getNotificationId(),
                    event.getTransactionId(),
                    accountId,
                    channel,
                    maskEndpoint(endpoint),
                    template.get().getTemplateCode()
            );
        }
    }

    private Map<String, Object> buildEndpointReplacementValues(
            Map<String, Object> context,
            String accountId,
            String partyRole,
            AccountNotificationEndpoint endpoint
    ) {
        Map<String, Object> replacementValues = new LinkedHashMap<>(context);
        replacementValues.put("accountId", accountId);
        replacementValues.put("partyRole", partyRole);
        replacementValues.put("endpointType", endpoint.getEndpointType());
        replacementValues.put("endpointValue", endpoint.getEndpointValue());
        return replacementValues;
    }

    private Map<String, Object> buildFinalNotification(
            AccountNotificationEndpoint endpoint,
            NotificationTemplate template,
            String channel,
            JsonNode deliveryPayload
    ) {
        Map<String, Object> finalNotification = new LinkedHashMap<>();
        finalNotification.put("channel", channel);
        finalNotification.put("recipient", maskEndpoint(endpoint));
        finalNotification.put("templateCode", template.getTemplateCode());
        addIfPresent(finalNotification, "subject", extractTextField(deliveryPayload, "subject"));
        addIfPresent(finalNotification, "title", extractTextField(deliveryPayload, "title"));
        finalNotification.put("text", extractNotificationText(deliveryPayload));
        return finalNotification;
    }

    private String resolveChannel(AccountNotificationEndpoint endpoint) {
        String endpointType = endpoint.getEndpointType();
        if (endpointType == null || endpointType.isBlank()) {
            return "UNKNOWN";
        }

        String normalizedEndpointType = endpointType.trim().toUpperCase(Locale.ROOT);
        return switch (normalizedEndpointType) {
            case "MOBILE", "MSISDN", "PHONE", "PHONE_NUMBER" -> "SMS";
            case "FCM", "APNS", "DEVICE", "DEVICE_TOKEN", "PUSH_TOKEN" -> "PUSH";
            default -> normalizedEndpointType;
        };
    }

    private JsonNode selectDeliveryPayload(JsonNode renderedTemplate, String channel) {
        if (renderedTemplate == null || !renderedTemplate.isObject()) {
            return renderedTemplate;
        }

        JsonNode channelsNode = findField(renderedTemplate, "channels");
        if (channelsNode == null || !channelsNode.isObject()) {
            return renderedTemplate;
        }

        JsonNode channelNode = findField(channelsNode, channel);
        if (channelNode == null) {
            String defaultChannel = extractDirectTextField(renderedTemplate, "defaultChannel");
            channelNode = findField(channelsNode, defaultChannel);
        }
        if (channelNode == null) {
            return renderedTemplate;
        }

        return selectLanguagePayload(renderedTemplate, channelNode);
    }

    private JsonNode selectLanguagePayload(JsonNode rootTemplate, JsonNode channelNode) {
        if (channelNode == null || !channelNode.isObject()) {
            return channelNode;
        }

        JsonNode languagesNode = findField(channelNode, "languages");
        if (languagesNode == null || !languagesNode.isObject()) {
            return channelNode;
        }

        String defaultLanguage = extractDirectTextField(rootTemplate, "defaultLanguage");
        JsonNode languageNode = findField(languagesNode, defaultLanguage);
        if (languageNode == null) {
            languageNode = findField(languagesNode, "en");
        }
        if (languageNode == null) {
            languageNode = firstFieldValue(languagesNode);
        }
        if (languageNode == null) {
            return channelNode;
        }

        ObjectNode payload = objectMapper.createObjectNode();
        channelNode.fields().forEachRemaining(field -> {
            if (!field.getKey().equalsIgnoreCase("languages")) {
                payload.set(field.getKey(), field.getValue());
            }
        });
        if (languageNode.isObject()) {
            languageNode.fields().forEachRemaining(field -> payload.set(field.getKey(), field.getValue()));
        } else {
            payload.set("body", languageNode);
        }
        return payload;
    }

    private JsonNode findField(JsonNode node, String fieldName) {
        if (node == null || !node.isObject() || fieldName == null || fieldName.isBlank()) {
            return null;
        }

        var fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (field.getKey().equalsIgnoreCase(fieldName)) {
                return field.getValue();
            }
        }
        return null;
    }

    private JsonNode firstFieldValue(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        var fields = node.fields();
        return fields.hasNext() ? fields.next().getValue() : null;
    }

    private String extractDirectTextField(JsonNode node, String fieldName) {
        JsonNode value = findField(node, fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.isTextual() ? value.asText() : value.toString();
    }

    private String extractNotificationText(JsonNode renderedTemplate) {
        if (renderedTemplate == null || renderedTemplate.isNull()) {
            return "";
        }
        if (renderedTemplate.isTextual()) {
            return renderedTemplate.asText();
        }
        for (String fieldName : TEXT_FIELD_NAMES) {
            String value = extractTextField(renderedTemplate, fieldName);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return collectTextValues(renderedTemplate);
    }

    private String extractTextField(JsonNode node, String fieldName) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (field.getKey().equalsIgnoreCase(fieldName) && !field.getValue().isNull()) {
                    return field.getValue().isTextual() ? field.getValue().asText() : field.getValue().toString();
                }
            }
            fields = node.fields();
            while (fields.hasNext()) {
                String nestedValue = extractTextField(fields.next().getValue(), fieldName);
                if (nestedValue != null && !nestedValue.isBlank()) {
                    return nestedValue;
                }
            }
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                String nestedValue = extractTextField(child, fieldName);
                if (nestedValue != null && !nestedValue.isBlank()) {
                    return nestedValue;
                }
            }
        }
        return null;
    }

    private String collectTextValues(JsonNode node) {
        List<String> values = new java.util.ArrayList<>();
        collectTextValues(node, values);
        return String.join(" ", values);
    }

    private void collectTextValues(JsonNode node, List<String> values) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            values.add(node.asText());
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectTextValues(child, values);
            }
            return;
        }
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                collectTextValues(fields.next().getValue(), values);
            }
        }
    }

    private void addIfPresent(Map<String, Object> values, String key, String value) {
        if (value != null && !value.isBlank()) {
            values.put(key, value);
        }
    }

    private String maskEndpoint(AccountNotificationEndpoint endpoint) {
        return maskValue(endpoint.getEndpointValue());
    }

    private String maskValue(String value) {
        if (value == null || value.length() <= 4) {
            return value;
        }
        return "****" + value.substring(value.length() - 4);
    }
}
