package com.paynest.e2e;

import com.paynest.common.ErrorCodes;
import com.paynest.config.security.JwtService;
import com.paynest.config.service.TenantRegistryService;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Slf4j
class MultiTenantIsolationE2ETest {

    private static final String DEFAULT_ADMIN_ACCOUNT_ID = "ADMIN0000000001";
    private static final String DEFAULT_ADMIN_LOGIN_ID = "superadmin";
    private static final String DEFAULT_ADMIN_PASSWORD = "Admin@123";
    private static final String NETWORK_ADMIN_DEFAULT_PASSWORD = "PayNest@123";
    private static final String NETWORK_ADMIN_UPDATED_PASSWORD = "Network@123";
    private static final String BUSINESS_USER_DEFAULT_PASSWORD = "PayNest@123";
    private static final String BUSINESS_USER_UPDATED_PASSWORD = "Business@123";
    private static final String DEFAULT_PIN = "0000";
    private static final String UPDATED_PIN = "2468";
    private static final int MIN_REQUEST_DELAY_MS = 25;
    private static final int MAX_REQUEST_DELAY_MS = 250;
    private static final TenantSpec TENANT_ONE = new TenantSpec("e2emta", "tenant_e2emta", "PayNest E2E Multi Tenant A");
    private static final TenantSpec TENANT_TWO = new TenantSpec("e2emtb", "tenant_e2emtb", "PayNest E2E Multi Tenant B");
    private static final TenantSpec TENANT_THREE = new TenantSpec("e2emtc", "tenant_e2emtc", "PayNest E2E Multi Tenant C");
    private static final TenantSpec TENANT_FOUR = new TenantSpec("e2emtd", "tenant_e2emtd", "PayNest E2E Multi Tenant D");
    private static final List<TenantSpec> TENANTS = List.of(TENANT_ONE, TENANT_TWO, TENANT_THREE, TENANT_FOUR);

