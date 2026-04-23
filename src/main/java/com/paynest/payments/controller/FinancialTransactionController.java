package com.paynest.payments.controller;

import com.paynest.config.dto.response.ApiResponse;
import com.paynest.config.tenant.TraceContext;
import com.paynest.payments.dto.StockApprovalRequest;
import com.paynest.payments.dto.StockInitiateRequest;
import com.paynest.payments.dto.StockReimbursementInitiateRequest;
import com.paynest.payments.dto.U2UPaymentRequest;
import com.paynest.payments.dto.BasePaymentResponse;
import com.paynest.pricing.dto.response.PricingComputationResponse;
import com.paynest.pricing.service.PricingService;
import com.paynest.payments.service.StockService;
import com.paynest.payments.service.U2UPaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pay")
@RequiredArgsConstructor
@Slf4j
public class FinancialTransactionController {
    private final U2UPaymentService u2uPaymentService;
    private final StockService stockService;
    private final PricingService pricingService;

    @PostMapping("/U2U")
    public ResponseEntity<BasePaymentResponse> transferMoney(
            @Valid @RequestBody U2UPaymentRequest request) {
        log.info(
                "Financial transaction request received. endpoint=/api/v1/pay/U2U, traceId={}, operationType={}, currency={}, amount={}, debitorIdentifier={}, creditorIdentifier={}",
                TraceContext.getTraceId(),
                request != null ? request.getOperationType() : null,
                request != null && request.getTransaction() != null ? request.getTransaction().getCurrency() : null,
                request != null && request.getTransaction() != null ? request.getTransaction().getAmount() : null,
                extractPartyIdentifier(request != null ? request.getDebitor() : null),
                extractPartyIdentifier(request != null ? request.getCreditor() : null)
        );

        try {
            BasePaymentResponse response = u2uPaymentService.processPayment(request);

            log.info(
                    "Financial transaction completed. endpoint=/api/v1/pay/U2U, traceId={}, responseStatus={}, code={}, transactionId={}",
                    TraceContext.getTraceId(),
                    response != null ? response.getResponseStatus() : null,
                    response != null ? response.getCode() : null,
                    response != null ? response.getTransactionId() : null
            );

            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            log.error(
                    "Financial transaction failed. endpoint=/api/v1/pay/U2U, traceId={}, operationType={}, currency={}, amount={}, debitorIdentifier={}, creditorIdentifier={}, error={}",
                    TraceContext.getTraceId(),
                    request != null ? request.getOperationType() : null,
                    request != null && request.getTransaction() != null ? request.getTransaction().getCurrency() : null,
                    request != null && request.getTransaction() != null ? request.getTransaction().getAmount() : null,
                    extractPartyIdentifier(request != null ? request.getDebitor() : null),
                    extractPartyIdentifier(request != null ? request.getCreditor() : null),
                    ex.getMessage(),
                    ex
            );
            throw ex;
        }
    }

    @PostMapping("/calculatePricing")
    public ResponseEntity<ApiResponse> calculatePricing(
            @Valid @RequestBody U2UPaymentRequest request) {

        try {
            PricingComputationResponse response = pricingService.calculatePricingAmounts(request);

            return ResponseEntity.ok(
                    new ApiResponse("SUCCESS", "Pricing calculated successfully", "pricingAmounts", response)
            );
        } catch (Exception ex) {
            log.error(
                    "Transaction pricing calculation failed. endpoint=/api/v1/pay/calculatePricing, traceId={}, operationType={}, currency={}, amount={}, debitorIdentifier={}, creditorIdentifier={}, error={}",
                    TraceContext.getTraceId(),
                    request != null ? request.getOperationType() : null,
                    request != null && request.getTransaction() != null ? request.getTransaction().getCurrency() : null,
                    request != null && request.getTransaction() != null ? request.getTransaction().getAmount() : null,
                    extractPartyIdentifier(request != null ? request.getDebitor() : null),
                    extractPartyIdentifier(request != null ? request.getCreditor() : null),
                    ex.getMessage(),
                    ex
            );
            throw ex;
        }
    }

    @PostMapping("/stockInitiate")
    public ResponseEntity<BasePaymentResponse> initiateStock(
            @Valid @RequestBody StockInitiateRequest request) {

        BasePaymentResponse response = stockService.initiateStock(request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/stockStatusUpdate")
    public ResponseEntity<BasePaymentResponse> updateStockStatus(
            @Valid @RequestBody StockApprovalRequest request) {

        BasePaymentResponse response = stockService.updateStockTransactionStatus(request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/stockReimbursementInitiate")
    public ResponseEntity<BasePaymentResponse> initiateStockReimbursement(
            @Valid @RequestBody StockReimbursementInitiateRequest request) {

        BasePaymentResponse response = stockService.initiateStockReimbursement(request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/stockReimbursementStatusUpdate")
    public ResponseEntity<BasePaymentResponse> updateStockReimbursementStatus(
            @Valid @RequestBody StockApprovalRequest request) {

        BasePaymentResponse response = stockService.updateStockReimbursementTransactionStatus(request);

        return ResponseEntity.ok(response);
    }

    private String extractPartyIdentifier(com.paynest.payments.dto.Party party) {
        if (party == null || party.getIdentifier() == null) {
            return null;
        }
        return party.getIdentifier().getType() + ":" + party.getIdentifier().getValue();
    }

}

