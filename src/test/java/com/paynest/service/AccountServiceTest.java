package com.paynest.service;

import com.paynest.dto.request.RegisterUserRequest;
import com.paynest.dto.request.RegistrationRequest;
import com.paynest.dto.response.AccountKycDetailsResponse;
import com.paynest.entity.Account;
import com.paynest.entity.AccountIdentifier;
import com.paynest.entity.Enumeration;
import com.paynest.entity.KycDocument;
import com.paynest.entity.Otp;
import com.paynest.entity.Role;
import com.paynest.entity.Wallet;
import com.paynest.entity.WalletBalance;
import com.paynest.exception.ApplicationException;
import com.paynest.repository.AccountAuthRepository;
import com.paynest.repository.AccountIdentifierRepository;
import com.paynest.repository.AccountRepository;
import com.paynest.repository.EnumerationRepository;
import com.paynest.repository.KycDocumentRepository;
import com.paynest.repository.OtpRepository;
import com.paynest.repository.RoleRepository;
import com.paynest.repository.UserRoleRepository;
import com.paynest.repository.WalletBalanceRepository;
import com.paynest.repository.WalletRepository;
import com.paynest.security.JWTUtils;
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

        assertEquals("USER_EXISTS", exception.getErrorCode());
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

        assertEquals("INVALID_ACCOUNT_TYPE", exception.getErrorCode());
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

            assertEquals("INVALID_PRIVILEGES", exception.getErrorCode());
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
