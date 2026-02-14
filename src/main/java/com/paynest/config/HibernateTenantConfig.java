package com.paynest.config;

import com.paynest.tenant.TenantIdentifierResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class HibernateTenantConfig {

    private final MultiTenantConnectionProviderImpl  connectionProvider;
    private final TenantIdentifierResolver tenantResolver;
    @Bean
    public HibernatePropertiesCustomizer hibernatePropertiesCustomizer() {



        return properties -> {

            log.info("Multi-tenant configuration loaded");

            properties.put(
                    "hibernate.multi_tenant_connection_provider",
                    connectionProvider);

            properties.put(
                    "hibernate.tenant_identifier_resolver",
                    tenantResolver);
        };
    }
}
