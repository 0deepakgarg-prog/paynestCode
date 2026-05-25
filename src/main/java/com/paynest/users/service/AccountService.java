package com.paynest.users.service;


import com.paynest.config.tenant.TenantTime;
import com.paynest.Utilities.IdGenerator;
import com.paynest.common.Constants;
import com.paynest.common.ErrorCodes;
import com.paynest.config.PropertyReader;
import com.paynest.config.entity.Enumeration;
import com.paynest.config.entity.SupportedLanguage;
import com.paynest.config.repository.EnumerationRepository;
import com.paynest.config.repository.SupportedLanguageRepository;
import com.paynest.config.tenant.TenantContext;
import com.paynest.payments.enums.InitiatedBy;
import com.paynest.payments.service.TransactionsService;
import com.paynest.tag.entity.Tag;
import com.paynest.tag.entity.UserTag;
import com.paynest.tag.repository.TagRepository;
import com.paynest.tag.repository.UserTagRepository;
import com.paynest.users.dto.request.*;
import com.paynest.users.dto.response.AccountStatusChangeResponse;
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
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private static final String SYSTEM_ACCOUNT_ID = "SYS0001";
    private static final BigDecimal SUBSCRIBER_DELETE_THRESHOLD = BigDecimal.TEN;
    private static final String ACCOUNT_DELETE_TXN_PREFIX = "AD";
    private static final String ACCOUNT_DELETE_SERVICE_CODE = "ACCOUNT_DELETION";
    private static final String SYSTEM_CONFIG_ENUM_TYPE = "SYSTEM_CONFIG";
    private static final String TESTING_MODE_ENUM_CODE = "TESTING_MODE";
    private static final String TESTING_MODE_OTP = "0000";
    private static final String TESTING_MODE_PIN = "0000";
    private static final String TESTING_MODE_PASSWORD = "PayNest@123";
    private static final String BASE_TAG_TYPE = "BASE";
    private static final String ENDPOINT_TYPE_MOBILE = "MOBILE";
    private static final String ENDPOINT_TYPE_EMAIL = "EMAIL";
    private static final String SUBSCRIBER_ROLE_CODE = "SUBSCRIBER";
    private static final String ACCOUNT_TYPE_BILLER = "BILLER";
    private static final String ACCOUNT_TYPE_MERCHANT = "MERCHANT";
    private static final String BILLER_CATEGORY_ENUM_TYPE = "BILLER_CATEGORY";
    private static final String BILLER_SUB_CATEGORY_ENUM_TYPE = "BILLER_SUB_CATEGORY";
    private static final Set<String> BILLER_INFO_ACCOUNT_TYPES = Set.of("ADMIN", "AGENT", "MERCHANT", "BILLER");
    private static final Set<String> MERCHANT_INFO_ACCOUNT_TYPES = Set.of("ADMIN", "AGENT", "MERCHANT", "BILLER");
    private static final String IDENTIFIER_TYPE_ACCOUNT_CODE = IdentifierType.ACCOUNT_CODE.name();
    private static final String IDENTIFIER_TYPE_MOBILE = IdentifierType.MOBILE.name();
    private static final String IDENTIFIER_TYPE_LOGIN_ID = IdentifierType.LOGINID.name();
    private static final Pattern ACCOUNT_CODE_PATTERN = Pattern.compile("^[A-Za-z0-9]{1,100}$");
    private static final Pattern BILLER_CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,100}$");
    private static final Pattern MERCHANT_CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,100}$");
    private static final Pattern MCC_CODE_PATTERN = Pattern.compile("^[0-9]{4}$");

    private final AccountRepository accountRepository;
    private final EnumerationRepository enumerationRepository;
    private final SupportedLanguageRepository supportedLanguageRepository;
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
    private final WalletCacheService walletCacheService;
    private final TransactionsService transactionsService;
    private final TagRepository tagRepository;
    private final UserTagRepository userTagRepository;
    private final AccountNotificationEndpointRepository accountNotificationEndpointRepository;
    private final AccountStatusHistoryRepository accountStatusHistoryRepository;
    private final AccountBillerInfoRepository accountBillerInfoRepository;
    private final AccountMerchantInfoRepository accountMerchantInfoRepository;
    private final AccountMerchantMccRepository accountMerchantMccRepository;
    private final PropertyReader propertyReader;

    @Transactional
    public Account registerUser(RegistrationRequestWithOtp request) {
        log.info(
                "Self registration started. tenantId={}, tenantSchema={}, mobile={}",
                TenantContext.getTenantId(),
                TenantContext.getTenant(),
                request != null && request.getUser() != null ? maskMobile(request.getUser().getMobile()) : null
        );

        Optional<Account> acc = accountRepository.findByMobileNumber(request.getUser().getMobile());
        if (acc.isPresent() && acc.get().getStatus().equals("ACTIVE")) {
            throw new ApplicationException(ErrorCodes.USER_EXISTS, "User already exists");
        }
        String accountCode = normalizeOptionalAccountCode(request.getUser().getAccountCode());
        if (accountCode != null) {
            validateAccountCodeIsAvailable(accountCode);
        }
        Optional<Otp> otpOpt = otpRepository.findByOtpValueAndStatusOrderByCreatedAtDesc(
                Integer.parseInt(request.getUser().getOtp()),
                "CREATED"
        );

        if (otpOpt.isEmpty() || !otpOpt.get().getMobileNumber().equals(request.getUser().getMobile()) ||
                !otpOpt.get().getReferenceType().equals("REGISTRATION") ||
                otpOpt.get().getExpiresAt().isBefore(TenantTime.now())) {
            throw new ApplicationException(ErrorCodes.INVALID_OTP, "Invalid or expired OTP");
        } else {
            log.info("Otp validation done. registering user");
        }
        Otp otp = otpOpt.get();
        otp.setStatus("PASSED");
        otp.setVerifiedAt(TenantTime.now());
        otpRepository.save(otp);
        log.info("OTP marked as PASSED after validation. otpId={}, mobile={}", otp.getOtpId(), maskMobile(otp.getMobileNumber()));

        List<Enumeration> currencyList =
                enumerationRepository.findByEnumTypeAndIsActive("CURRENCY", true);
        List<Enumeration> walletTypeList =
                enumerationRepository.findByEnumTypeAndIsActive("WALLET_TYPE", true);
        log.info(
                "Self registration setup data loaded. tenantId={}, tenantSchema={}, mobile={}, activeCurrencyCount={}, activeWalletTypeCount={}",
                TenantContext.getTenantId(),
                TenantContext.getTenant(),
                maskMobile(request.getUser().getMobile()),
                currencyList.size(),
                walletTypeList.size()
        );

        Account account = new Account();
        account.setAccountId(IdGenerator.generateAccountId());
        account.setAccountCode(accountCode);
        account.setMobileNumber(request.getUser().getMobile());
        account.setAccountType("SUBSCRIBER");
        account.setPreferredLang(resolvePreferredLanguage(null));
        account.setStatus("ACTIVE");
        account.setCreatedAt(TenantTime.now());
        account.setCreatedBy(account.getAccountId());
        accountRepository.save(account);
        syncAccountNotificationEndpoints(account);
        log.info(
                "Self registration account persisted. tenantId={}, tenantSchema={}, accountId={}, mobile={}, accountType={}",
                TenantContext.getTenantId(),
                TenantContext.getTenant(),
                account.getAccountId(),
                maskMobile(account.getMobileNumber()),
                account.getAccountType()
        );

        List<Wallet> wallets = new ArrayList<>();
        List<WalletBalance> walletBalances = new ArrayList<>();

        for (Enumeration type : walletTypeList) {

            for (Enumeration currency : currencyList) {

                Wallet wallet = new Wallet();
                if (account.getAccountType().equals("SUBSCRIBER") && type.getEnumCode().equals("COMMISSION")) {
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
        log.info(
                "Self registration role lookup starting. tenantId={}, tenantSchema={}, accountId={}, roleCode={}",
                TenantContext.getTenantId(),
                TenantContext.getTenant(),
                account.getAccountId(),
                SUBSCRIBER_ROLE_CODE
        );
        Optional<Role> subscriberRole = roleRepository.findByRoleCode(SUBSCRIBER_ROLE_CODE);
        if (subscriberRole.isEmpty()) {
            log.error(
                    "Self registration role lookup failed. tenantId={}, tenantSchema={}, accountId={}, roleCode={}, activeCurrencyCount={}, activeWalletTypeCount={}",
                    TenantContext.getTenantId(),
                    TenantContext.getTenant(),
                    account.getAccountId(),
                    SUBSCRIBER_ROLE_CODE,
                    currencyList.size(),
                    walletTypeList.size()
            );
            throw new ApplicationException(ErrorCodes.INVALID_ROLE, "Required role SUBSCRIBER is missing for tenant " + TenantContext.getTenantId());
        }
        log.info(
                "Self registration role lookup completed. tenantId={}, tenantSchema={}, accountId={}, roleCode={}, roleId={}",
                TenantContext.getTenantId(),
                TenantContext.getTenant(),
                account.getAccountId(),
                SUBSCRIBER_ROLE_CODE,
                subscriberRole.get().getRoleId()
        );
        userRole.setRoleId(subscriberRole.get().getRoleId());
        userRole.setAssignedAt(TenantTime.now());
        userRole.setAssignedBy(account.getAccountId());

        userRoleRepository.save(userRole);
        log.info(
                "Self registration user role persisted. tenantId={}, tenantSchema={}, accountId={}, roleCode={}, roleId={}",
                TenantContext.getTenantId(),
                TenantContext.getTenant(),
                account.getAccountId(),
                SUBSCRIBER_ROLE_CODE,
                userRole.getRoleId()
        );
        walletRepository.saveAll(wallets);
        walletBalanceRepository.saveAll(walletBalances);
        log.info(
                "Self registration wallets persisted. tenantId={}, tenantSchema={}, accountId={}, walletCount={}, walletBalanceCount={}",
                TenantContext.getTenantId(),
                TenantContext.getTenant(),
                account.getAccountId(),
                wallets.size(),
                walletBalances.size()
        );
        addDefaultTags(account.getAccountId(), account.getAccountType());

        AccountAuth accountAuth = new AccountAuth();
        long accountAuthId = IdGenerator.generateAccountAuthId();
        accountAuth.setId(accountAuthId);
        accountAuth.setAuthType("PIN");
        String pin = isTestingMode() ? TESTING_MODE_PIN : IdGenerator.generate4DigitPin();
        String UUID = java.util.UUID.randomUUID().toString();
        accountAuth.setAuthHash(UUID);
        accountAuth.setAuthValue(IdGenerator.hashPin(pin, UUID)); //TODO: Generate random PIN and send to user via notification.
        accountAuth.setIsFirstTimeLogin(true);
        accountAuth.setFailedAttempts(0);
        accountAuthRepository.save(accountAuth);

        //TODO : create auths for the user and send welcome notification.
        AccountIdentifier accountIdentifier = buildAccountIdentifier(
                account.getAccountId(),
                accountAuthId,
                IDENTIFIER_TYPE_MOBILE,
                request.getUser().getMobile()
        );
        accountIdentifierRepository.save(accountIdentifier);
        if (accountCode != null) {
            AccountIdentifier accountCodeIdentifier = buildAccountIdentifier(
                    account.getAccountId(),
                    accountAuthId,
                    IDENTIFIER_TYPE_ACCOUNT_CODE,
                    accountCode
            );
            accountIdentifierRepository.save(accountCodeIdentifier);
        }
        log.info(
                "Self registration completed. tenantId={}, tenantSchema={}, accountId={}, authId={}, identifiers={}",
                TenantContext.getTenantId(),
                TenantContext.getTenant(),
                account.getAccountId(),
                accountAuthId,
                accountCode != null ? "[MOBILE,ACCOUNT_CODE]" : "[MOBILE]"
        );

        return account;
    }

    @Transactional
    public Account registerAccountByRole(RegisterUserRequest accountRequest) {

        if (accountRequest == null || accountRequest.getUser() == null) {
            log.warn("RegisterUser validation failed. requestId={}, reason=request_or_user_missing", accountRequest != null ? accountRequest.getRequestId() : null);
            throw new ApplicationException(ErrorCodes.INVALID_REQUEST, "Request body and user details are required");
        }

        RegisterUserRequest.BusinessAccount requestedUser = accountRequest.getUser();
        log.info(
                "RegisterUser service started. requestId={}, accountType={}, role={}, loginId={}, mobile={}, preferredLang={}, actorAccountId={}, actorAccountType={}",
                accountRequest.getRequestId(),
                requestedUser.getAccountType(),
                requestedUser.getRole(),
                requestedUser.getLoginId(),
                maskMobile(requestedUser.getMobileNumber()),
                requestedUser.getPreferredLang(),
                currentAccountIdOrAnonymous(),
                currentAccountTypeOrUnknown()
        );

        String accountType = accountRequest.getUser().getAccountType();
        String normalizedAccountType = accountType == null ? "" : accountType.toUpperCase(Locale.ROOT);
        Set<String> allowedAccountTypes = Set.of("ADMIN", "AGENT", "MERCHANT", "BILLER", "BUSINESS");
        if (!allowedAccountTypes.contains(normalizedAccountType)) {
            log.warn(
                    "RegisterUser validation failed. requestId={}, reason=unsupported_account_type, accountType={}, allowedAccountTypes={}",
                    accountRequest.getRequestId(),
                    accountType,
                    allowedAccountTypes
            );
            throw new ApplicationException(ErrorCodes.INVALID_ACCOUNT_TYPE, "Unsupported account type");
        }

        if (requestedUser.getMobileNumber() == null || requestedUser.getMobileNumber().isBlank()) {
            log.warn("RegisterUser validation failed. requestId={}, reason=mobile_missing", accountRequest.getRequestId());
            throw new ApplicationException(ErrorCodes.INVALID_MOBILE, "Mobile number is required");
        }
        String accountCode = normalizeAndValidateAccountCode(requestedUser.getAccountCode());
        RegisterUserRequest.BillerInfo billerInfo = validateAndNormalizeBillerInfo(
                accountRequest.getBillerInfo(),
                normalizedAccountType,
                accountRequest.getRequestId()
        );
        RegisterUserRequest.MerchantInfo merchantInfo = validateAndNormalizeMerchantInfo(
                accountRequest.getMerchantInfo(),
                normalizedAccountType,
                accountRequest.getRequestId()
        );
        String accountIdentifierCode = resolveAccountIdentifierCode(accountCode, billerInfo, merchantInfo);

        log.info("RegisterUser validation checkpoint. requestId={}, step=checking_existing_mobile, mobile={}",
                accountRequest.getRequestId(), maskMobile(requestedUser.getMobileNumber()));
        Optional<Account> existingAccount = accountRepository.findByMobileNumber(requestedUser.getMobileNumber());
        if (existingAccount.isPresent() && "ACTIVE".equals(existingAccount.get().getStatus())) {
            log.warn(
                    "RegisterUser validation failed. requestId={}, reason=active_mobile_exists, existingAccountId={}, mobile={}",
                    accountRequest.getRequestId(),
                    existingAccount.get().getAccountId(),
                    maskMobile(requestedUser.getMobileNumber())
            );
            throw new ApplicationException(ErrorCodes.USER_EXISTS, "User already exists");
        }

        log.info("RegisterUser validation checkpoint. requestId={}, step=checking_existing_login_id, loginId={}",
                accountRequest.getRequestId(), requestedUser.getLoginId());
        Optional<AccountIdentifier> existingLoginId = accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus
                (IDENTIFIER_TYPE_LOGIN_ID, requestedUser.getLoginId(), Constants.ACCOUNT_STATUS_ACTIVE);
        if (existingLoginId.isPresent()) {
            log.warn(
                    "RegisterUser validation failed. requestId={}, reason=active_login_id_exists, loginId={}, existingAccountId={}",
                    accountRequest.getRequestId(),
                    requestedUser.getLoginId(),
                    existingLoginId.get().getAccountId()
            );
            throw new ApplicationException(ErrorCodes.LOGIN_ID_EXISTS, "Login Id already exists");
        }
        validateAccountCodeIsAvailable(accountIdentifierCode);

        log.info("RegisterUser validation checkpoint. requestId={}, step=checking_role, role={}",
                accountRequest.getRequestId(), requestedUser.getRole());
        Optional<Role> requestRole = roleRepository.findByRoleCode(requestedUser.getRole());
        if (requestRole.isEmpty()) {
            log.warn(
                    "RegisterUser validation failed. requestId={}, reason=role_not_found, role={}",
                    accountRequest.getRequestId(),
                    requestedUser.getRole()
            );
            throw new ApplicationException(ErrorCodes.INVALID_ROLE, "Role is Invalid");
        }

        List<Enumeration> currencyList =
                enumerationRepository.findByEnumTypeAndIsActive("CURRENCY", true);
        List<Enumeration> walletTypeList =
                enumerationRepository.findByEnumTypeAndIsActive("WALLET_TYPE", true);
        log.info(
                "RegisterUser wallet setup data loaded. requestId={}, activeCurrencyCount={}, activeWalletTypeCount={}",
                accountRequest.getRequestId(),
                currencyList.size(),
                walletTypeList.size()
        );

        Account account = new Account();
        account.setAccountId(IdGenerator.generateAccountId());
        account.setAccountCode(accountIdentifierCode);
        account.setAccountType(normalizedAccountType);
        account.setStatus("ACTIVE");
        account.setMobileNumber(requestedUser.getMobileNumber());
        account.setFirstName(requestedUser.getFirstName());
        account.setLastName(requestedUser.getLastName());
        account.setEmail(requestedUser.getEmail());
        account.setAddress(requestedUser.getAddress());
        account.setGender(requestedUser.getGender());
        account.setDateOfBirth(requestedUser.getDateOfBirth());
        account.setPreferredLang(resolvePreferredLanguage(requestedUser.getPreferredLang()));
        account.setNationality(requestedUser.getNationality());
        account.setSsn(requestedUser.getSsn());
        account.setRemarks(requestedUser.getRemarks());
        account.setCreatedAt(TenantTime.now());
        // account.setCreatedBy(accountRequest.getCreatedBy()); TODO : check the logic for created BY
        accountRepository.save(account);
        saveBillerInfoIfPresent(account.getAccountId(), billerInfo);
        saveMerchantInfoIfPresent(account.getAccountId(), merchantInfo);
        syncAccountNotificationEndpoints(account);
        log.info(
                "RegisterUser account persisted. requestId={}, accountId={}, accountType={}, preferredLang={}",
                accountRequest.getRequestId(),
                account.getAccountId(),
                account.getAccountType(),
                account.getPreferredLang()
        );

        if (!normalizedAccountType.equalsIgnoreCase("ADMIN")) {
            List<Wallet> wallets = new ArrayList<>();
            List<WalletBalance> walletBalances = new ArrayList<>();
            for (Enumeration type : walletTypeList) {
                if (type.getEnumCode().equalsIgnoreCase("SALARY") ||
                        type.getEnumCode().equalsIgnoreCase("BONUS")) {
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
            log.info(
                    "RegisterUser wallets persisted. requestId={}, accountId={}, walletCount={}, walletBalanceCount={}",
                    accountRequest.getRequestId(),
                    account.getAccountId(),
                    wallets.size(),
                    walletBalances.size()
            );

        } else {
            log.info("RegisterUser wallet creation skipped for admin account. requestId={}, accountId={}",
                    accountRequest.getRequestId(), account.getAccountId());
        }

        UserRole userRole = new UserRole();
        userRole.setUserId(account.getAccountId());
        userRole.setRoleId(requestRole.get().getRoleId());
        userRole.setAssignedAt(TenantTime.now());
        userRole.setAssignedBy(account.getAccountId());

        AccountAuth accountAuth = new AccountAuth();
        long accountAuthId = IdGenerator.generateAccountAuthId();
        accountAuth.setId(accountAuthId);
        accountAuth.setAuthType("PASSWORD");
        String password = isTestingMode() ? TESTING_MODE_PASSWORD : IdGenerator.generatePassword(8);
        log.info("password is : " + password);
        String uuid = java.util.UUID.randomUUID().toString();
        accountAuth.setAuthHash(uuid);
        accountAuth.setAuthValue(IdGenerator.hashPin(password, uuid));
        accountAuth.setIsFirstTimeLogin(true);
        accountAuth.setFailedAttempts(0);

        AccountIdentifier accountIdentifier = buildAccountIdentifier(
                account.getAccountId(),
                accountAuthId,
                IDENTIFIER_TYPE_MOBILE,
                account.getMobileNumber()
        );
        AccountIdentifier accountIdentifierLoginId = buildAccountIdentifier(
                account.getAccountId(),
                accountAuthId,
                IDENTIFIER_TYPE_LOGIN_ID,
                requestedUser.getLoginId()
        );
        AccountIdentifier accountCodeIdentifier = buildAccountIdentifier(
                account.getAccountId(),
                accountAuthId,
                IDENTIFIER_TYPE_ACCOUNT_CODE,
                accountIdentifierCode
        );

        //TODO : think about adding email as identifier.

        //Send notification for the generated password.
        userRoleRepository.save(userRole);
        accountAuthRepository.save(accountAuth);
        accountIdentifierRepository.save(accountIdentifier);
        accountIdentifierRepository.save(accountIdentifierLoginId);
        accountIdentifierRepository.save(accountCodeIdentifier);
        log.info(
                "RegisterUser auth and identifiers persisted. requestId={}, accountId={}, authId={}, identifiers=[MOBILE,LOGINID,ACCOUNT_CODE]",
                accountRequest.getRequestId(),
                account.getAccountId(),
                accountAuthId
        );

        addDefaultTags(account.getAccountId(), account.getAccountType());
        log.info(
                "RegisterUser service completed. requestId={}, accountId={}, accountType={}, role={}",
                accountRequest.getRequestId(),
                account.getAccountId(),
                account.getAccountType(),
                requestedUser.getRole()
        );
        return account;
    }

    private void addDefaultTags(String accountId, String accountType) {
        if (accountType == null || accountType.isBlank()) {
            return;
        }

        List<UserTag> defaultTags = tagRepository
                .findByCategoryIgnoreCaseAndTagTypeIgnoreCaseAndIsDefaultTrueAndStatusIgnoreCase(
                        accountType,
                        BASE_TAG_TYPE,
                        "ACTIVE"
                )
                .stream()
                .filter(tag -> userTagRepository.findByAccountIdAndTagId(accountId, tag.getTagId()).isEmpty())
                .map(tag -> buildDefaultUserTag(accountId, tag))
                .toList();

        if (!defaultTags.isEmpty()) {
            userTagRepository.saveAll(defaultTags);
        }
    }

    private boolean isTestingMode() {
        return enumerationRepository
                .findByEnumTypeIgnoreCaseAndEnumCodeIgnoreCaseAndIsActiveTrue(
                        SYSTEM_CONFIG_ENUM_TYPE,
                        TESTING_MODE_ENUM_CODE
                )
                .map(Enumeration::getEnumValue)
                .map(String::trim)
                .map(value -> value.equalsIgnoreCase("true")
                        || value.equalsIgnoreCase("yes")
                        || value.equalsIgnoreCase("y")
                        || value.equals("1"))
                .orElse(false);
    }

    private String normalizeAndValidateAccountCode(String accountCode) {
        if (accountCode == null || accountCode.isBlank()) {
            throw new ApplicationException(ErrorCodes.INVALID_REQUEST, "Account code is required");
        }
        String normalizedAccountCode = accountCode.trim();
        if (!ACCOUNT_CODE_PATTERN.matcher(normalizedAccountCode).matches()) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_REQUEST,
                    "Account code must be alphanumeric and must not exceed 100 characters"
            );
        }
        return normalizedAccountCode;
    }

    private String normalizeOptionalAccountCode(String accountCode) {
        if (accountCode == null || accountCode.isBlank()) {
            return null;
        }
        return normalizeAndValidateAccountCode(accountCode);
    }

    private String resolveAccountIdentifierCode(
            String accountCode,
            RegisterUserRequest.BillerInfo billerInfo,
            RegisterUserRequest.MerchantInfo merchantInfo
    ) {
        if (billerInfo != null) {
            return billerInfo.getBillerCode();
        }
        if (merchantInfo != null) {
            return merchantInfo.getMerchantCode();
        }
        return accountCode;
    }

    private void validateAccountCodeIsAvailable(String accountCode) {
        Optional<AccountIdentifier> existingAccountCode = accountIdentifierRepository
                .findByIdentifierTypeAndIdentifierValueAndStatus(
                        IDENTIFIER_TYPE_ACCOUNT_CODE,
                        accountCode,
                        Constants.ACCOUNT_STATUS_ACTIVE
                );
        if (existingAccountCode.isPresent()) {
            throw new ApplicationException(ErrorCodes.ACCOUNT_CODE_EXISTS, "Account code already exists");
        }
    }

    private RegisterUserRequest.BillerInfo validateAndNormalizeBillerInfo(
            RegisterUserRequest.BillerInfo billerInfo,
            String normalizedAccountType,
            String requestId
    ) {
        if (ACCOUNT_TYPE_BILLER.equals(normalizedAccountType) && billerInfo == null) {
            throw new ApplicationException(ErrorCodes.INVALID_REQUEST, "billerInfo is required for BILLER accounts");
        }

        if (billerInfo == null) {
            return null;
        }

        if (!BILLER_INFO_ACCOUNT_TYPES.contains(normalizedAccountType)) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_REQUEST,
                    "billerInfo is supported only for ADMIN, AGENT, MERCHANT, and BILLER accounts"
            );
        }

        String billerCategory = normalizeRequiredBillerText(
                billerInfo.getBillerCategory(),
                "billerCategory is required when billerInfo is provided"
        );
        String billerCode = normalizeRequiredBillerText(
                billerInfo.getBillerCode(),
                "billerCode is required when billerInfo is provided"
        );

        if (!BILLER_CODE_PATTERN.matcher(billerCode).matches()) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_REQUEST,
                    "billerCode must be alphanumeric and may include underscore or hyphen, with a maximum length of 100"
            );
        }

        Enumeration categoryEnumeration = enumerationRepository
                .findByEnumTypeIgnoreCaseAndEnumCodeIgnoreCaseAndIsActiveTrue(
                        BILLER_CATEGORY_ENUM_TYPE,
                        billerCategory
                )
                .orElse(null);
        if (categoryEnumeration == null) {
            throw new ApplicationException(ErrorCodes.INVALID_REQUEST, "Invalid billerCategory");
        }

        if (accountBillerInfoRepository.existsByBillerCodeIgnoreCase(billerCode)) {
            throw new ApplicationException(ErrorCodes.INVALID_REQUEST, "billerCode already exists");
        }

        billerInfo.setBillerCategory(billerCategory.toUpperCase(Locale.ROOT));
        billerInfo.setBillerCode(billerCode);
        if (billerInfo.getBillerSubCategory() != null) {
            String billerSubCategory = trimToNull(billerInfo.getBillerSubCategory());
            if (billerSubCategory != null && !enumerationRepository.existsByEnumTypeIgnoreCaseAndEnumCodeIgnoreCaseAndParentEnumIdAndIsActiveTrue(
                    BILLER_SUB_CATEGORY_ENUM_TYPE,
                    billerSubCategory,
                    categoryEnumeration.getId()
            )) {
                throw new ApplicationException(ErrorCodes.INVALID_REQUEST, "Invalid billerSubCategory for billerCategory");
            }
            billerInfo.setBillerSubCategory(billerSubCategory == null ? null : billerSubCategory.toUpperCase(Locale.ROOT));
        }

        log.info(
                "RegisterUser billerInfo validated. requestId={}, accountType={}, billerCode={}, billerCategory={}",
                requestId,
                normalizedAccountType,
                billerInfo.getBillerCode(),
                billerInfo.getBillerCategory()
        );
        return billerInfo;
    }

    private String normalizeRequiredBillerText(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new ApplicationException(ErrorCodes.INVALID_REQUEST, message);
        }
        return normalized;
    }

    private RegisterUserRequest.MerchantInfo validateAndNormalizeMerchantInfo(
            RegisterUserRequest.MerchantInfo merchantInfo,
            String normalizedAccountType,
            String requestId
    ) {
        if (ACCOUNT_TYPE_MERCHANT.equals(normalizedAccountType) && merchantInfo == null) {
            throw new ApplicationException(ErrorCodes.INVALID_REQUEST, "merchantInfo is required for MERCHANT accounts");
        }

        if (merchantInfo == null) {
            return null;
        }

        if (!MERCHANT_INFO_ACCOUNT_TYPES.contains(normalizedAccountType)) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_REQUEST,
                    "merchantInfo is supported only for ADMIN, AGENT, MERCHANT, and BILLER accounts"
            );
        }

        String merchantCode = normalizeRequiredBillerText(
                merchantInfo.getMerchantCode(),
                "merchantCode is required when merchantInfo is provided"
        );
        if (!MERCHANT_CODE_PATTERN.matcher(merchantCode).matches()) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_REQUEST,
                    "merchantCode must be alphanumeric and may include underscore or hyphen, with a maximum length of 100"
            );
        }

        if (accountMerchantInfoRepository.existsByMerchantCodeIgnoreCase(merchantCode)) {
            throw new ApplicationException(ErrorCodes.INVALID_REQUEST, "merchantCode already exists");
        }

        List<String> mccCodes = merchantInfo.getMccCodes();
        if (mccCodes == null || mccCodes.isEmpty()) {
            throw new ApplicationException(ErrorCodes.INVALID_REQUEST, "mccCodes is required when merchantInfo is provided");
        }

        LinkedHashSet<String> normalizedMccCodes = new LinkedHashSet<>();
        for (String mccCode : mccCodes) {
            String normalizedMccCode = trimToNull(mccCode);
            if (normalizedMccCode == null || !MCC_CODE_PATTERN.matcher(normalizedMccCode).matches()) {
                throw new ApplicationException(ErrorCodes.INVALID_REQUEST, "Each mccCode must be a 4 digit value");
            }
            normalizedMccCodes.add(normalizedMccCode);
        }

        merchantInfo.setMerchantCode(merchantCode);
        merchantInfo.setMccCodes(new ArrayList<>(normalizedMccCodes));

        log.info(
                "RegisterUser merchantInfo validated. requestId={}, accountType={}, merchantCode={}, mccCount={}",
                requestId,
                normalizedAccountType,
                merchantInfo.getMerchantCode(),
                merchantInfo.getMccCodes().size()
        );
        return merchantInfo;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void saveBillerInfoIfPresent(String accountId, RegisterUserRequest.BillerInfo billerInfo) {
        if (billerInfo == null) {
            return;
        }

        String actorAccountId = trimToNull(currentAccountIdOrAnonymous());
        if (actorAccountId == null) {
            actorAccountId = "anonymous";
        }

        AccountBillerInfo accountBillerInfo = new AccountBillerInfo();
        accountBillerInfo.setAccountId(accountId);
        accountBillerInfo.setBillerCategory(billerInfo.getBillerCategory());
        accountBillerInfo.setBillerCode(billerInfo.getBillerCode());
        accountBillerInfo.setBillerSubCategory(billerInfo.getBillerSubCategory());
        accountBillerInfo.setBillerConfig(billerInfo.getBillerConfig());
        accountBillerInfo.setBillerSettings(billerInfo.getBillerSettings());
        accountBillerInfo.setCreatedBy(actorAccountId);
        accountBillerInfo.setModifiedBy(actorAccountId);
        accountBillerInfoRepository.save(accountBillerInfo);
    }

    private void saveMerchantInfoIfPresent(String accountId, RegisterUserRequest.MerchantInfo merchantInfo) {
        if (merchantInfo == null) {
            return;
        }

        String actorAccountId = trimToNull(currentAccountIdOrAnonymous());
        if (actorAccountId == null) {
            actorAccountId = "anonymous";
        }

        AccountMerchantInfo accountMerchantInfo = new AccountMerchantInfo();
        accountMerchantInfo.setAccountId(accountId);
        accountMerchantInfo.setMerchantCode(merchantInfo.getMerchantCode());
        accountMerchantInfo.setMerchantConfig(merchantInfo.getMerchantConfig());
        accountMerchantInfo.setCreatedBy(actorAccountId);
        accountMerchantInfo.setModifiedBy(actorAccountId);
        accountMerchantInfo = accountMerchantInfoRepository.save(accountMerchantInfo);

        List<AccountMerchantMcc> merchantMccs = new ArrayList<>();
        for (String mccCode : merchantInfo.getMccCodes()) {
            AccountMerchantMcc merchantMcc = new AccountMerchantMcc();
            merchantMcc.setMerchantInfoId(accountMerchantInfo.getMerchantInfoId());
            merchantMcc.setMccCode(mccCode);
            merchantMcc.setCreatedBy(actorAccountId);
            merchantMcc.setModifiedBy(actorAccountId);
            merchantMccs.add(merchantMcc);
        }
        accountMerchantMccRepository.saveAll(merchantMccs);
    }

    private AccountIdentifier buildAccountIdentifier(String accountId, Long authId, String identifierType, String identifierValue) {
        AccountIdentifier accountIdentifier = new AccountIdentifier();
        accountIdentifier.setAccountId(accountId);
        accountIdentifier.setIdentifierType(identifierType);
        accountIdentifier.setIdentifierValue(identifierValue);
        accountIdentifier.setStatus(Constants.ACCOUNT_STATUS_ACTIVE);
        accountIdentifier.setAuthId(authId);
        return accountIdentifier;
    }

    private String maskMobile(String mobileNumber) {
        if (mobileNumber == null || mobileNumber.length() <= 4) {
            return mobileNumber;
        }
        return "****" + mobileNumber.substring(mobileNumber.length() - 4);
    }

    private String currentAccountIdOrAnonymous() {
        try {
            return JWTUtils.getCurrentAccountId();
        } catch (Exception ignored) {
            return "anonymous";
        }
    }

    private String currentAccountTypeOrUnknown() {
        try {
            return JWTUtils.getCurrentAccountType();
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    private String resolvePreferredLanguage(String preferredLanguage) {
        if (preferredLanguage == null || preferredLanguage.isBlank()) {
            return supportedLanguageRepository
                    .findFirstByIsDefaultTrueAndIsActiveTrueOrderByDisplayOrderAscIdAsc()
                    .map(SupportedLanguage::getLanguageCode)
                    .orElseThrow(() -> new ApplicationException(
                            ErrorCodes.INVALID_LANGUAGE,
                            "Default supported language is not configured"
                    ));
        }

        return supportedLanguageRepository
                .findByLanguageCodeIgnoreCaseAndIsActiveTrue(preferredLanguage.trim())
                .map(SupportedLanguage::getLanguageCode)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCodes.INVALID_LANGUAGE,
                        "Preferred language is not supported"
                ));
    }

    private UserTag buildDefaultUserTag(String accountId, Tag tag) {
        UserTag userTag = new UserTag();
        userTag.setAccountId(accountId);
        userTag.setTagId(tag.getTagId());
        userTag.setCreatedBy(accountId);
        return userTag;
    }

    @Transactional
    public void generateOtpForRegistration(RegistrationRequest request) {

        Optional<Account> account = accountRepository.findByMobileNumber(request.getUser().getMobile());
        if (account.isPresent() && account.get().getStatus().equals("ACTIVE")) {
            throw new ApplicationException(ErrorCodes.USER_EXISTS, "User already exists");
        }

        Optional<Otp> existingOtp = otpRepository.findTopByMobileNumberAndReferenceTypeAndStatusOrderByCreatedAtDesc(
                request.getUser().getMobile(),
                "REGISTRATION",
                "CREATED");
        if (existingOtp.isPresent() && existingOtp.get().getExpiresAt().isBefore(TenantTime.now())) {
            existingOtp.get().setStatus("EXPIRED");
            otpRepository.save(existingOtp.get());
        } else if (existingOtp.isPresent()) {
            throw new ApplicationException(ErrorCodes.OTP_GENERATED, "OTP Already generated for this mobile number");
        }
        Otp otp = new Otp();
        otp.setReferenceType("REGISTRATION");
        otp.setMobileNumber(request.getUser().getMobile());
        otp.setOtpValue((int) (Math.random() * 900000) + 100000);
        otp.setStatus("CREATED");
        otp.setExpiresAt(TenantTime.now().plusMinutes(10));
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
        ensureAccountCanBeMutated(account);

        account.setFirstName(request.getUser().getFirstName());
        account.setLastName(request.getUser().getLastName());
        account.setAddress(request.getUser().getAddress());
        account.setGender(request.getUser().getGender().toString());
        if (request.getUser().getPreferredLanguage() == null) {
            account.setPreferredLang("en");
        } else {
            account.setPreferredLang(request.getUser().getPreferredLanguage());
        }
        account.setDateOfBirth(request.getUser().getDob());
        account.setSsn(request.getUser().getSsn());
        account.setNationality(request.getUser().getNationality());
        account.setAttr1(request.getUser().getAttr1());
        account.setAttr2(request.getUser().getAttr2());
        account.setAttr3(request.getUser().getAttr3());
        account.setAttr4(request.getUser().getAttr4());
        account.setAttr5(request.getUser().getAttr5());
        account.setAttr6(request.getUser().getAttr6());
        account.setAttr7(request.getUser().getAttr7());
        account.setAttr8(request.getUser().getAttr8());
        account.setAttr9(request.getUser().getAttr9());
        account.setAttr10(request.getUser().getAttr10());
        account.setUpdatedBy(account.getAccountId());
        account.setUpdatedAt(TenantTime.now());
        if (request.getUser().getEmail() != null) {
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
        syncAccountNotificationEndpoints(account);

    }

    @Transactional
    public void updateAccountKycDetails(AddAccountKycRequest request) {

        log.info("Adding KYC for account");
        String accountId = JWTUtils.getCurrentAccountId();

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ApplicationException(ErrorCodes.INVALID_ACCOUNT, "Account not found"));
        ensureAccountCanBeMutated(account);

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

        if (!JWTUtils.getCurrentAccountId().equalsIgnoreCase(accountId)) {
            throw new ApplicationException(ErrorCodes.INVALID_PRIVILEGES, "Token does not have necessary access");
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ApplicationException(ErrorCodes.INVALID_ACCOUNT, "Account not found"));
        List<KycDocument> kycDocuments = kycDocumentRepository.findByAccountId(accountId);
        List<AccountIdentifier> accountIdentifiers =
                accountIdentifierRepository.findByAccountIdAndStatus(accountId, Constants.ACCOUNT_STATUS_ACTIVE);
        return new AccountKycDetailsResponse(account, kycDocuments, accountIdentifiers);
    }

    @Transactional
    public AccountStatusChangeResponse suspendAccount(String accountId, AccountStatusChangeRequest request) {
        validateAdminAccess();
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ApplicationException(ErrorCodes.INVALID_ACCOUNT, "Account not found"));
        validateSuspendResumeTarget(account);
        if (!Constants.ACCOUNT_STATUS_ACTIVE.equalsIgnoreCase(account.getStatus())) {
            throw new ApplicationException(ErrorCodes.INVALID_ACCOUNT_STATUS, "Only ACTIVE accounts can be suspended");
        }
        return changeAccountLifecycleStatus(
                account,
                Constants.ACCOUNT_STATUS_SUSPENDED,
                "SUSPEND",
                request
        );
    }

    @Transactional
    public AccountStatusChangeResponse resumeAccount(String accountId, AccountStatusChangeRequest request) {
        validateAdminAccess();
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ApplicationException(ErrorCodes.INVALID_ACCOUNT, "Account not found"));
        validateSuspendResumeTarget(account);
        if (!Constants.ACCOUNT_STATUS_SUSPENDED.equalsIgnoreCase(account.getStatus())) {
            throw new ApplicationException(ErrorCodes.INVALID_ACCOUNT_STATUS, "Only SUSPENDED accounts can be resumed");
        }
        return changeAccountLifecycleStatus(
                account,
                Constants.ACCOUNT_STATUS_ACTIVE,
                "RESUME",
                request
        );
    }

    @Transactional(readOnly = true)
    public List<AccountStatusHistory> getAccountStatusHistory(String accountId) {
        validateAdminAccess();
        return accountStatusHistoryRepository.findByAccountIdOrderByPerformedAtDesc(accountId);
    }

    private AccountStatusChangeResponse changeAccountLifecycleStatus(
            Account account,
            String newStatus,
            String actionType,
            AccountStatusChangeRequest request
    ) {
        LocalDateTime now = TenantTime.now();
        String previousStatus = account.getStatus();
        String actorAccountId = JWTUtils.getCurrentAccountId();
        String actorAccountType = JWTUtils.getCurrentAccountType();

        account.setStatus(newStatus);
        account.setUpdatedAt(now);
        account.setUpdatedBy(actorAccountId);
        accountRepository.save(account);

        updateWalletStatusesForAccount(account.getAccountId(), newStatus, now);
        revokeAuthChallengesIfSuspended(account.getAccountId(), newStatus);
        accountStatusHistoryRepository.save(buildAccountStatusHistory(
                account,
                previousStatus,
                newStatus,
                actionType,
                actorAccountId,
                actorAccountType,
                request,
                now
        ));

        return new AccountStatusChangeResponse(
                account.getAccountId(),
                account.getAccountType(),
                previousStatus,
                newStatus,
                actionType,
                actorAccountId,
                now
        );
    }

    private void updateWalletStatusesForAccount(String accountId, String newStatus, LocalDateTime now) {
        List<Wallet> wallets = walletRepository.findByAccountId(accountId);
        for (Wallet wallet : wallets) {
            if (Constants.ACCOUNT_STATUS_SUSPENDED.equalsIgnoreCase(newStatus)
                    && Constants.ACCOUNT_STATUS_ACTIVE.equalsIgnoreCase(wallet.getStatus())) {
                wallet.setStatus(Constants.ACCOUNT_STATUS_SUSPENDED);
                wallet.setIsLocked(true);
                wallet.setUpdatedAt(now);
            } else if (Constants.ACCOUNT_STATUS_ACTIVE.equalsIgnoreCase(newStatus)
                    && Constants.ACCOUNT_STATUS_SUSPENDED.equalsIgnoreCase(wallet.getStatus())) {
                wallet.setStatus(Constants.ACCOUNT_STATUS_ACTIVE);
                wallet.setIsLocked(false);
                wallet.setUpdatedAt(now);
            }
        }
        if (!wallets.isEmpty()) {
            walletRepository.saveAll(wallets);
            walletCacheService.refreshAccountWallets(accountId);
        }
    }

    private void revokeAuthChallengesIfSuspended(String accountId, String newStatus) {
        if (!Constants.ACCOUNT_STATUS_SUSPENDED.equalsIgnoreCase(newStatus)) {
            return;
        }
        List<AuthChallenge> authChallenges = authChallengeRepository.findAllByAccountId(accountId);
        for (AuthChallenge authChallenge : authChallenges) {
            authChallenge.setStatus(Constants.ACCOUNT_STATUS_SUSPENDED);
        }
        if (!authChallenges.isEmpty()) {
            authChallengeRepository.saveAll(authChallenges);
        }
    }

    private AccountStatusHistory buildAccountStatusHistory(
            Account account,
            String previousStatus,
            String newStatus,
            String actionType,
            String actorAccountId,
            String actorAccountType,
            AccountStatusChangeRequest request,
            LocalDateTime now
    ) {
        AccountStatusHistory history = new AccountStatusHistory();
        history.setAccountId(account.getAccountId());
        history.setAccountType(account.getAccountType());
        history.setActionType(actionType);
        history.setPreviousStatus(previousStatus);
        history.setNewStatus(newStatus);
        history.setPerformedBy(actorAccountId);
        history.setPerformedByType(actorAccountType);
        history.setReason(request == null ? null : request.getReason());
        history.setRemarks(request == null ? null : request.getRemarks());
        history.setPerformedAt(now);
        return history;
    }

    private void validateSuspendResumeTarget(Account account) {
        if ("ADMIN".equalsIgnoreCase(account.getAccountType())) {
            throw new ApplicationException(ErrorCodes.INVALID_ACCOUNT_TYPE, "Admin accounts cannot be suspended through this API");
        }
    }

    private void ensureAccountCanBeMutated(Account account) {
        if (Constants.ACCOUNT_STATUS_SUSPENDED.equalsIgnoreCase(account.getStatus())) {
            throw new ApplicationException(ErrorCodes.INVALID_ACCOUNT_STATUS, "Suspended accounts cannot perform this operation");
        }
        if (!Constants.ACCOUNT_STATUS_ACTIVE.equalsIgnoreCase(account.getStatus())) {
            throw new ApplicationException(ErrorCodes.INVALID_ACCOUNT_STATUS, "Account is not active");
        }
    }

    private void validateAdminAccess() {
        if (!"ADMIN".equalsIgnoreCase(JWTUtils.getCurrentAccountType())) {
            throw new ApplicationException(ErrorCodes.INVALID_PRIVILEGES, "Admin token is required");
        }
    }

    @Transactional
    public void deleteSubscriber(String accountId) {

        validateAdminAccess();

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

            String txnId = IdGenerator.generateTransactionId(
                    ACCOUNT_DELETE_TXN_PREFIX,
                    getRequiredServerInstance()
            );
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

    private String getRequiredServerInstance() {
        String serverInstance = propertyReader.getPropertyValue("server.instance");
        if (serverInstance == null || serverInstance.isBlank()) {
            throw new IllegalStateException("server.instance is not configured");
        }
        return serverInstance.trim();
    }

    private void deactivateSubscriberArtifacts(Account subscriber, List<Wallet> subscriberWallets) {
        LocalDateTime now = TenantTime.now();
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
        walletCacheService.refreshAccountWallets(subscriber.getAccountId());
    }

    private void syncAccountNotificationEndpoints(Account account) {
        if (account == null || account.getAccountId() == null) {
            return;
        }

        upsertPrimaryNotificationEndpoint(
                account.getAccountId(),
                ENDPOINT_TYPE_MOBILE,
                account.getMobileNumber()
        );
        upsertPrimaryNotificationEndpoint(
                account.getAccountId(),
                ENDPOINT_TYPE_EMAIL,
                account.getEmail()
        );
    }

    private void upsertPrimaryNotificationEndpoint(String accountId, String endpointType, String endpointValue) {
        if (endpointValue == null || endpointValue.isBlank()) {
            return;
        }

        AccountNotificationEndpoint endpoint = accountNotificationEndpointRepository
                .findByAccountIdAndEndpointTypeAndIsPrimaryTrue(accountId, endpointType)
                .orElseGet(AccountNotificationEndpoint::new);

        endpoint.setAccountId(accountId);
        endpoint.setEndpointType(endpointType);
        endpoint.setEndpointValue(endpointValue.trim());
        endpoint.setIsPrimary(Boolean.TRUE);
        endpoint.setStatus(Constants.ACCOUNT_STATUS_ACTIVE);
        endpoint.setUpdatedAt(TenantTime.now());
        accountNotificationEndpointRepository.save(endpoint);
    }

}

