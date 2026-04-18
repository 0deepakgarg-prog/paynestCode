package com.paynest.payments.service;

import com.paynest.payments.repository.ServiceCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ServiceCatalogService {

    private final ServiceCatalogRepository serviceCatalogRepository;

    @Transactional(readOnly = true)
    public String resolveServiceName(String serviceCode) {
        if (serviceCode == null || serviceCode.isBlank()) {
            return "Payment Service";
        }

        return serviceCatalogRepository.findFirstByServiceCodeIgnoreCaseAndIsActiveTrue(serviceCode)
                .map(serviceCatalog -> serviceCatalog.getServiceName())
                .filter(serviceName -> !serviceName.isBlank())
                .orElseGet(() -> serviceCode + " Service");
    }
}
