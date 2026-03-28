package com.paynest.users.service;

import com.paynest.Utilities.IdGenerator;
import com.paynest.common.Constants;
import com.paynest.common.ErrorCodes;
import com.paynest.config.entity.Enumeration;
import com.paynest.config.repository.EnumerationRepository;
import com.paynest.payments.enums.InitiatedBy;
import com.paynest.payments.service.TransactionsService;
import com.paynest.users.dto.request.*;
import com.paynest.users.dto.response.AccountKycDetailsResponse;
import com.paynest.users.entity.*;
import com.paynest.exception.ApplicationException;
import com.paynest.users.repository.*;
import com.paynest.config.security.JWTUtils;
import com.paynest.users.enums.IdentifierType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private static final String SYSTEM_ACCOUNT_ID = "SYS0001";
    private static final BigDecimal SUBSCRIBER_DELETE_THRESHOLD = BigDecimal.TEN;
    private static final String ACCOUNT_DELETE_TXN_PREFIX = "AD";
    private static final String ACCOUNT_DELETE_SERVICE_CODE = "ACCOUNT_DELETION";

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
    private final AuthChallengeRepository authChallengeRepository;
    private final WalletService walletService;
    private final TransactionsService transactionsService;

    @Transactional
    public Account registerUser(RegistrationRequestWithOtp request) {

        Optional<Account> acc = accountRepository.findByMobileNumber(request.getUser().getMobile());
        if (acc.isPresent() && acc.get().getStatus().equals("ACTIVE")) {
            throw new ApplicationException(ErrorCodes.USER_EXISTS,"User already exists");
        }
        Optional<Otp> otpOpt = otpRepository.findByOtpValue(
                Integer.parseInt(request.getUser().getOtp()));

        if (otpOpt.isEmpty() || !otpOpt.get().getMobileNumber().equals(request.getUser().getMobile()) ||
                !otpOpt.get().getReferenceType().equals("REGISTRATION") ||
                !otpOpt.get().getStatus().equals("CREATED") ||
                otpOpt.get().getExpiresAt().isBefore(java.time.LocalDateTime.now())) {
            throw new ApplicationException(ErrorCodes.INVALID_OTP,"Invalid or expired OTP");
        }else{
            log.info("Otp validation done. registering user");
        }

        List<Enumeration> currencyList =
                enumerationRepository.findByEnumTypeAndIsActive("CURRENCY", true);
        List<Enumeration> walletTypeList =
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
            throw new ApplicationException(ErrorCodes.INVALID_ACCOUNT_TYPE, "Unsupported account type");
        }

        if (accountRequest == null || accountRequest.getUser().getMobileNumber() == null
                || accountRequest.getUser().getMobileNumber().isBlank()) {
            throw new ApplicationException(ErrorCodes.INVALID_MOBILE, "Mobile number is required");
        }

        Optional<Account> existingAccount = accountRepository.findByMobileNumber(accountRequest.getUser().getMobileNumber());
        if (existingAccount.isPresent() && "ACTIVE".equals(existingAccount.get().getStatus())) {
            throw new ApplicationException(ErrorCodes.USER_EXISTS, "User already exists");
        }

        Optional<AccountIdentifier> existingLoginId = accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus
                ("LOGINID",accountRequest.getUser().getLoginId(),"ACTIVE");
        if (existingLoginId.isPresent()) {
            throw new ApplicationException(ErrorCodes.LOGIN_ID_EXISTS, "Login Id already exists");
        }

        Optional<Role> requestRole = roleRepository.findByRoleCode(accountRequest.getUser().getRole());
        if (requestRole.isEmpty()) {
            throw new ApplicationException(ErrorCodes.INVALID_ROLE, "Role is Invalid");
        }

        List<Enumeration> currencyList =
                enumerationRepository.findByEnumTypeAndIsActive("CURRENCY", true);
        List<Enumeration> walletTypeList =
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
            throw new ApplicationException(ErrorCodes.USER_EXISTS,"User already exists");
        }

        Optional<Otp> existingOtp = otpRepository.findTopByMobileNumberAndReferenceTypeAndStatusOrderByCreatedAtDesc(
                request.getUser().getMobile(),
                "REGISTRATION",
                "CREATED");
        if (existingOtp.isPresent() && existingOtp.get().getExpiresAt().isBefore(java.time.LocalDateTime.now())) {
            existingOtp.get().setStatus("EXPIRED");
            otpRepository.save(existingOtp.get());
        }else if(existingOtp.isPresent()){
            throw new ApplicationException(ErrorCodes.OTP_GENERATED,"OTP Already generated for this mobile number");
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
                .orElseThrow(() -> new ApplicationException(ErrorCodes.INVALID_ACCOUNT, "Account not found"));

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
                    .orElseThrow(() -> new ApplicationException(ErrorCodes.IDENTIFIER_NOT_FOUND,"Account identifier not found"));

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
                .orElseThrow(() -> new ApplicationException(ErrorCodes.INVALID_ACCOUNT, "Account not found"));

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
            throw new ApplicationException(ErrorCodes.INVALID_PRIVILEGES, "Token does not have necessary access");
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ApplicationException(ErrorCodes.INVALID_ACCOUNT, "Account not found"));
        List<KycDocument> kycDocuments = kycDocumentRepository.findByAccountIdAndIsActiveTrue(accountId);
        return new AccountKycDetailsResponse(account, kycDocuments);
    }

    @Transactional
    public void deleteSubscriber(String accountId) {

        if (!"ADMIN".equalsIgnoreCase(JWTUtils.getCurrentAccountType())) {
            throw new ApplicationException(ErrorCodes.INVALID_PRIVILEGES, "Token does not have necessary access");
        }

        Account subscriber = accountRepository.findById(accountId)
                .orElseThrow(() -> new ApplicationException(ErrorCodes.INVALID_ACCOUNT, "Account not found"));

        if (!"SUBSCRIBER".equalsIgnoreCase(subscriber.getAccountType())) {
            throw new ApplicationException(ErrorCodes.INVALID_ACCOUNT_TYPE, "Only subscriber accounts can be deleted");
        }

        if (!Constants.ACCOUNT_STATUS_ACTIVE.equalsIgnoreCase(subscriber.getStatus())) {
            throw new ApplicationException(ErrorCodes.INVALID_ACCOUNT_STATUS, "Subscriber is not active");
        }

        Account systemAccount = accountRepository.findById(SYSTEM_ACCOUNT_ID)
                .orElseThrow(() -> new ApplicationException(ErrorCodes.SYSTEM_ACCOUNT_NOT_FOUND, "System account not found"));

        if (!Constants.ACCOUNT_STATUS_ACTIVE.equalsIgnoreCase(systemAccount.getStatus())) {
            throw new ApplicationException(ErrorCodes.SYSTEM_ACCOUNT_INACTIVE, "System account is not active");
        }

        List<Wallet> subscriberWallets = walletRepository.findByAccountId(accountId);
        validateDeleteThreshold(subscriberWallets);
        transferBalancesToSystemWallet(subscriberWallets);
        deactivateSubscriberArtifacts(subscriber, subscriberWallets);
    }

    private void validateDeleteThreshold(List<Wallet> subscriberWallets) {
        for (Wallet wallet : subscriberWallets) {
            WalletBalance balance = walletBalanceRepository.lockBalance(wallet.getWalletId());
            if (balance.getAvailableBalance().compareTo(SUBSCRIBER_DELETE_THRESHOLD) > 0) {
                throw new ApplicationException(
                        ErrorCodes.DELETE_THRESHOLD_EXCEEDED,
                        "Subscriber cannot be deleted because wallet balance exceeds threshold"
                );
            }
        }
    }

    private void transferBalancesToSystemWallet(List<Wallet> subscriberWallets) {
        for (Wallet subscriberWallet : subscriberWallets) {
            WalletBalance subscriberBalance = walletBalanceRepository.lockBalance(subscriberWallet.getWalletId());
            BigDecimal transferableAmount = subscriberBalance.getAvailableBalance();

            if (transferableAmount == null || transferableAmount.signum() <= 0) {
                continue;
            }

            Wallet systemWallet = walletRepository.findByAccountIdAndCurrencyAndWalletType(
                            SYSTEM_ACCOUNT_ID,
                            subscriberWallet.getCurrency(),
                            subscriberWallet.getWalletType()
                    )
                    .orElseThrow(() -> new ApplicationException(
                            ErrorCodes.SYSTEM_WALLET_NOT_FOUND,
                            "System wallet not found for currency " + subscriberWallet.getCurrency()
                    ));

            if (!Constants.ACCOUNT_STATUS_ACTIVE.equalsIgnoreCase(systemWallet.getStatus())) {
                throw new ApplicationException(ErrorCodes.SYSTEM_WALLET_INACTIVE, "System wallet is not active");
            }

            String txnId = IdGenerator.generateTransactionId(ACCOUNT_DELETE_TXN_PREFIX);
            transactionsService.generateTransactionRecord(
                    txnId,
                    transferableAmount,
                    "SYSTEM",
                    ACCOUNT_DELETE_SERVICE_CODE,
                    buildAccountIdentifier(subscriberWallet.getAccountId()),
                    buildAccountIdentifier(systemWallet.getAccountId()),
                    subscriberWallet,
                    systemWallet,
                    InitiatedBy.DEBITOR
            );
            walletService.debitWallet(subscriberWallet, transferableAmount, txnId);
            walletService.creditWallet(systemWallet, transferableAmount, txnId);
        }
    }

    private AccountIdentifier buildAccountIdentifier(String accountId) {
        AccountIdentifier identifier = new AccountIdentifier();
        identifier.setAccountId(accountId);
        identifier.setIdentifierType(IdentifierType.ACCOUNT_ID.name());
        identifier.setIdentifierValue(accountId);
        identifier.setStatus(Constants.ACCOUNT_STATUS_ACTIVE);
        return identifier;
    }

    private void deactivateSubscriberArtifacts(Account subscriber, List<Wallet> subscriberWallets) {
        LocalDateTime now = LocalDateTime.now();
        String updatedBy = JWTUtils.getCurrentAccountId();

        subscriber.setStatus(Constants.ACCOUNT_STATUS_INACTIVE);
        subscriber.setUpdatedAt(now);
        subscriber.setUpdatedBy(updatedBy);
        accountRepository.save(subscriber);

        for (Wallet wallet : subscriberWallets) {
            wallet.setStatus(Constants.ACCOUNT_STATUS_INACTIVE);
            wallet.setIsLocked(true);
            wallet.setUpdatedAt(now);
        }
        walletRepository.saveAll(subscriberWallets);

        List<AccountIdentifier> identifiers = accountIdentifierRepository.findByAccountId(subscriber.getAccountId());
        for (AccountIdentifier identifier : identifiers) {
            identifier.setStatus(Constants.ACCOUNT_STATUS_INACTIVE);
            identifier.setUpdatedAt(now);
        }
        if (!identifiers.isEmpty()) {
            accountIdentifierRepository.saveAll(identifiers);
        }

        Set<Long> authIds = identifiers.stream()
                .map(AccountIdentifier::getAuthId)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
        List<AccountAuth> authRecords = authIds.isEmpty()
                ? List.of()
                : accountAuthRepository.findAllById(authIds);
        for (AccountAuth auth : authRecords) {
            auth.setStatus(Constants.ACCOUNT_STATUS_INACTIVE);
            auth.setUpdatedAt(now);
        }
        if (!authRecords.isEmpty()) {
            accountAuthRepository.saveAll(authRecords);
        }

        List<AuthChallenge> authChallenges = authChallengeRepository.findAllByAccountId(subscriber.getAccountId());
        for (AuthChallenge authChallenge : authChallenges) {
            authChallenge.setStatus(Constants.ACCOUNT_STATUS_INACTIVE);
        }
        if (!authChallenges.isEmpty()) {
            authChallengeRepository.saveAll(authChallenges);
        }
    }

}

