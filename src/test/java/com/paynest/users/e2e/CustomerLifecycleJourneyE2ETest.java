package com.paynest.users.e2e;

import com.paynest.config.security.JwtService;
import com.paynest.config.service.TenantRegistryService;
import com.paynest.payments.dto.BasePaymentResponse;
import com.paynest.payments.enums.TransactionStatus;
import com.paynest.payments.service.StockService;
import com.paynest.payments.service.U2UPaymentService;
import com.paynest.users.dto.request.AuthLoginRequest;
import com.paynest.users.dto.request.ChangePinRequest;
import com.paynest.users.dto.request.RegistrationRequest;
import com.paynest.users.dto.request.RegistrationRequestWithOtp;
import com.paynest.users.dto.response.AuthLoginResponse;
import com.paynest.users.entity.Account;
import com.paynest.users.service.AccountService;
import com.paynest.users.service.AuthService;
import com.paynest.users.service.PinService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.math.BigDecimal;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CustomerLifecycleJourneyE2ETest {

    private static final String TENANT_ID = "tenant-1";
    private static final String TENANT_SCHEMA = "public";
    private static final String CUSTOMER_ACCOUNT_ID = "AC202603200001";
    private static final String CUSTOMER_MOBILE = "9876543210";
    private static final String CUSTOMER_TOKEN = "customer-token";
    private static final String ADMIN_TOKEN = "admin-token";

    @LocalServerPort
    private int port;

    @MockBean
    private TenantRegistryService tenantRegistryService;

    @MockBean
    private AccountService accountService;

    @MockBean
    private PinService pinService;

    @MockBean
    private AuthService authService;

    @MockBean
    private U2UPaymentService u2uPaymentService;

    @MockBean
    private StockService stockService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;

        when(tenantRegistryService.getSchema(TENANT_ID)).thenReturn(TENANT_SCHEMA);

        when(jwtService.isTokenValid(CUSTOMER_TOKEN)).thenReturn(true);
        when(jwtService.extractAccountId(CUSTOMER_TOKEN)).thenReturn(CUSTOMER_ACCOUNT_ID);
        when(jwtService.extractTenant(CUSTOMER_TOKEN)).thenReturn(TENANT_ID);
        when(jwtService.getClaims(CUSTOMER_TOKEN)).thenReturn(claims("CUSTOMER"));

        when(jwtService.isTokenValid(ADMIN_TOKEN)).thenReturn(true);
        when(jwtService.extractAccountId(ADMIN_TOKEN)).thenReturn("ADMIN0001");
        when(jwtService.extractTenant(ADMIN_TOKEN)).thenReturn(TENANT_ID);
        when(jwtService.getClaims(ADMIN_TOKEN)).thenReturn(claims("ADMIN"));

        when(userDetailsService.loadUserByUsername(CUSTOMER_ACCOUNT_ID)).thenReturn(
                new User(CUSTOMER_ACCOUNT_ID, "N/A", AuthorityUtils.NO_AUTHORITIES)
        );
        when(userDetailsService.loadUserByUsername("ADMIN0001")).thenReturn(
                new User("ADMIN0001", "N/A", AuthorityUtils.NO_AUTHORITIES)
        );
    }

    @Test
    void customerJourney_shouldRegisterChangeDefaultPinPayAndDeleteSubscriber() {
        Account registeredCustomer = new Account();
        registeredCustomer.setAccountId(CUSTOMER_ACCOUNT_ID);

        doNothing().when(accountService).generateOtpForRegistration(any(RegistrationRequest.class));
        when(accountService.registerUser(any(RegistrationRequestWithOtp.class))).thenReturn(registeredCustomer);
        doNothing().when(pinService).changePin(any(ChangePinRequest.class), eq(false));

        when(authService.login(any(AuthLoginRequest.class)))
                .thenAnswer(invocation -> {
                    AuthLoginRequest request = invocation.getArgument(0);
                    if ("ADMIN0001".equals(request.getUser().getIdentifierValue())) {
                        return new AuthLoginResponse(
                                "SUCCESS",
                                "Login successful",
                                "ADMIN0001",
                                "Bearer",
                                ADMIN_TOKEN,
                                3600
                        );
                    }
                    return new AuthLoginResponse(
                            "SUCCESS",
                            "Login successful",
                            CUSTOMER_ACCOUNT_ID,
                            "Bearer",
                            CUSTOMER_TOKEN,
                            3600
                    );
                });

        when(u2uPaymentService.processPayment(any())).thenReturn(
                BasePaymentResponse.builder()
                        .responseStatus(TransactionStatus.SUCCESS)
                        .operationType("U2U_TRANSFER")
                        .code("PAYMENT_SUCCESS")
                        .message("U2U Payment Successful")
                        .transactionId("UU202603200001")
                        .amount(BigDecimal.valueOf(25.00))
                        .currency("USD")
                        .traceId("trace-123")
                        .build()
        );

        doNothing().when(accountService).deleteSubscriber(CUSTOMER_ACCOUNT_ID);

        given()
                .contentType(ContentType.JSON)
                .header("X-Tenant-Id", TENANT_ID)
                .body("""
                        {
                          "requestId": "req-otp-001",
                          "user": {
                            "mobileNumber": "%s"
                          }
                        }
                        """.formatted(CUSTOMER_MOBILE))
                .when()
                .post("/api/v1/account/register/selfGenOtp")
                .then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("requestId", equalTo("req-otp-001"))
                .body("message", equalTo("OTP generated successfully"));

        given()
                .contentType(ContentType.JSON)
                .header("X-Tenant-Id", TENANT_ID)
                .body("""
                        {
                          "requestId": "req-register-001",
                          "user": {
                            "mobileNumber": "%s",
                            "otp": "123456"
                          }
                        }
                        """.formatted(CUSTOMER_MOBILE))
                .when()
                .post("/api/v1/account/register/selfWithOtp")
                .then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("accountId", equalTo(CUSTOMER_ACCOUNT_ID));

        given()
                .contentType(ContentType.JSON)
                .header("X-Tenant-Id", TENANT_ID)
                .body("""
                        {
                          "oldPin": "1234",
                          "newPin": "4321",
                          "identifierType": "MOBILE",
                          "identifierValue": "%s"
                        }
                        """.formatted(CUSTOMER_MOBILE))
                .when()
                .post("/api/v1/account/pin/changeDefault")
                .then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("message", equalTo("PIN changed successfully"));

        String customerAccessToken =
                given()
                        .contentType(ContentType.JSON)
                        .header("X-Tenant-Id", TENANT_ID)
                        .body("""
                                {
                                  "requestId": "req-login-customer-001",
                                  "user": {
                                    "identifierType": "MOBILE",
                                    "identifierValue": "%s"
                                  },
                                  "authFactor": {
                                    "authType": "PIN",
                                    "credential": "4321"
                                  }
                                }
                                """.formatted(CUSTOMER_MOBILE))
                        .when()
                        .post("/api/v1/auth/login")
                        .then()
                        .statusCode(200)
                        .body("status", equalTo("SUCCESS"))
                        .body("accountId", equalTo(CUSTOMER_ACCOUNT_ID))
                        .body("accessToken", equalTo(CUSTOMER_TOKEN))
                        .extract()
                        .path("accessToken");

        given()
                .contentType(ContentType.JSON)
                .header("X-Tenant-Id", TENANT_ID)
                .header("Authorization", "Bearer " + customerAccessToken)
                .body("""
                        {
                          "operationType": "U2U_TRANSFER",
                          "initiatedBy": "DEBITOR",
                          "paymentReference": "pay-ref-001",
                          "comments": "customer initiated transfer",
                          "debitor": {
                            "accountType": "CUSTOMER",
                            "identifier": {
                              "type": "MOBILE",
                              "value": "%s"
                            },
                            "authentication": {
                              "type": "PIN",
                              "value": "4321"
                            }
                          },
                          "creditor": {
                            "accountType": "CUSTOMER",
                            "identifier": {
                              "type": "MOBILE",
                              "value": "9999999999"
                            }
                          },
                          "transaction": {
                            "amount": 25.00,
                            "currency": "USD"
                          },
                          "metadata": {
                            "channel": "MOBILE_APP"
                          },
                          "additionalInfo": {
                            "note": "journey test"
                          }
                        }
                        """.formatted(CUSTOMER_MOBILE))
                .when()
                .post("/api/v1/pay/U2U")
                .then()
                .statusCode(200)
                .body("responseStatus", equalTo("SUCCESS"))
                .body("code", equalTo("PAYMENT_SUCCESS"))
                .body("transactionId", notNullValue());

        String adminAccessToken =
                given()
                        .contentType(ContentType.JSON)
                        .header("X-Tenant-Id", TENANT_ID)
                        .body("""
                                {
                                  "requestId": "req-login-admin-001",
                                  "user": {
                                    "identifierType": "ACCOUNT_ID",
                                    "identifierValue": "ADMIN0001"
                                  },
                                  "authFactor": {
                                    "authType": "PASSWORD",
                                    "credential": "admin-secret"
                                  }
                                }
                                """)
                        .when()
                        .post("/api/v1/auth/login")
                        .then()
                        .statusCode(200)
                        .body("status", equalTo("SUCCESS"))
                        .body("accountId", equalTo("ADMIN0001"))
                        .body("accessToken", equalTo(ADMIN_TOKEN))
                        .extract()
                        .path("accessToken");

        given()
                .contentType(ContentType.JSON)
                .header("X-Tenant-Id", TENANT_ID)
                .header("Authorization", "Bearer " + adminAccessToken)
                .when()
                .delete("/api/v1/account/subscriber/{accountId}", CUSTOMER_ACCOUNT_ID)
                .then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("message", equalTo("Subscriber deactivated successfully"));

        InOrder inOrder = Mockito.inOrder(accountService, pinService, authService, u2uPaymentService);
        inOrder.verify(accountService).generateOtpForRegistration(any(RegistrationRequest.class));
        inOrder.verify(accountService).registerUser(any(RegistrationRequestWithOtp.class));
        inOrder.verify(pinService).changePin(any(ChangePinRequest.class), eq(false));
        inOrder.verify(authService).login(any(AuthLoginRequest.class));
        inOrder.verify(u2uPaymentService).processPayment(any());
        inOrder.verify(authService).login(any(AuthLoginRequest.class));
        inOrder.verify(accountService).deleteSubscriber(CUSTOMER_ACCOUNT_ID);
    }

    private Claims claims(String scope) {
        return new DefaultClaims(Map.of("scope", scope, "tenant", TENANT_ID));
    }
}
