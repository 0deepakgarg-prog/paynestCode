package com.paynest.users.service;

import com.paynest.Utilities.IdGenerator;
import com.paynest.common.ErrorCodes;
import com.paynest.users.dto.request.AuthLoginRequest;
import com.paynest.users.dto.response.AuthLoginResponse;
import com.paynest.users.dto.response.ChallengeTokenResponse;
import com.paynest.users.entity.Account;
import com.paynest.users.entity.AccountAuth;
import com.paynest.users.entity.AccountIdentifier;
import com.paynest.users.entity.AuthChallenge;
import com.paynest.exception.ApplicationException;
import com.paynest.users.repository.AccountAuthRepository;
import com.paynest.users.repository.AccountIdentifierRepository;
import com.paynest.users.repository.AccountRepository;
import com.paynest.users.repository.AuthChallengeRepository;
import com.paynest.config.security.JwtService;
import com.paynest.config.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AccountIdentifierRepository accountIdentifierRepository;
    private final AccountAuthRepository accountAuthRepository;
    private final AccountRepository accountRepository;
    private final AuthChallengeRepository authChallengeRepository;
    private final JwtService jwtService;
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

        if(auth.getIsFirstTimeLogin()){
            throw new ApplicationException(ErrorCodes.FORCE_AUTH_CHANGE, "First time login, please change your credential");
        }

        boolean valid = IdGenerator.verifyPin(
                request.getAuthFactor().getCredential(),
                auth.getAuthValue(),
                auth.getAuthHash()
        );

        if (!valid) {
            auth.setFailedAttempts((auth.getFailedAttempts() == null ? 0 : auth.getFailedAttempts()) + 1);
            auth.setLastFailedAt(LocalDateTime.now());
            accountAuthRepository.save(auth);
            throw new ApplicationException(ErrorCodes.INVALID_CREDENTIALS, "Invalid credentials");
        }

        auth.setFailedAttempts(0);
        auth.setLastLoginAt(LocalDateTime.now());
        accountAuthRepository.save(auth);

        String token = jwtService.generateToken(account.getAccountId(),
                auth.getAuthType(),
                TenantContext.getTenant(),
                account.getAccountType());
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
        String tenant = jwtService.extractTenant(accessToken);
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
        newChallenge.setExpiresAt(LocalDateTime.now().plusSeconds(jwtService.getChallengeExpirationSeconds()));
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
}

