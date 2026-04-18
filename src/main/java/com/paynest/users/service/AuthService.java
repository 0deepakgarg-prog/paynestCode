package com.paynest.users.service;


import com.paynest.config.tenant.TenantTime;
import com.paynest.Utilities.IdGenerator;
import com.paynest.common.Constants;
import com.paynest.common.ErrorCodes;
import com.paynest.config.PropertyReader;
import com.paynest.config.security.JwtService;
import com.paynest.config.tenant.TenantContext;
import com.paynest.exception.ApplicationException;
import com.paynest.exception.PaymentErrorCode;
import com.paynest.users.dto.request.AuthLoginRequest;
import com.paynest.users.dto.response.AuthLoginResponse;
import com.paynest.users.dto.response.ChallengeTokenResponse;
import com.paynest.users.entity.Account;
import com.paynest.users.entity.AccountAuth;
import com.paynest.users.entity.AccountIdentifier;
import com.paynest.users.entity.AuthChallenge;
import com.paynest.users.enums.AuthType;
import com.paynest.users.repository.AccountAuthRepository;
import com.paynest.users.repository.AccountIdentifierRepository;
import com.paynest.users.repository.AccountRepository;
import com.paynest.users.repository.AuthChallengeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AccountIdentifierRepository accountIdentifierRepository;
    private final AccountAuthRepository accountAuthRepository;
    private final AccountRepository accountRepository;
    private final AuthChallengeRepository authChallengeRepository;
    private final JwtService jwtService;
    private final PropertyReader propertyReader;
    private final WalletCacheService walletCacheService;

    @Transactional
    public AuthLoginResponse login(AuthLoginRequest request) {
        String identifierType = request.getUser().getIdentifierType().toUpperCase(Locale.ROOT);
        String identifierValue = request.getUser().getIdentifierValue();

        AccountIdentifier identifier = accountIdentifierRepository
                .findByIdentifierTypeAndIdentifierValueAndStatus(identifierType, identifierValue, "ACTIVE")
                .orElseThrow(() -> new ApplicationException(ErrorCodes.INVALID_CREDENTIALS, "Invalid credentials"));

        Account account = accountRepository.findById(identifier.getAccountId())
                .orElseThrow(() -> new ApplicationException(ErrorCodes.INVALID_ACCOUNT, "Account not found"));

        if (!"ACTIVE".equalsIgnoreCase(account.getStatus())) {
            throw new ApplicationException(ErrorCodes.ACCOUNT_INACTIVE, "Account is not active");
        }

        AccountAuth auth = accountAuthRepository.findById(identifier.getAuthId())
                .orElseThrow(() -> new ApplicationException(ErrorCodes.AUTH_NOT_FOUND, "Account Auth not found"));

        if (request.getAuthFactor().getAuthType() != null && !request.getAuthFactor().getAuthType().isBlank()) {
            String requestedType = request.getAuthFactor().getAuthType().toUpperCase(Locale.ROOT);
            if (!requestedType.equalsIgnoreCase(auth.getAuthType())) {
                throw new ApplicationException(ErrorCodes.INVALID_AUTH_TYPE, "Requested auth type does not match");
            }
        }

        if (auth.getIsFirstTimeLogin()) {
            throw new ApplicationException(ErrorCodes.FORCE_AUTH_CHANGE, "First time login, please change your credential");
        }

        boolean valid = IdGenerator.verifyPin(
                request.getAuthFactor().getCredential(),
                auth.getAuthValue(),
                auth.getAuthHash()
        );

        if (!valid) {
            auth.setFailedAttempts((auth.getFailedAttempts() == null ? 0 : auth.getFailedAttempts()) + 1);
            auth.setLastFailedAt(TenantTime.now());
            accountAuthRepository.save(auth);
            throw new ApplicationException(ErrorCodes.INVALID_CREDENTIALS, "Invalid credentials");
        }

        auth.setFailedAttempts(0);
        auth.setLastLoginAt(TenantTime.now());
        accountAuthRepository.save(auth);

        String token = jwtService.generateToken(
                account.getAccountId(),
                auth.getAuthType(),
                TenantContext.getTenant(),
                account.getAccountType()
        );
        walletCacheService.refreshAccountWallets(account.getAccountId());
        return new AuthLoginResponse(
                "SUCCESS",
                "Login successful",
                account.getAccountId(),
                "Bearer",
                token,
                jwtService.getExpirationSeconds()
        );
    }

    @Transactional
    public ChallengeTokenResponse generateChallengeToken(String authorizationHeader, String ipAddress) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new ApplicationException(ErrorCodes.INVALID_TOKEN, "Authorization header must contain a Bearer token");
        }

        String accessToken = authorizationHeader.substring(7);
        if (!jwtService.isTokenValid(accessToken)) {
            throw new ApplicationException(ErrorCodes.INVALID_TOKEN, "Bearer token is invalid or expired");
        }

        String accountId = jwtService.extractAccountId(accessToken);
        String authType = jwtService.extractAuthType(accessToken);

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ApplicationException(ErrorCodes.INVALID_ACCOUNT, "Account not found"));

        if (!"ACTIVE".equalsIgnoreCase(account.getStatus())) {
            throw new ApplicationException(ErrorCodes.ACCOUNT_INACTIVE, "Account is not active");
        }

        List<AuthChallenge> existingChallenges = authChallengeRepository.findAllByAccountId(accountId);
        for (AuthChallenge challenge : existingChallenges) {
            challenge.setStatus("REVOKED");
        }
        if (!existingChallenges.isEmpty()) {
            authChallengeRepository.saveAll(existingChallenges);
        }

        String challengeToken = jwtService.generateChallengeToken();

        AuthChallenge newChallenge = new AuthChallenge();
        newChallenge.setAccountId(account.getAccountId());
        newChallenge.setChallengeValue(challengeToken);
        newChallenge.setChallengeType(authType == null || authType.isBlank()
                ? "PIN"
                : authType.toUpperCase(Locale.ROOT));
        newChallenge.setExpiresAt(TenantTime.now().plusSeconds(jwtService.getChallengeExpirationSeconds()));
        newChallenge.setUsed(false);
        newChallenge.setStatus("ACTIVE");
        authChallengeRepository.save(newChallenge);

        return new ChallengeTokenResponse(
                "SUCCESS",
                "Challenge token generated successfully",
                account.getAccountId(),
                "Challenge",
                challengeToken,
                jwtService.getChallengeExpirationSeconds()
        );
    }

    @Transactional
    public void validateAuthentication(
            String authValue,
            AuthType authType,
            AccountIdentifier debitorIdentifier
    ) {
        LocalDateTime now = TenantTime.now();
        AccountAuth debitorAuth = getAuthorizationRecord(debitorIdentifier);
        if (authType != null && !authType.name().equalsIgnoreCase(debitorAuth.getAuthType())) {
            throw new ApplicationException(PaymentErrorCode.INVALID_AUTH_TYPE);
        }
        boolean passwordAuth = AuthType.PASSWORD == authType
                || "PASSWORD".equalsIgnoreCase(debitorAuth.getAuthType());
        boolean authSuccess = IdGenerator.verifyPin(
                authValue,
                debitorAuth.getAuthValue(),
                debitorAuth.getAuthHash()
        );

        if (authSuccess) {
            debitorAuth.setFailedAttempts(0);
            accountAuthRepository.save(debitorAuth);
            return;
        }

        int attempts = Optional.ofNullable(debitorAuth.getFailedAttempts())
                .orElse(0) + 1;
        int maxAttempts = Integer.parseInt(
                propertyReader.getPropertyValue("max.allowed.invalid.auth.attempts")
        );

        debitorAuth.setFailedAttempts(attempts);
        debitorAuth.setLastFailedAt(now);
        if (attempts > maxAttempts) {
            debitorAuth.setStatus(Constants.ACCOUNT_STATUS_LOCKED);
            debitorAuth.setUpdatedAt(now);
            accountAuthRepository.save(debitorAuth);

            throw new ApplicationException(PaymentErrorCode.ACCOUNT_LOCKED);
        }

        accountAuthRepository.save(debitorAuth);

        throw new ApplicationException(passwordAuth
                ? PaymentErrorCode.INVALID_PASSWORD
                : PaymentErrorCode.INVALID_PIN);
    }

    private AccountAuth getAuthorizationRecord(AccountIdentifier identifier) {
        AccountAuth accountAuth = accountAuthRepository.findById(identifier.getAuthId())
                .orElseThrow(() ->
                        new ApplicationException(PaymentErrorCode.ACCOUNT_AUTH_NOT_FOUND));

        if (Constants.ACCOUNT_STATUS_LOCKED.equalsIgnoreCase(accountAuth.getStatus())) {
            throw new ApplicationException(PaymentErrorCode.ACCOUNT_LOCKED);
        }

        if (!Constants.ACCOUNT_STATUS_ACTIVE.equalsIgnoreCase(accountAuth.getStatus())) {
            throw new ApplicationException(PaymentErrorCode.ACCOUNT_AUTH_INACTIVE);
        }

        return accountAuth;
    }
}
