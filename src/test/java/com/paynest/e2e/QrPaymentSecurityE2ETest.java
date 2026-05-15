package com.paynest.e2e;

import com.paynest.config.service.TenantRegistryService;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QrPaymentSecurityE2ETest {

    @LocalServerPort
    private int port;

    @MockBean
    private TenantRegistryService tenantRegistryService;

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        when(tenantRegistryService.getSchema("tenant-1")).thenReturn("public");
    }

    @Test
    void qrGenerateShouldReturn401WhenAuthorizationHeaderMissing() {
        String payload = """
                {
                  "qrType": "STATIC",
                  "operationType": "MERCHANTPAY",
                  "creditor": {
                    "identifierType": "LOGINID",
                    "identifierValue": "merchant-login",
                    "accountType": "MERCHANT",
                    "walletType": "MAIN"
                  },
                  "currency": "USD"
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .header("X-Tenant-Id", "tenant-1")
                .body(payload)
                .when()
                .post("/api/v1/qr/generate")
                .then()
                .statusCode(401)
                .body("responseStatus", equalTo("FAILURE"))
                .body("code", equalTo("TOKEN_REQUIRED"));
    }
}
