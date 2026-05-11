package com.paynest.e2e;

import com.paynest.config.service.TenantRegistryService;
import com.paynest.config.security.JwtService;
import com.paynest.common.ErrorCodes;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Slf4j
class SelfRegistrationRealApiE2ETest {

    private static final String TENANT_ID = "e2etest";
    private static final String TENANT_SCHEMA = "tenant_e2etest";
    private static final String DEFAULT_ADMIN_ACCOUNT_ID = "ADMIN0000000001";
    private static final String DEFAULT_ADMIN_LOGIN_ID = "superadmin";
    private static final String DEFAULT_ADMIN_PASSWORD = "Admin@123";
    private static final String NETWORK_ADMIN_LOGIN_PREFIX = "networkadmin";
    private static final String NETWORK_ADMIN_DEFAULT_PASSWORD = "PayNest@123";
    private static final String NETWORK_ADMIN_UPDATED_PASSWORD = "Network@123";
    private static final String BUSINESS_USER_DEFAULT_PASSWORD = "PayNest@123";
    private static final String BUSINESS_USER_UPDATED_PASSWORD = "Business@123";
    private static final int BULK_SUBSCRIBER_COUNT = 10;
    private static final int BULK_BUSINESS_USER_COUNT_PER_TYPE = 5;
    private static final BigDecimal AGENT_O2C_AMOUNT = new BigDecimal("2500.00");
    private static final BigDecimal MERCHANT_O2C_AMOUNT = new BigDecimal("175.00");
    private static final BigDecimal BILLER_O2C_AMOUNT = new BigDecimal("200.00");
    private static final BigDecimal[] SUBSCRIBER_CASH_IN_AMOUNTS = {
            new BigDecimal("150.00"),
            new BigDecimal("150.00"),
            new BigDecimal("150.00"),
            new BigDecimal("150.00"),
            new BigDecimal("150.00"),
            new BigDecimal("150.00"),
            new BigDecimal("150.00"),
            new BigDecimal("150.00"),
            new BigDecimal("150.00"),
            new BigDecimal("150.00")
    };
    private static final String DEFAULT_PIN = "0000";
    private static final String UPDATED_PIN = "2468";
    private static final String UPDATED_FIRST_NAME = "Ethan";
    private static final String UPDATED_LAST_NAME = "Tester";
    private static final String UPDATED_EMAIL = "ethan.tester.e2e@example.com";
    private static final String UPDATED_ADDRESS = "100 PayNest E2E Street";
    private static final String UPDATED_GENDER = "MALE";
    private static final String UPDATED_NATIONALITY = "USA";
    private static final String UPDATED_SSN = "999-88-7777";
    private static final String UPDATED_DOB = "1990-04-15";
    private static final String UPDATED_PREFERRED_LANG = "en";
    private static final String UPDATED_ATTR1 = "e2e-attr-1";
    private static final String UPDATED_ATTR2 = "e2e-attr-2";
    private static final String UPDATED_ATTR3 = "e2e-attr-3";
    private static final String UPDATED_ATTR4 = "e2e-attr-4";
    private static final String UPDATED_ATTR5 = "e2e-attr-5";
    private static final String UPDATED_ATTR6 = "e2e-attr-6";
    private static final String UPDATED_ATTR7 = "e2e-attr-7";
    private static final String UPDATED_ATTR8 = "e2e-attr-8";
    private static final String UPDATED_ATTR9 = "e2e-attr-9";
    private static final String UPDATED_ATTR10 = "e2e-attr-10";
    private static final String KYC_TYPE = "PASSPORT";
    private static final String KYC_VALUE = "PNE2E123456";
    private static final String KYC_ISSUE_DATE = "2020-01-15";
    private static final String KYC_EXPIRY_DATE = "2030-01-15";
    private static final String KYC_IMAGE_URL = "https://example.com/e2e/passport.png";

    static {
        //runDatabaseBootstrap();
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TenantRegistryService tenantRegistryService;

    @Autowired
    private JwtService jwtService;

    private String mobileNumber;
    private BigDecimal seededAvailableBalanceOffset = BigDecimal.ZERO;

    private record BusinessUser(String accountId, String accountType, String mobile, String accessToken,
                                String password) {
    }

    private record SubscriberUser(String accountId, String mobile, String accessToken, String pin) {
    }

    private record U2UParticipant(
            String accountId,
            String accountType,
            String mobile,
            String accessToken,
            String authType,
            String authValue
    ) {
    }

    private static void runDatabaseBootstrap() {
        try {
            Path projectRoot = Path.of(System.getProperty("user.dir"));
            Path scriptPath = projectRoot.resolve("scripts").resolve("paynest-db.ps1");
            log.info("E2E bootstrap starting: script={} tenantId={} tenantSchema={}",
                    scriptPath,
                    TENANT_ID,
                    TENANT_SCHEMA
            );
            Process process = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-File",
                    scriptPath.toString(),
                    "-TenantId",
                    TENANT_ID,
                    "-TenantName",
                    "PayNest E2E Test Tenant",
                    "-TenantSchema",
                    TENANT_SCHEMA
            )
                    .directory(projectRoot.toFile())
                    .redirectErrorStream(true)
                    .start();

            boolean completed = process.waitFor(180, TimeUnit.SECONDS);
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            log.info("E2E bootstrap completed={} exitCode={} output={}",
                    completed,
                    completed ? process.exitValue() : "not-available",
                    output
            );

            if (!completed) {
                process.destroyForcibly();
                throw new IllegalStateException("Database bootstrap timed out. Output: " + output);
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("Database bootstrap failed. Output: " + output);
            }
        } catch (Exception ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;

        seededAvailableBalanceOffset = BigDecimal.ZERO;
        mobileNumber = "700" + String.valueOf(System.currentTimeMillis()).substring(5);
        log.info("E2E setup starting: baseUri={} port={} tenantId={} tenantSchema={} mobileNumber={}",
                RestAssured.baseURI,
                RestAssured.port,
                TENANT_ID,
                TENANT_SCHEMA,
                mobileNumber
        );
        ensureTenant();
        ensureEnumeration("SYSTEM_CONFIG", "TESTING_MODE", "true", "Enable deterministic test credentials");
        ensureEnumeration("SYSTEM_CONFIG", "INTRAWALLET_BONUS_TO_MAIN_PERCENTAGE", "25", "Bonus to main intra-wallet percentage");
        ensureEnumeration("CURRENCY", "USD", "USD", "US Dollar");
        ensureEnumeration("CURRENCY", "INR", "INR", "Indian Rupee");
        ensureEnumeration("CURRENCY", "EUR", "EUR", "Euro");
        ensureEnumeration("WALLET_TYPE", "MAIN", "MAIN", "Main wallet");
        ensureSubscriberRole();
        //  cleanupMobileNumber(mobileNumber);
        log.info("E2E setup completed for mobileNumber={}", mobileNumber);
    }

    @AfterEach
    void tearDown() {
      /*  log.info("E2E teardown starting for mobileNumber={}", mobileNumber);
        if (mobileNumber != null) {
         //   cleanupMobileNumber(mobileNumber);
        }
        ensureEnumeration("SYSTEM_CONFIG", "TESTING_MODE", "false", "Enable deterministic test credentials");
        log.info("E2E teardown completed for mobileNumber={}", mobileNumber);
        */
    }


    @Test
    @Order(1)
    void selfRegistrationFlow_shouldGenerateOtpRegisterFetchDetailsChangePinAndLogin() {
        log.info("E2E self-registration flow starting for mobileNumber={}", mobileNumber);

        String generateOtpRequest = """
                {
                  "requestId": "req-e2e-otp",
                  "user": {
                    "mobileNumber": "%s"
                  }
                }
                """.formatted(mobileNumber);
        Response generateOtpResponse = postJson(
                "1 - generate self-registration OTP",
                "/api/v1/account/register/selfGenOtp",
                generateOtpRequest
        );
        generateOtpResponse.then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("requestId", equalTo("req-e2e-otp"))
                .body("message", equalTo("OTP generated successfully"));

        String generatedOtp = readLatestCreatedRegistrationOtp(mobileNumber);
        String registerRequest = """
                {
                  "requestId": "req-e2e-register",
                  "user": {
                    "mobileNumber": "%s",
                    "otp": "%s"
                  }
                }
                """.formatted(mobileNumber, generatedOtp);
        Response registerResponse = postJson(
                "2 - register with OTP",
                "/api/v1/account/register/selfWithOtp",
                registerRequest
        );
        String accountId = registerResponse.then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("requestId", equalTo("req-e2e-register"))
                .body("message", equalTo("User registered successfully"))
                .body("accountId", notNullValue())
                .extract()
                .path("accountId");
        log.info("E2E step 2 extracted accountId={}", accountId);

        String changePinRequest = """
                {
                  "oldPin": "%s",
                  "newPin": "%s",
                  "identifierType": "MOBILE",
                  "identifierValue": "%s"
                }
                """.formatted(DEFAULT_PIN, UPDATED_PIN, mobileNumber);
        Response changePinResponse = postJson(
                "3 - change default PIN",
                "/api/v1/account/pin/changeDefault",
                changePinRequest
        );
        changePinResponse.then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("message", equalTo("PIN changed successfully"));

        String loginRequest = """
                {
                  "requestId": "req-e2e-login",
                  "user": {
                    "identifierType": "MOBILE",
                    "identifierValue": "%s"
                  },
                  "authFactor": {
                    "authType": "PIN",
                    "credential": "%s"
                  }
                }
                """.formatted(mobileNumber, UPDATED_PIN);
        Response loginResponse = postJson(
                "4 - login with updated PIN",
                "/api/v1/auth/login",
                loginRequest
        );
        String accessToken = loginResponse.then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("message", equalTo("Login successful"))
                .body("accountId", equalTo(accountId))
                .body("tokenType", equalTo("Bearer"))
                .body("accessToken", notNullValue())
                .extract()
                .path("accessToken");
        log.info("E2E step 4 extracted login JWT for accountId={} tokenLength={}",
                accountId,
                accessToken.length()
        );

        String updateAccountRequest = """
                {
                  "requestId": "req-e2e-update-self",
                  "user": {
                    "firstName": "%s",
                    "lastName": "%s",
                    "email": "%s",
                    "address": "%s",
                    "gender": "%s",
                    "nationality": "%s",
                    "ssn": "%s",
                    "dob": "%s",
                    "preferredLang": "%s",
                    "attr1": "%s",
                    "attr2": "%s",
                    "attr3": "%s",
                    "attr4": "%s",
                    "attr5": "%s",
                    "attr6": "%s",
                    "attr7": "%s",
                    "attr8": "%s",
                    "attr9": "%s",
                    "attr10": "%s"
                  }
                }
                """.formatted(
                UPDATED_FIRST_NAME,
                UPDATED_LAST_NAME,
                UPDATED_EMAIL,
                UPDATED_ADDRESS,
                UPDATED_GENDER,
                UPDATED_NATIONALITY,
                UPDATED_SSN,
                UPDATED_DOB,
                UPDATED_PREFERRED_LANG,
                UPDATED_ATTR1,
                UPDATED_ATTR2,
                UPDATED_ATTR3,
                UPDATED_ATTR4,
                UPDATED_ATTR5,
                UPDATED_ATTR6,
                UPDATED_ATTR7,
                UPDATED_ATTR8,
                UPDATED_ATTR9,
                UPDATED_ATTR10
        );
        Response updateAccountResponse = putJson(
                "5 - update self account information",
                "/api/v1/account/updateSelf",
                accessToken,
                updateAccountRequest
        );
        updateAccountResponse.then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("message", equalTo("Account Updated successfully"));

        String addKycRequest = """
                {
                  "requestId": "req-e2e-add-kyc",
                  "kycData": {
                    "kycType": "%s",
                    "kycValue": "%s",
                    "issueDate": "%s",
                    "expiryDate": "%s",
                    "isPrimary": true,
                    "kycImageUrl": "%s"
                  }
                }
                """.formatted(
                KYC_TYPE,
                KYC_VALUE,
                KYC_ISSUE_DATE,
                KYC_EXPIRY_DATE,
                KYC_IMAGE_URL
        );
        Response addKycResponse = postJson(
                "6 - add KYC details",
                "/api/v1/account/addKyc",
                accessToken,
                addKycRequest
        );
        addKycResponse.then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("message", equalTo("KYC update request received, Pending for Approval"));

        Response accountDetailsResponse = getJson(
                "7 - fetch account details with login JWT",
                "/api/v1/account/getAccountDetails/{accountId}",
                accessToken,
                accountId
        );
        accountDetailsResponse.then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("message", equalTo("Account fetched successfully"))
                .body("account.account.accountId", equalTo(accountId))
                .body("account.account.mobileNumber", equalTo(mobileNumber))
                .body("account.account.accountType", equalTo("SUBSCRIBER"))
                .body("account.account.status", equalTo("ACTIVE"))
                .body("account.account.firstName", equalTo(UPDATED_FIRST_NAME))
                .body("account.account.lastName", equalTo(UPDATED_LAST_NAME))
                .body("account.account.email", equalTo(UPDATED_EMAIL))
                .body("account.account.address", equalTo(UPDATED_ADDRESS))
                .body("account.account.gender", equalTo(UPDATED_GENDER))
                .body("account.account.nationality", equalTo(UPDATED_NATIONALITY))
                .body("account.account.ssn", equalTo(UPDATED_SSN))
                .body("account.account.dateOfBirth", equalTo(UPDATED_DOB))
                .body("account.account.preferredLang", equalTo(UPDATED_PREFERRED_LANG))
                .body("account.account.attr1", equalTo(UPDATED_ATTR1))
                .body("account.account.attr2", equalTo(UPDATED_ATTR2))
                .body("account.account.attr3", equalTo(UPDATED_ATTR3))
                .body("account.account.attr4", equalTo(UPDATED_ATTR4))
                .body("account.account.attr5", equalTo(UPDATED_ATTR5))
                .body("account.account.attr6", equalTo(UPDATED_ATTR6))
                .body("account.account.attr7", equalTo(UPDATED_ATTR7))
                .body("account.account.attr8", equalTo(UPDATED_ATTR8))
                .body("account.account.attr9", equalTo(UPDATED_ATTR9))
                .body("account.account.attr10", equalTo(UPDATED_ATTR10))
                .body("account.kycDocuments.find { it.documentType == '" + KYC_TYPE + "' }.accountId", equalTo(accountId))
                .body("account.kycDocuments.find { it.documentType == '" + KYC_TYPE + "' }.documentNumber", equalTo(KYC_VALUE))
                .body("account.kycDocuments.find { it.documentType == '" + KYC_TYPE + "' }.issueDate", equalTo(KYC_ISSUE_DATE))
                .body("account.kycDocuments.find { it.documentType == '" + KYC_TYPE + "' }.expiryDate", equalTo(KYC_EXPIRY_DATE))
                .body("account.kycDocuments.find { it.documentType == '" + KYC_TYPE + "' }.documentUrl", equalTo(KYC_IMAGE_URL))
                .body("account.kycDocuments.find { it.documentType == '" + KYC_TYPE + "' }.verificationStatus", equalTo("PENDING"))
                .body("account.kycDocuments.find { it.documentType == '" + KYC_TYPE + "' }.isPrimary", equalTo(true))
                .body("account.accountIdentifiers.find { it.identifierType == 'MOBILE' }.identifierValue", equalTo(mobileNumber))
                .body("account.accountIdentifiers.find { it.identifierType == 'MOBILE' }.status", equalTo("ACTIVE"));

        Response walletResponse = getJson(
                "8 - fetch account wallets with login JWT",
                "/api/v1/wallet/getAccountWallets/{accountId}",
                accessToken,
                accountId
        );
        walletResponse.then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("message", equalTo("Wallets fetched successfully"))
                .body("wallets.accountId", equalTo(accountId))
                .body("wallets.balances.find { it.walletType == 'MAIN' && it.currency == 'USD' }", notNullValue())
                .body("wallets.balances.find { it.walletType == 'MAIN' && it.currency == 'INR' }", notNullValue());
        assertInitialWalletBalances(walletResponse);

        log.info("E2E self-registration flow completed successfully for accountId={} mobileNumber={}",
                accountId,
                mobileNumber
        );
    }

    @Test
    @Order(2)
    void databaseBootstrap_shouldSeedSuperAdminCreateNetworkAdminAndValidateCredentialChangeLogin() {
        log.info("E2E superadmin login validation starting for loginId={}", DEFAULT_ADMIN_LOGIN_ID);

        String superAdminLoginRequest = """
                {
                  "requestId": "req-e2e-superadmin-login",
                  "user": {
                    "identifierType": "LOGINID",
                    "identifierValue": "%s"
                  },
                  "authFactor": {
                    "authType": "PASSWORD",
                    "credential": "%s"
                  }
                }
                """.formatted(DEFAULT_ADMIN_LOGIN_ID, DEFAULT_ADMIN_PASSWORD);

        Response superAdminLoginResponse = postJson(
                "superadmin login with bootstrap credentials",
                "/api/v1/auth/login",
                superAdminLoginRequest
        );

        String superAdminAccessToken = superAdminLoginResponse.then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("message", equalTo("Login successful"))
                .body("accountId", equalTo(DEFAULT_ADMIN_ACCOUNT_ID))
                .body("tokenType", equalTo("Bearer"))
                .body("accessToken", notNullValue())
                .extract()
                .path("accessToken");

        assertJwt(superAdminAccessToken, DEFAULT_ADMIN_ACCOUNT_ID, "PASSWORD", "ADMIN");

        Integer superAdminRoleCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM %s ur
                JOIN %s r ON r.role_id = ur.role_id
                WHERE ur.user_id = ?
                  AND r.role_code = 'SUPERADMIN'
                """.formatted(tenantTable("user_roles"), tenantTable("roles")), Integer.class, DEFAULT_ADMIN_ACCOUNT_ID);
        assertEquals(1, superAdminRoleCount);

        String networkAdminMobile = "800" + String.valueOf(System.currentTimeMillis()).substring(5);
        String networkAdminLoginId = NETWORK_ADMIN_LOGIN_PREFIX + System.currentTimeMillis();
        String registerNetworkAdminRequest = """
                {
                  "requestId": "req-e2e-networkadmin-register",
                  "user": {
                    "mobileNumber": "%s",
                    "accountType": "ADMIN",
                    "firstName": "Network",
                    "lastName": "Admin",
                    "email": "networkadmin.e2e@example.com",
                    "address": "200 PayNest Network Street",
                    "gender": "MALE",
                    "dateOfBirth": "1991-06-20",
                    "preferredLang": "en",
                    "nationality": "USA",
                    "ssn": "111-22-3333",
                    "remarks": "E2E network admin",
                    "loginId": "%s",
                    "role": "NETWORKADMIN"
                  }
                }
                """.formatted(networkAdminMobile, networkAdminLoginId);

        Response registerNetworkAdminResponse = postJson(
                "create networkadmin using superadmin JWT",
                "/api/v1/account/registerUser",
                superAdminAccessToken,
                registerNetworkAdminRequest
        );
        String networkAdminAccountId = registerNetworkAdminResponse.then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("requestId", equalTo("req-e2e-networkadmin-register"))
                .body("message", equalTo("User registered successfully"))
                .body("accountId", notNullValue())
                .extract()
                .path("accountId");

        String networkAdminLoginRequest = """
                {
                  "requestId": "req-e2e-networkadmin-login-before-change",
                  "user": {
                    "identifierType": "LOGINID",
                    "identifierValue": "%s"
                  },
                  "authFactor": {
                    "authType": "PASSWORD",
                    "credential": "%s"
                  }
                }
                """.formatted(networkAdminLoginId, NETWORK_ADMIN_DEFAULT_PASSWORD);

        Response firstLoginResponse = postJson(
                "networkadmin first login should require credential change",
                "/api/v1/auth/login",
                networkAdminLoginRequest
        );
        firstLoginResponse.then()
                .statusCode(400)
                .body("code", equalTo(ErrorCodes.FORCE_AUTH_CHANGE));

        String changeDefaultPasswordRequest = """
                {
                  "requestId": "req-e2e-networkadmin-change-default-password",
                  "user": {
                    "identifierType": "LOGINID",
                    "identifierValue": "%s"
                  },
                  "authFactorOld": {
                    "authType": "PASSWORD",
                    "credential": "%s"
                  },
                  "authFactorNew": {
                    "authType": "PASSWORD",
                    "credential": "%s"
                  }
                }
                """.formatted(
                networkAdminLoginId,
                NETWORK_ADMIN_DEFAULT_PASSWORD,
                NETWORK_ADMIN_UPDATED_PASSWORD
        );
        Response changePasswordResponse = postJson(
                "networkadmin changes default password",
                "/api/v1/account/password/changeDefault",
                changeDefaultPasswordRequest
        );
        changePasswordResponse.then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("message", equalTo("Password changed successfully"));

        String networkAdminLoginAfterChangeRequest = """
                {
                  "requestId": "req-e2e-networkadmin-login-after-change",
                  "user": {
                    "identifierType": "LOGINID",
                    "identifierValue": "%s"
                  },
                  "authFactor": {
                    "authType": "PASSWORD",
                    "credential": "%s"
                  }
                }
                """.formatted(networkAdminLoginId, NETWORK_ADMIN_UPDATED_PASSWORD);
        Response networkAdminLoginResponse = postJson(
                "networkadmin login after password change",
                "/api/v1/auth/login",
                networkAdminLoginAfterChangeRequest
        );
        String networkAdminAccessToken = networkAdminLoginResponse.then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("message", equalTo("Login successful"))
                .body("accountId", equalTo(networkAdminAccountId))
                .body("tokenType", equalTo("Bearer"))
                .body("accessToken", notNullValue())
                .extract()
                .path("accessToken");

        assertJwt(networkAdminAccessToken, networkAdminAccountId, "PASSWORD", "ADMIN");
        Integer networkAdminRoleCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM %s ur
                JOIN %s r ON r.role_id = ur.role_id
                WHERE ur.user_id = ?
                  AND r.role_code = 'NETWORKADMIN'
                """.formatted(tenantTable("user_roles"), tenantTable("roles")), Integer.class, networkAdminAccountId);
        assertEquals(1, networkAdminRoleCount);

        createBusinessUserChangePasswordAndLogin(
                networkAdminAccessToken,
                "AGENT",
                "AGENT",
                "agent"
        );
        createBusinessUserChangePasswordAndLogin(
                networkAdminAccessToken,
                "MERCHANT",
                "MERCHANT",
                "merchant"
        );
        createBusinessUserChangePasswordAndLogin(
                networkAdminAccessToken,
                "BILLER",
                "BILLER",
                "biller"
        );

        log.info("E2E superadmin and networkadmin login validation completed successfully for superAdminAccountId={} networkAdminAccountId={} tokenLength={}",
                DEFAULT_ADMIN_ACCOUNT_ID,
                networkAdminAccountId,
                networkAdminAccessToken.length()
        );
    }

