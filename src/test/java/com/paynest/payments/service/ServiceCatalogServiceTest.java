package com.paynest.payments.service;

import com.paynest.payments.entity.ServiceCatalog;
import com.paynest.payments.repository.ServiceCatalogRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServiceCatalogServiceTest {

    @Test
    void resolveServiceName_shouldReturnActiveCatalogName() {
        ServiceCatalogRepository repository = mock(ServiceCatalogRepository.class);
        ServiceCatalog catalog = new ServiceCatalog();
        catalog.setServiceCode("U2U");
        catalog.setServiceName("Wallet Transfer");

        when(repository.findFirstByServiceCodeIgnoreCaseAndIsActiveTrue("U2U"))
                .thenReturn(Optional.of(catalog));

        ServiceCatalogService service = new ServiceCatalogService(repository);

        assertEquals("Wallet Transfer", service.resolveServiceName("U2U"));
    }

    @Test
    void resolveServiceName_shouldFallbackWhenCatalogRowIsMissing() {
        ServiceCatalogRepository repository = mock(ServiceCatalogRepository.class);
        when(repository.findFirstByServiceCodeIgnoreCaseAndIsActiveTrue("UNKNOWN"))
                .thenReturn(Optional.empty());

        ServiceCatalogService service = new ServiceCatalogService(repository);

        assertEquals("UNKNOWN Service", service.resolveServiceName("UNKNOWN"));
    }
}
