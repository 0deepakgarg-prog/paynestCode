package com.paynest.payments.service;

import com.paynest.config.tenant.TenantTime;
import com.paynest.exception.ApplicationException;
import com.paynest.payments.dto.RecentRecipientResponse;
import com.paynest.payments.entity.RecentRecipient;
import com.paynest.payments.entity.Transactions;
import com.paynest.payments.repository.RecentRecipientRepository;
import com.paynest.users.entity.Account;
import com.paynest.users.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RecentRecipientService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    private final RecentRecipientRepository recentRecipientRepository;
    private final AccountRepository accountRepository;

    @Transactional
    public void recordSuccessfulPayment(Transactions transaction) {
        if (transaction == null
                || isBlank(transaction.getDebitorAccountId())
                || isBlank(transaction.getCreditorAccountId())
                || isBlank(transaction.getServiceCode())) {
            return;
        }
        if (transaction.getDebitorAccountId().equalsIgnoreCase(transaction.getCreditorAccountId())) {
            return;
        }

        Account recipient = accountRepository.findById(transaction.getCreditorAccountId()).orElse(null);
        LocalDateTime now = TenantTime.now();
        LocalDateTime paidAt = transaction.getTransferOn() == null ? now : transaction.getTransferOn();
        String serviceCode = normalize(transaction.getServiceCode());
        String currency = normalize(defaultIfBlank(transaction.getCreditorCurrency(), transaction.getDebitorCurrency()));
        String walletType = normalize(defaultIfBlank(transaction.getCreditorWalletType(), "MAIN"));
        if (isBlank(serviceCode) || isBlank(currency) || isBlank(walletType)) {
            return;
        }

        recentRecipientRepository.upsertRecentRecipient(
                transaction.getDebitorAccountId(),
                transaction.getCreditorAccountId(),
                serviceCode,
                currency,
                walletType,
                recipient == null ? null : recipient.getAccountType(),
                transaction.getCreditorIdentifierType(),
                transaction.getCreditorIdentifierValue(),
                displayName(recipient, transaction.getCreditorIdentifierValue(), transaction.getCreditorAccountId()),
                transaction.getTransactionId(),
                paidAt,
                transaction.getField1(),
                transaction.getField2(),
                transaction.getField3(),
                transaction.getField4(),
                transaction.getField5(),
                now
        );
    }

    @Transactional(readOnly = true)
    public List<RecentRecipientResponse> getRecentRecipients(String accountId, String serviceCode, Integer limit) {
        String resolvedAccountId = resolveAccountId(accountId);
        Pageable pageable = PageRequest.of(0, normalizeLimit(limit));
        List<RecentRecipient> recipients = isBlank(serviceCode)
                ? recentRecipientRepository.findByAccountIdOrderByLastPaidAtDesc(resolvedAccountId, pageable)
                : recentRecipientRepository.findByAccountIdAndServiceCodeOrderByLastPaidAtDesc(
                resolvedAccountId,
                normalize(serviceCode),
                pageable
        );
        return recipients.stream().map(this::toResponse).toList();
    }

    private String resolveAccountId(String accountId) {
        if (!isBlank(accountId)) {
            return accountId.trim();
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || isBlank(authentication.getName())) {
            throw new ApplicationException("INVALID_REQUEST", "accountId is required");
        }
        return authentication.getName();
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private RecentRecipientResponse toResponse(RecentRecipient recipient) {
        return RecentRecipientResponse.builder()
                .accountId(recipient.getAccountId())
                .recipientAccountId(recipient.getRecipientAccountId())
                .recipientAccountType(recipient.getRecipientAccountType())
                .recipientIdentifierType(recipient.getRecipientIdentifierType())
                .recipientIdentifierValue(recipient.getRecipientIdentifierValue())
                .recipientDisplayName(recipient.getRecipientDisplayName())
                .serviceCode(recipient.getServiceCode())
                .currency(recipient.getCurrency())
                .walletType(recipient.getWalletType())
                .lastTransactionId(recipient.getLastTransactionId())
                .lastPaidAt(recipient.getLastPaidAt())
                .paymentCount(recipient.getPaymentCount())
                .field1(recipient.getField1())
                .field2(recipient.getField2())
                .field3(recipient.getField3())
                .field4(recipient.getField4())
                .field5(recipient.getField5())
                .build();
    }

    private String displayName(Account account, String identifierValue, String fallbackAccountId) {
        if (account != null) {
            String fullName = String.join(" ",
                    Optional.ofNullable(account.getFirstName()).orElse("").trim(),
                    Optional.ofNullable(account.getLastName()).orElse("").trim()
            ).trim();
            if (!fullName.isBlank()) {
                return fullName;
            }
            if (!isBlank(account.getAccountCode())) {
                return account.getAccountCode();
            }
            if (!isBlank(account.getMobileNumber())) {
                return account.getMobileNumber();
            }
        }
        return defaultIfBlank(identifierValue, fallbackAccountId);
    }

    private String defaultIfBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private String normalize(String value) {
        return isBlank(value) ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