    @Test
    @Order(3)
    void bulkUserCreation_shouldCreateTenSubscribersAndFiveBusinessUsersPerType() {
        log.info("E2E bulk user creation starting: subscribers={} businessUsersPerType={}",
                BULK_SUBSCRIBER_COUNT,
                BULK_BUSINESS_USER_COUNT_PER_TYPE
        );

        String networkAdminAccessToken = createNetworkAdminAndLogin("bulk");

        for (int i = 1; i <= BULK_SUBSCRIBER_COUNT; i++) {
            createSubscriberChangePinAndLogin(i);
            delayOneSecondBetweenBulkUsers();
        }

        for (int i = 1; i <= BULK_BUSINESS_USER_COUNT_PER_TYPE; i++) {
            createBusinessUserChangePasswordAndLogin(networkAdminAccessToken, "AGENT", "AGENT", "agent" + i);
            delayOneSecondBetweenBulkUsers();
            createBusinessUserChangePasswordAndLogin(networkAdminAccessToken, "MERCHANT", "MERCHANT", "merchant" + i);
            delayOneSecondBetweenBulkUsers();
            createBusinessUserChangePasswordAndLogin(networkAdminAccessToken, "BILLER", "BILLER", "biller" + i);
            delayOneSecondBetweenBulkUsers();
        }

        log.info("E2E bulk user creation completed successfully");
    }

    @Test
    @Order(4)
    void stockInitiatedByNetworkAdmin_shouldRejectSameApproverAndAllowDifferentNetworkAdminForUsdWallet() {
        String initiatingNetAdminToken = createNetworkAdminAndLogin("stockinit");
        String approvingNetAdminToken = createNetworkAdminAndLogin("stockapprove");
        String initiatingNetAdminAccountId = jwtService.getClaims(initiatingNetAdminToken).getSubject();
        String approvingNetAdminAccountId = jwtService.getClaims(approvingNetAdminToken).getSubject();
        createFxRate(initiatingNetAdminToken, "INR", new BigDecimal("80.00"));
        createFxRate(initiatingNetAdminToken, "EUR", new BigDecimal("0.90"));
        Response initiateStockResponse = postJson(
                "networkadmin initiates USD stock transaction",
                "/api/v1/pay/stockInitiate",
                initiatingNetAdminToken,
                stockInitiateRequest("req-e2e-stock-initiate-usd", "USD", "25000.00")
        );
        String transactionId = initiateStockResponse.then()
                .statusCode(200)
                .body("responseStatus", equalTo("PENDING"))
                .body("operationType", equalTo("STOCK"))
                .body("code", equalTo("STOCK_INITIATED"))
                .body("transactionId", notNullValue())
                .body("currency", equalTo("USD"))
                .extract()
                .path("transactionId");

        assertStockTransactionInitiator(transactionId, initiatingNetAdminAccountId);

        postJson(
                "same networkadmin cannot approve initiated stock transaction",
                "/api/v1/pay/stockStatusUpdate",
                initiatingNetAdminToken,
                stockApprovalRequest(transactionId, "APPROVED", null, "same approver should fail")
        ).then()
                .statusCode(400)
                .body("responseStatus", equalTo("FAILURE"))
                .body("operationType", equalTo("stockStatusUpdate"))
                .body("code", equalTo(ErrorCodes.INVALID_INITIATOR));

        postJson(
                "different networkadmin approves initiated stock transaction",
                "/api/v1/pay/stockStatusUpdate",
                approvingNetAdminToken,
                stockApprovalRequest(transactionId, "APPROVED", null, "approved by different network admin")
        ).then()
                .statusCode(200)
                .body("responseStatus", equalTo("SUCCESS"))
                .body("operationType", equalTo("STOCK"))
                .body("code", equalTo("STOCK_APPROVED"))
                .body("transactionId", equalTo(transactionId))
                .body("currency", equalTo("USD"));

        assertApprovedStockTransaction(transactionId, initiatingNetAdminAccountId, approvingNetAdminAccountId);

        BusinessUser agent = createBusinessUserChangePasswordAndLogin(
                initiatingNetAdminToken,
                "AGENT",
                "AGENT",
                "o2cagent"
        );
        performApprovedO2CTransaction(
                initiatingNetAdminToken,
                approvingNetAdminToken,
                agent,
                AGENT_O2C_AMOUNT.toPlainString()
        );
        Response initiateInrStockResponse = postJson(
                "networkadmin initiates INR stock transaction",
                "/api/v1/pay/stockInitiate",
                initiatingNetAdminToken,
                stockInitiateRequest("req-e2e-stock-initiate-inr", "INR", "12500.00")
        );
        String inrStockTransactionId = initiateInrStockResponse.then()
                .statusCode(200)
                .body("responseStatus", equalTo("PENDING"))
                .body("operationType", equalTo("STOCK"))
                .body("code", equalTo("STOCK_INITIATED"))
                .body("transactionId", notNullValue())
                .body("currency", equalTo("INR"))
                .extract()
                .path("transactionId");

        assertStockTransactionInitiator(inrStockTransactionId, initiatingNetAdminAccountId);

        postJson(
                "different networkadmin approves initiated INR stock transaction",
                "/api/v1/pay/stockStatusUpdate",
                approvingNetAdminToken,
                stockApprovalRequest(inrStockTransactionId, "APPROVED", null, "approved INR stock by different network admin")
        ).then()
                .statusCode(200)
                .body("responseStatus", equalTo("SUCCESS"))
                .body("operationType", equalTo("STOCK"))
                .body("code", equalTo("STOCK_APPROVED"))
                .body("transactionId", equalTo(inrStockTransactionId))
                .body("currency", equalTo("INR"));

        assertApprovedStockTransaction(inrStockTransactionId, initiatingNetAdminAccountId, approvingNetAdminAccountId, "INR");

        performApprovedO2CTransaction(
                initiatingNetAdminToken,
                approvingNetAdminToken,
                agent,
                "1250.00",
                "INR"
        );
        SubscriberUser inrSubscriber = createSubscriberChangePinAndLogin(900);
        performCashInFromAgent(agent, inrSubscriber, new BigDecimal("250.00"), "INR");
        assertMainCurrencyBalanceFromEnquiry(agent, "INR", new BigDecimal("1000.00"));
        assertMainCurrencyBalanceFromEnquiry(inrSubscriber, "INR", new BigDecimal("250.00"));
        assertMainCurrencyBalanceFromEnquiry(agent, "USD", AGENT_O2C_AMOUNT);
        assertMainCurrencyBalanceFromEnquiry(inrSubscriber, "USD", BigDecimal.ZERO);
        List<SubscriberUser> cashInSubscribers = registerUpdateAndCashInSubscribers(agent);

        BusinessUser merchant = createBusinessUserChangePasswordAndLogin(
                initiatingNetAdminToken,
                "MERCHANT",
                "MERCHANT",
                "o2cmerchant"
        );
        performApprovedO2CTransaction(
                initiatingNetAdminToken,
                approvingNetAdminToken,
                merchant,
                MERCHANT_O2C_AMOUNT.toPlainString()
        );

        BusinessUser biller = createBusinessUserChangePasswordAndLogin(
                initiatingNetAdminToken,
                "BILLER",
                "BILLER",
                "o2cbiller"
        );
        performApprovedO2CTransaction(
                initiatingNetAdminToken,
                approvingNetAdminToken,
                biller,
                BILLER_O2C_AMOUNT.toPlainString()
        );

        Map<String, BigDecimal> expectedBalances = runU2UScenarios(cashInSubscribers, List.of(agent, merchant, biller));
        runMerchantAndBillPaymentScenarios(cashInSubscribers, merchant, biller, expectedBalances);
        runCashOutScenarios(cashInSubscribers, agent, expectedBalances);
     //   runIntraWalletUsdToInrScenario(agent);
    }

    @Test
    @Order(5)
    void tagLifecycleAndServiceCharges_shouldApplyDefaultTagsAndResolveChargesFromLinkedTags() {
        String networkAdminAccessToken = createNetworkAdminAndLogin("tagpricing");
        String networkAdminAccountId = jwtService.getClaims(networkAdminAccessToken).getSubject();
        SubscriberUser subscriber = createSubscriberChangePinAndLogin(500);
        SubscriberUser receiverSubscriber = createSubscriberChangePinAndLogin(501);
        BusinessUser agent = createBusinessUserChangePasswordAndLogin(networkAdminAccessToken, "AGENT", "AGENT", "tagagent");
        BusinessUser merchant = createBusinessUserChangePasswordAndLogin(networkAdminAccessToken, "MERCHANT", "MERCHANT", "tagmerchant");
        BusinessUser biller = createBusinessUserChangePasswordAndLogin(networkAdminAccessToken, "BILLER", "BILLER", "tagbiller");

        assertDefaultBaseTag(subscriber.accountId(), "SUBSCRIBER_BASE", "SUBSCRIBER");
        assertDefaultBaseTag(receiverSubscriber.accountId(), "SUBSCRIBER_BASE", "SUBSCRIBER");
        assertDefaultBaseTag(agent.accountId(), "AGENT_BASE", "AGENT");
        assertDefaultBaseTag(merchant.accountId(), "MERCHANT_BASE", "MERCHANT");
        assertNoDefaultBaseTag(biller.accountId(), "BILLER");
        assertNoDefaultBaseTag(networkAdminAccountId, "ADMIN");
        assertSeededDefaultBaseTag("PARTNER_BASE", "PARTNER");

        String suffix = uniqueSuffix();
        Long subscriberBehaviorTagId = createTag(
                networkAdminAccessToken,
                "SUB_BEHAVIOR_" + suffix,
                "Subscriber Behavior " + suffix,
                "SUBSCRIBER",
                "BEHAVIOR"
        );
        Long receiverSubscriberRiskTagId = createTag(
                networkAdminAccessToken,
                "SUB_RISK_" + suffix,
                "Subscriber Risk " + suffix,
                "SUBSCRIBER",
                "RISK"
        );
        Long merchantRiskTagId = createTag(
                networkAdminAccessToken,
                "MERCHANT_RISK_" + suffix,
                "Merchant Risk " + suffix,
                "MERCHANT",
                "RISK"
        );
        Long agentUpgradeTagId = createTag(
                networkAdminAccessToken,
                "AGENT_UPGRADE_" + suffix,
                "Agent Upgrade " + suffix,
                "AGENT",
                "UPGRADE"
        );

        linkTagToAccount(networkAdminAccessToken, subscriberBehaviorTagId, subscriber.accountId());
        linkTagToAccount(networkAdminAccessToken, receiverSubscriberRiskTagId, receiverSubscriber.accountId());
        linkTagToAccount(networkAdminAccessToken, merchantRiskTagId, merchant.accountId());
        linkTagToAccount(networkAdminAccessToken, agentUpgradeTagId, agent.accountId());

        assertAccountHasTag(subscriber.accountId(), "SUB_BEHAVIOR_" + suffix, "BEHAVIOR", false);
        assertAccountHasTag(receiverSubscriber.accountId(), "SUB_RISK_" + suffix, "RISK", false);
        assertAccountHasTag(merchant.accountId(), "MERCHANT_RISK_" + suffix, "RISK", false);
        assertAccountHasTag(agent.accountId(), "AGENT_UPGRADE_" + suffix, "UPGRADE", false);

        unlinkTagFromAccount(networkAdminAccessToken, agentUpgradeTagId, agent.accountId());
        assertAccountDoesNotHaveTag(agent.accountId(), "AGENT_UPGRADE_" + suffix);
        linkTagToAccount(networkAdminAccessToken, agentUpgradeTagId, agent.accountId());
        assertAccountHasTag(agent.accountId(), "AGENT_UPGRADE_" + suffix, "UPGRADE", false);

        String serviceCode = "MERCHPAY";
        createLeastServiceChargeRules(
                networkAdminAccessToken,
                serviceCode,
                "SUB_BEHAVIOR_" + suffix,
                "MERCHANT_RISK_" + suffix
        );
        createDiscountRule(
                networkAdminAccessToken,
                serviceCode,
                "SUB_BEHAVIOR_" + suffix,
                "MERCHANT_RISK_" + suffix,
                "flat-discount-" + serviceCode,
                flatServiceChargeConfig("2.00")
        );
        createDiscountRule(
                networkAdminAccessToken,
                serviceCode,
                "SUB_BEHAVIOR_" + suffix,
                "MERCHANT_RISK_" + suffix,
                "percent-discount-" + serviceCode,
                percentServiceChargeConfig("1.25")
        );
        assertLeastServiceChargePricing(
                subscriber.accessToken(),
                serviceCode,
                "SUBSCRIBER",
                subscriber.mobile(),
                "PIN",
                subscriber.pin(),
                "MERCHANT",
                merchant.mobile(),
                "SUB_BEHAVIOR_" + suffix,
                "MERCHANT_RISK_" + suffix,
                new BigDecimal("2.50"),
                new BigDecimal("3.25")
        );

        String missingTagServiceCode = "E2EMISSING" + suffix.substring(0, Math.min(8, suffix.length())).toUpperCase(Locale.ROOT);
        createServiceChargeRule(
                networkAdminAccessToken,
                missingTagServiceCode,
                "MISSING_SENDER_" + suffix,
                "MISSING_RECEIVER_" + suffix,
                "missing-tags-" + missingTagServiceCode,
                flatServiceChargeConfig("8.75")
        );
        assertZeroServiceChargePricing(
                subscriber.accessToken(),
                missingTagServiceCode,
                "SUBSCRIBER",
                subscriber.mobile(),
                "PIN",
                subscriber.pin(),
                "MERCHANT",
                merchant.mobile()
        );

        createServiceChargeRule(
                networkAdminAccessToken,
                missingTagServiceCode,
                "ALLTags",
                "ALLtags",
                "all-tags-" + missingTagServiceCode,
                flatServiceChargeConfig("3.40")
        );
        assertLeastServiceChargePricing(
                subscriber.accessToken(),
                missingTagServiceCode,
                "SUBSCRIBER",
                subscriber.mobile(),
                "PIN",
                subscriber.pin(),
                "MERCHANT",
                merchant.mobile(),
                "ALLTAGS",
                "ALLTAGS",
                new BigDecimal("3.40"),
                BigDecimal.ZERO
        );

        String campaignServiceCode = "E2ECAMP" + suffix.substring(0, Math.min(8, suffix.length())).toUpperCase(Locale.ROOT);
        createCampaignServiceChargeRule(
                networkAdminAccessToken,
                campaignServiceCode,
                "campaign-" + campaignServiceCode,
                flatServiceChargeConfig("1.15")
        );
        assertLeastServiceChargePricing(
                subscriber.accessToken(),
                campaignServiceCode,
                "SUBSCRIBER",
                subscriber.mobile(),
                "PIN",
                subscriber.pin(),
                "MERCHANT",
                merchant.mobile(),
                "ALL",
                "ALL",
                new BigDecimal("1.15"),
                BigDecimal.ZERO
        );

        Long p2pLowestRuleId = createLeastServiceChargeRules(
                networkAdminAccessToken,
                "P2P",
                "SUB_BEHAVIOR_" + suffix,
                "SUB_RISK_" + suffix
        );
        updatePricingRuleCharge(networkAdminAccessToken, p2pLowestRuleId, "1.75");
        assertLeastServiceChargePricing(
                subscriber.accessToken(),
                "P2P",
                "SUBSCRIBER",
                subscriber.mobile(),
                "PIN",
                subscriber.pin(),
                "SUBSCRIBER",
                receiverSubscriber.mobile(),
                "SUB_BEHAVIOR_" + suffix,
                "SUB_RISK_" + suffix,
                new BigDecimal("1.75"),
                BigDecimal.ZERO
        );
        updatePricingRuleStatus(networkAdminAccessToken, p2pLowestRuleId, "INACTIVE");
        assertLeastServiceChargePricing(
                subscriber.accessToken(),
                "P2P",
                "SUBSCRIBER",
                subscriber.mobile(),
                "PIN",
                subscriber.pin(),
                "SUBSCRIBER",
                receiverSubscriber.mobile(),
                "SUB_BEHAVIOR_" + suffix,
                "SUB_RISK_" + suffix,
                new BigDecimal("4.00"),
                BigDecimal.ZERO
        );

        createLeastServiceChargeRules(
                networkAdminAccessToken,
                "CASHIN",
                "AGENT_UPGRADE_" + suffix,
                "SUB_BEHAVIOR_" + suffix
        );
        assertLeastServiceChargePricing(
                agent.accessToken(),
                "CASHIN",
                "AGENT",
                agent.mobile(),
                "PASSWORD",
                agent.password(),
                "SUBSCRIBER",
                subscriber.mobile(),
                "AGENT_UPGRADE_" + suffix,
                "SUB_BEHAVIOR_" + suffix,
                new BigDecimal("2.50"),
                BigDecimal.ZERO
        );

        createLeastServiceChargeRules(
                networkAdminAccessToken,
                "CASHOUT",
                "SUB_BEHAVIOR_" + suffix,
                "AGENT_UPGRADE_" + suffix
        );
        assertLeastServiceChargePricing(
                subscriber.accessToken(),
                "CASHOUT",
                "SUBSCRIBER",
                subscriber.mobile(),
                "PIN",
                subscriber.pin(),
                "AGENT",
                agent.mobile(),
                "SUB_BEHAVIOR_" + suffix,
                "AGENT_UPGRADE_" + suffix,
                new BigDecimal("2.50"),
                BigDecimal.ZERO
        );

        deleteTag(networkAdminAccessToken, subscriberBehaviorTagId);
        assertTagDeleted(subscriberBehaviorTagId, "SUB_BEHAVIOR_" + suffix);
        assertAccountDoesNotHaveTag(subscriber.accountId(), "SUB_BEHAVIOR_" + suffix);
    }

