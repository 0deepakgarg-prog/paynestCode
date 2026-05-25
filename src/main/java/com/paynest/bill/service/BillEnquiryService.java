package com.paynest.bill.service;

import com.paynest.bill.dto.BillEnquiryCamelRequest;
import com.paynest.bill.dto.BillEnquiryRequest;
import com.paynest.config.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class BillEnquiryService {

    private final RestClient.Builder restClientBuilder;

    @Value("${app.integrator.base-url:http://localhost:7788}")
    private String integratorBaseUrl;

    public Object enquire(BillEnquiryRequest request) {
        BillEnquiryCamelRequest camelRequest = new BillEnquiryCamelRequest(
                request.getBillerCode(),
                TenantContext.getTenantId(),
                request.getPartnerData()
        );

        return restClientBuilder
                .baseUrl(integratorBaseUrl)
                .build()
                .post()
                .uri("/bill/enquiry")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(camelRequest)
                .retrieve()
                .body(Object.class);
    }
}
