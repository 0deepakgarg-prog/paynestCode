package com.paynest.users.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paynest.config.security.JwtService;
import com.paynest.config.service.TenantRegistryService;
import com.paynest.users.dto.request.WalletRestrictionRequest;
import com.paynest.users.dto.response.WalletRestrictionResponse;
import com.paynest.users.service.WalletRestrictionService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.time.LocalDateTime;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WalletRestrictionSecurityE2ETest {

    private static final String TENANT_ID = "tenant-1";
    private static final String TENANT_SCHEMA = "public";
    private static final String ADMIN_TOKEN = "admin-token";
    private static final String SUBSCRIBER_TOKEN = "subscriber-token";

    @LocalServerPort
    private int port;

    @MockBean
    private TenantRegistryService tenantRegistryService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private WalletRestrictionService walletRestrictionService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;

        when(tenantRegistryService.getSchema(TENANT_ID)).thenReturn(TENANT_SCHEMA);
        when(tenantRegistryService.getTimeZone(TENANT_ID)).thenReturn("UTC");

        when(jwtService.isTokenValid(ADMIN_TOKEN)).thenReturn(true);
        when(jwtService.extractAccountId(ADMIN_TOKEN)).thenReturn("ADMIN0001");
        when(jwtService.extractTenant(ADMIN_TOKEN)).thenReturn(TENANT_ID);
        when(jwtService.getClaims(ADMIN_TOKEN)).thenReturn(claims("ADMIN"));

        when(jwtService.isTokenValid(SUBSCRIBER_TOKEN)).thenReturn(true);
        when(jwtService.extractAccountId(SUBSCRIBER_TOKEN)).thenReturn("CUST0001");
        when(jwtService.extractTenant(SUBSCRIBER_TOKEN)).thenReturn(TENANT_ID);
        when(jwtService.getClaims(SUBSCRIBER_TOKEN)).thenReturn(claims("SUBSCRIBER"));

        when(userDetailsService.loadUserByUsername("ADMIN0001")).thenReturn(
                new User("ADMIN0001", "N/A", AuthorityUtils.createAuthorityList("ROLE_ADMIN"))
        );
        when(userDetailsService.loadUserByUsername("CUST0001")).thenReturn(
                new User("CUST0001", "N/A", AuthorityUtils.createAuthorityList("ROLE_SUBSCRIBER"))
        );

        JsonNode selectedServicesRestrictions = selectedServicesRestrictions();
        when(walletRestrictionService.addWalletRestriction(any(WalletRestrictionRequest.class)))
                .thenReturn(new WalletRestrictionResponse(
                        1001L,
                        selectedServicesRestrictions,
                        0L,
                        LocalDateTime.of(2026, 5, 10, 10, 0),
                        "ADMIN0001"
                ));

        JsonNode allServicesRestrictions = allServicesRestrictions();
        when(walletRestrictionService.updateWalletRestriction(eq(1001L), any(WalletRestrictionRequest.class)))
                .thenReturn(new WalletRestrictionResponse(
                        1001L,
                        allServicesRestrictions,
                        1L,
                        LocalDateTime.of(2026, 5, 10, 10, 5),
                        "ADMIN0001"
                ));
    }

    @Test
    void addWalletRestriction_shouldAllowAdminToken() {
        given()
                .contentType(ContentType.JSON)
                .header("X-Tenant-Id", TENANT_ID)
                .header("Authorization", "Bearer " + ADMIN_TOKEN)
                .body("""
                        {
                          "walletId": 1001,
                          "restrictions": {
                            "sendBlock": {
                              "blocked": true,
                              "mode": "SELECTED_SERVICES",
                              "services": ["P2P", "BANK_TRANSFER"]
                            },
                            "receiveBlock": {
                              "blocked": false
                            }
                          }
                        }
                        """)
                .when()
                .post("/api/v1/wallet/restrictions")
                .then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("walletRestriction.walletId", equalTo(1001))
                .body("walletRestriction.restrictions.sendBlock.mode", equalTo("SELECTED_SERVICES"))
                .body("walletRestriction.updatedBy", equalTo("ADMIN0001"));

        verify(walletRestrictionService).addWalletRestriction(ArgumentMatchers.any(WalletRestrictionRequest.class));
    }

    @Test
    void updateWalletRestriction_shouldAllowAdminToken() {
        given()
                .contentType(ContentType.JSON)
                .header("X-Tenant-Id", TENANT_ID)
                .header("Authorization", "Bearer " + ADMIN_TOKEN)
                .body("""
                        {
                          "restrictions": {
                            "sendBlock": {
                              "blocked": true,
                              "mode": "ALL_SERVICES",
                              "services": []
                            },
                            "receiveBlock": {
                              "blocked": false
                            }
                          }
                        }
                        """)
                .when()
                .put("/api/v1/wallet/restrictions/1001")
                .then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("walletRestriction.walletId", equalTo(1001))
                .body("walletRestriction.restrictions.sendBlock.mode", equalTo("ALL_SERVICES"))
                .body("walletRestriction.version", equalTo(1));

        verify(walletRestrictionService).updateWalletRestriction(eq(1001L), any(WalletRestrictionRequest.class));
    }

    @Test
    void addWalletRestriction_shouldRejectNonAdminToken() {
        given()
                .contentType(ContentType.JSON)
                .header("X-Tenant-Id", TENANT_ID)
                .header("Authorization", "Bearer " + SUBSCRIBER_TOKEN)
                .body("""
                        {
                          "walletId": 1001,
                          "restrictions": {
                            "sendBlock": {
                              "blocked": true,
                              "mode": "ALL_SERVICES",
                              "services": []
                            },
                            "receiveBlock": {
                              "blocked": false
                            }
                          }
                        }
                        """)
                .when()
                .post("/api/v1/wallet/restrictions")
                .then()
                .statusCode(403)
                .body("responseStatus", equalTo("FAILURE"))
                .body("code", equalTo("ACCESS_DENIED"));
    }

    private Claims claims(String scope) {
        Claims claims = new DefaultClaims();
        claims.setSubject("ADMIN".equals(scope) ? "ADMIN0001" : "CUST0001");
        claims.put("tenant", TENANT_ID);
        claims.put("scope", scope);
        claims.put("authType", "PIN");
        return claims;
    }

    private JsonNode selectedServicesRestrictions() throws Exception {
        return objectMapper.readTree("""
                {
                  "sendBlock": {
                    "blocked": true,
                    "mode": "SELECTED_SERVICES",
                    "services": ["P2P", "BANK_TRANSFER"]
                  },
                  "receiveBlock": {
                    "blocked": false
                  }
                }
                """);
    }

    private JsonNode allServicesRestrictions() throws Exception {
        return objectMapper.readTree("""
                {
                  "sendBlock": {
                    "blocked": true,
                    "mode": "ALL_SERVICES",
                    "services": []
                  },
                  "receiveBlock": {
                    "blocked": false
                  }
                }
                """);
    }
}