    @Test
    @Order(6)
    void walletRestrictionLifecycle_shouldBlockSenderReceiverBothValidateNegativesAndHistory() {
        String networkAdminAccessToken = createNetworkAdminAndLogin("walletrestriction");
        String approvingNetworkAdminAccessToken = createNetworkAdminAndLogin("walletrestrictionapprove");
        String networkAdminAccountId = jwtService.getClaims(networkAdminAccessToken).getSubject();
        String approvingNetworkAdminAccountId = jwtService.getClaims(approvingNetworkAdminAccessToken).getSubject();
        String stockTransactionId = postJson(
                "networkadmin initiates USD stock for wallet restriction O2C",
                "/api/v1/pay/stockInitiate",
                networkAdminAccessToken,
                stockInitiateRequest("req-e2e-walletrestriction-stock-usd-" + uniqueSuffix(), "USD", "1000.00")
        ).then()
                .statusCode(200)
                .body("responseStatus", equalTo("PENDING"))
                .body("operationType", equalTo("STOCK"))
                .body("code", equalTo("STOCK_INITIATED"))
                .body("transactionId", notNullValue())
                .body("currency", equalTo("USD"))
                .extract()
                .path("transactionId");
        assertStockTransactionInitiator(stockTransactionId, networkAdminAccountId);

        postJson(
                "different networkadmin approves USD stock for wallet restriction O2C",
                "/api/v1/pay/stockStatusUpdate",
                approvingNetworkAdminAccessToken,
                stockApprovalRequest(stockTransactionId, "APPROVED", null, "approved USD stock for wallet restriction O2C")
        ).then()
                .statusCode(200)
                .body("responseStatus", equalTo("SUCCESS"))
                .body("operationType", equalTo("STOCK"))
                .body("code", equalTo("STOCK_APPROVED"))
                .body("transactionId", equalTo(stockTransactionId))
                .body("currency", equalTo("USD"));
        assertApprovedStockTransaction(stockTransactionId, networkAdminAccountId, approvingNetworkAdminAccountId, "USD");

        BusinessUser agent = createBusinessUserChangePasswordAndLogin(
                networkAdminAccessToken,
                "AGENT",
                "AGENT",
                "restrictionagent"
        );
        performApprovedO2CTransaction(
                networkAdminAccessToken,
                approvingNetworkAdminAccessToken,
                agent,
                "100.00"
        );
        SubscriberUser sender = createSubscriberChangePinAndLogin(610);
        SubscriberUser receiver = createSubscriberChangePinAndLogin(611);
        performCashInFromAgent(agent, sender, new BigDecimal("50.00"));

        Long agentWalletId = mainWalletId(agent.accountId(), "USD");
        Long senderWalletId = mainWalletId(sender.accountId(), "USD");
        Long receiverWalletId = mainWalletId(receiver.accountId(), "USD");
        U2UParticipant senderParticipant = subscriberParticipant(sender);
        U2UParticipant receiverParticipant = subscriberParticipant(receiver);

        postJson(
                "subscriber cannot add sender wallet restriction",
                "/api/v1/wallet/restrictions",
                sender.accessToken(),
                walletRestrictionRequest(senderWalletId, true, "ALL_SERVICES", List.of(), false, null, List.of())
        ).then()
                .statusCode(403)
                .body("responseStatus", equalTo("FAILURE"))
                .body("code", equalTo("ACCESS_DENIED"));

        postJson(
                "admin cannot add empty wallet restriction",
                "/api/v1/wallet/restrictions",
                networkAdminAccessToken,
                """
                        {
                          "walletId": %d,
                          "restrictions": {}
                        }
                        """.formatted(senderWalletId)
        ).then()
                .statusCode(400)
                .body("responseStatus", equalTo("FAILURE"))
                .body("code", equalTo(ErrorCodes.INVALID_REQUEST));

        getJson(
                "admin gets missing wallet restriction",
                "/api/v1/wallet/restrictions/{walletId}",
                networkAdminAccessToken,
                senderWalletId
        ).then()
                .statusCode(400)
                .body("responseStatus", equalTo("FAILURE"))
                .body("code", equalTo(ErrorCodes.WALLET_RESTRICTION_NOT_FOUND));

        postJson(
                "admin blocks sender wallet send",
                "/api/v1/wallet/restrictions",
                networkAdminAccessToken,
                walletRestrictionRequest(senderWalletId, true, "ALL_SERVICES", List.of(), false, null, List.of())
        ).then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("walletRestriction.walletId", equalTo(senderWalletId.intValue()))
                .body("walletRestriction.version", equalTo(0))
                .body("walletRestriction.restrictions.sendBlock.blocked", equalTo(true))
                .body("walletRestriction.restrictions.sendBlock.mode", equalTo("ALL_SERVICES"));

        assertBlockedU2U(senderParticipant, receiverParticipant, "WALLET_SEND_BLOCKED");
        assertWalletRestrictionHistory(networkAdminAccessToken, senderWalletId, 1, "ADD", 0);

        putJson(
                "admin unblocks sender wallet",
                "/api/v1/wallet/restrictions/%d".formatted(senderWalletId),
                networkAdminAccessToken,
                walletRestrictionRequest(null, false, null, List.of(), false, null, List.of())
        ).then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("walletRestriction.walletId", equalTo(senderWalletId.intValue()))
                .body("walletRestriction.version", equalTo(1))
                .body("walletRestriction.restrictions.sendBlock.blocked", equalTo(false));

        postJson(
                "admin blocks receiver wallet receive",
                "/api/v1/wallet/restrictions",
                networkAdminAccessToken,
                walletRestrictionRequest(receiverWalletId, false, null, List.of(), true, "ALL_SERVICES", List.of())
        ).then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("walletRestriction.walletId", equalTo(receiverWalletId.intValue()))
                .body("walletRestriction.version", equalTo(0))
                .body("walletRestriction.restrictions.receiveBlock.blocked", equalTo(true));

        assertBlockedU2U(senderParticipant, receiverParticipant, "WALLET_RECEIVE_BLOCKED");
        assertWalletRestrictionHistory(networkAdminAccessToken, senderWalletId, 2, "UPDATE", 1);
        assertWalletRestrictionHistory(networkAdminAccessToken, receiverWalletId, 1, "ADD", 0);

        putJson(
                "admin blocks both directions on sender wallet",
                "/api/v1/wallet/restrictions/%d".formatted(senderWalletId),
                networkAdminAccessToken,
                walletRestrictionRequest(null, true, "ALL_SERVICES", List.of(), true, "ALL_SERVICES", List.of())
        ).then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("walletRestriction.version", equalTo(2))
                .body("walletRestriction.restrictions.sendBlock.blocked", equalTo(true))
                .body("walletRestriction.restrictions.receiveBlock.blocked", equalTo(true));
        putJson(
                "admin blocks both directions on receiver wallet",
                "/api/v1/wallet/restrictions/%d".formatted(receiverWalletId),
                networkAdminAccessToken,
                walletRestrictionRequest(null, true, "ALL_SERVICES", List.of(), true, "ALL_SERVICES", List.of())
        ).then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("walletRestriction.version", equalTo(1))
                .body("walletRestriction.restrictions.sendBlock.blocked", equalTo(true))
                .body("walletRestriction.restrictions.receiveBlock.blocked", equalTo(true));

        assertBlockedU2U(senderParticipant, receiverParticipant, "WALLET_SEND_BLOCKED");
        assertWalletRestrictionHistory(networkAdminAccessToken, senderWalletId, 3, "UPDATE", 2);
        assertWalletRestrictionHistory(networkAdminAccessToken, receiverWalletId, 2, "UPDATE", 1);

        putJson(
                "admin unblocks sender wallet before selected services",
                "/api/v1/wallet/restrictions/%d".formatted(senderWalletId),
                networkAdminAccessToken,
                walletRestrictionRequest(null, false, null, List.of(), false, null, List.of())
        ).then()
                .statusCode(200)
                .body("walletRestriction.version", equalTo(3));
        putJson(
                "admin unblocks receiver wallet before selected services",
                "/api/v1/wallet/restrictions/%d".formatted(receiverWalletId),
                networkAdminAccessToken,
                walletRestrictionRequest(null, false, null, List.of(), false, null, List.of())
        ).then()
                .statusCode(200)
                .body("walletRestriction.version", equalTo(2));

        putJson(
                "admin blocks sender wallet selected service U2U",
                "/api/v1/wallet/restrictions/%d".formatted(senderWalletId),
                networkAdminAccessToken,
                walletRestrictionRequest(null, true, "SELECTED_SERVICES", List.of("U2U"), false, null, List.of())
        ).then()
                .statusCode(200)
                .body("walletRestriction.version", equalTo(4))
                .body("walletRestriction.restrictions.sendBlock.mode", equalTo("SELECTED_SERVICES"))
                .body("walletRestriction.restrictions.sendBlock.services[0]", equalTo("U2U"));
        assertBlockedU2U(senderParticipant, receiverParticipant, "WALLET_SEND_BLOCKED");

        putJson(
                "admin selected services excludes U2U so transfer goes through",
                "/api/v1/wallet/restrictions/%d".formatted(senderWalletId),
                networkAdminAccessToken,
                walletRestrictionRequest(null, true, "SELECTED_SERVICES", List.of("CASHIN", "BANK_TRANSFER"), false, null, List.of())
        ).then()
                .statusCode(200)
                .body("walletRestriction.version", equalTo(5));

        BigDecimal senderBefore = readMainUsdAvailableBalanceFromEnquiry(sender);
        BigDecimal receiverBefore = readMainUsdAvailableBalanceFromEnquiry(receiver);
        BigDecimal transferAmount = new BigDecimal("5.00");
        Map<String, BigDecimal> expectedBalances = new HashMap<>();
        expectedBalances.put(sender.accountId(), senderBefore);
        expectedBalances.put(receiver.accountId(), receiverBefore);
        performU2U(senderParticipant, receiverParticipant, transferAmount, expectedBalances);

        assertWalletRestrictionHistory(networkAdminAccessToken, senderWalletId, 6, "UPDATE", 5);
        assertWalletRestrictionHistory(networkAdminAccessToken, receiverWalletId, 3, "UPDATE", 2);

        postJson(
                "admin blocks agent wallet selected service U2U",
                "/api/v1/wallet/restrictions",
                networkAdminAccessToken,
                walletRestrictionRequest(agentWalletId, true, "SELECTED_SERVICES", List.of("U2U"), false, null, List.of())
        ).then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("walletRestriction.walletId", equalTo(agentWalletId.intValue()))
                .body("walletRestriction.version", equalTo(0))
                .body("walletRestriction.restrictions.sendBlock.mode", equalTo("SELECTED_SERVICES"))
                .body("walletRestriction.restrictions.sendBlock.services[0]", equalTo("U2U"));

        performCashInFromAgent(agent, receiver, new BigDecimal("5.00"));
        assertWalletRestrictionHistory(networkAdminAccessToken, agentWalletId, 1, "ADD", 0);

        putJson(
                "admin blocks agent wallet selected service CASHIN",
                "/api/v1/wallet/restrictions/%d".formatted(agentWalletId),
                networkAdminAccessToken,
                walletRestrictionRequest(null, true, "SELECTED_SERVICES", List.of("CASHIN"), false, null, List.of())
        ).then()
                .statusCode(200)
                .body("walletRestriction.version", equalTo(1))
                .body("walletRestriction.restrictions.sendBlock.mode", equalTo("SELECTED_SERVICES"))
                .body("walletRestriction.restrictions.sendBlock.services[0]", equalTo("CASHIN"));

        assertBlockedCashIn(agent, receiver, "WALLET_SEND_BLOCKED");
        assertWalletRestrictionHistory(networkAdminAccessToken, agentWalletId, 2, "UPDATE", 1);
    }

    @Test
    @Order(7)
    void intraWalletTransfer_shouldMoveFundsAcrossWalletsAndRefreshBalances() {
        String initiatingNetAdminToken = createNetworkAdminAndLogin("intrawallet");
        String approvingNetAdminToken = createNetworkAdminAndLogin("intrawalletapprove");
        String initiatingNetAdminAccountId = jwtService.getClaims(initiatingNetAdminToken).getSubject();
        String approvingNetAdminAccountId = jwtService.getClaims(approvingNetAdminToken).getSubject();
        String suffix = uniqueSuffix();

        createFxRate(initiatingNetAdminToken, "INR", new BigDecimal("80.00"));

        String usdStockTransactionId = postJson(
                "networkadmin initiates USD stock for intrawallet",
                "/api/v1/pay/stockInitiate",
                initiatingNetAdminToken,
                stockInitiateRequest("req-e2e-intrawallet-stock-usd-" + suffix, "USD", "5000.00")
        ).then()
                .statusCode(200)
                .body("responseStatus", equalTo("PENDING"))
                .body("operationType", equalTo("STOCK"))
                .body("code", equalTo("STOCK_INITIATED"))
                .body("transactionId", notNullValue())
                .extract()
                .path("transactionId");

        postJson(
                "different networkadmin approves USD stock for intrawallet",
                "/api/v1/pay/stockStatusUpdate",
                approvingNetAdminToken,
                stockApprovalRequest(usdStockTransactionId, "APPROVED", null, "approved USD stock for intrawallet")
        ).then()
                .statusCode(200)
                .body("responseStatus", equalTo("SUCCESS"))
                .body("operationType", equalTo("STOCK"))
                .body("code", equalTo("STOCK_APPROVED"))
                .body("transactionId", equalTo(usdStockTransactionId));
        assertApprovedStockTransaction(usdStockTransactionId, initiatingNetAdminAccountId, approvingNetAdminAccountId, "USD");

        String inrStockTransactionId = postJson(
                "networkadmin initiates INR stock for intrawallet",
                "/api/v1/pay/stockInitiate",
                initiatingNetAdminToken,
                stockInitiateRequest("req-e2e-intrawallet-stock-inr-" + suffix, "INR", "50000.00")
        ).then()
                .statusCode(200)
                .body("responseStatus", equalTo("PENDING"))
                .body("operationType", equalTo("STOCK"))
                .body("code", equalTo("STOCK_INITIATED"))
                .body("transactionId", notNullValue())
                .extract()
                .path("transactionId");

        postJson(
                "different networkadmin approves INR stock for intrawallet",
                "/api/v1/pay/stockStatusUpdate",
                approvingNetAdminToken,
                stockApprovalRequest(inrStockTransactionId, "APPROVED", null, "approved INR stock for intrawallet")
        ).then()
                .statusCode(200)
                .body("responseStatus", equalTo("SUCCESS"))
                .body("operationType", equalTo("STOCK"))
                .body("code", equalTo("STOCK_APPROVED"))
                .body("transactionId", equalTo(inrStockTransactionId));
        assertApprovedStockTransaction(inrStockTransactionId, initiatingNetAdminAccountId, approvingNetAdminAccountId, "INR");

        BusinessUser agent = createBusinessUserChangePasswordAndLogin(
                initiatingNetAdminToken,
                "AGENT",
                "AGENT",
                "intrawalletagent"
        );
        performApprovedO2CTransaction(
                initiatingNetAdminToken,
                approvingNetAdminToken,
                agent,
                "500.00",
                "USD"
        );

        runIntraWalletUsdToInrScenario(agent);
    }

    private void assertBlockedU2U(U2UParticipant sender, U2UParticipant receiver, String expectedErrorCode) {
        BigDecimal senderBefore = readMainUsdAvailableBalanceFromEnquiry(
                sender.accountId(),
                sender.accountType(),
                sender.accessToken()
        );
        BigDecimal receiverBefore = readMainUsdAvailableBalanceFromEnquiry(
                receiver.accountId(),
                receiver.accountType(),
                receiver.accessToken()
        );

        postJson(
                "blocked U2U from " + sender.accountId() + " to " + receiver.accountId(),
                "/api/v1/pay/U2U",
                sender.accessToken(),
                u2uRequest(sender, receiver, new BigDecimal("1.00"))
        ).then()
                .statusCode(400)
                .body("responseStatus", equalTo("FAILURE"))
                .body("operationType", equalTo("U2U"))
                .body("code", equalTo(expectedErrorCode));

        assertMainUsdBalanceFromEnquiry(sender.accountId(), sender.accountType(), sender.accessToken(), senderBefore);
        assertMainUsdBalanceFromEnquiry(receiver.accountId(), receiver.accountType(), receiver.accessToken(), receiverBefore);
        assertAvailableWalletBalanceSumIsZero("after blocked U2U attempt");
    }

    private void assertBlockedCashIn(BusinessUser agent, SubscriberUser subscriber, String expectedErrorCode) {
        BigDecimal agentBefore = readMainUsdAvailableBalanceFromEnquiry(agent);
        BigDecimal subscriberBefore = readMainUsdAvailableBalanceFromEnquiry(subscriber);

        postJson(
                "blocked CASHIN from agent " + agent.accountId() + " to subscriber " + subscriber.accountId(),
                "/api/v1/pay/CASHIN",
                agent.accessToken(),
                cashInRequest(agent, subscriber, new BigDecimal("1.00"))
        ).then()
                .statusCode(400)
                .body("responseStatus", equalTo("FAILURE"))
                .body("operationType", equalTo("CASHIN"))
                .body("code", equalTo(expectedErrorCode));

        assertMainUsdBalanceFromEnquiry(agent.accountId(), agent.accountType(), agent.accessToken(), agentBefore);
        assertMainUsdBalanceFromEnquiry(subscriber.accountId(), "SUBSCRIBER", subscriber.accessToken(), subscriberBefore);
        assertAvailableWalletBalanceSumIsZero("after blocked CASHIN attempt");
    }

    private void assertWalletRestrictionHistory(
            String adminAccessToken,
            Long walletId,
            int expectedHistoryCount,
            String expectedLatestActionType,
            int expectedLatestVersion
    ) {
        getJson(
                "fetch wallet restriction history " + walletId,
                "/api/v1/wallet/restrictions/{walletId}/history",
                adminAccessToken,
                walletId
        ).then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("walletRestrictionHistory.size()", equalTo(expectedHistoryCount))
                .body("walletRestrictionHistory[0].walletId", equalTo(walletId.intValue()))
                .body("walletRestrictionHistory[0].version", equalTo(expectedLatestVersion))
                .body("walletRestrictionHistory[0].actionType", equalTo(expectedLatestActionType))
                .body("walletRestrictionHistory[0].restrictions", notNullValue());
    }

    private String walletRestrictionRequest(
            Long walletId,
            boolean sendBlocked,
            String sendMode,
            List<String> sendServices,
            boolean receiveBlocked,
            String receiveMode,
            List<String> receiveServices
    ) {
        return """
                {
                  %s
                  "restrictions": {
                    "sendBlock": {
                      "blocked": %s,
                      "mode": %s,
                      "services": %s
                    },
                    "receiveBlock": {
                      "blocked": %s,
                      "mode": %s,
                      "services": %s
                    }
                  }
                }
                """.formatted(
                walletId == null ? "" : "\"walletId\": " + walletId + ",",
                sendBlocked,
                jsonStringOrNull(sendMode),
                jsonStringArray(sendServices),
                receiveBlocked,
                jsonStringOrNull(receiveMode),
                jsonStringArray(receiveServices)
        );
    }

