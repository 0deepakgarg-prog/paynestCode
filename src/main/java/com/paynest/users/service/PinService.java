package com.paynest.users.service;

import com.paynest.Utilities.IdGenerator;
import com.paynest.common.ErrorCodes;
import com.paynest.users.dto.request.ChangePasswordRequest;
import com.paynest.users.dto.request.ChangePinRequest;
import com.paynest.users.entity.AccountAuth;
import com.paynest.users.entity.AccountIdentifier;
import com.paynest.exception.ApplicationException;
import com.paynest.users.repository.AccountAuthRepository;
import com.paynest.users.repository.AccountIdentifierRepository;
import com.paynest.config.security.JWTUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PinService {

    private final AccountAuthRepository accountAuthRepository;
    private final AccountIdentifierRepository accountIdentifierRepository;

    public void changePin(ChangePinRequest request, boolean validateJWT) {

        Optional<AccountIdentifier> accountIdentifier = accountIdentifierRepository.
                findByIdentifierTypeAndIdentifierValueAndStatus(request.getIdentifierType(),
                        request.getIdentifierValue(), "ACTIVE");

        if(accountIdentifier.isEmpty()){
          throw new ApplicationException(ErrorCodes.IDENTIFIER_NOT_FOUND,"Account identifier not found");
        }

        if(validateJWT && !JWTUtils.getCurrentAccountId().equalsIgnoreCase(accountIdentifier.get().getAccountId())){
            throw new ApplicationException(ErrorCodes.INVALID_PRIVILEGES, "Token does not have necessary access");
        }

        AccountAuth auth = accountAuthRepository
                .findById(accountIdentifier.get().getAuthId())
                .orElseThrow(() -> new ApplicationException(ErrorCodes.AUTH_NOT_FOUND,"Account Auth not found"));

        if (!IdGenerator.verifyPin(request.getOldPin(), auth.getAuthValue(), auth.getAuthHash())) {
            throw new ApplicationException(ErrorCodes.INVALID_OLD_PIN,"Invalid old PIN");
        }

        /* TODO : Need to check if this condition can be feasible.
        if (request.getOldPin().equals(request.getNewPin())) {
            throw new ApplicationException(ErrorCodes.INVALID_OLD_PIN, "New PIN cannot be same as old PIN");
        }
         */

        String newPinHash = IdGenerator.hashPin(request.getNewPin(), auth.getAuthHash());
        auth.setAuthValue(newPinHash);
        auth.setUpdatedAt(LocalDateTime.now());
        auth.setStatus("ACTIVE");
        auth.setIsFirstTimeLogin(false);
        accountAuthRepository.save(auth);
    }


    @Transactional
    public void changePassword(ChangePasswordRequest request, boolean validateJwt) {

        if(!request.getAuthFactorNew().getAuthType().equalsIgnoreCase(request.getAuthFactorOld().getAuthType())){
            throw new ApplicationException(ErrorCodes.AUTH_TYPE_NOT_SAME, "Auth Type should be same.");
        }

        Optional<AccountIdentifier> accountIdentifier = accountIdentifierRepository
                .findByIdentifierTypeAndIdentifierValueAndStatus(request.getUser().getIdentifierType(),
                        request.getUser().getIdentifierValue(),"ACTIVE");

        if(accountIdentifier.isEmpty()){
            throw new ApplicationException(ErrorCodes.IDENTIFIER_NOT_FOUND, "Account identifier not found");
        }

        if(validateJwt && !JWTUtils.getCurrentAccountId().equalsIgnoreCase(accountIdentifier.get().getAccountId())){
            throw new ApplicationException(ErrorCodes.INVALID_PRIVILEGES, "Token does not have necessary access");
        }

        AccountAuth auth = accountAuthRepository.findById(accountIdentifier.get().getAuthId())
                .orElseThrow(() -> new ApplicationException(ErrorCodes.AUTH_NOT_FOUND, "Account Auth not found"));

        if (!"PASSWORD".equalsIgnoreCase(auth.getAuthType())) {
            throw new ApplicationException(ErrorCodes.INVALID_AUTH_TYPE, "Password auth type not configured for this account");
        }

        if (!IdGenerator.verifyPin(request.getAuthFactorOld().getCredential(), auth.getAuthValue(), auth.getAuthHash())) {
            throw new ApplicationException(ErrorCodes.INVALID_OLD_PASSWORD, "Invalid old password");
        }

        String newPasswordHash = IdGenerator.hashPin(request.getAuthFactorNew().getCredential(), auth.getAuthHash());
        auth.setAuthValue(newPasswordHash);
        auth.setUpdatedAt(LocalDateTime.now());
        auth.setPasswordChangedAt(LocalDateTime.now());
        auth.setStatus("ACTIVE");
        auth.setIsFirstTimeLogin(false);
        accountAuthRepository.save(auth);
    }
}

