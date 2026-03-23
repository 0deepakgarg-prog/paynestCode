package com.paynest.users.e2e;

import com.paynest.common.ErrorCodes;
import com.paynest.users.dto.request.RegisterUserRequest;
import com.paynest.users.entity.Account;
import com.paynest.exception.ApplicationException;
import com.paynest.users.service.AccountService;
import com.paynest.config.service.TenantRegistryService;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccountRegistrationE2ETest {

    @LocalServerPort
    private int port;

    @MockBean
    private TenantRegistryService tenantRegistryService;

    @MockBean
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        when(tenantRegistryService.getSchema("tenant-1")).thenReturn("public");
    }

    @Test
    void registerUser_shouldReturn200_whenRequestIsValid() {
        Account account = new Account();
        account.setAccountId("AC202602240001");
        when(accountService.registerAccountByRole(ArgumentMatchers.any(RegisterUserRequest.class)))
                .thenReturn(account);

        String payload = """
                {
                  "tenantId": "tenant-1",
                  "requestId": "req-001",
                  "user": {
                    "mobileNumber": "9876543210",
                    "accountType": "MERCHANT",
                    "firstName": "John",
                    "lastName": "Doe",
                    "email": "john.doe@test.com",
                    "address": "Address 1",
                    "gender": "MALE",
                    "dateOfBirth": "1990-01-01",
                    "preferredLang": "en",
                    "nationality": "US",
                    "ssn": "123456789",
                    "remarks": "new user",
                    "loginId": "johnlogin01"
                  }
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .header("X-Tenant-Id", "tenant-1")
                .body(payload)
                .when()
                .post("/api/v1/account/registerUser")
                .then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("accountId", equalTo("AC202602240001"));
    }

    @Test
    void registerUser_shouldReturn400_whenTenantHeaderMissing() {
        String payload = """
                {
                  "tenantId": "tenant-1",
                  "requestId": "req-002",
                  "user": {
                    "mobileNumber": "9876543210",
                    "accountType": "AGENT",
                    "firstName": "Jane",
                    "lastName": "Doe",
                    "loginId": "janelogin01"
                  }
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/api/v1/account/registerUser")
                .then()
                .statusCode(400);
    }

    @Test
    void registerUser_shouldReturn400_whenServiceThrowsApplicationException() {
        when(accountService.registerAccountByRole(ArgumentMatchers.any(RegisterUserRequest.class)))
                .thenThrow(new ApplicationException(ErrorCodes.USER_EXISTS, "User already exists"));

        String payload = """
                {
                  "tenantId": "tenant-1",
                  "requestId": "req-003",
                  "user": {
                    "mobileNumber": "9999999999",
                    "accountType": "BILLER",
                    "firstName": "Sam",
                    "lastName": "Smith",
                    "loginId": "samlogin01"
                  }
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .header("X-Tenant-Id", "tenant-1")
                .body(payload)
                .when()
                .post("/api/v1/account/registerUser")
                .then()
                .statusCode(400)
                .body("errorCode", equalTo(ErrorCodes.USER_EXISTS))
                .body("success", equalTo(false))
                .body("timestamp", notNullValue());
    }
}