    private String jsonStringOrNull(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private String jsonStringArray(List<String> values) {
        return values.stream()
                .map(this::jsonStringOrNull)
                .toList()
                .toString();
    }

    private Long mainWalletId(String accountId, String currency) {
        return jdbcTemplate.queryForObject("""
                SELECT wallet_id
                FROM %s
                WHERE account_id = ?
                  AND wallet_type = 'MAIN'
                  AND currency = ?
                  AND status = 'ACTIVE'
                """.formatted(tenantTable("wallet")), Long.class, accountId, currency);
    }

    private Long createTag(
            String accessToken,
            String tagCode,
            String tagName,
            String category,
            String tagType
    ) {
        Response response = postJson(
                "create tag " + tagCode,
                "/api/v1/tags",
                accessToken,
                """
                        {
                          "tagCode": "%s",
                          "tagName": "%s",
                          "category": "%s",
                          "tagType": "%s"
                        }
                        """.formatted(tagCode, tagName, category, tagType)
        );

        Number tagId = response.then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("message", equalTo("Tag created successfully"))
                .body("tag.tagCode", equalTo(tagCode))
                .body("tag.tagName", equalTo(tagName))
                .body("tag.category", equalTo(category))
                .body("tag.tagType", equalTo(tagType))
                .body("tag.isDefault", equalTo(false))
                .body("tag.status", equalTo("ACTIVE"))
                .extract()
                .path("tag.tagId");
        return tagId.longValue();
    }

    private void linkTagToAccount(String accessToken, Long tagId, String accountId) {
        postJson(
                "link tag " + tagId + " to account " + accountId,
                "/api/v1/tags/{tagId}/accounts/{accountId}",
                accessToken,
                "",
                tagId,
                accountId
        ).then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("message", equalTo("Tag linked successfully"))
                .body("accountTag.accountId", equalTo(accountId))
                .body("accountTag.tagId", equalTo(tagId.intValue()));
    }

    private void unlinkTagFromAccount(String accessToken, Long tagId, String accountId) {
        deleteJson(
                "unlink tag " + tagId + " from account " + accountId,
                "/api/v1/tags/{tagId}/accounts/{accountId}",
                accessToken,
                tagId,
                accountId
        ).then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("message", equalTo("Tag unlinked successfully"));
    }

    private void deleteTag(String accessToken, Long tagId) {
        deleteJson(
                "delete tag " + tagId,
                "/api/v1/tags/{tagId}",
                accessToken,
                tagId
        ).then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("message", equalTo("Tag deleted successfully"));
    }

    private Long createLeastServiceChargeRules(
            String accessToken,
            String serviceCode,
            String senderTagKey,
            String receiverTagKey
    ) {
        Long lowestRuleId = createServiceChargeRule(accessToken, serviceCode, senderTagKey, receiverTagKey, "2.50");
        createServiceChargeRule(
                accessToken,
                serviceCode,
                senderTagKey,
                receiverTagKey,
                "fixed-high-" + serviceCode,
                flatServiceChargeConfig("7.00")
        );
        createServiceChargeRule(
                accessToken,
                serviceCode,
                senderTagKey,
                receiverTagKey,
                "percent-" + serviceCode,
                percentServiceChargeConfig("5.00")
        );
        createServiceChargeRule(
                accessToken,
                serviceCode,
                senderTagKey,
                receiverTagKey,
                "hybrid-" + serviceCode,
                hybridServiceChargeConfig("1.00", "3.00")
        );
        return lowestRuleId;
    }

    private void assertLeastServiceChargePricing(
            String accessToken,
            String serviceCode,
            String senderAccountType,
            String senderMobile,
            String senderAuthType,
            String senderAuthValue,
            String receiverAccountType,
            String receiverMobile,
            String expectedSenderTagKey,
            String expectedReceiverTagKey,
            BigDecimal expectedServiceChargeAmount,
            BigDecimal expectedDiscountAmount
    ) {
        Response pricingResponse = postJson(
                "calculate " + serviceCode + " pricing through API",
                "/api/v1/pay/calculatePricing",
                accessToken,
                pricingRequestJson(
                        serviceCode,
                        senderAccountType,
                        senderMobile,
                        senderAuthType,
                        senderAuthValue,
                        receiverAccountType,
                        receiverMobile,
                        new BigDecimal("100.00")
                )
        );
        pricingResponse.then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("message", equalTo("Pricing calculated successfully"))
                .body("pricingAmounts.senderTagKey", equalTo(expectedSenderTagKey))
                .body("pricingAmounts.receiverTagKey", equalTo(expectedReceiverTagKey))
                .body("pricingAmounts.serviceChargeAffectedParty", equalTo("SENDER"));
        assertBigDecimalEquals(
                expectedServiceChargeAmount,
                toBigDecimal(pricingResponse.jsonPath().get("pricingAmounts.serviceChargeAmount")),
                "lowest " + serviceCode + " linked tag service charge"
        );
        assertBigDecimalEquals(
                expectedDiscountAmount,
                toBigDecimal(pricingResponse.jsonPath().get("pricingAmounts.discountAmount")),
                serviceCode + " linked tag discount"
        );
    }

    private void assertZeroServiceChargePricing(
            String accessToken,
            String serviceCode,
            String senderAccountType,
            String senderMobile,
            String senderAuthType,
            String senderAuthValue,
            String receiverAccountType,
            String receiverMobile
    ) {
        Response pricingResponse = postJson(
                "calculate " + serviceCode + " pricing without matching tags",
                "/api/v1/pay/calculatePricing",
                accessToken,
                pricingRequestJson(
                        serviceCode,
                        senderAccountType,
                        senderMobile,
                        senderAuthType,
                        senderAuthValue,
                        receiverAccountType,
                        receiverMobile,
                        new BigDecimal("100.00")
                )
        );
        pricingResponse.then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("message", equalTo("Pricing calculated successfully"));
        assertBigDecimalEquals(
                BigDecimal.ZERO,
                toBigDecimal(pricingResponse.jsonPath().get("pricingAmounts.serviceChargeAmount")),
                serviceCode + " service charge for non-existing tag rule"
        );
        assertBigDecimalEquals(
                BigDecimal.ZERO,
                toBigDecimal(pricingResponse.jsonPath().get("pricingAmounts.discountAmount")),
                serviceCode + " discount for non-existing tag rule"
        );
    }

    private Long createServiceChargeRule(
            String accessToken,
            String serviceCode,
            String senderTagKey,
            String receiverTagKey,
            String flatCharge
    ) {
        return createServiceChargeRule(
                accessToken,
                serviceCode,
                senderTagKey,
                receiverTagKey,
                "flat-min-" + flatCharge,
                flatServiceChargeConfig(flatCharge)
        );
    }

    private Long createServiceChargeRule(
            String accessToken,
            String serviceCode,
            String senderTagKey,
            String receiverTagKey,
            String ruleNameSuffix,
            String pricingConfig
    ) {
        return createPricingRule(
                accessToken,
                serviceCode,
                "SERVICE_CHARGE",
                senderTagKey,
                receiverTagKey,
                ruleNameSuffix,
                pricingConfig
        );
    }

    private Long createCampaignServiceChargeRule(
            String accessToken,
            String serviceCode,
            String ruleNameSuffix,
            String pricingConfig
    ) {
        return createPricingRule(
                accessToken,
                serviceCode,
                "SERVICE_CHARGE",
                "CAMPAIGN",
                "ALL",
                "ALL",
                ruleNameSuffix,
                pricingConfig
        );
    }

    private Long createDiscountRule(
            String accessToken,
            String serviceCode,
            String senderTagKey,
            String receiverTagKey,
            String ruleNameSuffix,
            String pricingConfig
    ) {
        return createPricingRule(
                accessToken,
                serviceCode,
                "DISCOUNT",
                senderTagKey,
                receiverTagKey,
                ruleNameSuffix,
                pricingConfig
        );
    }

    private Long createPricingRule(
            String accessToken,
            String serviceCode,
            String ruleType,
            String senderTagKey,
            String receiverTagKey,
            String ruleNameSuffix,
            String pricingConfig
    ) {
        return createPricingRule(
                accessToken,
                serviceCode,
                ruleType,
                "STATIC",
                senderTagKey,
                receiverTagKey,
                ruleNameSuffix,
                pricingConfig
        );
    }

    private Long createPricingRule(
            String accessToken,
            String serviceCode,
            String ruleType,
            String pricingType,
            String senderTagKey,
            String receiverTagKey,
            String ruleNameSuffix,
            String pricingConfig
    ) {
        Number pricingId = postJson(
                "create service charge pricing rule",
                "/api/v1/pricing",
                accessToken,
                """
                        {
                          "pricingName": "E2E tag %s %s %s",
                          "serviceCode": "%s",
                          "ruleType": "%s",
                          "pricingType": "%s",
                          "payer": "SENDER",
                          "senderTagKey": "%s",
                          "receiverTagKey": "%s",
                          "currency": "USD",
                          "pricingConfig": %s,
                          "status": "ACTIVE"
                        }
                        """.formatted(
                        ruleType.toLowerCase(Locale.ROOT),
                        serviceCode,
                        ruleNameSuffix,
                        serviceCode,
                        ruleType,
                        pricingType,
                        senderTagKey,
                        receiverTagKey,
                        pricingConfig
                )
        ).then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("message", equalTo("Pricing rule created successfully"))
                .body("pricing.serviceCode", equalTo(serviceCode))
                .body("pricing.ruleType", equalTo(ruleType))
                .body("pricing.senderTagKey", equalTo(senderTagKey.toUpperCase(Locale.ROOT)))
                .body("pricing.receiverTagKey", equalTo(receiverTagKey.toUpperCase(Locale.ROOT)))
                .extract()
                .path("pricing.id");
        return pricingId.longValue();
    }

    private void updatePricingRuleCharge(String accessToken, Long pricingRuleId, String flatCharge) {
        patchJson(
                "update service charge pricing rule " + pricingRuleId,
                "/api/v1/pricing/{id}",
                accessToken,
                """
                        {
                          "pricingConfig": %s
                        }
                        """.formatted(flatServiceChargeConfig(flatCharge)),
                pricingRuleId
        ).then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("message", equalTo("Pricing rule updated successfully"))
                .body("pricing.id", equalTo(pricingRuleId.intValue()));
    }

    private void updatePricingRuleStatus(String accessToken, Long pricingRuleId, String status) {
        patchJson(
                "update pricing rule status " + pricingRuleId,
                "/api/v1/pricing/{id}/status",
                accessToken,
                """
                        {
                          "status": "%s"
                        }
                        """.formatted(status),
                pricingRuleId
        ).then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("message", equalTo("Pricing rule status updated successfully"))
                .body("pricing.id", equalTo(pricingRuleId.intValue()))
                .body("pricing.status", equalTo(status));
    }

    private String flatServiceChargeConfig(String value) {
        return """
                {
                  "basedOn": "TXNAMOUNT",
                  "charging_strategy": "FLAT",
                  "calc": {
                    "type": "FLAT",
                    "value": %s
                  }
                }
                """.formatted(value);
    }

    private String percentServiceChargeConfig(String value) {
        return """
                {
                  "basedOn": "TXNAMOUNT",
                  "charging_strategy": "FLAT",
                  "calc": {
                    "type": "PERCENT",
                    "value": %s
                  }
                }
                """.formatted(value);
    }

    private String hybridServiceChargeConfig(String flatValue, String percentValue) {
        return """
                {
                  "basedOn": "TXNAMOUNT",
                  "charging_strategy": "FLAT",
                  "calc": {
                    "type": "HYBRID",
                    "operator": "ADD",
                    "components": [
                      {
                        "type": "FLAT",
                        "value": %s
                      },
                      {
                        "type": "PERCENT",
                        "value": %s
                      }
                    ]
                  }
                }
                """.formatted(flatValue, percentValue);
    }

    private String pricingRequestJson(
            String serviceCode,
            String senderAccountType,
            String senderMobile,
            String senderAuthType,
            String senderAuthValue,
            String receiverAccountType,
            String receiverMobile,
            BigDecimal amount
    ) {
        return """
                {
                  "operationType": "%s",
                  "requestGateway": "MOBILE",
                  "preferredLang": "en",
                  "initiatedBy": "DEBITOR",
                  "debitor": {
                    "accountType": "%s",
                    "identifier": {
                      "type": "MOBILE",
                      "value": "%s"
                    },
                    "walletType": "MAIN",
                    "authentication": {
                      "type": "%s",
                      "value": "%s"
                    }
                  },
                  "creditor": {
                    "accountType": "%s",
                    "identifier": {
                      "type": "MOBILE",
                      "value": "%s"
                    },
                    "walletType": "MAIN"
                  },
                  "transaction": {
                    "amount": %s,
                    "currency": "USD"
                  }
                }
                """.formatted(
                serviceCode,
                senderAccountType,
                senderMobile,
                senderAuthType,
                senderAuthValue,
                receiverAccountType,
                receiverMobile,
                amount.toPlainString()
        );
    }

    private void assertDefaultBaseTag(String accountId, String tagCode, String category) {
        assertAccountHasTag(accountId, tagCode, "BASE", true);
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM %s at
                JOIN %s t ON t.tag_id = at.tag_id
                WHERE at.account_id = ?
                  AND at.status = 'ACTIVE'
                  AND t.tag_code = ?
                  AND t.category = ?
                  AND t.tag_type = 'BASE'
                  AND t.is_default = TRUE
                  AND t.status = 'ACTIVE'
                """.formatted(tenantTable("account_tags"), tenantTable("tags")), Integer.class, accountId, tagCode, category);
        assertEquals(1, count, "Expected default base tag " + tagCode + " for account " + accountId);
    }

    private void assertSeededDefaultBaseTag(String tagCode, String category) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM %s
                WHERE tag_code = ?
                  AND category = ?
                  AND tag_type = 'BASE'
                  AND is_default = TRUE
                  AND status = 'ACTIVE'
                """.formatted(tenantTable("tags")), Integer.class, tagCode, category);
        assertEquals(1, count, "Expected seeded default base tag " + tagCode);
    }

