package com.paynest.notifications.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paynest.notifications.dto.NotificationOutboxRequest;
import com.paynest.notifications.entity.NotificationOutbox;
import com.paynest.notifications.repository.NotificationOutboxRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationOutboxServiceTest {

    @Test
    void enqueue_shouldCreatePendingNotificationRow() {
        NotificationOutboxRepository repository = mock(NotificationOutboxRepository.class);
        when(repository.save(any(NotificationOutbox.class))).thenAnswer(invocation -> invocation.getArgument(0));
        NotificationOutboxService service = new NotificationOutboxService(repository);

        service.enqueue(NotificationOutboxRequest.builder()
                .transactionId("txn-1")
                .accountId("account-1")
                .partyRole("sender")
                .channel("sms")
                .recipient("+15550001")
                .recipientMasked("****0001")
                .templateCode("U2U.SUCCESS.SENDER")
                .notificationText("Payment successful")
                .payload(new ObjectMapper().createObjectNode().put("body", "Payment successful"))
                .serviceCode("u2u")
                .transferStatus("ts")
                .traceId("trace-1")
                .build());

        ArgumentCaptor<NotificationOutbox> captor = ArgumentCaptor.forClass(NotificationOutbox.class);
        verify(repository).save(captor.capture());

        NotificationOutbox notification = captor.getValue();
        assertEquals(NotificationOutboxService.STATUS_PENDING, notification.getStatus());
        assertEquals(0, notification.getAttemptCount());
        assertEquals("SENDER", notification.getPartyRole());
        assertEquals("SMS", notification.getChannel());
        assertEquals("+15550001", notification.getRecipient());
        assertEquals("Payment successful", notification.getNotificationText());
        assertEquals("U2U", notification.getServiceCode());
        assertEquals("TS", notification.getTransferStatus());
    }
}
