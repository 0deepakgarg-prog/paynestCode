package com.paynest.payments.repository;

import com.paynest.payments.entity.ServiceCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ServiceCatalogRepository extends JpaRepository<ServiceCatalog, String> {

    Optional<ServiceCatalog> findFirstByServiceCodeIgnoreCaseAndIsActiveTrue(String serviceCode);
}
