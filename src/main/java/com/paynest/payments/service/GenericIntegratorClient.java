package com.paynest.payments.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.paynest.payments.dto.GenericIntegratorPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class GenericIntegratorClient {

    private final RestClient.Builder restClientBuilder;

    @Value("${app.integrator.base-url:http://localhost:7788}")
    private String integratorBaseUrl;

    @Value("${app.integrator.generic-services-path:/services/execute/sync}")
    private String genericServicesPath;

    @Value("${app.integrator.generic-services-async-path:/services/execute/async}")
    private String genericServicesAsyncPath;

    public JsonNode send(GenericIntegratorPayload payload) {
        return send(payload, "SYNC");
    }

    public JsonNode send(GenericIntegratorPayload payload, String integratorCallMode) {
        return restClientBuilder
                .baseUrl(integratorBaseUrl)
                .build()
                .post()
                .uri(resolvePath(integratorCallMode))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(JsonNode.class);
    }

    private String resolvePath(String integratorCallMode) {
        if ("ASYNC".equalsIgnoreCase(integratorCallMode)) {
            return genericServicesAsyncPath;
        }
        return genericServicesPath;
    }
}
