package com.paynest.service;

import com.paynest.Utilities.IdGenerator;
import com.paynest.dto.RegistrationRequest;
import com.paynest.dto.RegistrationRequestWithOtp;
import com.paynest.entity.*;
import com.paynest.exception.ApplicationException;
import com.paynest.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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


}
