package com.paynest.tenant;

import jakarta.annotation.PostConstruct;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
@Component
@Slf4j
public class TenantIdentifierResolver
        implements CurrentTenantIdentifierResolver {

    private static final String DEFAULT_TENANT = "public";


    @Override
    public String resolveCurrentTenantIdentifier() {
        String tenantId = TenantContext.getTenant();

        if (tenantId == null)
            return DEFAULT_TENANT;

        log.info("Hibernate resolving tenant: {}", tenantId);

        return tenantId;
    }

    @PostConstruct
    public void init() {
        log.info("TenantIdentifierResolver loaded");
    }


    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}

