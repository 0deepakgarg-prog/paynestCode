package com.paynest.config.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantTimeTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void currentZoneId_whenTenantTimeZoneIsPresent_returnsTenantZone() {
        TenantContext.setTimeZone("Europe/Chisinau");

        assertEquals(ZoneId.of("Europe/Chisinau"), TenantTime.currentZoneId());
    }

    @Test
    void currentZoneId_whenTenantTimeZoneIsMissing_returnsUtc() {
        assertEquals(ZoneId.of("UTC"), TenantTime.currentZoneId());
    }

    @Test
    void now_usesTenantZone() {
        TenantContext.setTimeZone("Europe/Chisinau");

        LocalDateTime expectedNow = LocalDateTime.now(ZoneId.of("Europe/Chisinau"));
        LocalDateTime tenantNow = TenantTime.now();

        assertEquals(expectedNow.getHour(), tenantNow.getHour());
    }

    @Test
    void dateAfterMillis_returnsFutureDate() {
        Date now = TenantTime.date();
        Date later = TenantTime.dateAfterMillis(1_000);

        assertTrue(later.after(now));
    }
}
