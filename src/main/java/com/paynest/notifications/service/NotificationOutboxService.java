package com.paynest.notifications.service;

import com.paynest.notifications.dto.NotificationOutboxRequest;
import com.paynest.notifications.entity.NotificationOutbox;
import com.paynest.notifications.repository.NotificationOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class NotificationOutboxService {

    public static final String STATUS_PENDING = "PENDING";

    private final NotificationOutboxRepository notificationOutboxRepository;

    public NotificationOutbox enqueue(NotificationOutboxRequest request) {
        NotificationOutbox notification = new NotificationOutbox();
        notification.setTransactionId(request.getTransactionId());
        notification.setAccountId(request.getAccountId());
        notification.setPartyRole(uppercase(request.getPartyRole()));
        notification.setChannel(uppercase(request.getChannel()));
        notification.setRecipient(request.getRecipient());
        notification.setRecipientMasked(request.getRecipientMasked());
        notification.setTemplateCode(request.getTemplateCode());
        notification.setSubject(request.getSubject());
        notification.setTitle(request.getTitle());
        notification.setNotificationText(request.getNotificationText());
        notification.setPayload(request.getPayload());
        notification.setStatus(STATUS_PENDING);
        notification.setAttemptCount(0);
        notification.setServiceCode(uppercase(request.getServiceCode()));
        notification.setTransferStatus(uppercase(request.getTransferStatus()));
        notification.setTraceId(request.getTraceId());
        return notificationOutboxRepository.save(notification);
    }

    private String uppercase(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
