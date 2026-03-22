package com.paynest.e2e;

import com.paynest.security.JwtService;
import com.paynest.service.TenantRegistryService;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class U2UPaymentSecurityE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private TenantRegistryService tenantRegistryService;

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        when(tenantRegistryService.getSchema("tenant-1")).thenReturn("public");
        when(tenantRegistryService.getSchema("tenant-2")).thenReturn("tenant_2_schema");
    }

    @Test
    void u2uShouldReturn401WhenAuthorizationHeaderMissing() {
        String payload = """
                {
                  "requestGateway": "MOBILE",
                  "preferredLang": "en",
                  "initiatedBy": "DEBITOR",
                  "debitor": {
                    "accountType": "SUBSCRIBER",
                    "walletType": "MAIN",
                    "identifier": {
                      "type": "MOBILE",
                      "value": "9999999999"
                    },
                    "authentication": {
                      "type": "PIN",
                      "value": "1234"
                    }
                  },
                  "creditor": {
                    "accountType": "SUBSCRIBER",
                    "walletType": "MAIN",
                    "identifier": {
                      "type": "MOBILE",
                      "value": "8888888888"
                    }
                  },
                  "transaction": {
                    "amount": 10.50,
                    "currency": "USD"
                  }
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .header("X-Tenant-Id", "tenant-1")
                .body(payload)
                .when()
                .post("/api/v1/pay/U2U")
                .then()
                .statusCode(401)
                .body("responseStatus", equalTo("FAILURE"))
                .body("operationType", equalTo("U2U"))
                .body("code", equalTo("TOKEN_REQUIRED"));
    }

    @Test
    void u2uShouldReturn401WhenJwtTenantDoesNotMatchTenantHeader() {
        String payload = """
                {
                  "requestGateway": "MOBILE",
                  "preferredLang": "en",
                  "initiatedBy": "DEBITOR",
                  "debitor": {
                    "accountType": "SUBSCRIBER",
                    "walletType": "MAIN",
                    "identifier": {
                      "type": "MOBILE",
                      "value": "9999999999"
                    },
                    "authentication": {
                      "type": "PIN",
                      "value": "1234"
                    }
                  },
                  "creditor": {
                    "accountType": "SUBSCRIBER",
                    "walletType": "MAIN",
                    "identifier": {
                      "type": "MOBILE",
                      "value": "8888888888"
                    }
                  },
                  "transaction": {
                    "amount": 10.50,
                    "currency": "USD"
                  }
                }
                """;

        String token = jwtService.generateToken("acc-1", "PIN", "tenant-2", "SUBSCRIBER");

        given()
                .contentType(ContentType.JSON)
                .header("X-Tenant-Id", "tenant-1")
                .header("Authorization", "Bearer " + token)
                .body(payload)
                .when()
                .post("/api/v1/pay/U2U")
                .then()
                .statusCode(401)
                .body("responseStatus", equalTo("FAILURE"))
                .body("operationType", equalTo("U2U"))
                .body("code", equalTo("INVALID_TOKEN"));
    }
}
