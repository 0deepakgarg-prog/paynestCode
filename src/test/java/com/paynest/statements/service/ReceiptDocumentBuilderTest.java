package com.paynest.statements.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paynest.common.Constants;
import com.paynest.config.PropertyReader;
import com.paynest.payments.entity.TransactionDetails;
import com.paynest.payments.entity.TransactionDetailsId;
import com.paynest.payments.entity.Transactions;
import com.paynest.payments.repository.TransactionDetailsRepository;
import com.paynest.payments.service.ServiceCatalogService;
import com.paynest.statements.dto.ReceiptDocument;
import com.paynest.users.entity.Account;
import com.paynest.users.entity.Wallet;
import com.paynest.users.repository.AccountRepository;
import com.paynest.users.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceiptDocumentBuilderTest {

    @Mock
    private TransactionDetailsRepository transactionDetailsRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private ServiceCatalogService serviceCatalogService;

    @Mock
    private PropertyReader propertyReader;

    private ReceiptDocumentBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new ReceiptDocumentBuilder(
                transactionDetailsRepository,
                accountRepository,
                walletRepository,
                serviceCatalogService,
                propertyReader,
                new ObjectMapper()
        );
        when(propertyReader.getPropertyValue("currency.factor")).thenReturn("100");
    }

    @Test
    void build_shouldMapTransactionToCustomerFacingReceiptDocument() {
        Transactions transaction = transaction("U2U");
        TransactionDetails debitDetail = detail("TXN1", 1L, "acc-1", Constants.TXN_TYPE_DR, "101");
        TransactionDetails creditDetail = detail("TXN1", 2L, "acc-2", Constants.TXN_TYPE_CR, "202");
        Account debtor = account("acc-1", "Sam", "Sender", "76008354", "SUBSCRIBER", "ro");
        Account creditor = account("acc-2", "Jane", "Receiver", "77000002", "SUBSCRIBER", "en");

        when(transactionDetailsRepository.findByIdTransactionId("TXN1"))
                .thenReturn(List.of(creditDetail, debitDetail));
        when(accountRepository.findAllById(any())).thenReturn(List.of(debtor, creditor));
        when(walletRepository.findAllById(any()))
                .thenReturn(List.of(wallet(101L, "acc-1", "USD", "MAIN"), wallet(202L, "acc-2", "USD", "MAIN")));
        when(serviceCatalogService.resolveServiceName("U2U")).thenReturn("User Transfer");

        ReceiptDocument document = builder.build(transaction, "acc-1");

        assertEquals("TXN1", document.getTransactionId());
        assertEquals("User Transfer", document.getServiceName());
        assertEquals("Success", document.getStatus());
        assertEquals("Debit", document.getTransactionDirection());
        assertEquals("17-Apr-26, 04:57:13 PM", document.getTransferOn());
        assertEquals("76008354", document.getAccountMobileNumber());
        assertEquals("ro", document.getPreferredLanguage());
        assertEquals("Sam Sender", document.getDebtor().getAccountName());
        assertEquals("Jane Receiver", document.getCreditor().getAccountName());
        assertEquals(new BigDecimal("500.00"), document.getTransactionAmount());
        assertEquals(new BigDecimal("2.50"), document.getServiceChargePaid());
        assertEquals(new BigDecimal("502.50"), document.getTotalAmountPaid());
        assertEquals(new BigDecimal("194604.37"), document.getPreviousBalance());
        assertEquals(new BigDecimal("194104.37"), document.getPostBalance());
        assertEquals("302", document.getAdditionalInfo().get("transactionCode"));
        assertFalse(document.getAdditionalInfo().containsKey("metadataOnly"));
    }

    @Test
    void build_whenNamesAreMissing_shouldKeepPartyNameEmpty() {
        Transactions transaction = transaction("U2U");
        TransactionDetails debitDetail = detail("TXN1", 1L, "acc-1", Constants.TXN_TYPE_DR, "101");
        TransactionDetails creditDetail = detail("TXN1", 2L, "acc-2", Constants.TXN_TYPE_CR, "202");
        debitDetail.setIdentifierId("76008354");
        creditDetail.setIdentifierId("77000002");
        Account debtor = account("acc-1", null, null, "76008354", "SUBSCRIBER", "ro");
        Account creditor = account("acc-2", "", "", "77000002", "SUBSCRIBER", "en");

        when(transactionDetailsRepository.findByIdTransactionId("TXN1"))
                .thenReturn(List.of(creditDetail, debitDetail));
        when(accountRepository.findAllById(any())).thenReturn(List.of());
        when(accountRepository.findByMobileNumber(any()))
                .thenAnswer(invocation -> switch (invocation.getArgument(0, String.class)) {
                    case "76008354" -> Optional.of(debtor);
                    case "77000002" -> Optional.of(creditor);
                    default -> Optional.empty();
                });
        when(walletRepository.findAllById(any()))
                .thenReturn(List.of(wallet(101L, "acc-1", "USD", "MAIN"), wallet(202L, "acc-2", "USD", "MAIN")));
        when(serviceCatalogService.resolveServiceName("U2U")).thenReturn("User Transfer");

        ReceiptDocument document = builder.build(transaction, "acc-1");

        assertEquals(null, document.getDebtor().getAccountName());
        assertEquals(null, document.getCreditor().getAccountName());
    }

    @Test
    void build_whenAccountIsCredited_shouldNotApplyDebitServiceCharge() {
        Transactions transaction = transaction("CASHIN");
        transaction.setTransactionId("CI1");
        transaction.setDebitorAccountId("agent-1");
        transaction.setCreditorAccountId("sub-1");
        TransactionDetails debitDetail = detail("CI1", 1L, "agent-1", Constants.TXN_TYPE_DR, "101");
        TransactionDetails creditDetail = detail("CI1", 2L, "sub-1", Constants.TXN_TYPE_CR, "202");
        Account agent = account("agent-1", "Alex", "Agent", "90000001", "AGENT", "en");
        Account customer = account("sub-1", "Chris", "Customer", "76000001", "SUBSCRIBER", "en");

        when(transactionDetailsRepository.findByIdTransactionId("CI1"))
                .thenReturn(List.of(creditDetail, debitDetail));
        when(accountRepository.findAllById(any())).thenReturn(List.of(agent, customer));
        when(walletRepository.findAllById(any()))
                .thenReturn(List.of(wallet(101L, "agent-1", "USD", "MAIN"), wallet(202L, "sub-1", "USD", "MAIN")));
        when(serviceCatalogService.resolveServiceName("CASHIN")).thenReturn("Cash In");

        ReceiptDocument document = builder.build(transaction, "sub-1");

        assertEquals("Credit", document.getTransactionDirection());
        assertEquals(new BigDecimal("0.00"), document.getServiceChargePaid());
        assertEquals(new BigDecimal("500.00"), document.getTotalAmountPaid());
    }

    private Transactions transaction(String serviceCode) {
        Transactions transaction = new Transactions();
        transaction.setTransactionId("TXN1");
        transaction.setTransferOn(LocalDateTime.of(2026, 4, 17, 16, 57, 13, 995000000));
        transaction.setTransactionValue(new BigDecimal("50000.00"));
        transaction.setTransferStatus(Constants.TRANSACTION_SUCCESS);
        transaction.setRequestGateway("APP");
        transaction.setServiceCode(serviceCode);
        transaction.setTraceId("trace-1");
        transaction.setPaymentReference("ref-1");
        transaction.setDebitorAccountId("acc-1");
        transaction.setCreditorAccountId("acc-2");
        transaction.setCreatedBy("acc-1");
        transaction.setComments("Test transfer");
        transaction.setFeesDetails("{\"serviceCharge\":\"250.00\"}");
        transaction.setMetadata("{\"metadataOnly\":\"ignored\"}");
        transaction.setAdditionalInfo("{\"transactionCode\":\"302\"}");
        return transaction;
    }

    private TransactionDetails detail(
            String transactionId,
            Long sequenceNumber,
            String accountId,
            String entryType,
            String walletNumber
    ) {
        TransactionDetails detail = new TransactionDetails();
        detail.setId(new TransactionDetailsId(transactionId, sequenceNumber));
        detail.setAccountId(accountId);
        detail.setUserType("SUBSCRIBER");
        detail.setEntryType(entryType);
        detail.setIdentifierId(accountId);
        detail.setSecondIdentifierId("second-" + accountId);
        detail.setTransactionValue(new BigDecimal("50000.00"));
        detail.setApprovedValue(new BigDecimal("50000.00"));
        detail.setPreviousBalance(new BigDecimal("19460437.00"));
        detail.setPostBalance(new BigDecimal("19410437.00"));
        detail.setTransferOn(LocalDateTime.of(2026, 4, 17, 16, 57, 13, 995000000));
        detail.setTransferStatus(Constants.TRANSACTION_SUCCESS);
        detail.setWalletNumber(walletNumber);
        return detail;
    }

    private Account account(
            String accountId,
            String firstName,
            String lastName,
            String mobileNumber,
            String accountType,
            String preferredLanguage
    ) {
        Account account = new Account();
        account.setAccountId(accountId);
        account.setFirstName(firstName);
        account.setLastName(lastName);
        account.setMobileNumber(mobileNumber);
        account.setAccountType(accountType);
        account.setPreferredLang(preferredLanguage);
        return account;
    }

    private Wallet wallet(Long walletId, String accountId, String currency, String walletType) {
        Wallet wallet = new Wallet();
        wallet.setWalletId(walletId);
        wallet.setAccountId(accountId);
        wallet.setCurrency(currency);
        wallet.setWalletType(walletType);
        return wallet;
    }
}
