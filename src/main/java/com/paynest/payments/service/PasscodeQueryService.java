package com.paynest.payments.service;

import com.paynest.config.PropertyReader;
import com.paynest.exception.ApplicationException;
import com.paynest.exception.PaymentErrorCode;
import com.paynest.payments.dto.PasscodeDetailsResponse;
import com.paynest.payments.dto.PasscodePartyDetails;
import com.paynest.payments.entity.Passcode;
import com.paynest.payments.repository.PasscodeRepository;
import com.paynest.users.entity.Account;
import com.paynest.users.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class PasscodeQueryService {

    private final PasscodeRepository passcodeRepository;
    private final AccountRepository accountRepository;
    private final PropertyReader propertyReader;

    @Transactional(readOnly = true)
    public PasscodeDetailsResponse getPasscodeDetails(String passcodeValue) {
        if (passcodeValue == null || !passcodeValue.trim().matches("\\d{10}")) {
            throw new ApplicationException(PaymentErrorCode.PASSCODE_NOT_FOUND);
        }

        Passcode passcode = passcodeRepository.findByPasscode(passcodeValue.trim())
                .orElseThrow(() -> new ApplicationException(PaymentErrorCode.PASSCODE_NOT_FOUND));

        Account senderAccount = accountRepository.findById(passcode.getSenderAccountId()).orElse(null);

        return PasscodeDetailsResponse.builder()
                .passcode(passcode.getPasscode())
                .status(passcode.getStatus())
                .transactionId(passcode.getTransactionId())
                .cashoutTransactionId(passcode.getCashoutTransactionId())
                .amount(toDisplayAmount(passcode.getAmount()))
                .currency(passcode.getCurrency())
                .sender(toSenderDetails(passcode, senderAccount))
                .receiver(toReceiverDetails(passcode))
                .createdOn(passcode.getCreatedOn())
                .modifiedOn(passcode.getModifiedOn())
                .redeemedOn(passcode.getRedeemedOn())
                .build();
    }

    private PasscodePartyDetails toSenderDetails(Passcode passcode, Account senderAccount) {
        return PasscodePartyDetails.builder()
                .accountId(passcode.getSenderAccountId())
                .accountType(senderAccount != null ? senderAccount.getAccountType() : null)
                .msisdn(passcode.getSenderMsisdn())
                .firstName(senderAccount != null ? senderAccount.getFirstName() : null)
                .lastName(senderAccount != null ? senderAccount.getLastName() : null)
                .build();
    }

    private PasscodePartyDetails toReceiverDetails(Passcode passcode) {
        return PasscodePartyDetails.builder()
                .msisdn(passcode.getUnregisteredMsisdn())
                .firstName(passcode.getFirstName())
                .lastName(passcode.getLastName())
                .kycDocumentId(passcode.getKycDocumentId())
                .build();
    }

    private BigDecimal toDisplayAmount(BigDecimal storedAmount) {
        return storedAmount.divide(
                new BigDecimal(getRequiredCurrencyFactor()),
                2,
                RoundingMode.HALF_UP
        );
    }

    private String getRequiredCurrencyFactor() {
        String currencyFactor = propertyReader.getPropertyValue("currency.factor");
        if (currencyFactor == null || currencyFactor.isBlank()) {
            throw new IllegalStateException("currency.factor is not configured");
        }
        return currencyFactor.trim();
    }
}
