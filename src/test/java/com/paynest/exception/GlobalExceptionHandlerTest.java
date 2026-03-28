package com.paynest.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.paynest.payments.dto.BasePaymentResponse;
import com.paynest.payments.dto.Party;
import com.paynest.payments.dto.U2UPaymentRequest;
import com.paynest.enums.WalletType;
import com.paynest.service.ErrorCatalogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private ErrorCatalogService errorCatalogService;

    @Test
    void handleApplicationException_shouldIncludeTransactionIdForPaymentRequests() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(errorCatalogService);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/pay/U2U");
        ApplicationException exception =
                new ApplicationException("INSUFFICIENT_BALANCE", "Insufficient balance", "UU123");
        when(errorCatalogService.resolve(
                eq("INSUFFICIENT_BALANCE"),
                anyMap(),
                eq("Insufficient balance"),
                eq(HttpStatus.BAD_REQUEST)
        )).thenReturn(new ErrorCatalogService.ResolvedError("Insufficient balance", HttpStatus.BAD_REQUEST));

        ResponseEntity<?> response = handler.handleApplicationException(exception, request);

        assertEquals(400, response.getStatusCode().value());
        BasePaymentResponse body = assertInstanceOf(BasePaymentResponse.class, response.getBody());
        assertEquals("INSUFFICIENT_BALANCE", body.getCode());
        assertEquals("Insufficient balance", body.getMessage());
        assertEquals("UU123", body.getTransactionId());
        assertEquals("U2U", body.getOperationType());
    }

    @Test
    void handleHttpMessageNotReadable_shouldReturnMeaningfulMessageForInvalidEnumValue() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(errorCatalogService);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/pay/U2U");
        InvalidFormatException invalidFormatException =
                InvalidFormatException.from(null, "Invalid enum", "INVALID", WalletType.class);
        invalidFormatException.prependPath(new Party(), "walletType");
        invalidFormatException.prependPath(new U2UPaymentRequest(), "debitor");

        HttpMessageNotReadableException exception = new HttpMessageNotReadableException(
                "Unreadable request body",
                invalidFormatException,
                new MockHttpInputMessage(new byte[0])
        );
        when(errorCatalogService.resolve(
                eq("INVALID_ENUM_VALUE"),
                anyMap(),
                eq(null),
                eq(HttpStatus.BAD_REQUEST)
        )).thenReturn(new ErrorCatalogService.ResolvedError(
                "Invalid value 'INVALID' for field 'debitor.walletType'. Allowed values: MAIN, BONUS, SALARY",
                HttpStatus.BAD_REQUEST
        ));

        ResponseEntity<?> response = handler.handleHttpMessageNotReadable(exception, request);

        assertEquals(400, response.getStatusCode().value());
        BasePaymentResponse body = assertInstanceOf(BasePaymentResponse.class, response.getBody());
        assertEquals("INVALID_ENUM_VALUE", body.getCode());
        assertTrue(body.getMessage().contains("debitor.walletType"));
        assertTrue(body.getMessage().contains("MAIN"));
    }
}
