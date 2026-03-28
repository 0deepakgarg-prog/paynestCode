package com.paynest.users.service;

import com.paynest.common.ErrorCodes;
import com.paynest.users.dto.request.RegisterUserRequest;
import com.paynest.users.dto.request.RegistrationRequest;
import com.paynest.users.dto.response.AccountKycDetailsResponse;
import com.paynest.users.entity.Account;
import com.paynest.users.entity.AccountAuth;
import com.paynest.users.entity.AccountIdentifier;
import com.paynest.users.entity.AuthChallenge;
import com.paynest.config.entity.Enumeration;
import com.paynest.users.entity.KycDocument;
import com.paynest.users.entity.Otp;
import com.paynest.users.entity.Role;
import com.paynest.users.entity.Wallet;
import com.paynest.users.entity.WalletBalance;
import com.paynest.exception.ApplicationException;
import com.paynest.users.repository.AccountAuthRepository;
import com.paynest.users.repository.AccountIdentifierRepository;
import com.paynest.users.repository.AccountRepository;
import com.paynest.users.repository.AuthChallengeRepository;
import com.paynest.config.repository.EnumerationRepository;
import com.paynest.users.repository.KycDocumentRepository;
import com.paynest.users.repository.OtpRepository;
import com.paynest.users.repository.RoleRepository;
import com.paynest.users.repository.UserRoleRepository;
import com.paynest.users.repository.WalletBalanceRepository;
import com.paynest.users.repository.WalletRepository;
import com.paynest.config.security.JWTUtils;
import com.paynest.payments.enums.InitiatedBy;
import com.paynest.payments.service.TransactionsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private EnumerationRepository enumerationRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletBalanceRepository walletBalanceRepository;

    @Mock
    private OtpRepository otpRepository;

    @Mock
    private AccountIdentifierRepository accountIdentifierRepository;

    @Mock
    private AccountAuthRepository accountAuthRepository;

    @Mock
    private KycDocumentRepository kycDocumentRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private AuthChallengeRepository authChallengeRepository;

    @Mock
    private WalletService walletService;

    @Mock
    private TransactionsService transactionsService;

    @InjectMocks
    private AccountService accountService;

    @Test
    void generateOtpForRegistration_shouldThrowWhenActiveUserAlreadyExists() {
        RegistrationRequest request = registrationRequest("9999999999");
        Account existingAccount = new Account();
        existingAccount.setStatus("ACTIVE");

        when(accountRepository.findByMobileNumber("9999999999")).thenReturn(Optional.of(existingAccount));

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> accountService.generateOtpForRegistration(request)
        );

        assertEquals(ErrorCodes.USER_EXISTS, exception.getErrorCode());
        verify(otpRepository, never()).save(any(Otp.class));
    }

    @Test
    void generateOtpForRegistration_shouldExpireExistingOtpAndCreateNewOtp() {
        RegistrationRequest request = registrationRequest("9999999999");
        Otp expiredOtp = new Otp();
        expiredOtp.setMobileNumber("9999999999");
        expiredOtp.setReferenceType("REGISTRATION");
        expiredOtp.setStatus("CREATED");
        expiredOtp.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        when(accountRepository.findByMobileNumber("9999999999")).thenReturn(Optional.empty());
        when(otpRepository.findTopByMobileNumberAndReferenceTypeAndStatusOrderByCreatedAtDesc(
                "9999999999",
                "REGISTRATION",
                "CREATED"
        )).thenReturn(Optional.of(expiredOtp));

        accountService.generateOtpForRegistration(request);

        ArgumentCaptor<Otp> otpCaptor = ArgumentCaptor.forClass(Otp.class);
        verify(otpRepository, times(2)).save(otpCaptor.capture());

        List<Otp> savedOtps = otpCaptor.getAllValues();
        assertEquals("EXPIRED", savedOtps.get(0).getStatus());
        assertEquals("CREATED", savedOtps.get(1).getStatus());
        assertEquals("9999999999", savedOtps.get(1).getMobileNumber());
        assertEquals("REGISTRATION", savedOtps.get(1).getReferenceType());
        assertNotNull(savedOtps.get(1).getOtpValue());
    }

    @Test
    void registerAccountByRole_shouldThrowWhenAccountTypeIsUnsupported() {
        RegisterUserRequest request = registerUserRequest("subscriber", "merchantLogin", "MERCHANT");

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> accountService.registerAccountByRole(request)
        );

        assertEquals(ErrorCodes.INVALID_ACCOUNT_TYPE, exception.getErrorCode());
    }

    @Test
    void registerAccountByRole_shouldCreateWalletsAndAuthArtifactsForNonAdminAccounts() {
        RegisterUserRequest request = registerUserRequest("merchant", "merchantLogin", "MERCHANT");
        Role role = new Role();
        role.setRoleId(7L);
        role.setRoleCode("MERCHANT");

        when(accountRepository.findByMobileNumber("9999999999")).thenReturn(Optional.empty());
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus(
                "LOGINID",
                "merchantLogin",
                "ACTIVE"
        )).thenReturn(Optional.empty());
        when(roleRepository.findByRoleCode("MERCHANT")).thenReturn(Optional.of(role));
        when(enumerationRepository.findByEnumTypeAndIsActive("CURRENCY", true))
                .thenReturn(List.of(enumeration("CURRENCY", "USD"), enumeration("CURRENCY", "EUR")));
        when(enumerationRepository.findByEnumTypeAndIsActive("WALLET_TYPE", true))
                .thenReturn(List.of(
                        enumeration("WALLET_TYPE", "MAIN"),
                        enumeration("WALLET_TYPE", "SALARY"),
                        enumeration("WALLET_TYPE", "BONUS")
                ));
        when(walletRepository.getNextWalletId()).thenReturn(101L, 102L);

        Account account = accountService.registerAccountByRole(request);

        assertEquals("MERCHANT", account.getAccountType());
        assertEquals("ACTIVE", account.getStatus());
        assertEquals("9999999999", account.getMobileNumber());

        ArgumentCaptor<List<Wallet>> walletCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<WalletBalance>> walletBalanceCaptor = ArgumentCaptor.forClass(List.class);
        verify(walletRepository).saveAll(walletCaptor.capture());
        verify(walletBalanceRepository).saveAll(walletBalanceCaptor.capture());
        verify(userRoleRepository).save(any());
        verify(accountIdentifierRepository, times(2)).save(any(AccountIdentifier.class));
        verify(accountAuthRepository).save(any());

        List<Wallet> wallets = walletCaptor.getValue();
        assertEquals(2, wallets.size());
        assertEquals(1, wallets.stream().filter(Wallet::getIsDefault).count());
        assertFalse(wallets.stream().anyMatch(wallet -> "SALARY".equalsIgnoreCase(wallet.getWalletType())));
        assertFalse(wallets.stream().anyMatch(wallet -> "BONUS".equalsIgnoreCase(wallet.getWalletType())));
        assertEquals(2, walletBalanceCaptor.getValue().size());
    }

    @Test
    void getAccountWithKycDetails_shouldThrowWhenTokenAccountDiffersFromRequestedAccount() {
        try (MockedStatic<JWTUtils> jwtUtils = org.mockito.Mockito.mockStatic(JWTUtils.class)) {
            jwtUtils.when(JWTUtils::getCurrentAccountId).thenReturn("acc-2");

            ApplicationException exception = assertThrows(
                    ApplicationException.class,
                    () -> accountService.getAccountWithKycDetails("acc-1")
            );

            assertEquals(ErrorCodes.INVALID_PRIVILEGES, exception.getErrorCode());
        }
    }

    @Test
    void getAccountWithKycDetails_shouldReturnAccountAndKycDocumentsWhenAuthorized() {
        Account account = new Account();
        account.setAccountId("acc-1");
        KycDocument kycDocument = new KycDocument();
        kycDocument.setAccountId("acc-1");
        kycDocument.setDocumentType("PASSPORT");

        when(accountRepository.findById("acc-1")).thenReturn(Optional.of(account));
        when(kycDocumentRepository.findByAccountIdAndIsActiveTrue("acc-1")).thenReturn(List.of(kycDocument));

        try (MockedStatic<JWTUtils> jwtUtils = org.mockito.Mockito.mockStatic(JWTUtils.class)) {
            jwtUtils.when(JWTUtils::getCurrentAccountId).thenReturn("acc-1");

            AccountKycDetailsResponse response = accountService.getAccountWithKycDetails("acc-1");

            assertSame(account, response.getAccount());
            assertEquals(1, response.getKycDocuments().size());
            assertSame(kycDocument, response.getKycDocuments().get(0));
        }
    }

    @Test
    void deleteSubscriber_shouldRejectWhenWalletBalanceExceedsThreshold() {
        Account subscriber = new Account();
        subscriber.setAccountId("sub-1");
        subscriber.setAccountType("SUBSCRIBER");
        subscriber.setStatus("ACTIVE");

        Account system = new Account();
        system.setAccountId("SYS0001");
        system.setStatus("ACTIVE");

        Wallet wallet = new Wallet();
        wallet.setWalletId(101L);
        wallet.setAccountId("sub-1");
        wallet.setCurrency("USD");
        wallet.setWalletType("MAIN");
        wallet.setStatus("ACTIVE");

        WalletBalance walletBalance = new WalletBalance();
        walletBalance.setWalletId(101L);
        walletBalance.setAvailableBalance(java.math.BigDecimal.valueOf(11));
        walletBalance.setFrozenBalance(java.math.BigDecimal.ZERO);
        walletBalance.setFicBalance(java.math.BigDecimal.ZERO);

        when(accountRepository.findById("sub-1")).thenReturn(Optional.of(subscriber));
        when(accountRepository.findById("SYS0001")).thenReturn(Optional.of(system));
        when(walletRepository.findByAccountId("sub-1")).thenReturn(List.of(wallet));
        when(walletBalanceRepository.lockBalance(101L)).thenReturn(walletBalance);

        try (MockedStatic<JWTUtils> jwtUtils = org.mockito.Mockito.mockStatic(JWTUtils.class)) {
            jwtUtils.when(JWTUtils::getCurrentAccountType).thenReturn("ADMIN");

            ApplicationException exception = assertThrows(
                    ApplicationException.class,
                    () -> accountService.deleteSubscriber("sub-1")
            );

            assertEquals(ErrorCodes.DELETE_THRESHOLD_EXCEEDED, exception.getErrorCode());
            verify(walletService, never()).debitWallet(any(), any(), anyString());
            verify(accountRepository, never()).save(any(Account.class));
        }
    }

    @Test
    void deleteSubscriber_shouldTransferBalancesAndDeactivateArtifacts() {
        Account subscriber = new Account();
        subscriber.setAccountId("sub-1");
        subscriber.setAccountType("SUBSCRIBER");
        subscriber.setStatus("ACTIVE");

        Account system = new Account();
        system.setAccountId("SYS0001");
        system.setStatus("ACTIVE");

        Wallet subscriberWallet = new Wallet();
        subscriberWallet.setWalletId(101L);
        subscriberWallet.setAccountId("sub-1");
        subscriberWallet.setCurrency("USD");
        subscriberWallet.setWalletType("MAIN");
        subscriberWallet.setStatus("ACTIVE");

        Wallet systemWallet = new Wallet();
        systemWallet.setWalletId(201L);
        systemWallet.setAccountId("SYS0001");
        systemWallet.setCurrency("USD");
        systemWallet.setWalletType("MAIN");
        systemWallet.setStatus("ACTIVE");

        WalletBalance subscriberBalance = new WalletBalance();
        subscriberBalance.setWalletId(101L);
        subscriberBalance.setAvailableBalance(java.math.BigDecimal.valueOf(5));
        subscriberBalance.setFrozenBalance(java.math.BigDecimal.ZERO);
        subscriberBalance.setFicBalance(java.math.BigDecimal.ZERO);

        AccountIdentifier identifier = new AccountIdentifier();
        identifier.setAccountId("sub-1");
        identifier.setAuthId(3001L);
        identifier.setIdentifierType("MOBILE");
        identifier.setIdentifierValue("9999999999");
        identifier.setStatus("ACTIVE");

        AccountAuth accountAuth = new AccountAuth();
        accountAuth.setId(3001L);
        accountAuth.setStatus("ACTIVE");

        AuthChallenge authChallenge = new AuthChallenge();
        authChallenge.setAccountId("sub-1");
        authChallenge.setStatus("ACTIVE");

        when(accountRepository.findById("sub-1")).thenReturn(Optional.of(subscriber));
        when(accountRepository.findById("SYS0001")).thenReturn(Optional.of(system));
        when(walletRepository.findByAccountId("sub-1")).thenReturn(List.of(subscriberWallet));
        when(walletBalanceRepository.lockBalance(101L)).thenReturn(subscriberBalance, subscriberBalance);
        when(walletRepository.findByAccountIdAndCurrencyAndWalletType("SYS0001", "USD", "MAIN"))
                .thenReturn(Optional.of(systemWallet));
        when(accountIdentifierRepository.findByAccountId("sub-1")).thenReturn(List.of(identifier));
        when(accountAuthRepository.findAllById(any())).thenReturn(List.of(accountAuth));
        when(authChallengeRepository.findAllByAccountId("sub-1")).thenReturn(List.of(authChallenge));
        doNothing().when(walletService).debitWallet(any(), any(), anyString());
        doNothing().when(walletService).creditWallet(any(), any(), anyString());

        try (MockedStatic<JWTUtils> jwtUtils = org.mockito.Mockito.mockStatic(JWTUtils.class)) {
            jwtUtils.when(JWTUtils::getCurrentAccountType).thenReturn("ADMIN");
            jwtUtils.when(JWTUtils::getCurrentAccountId).thenReturn("admin-1");

            accountService.deleteSubscriber("sub-1");
        }

        assertEquals("INACTIVE", subscriber.getStatus());
        assertEquals("admin-1", subscriber.getUpdatedBy());
        assertEquals("INACTIVE", subscriberWallet.getStatus());
        assertEquals(Boolean.TRUE, subscriberWallet.getIsLocked());
        assertEquals("INACTIVE", identifier.getStatus());
        assertEquals("INACTIVE", accountAuth.getStatus());
        assertEquals("INACTIVE", authChallenge.getStatus());

        verify(transactionsService).generateTransactionRecord(
                anyString(),
                org.mockito.ArgumentMatchers.eq(java.math.BigDecimal.valueOf(5)),
                org.mockito.ArgumentMatchers.eq("SYSTEM"),
                org.mockito.ArgumentMatchers.eq("ACCOUNT_DELETION"),
                any(AccountIdentifier.class),
                any(AccountIdentifier.class),
                org.mockito.ArgumentMatchers.eq(subscriberWallet),
                org.mockito.ArgumentMatchers.eq(systemWallet),
                org.mockito.ArgumentMatchers.eq(InitiatedBy.DEBITOR)
        );
        verify(walletService).debitWallet(
                eq(subscriberWallet),
                eq(java.math.BigDecimal.valueOf(5)),
                anyString()
        );
        verify(walletService).creditWallet(
                eq(systemWallet),
                eq(java.math.BigDecimal.valueOf(5)),
                anyString()
        );
        verify(accountRepository).save(subscriber);
        verify(walletRepository).saveAll(List.of(subscriberWallet));
        verify(accountIdentifierRepository).saveAll(List.of(identifier));
        verify(accountAuthRepository).saveAll(List.of(accountAuth));
        verify(authChallengeRepository).saveAll(List.of(authChallenge));
    }

    private RegistrationRequest registrationRequest(String mobile) {
        RegistrationRequest.UserData userData = new RegistrationRequest.UserData();
        userData.setMobile(mobile);

        RegistrationRequest request = new RegistrationRequest();
        request.setRequestId("req-1");
        request.setUser(userData);
        return request;
    }

    private RegisterUserRequest registerUserRequest(String accountType, String loginId, String roleCode) {
        RegisterUserRequest.BusinessAccount user = new RegisterUserRequest.BusinessAccount();
        user.setMobileNumber("9999999999");
        user.setAccountType(accountType);
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEmail("test@example.com");
        user.setAddress("Street 1");
        user.setGender("MALE");
        user.setLoginId(loginId);
        user.setRole(roleCode);

        RegisterUserRequest request = new RegisterUserRequest();
        request.setRequestId("req-1");
        request.setUser(user);
        return request;
    }

    private Enumeration enumeration(String type, String code) {
        Enumeration enumeration = new Enumeration();
        enumeration.setEnumType(type);
        enumeration.setEnumCode(code);
        enumeration.setEnumValue(code);
        return enumeration;
    }
}

