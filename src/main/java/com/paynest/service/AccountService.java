package com.paynest.service;

import com.paynest.Utilities.IdGenerator;
import com.paynest.dto.request.*;
import com.paynest.dto.response.AccountKycDetailsResponse;
import com.paynest.entity.*;
import com.paynest.exception.ApplicationException;
import com.paynest.repository.*;
import com.paynest.security.JWTUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;
    private final EnumerationRepository enumerationRepository;
    private final WalletRepository walletRepository;
    private final WalletBalanceRepository walletBalanceRepository;
    private final OtpRepository otpRepository;
    private final AccountIdentifierRepository accountIdentifierRepository;
    private final AccountAuthRepository accountAuthRepository;
    private final KycDocumentRepository kycDocumentRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;

    @Transactional
    public Account registerUser(RegistrationRequestWithOtp request) {

        Optional<Account> acc = accountRepository.findByMobileNumber(request.getUser().getMobile());
        if (acc.isPresent() && acc.get().getStatus().equals("ACTIVE")) {
            throw new ApplicationException("USER_EXISTS","User already exists");
        }
        Optional<Otp> otpOpt = otpRepository.findByOtpValue(
                Integer.parseInt(request.getUser().getOtp()));

        if (otpOpt.isEmpty() || !otpOpt.get().getMobileNumber().equals(request.getUser().getMobile()) ||
                !otpOpt.get().getReferenceType().equals("REGISTRATION") ||
                !otpOpt.get().getStatus().equals("CREATED") ||
                otpOpt.get().getExpiresAt().isBefore(java.time.LocalDateTime.now())) {
            throw new ApplicationException("INVALID_OTP","Invalid or expired OTP");
        }else{
            log.info("Otp validation done. registering user");
        }

        List<com.paynest.entity.Enumeration> currencyList =
                enumerationRepository.findByEnumTypeAndIsActive("CURRENCY", true);
        List<com.paynest.entity.Enumeration> walletTypeList =
                enumerationRepository.findByEnumTypeAndIsActive("WALLET_TYPE",  true);

        Account account = new Account();
        account.setAccountId(IdGenerator.generateAccountId());
        account.setMobileNumber(request.getUser().getMobile());
        account.setAccountType("SUBSCRIBER");
        account.setStatus("ACTIVE");
        account.setCreatedAt(java.time.LocalDateTime.now());
        account.setCreatedBy(account.getAccountId());
        accountRepository.save(account);

        List<Wallet> wallets = new ArrayList<>();
        List<WalletBalance> walletBalances = new ArrayList<>();

        for (Enumeration type : walletTypeList) {

            for (Enumeration currency : currencyList) {

                Wallet wallet = new Wallet();
                if(account.getAccountType().equals("SUBSCRIBER") && type.getEnumCode().equals("COMMISSION")){
                    continue;
                }
                wallet.setWalletId(walletRepository.getNextWalletId());
                wallet.setAccountId(account.getAccountId());
                wallet.setCurrency(currency.getEnumCode());
                wallet.setWalletType(type.getEnumCode());
                wallet.setIsDefault(currency.getEnumCode().equals("USD") && type.getEnumCode().equals("MAIN"));
                wallets.add(wallet);
                WalletBalance walletBalance = new WalletBalance();
                walletBalance.setWalletId(wallet.getWalletId());
                walletBalance.setAvailableBalance(BigDecimal.ZERO);
                walletBalance.setFrozenBalance(BigDecimal.ZERO);
                walletBalance.setFicBalance(BigDecimal.ZERO);
                walletBalances.add(walletBalance);
            }
        }

        UserRole userRole = new UserRole();
        userRole.setUserId(account.getAccountId());
        userRole.setRoleId(roleRepository.findByRoleCode("CUSTOMER").get().getRoleId());
        userRole.setAssignedAt(LocalDateTime.now());
        userRole.setAssignedBy(account.getAccountId());

        userRoleRepository.save(userRole);
        walletRepository.saveAll(wallets);
        walletBalanceRepository.saveAll(walletBalances);

        AccountAuth accountAuth = new AccountAuth();
        long accountAuthId = IdGenerator.generateAccountAuthId();
        accountAuth.setId(accountAuthId);
        accountAuth.setAuthType("PIN");
        String pin = IdGenerator.generate4DigitPin();
        String UUID = java.util.UUID.randomUUID().toString();
        accountAuth.setAuthHash(UUID);
        accountAuth.setAuthValue(IdGenerator.hashPin(pin, UUID)); //TODO: Generate random PIN and send to user via notification.
        accountAuth.setIsFirstTimeLogin(true);
        accountAuth.setFailedAttempts(0);


        //TODO : create auths for the user and send welcome notification.
        AccountIdentifier accountIdentifier = new AccountIdentifier();
        accountIdentifier.setAccountId(account.getAccountId());
        accountIdentifier.setIdentifierType("MOBILE");
        accountIdentifier.setIdentifierValue(request.getUser().getMobile());
        accountIdentifier.setStatus("ACTIVE");
        accountIdentifier.setAuthId(accountAuthId);
        accountIdentifierRepository.save(accountIdentifier);
        accountAuthRepository.save(accountAuth);
        return account;
    }

    @Transactional
    public Account registerAccountByRole(RegisterUserRequest accountRequest) {

        String accountType = accountRequest.getUser().getAccountType();
        String normalizedAccountType = accountType == null ? "" : accountType.toUpperCase(Locale.ROOT);
        Set<String> allowedAccountTypes = Set.of("ADMIN", "AGENT", "MERCHANT", "BILLER");
        if (!allowedAccountTypes.contains(normalizedAccountType)) {
            throw new ApplicationException("INVALID_ACCOUNT_TYPE", "Unsupported account type");
        }

        if (accountRequest == null || accountRequest.getUser().getMobileNumber() == null
                || accountRequest.getUser().getMobileNumber().isBlank()) {
            throw new ApplicationException("INVALID_MOBILE", "Mobile number is required");
        }

        Optional<Account> existingAccount = accountRepository.findByMobileNumber(accountRequest.getUser().getMobileNumber());
        if (existingAccount.isPresent() && "ACTIVE".equals(existingAccount.get().getStatus())) {
            throw new ApplicationException("USER_EXISTS", "User already exists");
        }

        Optional<AccountIdentifier> existingLoginId = accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus
                ("LOGINID",accountRequest.getUser().getLoginId(),"ACTIVE");
        if (existingLoginId.isPresent()) {
            throw new ApplicationException("LOGIN_ID_EXISTS", "Login Id already exists");
        }

        Optional<Role> requestRole = roleRepository.findByRoleCode(accountRequest.getUser().getRole());
        if (requestRole.isEmpty()) {
            throw new ApplicationException("INVALID_ROLE", "Role is Invalid");
        }

        List<com.paynest.entity.Enumeration> currencyList =
                enumerationRepository.findByEnumTypeAndIsActive("CURRENCY", true);
        List<com.paynest.entity.Enumeration> walletTypeList =
                enumerationRepository.findByEnumTypeAndIsActive("WALLET_TYPE", true);

        Account account = new Account();
        account.setAccountId(IdGenerator.generateAccountId());
        account.setAccountType(normalizedAccountType);
        account.setStatus("ACTIVE");
        account.setMobileNumber(accountRequest.getUser().getMobileNumber());
        account.setFirstName(accountRequest.getUser().getFirstName());
        account.setLastName(accountRequest.getUser().getLastName());
        account.setEmail(accountRequest.getUser().getEmail());
        account.setAddress(accountRequest.getUser().getAddress());
        account.setGender(accountRequest.getUser().getGender());
        account.setDateOfBirth(accountRequest.getUser().getDateOfBirth());
        account.setPreferredLang(accountRequest.getUser().getPreferredLang());
        account.setNationality(accountRequest.getUser().getNationality());
        account.setSsn(accountRequest.getUser().getSsn());
        account.setRemarks(accountRequest.getUser().getRemarks());
        account.setCreatedAt(java.time.LocalDateTime.now());
       // account.setCreatedBy(accountRequest.getCreatedBy()); TODO : check the logic for created BY
        accountRepository.save(account);

        if(!normalizedAccountType.equalsIgnoreCase("ADMIN")) {
            List<Wallet> wallets = new ArrayList<>();
            List<WalletBalance> walletBalances = new ArrayList<>();
            for (Enumeration type : walletTypeList) {
                if(type.getEnumCode().equalsIgnoreCase("SALARY") ||
                        type.getEnumCode().equalsIgnoreCase("BONUS")){
                    continue;
                }
                for (Enumeration currency : currencyList) {
                    Wallet wallet = new Wallet();
                    wallet.setWalletId(walletRepository.getNextWalletId());
                    wallet.setAccountId(account.getAccountId());
                    wallet.setCurrency(currency.getEnumCode());
                    wallet.setWalletType(type.getEnumCode());
                    wallet.setIsDefault(currency.getEnumCode().equals("USD") && type.getEnumCode().equals("MAIN"));
                    wallets.add(wallet);

                    WalletBalance walletBalance = new WalletBalance();
                    walletBalance.setWalletId(wallet.getWalletId());
                    walletBalance.setAvailableBalance(BigDecimal.ZERO);
                    walletBalance.setFrozenBalance(BigDecimal.ZERO);
                    walletBalance.setFicBalance(BigDecimal.ZERO);
                    walletBalances.add(walletBalance);
                }
            }
            walletRepository.saveAll(wallets);
            walletBalanceRepository.saveAll(walletBalances);

        }

        UserRole userRole = new UserRole();
        userRole.setUserId(account.getAccountId());
        userRole.setRoleId(requestRole.get().getRoleId());
        userRole.setAssignedAt(LocalDateTime.now());
        userRole.setAssignedBy(account.getAccountId());

        AccountAuth accountAuth = new AccountAuth();
        long accountAuthId = IdGenerator.generateAccountAuthId();
        accountAuth.setId(accountAuthId);
        accountAuth.setAuthType("PASSWORD");
        String password = IdGenerator.generatePassword(8);
        log.info("password is : " + password);
        String uuid = java.util.UUID.randomUUID().toString();
        accountAuth.setAuthHash(uuid);
        accountAuth.setAuthValue(IdGenerator.hashPin(password, uuid));
        accountAuth.setIsFirstTimeLogin(true);
        accountAuth.setFailedAttempts(0);

        AccountIdentifier accountIdentifier = new AccountIdentifier();
        accountIdentifier.setAccountId(account.getAccountId());
        accountIdentifier.setIdentifierType("MOBILE");
        accountIdentifier.setIdentifierValue(account.getMobileNumber());
        accountIdentifier.setStatus("ACTIVE");
        accountIdentifier.setAuthId(accountAuthId);

        AccountIdentifier accountIdentifierLoginId = new AccountIdentifier();
        accountIdentifierLoginId.setAccountId(account.getAccountId());
        accountIdentifierLoginId.setIdentifierType("LOGINID");
        accountIdentifierLoginId.setIdentifierValue(accountRequest.getUser().getLoginId());
        accountIdentifierLoginId.setStatus("ACTIVE");
        accountIdentifierLoginId.setAuthId(accountAuthId);

        //TODO : think about adding email as identifier.

        //Send notification for the generated password.
        userRoleRepository.save(userRole);
        accountIdentifierRepository.save(accountIdentifier);
        accountIdentifierRepository.save(accountIdentifierLoginId);
        accountAuthRepository.save(accountAuth);
        return account;
    }

    @Transactional
    public void generateOtpForRegistration(RegistrationRequest request) {

        Optional<Account> account = accountRepository.findByMobileNumber(request.getUser().getMobile());
        if (account.isPresent() && account.get().getStatus().equals("ACTIVE")) {
            throw new ApplicationException("USER_EXISTS","User already exists");
        }

        Optional<Otp> existingOtp = otpRepository.findTopByMobileNumberAndReferenceTypeAndStatusOrderByCreatedAtDesc(
                request.getUser().getMobile(),
                "REGISTRATION",
                "CREATED");
        if (existingOtp.isPresent() && existingOtp.get().getExpiresAt().isBefore(java.time.LocalDateTime.now())) {
            existingOtp.get().setStatus("EXPIRED");
            otpRepository.save(existingOtp.get());
        }else if(existingOtp.isPresent()){
            throw new ApplicationException("OTP_GENERATED","OTP Already generated for this mobile number");
        }
        Otp otp = new Otp();
        otp.setReferenceType("REGISTRATION");
        otp.setMobileNumber(request.getUser().getMobile());
        otp.setOtpValue((int) (Math.random() * 900000) + 100000);
        otp.setStatus("CREATED");
        otp.setExpiresAt(java.time.LocalDateTime.now().plusMinutes(10));
        otpRepository.save(otp);
        log.info("Generated OTP {} for mobile number {}", otp.getOtpValue(), otp.getMobileNumber());

        //TODO: Integrate with SMS gateway to send OTP to user's mobile number
        //Sync up with notification module to send OTP.


        return;
    }



    @Transactional
    public void updateAccountDetails(UpdateAccountRequest request) {

        String accountId = JWTUtils.getCurrentAccountId();

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ApplicationException("INVALID_ACCOUNT", "Account not found"));

        account.setFirstName(request.getUser().getFirstName());
        account.setLastName(request.getUser().getLastName());
        account.setAddress(request.getUser().getAddress());
        account.setGender(request.getUser().getGender().toString());
        if(request.getUser().getPreferredLanguage() == null) {
            account.setPreferredLang("en");
        }else{
            account.setPreferredLang(request.getUser().getPreferredLanguage());
        }
        account.setDateOfBirth(request.getUser().getDob());
        account.setSsn(request.getUser().getSsn());
        account.setNationality(request.getUser().getNationality());
        account.setUpdatedBy(account.getAccountId());
        account.setUpdatedAt(java.time.LocalDateTime.now());
        if(request.getUser().getEmail() !=null){
            account.setEmail(request.getUser().getEmail());

            /*

            //Not sure if this should be done.

            AccountIdentifier accountIdentifierOld = accountIdentifierRepository
                    .findByAccountIdAndStatus(account.getAccountId(),"ACTIVE")
                    .stream()
                    .filter(id -> id.getIdentifierType().equals("MOBILE"))
                    .findFirst()
                    .orElseThrow(() -> new ApplicationException("IDENTIFIER_NOT_FOUND","Account identifier not found"));

            AccountIdentifier accountIdentifier = new AccountIdentifier();
            accountIdentifier.setAccountId(account.getAccountId());
            accountIdentifier.setIdentifierType("EMAIL");
            accountIdentifier.setIdentifierValue(request.getUser().getEmail());
            accountIdentifier.setStatus("ACTIVE");
            accountIdentifier.setAuthId(accountIdentifierOld.getAuthId());
            accountIdentifierRepository.save(accountIdentifier);
            */

        }
        accountRepository.save(account);

    }

    @Transactional
    public void updateAccountKycDetails(AddAccountKycRequest request) {

        log.info("Adding KYC for account");
        String accountId = JWTUtils.getCurrentAccountId();

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ApplicationException("INVALID_ACCOUNT", "Account not found"));

        KycDocument kycDocument = new KycDocument();
        kycDocument.setAccountId(account.getAccountId());
        kycDocument.setDocumentType(request.getKycData().getKycType());
        kycDocument.setDocumentNumber(request.getKycData().getKycValue());
        kycDocument.setIssueDate(request.getKycData().getIssueDate());
        kycDocument.setExpiryDate(request.getKycData().getExpiryDate());
        kycDocument.setIsPrimary(request.getKycData().isPrimary());
        kycDocument.setIsActive(false);
        kycDocument.setVerificationStatus(VerificationStatus.PENDING.toString());
        kycDocument.setDocumentUrl(request.getKycData().getKycImageUrl());
        kycDocumentRepository.save(kycDocument);
    }

    @Transactional(readOnly = true)
    public AccountKycDetailsResponse getAccountWithKycDetails(String accountId) {

        if(!JWTUtils.getCurrentAccountId().equalsIgnoreCase(accountId))
        {
            throw new ApplicationException("INVALID_PRIVILEGES", "Token does not have necessary access");
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ApplicationException("INVALID_ACCOUNT", "Account not found"));
        List<KycDocument> kycDocuments = kycDocumentRepository.findByAccountIdAndIsActiveTrue(accountId);
        return new AccountKycDetailsResponse(account, kycDocuments);
    }

}
