package com.paynest.config.repository;


import com.paynest.config.entity.TenantRegistry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantRegistryRepository
        extends JpaRepository<TenantRegistry, String> {

    Optional<TenantRegistry> findByTenantIdAndStatus(
            String tenantId,
            String status
    );
}

