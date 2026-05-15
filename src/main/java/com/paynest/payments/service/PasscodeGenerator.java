package com.paynest.payments.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class PasscodeGenerator {
    private static final int PASSCODE_LENGTH = 10;
    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        StringBuilder builder = new StringBuilder(PASSCODE_LENGTH);
        for (int i = 0; i < PASSCODE_LENGTH; i++) {
            builder.append(secureRandom.nextInt(10));
        }
        return builder.toString();
    }
}