    private void assertNoDefaultBaseTag(String accountId, String category) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM %s at
                JOIN %s t ON t.tag_id = at.tag_id
                WHERE at.account_id = ?
                  AND at.status = 'ACTIVE'
                  AND t.category = ?
                  AND t.tag_type = 'BASE'
                  AND t.is_default = TRUE
                  AND t.status = 'ACTIVE'
                """.formatted(tenantTable("account_tags"), tenantTable("tags")), Integer.class, accountId, category);
        assertEquals(0, count, "Expected no default base tag for category " + category + " account " + accountId);
    }

    private void assertAccountHasTag(String accountId, String tagCode, String tagType, boolean isDefault) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM %s at
                JOIN %s t ON t.tag_id = at.tag_id
                WHERE at.account_id = ?
                  AND at.status = 'ACTIVE'
                  AND t.tag_code = ?
                  AND t.tag_type = ?
                  AND t.is_default = ?
                  AND t.status = 'ACTIVE'
                """.formatted(tenantTable("account_tags"), tenantTable("tags")), Integer.class, accountId, tagCode, tagType, isDefault);
        assertEquals(1, count, "Expected account " + accountId + " to have tag " + tagCode);
    }

    private void assertAccountDoesNotHaveTag(String accountId, String tagCode) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM %s at
                JOIN %s t ON t.tag_id = at.tag_id
                WHERE at.account_id = ?
                  AND t.tag_code = ?
                """.formatted(tenantTable("account_tags"), tenantTable("tags")), Integer.class, accountId, tagCode);
        assertEquals(0, count, "Expected account " + accountId + " not to have tag " + tagCode);
    }

    private void assertTagDeleted(Long tagId, String tagCode) {
        Integer tagCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM %s
                WHERE tag_id = ?
                   OR tag_code = ?
                """.formatted(tenantTable("tags")), Integer.class, tagId, tagCode);
        Integer accountTagCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM %s
                WHERE tag_id = ?
                """.formatted(tenantTable("account_tags")), Integer.class, tagId);
        assertEquals(0, tagCount, "Expected tag to be deleted: " + tagCode);
        assertEquals(0, accountTagCount, "Expected account tag links to be removed for deleted tag: " + tagCode);
    }

    private String createNetworkAdminAndLogin(String scenarioPrefix) {
        String superAdminAccessToken = loginWithPassword(
                "req-e2e-" + scenarioPrefix + "-superadmin-login",
                DEFAULT_ADMIN_LOGIN_ID,
                DEFAULT_ADMIN_PASSWORD,
                DEFAULT_ADMIN_ACCOUNT_ID,
                "ADMIN"
        );
        assertUserRole(DEFAULT_ADMIN_ACCOUNT_ID, "SUPERADMIN");

        String uniqueSuffix = uniqueSuffix();
        String networkAdminMobile = mobileNumber("800", uniqueSuffix);
        String networkAdminLoginId = NETWORK_ADMIN_LOGIN_PREFIX + scenarioPrefix + uniqueSuffix;
        String registerNetworkAdminRequest = """
                {
                  "requestId": "req-e2e-%s-networkadmin-register",
                  "user": {
                    "mobileNumber": "%s",
                    "accountType": "ADMIN",
                    "firstName": "Network",
                    "lastName": "Admin",
                    "email": "networkadmin.%s.e2e@example.com",
                    "address": "200 PayNest Network Street",
                    "gender": "MALE",
                    "dateOfBirth": "1991-06-20",
                    "preferredLang": "en",
                    "nationality": "USA",
                    "ssn": "111-22-3333",
                    "remarks": "E2E network admin",
                    "loginId": "%s",
                    "role": "NETWORKADMIN"
                  }
                }
                """.formatted(scenarioPrefix, networkAdminMobile, scenarioPrefix, networkAdminLoginId);

        Response registerNetworkAdminResponse = postJson(
                "create networkadmin using superadmin JWT",
                "/api/v1/account/registerUser",
                superAdminAccessToken,
                registerNetworkAdminRequest
        );
        String networkAdminAccountId = registerNetworkAdminResponse.then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("requestId", equalTo("req-e2e-" + scenarioPrefix + "-networkadmin-register"))
                .body("message", equalTo("User registered successfully"))
                .body("accountId", notNullValue())
                .extract()
                .path("accountId");

        postJson(
                "networkadmin first login should require credential change",
                "/api/v1/auth/login",
                loginRequest(
                        "req-e2e-" + scenarioPrefix + "-networkadmin-login-before-change",
                        networkAdminLoginId,
                        NETWORK_ADMIN_DEFAULT_PASSWORD
                )
        ).then()
                .statusCode(400)
                .body("code", equalTo(ErrorCodes.FORCE_AUTH_CHANGE));

        postJson(
                "networkadmin changes default password",
                "/api/v1/account/password/changeDefault",
                changePasswordRequest(
                        "req-e2e-" + scenarioPrefix + "-networkadmin-change-default-password",
                        networkAdminLoginId,
                        NETWORK_ADMIN_DEFAULT_PASSWORD,
                        NETWORK_ADMIN_UPDATED_PASSWORD
                )
        ).then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("message", equalTo("Password changed successfully"));

        String networkAdminAccessToken = loginWithPassword(
                "req-e2e-" + scenarioPrefix + "-networkadmin-login-after-change",
                networkAdminLoginId,
                NETWORK_ADMIN_UPDATED_PASSWORD,
                networkAdminAccountId,
                "ADMIN"
        );
        assertUserRole(networkAdminAccountId, "NETWORKADMIN");
        assertBusinessAccount(
                networkAdminAccountId,
                "ADMIN",
                networkAdminMobile,
                networkAdminLoginId,
                "Network",
                "Admin",
                "networkadmin." + scenarioPrefix + ".e2e@example.com"
        );
        return networkAdminAccessToken;
    }

    private void createFxRate(String adminAccessToken, String targetCurrency, BigDecimal usdRate) {
        postJson(
                "create " + targetCurrency + " FX rate",
                "/api/v1/fx-rates",
                adminAccessToken,
                """
                        {
                          "targetCurrency": "%s",
                          "usdRate": %s,
                          "rateType": "MID",
                          "provider": "E2E",
                          "validFrom": "2020-01-01T00:00:00",
                          "isActive": true,
                          "field1": "SELF_REG_E2E"
                        }
                        """.formatted(targetCurrency, usdRate.toPlainString())
        ).then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("message", equalTo("FX rate created successfully"))
                .body("fxRate.targetCurrency", equalTo(targetCurrency))
                .body("fxRate.usdRate", notNullValue())
                .body("fxRate.versionNo", notNullValue());
    }

    private SubscriberUser createSubscriberChangePinAndLogin(int index) {
        String uniqueSuffix = uniqueSuffix();
        String subscriberMobile = mobileNumber("700", uniqueSuffix);
        String updatedPin = String.format("%04d", 2000 + index);

        String generateOtpRequest = """
                {
                  "requestId": "req-e2e-bulk-subscriber-%d-otp",
                  "user": {
                    "mobileNumber": "%s"
                  }
                }
                """.formatted(index, subscriberMobile);
        postJson(
                "bulk subscriber " + index + " generate OTP",
                "/api/v1/account/register/selfGenOtp",
                generateOtpRequest
        ).then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("requestId", equalTo("req-e2e-bulk-subscriber-" + index + "-otp"))
                .body("message", equalTo("OTP generated successfully"));

        String generatedOtp = readLatestCreatedRegistrationOtp(subscriberMobile);
        String registerRequest = """
                {
                  "requestId": "req-e2e-bulk-subscriber-%d-register",
                  "user": {
                    "mobileNumber": "%s",
                    "otp": "%s"
                  }
                }
                """.formatted(index, subscriberMobile, generatedOtp);
        Response registerResponse = postJson(
                "bulk subscriber " + index + " register",
                "/api/v1/account/register/selfWithOtp",
                registerRequest
        );
        String accountId = registerResponse.then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("requestId", equalTo("req-e2e-bulk-subscriber-" + index + "-register"))
                .body("message", equalTo("User registered successfully"))
                .body("accountId", notNullValue())
                .extract()
                .path("accountId");

        String changePinRequest = """
                {
                  "oldPin": "%s",
                  "newPin": "%s",
                  "identifierType": "MOBILE",
                  "identifierValue": "%s"
                }
                """.formatted(DEFAULT_PIN, updatedPin, subscriberMobile);
        postJson(
                "bulk subscriber " + index + " changes default PIN",
                "/api/v1/account/pin/changeDefault",
                changePinRequest
        ).then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("message", equalTo("PIN changed successfully"));

        String accessToken = loginWithPin(
                "req-e2e-bulk-subscriber-" + index + "-login",
                subscriberMobile,
                updatedPin,
                accountId,
                "SUBSCRIBER"
        );
        assertJwt(accessToken, accountId, "PIN", "SUBSCRIBER");
        assertUserRole(accountId, "SUBSCRIBER");
        assertSubscriberAccount(accountId, subscriberMobile);
        SubscriberUser subscriber = new SubscriberUser(accountId, subscriberMobile, accessToken, updatedPin);
        assertDefaultWalletsFromEnquiry(subscriber);
        return subscriber;
    }

    private List<SubscriberUser> registerUpdateAndCashInSubscribers(BusinessUser agent) {
        List<SubscriberUser> subscribers = new ArrayList<>();
        for (int i = 1; i <= SUBSCRIBER_CASH_IN_AMOUNTS.length; i++) {
            SubscriberUser subscriber = createSubscriberChangePinAndLogin(100 + i);
            updateSubscriberData(subscriber, i);
            performCashInFromAgent(agent, subscriber, SUBSCRIBER_CASH_IN_AMOUNTS[i - 1]);
            assertMainUsdBalanceFromEnquiry(subscriber, SUBSCRIBER_CASH_IN_AMOUNTS[i - 1]);
            subscribers.add(subscriber);
        }
        return subscribers;
    }

    private void runIntraWalletUsdToInrScenario(BusinessUser agent) {
        SubscriberUser subscriber = createSubscriberChangePinAndLogin(950);
        performCashInFromAgent(agent, subscriber, new BigDecimal("100.00"), "USD");
        assertMainCurrencyBalanceFromEnquiry(subscriber, "USD", new BigDecimal("100.00"));
        assertMainCurrencyBalanceFromEnquiry(subscriber, "INR", BigDecimal.ZERO);

        postJson(
                "subscriber intra-wallet USD to INR transfer",
                "/api/v1/pay/INTRAWALLET",
                subscriber.accessToken(),
                intraWalletTransferRequest(subscriber, new BigDecimal("10.00"), "USD", "INR")
        ).then()
                .statusCode(200)
                .body("responseStatus", equalTo("SUCCESS"))
                .body("operationType", equalTo("INTRAWALLET"))
                .body("code", equalTo("PAYMENT_SUCCESS"))
                .body("sourceWalletType", equalTo("MAIN"))
                .body("sourceCurrency", equalTo("USD"))
                .body("targetWalletType", equalTo("MAIN"))
                .body("targetCurrency", equalTo("INR"))
                .body("bonusToMainPercentage", equalTo(100))
                .body("transactionId", notNullValue());

        assertMainCurrencyBalanceFromEnquiry(subscriber, "USD", new BigDecimal("90.00"));
        assertMainCurrencyBalanceFromEnquiry(subscriber, "INR", new BigDecimal("800.00"));

        seedWalletBalance(subscriber.accountId(), "BONUS", "USD", new BigDecimal("100.00"));
        assertCurrencyWalletBalanceFromEnquiry(subscriber, "BONUS", "USD", new BigDecimal("100.00"));

        postJson(
                "subscriber intra-wallet BONUS USD to MAIN INR transfer",
                "/api/v1/pay/INTRAWALLET",
                subscriber.accessToken(),
                intraWalletTransferRequest(subscriber, new BigDecimal("100.00"), "BONUS", "MAIN", "USD", "INR")
        ).then()
                .statusCode(200)
                .body("responseStatus", equalTo("SUCCESS"))
                .body("operationType", equalTo("INTRAWALLET"))
                .body("code", equalTo("PAYMENT_SUCCESS"))
                .body("sourceWalletType", equalTo("BONUS"))
                .body("sourceCurrency", equalTo("USD"))
                .body("targetWalletType", equalTo("MAIN"))
                .body("targetCurrency", equalTo("INR"))
                .body("bonusToMainPercentage", equalTo(25))
                .body("transactionId", notNullValue());

        assertCurrencyWalletBalanceFromEnquiry(subscriber, "BONUS", "USD", BigDecimal.ZERO);
        assertMainCurrencyBalanceFromEnquiry(subscriber, "INR", new BigDecimal("2800.00"));
    }

    private void updateSubscriberData(SubscriberUser subscriber, int index) {
        String uniqueEmailToken = subscriber.accountId().toLowerCase();
        String ssnSuffix = subscriber.mobile().substring(subscriber.mobile().length() - 4);
        String updateAccountRequest = """
                {
                  "requestId": "req-e2e-cashin-subscriber-%d-update",
                  "user": {
                    "firstName": "CashinSub%d",
                    "lastName": "Updated",
                    "email": "cashin.%s.e2e@example.com",
                    "address": "400 PayNest Cashin Street",
                    "gender": "MALE",
                    "nationality": "USA",
                    "ssn": "333-44-%s",
                    "dob": "1993-08-%02d",
                    "preferredLang": "en",
                    "attr1": "cashin-sub-%d",
                    "attr2": "updated"
                  }
                }
                """.formatted(index, index, uniqueEmailToken, ssnSuffix, Math.min(index, 28), index);

        putJson(
                "update cash-in subscriber " + index + " data",
                "/api/v1/account/updateSelf",
                subscriber.accessToken(),
                updateAccountRequest
        ).then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("message", equalTo("Account Updated successfully"));
    }

    private void performCashInFromAgent(BusinessUser agent, SubscriberUser subscriber, BigDecimal amount) {
        performCashInFromAgent(agent, subscriber, amount, "USD");
    }

    private void performCashInFromAgent(BusinessUser agent, SubscriberUser subscriber, BigDecimal amount, String currency) {
        Response response = postJson(
                "agent cash-in to subscriber " + subscriber.accountId() + " " + currency,
                "/api/v1/pay/CASHIN",
                agent.accessToken(),
                cashInRequest(agent, subscriber, amount, currency)
        );

        String transactionId = response.then()
                .statusCode(200)
                .body("responseStatus", equalTo("SUCCESS"))
                .body("operationType", equalTo("CASHIN"))
                .body("code", equalTo("PAYMENT_SUCCESS"))
                .body("currency", equalTo(currency))
                .body("transactionId", notNullValue())
                .extract()
                .path("transactionId");

        assertApprovedFinancialTransferTransaction(transactionId, "CASHIN", agent.accountId(), subscriber.accountId(), amount, currency);
    }

    private Map<String, BigDecimal> runU2UScenarios(List<SubscriberUser> subscribers, List<BusinessUser> businessUsers) {
        Map<String, BigDecimal> expectedBalances = new HashMap<>();
        BigDecimal cashInTotal = BigDecimal.ZERO;
        for (int i = 0; i < subscribers.size(); i++) {
            expectedBalances.put(subscribers.get(i).accountId(), SUBSCRIBER_CASH_IN_AMOUNTS[i]);
            cashInTotal = cashInTotal.add(SUBSCRIBER_CASH_IN_AMOUNTS[i]);
        }

        expectedBalances.put(businessUsers.get(0).accountId(), AGENT_O2C_AMOUNT.subtract(cashInTotal));
        expectedBalances.put(businessUsers.get(1).accountId(), MERCHANT_O2C_AMOUNT);
        expectedBalances.put(businessUsers.get(2).accountId(), BILLER_O2C_AMOUNT);

        for (int i = 0; i < subscribers.size(); i++) {
            U2UParticipant sender = subscriberParticipant(subscribers.get(i));
            U2UParticipant receiver = subscriberParticipant(subscribers.get((i + 1) % subscribers.size()));
            BigDecimal amount = new BigDecimal("0.10").add(new BigDecimal("0.01").multiply(BigDecimal.valueOf(i)));
            performU2U(sender, receiver, amount, expectedBalances);
        }
        for (int i = subscribers.size() - 1; i >= 0; i--) {
            U2UParticipant sender = subscriberParticipant(subscribers.get(i));
            U2UParticipant receiver = subscriberParticipant(subscribers.get((i + subscribers.size() - 1) % subscribers.size()));
            BigDecimal amount = new BigDecimal("0.05").add(new BigDecimal("0.01").multiply(BigDecimal.valueOf(subscribers.size() - 1 - i)));
            performU2U(sender, receiver, amount, expectedBalances);
        }

        performU2U(businessParticipant(businessUsers.get(0)), businessParticipant(businessUsers.get(1)), new BigDecimal("5.00"), expectedBalances);
        performU2U(businessParticipant(businessUsers.get(1)), businessParticipant(businessUsers.get(2)), new BigDecimal("7.50"), expectedBalances);
        performU2U(businessParticipant(businessUsers.get(2)), businessParticipant(businessUsers.get(0)), new BigDecimal("3.25"), expectedBalances);
        performU2U(businessParticipant(businessUsers.get(0)), businessParticipant(businessUsers.get(2)), new BigDecimal("2.75"), expectedBalances);
        performU2U(businessParticipant(businessUsers.get(2)), businessParticipant(businessUsers.get(1)), new BigDecimal("1.50"), expectedBalances);

        assertInsufficientU2U(subscriberParticipant(subscribers.get(0)), subscriberParticipant(subscribers.get(1)), expectedBalances);

        assertAvailableWalletBalanceSumIsZero("after subscriber and business U2U scenarios");
        return expectedBalances;
    }

    private U2UParticipant subscriberParticipant(SubscriberUser subscriber) {
        return new U2UParticipant(
                subscriber.accountId(),
                "SUBSCRIBER",
                subscriber.mobile(),
                subscriber.accessToken(),
                "PIN",
                subscriber.pin()
        );
    }

    private U2UParticipant businessParticipant(BusinessUser user) {
        return new U2UParticipant(
                user.accountId(),
                user.accountType(),
                user.mobile(),
                user.accessToken(),
                "PASSWORD",
                user.password()
        );
    }

    private void performU2U(
            U2UParticipant sender,
            U2UParticipant receiver,
            BigDecimal amount,
            Map<String, BigDecimal> expectedBalances
    ) {
        Response response = postJson(
                "U2U from " + sender.accountType() + " " + sender.accountId() + " to " + receiver.accountType() + " " + receiver.accountId(),
                "/api/v1/pay/U2U",
                sender.accessToken(),
                u2uRequest(sender, receiver, amount)
        );

        String transactionId = response.then()
                .statusCode(200)
                .body("responseStatus", equalTo("SUCCESS"))
                .body("operationType", equalTo("U2U"))
                .body("code", equalTo("PAYMENT_SUCCESS"))
                .body("currency", equalTo("USD"))
                .body("transactionId", notNullValue())
                .extract()
                .path("transactionId");

        expectedBalances.put(sender.accountId(), expectedBalances.get(sender.accountId()).subtract(amount));
        expectedBalances.put(receiver.accountId(), expectedBalances.get(receiver.accountId()).add(amount));

        assertApprovedU2UTransaction(transactionId, sender, receiver, amount);
        assertMainUsdBalanceFromEnquiry(sender.accountId(), sender.accountType(), sender.accessToken(), expectedBalances.get(sender.accountId()));
        assertMainUsdBalanceFromEnquiry(receiver.accountId(), receiver.accountType(), receiver.accessToken(), expectedBalances.get(receiver.accountId()));
    }

    private void assertInsufficientU2U(
            U2UParticipant sender,
            U2UParticipant receiver,
            Map<String, BigDecimal> expectedBalances
    ) {
        BigDecimal amount = expectedBalances.get(sender.accountId()).add(new BigDecimal("1000.00"));
        postJson(
                "insufficient balance U2U from " + sender.accountId(),
                "/api/v1/pay/U2U",
                sender.accessToken(),
                u2uRequest(sender, receiver, amount)
        ).then()
                .statusCode(400)
                .body("responseStatus", equalTo("FAILURE"))
                .body("operationType", equalTo("U2U"))
                .body("code", equalTo(ErrorCodes.INSUFFICIENT_BALANCE));

        assertMainUsdBalanceFromEnquiry(sender.accountId(), sender.accountType(), sender.accessToken(), expectedBalances.get(sender.accountId()));
        assertMainUsdBalanceFromEnquiry(receiver.accountId(), receiver.accountType(), receiver.accessToken(), expectedBalances.get(receiver.accountId()));
        assertAvailableWalletBalanceSumIsZero("after insufficient U2U attempt");
    }

    private void runCashOutScenarios(
            List<SubscriberUser> subscribers,
            BusinessUser agent,
            Map<String, BigDecimal> expectedBalances
    ) {
        BigDecimal[] amounts = {
                new BigDecimal("4.20"),
                new BigDecimal("7.35"),
                new BigDecimal("23.50"),
                new BigDecimal("3.65"),
                new BigDecimal("20.80"),
                new BigDecimal("45.95")
        };

        for (int i = 0; i < amounts.length; i++) {
            performCashOut(subscribers.get(i), agent, amounts[i], expectedBalances);
        }

        assertInsufficientCashOut(subscribers.get(0), agent, expectedBalances);
        assertAvailableWalletBalanceSumIsZero("after cash-out scenarios");
    }

    private void performCashOut(
            SubscriberUser subscriber,
            BusinessUser agent,
            BigDecimal amount,
            Map<String, BigDecimal> expectedBalances
    ) {
        Response response = postJson(
                "subscriber cash-out to agent " + subscriber.accountId(),
                "/api/v1/pay/CASHOUT",
                subscriber.accessToken(),
                cashOutRequest(subscriber, agent, amount)
        );

        String transactionId = response.then()
                .statusCode(200)
                .body("responseStatus", equalTo("SUCCESS"))
                .body("operationType", equalTo("CASHOUT"))
                .body("code", equalTo("PAYMENT_SUCCESS"))
                .body("currency", equalTo("USD"))
                .body("transactionId", notNullValue())
                .extract()
                .path("transactionId");

        expectedBalances.put(subscriber.accountId(), expectedBalances.get(subscriber.accountId()).subtract(amount));
        expectedBalances.put(agent.accountId(), expectedBalances.get(agent.accountId()).add(amount));

        assertApprovedFinancialTransferTransaction(transactionId, "CASHOUT", subscriber.accountId(), agent.accountId(), amount);
        assertMainUsdBalanceFromEnquiry(subscriber, expectedBalances.get(subscriber.accountId()));
        assertMainUsdBalanceFromEnquiry(agent, expectedBalances.get(agent.accountId()));
    }

    private void assertInsufficientCashOut(
            SubscriberUser subscriber,
            BusinessUser agent,
            Map<String, BigDecimal> expectedBalances
    ) {
        BigDecimal amount = expectedBalances.get(subscriber.accountId()).add(new BigDecimal("1000.00"));
        postJson(
                "insufficient balance cash-out from subscriber " + subscriber.accountId(),
                "/api/v1/pay/CASHOUT",
                subscriber.accessToken(),
                cashOutRequest(subscriber, agent, amount)
        ).then()
                .statusCode(400)
                .body("responseStatus", equalTo("FAILURE"))
                .body("operationType", equalTo("CASHOUT"))
                .body("code", equalTo(ErrorCodes.INSUFFICIENT_BALANCE));

        assertMainUsdBalanceFromEnquiry(subscriber, expectedBalances.get(subscriber.accountId()));
        assertMainUsdBalanceFromEnquiry(agent, expectedBalances.get(agent.accountId()));
        assertAvailableWalletBalanceSumIsZero("after insufficient cash-out attempt");
    }

    private void runMerchantAndBillPaymentScenarios(
            List<SubscriberUser> subscribers,
            BusinessUser merchant,
            BusinessUser biller,
            Map<String, BigDecimal> expectedBalances
    ) {
        BigDecimal[] merchantAmounts = {
                new BigDecimal("6.15"),
                new BigDecimal("8.20"),
                new BigDecimal("0.25"),
                new BigDecimal("10.30"),
                new BigDecimal("16.35")
        };
        for (int i = 0; i < merchantAmounts.length; i++) {
            performMerchantPayment(subscribers.get(i), merchant, merchantAmounts[i], expectedBalances);
        }

        BigDecimal[] billAmounts = {
                new BigDecimal("20.10"),
                new BigDecimal("90.12"),
                new BigDecimal("65.14"),
                new BigDecimal("10.16"),
                new BigDecimal("23.18")
        };
        boolean[] settlementStatuses = {true, false, true, false, true};
        for (int i = 0; i < billAmounts.length; i++) {
            performBillPaymentAndSettlement(
                    subscribers.get(i),
                    biller,
                    billAmounts[i],
                    settlementStatuses[i],
                    expectedBalances
            );
        }

        assertMainUsdBalanceFromEnquiry(merchant, expectedBalances.get(merchant.accountId()));
        assertMainUsdBalanceFromEnquiry(biller, expectedBalances.get(biller.accountId()));
        assertAvailableWalletBalanceSumIsZero("after merchant and bill payment scenarios");
    }

    private void performMerchantPayment(
            SubscriberUser subscriber,
            BusinessUser merchant,
            BigDecimal amount,
            Map<String, BigDecimal> expectedBalances
    ) {
        Response response = postJson(
                "merchant payment from subscriber " + subscriber.accountId(),
                "/api/v1/pay/MERCHANTPAY",
                subscriber.accessToken(),
                merchantPaymentRequest(subscriber, merchant, amount)
        );

        String transactionId = response.then()
                .statusCode(200)
                .body("responseStatus", equalTo("SUCCESS"))
                .body("operationType", equalTo("MERCHANTPAY"))
                .body("code", equalTo("PAYMENT_SUCCESS"))
                .body("currency", equalTo("USD"))
                .body("transactionId", notNullValue())
                .extract()
                .path("transactionId");

        expectedBalances.put(subscriber.accountId(), expectedBalances.get(subscriber.accountId()).subtract(amount));
        expectedBalances.put(merchant.accountId(), expectedBalances.get(merchant.accountId()).add(amount));

        assertApprovedFinancialTransferTransaction(transactionId, "MERCHANTPAY", subscriber.accountId(), merchant.accountId(), amount);
        assertMainUsdBalanceFromEnquiry(subscriber, expectedBalances.get(subscriber.accountId()));
        assertMainUsdBalanceFromEnquiry(merchant, expectedBalances.get(merchant.accountId()));
    }

    private void performBillPaymentAndSettlement(
            SubscriberUser subscriber,
            BusinessUser biller,
            BigDecimal amount,
            boolean settlementStatus,
            Map<String, BigDecimal> expectedBalances
    ) {
        BigDecimal subscriberBalanceBefore = readMainUsdAvailableBalanceFromEnquiry(subscriber);
        BigDecimal billerBalanceBefore = readMainUsdAvailableBalanceFromEnquiry(biller);
        Response response = postJson(
                "bill payment from subscriber " + subscriber.accountId(),
                "/api/v1/pay/BILLPAY",
                subscriber.accessToken(),
                billPaymentRequest(subscriber, biller, amount)
        );

        String transactionId = response.then()
                .statusCode(200)
                .body("responseStatus", equalTo("SUCCESS"))
                .body("operationType", equalTo("BILLPAY"))
                .body("code", equalTo("PAYMENT_SUCCESS"))
                .body("currency", equalTo("USD"))
                .body("billStatus", equalTo("PENDING"))
                .body("transactionId", notNullValue())
                .body("traceId", notNullValue())
                .extract()
                .path("transactionId");
        String transactionTraceId = response.jsonPath().getString("traceId");

        expectedBalances.put(subscriber.accountId(), expectedBalances.get(subscriber.accountId()).subtract(amount));
        expectedBalances.put(biller.accountId(), expectedBalances.get(biller.accountId()).add(amount));

        assertBillPaymentStatus(transactionId, "PENDING");
        assertFinancialTransferTransaction(transactionId, "BILLPAY", "TA", subscriber.accountId(), biller.accountId(), amount);
        assertMainUsdAvailableBalanceFromEnquiry(subscriber, expectedBalances.get(subscriber.accountId()));
        assertMainUsdAvailableBalanceFromEnquiry(biller, expectedBalances.get(biller.accountId()));

        settleBillPayment(transactionId, transactionTraceId, biller, settlementStatus);
        if (settlementStatus) {
            assertBillPaymentStatus(transactionId, "SUCCESS");
            assertFinancialTransferTransaction(transactionId, "BILLPAY", "TS", subscriber.accountId(), biller.accountId(), amount);
        } else {
            expectedBalances.put(subscriber.accountId(), expectedBalances.get(subscriber.accountId()).add(amount));
            expectedBalances.put(biller.accountId(), expectedBalances.get(biller.accountId()).subtract(amount));
            assertBillPaymentStatus(transactionId, "FAILED");
            assertFinancialTransferTransaction(transactionId, "BILLPAY", "TF", subscriber.accountId(), biller.accountId(), amount);
            assertBigDecimalEquals(
                    subscriberBalanceBefore,
                    readMainUsdAvailableBalanceFromEnquiry(subscriber),
                    "subscriber MAIN USD available balance after failed bill settlement rollback"
            );
            assertBigDecimalEquals(
                    billerBalanceBefore,
                    readMainUsdAvailableBalanceFromEnquiry(biller),
                    "biller MAIN USD available balance after failed bill settlement rollback"
            );
        }

        assertMainUsdBalanceFromEnquiry(subscriber, expectedBalances.get(subscriber.accountId()));
        assertMainUsdBalanceFromEnquiry(biller, expectedBalances.get(biller.accountId()));
    }

    private void settleBillPayment(
            String transactionId,
            String transactionTraceId,
            BusinessUser biller,
            boolean settlementStatus
    ) {
        postJson(
                (settlementStatus ? "successful" : "failed") + " settlement for bill payment " + transactionId,
                "/api/v1/internal/settletxn",
                biller.accessToken(),
                settleTransactionRequest(transactionTraceId, settlementStatus)
        ).then()
                .statusCode(200)
                .body("responseStatus", equalTo("SUCCESS"))
                .body("operationType", equalTo("SETTLETXN"))
                .body("code", equalTo(settlementStatus ? "SETTLEMENT_SUCCESS" : "ROLLBACK_SUCCESS"))
                .body("transactionId", equalTo(transactionId))
                .body("transactionTraceId", equalTo(transactionTraceId))
                .body("serviceCode", equalTo("BILLPAY"))
                .body("transferStatus", equalTo(settlementStatus ? "TS" : "TF"));
    }

    private String readLatestCreatedRegistrationOtp(String mobile) {
        Integer otpValue = jdbcTemplate.queryForObject("""
                SELECT otp_value
                FROM %s
                WHERE mobile_number = ?
                  AND reference_type = 'REGISTRATION'
                  AND status = 'CREATED'
                ORDER BY created_at DESC
                LIMIT 1
                """.formatted(tenantTable("otp")), Integer.class, mobile);

        if (otpValue == null) {
            throw new AssertionError("Expected generated OTP for mobile " + mobile);
        }
        log.info("E2E OTP loaded from DB for mobile={}", maskMobile(mobile));
        return String.valueOf(otpValue);
    }

    private BusinessUser createBusinessUserChangePasswordAndLogin(
            String creatorToken,
            String accountType,
            String roleCode,
            String loginPrefix
    ) {
        String uniqueSuffix = uniqueSuffix();
        String loginId = loginPrefix + uniqueSuffix;
        String mobile = switch (accountType) {
            case "AGENT" -> mobileNumber("810", uniqueSuffix);
            case "MERCHANT" -> mobileNumber("820", uniqueSuffix);
            case "BILLER" -> mobileNumber("830", uniqueSuffix);
            default -> mobileNumber("840", uniqueSuffix);
        };
        String updatedPassword = BUSINESS_USER_UPDATED_PASSWORD + accountType.charAt(0);
        String firstName = roleCode.substring(0, 1) + roleCode.substring(1).toLowerCase();
        String email = loginPrefix + ".e2e@example.com";

        String registerRequest = """
                {
                  "requestId": "req-e2e-%s-register",
                  "user": {
                    "mobileNumber": "%s",
                    "accountType": "%s",
                    "firstName": "%s",
                    "lastName": "User",
                    "email": "%s",
                    "address": "300 PayNest Business Street",
                    "gender": "MALE",
                    "dateOfBirth": "1992-07-21",
                    "preferredLang": "en",
                    "nationality": "USA",
                    "ssn": "222-33-4444",
                    "remarks": "E2E %s user",
                    "loginId": "%s",
                    "role": "%s"
                  }
                }
                """.formatted(
                loginPrefix,
                mobile,
                accountType,
                firstName,
                email,
                accountType,
                loginId,
                roleCode
        );

        Response registerResponse = postJson(
                "create " + accountType + " using networkadmin JWT",
                "/api/v1/account/registerUser",
                creatorToken,
                registerRequest
        );
        String accountId = registerResponse.then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("requestId", equalTo("req-e2e-" + loginPrefix + "-register"))
                .body("message", equalTo("User registered successfully"))
                .body("accountId", notNullValue())
                .extract()
                .path("accountId");

        String firstLoginRequest = loginRequest(
                "req-e2e-" + loginPrefix + "-login-before-change",
                loginId,
                BUSINESS_USER_DEFAULT_PASSWORD
        );
        Response firstLoginResponse = postJson(
                accountType + " first login should require credential change",
                "/api/v1/auth/login",
                firstLoginRequest
        );
        firstLoginResponse.then()
                .statusCode(400)
                .body("code", equalTo(ErrorCodes.FORCE_AUTH_CHANGE));

        String changePasswordRequest = changePasswordRequest(
                "req-e2e-" + loginPrefix + "-change-default-password",
                loginId,
                BUSINESS_USER_DEFAULT_PASSWORD,
                updatedPassword
        );
        Response changePasswordResponse = postJson(
                accountType + " changes default password",
                "/api/v1/account/password/changeDefault",
                changePasswordRequest
        );
        changePasswordResponse.then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("message", equalTo("Password changed successfully"));

        String loginAfterChangeRequest = loginRequest(
                "req-e2e-" + loginPrefix + "-login-after-change",
                loginId,
                updatedPassword
        );
        Response loginResponse = postJson(
                accountType + " login after password change",
                "/api/v1/auth/login",
                loginAfterChangeRequest
        );
        String accessToken = loginResponse.then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("message", equalTo("Login successful"))
                .body("accountId", equalTo(accountId))
                .body("tokenType", equalTo("Bearer"))
                .body("accessToken", notNullValue())
                .extract()
                .path("accessToken");

        assertJwt(accessToken, accountId, "PASSWORD", accountType);
        assertUserRole(accountId, roleCode);
        assertBusinessAccount(accountId, accountType, mobile, loginId, firstName, "User", email);
        return new BusinessUser(accountId, accountType, mobile, accessToken, updatedPassword);
    }

    private String loginWithPassword(
            String requestId,
            String loginId,
            String password,
            String expectedAccountId,
            String expectedAccountType
    ) {
        Response loginResponse = postJson(
                "login with password for " + loginId,
                "/api/v1/auth/login",
                loginRequest(requestId, loginId, password)
        );
        String accessToken = loginResponse.then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("message", equalTo("Login successful"))
                .body("accountId", equalTo(expectedAccountId))
                .body("tokenType", equalTo("Bearer"))
                .body("accessToken", notNullValue())
                .extract()
                .path("accessToken");
        assertJwt(accessToken, expectedAccountId, "PASSWORD", expectedAccountType);
        return accessToken;
    }

    private String loginWithPin(
            String requestId,
            String mobile,
            String pin,
            String expectedAccountId,
            String expectedAccountType
    ) {
        String loginRequest = """
                {
                  "requestId": "%s",
                  "user": {
                    "identifierType": "MOBILE",
                    "identifierValue": "%s"
                  },
                  "authFactor": {
                    "authType": "PIN",
                    "credential": "%s"
                  }
                }
                """.formatted(requestId, mobile, pin);
        Response loginResponse = postJson(
                "login with PIN for " + mobile,
                "/api/v1/auth/login",
                loginRequest
        );
        String accessToken = loginResponse.then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("message", equalTo("Login successful"))
                .body("accountId", equalTo(expectedAccountId))
                .body("tokenType", equalTo("Bearer"))
                .body("accessToken", notNullValue())
                .extract()
                .path("accessToken");
        assertJwt(accessToken, expectedAccountId, "PIN", expectedAccountType);
        return accessToken;
    }

    private String uniqueSuffix() {
        return Long.toUnsignedString(System.nanoTime());
    }

    private void delayOneSecondBetweenBulkUsers() {
        try {
            Thread.sleep(1_000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting between bulk user creations", ex);
        }
    }

    private String mobileNumber(String prefix, String uniqueSuffix) {
        int start = Math.max(0, uniqueSuffix.length() - 8);
        return prefix + uniqueSuffix.substring(start);
    }

    private String maskMobile(String mobile) {
        if (mobile == null || mobile.length() <= 4) {
            return mobile;
        }
        return "****" + mobile.substring(mobile.length() - 4);
    }

    private String loginRequest(String requestId, String loginId, String password) {
        return """
                {
                  "requestId": "%s",
                  "user": {
                    "identifierType": "LOGINID",
                    "identifierValue": "%s"
                  },
                  "authFactor": {
                    "authType": "PASSWORD",
                    "credential": "%s"
                  }
                }
                """.formatted(requestId, loginId, password);
    }

    private String changePasswordRequest(String requestId, String loginId, String oldPassword, String newPassword) {
        return """
                {
                  "requestId": "%s",
                  "user": {
                    "identifierType": "LOGINID",
                    "identifierValue": "%s"
                  },
                  "authFactorOld": {
                    "authType": "PASSWORD",
                    "credential": "%s"
                  },
                  "authFactorNew": {
                    "authType": "PASSWORD",
                    "credential": "%s"
                  }
                }
                """.formatted(requestId, loginId, oldPassword, newPassword);
    }

    private String stockInitiateRequest(String requestId, String currency, String amount) {
        return """
                {
                  "operationType": "STOCK",
                  "paymentReference": "%s",
                  "comments": "E2E stock initiation for %s wallet",
                  "transaction": {
                    "amount": %s,
                    "currency": "%s"
                  },
                  "metadata": {
                    "scenario": "same-initiator-approval-block"
                  },
                  "additionalInfo": {
                    "channel": "real-api-e2e"
                  }
                }
                """.formatted(requestId, currency, amount, currency);
    }

    private String stockApprovalRequest(
            String transactionId,
            String status,
            String errorCode,
            String comments
    ) {
        String errorCodeField = errorCode == null || errorCode.isBlank()
                ? ""
                : """
                          "errorCode": "%s",
                """.formatted(errorCode);
        return """
                        {
                          "transactionId": "%s",
                          "status": "%s",
                %s          "comments": "%s"
                        }
                """.formatted(transactionId, status, errorCodeField, comments);
    }

    private void performApprovedO2CTransaction(
            String initiatingAdminToken,
            String approvingAdminToken,
            BusinessUser channelUser,
            String amount
    ) {
        performApprovedO2CTransaction(initiatingAdminToken, approvingAdminToken, channelUser, amount, "USD");
    }

    private void performApprovedO2CTransaction(
            String initiatingAdminToken,
            String approvingAdminToken,
            BusinessUser channelUser,
            String amount,
            String currency
    ) {
        Response initiateO2CResponse = postJson(
                "initiate " + currency + " O2C for " + channelUser.accountType(),
                "/api/v1/pay/o2c/initiate",
                initiatingAdminToken,
                o2cInitiateRequest(channelUser, amount, currency)
        );
        String transactionId = initiateO2CResponse.then()
                .statusCode(200)
                .body("responseStatus", equalTo("PENDING"))
                .body("operationType", equalTo("O2C"))
                .body("code", equalTo("O2C_INITIATED"))
                .body("transactionId", notNullValue())
                .body("currency", equalTo(currency))
                .extract()
                .path("transactionId");

        postJson(
                "same networkadmin cannot approve O2C for " + channelUser.accountType(),
                "/api/v1/pay/o2c/status",
                initiatingAdminToken,
                o2cApprovalRequest(transactionId, "APPROVED", "same approver should fail")
        ).then()
                .statusCode(400)
                .body("responseStatus", equalTo("FAILURE"))
                .body("operationType", equalTo("O2C_STATUS"))
                .body("code", equalTo(ErrorCodes.INVALID_INITIATOR));

        postJson(
                "approve O2C for " + channelUser.accountType(),
                "/api/v1/pay/o2c/status",
                approvingAdminToken,
                o2cApprovalRequest(transactionId, "APPROVED", "approved O2C for " + channelUser.accountType())
        ).then()
                .statusCode(200)
                .body("responseStatus", equalTo("SUCCESS"))
                .body("operationType", equalTo("O2C"))
                .body("code", equalTo("O2C_APPROVED"))
                .body("transactionId", equalTo(transactionId))
                .body("currency", equalTo(currency));

        assertApprovedO2CTransaction(transactionId, channelUser, new BigDecimal(amount), currency);
        assertMainCurrencyBalanceFromEnquiry(channelUser, currency, new BigDecimal(amount));
    }

    private String o2cInitiateRequest(BusinessUser channelUser, String amount) {
        return o2cInitiateRequest(channelUser, amount, "USD");
    }

    private String o2cInitiateRequest(BusinessUser channelUser, String amount, String currency) {
        return """
                {
                  "requestGateway": "WEB",
                  "preferredLang": "en",
                  "paymentReference": "e2e-o2c-%s-%s",
                  "comments": "E2E O2C for %s",
                  "channel": {
                    "accountType": "%s",
                    "walletType": "MAIN",
                    "identifier": {
                      "type": "MOBILE",
                      "value": "%s"
                    }
                  },
                  "transaction": {
                    "amount": %s,
                    "currency": "%s"
                  },
                  "metadata": {
                    "scenario": "e2e-o2c"
                  }
                }
                """.formatted(
                channelUser.accountType().toLowerCase(),
                channelUser.accountId(),
                channelUser.accountType(),
                channelUser.accountType(),
                channelUser.mobile(),
                amount,
                currency
        );
    }

    private String o2cApprovalRequest(String transactionId, String status, String comments) {
        return """
                {
                  "transactionId": "%s",
                  "status": "%s",
                  "comments": "%s"
                }
                """.formatted(transactionId, status, comments);
    }

    private String cashInRequest(BusinessUser agent, SubscriberUser subscriber, BigDecimal amount) {
        return cashInRequest(agent, subscriber, amount, "USD");
    }

    private String cashInRequest(BusinessUser agent, SubscriberUser subscriber, BigDecimal amount, String currency) {
        return """
                {
                  "operationType": "CASHIN",
                  "requestGateway": "MOBILE",
                  "preferredLang": "en",
                  "initiatedBy": "DEBITOR",
                  "paymentReference": "e2e-cashin-%s-%s",
                  "comments": "E2E cash-in from agent to subscriber",
                  "debitor": {
                    "accountType": "AGENT",
                    "walletType": "MAIN",
                    "identifier": {
                      "type": "MOBILE",
                      "value": "%s"
                    },
                    "authentication": {
                      "type": "PASSWORD",
                      "value": "%s"
                    }
                  },
                  "creditor": {
                    "accountType": "SUBSCRIBER",
                    "walletType": "MAIN",
                    "identifier": {
                      "type": "MOBILE",
                      "value": "%s"
                    }
                  },
                  "transaction": {
                    "amount": %s,
                    "currency": "%s"
                  },
                  "metadata": {
                    "scenario": "e2e-agent-cashin"
                  }
                }
                """.formatted(
                agent.accountId(),
                subscriber.accountId(),
                agent.mobile(),
                agent.password(),
                subscriber.mobile(),
                amount.toPlainString(),
                currency
        );
    }

    private String intraWalletTransferRequest(
            SubscriberUser subscriber,
            BigDecimal amount,
            String sourceCurrency,
            String targetCurrency
    ) {
        return intraWalletTransferRequest(subscriber, amount, "MAIN", "MAIN", sourceCurrency, targetCurrency);
    }

    private String intraWalletTransferRequest(
            SubscriberUser subscriber,
            BigDecimal amount,
            String sourceWalletType,
            String targetWalletType,
            String sourceCurrency,
            String targetCurrency
    ) {
        return """
                {
                  "requestGateway": "MOBILE",
                  "preferredLang": "en",
                  "paymentReference": "e2e-intrawallet-%s-%s-%s",
                  "comments": "E2E intra-wallet currency transfer",
                  "sourceWalletType": "%s",
                  "targetWalletType": "%s",
                  "party": {
                    "accountType": "SUBSCRIBER",
                    "walletType": "%s",
                    "identifier": {
                      "type": "MOBILE",
                      "value": "%s"
                    },
                    "authentication": {
                      "type": "PIN",
                      "value": "%s"
                    }
                  },
                  "amount": %s,
                  "sourceCurrency": "%s",
                  "targetCurrency": "%s",
                  "metadata": {
                    "scenario": "e2e-intrawallet"
                  }
                }
                """.formatted(
                subscriber.accountId(),
                sourceCurrency.toLowerCase(Locale.ROOT),
                targetCurrency.toLowerCase(Locale.ROOT),
                sourceWalletType,
                targetWalletType,
                sourceWalletType,
                subscriber.mobile(),
                subscriber.pin(),
                amount.toPlainString(),
                sourceCurrency,
                targetCurrency
        );
    }

    private String cashOutRequest(SubscriberUser subscriber, BusinessUser agent, BigDecimal amount) {
        return """
                {
                  "operationType": "CASHOUT",
                  "requestGateway": "MOBILE",
                  "preferredLang": "en",
                  "initiatedBy": "DEBITOR",
                  "paymentReference": "e2e-cashout-%s-%s",
                  "comments": "E2E cash-out from subscriber to agent",
                  "debitor": {
                    "accountType": "SUBSCRIBER",
                    "walletType": "MAIN",
                    "identifier": {
                      "type": "MOBILE",
                      "value": "%s"
                    },
                    "authentication": {
                      "type": "PIN",
                      "value": "%s"
                    }
                  },
                  "creditor": {
                    "accountType": "AGENT",
                    "walletType": "MAIN",
                    "identifier": {
                      "type": "MSISDN",
                      "value": "%s"
                    }
                  },
                  "transaction": {
                    "amount": %s,
                    "currency": "USD"
                  },
                  "metadata": {
                    "scenario": "e2e-subscriber-cashout"
                  }
                }
                """.formatted(
                subscriber.accountId(),
                agent.accountId(),
                subscriber.mobile(),
                subscriber.pin(),
                agent.mobile(),
                amount.toPlainString()
        );
    }

    private String u2uRequest(U2UParticipant sender, U2UParticipant receiver, BigDecimal amount) {
        return """
                {
                  "operationType": "U2U",
                  "requestGateway": "MOBILE",
                  "preferredLang": "en",
                  "initiatedBy": "DEBITOR",
                  "paymentReference": "e2e-u2u-%s-%s",
                  "comments": "E2E U2U transfer",
                  "debitor": {
                    "accountType": "%s",
                    "walletType": "MAIN",
                    "identifier": {
                      "type": "MOBILE",
                      "value": "%s"
                    },
                    "authentication": {
                      "type": "%s",
                      "value": "%s"
                    }
                  },
                  "creditor": {
                    "accountType": "%s",
                    "walletType": "MAIN",
                    "identifier": {
                      "type": "MOBILE",
                      "value": "%s"
                    }
                  },
                  "transaction": {
                    "amount": %s,
                    "currency": "USD"
                  },
                  "metadata": {
                    "scenario": "e2e-u2u"
                  }
                }
                """.formatted(
                sender.accountId(),
                receiver.accountId(),
                sender.accountType(),
                sender.mobile(),
                sender.authType(),
                sender.authValue(),
                receiver.accountType(),
                receiver.mobile(),
                amount.toPlainString()
        );
    }

    private String merchantPaymentRequest(SubscriberUser subscriber, BusinessUser merchant, BigDecimal amount) {
        return """
                {
                  "operationType": "MERCHANTPAY",
                  "requestGateway": "MOBILE",
                  "preferredLang": "en",
                  "initiatedBy": "DEBITOR",
                  "paymentReference": "e2e-merchantpay-%s-%s",
                  "comments": "E2E merchant payment",
                  "debitor": {
                    "accountType": "SUBSCRIBER",
                    "walletType": "MAIN",
                    "identifier": {
                      "type": "MOBILE",
                      "value": "%s"
                    },
                    "authentication": {
                      "type": "PIN",
                      "value": "%s"
                    }
                  },
                  "creditor": {
                    "accountType": "MERCHANT",
                    "walletType": "MAIN",
                    "identifier": {
                      "type": "MSISDN",
                      "value": "%s"
                    }
                  },
                  "transaction": {
                    "amount": %s,
                    "currency": "USD"
                  },
                  "metadata": {
                    "scenario": "e2e-merchantpay"
                  }
                }
                """.formatted(
                subscriber.accountId(),
                merchant.accountId(),
                subscriber.mobile(),
                subscriber.pin(),
                merchant.mobile(),
                amount.toPlainString()
        );
    }

    private String billPaymentRequest(SubscriberUser subscriber, BusinessUser biller, BigDecimal amount) {
        return """
                {
                  "operationType": "BILLPAY",
                  "requestGateway": "MOBILE",
                  "preferredLang": "en",
                  "initiatedBy": "DEBITOR",
                  "paymentReference": "e2e-billpay-%s-%s",
                  "comments": "E2E bill payment",
                  "debitor": {
                    "accountType": "SUBSCRIBER",
                    "walletType": "MAIN",
                    "identifier": {
                      "type": "MOBILE",
                      "value": "%s"
                    },
                    "authentication": {
                      "type": "PIN",
                      "value": "%s"
                    }
                  },
                  "creditor": {
                    "accountType": "BILLER",
                    "walletType": "MAIN",
                    "identifier": {
                      "type": "MSISDN",
                      "value": "%s"
                    }
                  },
                  "transaction": {
                    "amount": %s,
                    "currency": "USD"
                  },
                  "metadata": {
                    "scenario": "e2e-billpay"
                  }
                }
                """.formatted(
                subscriber.accountId(),
                biller.accountId(),
                subscriber.mobile(),
                subscriber.pin(),
                biller.mobile(),
                amount.toPlainString()
        );
    }

    private String settleTransactionRequest(String traceId, boolean settlementStatus) {
        return """
                {
                  "traceId": "%s",
                  "settlementStatus": %s,
                  "comments": "%s bill settlement from E2E",
                  "additionalInfo": {
                    "scenario": "e2e-bill-settlement"
                  }
                }
                """.formatted(
                traceId,
                settlementStatus,
                settlementStatus ? "successful" : "failed"
        );
    }

    private void assertUserRole(String accountId, String roleCode) {
        Integer roleCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM %s ur
                JOIN %s r ON r.role_id = ur.role_id
                WHERE ur.user_id = ?
                  AND r.role_code = ?
                """.formatted(tenantTable("user_roles"), tenantTable("roles")), Integer.class, accountId, roleCode);
        assertEquals(1, roleCount);
    }

    private void assertStockTransactionInitiator(String transactionId, String initiatingAccountId) {
        Map<String, Object> transaction = jdbcTemplate.queryForMap("""
                SELECT transaction_id,
                       service_code,
                       transfer_status,
                       created_by
                FROM %s
                WHERE transaction_id = ?
                """.formatted(tenantTable("transactions")), transactionId);

        assertEquals(transactionId, transaction.get("transaction_id"));
        assertEquals("STOCK", transaction.get("service_code"));
        assertEquals("TI", transaction.get("transfer_status"));
        assertEquals(initiatingAccountId, transaction.get("created_by"));
    }

    private void assertApprovedStockTransaction(
            String transactionId,
            String initiatingAccountId,
            String approvingAccountId
    ) {
        assertApprovedStockTransaction(transactionId, initiatingAccountId, approvingAccountId, "USD");
    }

    private void assertApprovedStockTransaction(
            String transactionId,
            String initiatingAccountId,
            String approvingAccountId,
            String currency
    ) {
        Map<String, Object> transaction = jdbcTemplate.queryForMap("""
                SELECT transaction_id,
                       service_code,
                       transfer_status,
                       created_by,
                       modified_by,
                       transaction_value
                FROM %s
                WHERE transaction_id = ?
                """.formatted(tenantTable("transactions")), transactionId);

        assertEquals(transactionId, transaction.get("transaction_id"));
        assertEquals("STOCK", transaction.get("service_code"));
        assertEquals("TS", transaction.get("transfer_status"));
        assertEquals(initiatingAccountId, transaction.get("created_by"));
        assertEquals(approvingAccountId, transaction.get("modified_by"));

        BigDecimal transactionValue = toBigDecimal(transaction.get("transaction_value"));
        List<Map<String, Object>> details = jdbcTemplate.queryForList("""
                SELECT td.txn_sequence_number,
                       td.entry_type,
                       td.transfer_status,
                       td.transaction_value,
                       td.approved_value,
                       td.previous_balance,
                       td.post_balance,
                       td.previous_fic_balance,
                       td.post_fic_balance,
                       td.previous_frozen_balance,
                       td.post_frozen_balance,
                       w.currency,
                       wb.available_balance,
                       wb.fic_balance,
                       wb.frozen_balance
                FROM %s td
                JOIN %s w ON w.wallet_id = CAST(td.wallet_number AS BIGINT)
                JOIN %s wb ON wb.wallet_id = w.wallet_id
                WHERE td.transaction_id = ?
                ORDER BY td.txn_sequence_number
                """.formatted(
                tenantTable("transaction_details"),
                tenantTable("wallet"),
                tenantTable("wallet_balance")
        ), transactionId);
        assertEquals(2, details.size());

        assertApprovedBalanceMovementDetail(details.get(0), "STOCK", "DR", currency, transactionValue, transactionValue.negate());
        assertApprovedBalanceMovementDetail(details.get(1), "STOCK", "CR", currency, transactionValue, transactionValue);
    }

    private void assertApprovedO2CTransaction(
            String transactionId,
            BusinessUser channelUser,
            BigDecimal displayAmount
    ) {
        assertApprovedO2CTransaction(transactionId, channelUser, displayAmount, "USD");
    }

    private void assertApprovedO2CTransaction(
            String transactionId,
            BusinessUser channelUser,
            BigDecimal displayAmount,
            String currency
    ) {
        Map<String, Object> transaction = jdbcTemplate.queryForMap("""
                SELECT transaction_id,
                       service_code,
                       transfer_status,
                       transaction_value
                FROM %s
                WHERE transaction_id = ?
                """.formatted(tenantTable("transactions")), transactionId);

        assertEquals(transactionId, transaction.get("transaction_id"));
        assertEquals("O2C", transaction.get("service_code"));
        assertEquals("TS", transaction.get("transfer_status"));

        BigDecimal expectedStoredAmount = displayAmount.multiply(new BigDecimal("100.00"));
        BigDecimal transactionValue = toBigDecimal(transaction.get("transaction_value"));
        assertBigDecimalEquals(expectedStoredAmount, transactionValue, "O2C transaction value");

        List<Map<String, Object>> details = jdbcTemplate.queryForList("""
                SELECT td.txn_sequence_number,
                       td.account_id,
                       td.entry_type,
                       td.transfer_status,
                       td.transaction_value,
                       td.approved_value,
                       td.previous_balance,
                       td.post_balance,
                       td.previous_fic_balance,
                       td.post_fic_balance,
                       td.previous_frozen_balance,
                       td.post_frozen_balance,
                       w.currency,
                       wb.available_balance,
                       wb.fic_balance,
                       wb.frozen_balance
                FROM %s td
                JOIN %s w ON w.wallet_id = CAST(td.wallet_number AS BIGINT)
                JOIN %s wb ON wb.wallet_id = w.wallet_id
                WHERE td.transaction_id = ?
                ORDER BY td.txn_sequence_number
                """.formatted(
                tenantTable("transaction_details"),
                tenantTable("wallet"),
                tenantTable("wallet_balance")
        ), transactionId);
        assertEquals(2, details.size());
        assertEquals(channelUser.accountId(), details.get(1).get("account_id"));

        assertApprovedBalanceMovementDetail(details.get(0), "O2C", "DR", currency, transactionValue, transactionValue.negate());
        assertApprovedBalanceMovementDetail(details.get(1), "O2C", "CR", currency, transactionValue, transactionValue);
    }

    private void assertApprovedU2UTransaction(
            String transactionId,
            U2UParticipant sender,
            U2UParticipant receiver,
            BigDecimal displayAmount
    ) {
        assertApprovedFinancialTransferTransaction(
                transactionId,
                "U2U",
                sender.accountId(),
                receiver.accountId(),
                displayAmount
        );
    }

    private void assertApprovedFinancialTransferTransaction(
            String transactionId,
            String operationType,
            String debitorAccountId,
            String creditorAccountId,
            BigDecimal displayAmount
    ) {
        assertApprovedFinancialTransferTransaction(
                transactionId,
                operationType,
                debitorAccountId,
                creditorAccountId,
                displayAmount,
                "USD"
        );
    }

    private void assertApprovedFinancialTransferTransaction(
            String transactionId,
            String operationType,
            String debitorAccountId,
            String creditorAccountId,
            BigDecimal displayAmount,
            String currency
    ) {
        assertFinancialTransferTransaction(
                transactionId,
                operationType,
                "TS",
                debitorAccountId,
                creditorAccountId,
                displayAmount,
                currency
        );
    }

    private void assertBillPaymentStatus(String transactionId, String expectedStatus) {
        Map<String, Object> billStatus = jdbcTemplate.queryForMap("""
                SELECT transaction_id,
                       status
                FROM %s
                WHERE transaction_id = ?
                """.formatted(tenantTable("bill_payment_status")), transactionId);

        assertEquals(transactionId, billStatus.get("transaction_id"));
        assertEquals(expectedStatus, billStatus.get("status"));
    }

    private void assertFinancialTransferTransaction(
            String transactionId,
            String operationType,
            String expectedStatus,
            String debitorAccountId,
            String creditorAccountId,
            BigDecimal displayAmount
    ) {
        assertFinancialTransferTransaction(
                transactionId,
                operationType,
                expectedStatus,
                debitorAccountId,
                creditorAccountId,
                displayAmount,
                "USD"
        );
    }

    private void assertFinancialTransferTransaction(
            String transactionId,
            String operationType,
            String expectedStatus,
            String debitorAccountId,
            String creditorAccountId,
            BigDecimal displayAmount,
            String currency
    ) {
        Map<String, Object> transaction = jdbcTemplate.queryForMap("""
                SELECT transaction_id,
                       service_code,
                       transfer_status,
                       transaction_value
                FROM %s
                WHERE transaction_id = ?
                """.formatted(tenantTable("transactions")), transactionId);

        assertEquals(transactionId, transaction.get("transaction_id"));
        assertEquals(operationType, transaction.get("service_code"));
        assertEquals(expectedStatus, transaction.get("transfer_status"));

        BigDecimal expectedStoredAmount = displayAmount.multiply(new BigDecimal("100.00"));
        BigDecimal transactionValue = toBigDecimal(transaction.get("transaction_value"));
        assertBigDecimalEquals(expectedStoredAmount, transactionValue, operationType + " transaction value");

        List<Map<String, Object>> details = jdbcTemplate.queryForList("""
                SELECT td.txn_sequence_number,
                       td.account_id,
                       td.entry_type,
                       td.transfer_status,
                       td.transaction_value,
                       td.approved_value,
                       td.previous_balance,
                       td.post_balance,
                       td.previous_fic_balance,
                       td.post_fic_balance,
                       td.previous_frozen_balance,
                       td.post_frozen_balance,
                       w.currency,
                       wb.available_balance,
                       wb.fic_balance,
                       wb.frozen_balance
                FROM %s td
                JOIN %s w ON w.wallet_id = CAST(td.wallet_number AS BIGINT)
                JOIN %s wb ON wb.wallet_id = w.wallet_id
                WHERE td.transaction_id = ?
                ORDER BY td.txn_sequence_number
                """.formatted(
                tenantTable("transaction_details"),
                tenantTable("wallet"),
                tenantTable("wallet_balance")
        ), transactionId);
        assertEquals(2, details.size());
        assertEquals(debitorAccountId, details.get(0).get("account_id"));
        assertEquals(creditorAccountId, details.get(1).get("account_id"));

        BigDecimal debitorExpectedDelta = "TF".equals(expectedStatus) ? BigDecimal.ZERO : transactionValue.negate();
        BigDecimal creditorExpectedDelta = "TF".equals(expectedStatus) ? BigDecimal.ZERO : transactionValue;
        assertBalanceMovementDetail(details.get(0), operationType, expectedStatus, "DR", currency, transactionValue, debitorExpectedDelta);
        assertBalanceMovementDetail(details.get(1), operationType, expectedStatus, "CR", currency, transactionValue, creditorExpectedDelta);
    }

    private void assertApprovedBalanceMovementDetail(
            Map<String, Object> detail,
            String operationType,
            String expectedEntryType,
            String expectedCurrency,
            BigDecimal expectedAmount,
            BigDecimal expectedAvailableDelta
    ) {
        assertBalanceMovementDetail(detail, operationType, "TS", expectedEntryType, expectedCurrency, expectedAmount, expectedAvailableDelta);
    }

    private void assertBalanceMovementDetail(
            Map<String, Object> detail,
            String operationType,
            String expectedStatus,
            String expectedEntryType,
            String expectedCurrency,
            BigDecimal expectedAmount,
            BigDecimal expectedAvailableDelta
    ) {
        assertEquals(expectedEntryType, detail.get("entry_type"));
        assertEquals(expectedStatus, detail.get("transfer_status"));
        assertEquals(expectedCurrency, detail.get("currency"));
        assertBigDecimalEquals(expectedAmount, toBigDecimal(detail.get("transaction_value")), "transaction value");
        assertBigDecimalEquals(expectedAmount, toBigDecimal(detail.get("approved_value")), "approved value");

        BigDecimal previousBalance = toBigDecimal(detail.get("previous_balance"));
        BigDecimal postBalance = toBigDecimal(detail.get("post_balance"));
        assertBigDecimalEquals(
                previousBalance.add(expectedAvailableDelta),
                postBalance,
                operationType + " " + expectedEntryType + " available balance movement"
        );

        BigDecimal previousFicBalance = toBigDecimal(detail.get("previous_fic_balance"));
        BigDecimal expectedPostFicBalance = previousFicBalance;
        if ("BILLPAY".equals(operationType) && "TA".equals(expectedStatus) && "CR".equals(expectedEntryType)) {
            expectedPostFicBalance = previousFicBalance.add(expectedAmount);
        }
        assertBigDecimalEquals(
                expectedPostFicBalance,
                toBigDecimal(detail.get("post_fic_balance")),
                operationType + " " + expectedEntryType + " FIC balance movement"
        );
        assertBigDecimalEquals(
                toBigDecimal(detail.get("previous_frozen_balance")),
                toBigDecimal(detail.get("post_frozen_balance")),
                operationType + " " + expectedEntryType + " frozen balance movement"
        );
        assertBigDecimalEquals(
                postBalance,
                toBigDecimal(detail.get("available_balance")),
                operationType + " " + expectedEntryType + " wallet available balance"
        );
        assertBigDecimalEquals(
                expectedPostFicBalance,
                toBigDecimal(detail.get("fic_balance")),
                operationType + " " + expectedEntryType + " wallet FIC balance"
        );
        assertBigDecimalEquals(
                toBigDecimal(detail.get("post_frozen_balance")),
                toBigDecimal(detail.get("frozen_balance")),
                operationType + " " + expectedEntryType + " wallet frozen balance"
        );
    }

    private void assertMainUsdBalanceFromEnquiry(BusinessUser user, BigDecimal expectedDisplayBalance) {
        assertMainUsdBalanceFromEnquiry(
                user.accountId(),
                user.accountType(),
                user.accessToken(),
                expectedDisplayBalance
        );
    }

    private void assertMainUsdBalanceFromEnquiry(SubscriberUser user, BigDecimal expectedDisplayBalance) {
        assertMainUsdBalanceFromEnquiry(
                user.accountId(),
                "SUBSCRIBER",
                user.accessToken(),
                expectedDisplayBalance
        );
    }

    private void assertMainUsdAvailableBalanceFromEnquiry(SubscriberUser user, BigDecimal expectedDisplayBalance) {
        assertMainUsdAvailableBalanceFromEnquiry(
                user.accountId(),
                "SUBSCRIBER",
                user.accessToken(),
                expectedDisplayBalance
        );
    }

    private void assertMainUsdAvailableBalanceFromEnquiry(BusinessUser user, BigDecimal expectedDisplayBalance) {
        assertMainUsdAvailableBalanceFromEnquiry(
                user.accountId(),
                user.accountType(),
                user.accessToken(),
                expectedDisplayBalance
        );
    }

    private BigDecimal readMainUsdAvailableBalanceFromEnquiry(SubscriberUser user) {
        return readMainUsdAvailableBalanceFromEnquiry(
                user.accountId(),
                "SUBSCRIBER",
                user.accessToken()
        );
    }

    private BigDecimal readMainUsdAvailableBalanceFromEnquiry(BusinessUser user) {
        return readMainUsdAvailableBalanceFromEnquiry(
                user.accountId(),
                user.accountType(),
                user.accessToken()
        );
    }

    private void assertDefaultWalletsFromEnquiry(SubscriberUser user) {
        Response walletResponse = getJson(
                "fetch default wallets for subscriber",
                "/api/v1/wallet/getAccountWallets/{accountId}",
                user.accessToken(),
                user.accountId()
        );
        walletResponse.then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("message", equalTo("Wallets fetched successfully"))
                .body("wallets.accountId", equalTo(user.accountId()))
                .body("wallets.balances.find { it.walletType == 'MAIN' && it.currency == 'USD' }", notNullValue())
                .body("wallets.balances.find { it.walletType == 'MAIN' && it.currency == 'INR' }", notNullValue());

        assertInitialWalletBalances(walletResponse);
    }

    private void assertMainUsdBalanceFromEnquiry(
            String accountId,
            String accountType,
            String accessToken,
            BigDecimal expectedDisplayBalance
    ) {
        assertMainCurrencyBalanceFromEnquiry(accountId, accountType, accessToken, "USD", expectedDisplayBalance);
    }

    private void assertMainCurrencyBalanceFromEnquiry(BusinessUser user, String currency, BigDecimal expectedDisplayBalance) {
        assertMainCurrencyBalanceFromEnquiry(
                user.accountId(),
                user.accountType(),
                user.accessToken(),
                currency,
                expectedDisplayBalance
        );
    }

    private void assertMainCurrencyBalanceFromEnquiry(SubscriberUser user, String currency, BigDecimal expectedDisplayBalance) {
        assertMainCurrencyBalanceFromEnquiry(
                user.accountId(),
                "SUBSCRIBER",
                user.accessToken(),
                currency,
                expectedDisplayBalance
        );
    }

    private void assertCurrencyWalletBalanceFromEnquiry(
            SubscriberUser user,
            String walletType,
            String currency,
            BigDecimal expectedDisplayBalance
    ) {
        assertCurrencyWalletBalanceFromEnquiry(
                user.accountId(),
                "SUBSCRIBER",
                user.accessToken(),
                walletType,
                currency,
                expectedDisplayBalance
        );
    }

    private void seedWalletBalance(String accountId, String walletType, String currency, BigDecimal displayAmount) {
        BigDecimal storedAmount = displayAmount.multiply(new BigDecimal("100"));
        BigDecimal previousStoredAmount = jdbcTemplate.queryForObject("""
                        SELECT wb.available_balance
                        FROM %s wb
                        JOIN %s w ON w.wallet_id = wb.wallet_id
                        WHERE w.account_id = ?
                          AND w.wallet_type = ?
                          AND w.currency = ?
                          AND w.status = 'ACTIVE'
                        """.formatted(tenantTable("wallet_balance"), tenantTable("wallet")),
                BigDecimal.class,
                accountId,
                walletType,
                currency
        );
        previousStoredAmount = previousStoredAmount == null ? BigDecimal.ZERO : previousStoredAmount;
        int updated = jdbcTemplate.update("""
                        UPDATE %s wb
                        SET available_balance = ?,
                               frozen_balance = 0,
                               fic_balance = 0
                          FROM %s w
                         WHERE w.wallet_id = wb.wallet_id
                           AND w.account_id = ?
                           AND w.wallet_type = ?
                           AND w.currency = ?
                        """.formatted(tenantTable("wallet_balance"), tenantTable("wallet")),
                storedAmount,
                accountId,
                walletType,
                currency
        );
        assertEquals(1, updated, "Expected to seed " + walletType + " " + currency + " wallet for " + accountId);
        seededAvailableBalanceOffset = seededAvailableBalanceOffset.add(storedAmount.subtract(previousStoredAmount));
    }

    private void assertMainCurrencyBalanceFromEnquiry(
            String accountId,
            String accountType,
            String accessToken,
            String currency,
            BigDecimal expectedDisplayBalance
    ) {
        assertCurrencyWalletBalanceFromEnquiry(
                accountId,
                accountType,
                accessToken,
                "MAIN",
                currency,
                expectedDisplayBalance
        );
    }

    private void assertCurrencyWalletBalanceFromEnquiry(
            String accountId,
            String accountType,
            String accessToken,
            String walletType,
            String currency,
            BigDecimal expectedDisplayBalance
    ) {
        Map<String, Object> walletBalance = walletBalanceFromEnquiry(accountId, accountType, accessToken, walletType, currency);
        assertBigDecimalEquals(
                expectedDisplayBalance,
                toBigDecimal(walletBalance.get("availableBalance")),
                accountType + " " + walletType + " " + currency + " available balance from enquiry"
        );
        assertBigDecimalEquals(
                BigDecimal.ZERO,
                toBigDecimal(walletBalance.get("frozenBalance")),
                accountType + " " + walletType + " " + currency + " frozen balance from enquiry"
        );
        if (!accountType.equalsIgnoreCase("biller")) {
            assertBigDecimalEquals(
                    BigDecimal.ZERO,
                    toBigDecimal(walletBalance.get("ficBalance")),
                    accountType + " " + walletType + " " + currency + " FIC balance from enquiry"
            );
        }
    }

    private void assertMainUsdAvailableBalanceFromEnquiry(
            String accountId,
            String accountType,
            String accessToken,
            BigDecimal expectedDisplayBalance
    ) {
        Map<String, Object> mainUsdBalance = mainUsdBalanceFromEnquiry(accountId, accountType, accessToken);
        assertBigDecimalEquals(
                expectedDisplayBalance,
                toBigDecimal(mainUsdBalance.get("availableBalance")),
                accountType + " MAIN USD available balance from enquiry"
        );
    }

    private BigDecimal readMainUsdAvailableBalanceFromEnquiry(
            String accountId,
            String accountType,
            String accessToken
    ) {
        Map<String, Object> mainUsdBalance = mainUsdBalanceFromEnquiry(accountId, accountType, accessToken);
        return toBigDecimal(mainUsdBalance.get("availableBalance"));
    }

    private Map<String, Object> mainUsdBalanceFromEnquiry(
            String accountId,
            String accountType,
            String accessToken
    ) {
        return mainBalanceFromEnquiry(accountId, accountType, accessToken, "USD");
    }

    private Map<String, Object> mainBalanceFromEnquiry(
            String accountId,
            String accountType,
            String accessToken,
            String currency
    ) {
        return walletBalanceFromEnquiry(accountId, accountType, accessToken, "MAIN", currency);
    }

    private Map<String, Object> walletBalanceFromEnquiry(
            String accountId,
            String accountType,
            String accessToken,
            String walletType,
            String currency
    ) {
        Response walletResponse = getJson(
                "fetch wallet balance for " + accountType,
                "/api/v1/wallet/getAccountWallets/{accountId}",
                accessToken,
                accountId
        );
        walletResponse.then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("message", equalTo("Wallets fetched successfully"))
                .body("wallets.accountId", equalTo(accountId))
                .body("wallets.balances.find { it.walletType == '" + walletType + "' && it.currency == '" + currency + "' }", notNullValue());

        List<Map<String, Object>> balances = walletResponse.jsonPath().getList("wallets.balances");
        return balances.stream()
                .filter(balance -> walletType.equals(balance.get("walletType"))
                        && currency.equals(balance.get("currency")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected " + walletType + " " + currency + " wallet for account " + accountId));
    }

    private void assertBigDecimalEquals(BigDecimal expected, BigDecimal actual, String label) {
        assertEquals(0, expected.compareTo(actual), "Expected " + label + " to be " + expected + " but was " + actual);
    }

    private BigDecimal toBigDecimal(Object value) {
        return value instanceof BigDecimal amount ? amount : new BigDecimal(String.valueOf(value));
    }

    private void assertBusinessAccount(
            String accountId,
            String accountType,
            String mobile,
            String loginId,
            String firstName,
            String lastName,
            String email
    ) {
        Map<String, Object> account = jdbcTemplate.queryForMap("""
                SELECT account_id,
                       account_type,
                       mobile_number,
                       first_name,
                       last_name,
                       email,
                       status
                FROM %s
                WHERE account_id = ?
                """.formatted(tenantTable("account")), accountId);

        assertEquals(accountId, account.get("account_id"));
        assertEquals(accountType, account.get("account_type"));
        assertEquals(mobile, account.get("mobile_number"));
        assertEquals(firstName, account.get("first_name"));
        assertEquals(lastName, account.get("last_name"));
        assertEquals(email, account.get("email"));
        assertEquals("ACTIVE", account.get("status"));

        List<Map<String, Object>> identifiers = jdbcTemplate.queryForList("""
                SELECT identifier_type,
                       identifier_value,
                       auth_id,
                       status
                FROM %s
                WHERE account_id = ?
                ORDER BY identifier_type
                """.formatted(tenantTable("account_identifiers")), accountId);

        assertEquals(2, identifiers.size());
        Map<String, Object> loginIdentifier = findIdentifier(identifiers, "LOGINID");
        Map<String, Object> mobileIdentifier = findIdentifier(identifiers, "MOBILE");

        assertEquals(loginId, loginIdentifier.get("identifier_value"));
        assertEquals("ACTIVE", loginIdentifier.get("status"));
        assertEquals(mobile, mobileIdentifier.get("identifier_value"));
        assertEquals("ACTIVE", mobileIdentifier.get("status"));
        assertEquals(loginIdentifier.get("auth_id"), mobileIdentifier.get("auth_id"));

        Integer activeAuthCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM %s
                WHERE auth_id = ?
                  AND auth_type = 'PASSWORD'
                  AND status = 'ACTIVE'
                  AND is_first_time_login = FALSE
                """.formatted(tenantTable("account_auth")), Integer.class, loginIdentifier.get("auth_id"));
        assertEquals(1, activeAuthCount);
    }

    private void assertSubscriberAccount(String accountId, String mobile) {
        Map<String, Object> account = jdbcTemplate.queryForMap("""
                SELECT account_id,
                       account_type,
                       mobile_number,
                       status
                FROM %s
                WHERE account_id = ?
                """.formatted(tenantTable("account")), accountId);

        assertEquals(accountId, account.get("account_id"));
        assertEquals("SUBSCRIBER", account.get("account_type"));
        assertEquals(mobile, account.get("mobile_number"));
        assertEquals("ACTIVE", account.get("status"));

        List<Map<String, Object>> identifiers = jdbcTemplate.queryForList("""
                SELECT identifier_type,
                       identifier_value,
                       auth_id,
                       status
                FROM %s
                WHERE account_id = ?
                """.formatted(tenantTable("account_identifiers")), accountId);

        assertEquals(1, identifiers.size());
        Map<String, Object> mobileIdentifier = findIdentifier(identifiers, "MOBILE");
        assertEquals(mobile, mobileIdentifier.get("identifier_value"));
        assertEquals("ACTIVE", mobileIdentifier.get("status"));

        Integer activeAuthCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM %s
                WHERE auth_id = ?
                  AND auth_type = 'PIN'
                  AND status = 'ACTIVE'
                  AND is_first_time_login = FALSE
                """.formatted(tenantTable("account_auth")), Integer.class, mobileIdentifier.get("auth_id"));
        assertEquals(1, activeAuthCount);

        Integer walletCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM %s w
                JOIN %s wb ON wb.wallet_id = w.wallet_id
                WHERE w.account_id = ?
                  AND w.status = 'ACTIVE'
                  AND wb.available_balance = 0
                  AND wb.frozen_balance = 0
                  AND wb.fic_balance = 0
                """.formatted(tenantTable("wallet"), tenantTable("wallet_balance")), Integer.class, accountId);
        assertTrue(walletCount >= 1, "Expected at least one active zero-balance wallet for account " + accountId);
    }

    private Map<String, Object> findIdentifier(List<Map<String, Object>> identifiers, String identifierType) {
        return identifiers.stream()
                .filter(identifier -> identifierType.equals(identifier.get("identifier_type")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected identifier type " + identifierType));
    }

    private void assertJwt(String accessToken, String accountId, String authType, String accountType) {
        assertTrue(jwtService.isTokenValid(accessToken), "Expected login JWT to be valid");
        Claims claims = jwtService.getClaims(accessToken);
        assertEquals(accountId, claims.getSubject());
        assertEquals(TENANT_ID, claims.get("tenant", String.class));
        assertEquals(authType, claims.get("authType", String.class));
        assertEquals(accountType, claims.get("scope", String.class));
    }

    private Response postJson(String stepName, String path, String requestBody) {
        log.info("E2E step request: step=\"{}\" method=POST path={} tenantId={} body={}",
                stepName,
                path,
                TENANT_ID,
                requestBody
        );

        Response response = given()
                .contentType(ContentType.JSON)
                .header("X-Tenant-Id", TENANT_ID)
                .body(requestBody)
                .when()
                .post(path);

        logResponse(stepName, response);
        assertFinancialWalletBalanceSumIfNeeded(stepName, path, response);
        return response;
    }

    private Response postJson(String stepName, String path, String bearerToken, String requestBody) {
        log.info("E2E step request: step=\"{}\" method=POST path={} tenantId={} authorization=\"Bearer <{} chars>\" body={}",
                stepName,
                path,
                TENANT_ID,
                bearerToken.length(),
                requestBody
        );

        Response response = given()
                .contentType(ContentType.JSON)
                .header("X-Tenant-Id", TENANT_ID)
                .header("Authorization", "Bearer " + bearerToken)
                .body(requestBody)
                .when()
                .post(path);

        logResponse(stepName, response);
        assertFinancialWalletBalanceSumIfNeeded(stepName, path, response);
        return response;
    }

    private Response postJson(String stepName, String path, String bearerToken, String requestBody, Object... pathParams) {
        log.info("E2E step request: step=\"{}\" method=POST path={} tenantId={} authorization=\"Bearer <{} chars>\" pathParams={} body={}",
                stepName,
                path,
                TENANT_ID,
                bearerToken.length(),
                List.of(pathParams),
                requestBody
        );

        Response response = given()
                .contentType(ContentType.JSON)
                .header("X-Tenant-Id", TENANT_ID)
                .header("Authorization", "Bearer " + bearerToken)
                .body(requestBody)
                .when()
                .post(path, pathParams);

        logResponse(stepName, response);
        assertFinancialWalletBalanceSumIfNeeded(stepName, path, response);
        return response;
    }

    private Response patchJson(String stepName, String path, String bearerToken, String requestBody, Object... pathParams) {
        log.info("E2E step request: step=\"{}\" method=PATCH path={} tenantId={} authorization=\"Bearer <{} chars>\" pathParams={} body={}",
                stepName,
                path,
                TENANT_ID,
                bearerToken.length(),
                List.of(pathParams),
                requestBody
        );

        Response response = given()
                .contentType(ContentType.JSON)
                .header("X-Tenant-Id", TENANT_ID)
                .header("Authorization", "Bearer " + bearerToken)
                .body(requestBody)
                .when()
                .patch(path, pathParams);

        logResponse(stepName, response);
        assertFinancialWalletBalanceSumIfNeeded(stepName, path, response);
        return response;
    }

    private Response deleteJson(String stepName, String path, String bearerToken, Object... pathParams) {
        log.info("E2E step request: step=\"{}\" method=DELETE path={} tenantId={} authorization=\"Bearer <{} chars>\" pathParams={}",
                stepName,
                path,
                TENANT_ID,
                bearerToken.length(),
                List.of(pathParams)
        );

        Response response = given()
                .contentType(ContentType.JSON)
                .header("X-Tenant-Id", TENANT_ID)
                .header("Authorization", "Bearer " + bearerToken)
                .when()
                .delete(path, pathParams);

        logResponse(stepName, response);
        return response;
    }

    private void assertFinancialWalletBalanceSumIfNeeded(String stepName, String path, Response response) {
        if (!path.startsWith("/api/v1/pay/")) {
            return;
        }
        if (response.statusCode() >= 500) {
            return;
        }

        assertAvailableWalletBalanceSumIsZero(stepName);
    }

    private void assertAvailableWalletBalanceSumIsZero(String assertionContext) {
        Map<String, Object> balanceSums = jdbcTemplate.queryForMap("""
                SELECT COALESCE(SUM(available_balance), 0) AS available_balance_sum
                FROM %s
                """.formatted(tenantTable("wallet_balance")));

        assertBigDecimalEquals(
                seededAvailableBalanceOffset,
                toBigDecimal(balanceSums.get("available_balance_sum")),
                assertionContext + " available wallet balance sum"
        );
    }

    private Response putJson(String stepName, String path, String bearerToken, String requestBody) {
        log.info("E2E step request: step=\"{}\" method=PUT path={} tenantId={} authorization=\"Bearer <{} chars>\" body={}",
                stepName,
                path,
                TENANT_ID,
                bearerToken.length(),
                requestBody
        );

        Response response = given()
                .contentType(ContentType.JSON)
                .header("X-Tenant-Id", TENANT_ID)
                .header("Authorization", "Bearer " + bearerToken)
                .body(requestBody)
                .when()
                .put(path);

        logResponse(stepName, response);
        return response;
    }

    private Response getJson(String stepName, String path, String bearerToken, Object... pathParams) {
        log.info("E2E step request: step=\"{}\" method=GET path={} tenantId={} authorization=\"Bearer <{} chars>\" pathParams={}",
                stepName,
                path,
                TENANT_ID,
                bearerToken.length(),
                List.of(pathParams)
        );

        Response response = given()
                .contentType(ContentType.JSON)
                .header("X-Tenant-Id", TENANT_ID)
                .header("Authorization", "Bearer " + bearerToken)
                .when()
                .get(path, pathParams);

        logResponse(stepName, response);
        return response;
    }

    private void logResponse(String stepName, Response response) {
        log.info("E2E step response: step=\"{}\" status={} timeMs={} body={}",
                stepName,
                response.statusCode(),
                response.time(),
                response.asPrettyString()
        );
    }

    private void assertInitialWalletBalances(Response walletResponse) {
        List<Map<String, Object>> balances = walletResponse.jsonPath().getList("wallets.balances");
        assertFalse(balances.isEmpty(), "Expected at least one wallet balance in wallet API response");

        for (Map<String, Object> balance : balances) {
            assertZeroBalance(balance, "availableBalance");
            assertZeroBalance(balance, "frozenBalance");
            assertZeroBalance(balance, "ficBalance");
        }
        log.info("E2E wallet balance validation completed: walletCount={} allInitialBalances=0", balances.size());
    }

    private void assertZeroBalance(Map<String, Object> balance, String fieldName) {
        Object value = balance.get(fieldName);
        BigDecimal amount = new BigDecimal(String.valueOf(value));
        assertEquals(
                0,
                amount.compareTo(BigDecimal.ZERO),
                "Expected " + fieldName + " to be 0 for walletType=" + balance.get("walletType")
                        + " currency=" + balance.get("currency")
        );
    }

    private void ensureTenant() {
        log.info("E2E ensure tenant: tenantId={} tenantSchema={}", TENANT_ID, TENANT_SCHEMA);
        if (hasColumn("public", "tenant_registry", "iana_time_zone")) {
            jdbcTemplate.update("""
                    INSERT INTO public.tenant_registry (
                        tenant_id,
                        tenant_name,
                        schema_name,
                        iana_time_zone,
                        status,
                        created_at,
                        updated_at
                    )
                    SELECT ?, ?, ?, 'UTC', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    WHERE NOT EXISTS (
                        SELECT 1
                        FROM public.tenant_registry
                        WHERE tenant_id = ?
                    )
                    """, TENANT_ID, "E2E Tenant", TENANT_SCHEMA, TENANT_ID);
        } else {
            jdbcTemplate.update("""
                    INSERT INTO public.tenant_registry (
                        tenant_id,
                        tenant_name,
                        schema_name,
                        status,
                        created_at,
                        updated_at
                    )
                    SELECT ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    WHERE NOT EXISTS (
                        SELECT 1
                        FROM public.tenant_registry
                        WHERE tenant_id = ?
                    )
                    """, TENANT_ID, "E2E Tenant", TENANT_SCHEMA, TENANT_ID);
        }
        tenantRegistryService.loadTenants();
        log.info("E2E ensure tenant completed: tenantId={} tenantSchema={}", TENANT_ID, TENANT_SCHEMA);
    }

    private boolean hasColumn(String tableSchema, String tableName, String columnName) {
        Boolean exists = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM information_schema.columns
                    WHERE table_schema = ?
                      AND table_name = ?
                      AND column_name = ?
                )
                """, Boolean.class, tableSchema, tableName, columnName);
        return Boolean.TRUE.equals(exists);
    }

    private void ensureEnumeration(String enumType, String enumCode, String enumValue, String description) {
        log.info("E2E ensure enumeration: type={} code={} value={}", enumType, enumCode, enumValue);
        int updated = jdbcTemplate.update("""
                UPDATE %s
                SET enum_value = ?,
                    description = ?,
                    is_active = TRUE,
                    updated_at = CURRENT_TIMESTAMP
                WHERE UPPER(enum_type) = UPPER(?)
                  AND UPPER(enum_code) = UPPER(?)
                """.formatted(tenantTable("enumerations")), enumValue, description, enumType, enumCode);

        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO %s (
                        enum_type,
                        enum_code,
                        enum_value,
                        description,
                        display_order,
                        is_active,
                        is_system,
                        created_at
                    )
                    VALUES (?, ?, ?, ?, 0, TRUE, TRUE, CURRENT_TIMESTAMP)
                    """.formatted(tenantTable("enumerations")), enumType, enumCode, enumValue, description);
        }
        log.info("E2E ensure enumeration completed: type={} code={} action={}",
                enumType,
                enumCode,
                updated == 0 ? "inserted" : "updated"
        );
    }

    private void ensureSubscriberRole() {
        log.info("E2E ensure subscriber role");
        jdbcTemplate.update("""
                INSERT INTO %s (
                    role_code,
                    role_name,
                    role_type,
                    description,
                    status,
                    created_at
                )
                SELECT 'SUBSCRIBER', 'Subscriber', 'SUBSCRIBER', 'Default subscriber role', 'ACTIVE', CURRENT_TIMESTAMP
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM %s
                    WHERE UPPER(role_code) = 'SUBSCRIBER'
                )
                """.formatted(tenantTable("roles"), tenantTable("roles")));
        log.info("E2E ensure subscriber role completed");
    }

    private String tenantTable(String tableName) {
        return TENANT_SCHEMA + "." + tableName;
    }
}
