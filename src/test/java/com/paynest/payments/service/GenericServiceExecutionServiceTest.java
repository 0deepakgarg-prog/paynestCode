package com.paynest.payments.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paynest.enums.AccountType;
import com.paynest.enums.RequestGateway;
import com.paynest.payments.dto.GenericServiceExecutionResponse;
import com.paynest.payments.dto.GenericServiceExecutionRequest;
import com.paynest.payments.dto.GenericServiceFinancialInfo;
import com.paynest.payments.dto.Identifier;
import com.paynest.payments.dto.Party;
import com.paynest.payments.entity.ServiceCatalog;
import com.paynest.payments.enums.InitiatedBy;
import com.paynest.payments.repository.ServiceCatalogRepository;
import com.paynest.payments.repository.ThirdPartyResponseRepository;
import com.paynest.payments.repository.TransactionsRepository;
import com.paynest.pricing.dto.response.PricingComputationResponse;
import com.paynest.pricing.service.PricingService;
import com.paynest.users.entity.Account;
import com.paynest.users.entity.AccountIdentifier;
import com.paynest.users.entity.Wallet;
import com.paynest.users.enums.IdentifierType;
import com.paynest.users.enums.WalletType;
import com.paynest.users.repository.AccountIdentifierRepository;
import com.paynest.users.repository.AccountRepository;
import com.paynest.users.repository.WalletRepository;
import com.paynest.users.service.WalletCacheService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenericServiceExecutionServiceTest {

    @Mock
    private ServiceCatalogRepository serviceCatalogRepository;

    @Mock
    private TransactionsRepository transactionsRepository;

    @Mock
    private ThirdPartyResponseRepository thirdPartyResponseRepository;

    @Mock
    private GenericIntegratorClient genericIntegratorClient;

    @Mock
    private PricingService pricingService;

    @Mock
    private PaymentTransactionRecorderService paymentTransactionRecorderService;

    @Mock
    private BalanceService balanceService;

    @Mock
    private AccountIdentifierRepository accountIdentifierRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletCacheService walletCacheService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private GenericServiceExecutionService genericServiceExecutionService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void executeFinancial_shouldRefreshWalletCacheForDebitorAndCreditor() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("DEBITOR_ACC", "pin", List.of())
        );
        GenericServiceExecutionRequest request = financialRequest();
        JsonNode debitorJson = objectMapper.valueToTree(request.getDebitor());
        assertTrue(debitorJson.has("identifier"));
        assertFalse(debitorJson.has("accountId"));
        assertFalse(debitorJson.has("accountCode"));
        ServiceCatalog catalog = serviceCatalog("IPSP2P");
        Account debitorAccount = account("DEBITOR_ACC", "SUBSCRIBER");
        Account creditorAccount = account("CREDITOR_ACC", "SUBSCRIBER");
        AccountIdentifier debitorIdentifier = accountIdentifier("DEBITOR_ACC");
        AccountIdentifier creditorIdentifier = accountIdentifier("CREDITOR_ACC");
        Wallet debitorWallet = wallet(101L, "DEBITOR_ACC");
        Wallet creditorWallet = wallet(102L, "CREDITOR_ACC");

        when(serviceCatalogRepository.findFirstByServiceCodeIgnoreCaseAndIsFinancialAndIsActiveTrue("IPSP2P", true))
                .thenReturn(Optional.of(catalog));
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus(
                "ACCOUNT_ID",
                "DEBITOR_ACC",
                "ACTIVE"
        )).thenReturn(Optional.of(debitorIdentifier));
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus(
                "ACCOUNT_ID",
                "CREDITOR_ACC",
                "ACTIVE"
        )).thenReturn(Optional.of(creditorIdentifier));
        PricingComputationResponse pricingComputation = new PricingComputationResponse();
        pricingComputation.addServiceCharge(new BigDecimal("1.50"));
        pricingComputation.markServiceChargeAffectedParty("SENDER");
        when(pricingService.calculatePricingAmountsForAccounts(
                "IPSP2P",
                "USD",
                new BigDecimal("10.00"),
                "DEBITOR_ACC",
                "CREDITOR_ACC"
        )).thenReturn(pricingComputation);
        when(accountRepository.findByAccountIdAndStatus("DEBITOR_ACC", "ACTIVE")).thenReturn(List.of(debitorAccount));
        when(accountRepository.findByAccountIdAndStatus("CREDITOR_ACC", "ACTIVE")).thenReturn(List.of(creditorAccount));
        when(walletRepository.findByAccountIdAndCurrencyAndWalletType("DEBITOR_ACC", "USD", "MAIN"))
                .thenReturn(Optional.of(debitorWallet));
        when(walletRepository.findByAccountIdAndCurrencyAndWalletType("CREDITOR_ACC", "USD", "MAIN"))
                .thenReturn(Optional.of(creditorWallet));

        GenericServiceExecutionResponse response = genericServiceExecutionService.executeFinancial(request);

        verify(walletCacheService).refreshAccountWallets("DEBITOR_ACC");
        verify(walletCacheService).refreshAccountWallets("CREDITOR_ACC");
        ArgumentCaptor<Map<String, Object>> additionalInfoCaptor = ArgumentCaptor.forClass(Map.class);
        verify(paymentTransactionRecorderService).recordTransaction(
                eq(response.getTransactionId()),
                eq(new BigDecimal("10.00")),
                eq("MOBILE"),
                eq("IPSP2P"),
                eq("en"),
                same(debitorIdentifier),
                same(creditorIdentifier),
                eq("SUBSCRIBER"),
                eq("SUBSCRIBER"),
                same(debitorWallet),
                same(creditorWallet),
                eq(InitiatedBy.DEBITOR),
                isNull(),
                additionalInfoCaptor.capture(),
                eq("ref-1"),
                eq("Internal P2P")
        );
        assertEquals("GEN-001", additionalInfoCaptor.getValue().get("externalRef"));
        assertEquals("generic-flow", additionalInfoCaptor.getValue().get("source"));
        verify(balanceService).transferWalletAmountWithPricing(
                same(debitorWallet),
                same(creditorWallet),
                eq(new BigDecimal("10.00")),
                eq("IPSP2P"),
                eq(InitiatedBy.DEBITOR),
                eq(response.getTransactionId()),
                same(pricingComputation)
        );
        JsonNode responseJson = objectMapper.valueToTree(response);
        assertFalse(responseJson.has("pricingInfo"));
    }

    private GenericServiceExecutionRequest financialRequest() {
        GenericServiceExecutionRequest request = new GenericServiceExecutionRequest();
        request.setServiceCode("IPSP2P");
        request.setRequestGateway(RequestGateway.MOBILE);
        request.setLanguage("en");
        request.setReferenceId("ref-1");
        request.setDebitor(party("DEBITOR_ACC"));
        request.setCreditor(party("CREDITOR_ACC"));
        request.setAdditionalInfo(objectMapper.valueToTree(Map.of(
                "externalRef", "GEN-001",
                "source", "generic-flow"
        )));

        GenericServiceFinancialInfo financialInfo = new GenericServiceFinancialInfo();
        financialInfo.setAmount(new BigDecimal("10.00"));
        financialInfo.setCurrency("USD");
        request.setFinancialInfo(financialInfo);
        return request;
    }

    private Party party(String accountId) {
        Party party = new Party();
        party.setAccountType(AccountType.SUBSCRIBER);
        party.setWalletType(WalletType.MAIN);

        Identifier identifier = new Identifier();
        identifier.setType(IdentifierType.ACCOUNT_ID);
        identifier.setValue(accountId);
        party.setIdentifier(identifier);
        return party;
    }

    private AccountIdentifier accountIdentifier(String accountId) {
        AccountIdentifier identifier = new AccountIdentifier();
        identifier.setAccountId(accountId);
        identifier.setIdentifierType("ACCOUNT_ID");
        identifier.setIdentifierValue(accountId);
        identifier.setStatus("ACTIVE");
        return identifier;
    }

    private ServiceCatalog serviceCatalog(String serviceCode) {
        ServiceCatalog catalog = new ServiceCatalog();
        catalog.setServiceCode(serviceCode);
        catalog.setServiceName("Internal P2P");
        catalog.setServiceCategory("TRANSFER");
        catalog.setTransactionType("TRANSFER");
        catalog.setIsFinancial(true);
        catalog.setSendToIntegrator(false);
        catalog.setRequiresConfirmation(false);
        catalog.setIntegratorCallMode("SYNC");
        catalog.setIsActive(true);
        return catalog;
    }

    private Account account(String accountId, String accountType) {
        Account account = new Account();
        account.setAccountId(accountId);
        account.setAccountType(accountType);
        account.setStatus("ACTIVE");
        return account;
    }

    private Wallet wallet(Long walletId, String accountId) {
        Wallet wallet = new Wallet();
        wallet.setWalletId(walletId);
        wallet.setAccountId(accountId);
        wallet.setCurrency("USD");
        wallet.setWalletType("MAIN");
        wallet.setIsDefault(true);
        wallet.setStatus("ACTIVE");
        return wallet;
    }
}
