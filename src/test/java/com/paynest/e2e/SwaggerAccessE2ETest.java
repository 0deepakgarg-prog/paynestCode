package com.paynest.e2e;

import com.paynest.config.service.TenantRegistryService;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SwaggerAccessE2ETest {

    @LocalServerPort
    private int port;

    @MockBean
    private TenantRegistryService tenantRegistryService;

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
    }

    @Test
    void swaggerUiShouldBeAccessibleWithoutTenantHeaderOrToken() {
        given()
                .when()
                .get("/swagger-ui/index.html")
                .then()
                .statusCode(200)
                .body(containsString("Swagger UI"));
    }

    @Test
    void openApiDocsShouldBeAccessibleWithoutTenantHeaderOrToken() {
        given()
                .when()
                .get("/v3/api-docs")
                .then()
                .statusCode(200)
                .body("openapi", notNullValue());
    }
}
