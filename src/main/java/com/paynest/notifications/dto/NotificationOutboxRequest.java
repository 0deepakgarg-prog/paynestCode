package com.paynest.notifications.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NotificationOutboxRequest {

    private String transactionId;
    private String accountId;
    private String partyRole;
    private String channel;
    private String recipient;
    private String recipientMasked;
    private String templateCode;
    private String subject;
    private String title;
    private String notificationText;
    private JsonNode payload;
    private String serviceCode;
    private String transferStatus;
    private String traceId;
}
