package com.paynest.Utilities;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.paynest.config.PropertyReader;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class IdGenerator {

    private static final SecureRandom secureRandom = new SecureRandom();
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter SHORT_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyMMdd");

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HHmmss");

    private static final String CHAR_POOL =
            "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // No 0,O,1,I
    private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS = "0123456789";
    private static final String SPECIAL = "@#$%&*!?";
    private static final String ALL = UPPER + LOWER + DIGITS + SPECIAL;

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
        log.info("generated Pin is : " + pin);
        return String.valueOf(pin);
    }


    public static String generatePassword(int length) {

        if (length < 8) {
            throw new IllegalArgumentException("Password length must be >= 8");
        }

        List<Character> password = new ArrayList<>();
        password.add(UPPER.charAt(secureRandom.nextInt(UPPER.length())));
        password.add(LOWER.charAt(secureRandom.nextInt(LOWER.length())));
        password.add(DIGITS.charAt(secureRandom.nextInt(DIGITS.length())));
        password.add(SPECIAL.charAt(secureRandom.nextInt(SPECIAL.length())));
        for (int i = 4; i < length; i++) {
            password.add(ALL.charAt(secureRandom.nextInt(ALL.length())));
        }
        Collections.shuffle(password, secureRandom);
        StringBuilder sb = new StringBuilder();
        for (char ch : password) {
            sb.append(ch);
        }
        log.info("generated Password is : " + sb.toString());
        return sb.toString();
    }

    public static String generateLoginId(int length) {

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < length; i++) {
            sb.append(
                    CHAR_POOL.charAt(secureRandom.nextInt(CHAR_POOL.length()))
            );
        }
        log.info("generated LoginId is : " + sb.toString());
        return sb.toString();
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

    public static String generateTransactionId(String prefix, String serverInstance) {
        LocalDateTime timeStamp=LocalDateTime.now();
        String datePart = timeStamp.format(SHORT_DATE_FORMAT);
        String timePart = timeStamp.format(TIME_FORMAT);
        int randomNumber = secureRandom.nextInt(10000);
        String randomPart = String.format("%04d", randomNumber);
        return prefix.concat(datePart)
                .concat("-")
                .concat(timePart)
                .concat("-")
                .concat(serverInstance)
                .concat(randomPart);
    }

    public static String generateTransactionId(String prefix) {
        return generateTransactionId(prefix, "A");
    }
}

