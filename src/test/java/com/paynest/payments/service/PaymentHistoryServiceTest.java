package com.paynest.payments.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paynest.common.Constants;
import com.paynest.common.ErrorCodes;
import com.paynest.config.security.JWTUtils;
import com.paynest.exception.ApplicationException;
import com.paynest.payments.dto.PaymentHistoryResponse;
import com.paynest.payments.entity.TransactionDetails;
import com.paynest.payments.entity.TransactionDetailsId;
import com.paynest.payments.entity.Transactions;
import com.paynest.payments.repository.TransactionDetailsRepository;
import com.paynest.payments.repository.TransactionsRepository;
import com.paynest.users.entity.Account;
import com.paynest.users.entity.Wallet;
import com.paynest.users.repository.AccountRepository;
import com.paynest.users.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentHistoryServiceTest {

    @Mock
    private TransactionDetailsRepository transactionDetailsRepository;

    @Mock
    private TransactionsRepository transactionsRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private ServiceCatalogService serviceCatalogService;

    private PaymentHistoryService paymentHistoryService;

    @BeforeEach
    void setUp() {
        paymentHistoryService = new PaymentHistoryService(
                transactionDetailsRepository,
                transactionsRepository,
                accountRepository,
                walletRepository,
                serviceCatalogService,
                new ObjectMapper()
        );
    }

    @Test
    void getPaymentHistory_whenLimitIsMissing_returnsAllTransactionsInDescendingOrder() {
        TransactionDetails currentDetail = detail("TXN1", 1L, "acc-1", Constants.TXN_TYPE_DR, "101");
        TransactionDetails counterpartyDetail = detail("TXN1", 2L, "acc-2", Constants.TXN_TYPE_CR, "202");
        Transactions transaction = transaction();
        Account currentAccount = account("acc-1", "Sam", "Sender", "76000001", "SUBSCRIBER");
        Account counterpartyAccount = account("acc-2", "Jane", "Receiver", "77000002", "MERCHANT");
        Wallet currentWallet = wallet(101L, "acc-1", "USD", "MAIN");
        Wallet counterpartyWallet = wallet(202L, "acc-2", "USD", "MAIN");

        when(transactionDetailsRepository.findAll(
                any(Specification.class),
                eq(Sort.by(Sort.Direction.DESC, "transferOn"))
        )).thenReturn(List.of(currentDetail));
        when(transactionsRepository.findAllById(any())).thenReturn(List.of(transaction));
        when(transactionDetailsRepository.findByIdTransactionIdIn(anyCollection()))
                .thenReturn(List.of(currentDetail, counterpartyDetail));
        when(accountRepository.findAllById(any())).thenReturn(List.of(currentAccount, counterpartyAccount));
        when(walletRepository.findAllById(any())).thenReturn(List.of(currentWallet, counterpartyWallet));
        when(serviceCatalogService.resolveServiceName("U2U")).thenReturn("User Transfer");

        try (MockedStatic<JWTUtils> jwtUtils = mockStatic(JWTUtils.class)) {
            jwtUtils.when(JWTUtils::getCurrentAccountId).thenReturn("acc-1");
            jwtUtils.when(JWTUtils::getCurrentAccountType).thenReturn("SUBSCRIBER");

            PaymentHistoryResponse response = paymentHistoryService.getPaymentHistory(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );

            assertEquals(1L, response.getTotalRecords());
            assertEquals("TXN1", response.getTransactions().get(0).getTransactionId());
            assertEquals("User Transfer", response.getTransactions().get(0).getServiceName());
            assertEquals("Success", response.getTransactions().get(0).getStatus());
            assertEquals("Jane Receiver", response.getTransactions().get(0).getCounterpartyName());
            assertEquals("302", response.getTransactions().get(0).getAdditionalInfo().get("transactionCode"));
            assertEquals(false, response.getTransactions().get(0).getAdditionalInfo().containsKey("metadataOnly"));
            assertEquals(101L, response.getTransactions().get(0).getWalletId());
        }
    }

    @Test
    void getPaymentHistory_whenLimitIsPresent_usesOneBasedOffsetAsPageNumber() {
        TransactionDetails currentDetail = detail("TXN1", 1L, "acc-1", Constants.TXN_TYPE_DR, "101");
        Transactions transaction = transaction();

        when(transactionDetailsRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(currentDetail), Pageable.ofSize(10), 83));
        when(transactionsRepository.findAllById(any())).thenReturn(List.of(transaction));
        when(transactionDetailsRepository.findByIdTransactionIdIn(anyCollection()))
                .thenReturn(List.of(currentDetail));
        when(accountRepository.findAllById(any())).thenReturn(List.of(account("acc-1", "Sam", "Sender", "76000001", "SUBSCRIBER")));
        when(walletRepository.findAllById(any())).thenReturn(List.of(wallet(101L, "acc-1", "USD", "MAIN")));
        when(serviceCatalogService.resolveServiceName("U2U")).thenReturn("User Transfer");

        try (MockedStatic<JWTUtils> jwtUtils = mockStatic(JWTUtils.class)) {
            jwtUtils.when(JWTUtils::getCurrentAccountId).thenReturn("acc-1");
            jwtUtils.when(JWTUtils::getCurrentAccountType).thenReturn("SUBSCRIBER");

            PaymentHistoryResponse response = paymentHistoryService.getPaymentHistory(
                    null,
                    "04/07/2025",
                    "07/07/2025",
                    1,
                    10,
                    "WALLET",
                    "ASC",
                    "SUCCESS,FAILED,PENDING"
            );

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(transactionDetailsRepository).findAll(any(Specification.class), pageableCaptor.capture());
            assertEquals(0, pageableCaptor.getValue().getPageNumber());
            assertEquals(10, pageableCaptor.getValue().getPageSize());
            assertEquals(Sort.Direction.ASC, pageableCaptor.getValue().getSort().getOrderFor("transferOn").getDirection());
            assertEquals(83L, response.getTotalRecords());
        }
    }

    @Test
    void getPaymentHistory_whenNonAdminRequestsAnotherAccount_throwsInvalidPrivileges() {
        try (MockedStatic<JWTUtils> jwtUtils = mockStatic(JWTUtils.class)) {
            jwtUtils.when(JWTUtils::getCurrentAccountId).thenReturn("acc-1");
            jwtUtils.when(JWTUtils::getCurrentAccountType).thenReturn("SUBSCRIBER");

            ApplicationException exception = assertThrows(
                    ApplicationException.class,
                    () -> paymentHistoryService.getPaymentHistory(
                            "acc-2",
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null
                    )
            );

            assertEquals(ErrorCodes.INVALID_PRIVILEGES, exception.getErrorCode());
        }
    }

    @Test
    void getPaymentHistory_whenStatusIsUnknown_throwsInvalidStatus() {
        try (MockedStatic<JWTUtils> jwtUtils = mockStatic(JWTUtils.class)) {
            jwtUtils.when(JWTUtils::getCurrentAccountId).thenReturn("acc-1");
            jwtUtils.when(JWTUtils::getCurrentAccountType).thenReturn("SUBSCRIBER");

            ApplicationException exception = assertThrows(
                    ApplicationException.class,
                    () -> paymentHistoryService.getPaymentHistory(
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            "UNKNOWN"
                    )
            );

            assertEquals("INVALID_STATUS", exception.getErrorCode());
        }
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
        detail.setTransactionValue(new BigDecimal("500.00"));
        detail.setApprovedValue(new BigDecimal("500.00"));
        detail.setPreviousBalance(new BigDecimal("194604.37"));
        detail.setPostBalance(new BigDecimal("194104.37"));
        detail.setTransferOn(LocalDateTime.of(2026, 4, 17, 16, 57, 13, 995000000));
        detail.setServiceCode("U2U");
        detail.setTransferStatus(Constants.TRANSACTION_SUCCESS);
        detail.setWalletNumber(walletNumber);
        return detail;
    }

    private Transactions transaction() {
        Transactions transaction = new Transactions();
        transaction.setTransactionId("TXN1");
        transaction.setTransferOn(LocalDateTime.of(2026, 4, 17, 16, 57, 13, 995000000));
        transaction.setTransactionValue(new BigDecimal("500.00"));
        transaction.setTransferStatus(Constants.TRANSACTION_SUCCESS);
        transaction.setRequestGateway("APP");
        transaction.setServiceCode("U2U");
        transaction.setTraceId("trace-1");
        transaction.setPaymentReference("ref-1");
        transaction.setDebitorAccountId("acc-1");
        transaction.setCreditorAccountId("acc-2");
        transaction.setCreatedBy("acc-1");
        transaction.setComments("Test transfer");
        transaction.setMetadata("{\"metadataOnly\":\"ignored\"}");
        transaction.setAdditionalInfo("{\"transactionCode\":\"302\"}");
        return transaction;
    }

    private Account account(
            String accountId,
            String firstName,
            String lastName,
            String mobileNumber,
            String accountType
    ) {
        Account account = new Account();
        account.setAccountId(accountId);
        account.setFirstName(firstName);
        account.setLastName(lastName);
        account.setMobileNumber(mobileNumber);
        account.setAccountType(accountType);
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
