package com.paynest.payments.controller;

import com.paynest.common.Constants;
import com.paynest.config.dto.response.ApiResponse;
import com.paynest.payments.dto.GenericServiceExecutionRequest;
import com.paynest.payments.dto.GenericServiceExecutionResponse;
import com.paynest.payments.service.GenericServiceExecutionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/services")
@RequiredArgsConstructor
public class GenericServiceExecutionController {

    private final GenericServiceExecutionService genericServiceExecutionService;

    @PostMapping({"/financial/execute", "/generic/financial"})
    public ResponseEntity<ApiResponse> executeFinancial(
            @Valid @RequestBody GenericServiceExecutionRequest request
    ) {
        GenericServiceExecutionResponse response = genericServiceExecutionService.executeFinancial(request);
        return ResponseEntity.ok(new ApiResponse(
                "SUCCESS",
                buildFinancialMessage(response),
                "serviceExecution",
                response
        ));
    }

    @PostMapping({"/non-financial/execute", "/generic/non-financial"})
    public ResponseEntity<ApiResponse> executeNonFinancial(
            @Valid @RequestBody GenericServiceExecutionRequest request
    ) {
        GenericServiceExecutionResponse response = genericServiceExecutionService.executeNonFinancial(request);
        return ResponseEntity.ok(new ApiResponse(
                "SUCCESS",
                buildNonFinancialMessage(response),
                "serviceExecution",
                response
        ));
    }

    private String buildFinancialMessage(GenericServiceExecutionResponse response) {
        String serviceName = response != null && response.getServiceName() != null
                && !response.getServiceName().isBlank()
                ? response.getServiceName()
                : "Financial";

        if (response != null && Constants.TRANSACTION_AMBIGUOUS.equals(response.getStatus())) {
            return serviceName + " service request has been accepted and is awaiting confirmation.";
        }
        if (response != null && Constants.TRANSACTION_INITIATED.equals(response.getStatus())) {
            return serviceName + " service has been initiated successfully.";
        }
        return serviceName + " service completed successfully.";
    }

    private String buildNonFinancialMessage(GenericServiceExecutionResponse response) {
        String serviceName = response != null && response.getServiceName() != null
                && !response.getServiceName().isBlank()
                ? response.getServiceName()
                : "Non-financial";

        return serviceName + " service completed successfully.";
    }
}
