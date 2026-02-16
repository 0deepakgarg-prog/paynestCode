package com.paynest.service;

import com.paynest.Utilities.IdGenerator;
import com.paynest.dto.ChangePinRequest;
import com.paynest.entity.Account;
import com.paynest.entity.AccountAuth;
import com.paynest.entity.AccountIdentifier;
import com.paynest.exception.ApplicationException;
import com.paynest.repository.AccountAuthRepository;
import com.paynest.repository.AccountIdentifierRepository;
import com.paynest.repository.AccountRepository;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;

import javax.swing.text.Utilities;
import java.time.LocalDateTime;

@Service
public class PinService {

    private AccountAuthRepository accountAuthRepository;
    private AccountRepository accountRepository;
    private AccountIdentifierRepository accountIdentifierRepository;

    public void changePin(String accountId, ChangePinRequest request) {

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ApplicationException("INVALID_ACCOUNT","Account not found"));

        AccountIdentifier accountIdentifier = accountIdentifierRepository
                .findByAccountIdAndStatus(accountId,"ACTIVE")
                .stream()
                .filter(id -> id.getIdentifierType().equals(request.getIdentifierType())
                        && id.getIdentifierValue().equals(request.getIdentifierValue()))
                .findFirst()
                .orElseThrow(() -> new ApplicationException("IDENTIFIER_NOT_FOUND","Account identifier not found"));



        AccountAuth auth = accountAuthRepository
                .findById(accountIdentifier.getAuthId())
                .orElseThrow(() -> new ApplicationException("AUTH_NOT_FOUND","Account Auth not found"));

        if (!IdGenerator.verifyPin(request.getOldPin(), auth.getAuthValue(), auth.getAuthHash())) {
            throw new ApplicationException("INVALID_OLD_PIN","Invalid old PIN");
        }

        /* TODO : Need to check if this condition can be feasible.
        if (request.getOldPin().equals(request.getNewPin())) {
            throw new ApplicationException("New PIN cannot be same as old PIN");
        }
         */

        String newPinHash = IdGenerator.hashPin(request.getNewPin(), auth.getAuthHash());
        auth.setAuthValue(newPinHash);
        auth.setUpdatedAt(LocalDateTime.now());
        auth.setStatus("ACTIVE");
        auth.setIsFirstTimeLogin(false);
        accountAuthRepository.save(auth);
    }
}
