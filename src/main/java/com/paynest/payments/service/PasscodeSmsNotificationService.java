package com.paynest.payments.service;

import com.paynest.config.PropertyReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasscodeSmsNotificationService {

    private final PropertyReader propertyReader;

    public void sendPasscode(String senderMsisdn, String receiverMsisdn, String passcode) {
        String target = propertyReader.getPropertyValue("passcode.sms.recipients");
        String normalizedTarget = target == null || target.isBlank()
                ? "BOTH"
                : target.trim().toUpperCase(Locale.ROOT);

        if ("SENDER".equals(normalizedTarget) || "BOTH".equals(normalizedTarget)) {
            log.info("Passcode SMS requested for sender msisdn={}", senderMsisdn);
        }
        if ("RECEIVER".equals(normalizedTarget) || "BOTH".equals(normalizedTarget)) {
            log.info("Passcode SMS requested for receiver msisdn={}", receiverMsisdn);
        }
        if (!"SENDER".equals(normalizedTarget)
                && !"RECEIVER".equals(normalizedTarget)
                && !"BOTH".equals(normalizedTarget)
                && !"NONE".equals(normalizedTarget)) {
            log.warn("Unsupported passcode.sms.recipients value '{}'. Passcode SMS skipped.", normalizedTarget);
        }
    }
}
