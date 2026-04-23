package com.paynest.config.tenant;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

public final class TenantTime {

    public static final String DEFAULT_TIME_ZONE = "UTC";
    private static final ZoneId DEFAULT_ZONE_ID = ZoneId.of(DEFAULT_TIME_ZONE);

    private TenantTime() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(currentZoneId());
    }

    public static LocalDate today() {
        return LocalDate.now(currentZoneId());
    }

    public static ZonedDateTime zonedNow() {
        return ZonedDateTime.now(currentZoneId());
    }

    public static Instant instant() {
        return Instant.now();
    }

    public static Date date() {
        return Date.from(instant());
    }

    public static Date dateAfterMillis(long millis) {
        return Date.from(instant().plusMillis(millis));
    }

    public static long epochMillis() {
        return instant().toEpochMilli();
    }

    public static ZoneId currentZoneId() {
        String timeZone = TenantContext.getTimeZone();
        if (timeZone == null || timeZone.isBlank()) {
            return DEFAULT_ZONE_ID;
        }
        try {
            return ZoneId.of(timeZone);
        } catch (Exception ex) {
            return DEFAULT_ZONE_ID;
        }
    }
}
