package com.paynest.payments.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paynest.common.Constants;
import com.paynest.exception.ApplicationException;
import com.paynest.payments.dto.GenericIntegratorPayload;
import com.paynest.payments.dto.SettleTransactionRequest;
import com.paynest.payments.entity.ThirdPartyResponse;
import com.paynest.payments.entity.Transactions;
import com.paynest.payments.repository.ThirdPartyResponseRepository;
import com.paynest.payments.repository.TransactionsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillPayIntegratorSettlementService {

    private static final String INTEGRATOR_NAME = "INTEGRATOR";

    private final GenericIntegratorClient genericIntegratorClient;
    private final TransactionsRepository transactionsRepository;
    private final ThirdPartyResponseRepository thirdPartyResponseRepository;
    private final TransactionSettlementService transactionSettlementService;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    public void callIntegratorAndSettle(
            GenericIntegratorPayload payload,
            boolean confirmationRequired,
            String integratorCallMode
    ) {
        String transactionId = payload.getTransactionId();
        recordIntegratorRequest(payload);
        try {
            JsonNode integratorResponse = genericIntegratorClient.send(payload, integratorCallMode);

            if (isFailureResponse(integratorResponse)) {
                updateIntegratorResponse(transactionId, "FAILED", integratorResponse, "Integrator returned failure");
                settleFailureSafely(transactionId, "Integrator returned failure");
                throw integratorFailure("Integrator returned failure");
            }

            updateIntegratorResponse(transactionId, "SUCCESS", integratorResponse, null);
            if (confirmationRequired) {
                log.info(
                        "Bill payment integrator call succeeded for confirmation-required service. transactionId={} remainsStatus={}",
                        transactionId,
                        Constants.TRANSACTION_AMBIGUOUS
                );
                return;
            }

            settleSuccessSafely(transactionId);
        } catch (ApplicationException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            if (isTimeout(ex)) {
                log.warn(
                        "Bill payment integrator call timed out. transactionId={} statusUnchanged=true error={}",
                        transactionId,
                        ex.getMessage()
                );
                updateIntegratorResponse(transactionId, "TIMEOUT", null, ex.getMessage());
                throw integratorFailure("Integrator call timed out");
            }
            updateIntegratorResponse(transactionId, "FAILED", null, ex.getMessage());
            settleFailureSafely(transactionId, "Integrator call failed");
            throw integratorFailure("Integrator call failed");
        } catch (RestClientResponseException ex) {
            JsonNode errorBody = parseJson(ex.getResponseBodyAsString());
            updateIntegratorResponse(transactionId, "FAILED", errorBody, ex.getMessage());
            settleFailureSafely(transactionId, "Integrator returned HTTP " + ex.getStatusCode().value());
            throw integratorFailure("Integrator returned HTTP " + ex.getStatusCode().value());
        } catch (Exception ex) {
            updateIntegratorResponse(transactionId, "FAILED", null, ex.getMessage());
            settleFailureSafely(transactionId, "Integrator call failed");
            throw integratorFailure("Integrator call failed");
        }
    }

    private void settleSuccessSafely(String transactionId) {
        try {
            settleSuccess(transactionId);
        } catch (RuntimeException ex) {
            log.error("Unable to settle transaction as success after integrator success. transactionId={}", transactionId, ex);
        }
    }

    private void settleFailureSafely(String transactionId, String reason) {
        try {
            settleFailure(transactionId, reason);
        } catch (RuntimeException ex) {
            log.error("Unable to settle transaction as failure after integrator failure. transactionId={}", transactionId, ex);
        }
    }

    public void settleSuccess(String transactionId) {
        transactionSettlementService.settleTransaction(buildSettlementRequest(
                transactionId,
                true,
                "Integrator confirmed transaction"
        ));
    }

    public void settleFailure(String transactionId, String reason) {
        transactionSettlementService.settleTransaction(buildSettlementRequest(
                transactionId,
                false,
                reason
        ));
    }

    public void recordIntegratorRequest(GenericIntegratorPayload payload) {
        executeInNewTransaction(() -> {
            recordIntegratorRequestInTransaction(payload);
            return null;
        });
    }

    private void recordIntegratorRequestInTransaction(GenericIntegratorPayload payload) {
        ThirdPartyResponse record = thirdPartyResponseRepository.findByTransactionId(payload.getTransactionId())
                .orElseGet(ThirdPartyResponse::new);
        record.setTransactionId(payload.getTransactionId());
        record.setServiceCode(payload.getServiceCode());
        record.setIntegratorName(INTEGRATOR_NAME);
        if (record.getRequestBody() == null || record.getRequestBody().isNull()) {
            record.setRequestBody(objectMapper.valueToTree(payload));
        }
        if (record.getStatus() == null || record.getStatus().isBlank()) {
            record.setStatus("PENDING");
        }
        thirdPartyResponseRepository.save(record);
    }

    public void updateIntegratorResponse(
            String transactionId,
            String status,
            JsonNode responseBody,
            String errorMessage
    ) {
        executeInNewTransaction(() -> {
            updateIntegratorResponseInTransaction(transactionId, status, responseBody, errorMessage);
            return null;
        });
    }

    private void updateIntegratorResponseInTransaction(
            String transactionId,
            String status,
            JsonNode responseBody,
            String errorMessage
    ) {
        ThirdPartyResponse record = thirdPartyResponseRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new IllegalStateException("Third party response record not found: " + transactionId));
        record.setStatus(status);
        record.setResponseBody(responseBody);
        record.setErrorMessage(errorMessage);
        thirdPartyResponseRepository.save(record);
    }

    private <T> T executeInNewTransaction(Supplier<T> operation) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transactionTemplate.execute(status -> operation.get());
    }

    private Transactions getTransaction(String transactionId) {
        Transactions transaction = transactionsRepository.findByTransactionId(transactionId);
        if (transaction == null) {
            throw new IllegalStateException("Transaction not found: " + transactionId);
        }
        return transaction;
    }

    private SettleTransactionRequest buildSettlementRequest(
            String transactionId,
            boolean settlementStatus,
            String comments
    ) {
        Transactions transaction = getTransaction(transactionId);
        SettleTransactionRequest request = new SettleTransactionRequest();
        request.setTraceId(transaction.getTraceId());
        request.setSettlementStatus(settlementStatus);
        request.setComments(comments);
        return request;
    }

    private boolean isFailureResponse(JsonNode response) {
        if (response == null || response.isNull()) {
            return false;
        }
        return isFailureValue(response, "status")
                || isFailureValue(response, "responseStatus")
                || isFailureValue(response, "result")
                || isFailureValue(response, "code");
    }

    private boolean isFailureValue(JsonNode response, String fieldName) {
        JsonNode value = response.get(fieldName);
        if (value == null || value.isNull()) {
            return false;
        }
        String normalized = value.asText("").trim().toUpperCase();
        return "FAILURE".equals(normalized)
                || "FAILED".equals(normalized)
                || "ERROR".equals(normalized)
                || "REJECTED".equals(normalized);
    }

    private boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return true;
    }

    private JsonNode parseJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception ex) {
            return objectMapper.getNodeFactory().textNode(value);
        }
    }

    private ApplicationException integratorFailure(String message) {
        return new ApplicationException("INTEGRATOR_FAILURE", message);
    }

}
