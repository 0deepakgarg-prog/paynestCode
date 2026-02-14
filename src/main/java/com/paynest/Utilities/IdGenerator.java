package com.paynest.Utilities;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

public class IdGenerator {

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

}
