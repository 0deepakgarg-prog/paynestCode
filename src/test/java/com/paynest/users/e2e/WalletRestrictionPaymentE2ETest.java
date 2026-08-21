package com.paynest.users.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paynest.common.Constants;
import com.paynest.config.PropertyReader;
import com.paynest.config.entity.SupportedLanguage;
import com.paynest.config.repository.EnumerationRepository;
import com.paynest.config.repository.SupportedLanguageRepository;
import com.paynest.config.security.JwtService;
import com.paynest.config.service.TenantRegistryService;
import com.paynest.limits.service.TransactionLimitValidator;
import com.paynest.notifications.service.TransactionNotificationEventPublisher;
import com.paynest.payments.entity.Transactions;
import com.paynest.payments.repository.TransactionDetailsRepository;
import com.paynest.payments.repository.TransactionsRepository;
import com.paynest.payments.repository.WalletLedgerRepository;
import com.paynest.payments.service.BillPayPaymentService;
import com.paynest.payments.service.CashInPaymentService;
import com.paynest.payments.service.CashOutPaymentService;
import com.paynest.payments.service.IntraWalletTransferService;
import com.paynest.payments.service.MerchPayPaymentService;
import com.paynest.payments.service.O2CPaymentService;
import com.paynest.payments.service.StockService;
import com.paynest.payments.service.TransactionSettlementService;
import com.paynest.pricing.service.PricingService;
import com.paynest.users.entity.Account;
import com.paynest.users.entity.AccountIdentifier;
import com.paynest.users.entity.Wallet;
import com.paynest.users.entity.WalletBalance;
import com.paynest.users.entity.WalletRestriction;
import com.paynest.users.repository.AccountIdentifierRepository;
import com.paynest.users.repository.AccountRepository;
import com.paynest.users.repository.WalletBalanceRepository;
import com.paynest.users.repository.WalletRepository;
import com.paynest.users.repository.WalletRestrictionRepository;
import com.paynest.users.service.AuthService;
import com.paynest.users.service.WalletCacheService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WalletRestrictionPaymentE2ETest {

    private static final String TENANT_ID = "tenant-1";
    private static final String TENANT_SCHEMA = "public";
    private static final String TOKEN = "subscriber-token";
    private static final String DEBITOR_ACCOUNT_ID = "ACC-DEBITOR";
    private static final String CREDITOR_ACCOUNT_ID = "ACC-CREDITOR";
    private static final Long DEBITOR_WALLET_ID = 1001L;
    private static final Long CREDITOR_WALLET_ID = 1002L;

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TenantRegistryService tenantRegistryService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private PropertyReader propertyReader;

    @MockBean
    private EnumerationRepository enumerationRepository;

    @MockBean
    private SupportedLanguageRepository supportedLanguageRepository;

    @MockBean
    private AccountIdentifierRepository accountIdentifierRepository;

    @MockBean
    private AccountRepository accountRepository;

    @MockBean
    private WalletRepository walletRepository;

    @MockBean
    private WalletBalanceRepository walletBalanceRepository;

    @MockBean
    private WalletLedgerRepository walletLedgerRepository;

    @MockBean
    private TransactionsRepository transactionsRepository;

    @MockBean
    private TransactionDetailsRepository transactionDetailsRepository;

    @MockBean
    private WalletRestrictionRepository walletRestrictionRepository;

    @MockBean
    private AuthService authService;

    @MockBean
    private WalletCacheService walletCacheService;

    @MockBean
    private TransactionNotificationEventPublisher transactionNotificationEventPublisher;

    @MockBean
    private com.paynest.payments.service.TransactionsService transactionsService;

    @MockBean
    private MerchPayPaymentService merchPayPaymentService;

    @MockBean
    private CashInPaymentService cashInPaymentService;

    @MockBean
    private CashOutPaymentService cashOutPaymentService;

    @MockBean
    private BillPayPaymentService billPayPaymentService;

    @MockBean
    private TransactionSettlementService transactionSettlementService;

    @MockBean
    private StockService stockService;

    @MockBean
    private O2CPaymentService o2cPaymentService;

    @MockBean
    private PricingService pricingService;

    @MockBean
    private IntraWalletTransferService intraWalletTransferService;

    @MockBean
    private TransactionLimitValidator transactionLimitValidator;

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;

        when(tenantRegistryService.getSchema(TENANT_ID)).thenReturn(TENANT_SCHEMA);
        when(tenantRegistryService.getTimeZone(TENANT_ID)).thenReturn("UTC");

        when(jwtService.isTokenValid(TOKEN)).thenReturn(true);
        when(jwtService.extractAccountId(TOKEN)).thenReturn(DEBITOR_ACCOUNT_ID);
        when(jwtService.extractTenant(TOKEN)).thenReturn(TENANT_ID);
        when(jwtService.getClaims(TOKEN)).thenReturn(claims());
        when(userDetailsService.loadUserByUsername(DEBITOR_ACCOUNT_ID)).thenReturn(
                new User(DEBITOR_ACCOUNT_ID, "N/A", AuthorityUtils.createAuthorityList("ROLE_SUBSCRIBER"))
        );

        when(propertyReader.getPropertyValue("operations.allowed"))
                .thenReturn("U2U,MERCHANTPAY,CASHIN,CASHOUT,BILLPAY,O2C,INTRAWALLET");
        when(propertyReader.getPropertyValue("server.instance")).thenReturn("A");
        when(propertyReader.getPropertyValue("currency.factor")).thenReturn("100");

        when(supportedLanguageRepository.findByLanguageCodeIgnoreCaseAndIsActiveTrue("en"))
                .thenReturn(Optional.of(supportedLanguage()));
        when(enumerationRepository.existsByEnumTypeIgnoreCaseAndEnumCodeIgnoreCaseAndIsActiveTrue("CURRENCY", "USD"))
                .thenReturn(true);

        AccountIdentifier debitorIdentifier = identifier(DEBITOR_ACCOUNT_ID, "9999999999");
        AccountIdentifier creditorIdentifier = identifier(CREDITOR_ACCOUNT_ID, "8888888888");
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus(
                "MOBILE",
                "9999999999",
                Constants.ACCOUNT_STATUS_ACTIVE
        )).thenReturn(Optional.of(debitorIdentifier));
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus(
                "MOBILE",
                "8888888888",
                Constants.ACCOUNT_STATUS_ACTIVE
        )).thenReturn(Optional.of(creditorIdentifier));

        when(accountRepository.findByAccountIdAndStatus(DEBITOR_ACCOUNT_ID, Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(List.of(account(DEBITOR_ACCOUNT_ID)));
        when(accountRepository.findByAccountIdAndStatus(CREDITOR_ACCOUNT_ID, Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(List.of(account(CREDITOR_ACCOUNT_ID)));

        Wallet debitorWallet = wallet(DEBITOR_WALLET_ID, DEBITOR_ACCOUNT_ID);
        Wallet creditorWallet = wallet(CREDITOR_WALLET_ID, CREDITOR_ACCOUNT_ID);
        when(walletRepository.findByAccountIdAndCurrencyAndWalletType(DEBITOR_ACCOUNT_ID, "USD", "MAIN"))
                .thenReturn(Optional.of(debitorWallet));
        when(walletRepository.findByAccountIdAndCurrencyAndWalletType(CREDITOR_ACCOUNT_ID, "USD", "MAIN"))
                .thenReturn(Optional.of(creditorWallet));

        when(walletBalanceRepository.lockBalance(DEBITOR_WALLET_ID))
                .thenReturn(balance(DEBITOR_WALLET_ID, "100000.00"));
        when(walletBalanceRepository.lockBalance(CREDITOR_WALLET_ID))
                .thenReturn(balance(CREDITOR_WALLET_ID, "100000.00"));
        when(transactionsRepository.findByTransactionId(anyString())).thenAnswer(invocation -> {
            Transactions transaction = new Transactions();
            transaction.setTransactionId(invocation.getArgument(0));
            transaction.setTransferStatus(Constants.TRANSACTION_INITIATED);
            return transaction;
        });
        when(transactionDetailsRepository.findByIdTransactionId(anyString())).thenReturn(List.of());
    }

    @Test
    void u2uShouldRejectWhenWalletBlocksBothSendAndReceive() throws Exception {
        when(walletRestrictionRepository.findById(DEBITOR_WALLET_ID))
                .thenReturn(Optional.of(restriction(DEBITOR_WALLET_ID, true, "ALL_SERVICES", List.of(), true, "ALL_SERVICES", List.of())));
        when(walletRestrictionRepository.findById(CREDITOR_WALLET_ID))
                .thenReturn(Optional.of(restriction(CREDITOR_WALLET_ID, true, "ALL_SERVICES", List.of(), true, "ALL_SERVICES", List.of())));

        postU2U()
                .then()
                .statusCode(400)
                .body("code", equalTo("WALLET_SEND_BLOCKED"));

        verify(walletLedgerRepository, never()).save(Mockito.any());
    }

    @Test
    void u2uShouldRejectWhenCreditorWalletBlocksReceive() throws Exception {
        when(walletRestrictionRepository.findById(DEBITOR_WALLET_ID)).thenReturn(Optional.empty());
        when(walletRestrictionRepository.findById(CREDITOR_WALLET_ID))
                .thenReturn(Optional.of(restriction(CREDITOR_WALLET_ID, false, null, List.of(), true, "ALL_SERVICES", List.of())));

        postU2U()
                .then()
                .statusCode(400)
                .body("code", equalTo("WALLET_RECEIVE_BLOCKED"));

        verify(walletLedgerRepository, never()).save(Mockito.any());
    }

    @Test
    void u2uShouldRejectWhenDebitorWalletBlocksSend() throws Exception {
        when(walletRestrictionRepository.findById(DEBITOR_WALLET_ID))
                .thenReturn(Optional.of(restriction(DEBITOR_WALLET_ID, true, "ALL_SERVICES", List.of(), false, null, List.of())));
        when(walletRestrictionRepository.findById(CREDITOR_WALLET_ID)).thenReturn(Optional.empty());

        postU2U()
                .then()
                .statusCode(400)
                .body("code", equalTo("WALLET_SEND_BLOCKED"));

        verify(walletLedgerRepository, never()).save(Mockito.any());
    }

    @Test
    void u2uShouldRejectWhenSelectedServicesIncludeU2U() throws Exception {
        when(walletRestrictionRepository.findById(DEBITOR_WALLET_ID))
                .thenReturn(Optional.of(restriction(DEBITOR_WALLET_ID, true, "SELECTED_SERVICES", List.of("U2U"), false, null, List.of())));
        when(walletRestrictionRepository.findById(CREDITOR_WALLET_ID)).thenReturn(Optional.empty());

        postU2U()
                .then()
                .statusCode(400)
                .body("code", equalTo("WALLET_SEND_BLOCKED"));

        verify(walletLedgerRepository, never()).save(Mockito.any());
    }

    @Test
    void u2uShouldGoThroughWhenSelectedServicesDoNotIncludeU2U() throws Exception {
        when(walletRestrictionRepository.findById(DEBITOR_WALLET_ID))
                .thenReturn(Optional.of(restriction(DEBITOR_WALLET_ID, true, "SELECTED_SERVICES", List.of("CASHIN", "BANK_TRANSFER"), false, null, List.of())));
        when(walletRestrictionRepository.findById(CREDITOR_WALLET_ID)).thenReturn(Optional.empty());

        postU2U()
                .then()
                .statusCode(200)
                .body("responseStatus", equalTo("SUCCESS"))
                .body("operationType", equalTo("U2U"));

        verify(walletLedgerRepository, Mockito.times(2)).save(Mockito.any());
    }

    private io.restassured.response.Response postU2U() {
        return given()
                .contentType(ContentType.JSON)
                .header("X-Tenant-Id", TENANT_ID)
                .header("Authorization", "Bearer " + TOKEN)
                .body("""
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
                        """)
                .when()
                .post("/api/v1/pay/U2U");
    }

    private Claims claims() {
        Claims claims = new DefaultClaims();
        claims.setSubject(DEBITOR_ACCOUNT_ID);
        claims.put("tenant", TENANT_ID);
        claims.put("scope", "SUBSCRIBER");
        claims.put("authType", "PIN");
        return claims;
    }

    private SupportedLanguage supportedLanguage() {
        SupportedLanguage language = new SupportedLanguage();
        language.setLanguageCode("en");
        language.setLanguageName("English");
        language.setIsActive(true);
        language.setIsDefault(true);
        return language;
    }

    private AccountIdentifier identifier(String accountId, String identifierValue) {
        AccountIdentifier identifier = new AccountIdentifier();
        identifier.setAccountId(accountId);
        identifier.setIdentifierType("MOBILE");
        identifier.setIdentifierValue(identifierValue);
        identifier.setStatus(Constants.ACCOUNT_STATUS_ACTIVE);
        return identifier;
    }

    private Account account(String accountId) {
        Account account = new Account();
        account.setAccountId(accountId);
        account.setAccountType("SUBSCRIBER");
        account.setStatus(Constants.ACCOUNT_STATUS_ACTIVE);
        return account;
    }

    private Wallet wallet(Long walletId, String accountId) {
        Wallet wallet = new Wallet();
        wallet.setWalletId(walletId);
        wallet.setAccountId(accountId);
        wallet.setCurrency("USD");
        wallet.setWalletType("MAIN");
        wallet.setStatus(Constants.ACCOUNT_STATUS_ACTIVE);
        wallet.setIsLocked(false);
        return wallet;
    }

    private WalletBalance balance(Long walletId, String availableBalance) {
        WalletBalance balance = new WalletBalance();
        balance.setWalletId(walletId);
        balance.setAvailableBalance(new BigDecimal(availableBalance));
        balance.setFrozenBalance(BigDecimal.ZERO);
        balance.setFicBalance(BigDecimal.ZERO);
        return balance;
    }

    private WalletRestriction restriction(
            Long walletId,
            boolean sendBlocked,
            String sendMode,
            List<String> sendServices,
            boolean receiveBlocked,
            String receiveMode,
            List<String> receiveServices) throws Exception {
        WalletRestriction restriction = new WalletRestriction();
        restriction.setWalletId(walletId);
        restriction.setRestrictions(restrictions(sendBlocked, sendMode, sendServices, receiveBlocked, receiveMode, receiveServices));
        return restriction;
    }

    private JsonNode restrictions(
            boolean sendBlocked,
            String sendMode,
            List<String> sendServices,
            boolean receiveBlocked,
            String receiveMode,
            List<String> receiveServices) throws Exception {
        String sendServicesJson = objectMapper.writeValueAsString(sendServices);
        String receiveServicesJson = objectMapper.writeValueAsString(receiveServices);
        return objectMapper.readTree("""
                {
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
                """.formatted(
                sendBlocked,
                sendMode == null ? "null" : "\"" + sendMode + "\"",
                sendServicesJson,
                receiveBlocked,
                receiveMode == null ? "null" : "\"" + receiveMode + "\"",
                receiveServicesJson
        ));
    }
}
