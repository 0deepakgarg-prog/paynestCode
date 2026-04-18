package com.paynest.payments.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paynest.common.Constants;
import com.paynest.common.ErrorCodes;
import com.paynest.config.security.JWTUtils;
import com.paynest.exception.ApplicationException;
import com.paynest.payments.dto.TransactionDetailResponse;
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
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionDetailQueryServiceTest {

    @Mock
    private TransactionsRepository transactionsRepository;

    @Mock
    private TransactionDetailsRepository transactionDetailsRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private ServiceCatalogService serviceCatalogService;

    private TransactionDetailQueryService transactionDetailQueryService;

    @BeforeEach
    void setUp() {
        transactionDetailQueryService = new TransactionDetailQueryService(
                transactionsRepository,
                transactionDetailsRepository,
                accountRepository,
                walletRepository,
                serviceCatalogService,
                new ObjectMapper()
        );
    }

    @Test
    void getTransactionDetail_returnsPaynestTransactionDetailForRequestedAccount() {
        Transactions transaction = transaction();
        TransactionDetails debitDetail = detail("TXN1", 1L, "acc-1", Constants.TXN_TYPE_DR, "101");
        TransactionDetails creditDetail = detail("TXN1", 2L, "acc-2", Constants.TXN_TYPE_CR, "202");
        Account debitor = account("acc-1", "Sam", "Sender", "76000001", "SUBSCRIBER");
        Account creditor = account("acc-2", "Jane", "Receiver", "77000002", "MERCHANT");
        Wallet debitorWallet = wallet(101L, "acc-1", "USD", "MAIN");
        Wallet creditorWallet = wallet(202L, "acc-2", "USD", "COMMISSION");

        when(transactionsRepository.findById("TXN1")).thenReturn(Optional.of(transaction));
        when(transactionDetailsRepository.findByIdTransactionId("TXN1"))
                .thenReturn(List.of(creditDetail, debitDetail));
        when(accountRepository.findAllById(any())).thenReturn(List.of(debitor, creditor));
        when(walletRepository.findAllById(any())).thenReturn(List.of(debitorWallet, creditorWallet));
        when(serviceCatalogService.resolveServiceName("U2U")).thenReturn("User Transfer");

        try (MockedStatic<JWTUtils> jwtUtils = mockStatic(JWTUtils.class)) {
            jwtUtils.when(JWTUtils::getCurrentAccountId).thenReturn("acc-1");
            jwtUtils.when(JWTUtils::getCurrentAccountType).thenReturn("SUBSCRIBER");

            TransactionDetailResponse response =
                    transactionDetailQueryService.getTransactionDetail("acc-1", "TXN1");

            assertEquals("TXN1", response.getTransactionId());
            assertEquals("acc-1", response.getAccountId());
            assertEquals("U2U", response.getServiceCode());
            assertEquals("User Transfer", response.getServiceName());
            assertEquals(Constants.TRANSACTION_SUCCESS, response.getTransferStatus());
            assertEquals("Success", response.getStatus());
            assertEquals(Constants.TXN_TYPE_DR, response.getEntryType());
            assertEquals(new BigDecimal("500.00"), response.getTransactionAmount());
            assertEquals(new BigDecimal("500.00"), response.getRequestedAmount());
            assertEquals(new BigDecimal("194604.37"), response.getPreviousBalance());
            assertEquals(new BigDecimal("194104.37"), response.getPostBalance());
            assertEquals("trace-1", response.getTraceId());
            assertEquals("ref-1", response.getPaymentReference());
            assertEquals("Test transfer", response.getRemarks());
            assertEquals("Sam Sender", response.getDebitor().getAccountName());
            assertEquals("Jane Receiver", response.getCreditor().getAccountName());
            assertEquals(101L, response.getDebitor().getWalletId());
            assertEquals("MAIN", response.getDebitor().getWalletType());
            assertEquals(202L, response.getCreditor().getWalletId());
            assertEquals("COMMISSION", response.getCreditor().getWalletType());
            assertEquals(2, response.getEntries().size());
            assertEquals("302", response.getAdditionalInfo().get("transactionCode"));
            assertFalse(response.getAdditionalInfo().containsKey("metadataOnly"));
        }
    }

    @Test
    void getTransactionDetail_whenNonAdminRequestsAnotherAccount_throwsInvalidPrivileges() {
        try (MockedStatic<JWTUtils> jwtUtils = mockStatic(JWTUtils.class)) {
            jwtUtils.when(JWTUtils::getCurrentAccountId).thenReturn("acc-1");
            jwtUtils.when(JWTUtils::getCurrentAccountType).thenReturn("SUBSCRIBER");

            ApplicationException exception = assertThrows(
                    ApplicationException.class,
                    () -> transactionDetailQueryService.getTransactionDetail("acc-2", "TXN1")
            );

            assertEquals(ErrorCodes.INVALID_PRIVILEGES, exception.getErrorCode());
        }
    }

    @Test
    void getTransactionDetail_whenTransactionDoesNotHaveAccountEntry_throwsNotFound() {
        Transactions transaction = transaction();
        TransactionDetails creditDetail = detail("TXN1", 2L, "acc-2", Constants.TXN_TYPE_CR, "202");

        when(transactionsRepository.findById("TXN1")).thenReturn(Optional.of(transaction));
        when(transactionDetailsRepository.findByIdTransactionId("TXN1"))
                .thenReturn(List.of(creditDetail));

        try (MockedStatic<JWTUtils> jwtUtils = mockStatic(JWTUtils.class)) {
            jwtUtils.when(JWTUtils::getCurrentAccountId).thenReturn("acc-1");
            jwtUtils.when(JWTUtils::getCurrentAccountType).thenReturn("SUBSCRIBER");

            ApplicationException exception = assertThrows(
                    ApplicationException.class,
                    () -> transactionDetailQueryService.getTransactionDetail("acc-1", "TXN1")
            );

            assertEquals(ErrorCodes.TRANSACTION_DETAIL_NOT_FOUND, exception.getErrorCode());
        }
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
        detail.setPreviousFicBalance(BigDecimal.ZERO);
        detail.setPostFicBalance(BigDecimal.ZERO);
        detail.setPreviousFrozenBalance(BigDecimal.ZERO);
        detail.setPostFrozenBalance(BigDecimal.ZERO);
        detail.setTransferOn(LocalDateTime.of(2026, 4, 17, 16, 57, 13, 995000000));
        detail.setServiceCode("U2U");
        detail.setTransferStatus(Constants.TRANSACTION_SUCCESS);
        detail.setWalletNumber(walletNumber);
        return detail;
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