    static {
        log.info("Multi-tenant bootstrap static initializer starting. tenantCount={} tenants={}",
                TENANTS.size(),
                TENANTS
        );
        TENANTS.forEach(MultiTenantIsolationE2ETest::runDatabaseBootstrap);
        log.info("Multi-tenant bootstrap static initializer completed. tenantCount={}", TENANTS.size());
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TenantRegistryService tenantRegistryService;

    @Autowired
    private JwtService jwtService;

    private record TenantSpec(String tenantId, String schema, String tenantName) {
    }

    private record SubscriberUser(String accountId, String mobile, String accessToken, String pin) {
    }

    private record BusinessUser(String accountId, String accountType, String mobile, String accessToken,
                                String password) {
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

    private record TenantRun(
            TenantSpec tenant,
            SubscriberUser subscriber,
            SubscriberUser receiverSubscriber,
            BusinessUser agent,
            BusinessUser merchant,
            BusinessUser biller,
            List<String> transactionIds
    ) {
    }

    private static void runDatabaseBootstrap(TenantSpec tenant) {
        Path outputPath = null;
        try {
            Path projectRoot = Path.of(System.getProperty("user.dir"));
            Path scriptPath = projectRoot.resolve("scripts").resolve("paynest-db.ps1");
            Path outputDirectory = projectRoot.resolve("target").resolve("e2e-bootstrap-logs");
            Files.createDirectories(outputDirectory);
            outputPath = outputDirectory.resolve(tenant.tenantId() + "-bootstrap.log");
            Files.deleteIfExists(outputPath);

            List<String> command = List.of(
                    "powershell.exe",
                    "-NoProfile",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-File",
                    scriptPath.toString(),
                    "-TenantId",
                    tenant.tenantId(),
                    "-TenantName",
                    tenant.tenantName(),
                    "-TenantSchema",
                    tenant.schema()
            );

            log.info(
                    "Multi-tenant bootstrap starting. tenantId={} tenantSchema={} tenantName={} projectRoot={} script={} scriptExists={} outputFile={} command={}",
                    tenant.tenantId(),
                    tenant.schema(),
                    tenant.tenantName(),
                    projectRoot,
                    scriptPath,
                    Files.exists(scriptPath),
                    outputPath,
                    command
            );

            Process process = new ProcessBuilder(command)
                    .directory(projectRoot.toFile())
                    .redirectOutput(outputPath.toFile())
                    .redirectErrorStream(true)
                    .start();

            log.info("Multi-tenant bootstrap process started. tenantId={} pid={}", tenant.tenantId(), process.pid());
            boolean completed = process.waitFor(180, TimeUnit.SECONDS);
            if (!completed) {
                log.error(
                        "Multi-tenant bootstrap timed out before process exit. tenantId={} pid={} outputFile={}",
                        tenant.tenantId(),
                        process.pid(),
                        outputPath
                );
                process.destroyForcibly();
                boolean killed = process.waitFor(10, TimeUnit.SECONDS);
                String output = readBootstrapOutput(outputPath);
                log.error(
                        "Multi-tenant bootstrap process killed after timeout. tenantId={} killed={} outputFile={} output={}",
                        tenant.tenantId(),
                        killed,
                        outputPath,
                        output
                );
                throw new IllegalStateException("Database bootstrap timed out for " + tenant.tenantId() + ". Output: " + output);
            }

            String output = readBootstrapOutput(outputPath);
            log.info("Multi-tenant bootstrap process completed. tenantId={} exitCode={} outputFile={} output={}",
                    tenant.tenantId(),
                    process.exitValue(),
                    outputPath,
                    output
            );
            log.info("Multi-tenant bootstrap completed. completed={} tenantId={} exitCode={}",
                    completed,
                    tenant.tenantId(),
                    process.exitValue()
            );
            if (process.exitValue() != 0) {
                throw new IllegalStateException("Database bootstrap failed for " + tenant.tenantId() + ": " + output);
            }
        } catch (Exception ex) {
            log.error("Multi-tenant bootstrap failed before tests. tenantId={} outputFile={}",
                    tenant.tenantId(),
                    outputPath,
                    ex
            );
            throw new ExceptionInInitializerError(ex);
        }
    }

    private static String readBootstrapOutput(Path outputPath) throws IOException {
        if (outputPath == null || !Files.exists(outputPath)) {
            return "";
        }
        return Files.readString(outputPath, StandardCharsets.UTF_8);
    }

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        tenantRegistryService.loadTenants();
        for (TenantSpec tenant : TENANTS) {
            ensureEnumeration(tenant, "SYSTEM_CONFIG", "TESTING_MODE", "true", "Enable deterministic test credentials");
            ensureEnumeration(tenant, "CURRENCY", "USD", "USD", "US Dollar");
            ensureEnumeration(tenant, "CURRENCY", "INR", "INR", "Indian Rupee");
            ensureEnumeration(tenant, "WALLET_TYPE", "MAIN", "MAIN", "Main wallet");
        }
    }

    @Test
    void sameSubscriberMsisdnAndFinancialTransactions_shouldRemainTenantIsolated() {
        String sharedSubscriberMobile = mobileNumber("799", uniqueSuffix());
        ExecutorService executorService = Executors.newFixedThreadPool(TENANTS.size());
        try {
            List<CompletableFuture<TenantRun>> futures = new ArrayList<>();
            for (int i = 0; i < TENANTS.size(); i++) {
                TenantSpec tenant = TENANTS.get(i);
                String suffix = String.valueOf((char) ('a' + i));
                futures.add(CompletableFuture.supplyAsync(() -> exerciseTenant(tenant, sharedSubscriberMobile, suffix), executorService));
            }

            List<TenantRun> runs = futures.stream()
                    .map(CompletableFuture::join)
                    .toList();

            logTenantTransactionSummary("before-final-verification", runs);
            for (TenantRun run : runs) {
                assertSubscriberMobileExistsOnlyInTenant(run.tenant(), sharedSubscriberMobile, run.subscriber().accountId());
                assertTenantTransactionIdsExist(run.tenant(), run.transactionIds());
                for (TenantRun otherRun : runs) {
                    if (!run.tenant().equals(otherRun.tenant())) {
                        assertTenantDoesNotContainMobile(run.tenant(), otherRun.agent().mobile());
                        assertTenantDoesNotContainMobile(run.tenant(), otherRun.merchant().mobile());
                        assertTenantDoesNotContainMobile(run.tenant(), otherRun.biller().mobile());
                        assertTenantDoesNotContainMobile(run.tenant(), otherRun.receiverSubscriber().mobile());
                    }
                }
            }
            logTenantTransactionSummary("after-final-verification", runs);
        } finally {
            executorService.shutdownNow();
        }
    }

    private void logTenantTransactionSummary(String checkpoint, List<TenantRun> runs) {
        log.info(
                "Multi-tenant E2E transaction summary checkpoint={} tenantCount={}",
                checkpoint,
                runs.size()
        );
        for (TenantRun run : runs) {
            log.info(
                    "Multi-tenant E2E tenant transactions checkpoint={} tenantId={} tenantSchema={} transactionCount={} transactionIds={} subscriberAccountId={} receiverSubscriberAccountId={} agentAccountId={} merchantAccountId={} billerAccountId={}",
                    checkpoint,
                    run.tenant().tenantId(),
                    run.tenant().schema(),
                    run.transactionIds().size(),
                    run.transactionIds(),
                    run.subscriber().accountId(),
                    run.receiverSubscriber().accountId(),
                    run.agent().accountId(),
                    run.merchant().accountId(),
                    run.biller().accountId()
            );
        }
    }

    private TenantRun exerciseTenant(TenantSpec tenant, String sharedSubscriberMobile, String suffix) {
        String runSuffix = suffix.toUpperCase() + uniqueSuffix();
        String initiatingAdminToken = createNetworkAdminAndLogin(tenant, "mtinit" + suffix);
        String approvingAdminToken = createNetworkAdminAndLogin(tenant, "mtapprove" + suffix);
        SubscriberUser subscriber = createSubscriberChangePinAndLogin(tenant, sharedSubscriberMobile, 500 + suffix.charAt(0));
        SubscriberUser receiverSubscriber = createSubscriberChangePinAndLogin(tenant, mobileNumber("798", runSuffix), 600 + suffix.charAt(0));
        BusinessUser agent = createBusinessUserChangePasswordAndLogin(tenant, initiatingAdminToken, "AGENT", "AGENT", "mtagent" + runSuffix);
        BusinessUser merchant = createBusinessUserChangePasswordAndLogin(tenant, initiatingAdminToken, "MERCHANT", "MERCHANT", "mtmerchant" + runSuffix);
        BusinessUser biller = createBusinessUserChangePasswordAndLogin(tenant, initiatingAdminToken, "BILLER", "BILLER", "mtbiller" + runSuffix);

        assertDefaultBaseTag(tenant, subscriber.accountId(), "SUBSCRIBER_BASE", "SUBSCRIBER");
        assertDefaultBaseTag(tenant, receiverSubscriber.accountId(), "SUBSCRIBER_BASE", "SUBSCRIBER");
        assertDefaultBaseTag(tenant, agent.accountId(), "AGENT_BASE", "AGENT");
        assertDefaultBaseTag(tenant, merchant.accountId(), "MERCHANT_BASE", "MERCHANT");

        Long subscriberBehaviorTagId = createTag(tenant, initiatingAdminToken, "MT_SUB_BEHAVIOR_" + runSuffix, "SUBSCRIBER", "BEHAVIOR");
        Long agentUpgradeTagId = createTag(tenant, initiatingAdminToken, "MT_AGENT_UPGRADE_" + runSuffix, "AGENT", "UPGRADE");
        linkTagToAccount(tenant, initiatingAdminToken, subscriberBehaviorTagId, subscriber.accountId());
        linkTagToAccount(tenant, initiatingAdminToken, agentUpgradeTagId, agent.accountId());

        createServiceChargeRule(
                tenant,
                initiatingAdminToken,
                "CASHIN",
                "MT_AGENT_UPGRADE_" + runSuffix,
                "MT_SUB_BEHAVIOR_" + runSuffix,
                "2.50"
        );
        assertPricingCalculation(
                tenant,
                agent.accessToken(),
                agent,
                subscriber,
                "MT_AGENT_UPGRADE_" + runSuffix,
                "MT_SUB_BEHAVIOR_" + runSuffix
        );

        List<String> transactionIds = new ArrayList<>();
        transactionIds.add(performApprovedStockTransaction(tenant, initiatingAdminToken, approvingAdminToken, new BigDecimal("1500.00")));
        transactionIds.add(performApprovedO2CTransaction(tenant, initiatingAdminToken, approvingAdminToken, agent, new BigDecimal("500.00")));
        transactionIds.add(performApprovedO2CTransaction(tenant, initiatingAdminToken, approvingAdminToken, merchant, new BigDecimal("25.00")));
        transactionIds.add(performApprovedO2CTransaction(tenant, initiatingAdminToken, approvingAdminToken, biller, new BigDecimal("30.00")));

        transactionIds.add(performApprovedStockTransaction(tenant, initiatingAdminToken, approvingAdminToken, new BigDecimal("1250.00"), "INR"));
        String inrO2CId = performApprovedO2CTransaction(
                tenant,
                initiatingAdminToken,
                approvingAdminToken,
                agent,
                new BigDecimal("1250.00"),
                "INR"
        );
        transactionIds.add(inrO2CId);
        String inrCashInId = performCashIn(tenant, agent, subscriber, new BigDecimal("250.00"), "INR");
        transactionIds.add(inrCashInId);
        assertFinancialTransferTransaction(tenant, inrCashInId, "CASHIN", agent.accountId(), subscriber.accountId(), new BigDecimal("250.00"));
        assertMainCurrencyBalance(tenant, agent.accountId(), "INR", new BigDecimal("1000.00"));
        assertMainCurrencyBalance(tenant, subscriber.accountId(), "INR", new BigDecimal("250.00"));
        assertMainCurrencyBalance(tenant, agent.accountId(), "USD", new BigDecimal("500.00"));
        assertMainCurrencyBalance(tenant, subscriber.accountId(), "USD", BigDecimal.ZERO);

        String firstCashInId = performCashIn(tenant, agent, subscriber, new BigDecimal("150.00"));
        String secondCashInId = performCashIn(tenant, agent, receiverSubscriber, new BigDecimal("100.00"));
        transactionIds.add(firstCashInId);
        transactionIds.add(secondCashInId);
        assertFinancialTransferTransaction(tenant, firstCashInId, "CASHIN", agent.accountId(), subscriber.accountId(), new BigDecimal("150.00"));
        assertFinancialTransferTransaction(tenant, secondCashInId, "CASHIN", agent.accountId(), receiverSubscriber.accountId(), new BigDecimal("100.00"));

        String u2uId = performU2U(tenant, subscriberParticipant(subscriber), subscriberParticipant(receiverSubscriber), new BigDecimal("40.00"));
        transactionIds.add(u2uId);
        assertFinancialTransferTransaction(tenant, u2uId, "U2U", subscriber.accountId(), receiverSubscriber.accountId(), new BigDecimal("40.00"));

        String merchantPaymentId = performMerchantPayment(tenant, receiverSubscriber, merchant, new BigDecimal("20.00"));
        transactionIds.add(merchantPaymentId);
        assertFinancialTransferTransaction(tenant, merchantPaymentId, "MERCHANTPAY", receiverSubscriber.accountId(), merchant.accountId(), new BigDecimal("20.00"));

        String billPaymentId = performBillPaymentAndSettlement(tenant, subscriber, biller, new BigDecimal("15.00"));
        transactionIds.add(billPaymentId);
        assertFinancialTransferTransaction(tenant, billPaymentId, "BILLPAY", subscriber.accountId(), biller.accountId(), new BigDecimal("15.00"));

        String failedBillPaymentId = performBillPaymentAndSettlement(tenant, subscriber, biller, new BigDecimal("5.00"), false);
        transactionIds.add(failedBillPaymentId);
        assertTransactionStatus(tenant, failedBillPaymentId, "BILLPAY", "TF", new BigDecimal("5.00"));

        String cashOutId = performCashOut(tenant, receiverSubscriber, agent, new BigDecimal("10.00"));
        transactionIds.add(cashOutId);
        assertFinancialTransferTransaction(tenant, cashOutId, "CASHOUT", receiverSubscriber.accountId(), agent.accountId(), new BigDecimal("10.00"));

        assertMainUsdBalance(tenant, agent.accountId(), new BigDecimal("260.00"));
        assertMainUsdBalance(tenant, subscriber.accountId(), new BigDecimal("95.00"));
        assertMainUsdBalance(tenant, receiverSubscriber.accountId(), new BigDecimal("110.00"));
        assertMainUsdBalance(tenant, merchant.accountId(), new BigDecimal("45.00"));
        assertMainUsdBalance(tenant, biller.accountId(), new BigDecimal("45.00"));

        return new TenantRun(tenant, subscriber, receiverSubscriber, agent, merchant, biller, transactionIds);
    }

    private String createNetworkAdminAndLogin(TenantSpec tenant, String scenarioPrefix) {
        String superAdminToken = loginWithPassword(
                tenant,
                "superadmin login " + tenant.tenantId(),
                DEFAULT_ADMIN_LOGIN_ID,
                DEFAULT_ADMIN_PASSWORD,
                DEFAULT_ADMIN_ACCOUNT_ID
        );
        String uniqueSuffix = uniqueSuffix();
        String mobile = mobileNumber("800", uniqueSuffix);
        String loginId = "networkadmin" + scenarioPrefix + uniqueSuffix;
        String registerRequest = """
                {
                  "requestId": "req-%s-networkadmin-register",
                  "user": {
                    "mobileNumber": "%s",
                    "accountType": "ADMIN",
                    "accountCode": "%s",
                    "firstName": "Network",
                    "lastName": "Admin",
                    "email": "networkadmin.%s@example.com",
                    "address": "200 PayNest Network Street",
                    "gender": "MALE",
                    "dateOfBirth": "1991-06-20",
                    "preferredLang": "en",
                    "nationality": "USA",
                    "ssn": "111-22-%s",
                    "remarks": "E2E network admin",
                    "loginId": "%s",
                    "role": "NETWORKADMIN"
                  }
                }
                """.formatted(scenarioPrefix, mobile, loginId, loginId, uniqueSuffix.substring(Math.max(0, uniqueSuffix.length() - 4)), loginId);

        String accountId = postJson(tenant, "create network admin", "/api/v1/account/registerUser", superAdminToken, registerRequest)
                .then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("message", equalTo("User registered successfully"))
                .body("accountId", notNullValue())
                .extract()
                .path("accountId");

        postJson(tenant, "network admin first login", "/api/v1/auth/login", loginRequest("req-first-" + loginId, loginId, NETWORK_ADMIN_DEFAULT_PASSWORD))
                .then()
                .statusCode(400)
                .body("code", equalTo(ErrorCodes.FORCE_AUTH_CHANGE));
        postJson(tenant, "network admin change password", "/api/v1/account/password/changeDefault", changePasswordRequest(
                "req-change-" + loginId,
                loginId,
                NETWORK_ADMIN_DEFAULT_PASSWORD,
                NETWORK_ADMIN_UPDATED_PASSWORD
        )).then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"));

        String accessToken = loginWithPassword(tenant, "network admin login after change", loginId, NETWORK_ADMIN_UPDATED_PASSWORD, accountId);
        assertJwt(accessToken, accountId, "PASSWORD", "ADMIN");
        return accessToken;
    }

    private SubscriberUser createSubscriberChangePinAndLogin(TenantSpec tenant, String mobile, int pinSeed) {
        postJson(tenant, "generate subscriber OTP", "/api/v1/account/register/selfGenOtp", """
                {
                  "requestId": "req-%s-%s-sub-otp",
                  "user": {
                    "mobileNumber": "%s"
                  }
                }
                """.formatted(tenant.tenantId(), mobile, mobile))
                .then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"));

        String otp = readLatestCreatedRegistrationOtp(tenant, mobile);
        String accountId = postJson(tenant, "register subscriber", "/api/v1/account/register/selfWithOtp", """
                {
                  "requestId": "req-%s-%s-sub-register",
                  "user": {
                    "mobileNumber": "%s",
                    "otp": "%s"
                  }
                }
                """.formatted(tenant.tenantId(), mobile, mobile, otp))
                .then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("message", equalTo("User registered successfully"))
                .body("accountId", notNullValue())
                .extract()
                .path("accountId");

        String updatedPin = String.format("%04d", pinSeed);
        postJson(tenant, "change subscriber PIN", "/api/v1/account/pin/changeDefault", """
                {
                  "oldPin": "%s",
                  "newPin": "%s",
                  "identifierType": "MOBILE",
                  "identifierValue": "%s"
                }
                """.formatted(DEFAULT_PIN, updatedPin, mobile))
                .then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"));

        String accessToken = postJson(tenant, "subscriber login", "/api/v1/auth/login", pinLoginRequest("req-login-" + tenant.tenantId() + "-" + mobile, mobile, updatedPin))
                .then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("accountId", equalTo(accountId))
                .body("accessToken", notNullValue())
                .extract()
                .path("accessToken");
        assertJwt(accessToken, accountId, "PIN", "SUBSCRIBER");
        assertAccountMobile(tenant, accountId, "SUBSCRIBER", mobile);
        return new SubscriberUser(accountId, mobile, accessToken, updatedPin);
    }

    private BusinessUser createBusinessUserChangePasswordAndLogin(
            TenantSpec tenant,
            String creatorToken,
            String accountType,
            String roleCode,
            String loginPrefix
    ) {
        String uniqueSuffix = uniqueSuffix();
        String loginId = loginPrefix + uniqueSuffix;
        String mobile = mobileNumber("810", uniqueSuffix);
        String password = BUSINESS_USER_UPDATED_PASSWORD + accountType.charAt(0);
        String accountCode = loginPrefix + uniqueSuffix;
        String profileSection = businessProfileSection(accountType, uniqueSuffix);
        String registerRequest = """
                {
                  "requestId": "req-%s-%s-register",
                  "user": {
                    "mobileNumber": "%s",
                    "accountType": "%s",
                    "accountCode": "%s",
                    "firstName": "%s",
                    "lastName": "User",
                    "email": "%s@example.com",
                    "address": "300 PayNest Business Street",
                    "gender": "MALE",
                    "dateOfBirth": "1992-07-21",
                    "preferredLang": "en",
                    "nationality": "USA",
                    "ssn": "222-33-%s",
                    "remarks": "E2E %s user",
                    "loginId": "%s",
                    "role": "%s"
                  }%s
                }
                """.formatted(tenant.tenantId(), loginPrefix, mobile, accountType, accountCode, roleCode, loginId, uniqueSuffix.substring(Math.max(0, uniqueSuffix.length() - 4)), accountType, loginId, roleCode, profileSection);
        String accountId = postJson(tenant, "create " + accountType, "/api/v1/account/registerUser", creatorToken, registerRequest)
                .then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("message", equalTo("User registered successfully"))
                .body("accountId", notNullValue())
                .extract()
                .path("accountId");

        postJson(tenant, accountType + " first login", "/api/v1/auth/login", loginRequest("req-first-" + loginId, loginId, BUSINESS_USER_DEFAULT_PASSWORD))
                .then()
                .statusCode(400)
                .body("code", equalTo(ErrorCodes.FORCE_AUTH_CHANGE));
        postJson(tenant, accountType + " change password", "/api/v1/account/password/changeDefault", changePasswordRequest(
                "req-change-" + loginId,
                loginId,
                BUSINESS_USER_DEFAULT_PASSWORD,
                password
        )).then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"));

        String accessToken = loginWithPassword(tenant, accountType + " login after change", loginId, password, accountId);
        assertJwt(accessToken, accountId, "PASSWORD", accountType);
        assertAccountMobile(tenant, accountId, accountType, mobile);
        assertBusinessProfile(tenant, accountId, accountType, uniqueSuffix);
        return new BusinessUser(accountId, accountType, mobile, accessToken, password);
    }

    private String businessProfileSection(String accountType, String uniqueSuffix) {
        if ("MERCHANT".equalsIgnoreCase(accountType)) {
            return """
                    ,
                                      "merchantInfo": {
                                        "merchantCode": "MER%s",
                                        "mccCodes": ["5411", "5812"],
                                        "merchantConfig": {
                                          "source": "e2e"
                                        }
                                      }
                    """.formatted(uniqueSuffix);
        }
        if ("BILLER".equalsIgnoreCase(accountType)) {
            return """
                    ,
                                      "billerInfo": {
                                        "billerCategory": "UTILITIES",
                                        "billerCode": "BIL%s",
                                        "billerSubCategory": "ELEC",
                                        "billerConfig": {
                                          "source": "e2e"
                                        },
                                        "billerSettings": {
                                          "enabled": true
                                        }
                                      }
                    """.formatted(uniqueSuffix);
        }
        return "";
    }

    private void assertBusinessProfile(TenantSpec tenant, String accountId, String accountType, String uniqueSuffix) {
        if ("MERCHANT".equalsIgnoreCase(accountType)) {
            Integer merchantCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + tenant.schema() + ".account_merchant_info WHERE account_id = ? AND merchant_code = ?",
                    Integer.class,
                    accountId,
                    "MER" + uniqueSuffix
            );
            assertEquals(1, merchantCount);
            Integer mccCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + tenant.schema() + ".account_merchant_mcc mcc JOIN " + tenant.schema() + ".account_merchant_info mi ON mi.merchant_info_id = mcc.merchant_info_id WHERE mi.account_id = ? AND mcc.mcc_code IN ('5411', '5812')",
                    Integer.class,
                    accountId
            );
            assertEquals(2, mccCount);
        }
        if ("BILLER".equalsIgnoreCase(accountType)) {
            Integer billerCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + tenant.schema() + ".account_biller_info WHERE account_id = ? AND biller_code = ?",
                    Integer.class,
                    accountId,
                    "BIL" + uniqueSuffix
            );
            assertEquals(1, billerCount);
        }
    }

    private Long createTag(TenantSpec tenant, String accessToken, String tagCode, String category, String tagType) {
        Number tagId = postJson(tenant, "create tag " + tagCode, "/api/v1/tags", accessToken, """
                {
                  "tagCode": "%s",
                  "tagName": "%s",
                  "category": "%s",
                  "tagType": "%s"
                }
                """.formatted(tagCode, tagCode, category, tagType))
                .then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("tag.tagCode", equalToIgnoringCase(tagCode))
                .extract()
                .path("tag.tagId");
        return tagId.longValue();
    }

    private void linkTagToAccount(TenantSpec tenant, String accessToken, Long tagId, String accountId) {
        postJson(tenant, "link tag", "/api/v1/tags/{tagId}/accounts/{accountId}", accessToken, "", tagId, accountId)
                .then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("accountTag.accountId", equalTo(accountId));
    }

    private void createServiceChargeRule(
            TenantSpec tenant,
            String accessToken,
            String serviceCode,
            String senderTagKey,
            String receiverTagKey,
            String flatCharge
    ) {
        postJson(tenant, "create pricing rule", "/api/v1/pricing", accessToken, """
                {
                  "pricingName": "MT %s charge %s %s",
                  "serviceCode": "%s",
                  "ruleType": "SERVICE_CHARGE",
                  "pricingType": "STATIC",
                  "payer": "SENDER",
                  "senderTagKey": "%s",
                  "receiverTagKey": "%s",
                  "currency": "USD",
                  "pricingConfig": {
                    "basedOn": "TXNAMOUNT",
                    "charging_strategy": "FLAT",
                    "calc": {
                      "type": "FLAT",
                      "value": %s
                    }
                  },
                  "status": "ACTIVE"
                }
                """.formatted(tenant.tenantId(), serviceCode, senderTagKey, serviceCode, senderTagKey, receiverTagKey, flatCharge))
                .then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("pricing.serviceCode", equalTo(serviceCode));
    }

    private void assertPricingCalculation(
            TenantSpec tenant,
            String accessToken,
            BusinessUser agent,
            SubscriberUser subscriber,
            String expectedSenderTag,
            String expectedReceiverTag
    ) {
        Response response = postJson(tenant, "calculate tenant pricing", "/api/v1/pay/calculatePricing", accessToken, """
                {
                  "operationType": "CASHIN",
                  "requestGateway": "MOBILE",
                  "preferredLang": "en",
                  "initiatedBy": "DEBITOR",
                  "debitor": {
                    "accountType": "AGENT",
                    "identifier": {
                      "type": "MOBILE",
                      "value": "%s"
                    },
                    "walletType": "MAIN",
                    "authentication": {
                      "type": "PASSWORD",
                      "value": "%s"
                    }
                  },
                  "creditor": {
                    "accountType": "SUBSCRIBER",
                    "identifier": {
                      "type": "MOBILE",
                      "value": "%s"
                    },
                    "walletType": "MAIN"
                  },
                  "transaction": {
                    "amount": 75.00,
                    "currency": "USD"
                  }
                }
                """.formatted(agent.mobile(), agent.password(), subscriber.mobile()));
        response.then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("pricingAmounts.senderTagKey", equalTo(expectedSenderTag))
                .body("pricingAmounts.receiverTagKey", equalTo(expectedReceiverTag));
        assertBigDecimalEquals(new BigDecimal("2.50"), toBigDecimal(response.jsonPath().get("pricingAmounts.serviceChargeAmount")), tenant.tenantId() + " service charge");
    }

    private String performApprovedStockTransaction(
            TenantSpec tenant,
            String initiatingAdminToken,
            String approvingAdminToken,
            BigDecimal amount
    ) {
        return performApprovedStockTransaction(tenant, initiatingAdminToken, approvingAdminToken, amount, "USD");
    }

    private String performApprovedStockTransaction(
            TenantSpec tenant,
            String initiatingAdminToken,
            String approvingAdminToken,
            BigDecimal amount,
            String currency
    ) {
        String transactionId = postJson(
                tenant,
                "initiate " + currency + " stock " + tenant.tenantId(),
                "/api/v1/pay/stockInitiate",
                initiatingAdminToken,
                stockInitiateRequest("mt-stock-" + currency.toLowerCase() + "-" + tenant.tenantId() + "-" + uniqueSuffix(), currency, amount.toPlainString())
        ).then()
                .statusCode(200)
                .body("responseStatus", equalTo("PENDING"))
                .body("operationType", equalTo("STOCK"))
                .body("code", equalTo("STOCK_INITIATED"))
                .body("transactionId", notNullValue())
                .extract()
                .path("transactionId");

        postJson(
                tenant,
                "same admin " + currency + " stock approval should fail " + tenant.tenantId(),
                "/api/v1/pay/stockStatusUpdate",
                initiatingAdminToken,
                stockApprovalRequest(transactionId, "APPROVED", null, "same approver should fail")
        ).then()
                .statusCode(400)
                .body("responseStatus", equalTo("FAILURE"))
                .body("operationType", equalTo("stockStatusUpdate"))
                .body("code", equalTo(ErrorCodes.INVALID_INITIATOR));

        postJson(
                tenant,
                "approve " + currency + " stock " + tenant.tenantId(),
                "/api/v1/pay/stockStatusUpdate",
                approvingAdminToken,
                stockApprovalRequest(transactionId, "APPROVED", null, "approved by different network admin")
        ).then()
                .statusCode(200)
                .body("responseStatus", equalTo("SUCCESS"))
                .body("operationType", equalTo("STOCK"))
                .body("code", equalTo("STOCK_APPROVED"))
                .body("transactionId", equalTo(transactionId));

        assertTransactionStatus(tenant, transactionId, "STOCK", "TS", amount);
        return transactionId;
    }

    private String performApprovedO2CTransaction(
            TenantSpec tenant,
            String initiatingAdminToken,
            String approvingAdminToken,
            BusinessUser channelUser,
            BigDecimal amount
    ) {
        return performApprovedO2CTransaction(tenant, initiatingAdminToken, approvingAdminToken, channelUser, amount, "USD");
    }

    private String performApprovedO2CTransaction(
            TenantSpec tenant,
            String initiatingAdminToken,
            String approvingAdminToken,
            BusinessUser channelUser,
            BigDecimal amount,
            String currency
    ) {
        String transactionId = postJson(
                tenant,
                "initiate " + currency + " O2C " + channelUser.accountType() + " " + tenant.tenantId(),
                "/api/v1/pay/o2c/initiate",
                initiatingAdminToken,
                o2cInitiateRequest(channelUser, amount, currency)
        ).then()
                .statusCode(200)
                .body("responseStatus", equalTo("PENDING"))
                .body("operationType", equalTo("O2C"))
                .body("code", equalTo("O2C_INITIATED"))
                .body("transactionId", notNullValue())
                .extract()
                .path("transactionId");

        postJson(
                tenant,
                "same admin O2C approval should fail " + tenant.tenantId(),
                "/api/v1/pay/o2c/status",
                initiatingAdminToken,
                o2cApprovalRequest(transactionId, "APPROVED", "same approver should fail")
        ).then()
                .statusCode(400)
                .body("responseStatus", equalTo("FAILURE"))
                .body("operationType", equalTo("O2C_STATUS"))
                .body("code", equalTo(ErrorCodes.INVALID_INITIATOR));

        postJson(
                tenant,
                "approve O2C " + channelUser.accountType() + " " + tenant.tenantId(),
                "/api/v1/pay/o2c/status",
                approvingAdminToken,
                o2cApprovalRequest(transactionId, "APPROVED", "approved O2C for " + channelUser.accountType())
        ).then()
                .statusCode(200)
                .body("responseStatus", equalTo("SUCCESS"))
                .body("operationType", equalTo("O2C"))
                .body("code", equalTo("O2C_APPROVED"))
                .body("transactionId", equalTo(transactionId));

        assertTransactionStatus(tenant, transactionId, "O2C", "TS", amount);
        assertMainCurrencyBalance(tenant, channelUser.accountId(), currency, amount);
        return transactionId;
    }

    private String performCashIn(TenantSpec tenant, BusinessUser agent, SubscriberUser subscriber, BigDecimal amount) {
        return performCashIn(tenant, agent, subscriber, amount, "USD");
    }

    private String performCashIn(TenantSpec tenant, BusinessUser agent, SubscriberUser subscriber, BigDecimal amount, String currency) {
        Response response = postJson(tenant, currency + " cash-in " + tenant.tenantId(), "/api/v1/pay/CASHIN", agent.accessToken(), """
                {
                  "operationType": "CASHIN",
                  "requestGateway": "MOBILE",
                  "preferredLang": "en",
                  "initiatedBy": "DEBITOR",
                  "paymentReference": "mt-cashin-%s-%s",
                  "comments": "Multi tenant cash-in",
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
                  }
                }
                """.formatted(
                tenant.tenantId(),
                subscriber.accountId(),
                agent.mobile(),
                agent.password(),
                subscriber.mobile(),
                amount.toPlainString(),
                currency
        ));
        return response.then()
                .statusCode(200)
                .body("responseStatus", equalTo("SUCCESS"))
                .body("operationType", equalTo("CASHIN"))
                .body("code", equalTo("PAYMENT_SUCCESS"))
                .body("transactionId", notNullValue())
                .extract()
                .path("transactionId");
    }

    private String performU2U(TenantSpec tenant, U2UParticipant sender, U2UParticipant receiver, BigDecimal amount) {
        return postJson(tenant, "U2U " + tenant.tenantId(), "/api/v1/pay/U2U", sender.accessToken(), u2uRequest(sender, receiver, amount))
                .then()
                .statusCode(200)
                .body("responseStatus", equalTo("SUCCESS"))
                .body("operationType", equalTo("U2U"))
                .body("code", equalTo("PAYMENT_SUCCESS"))
                .body("transactionId", notNullValue())
                .extract()
                .path("transactionId");
    }

    private String performMerchantPayment(TenantSpec tenant, SubscriberUser subscriber, BusinessUser merchant, BigDecimal amount) {
        return postJson(
                tenant,
                "merchant payment " + tenant.tenantId(),
                "/api/v1/pay/MERCHANTPAY",
                subscriber.accessToken(),
                merchantPaymentRequest(subscriber, merchant, amount)
        ).then()
                .statusCode(200)
                .body("responseStatus", equalTo("SUCCESS"))
                .body("operationType", equalTo("MERCHANTPAY"))
                .body("code", equalTo("PAYMENT_SUCCESS"))
                .body("transactionId", notNullValue())
                .extract()
                .path("transactionId");
    }

    private String performBillPaymentAndSettlement(TenantSpec tenant, SubscriberUser subscriber, BusinessUser biller, BigDecimal amount) {
        return performBillPaymentAndSettlement(tenant, subscriber, biller, amount, true);
    }

    private String performBillPaymentAndSettlement(
            TenantSpec tenant,
            SubscriberUser subscriber,
            BusinessUser biller,
            BigDecimal amount,
            boolean settlementStatus
    ) {
        BigDecimal subscriberBalanceBefore = readMainUsdBalance(tenant, subscriber.accountId());
        BigDecimal billerBalanceBefore = readMainUsdBalance(tenant, biller.accountId());
        Response response = postJson(
                tenant,
                "bill payment " + tenant.tenantId(),
                "/api/v1/pay/BILLPAY",
                subscriber.accessToken(),
                billPaymentRequest(subscriber, biller, amount)
        );
        String transactionId = response.then()
                .statusCode(200)
                .body("responseStatus", equalTo("SUCCESS"))
                .body("operationType", equalTo("BILLPAY"))
                .body("code", equalTo("PAYMENT_SUCCESS"))
                .body("billStatus", equalTo("PENDING"))
                .body("transactionId", notNullValue())
                .body("traceId", notNullValue())
                .extract()
                .path("transactionId");
        String traceId = response.jsonPath().getString("traceId");

        postJson(
                tenant,
                "settle bill payment " + tenant.tenantId(),
                "/api/v1/internal/settletxn",
                biller.accessToken(),
                settleTransactionRequest(traceId, settlementStatus)
        ).then()
                .statusCode(200)
                .body("responseStatus", equalTo("SUCCESS"))
                .body("operationType", equalTo("SETTLETXN"))
                .body("code", equalTo(settlementStatus ? "SETTLEMENT_SUCCESS" : "ROLLBACK_SUCCESS"))
                .body("transactionId", equalTo(transactionId))
                .body("transactionTraceId", equalTo(traceId))
                .body("serviceCode", equalTo("BILLPAY"))
                .body("transferStatus", equalTo(settlementStatus ? "TS" : "TF"));

        if (settlementStatus) {
            assertMainUsdBalance(tenant, subscriber.accountId(), subscriberBalanceBefore.subtract(amount));
            assertMainUsdBalance(tenant, biller.accountId(), billerBalanceBefore.add(amount));
        } else {
            assertMainUsdBalance(tenant, subscriber.accountId(), subscriberBalanceBefore);
            assertMainUsdBalance(tenant, biller.accountId(), billerBalanceBefore);
        }
        return transactionId;
    }

    private String performCashOut(TenantSpec tenant, SubscriberUser subscriber, BusinessUser agent, BigDecimal amount) {
        return postJson(
                tenant,
                "cash-out " + tenant.tenantId(),
                "/api/v1/pay/CASHOUT",
                subscriber.accessToken(),
                cashOutRequest(subscriber, agent, amount)
        ).then()
                .statusCode(200)
                .body("responseStatus", equalTo("SUCCESS"))
                .body("operationType", equalTo("CASHOUT"))
                .body("code", equalTo("PAYMENT_SUCCESS"))
                .body("transactionId", notNullValue())
                .extract()
                .path("transactionId");
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

    private String loginWithPassword(TenantSpec tenant, String stepName, String loginId, String password, String expectedAccountId) {
        String accessToken = postJson(tenant, stepName, "/api/v1/auth/login", loginRequest("req-" + uniqueSuffix(), loginId, password))
                .then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("accountId", equalTo(expectedAccountId))
                .body("accessToken", notNullValue())
                .extract()
                .path("accessToken");
        return accessToken;
    }

    private Response postJson(TenantSpec tenant, String stepName, String path, String requestBody) {
        log.info("Multi-tenant E2E request step={} tenant={} method=POST path={} body={}", stepName, tenant.tenantId(), path, requestBody);
        delayBeforeRequest(tenant, stepName, path);
        Response response = given()
                .contentType(ContentType.JSON)
                .header("X-Tenant-Id", tenant.tenantId())
                .body(requestBody)
                .when()
                .post(path);
        log.info("Multi-tenant E2E response step={} tenant={} status={} body={}", stepName, tenant.tenantId(), response.statusCode(), response.asPrettyString());
        return response;
    }

    private Response postJson(TenantSpec tenant, String stepName, String path, String bearerToken, String requestBody, Object... pathParams) {
        log.info("Multi-tenant E2E request step={} tenant={} method=POST path={} pathParams={} body={}",
                stepName,
                tenant.tenantId(),
                path,
                List.of(pathParams),
                requestBody
        );
        delayBeforeRequest(tenant, stepName, path);
        Response response = given()
                .contentType(ContentType.JSON)
                .header("X-Tenant-Id", tenant.tenantId())
                .header("Authorization", "Bearer " + bearerToken)
                .body(requestBody)
                .when()
                .post(path, pathParams);
        log.info("Multi-tenant E2E response step={} tenant={} status={} body={}", stepName, tenant.tenantId(), response.statusCode(), response.asPrettyString());
        return response;
    }

    private void delayBeforeRequest(TenantSpec tenant, String stepName, String path) {
        int delayMs = ThreadLocalRandom.current().nextInt(MIN_REQUEST_DELAY_MS, MAX_REQUEST_DELAY_MS + 1);
        log.info(
                "Multi-tenant E2E request jitter step={} tenant={} path={} delayMs={}",
                stepName,
                tenant.tenantId(),
                path,
                delayMs
        );
        try {
            TimeUnit.MILLISECONDS.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while delaying request for tenant " + tenant.tenantId(), ex);
        }
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

    private String pinLoginRequest(String requestId, String mobile, String pin) {
        return """
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
                  "comments": "Multi tenant stock initiation for %s wallet",
                  "transaction": {
                    "amount": %s,
                    "currency": "%s"
                  },
                  "metadata": {
                    "scenario": "multi-tenant-stock"
                  },
                  "additionalInfo": {
                    "channel": "multi-tenant-e2e"
                  }
                }
                """.formatted(requestId, currency, amount, currency);
    }

    private String stockApprovalRequest(String transactionId, String status, String errorCode, String comments) {
        String errorCodeField = errorCode == null || errorCode.isBlank()
                ? ""
                : """
                  "errorCode": "%s",
                """.formatted(errorCode);
        return """
                {
                  "transactionId": "%s",
                  "status": "%s",
                %s  "comments": "%s"
                }
                """.formatted(transactionId, status, errorCodeField, comments);
    }

    private String o2cInitiateRequest(BusinessUser channelUser, BigDecimal amount) {
        return o2cInitiateRequest(channelUser, amount, "USD");
    }

    private String o2cInitiateRequest(BusinessUser channelUser, BigDecimal amount, String currency) {
        return """
                {
                  "requestGateway": "WEB",
                  "preferredLang": "en",
                  "paymentReference": "mt-o2c-%s-%s",
                  "comments": "Multi tenant O2C for %s",
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
                    "scenario": "multi-tenant-o2c"
                  }
                }
                """.formatted(
                channelUser.accountType().toLowerCase(),
                channelUser.accountId(),
                channelUser.accountType(),
                channelUser.accountType(),
                channelUser.mobile(),
                amount.toPlainString(),
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

    private String cashOutRequest(SubscriberUser subscriber, BusinessUser agent, BigDecimal amount) {
        return """
                {
                  "operationType": "CASHOUT",
                  "requestGateway": "MOBILE",
                  "preferredLang": "en",
                  "initiatedBy": "DEBITOR",
                  "paymentReference": "mt-cashout-%s-%s",
                  "comments": "Multi tenant cash-out",
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
                    "scenario": "multi-tenant-cashout"
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
                  "paymentReference": "mt-u2u-%s-%s",
                  "comments": "Multi tenant U2U transfer",
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
                    "scenario": "multi-tenant-u2u"
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
                  "paymentReference": "mt-merchantpay-%s-%s",
                  "comments": "Multi tenant merchant payment",
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
                    "scenario": "multi-tenant-merchantpay"
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
                  "paymentReference": "mt-billpay-%s-%s",
                  "comments": "Multi tenant bill payment",
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
                    "scenario": "multi-tenant-billpay"
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
                  "comments": "%s bill settlement from multi tenant E2E",
                  "additionalInfo": {
                    "scenario": "multi-tenant-bill-settlement"
                  }
                }
                """.formatted(
                traceId,
                settlementStatus,
                settlementStatus ? "successful" : "failed"
        );
    }

    private void assertMainUsdBalance(TenantSpec tenant, String accountId, BigDecimal expectedDisplayAmount) {
        assertMainCurrencyBalance(tenant, accountId, "USD", expectedDisplayAmount);
    }

    private void assertMainCurrencyBalance(
            TenantSpec tenant,
            String accountId,
            String currency,
            BigDecimal expectedDisplayAmount
    ) {
        BigDecimal storedExpectedAmount = expectedDisplayAmount.multiply(new BigDecimal("100.00"));
        Map<String, Object> balance = jdbcTemplate.queryForMap("""
                SELECT wb.available_balance,
                       wb.frozen_balance,
                       wb.fic_balance
                FROM %s w
                JOIN %s wb ON wb.wallet_id = w.wallet_id
                WHERE w.account_id = ?
                  AND w.wallet_type = 'MAIN'
                  AND w.currency = ?
                """.formatted(tenantTable(tenant, "wallet"), tenantTable(tenant, "wallet_balance")), accountId, currency);
        assertBigDecimalEquals(storedExpectedAmount, toBigDecimal(balance.get("available_balance")), tenant.tenantId() + " " + currency + " available balance");
        assertBigDecimalEquals(BigDecimal.ZERO, toBigDecimal(balance.get("frozen_balance")), tenant.tenantId() + " " + currency + " frozen balance");
        assertBigDecimalEquals(BigDecimal.ZERO, toBigDecimal(balance.get("fic_balance")), tenant.tenantId() + " " + currency + " fic balance");
    }

    private BigDecimal readMainUsdBalance(TenantSpec tenant, String accountId) {
        BigDecimal storedAmount = jdbcTemplate.queryForObject("""
                SELECT wb.available_balance
                FROM %s w
                JOIN %s wb ON wb.wallet_id = w.wallet_id
                WHERE w.account_id = ?
                  AND w.wallet_type = 'MAIN'
                  AND w.currency = 'USD'
                """.formatted(tenantTable(tenant, "wallet"), tenantTable(tenant, "wallet_balance")), BigDecimal.class, accountId);
        if (storedAmount == null) {
            throw new AssertionError("Expected MAIN USD wallet balance for " + accountId + " in " + tenant.tenantId());
        }
        return storedAmount.divide(new BigDecimal("100.00"));
    }

    private void assertTransactionStatus(
            TenantSpec tenant,
            String transactionId,
            String serviceCode,
            String transferStatus,
            BigDecimal displayAmount
    ) {
        Map<String, Object> transaction = jdbcTemplate.queryForMap("""
                SELECT transaction_id,
                       service_code,
                       transfer_status,
                       transaction_value
                FROM %s
                WHERE transaction_id = ?
                """.formatted(tenantTable(tenant, "transactions")), transactionId);
        BigDecimal expectedStoredAmount = displayAmount.multiply(new BigDecimal("100.00"));
        assertEquals(transactionId, transaction.get("transaction_id"));
        assertEquals(serviceCode, transaction.get("service_code"));
        assertEquals(transferStatus, transaction.get("transfer_status"));
        assertBigDecimalEquals(expectedStoredAmount, toBigDecimal(transaction.get("transaction_value")), tenant.tenantId() + " transaction value");
    }

    private void assertTenantTransactionIdsExist(TenantSpec tenant, List<String> transactionIds) {
        for (String transactionId : transactionIds) {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM %s
                    WHERE transaction_id = ?
                    """.formatted(tenantTable(tenant, "transactions")), Integer.class, transactionId);
            assertEquals(1, count, "Expected transaction " + transactionId + " in tenant " + tenant.tenantId());
        }
    }

    private void assertFinancialTransferTransaction(
            TenantSpec tenant,
            String transactionId,
            String serviceCode,
            String debitorAccountId,
            String creditorAccountId,
            BigDecimal displayAmount
    ) {
        Map<String, Object> transaction = jdbcTemplate.queryForMap("""
                SELECT transaction_id,
                       service_code,
                       transfer_status,
                       transaction_value
                FROM %s
                WHERE transaction_id = ?
                """.formatted(tenantTable(tenant, "transactions")), transactionId);
        BigDecimal expectedStoredAmount = displayAmount.multiply(new BigDecimal("100.00"));
        assertEquals(transactionId, transaction.get("transaction_id"));
        assertEquals(serviceCode, transaction.get("service_code"));
        assertEquals("TS", transaction.get("transfer_status"));
        assertBigDecimalEquals(expectedStoredAmount, toBigDecimal(transaction.get("transaction_value")), tenant.tenantId() + " transaction value");

        List<Map<String, Object>> details = jdbcTemplate.queryForList("""
                SELECT account_id,
                       entry_type,
                       transfer_status,
                       transaction_value
                FROM %s
                WHERE transaction_id = ?
                ORDER BY txn_sequence_number
                """.formatted(tenantTable(tenant, "transaction_details")), transactionId);
        assertEquals(2, details.size());
        assertEquals(debitorAccountId, details.get(0).get("account_id"));
        assertEquals("DR", details.get(0).get("entry_type"));
        assertEquals(creditorAccountId, details.get(1).get("account_id"));
        assertEquals("CR", details.get(1).get("entry_type"));
        assertBigDecimalEquals(expectedStoredAmount, toBigDecimal(details.get(0).get("transaction_value")), tenant.tenantId() + " debit detail amount");
        assertBigDecimalEquals(expectedStoredAmount, toBigDecimal(details.get(1).get("transaction_value")), tenant.tenantId() + " credit detail amount");
    }

    private void assertSubscriberMobileExistsOnlyInTenant(TenantSpec tenant, String mobile, String expectedAccountId) {
        Map<String, Object> account = jdbcTemplate.queryForMap("""
                SELECT account_id,
                       account_type,
                       mobile_number
                FROM %s
                WHERE mobile_number = ?
                """.formatted(tenantTable(tenant, "account")), mobile);
        assertEquals(expectedAccountId, account.get("account_id"));
        assertEquals("SUBSCRIBER", account.get("account_type"));
        assertEquals(mobile, account.get("mobile_number"));
    }

    private void assertTenantDoesNotContainMobile(TenantSpec tenant, String mobile) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM %s
                WHERE mobile_number = ?
                """.formatted(tenantTable(tenant, "account")), Integer.class, mobile);
        assertEquals(0, count, "Tenant " + tenant.tenantId() + " should not contain mobile " + mobile);
    }

    private void assertAccountMobile(TenantSpec tenant, String accountId, String accountType, String mobile) {
        Map<String, Object> account = jdbcTemplate.queryForMap("""
                SELECT account_id,
                       account_type,
                       mobile_number
                FROM %s
                WHERE account_id = ?
                """.formatted(tenantTable(tenant, "account")), accountId);
        assertEquals(accountId, account.get("account_id"));
        assertEquals(accountType, account.get("account_type"));
        assertEquals(mobile, account.get("mobile_number"));
    }

    private void assertDefaultBaseTag(TenantSpec tenant, String accountId, String tagCode, String category) {
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
                """.formatted(tenantTable(tenant, "account_tags"), tenantTable(tenant, "tags")), Integer.class, accountId, tagCode, category);
        assertEquals(1, count, "Expected default base tag " + tagCode + " for " + accountId + " in " + tenant.tenantId());
    }

    private String readLatestCreatedRegistrationOtp(TenantSpec tenant, String mobile) {
        Integer otpValue = jdbcTemplate.queryForObject("""
                SELECT otp_value
                FROM %s
                WHERE mobile_number = ?
                  AND reference_type = 'REGISTRATION'
                  AND status = 'CREATED'
                ORDER BY created_at DESC
                LIMIT 1
                """.formatted(tenantTable(tenant, "otp")), Integer.class, mobile);
        if (otpValue == null) {
            throw new AssertionError("Expected OTP for " + mobile + " in tenant " + tenant.tenantId());
        }
        return String.valueOf(otpValue);
    }

    private void ensureEnumeration(TenantSpec tenant, String enumType, String enumCode, String enumValue, String description) {
        int updated = jdbcTemplate.update("""
                UPDATE %s
                SET enum_value = ?,
                    description = ?,
                    is_active = TRUE
                WHERE UPPER(enum_type) = UPPER(?)
                  AND UPPER(enum_code) = UPPER(?)
                """.formatted(tenantTable(tenant, "enumerations")), enumValue, description, enumType, enumCode);
        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO %s (
                        enum_type,
                        enum_code,
                        enum_value,
                        description,
                        sort_order,
                        is_active,
                        created_at
                    )
                    VALUES (?, ?, ?, ?, 0, TRUE, CURRENT_TIMESTAMP)
                    """.formatted(tenantTable(tenant, "enumerations")), enumType, enumCode, enumValue, description);
        }
    }

    private void assertJwt(String accessToken, String accountId, String authType, String accountType) {
        assertEquals(accountId, jwtService.getClaims(accessToken).getSubject());
        assertEquals(authType, jwtService.getClaims(accessToken).get("authType", String.class));
        assertEquals(accountType, jwtService.getClaims(accessToken).get("scope", String.class));
    }

    private String tenantTable(TenantSpec tenant, String tableName) {
        return tenant.schema() + "." + tableName;
    }

    private String uniqueSuffix() {
        return Long.toUnsignedString(System.nanoTime());
    }

    private String mobileNumber(String prefix, String uniqueSuffix) {
        String digits = uniqueSuffix.replaceAll("\\D", "");
        if (digits.length() < 7) {
            digits = String.format("%7s", digits).replace(' ', '0');
        }
        return prefix + digits.substring(digits.length() - 7);
    }

    private BigDecimal toBigDecimal(Object value) {
        return value instanceof BigDecimal amount ? amount : new BigDecimal(String.valueOf(value));
    }

    private void assertBigDecimalEquals(BigDecimal expected, BigDecimal actual, String label) {
        assertEquals(0, expected.compareTo(actual), "Expected " + label + " to be " + expected + " but was " + actual);
    }
}
