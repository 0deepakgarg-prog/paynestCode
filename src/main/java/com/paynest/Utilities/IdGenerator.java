package com.paynest.Utilities;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.commons.codec.digest.DigestUtils;

public class IdGenerator {

    private static final SecureRandom secureRandom = new SecureRandom();
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HHmmss");

    public static String generateAccountId() {

        LocalDateTime now = LocalDateTime.now();

        String datePart = now.format(DATE_FORMAT);
        String timePart = now.format(TIME_FORMAT);

        // 6 digit random number
        int random = ThreadLocalRandom.current()
                .nextInt(100000, 999999);

        return String.format(
                "AC%s%s%06d",
                datePart,
                timePart,
                random
        );
    }

    public static long generateAccountAuthId() {

        long timestamp = System.currentTimeMillis();

        int randomPart = secureRandom.nextInt(900) + 100; // 3 digit random

        return Long.parseLong(timestamp + String.valueOf(randomPart));
    }

    public static String generate4DigitPin() {
        int pin = secureRandom.nextInt(9000) + 1000;
        return String.valueOf(pin);
    }

    public static String hashPin(String pin, String uuid) {
        return DigestUtils.sha256Hex(pin + uuid);
    }

    public static boolean verifyPin(String inputPin,
                                    String storedHash,
                                    String uuid) {

        String inputHash = DigestUtils.sha256Hex(inputPin + uuid);
        return inputHash.equals(storedHash);
    }
}
