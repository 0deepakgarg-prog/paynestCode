package com.paynest.notifications.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paynest.config.PropertyReader;
import com.paynest.notifications.dto.NotificationOutboxRequest;
import com.paynest.notifications.entity.NotificationOutbox;
import com.paynest.notifications.event.TransactionNotificationEvent;
import com.paynest.users.entity.AccountNotificationEndpoint;
import com.paynest.users.entity.NotificationTemplate;
import com.paynest.users.repository.AccountNotificationEndpointRepository;
import com.paynest.users.repository.NotificationTemplateRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AsyncTransactionNotificationProcessorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void process_shouldQueueSmsPayloadForMobileEndpoint() throws Exception {
        NotificationTemplateRepository templateRepository = mock(NotificationTemplateRepository.class);
        AccountNotificationEndpointRepository endpointRepository = mock(AccountNotificationEndpointRepository.class);
        PropertyReader propertyReader = mock(PropertyReader.class);
        NotificationOutboxService notificationOutboxService = mock(NotificationOutboxService.class);

        NotificationTemplate template = new NotificationTemplate();
        template.setTemplateCode("U2U.TRANSFER_SUCCESS.SENDER");
        template.setTemplateDefinition(objectMapper.readTree("""
                {
                  "defaultLanguage": "en",
                  "defaultChannel": "PUSH",
                  "channels": {
                    "EMAIL": {
                      "languages": {
                        "en": {
                          "subject": "Email subject",
                          "body": "Email body {{amount}} {{currency}}"
                        }
                      }
                    },
                    "SMS": {
                      "senderId": "PAYNEST",
                      "languages": {
                        "en": {
                          "body": "SMS body {{amount}} {{currency}}"
                        }
                      }
                    },
                    "PUSH": {
                      "languages": {
                        "en": {
                          "title": "Push title",
                          "body": "Push body {{amount}} {{currency}}"
                        }
                      }
                    }
                  }
                }
                """));

        AccountNotificationEndpoint endpoint = new AccountNotificationEndpoint();
        endpoint.setAccountId("sender-1");
        endpoint.setEndpointType("MOBILE");
        endpoint.setEndpointValue("+15550001");

        when(propertyReader.getPropertyValue("currency.factor")).thenReturn("100");
        when(templateRepository.findByTemplateCodeLikeAndStatusOrderByTemplateCodeAsc(anyString(), eq("ACTIVE")))
                .thenReturn(List.of(template));
        when(endpointRepository.findByAccountIdAndStatusAndIsPrimaryTrue("sender-1", "ACTIVE"))
                .thenReturn(List.of(endpoint));

        NotificationOutbox savedNotification = new NotificationOutbox();
        savedNotification.setNotificationId(1L);
        when(notificationOutboxService.enqueue(any(NotificationOutboxRequest.class))).thenReturn(savedNotification);

        AsyncTransactionNotificationProcessor processor = new AsyncTransactionNotificationProcessor(
                templateRepository,
                endpointRepository,
                objectMapper,
                propertyReader,
                notificationOutboxService
        );

        processor.process(TransactionNotificationEvent.builder()
                .transactionId("txn-1")
                .tenantSchema("tenant_one")
                .tenantId("TENANT")
                .tenantTimeZone("UTC")
                .transferStatus("TS")
                .serviceCode("U2U")
                .traceId("trace-1")
                .transactionValue(new BigDecimal("1250"))
                .debitorAccountId("sender-1")
                .debitorCurrency("USD")
                .build());

        ArgumentCaptor<NotificationOutboxRequest> captor =
                ArgumentCaptor.forClass(NotificationOutboxRequest.class);
        verify(notificationOutboxService).enqueue(captor.capture());

        NotificationOutboxRequest request = captor.getValue();
        assertEquals("SMS", request.getChannel());
        assertEquals("+15550001", request.getRecipient());
        assertEquals("SMS body 12.5 USD", request.getNotificationText());
        assertEquals("PAYNEST", request.getPayload().get("senderId").asText());
    }
}
