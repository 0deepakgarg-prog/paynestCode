package com.paynest.payments.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paynest.Utilities.IdGenerator;
import com.paynest.common.Constants;
import com.paynest.config.PropertyReader;
import com.paynest.config.tenant.TraceContext;
import com.paynest.config.tenant.TenantTime;
import com.paynest.exception.ApplicationException;
import com.paynest.exception.PaymentErrorCode;
import com.paynest.notifications.service.TransactionNotificationEventPublisher;
import com.paynest.payments.dto.CardPreAuthAdjustmentRequest;
import com.paynest.payments.dto.CardPreAuthDebitRequest;
import com.paynest.payments.dto.CardPreAuthHoldRequest;
import com.paynest.payments.dto.CardPreAuthHoldResponse;
import com.paynest.payments.dto.Identifier;
import com.paynest.payments.dto.Party;
import com.paynest.payments.dto.TransactionInfo;
import com.paynest.payments.entity.CardPreAuthHold;
import com.paynest.payments.entity.TransactionDetails;
import com.paynest.payments.entity.TransactionDetailsId;
import com.paynest.payments.entity.Transactions;
import com.paynest.payments.entity.WalletLedger;
import com.paynest.payments.enums.CardPreAuthHoldStatus;
import com.paynest.payments.repository.CardPreAuthHoldRepository;
import com.paynest.payments.repository.TransactionDetailsRepository;
import com.paynest.payments.repository.TransactionsRepository;
import com.paynest.payments.repository.WalletLedgerRepository;
import com.paynest.payments.validation.WalletRestrictionValidator;
import com.paynest.users.entity.Account;
import com.paynest.users.entity.AccountIdentifier;
import com.paynest.users.entity.Wallet;
import com.paynest.users.entity.WalletBalance;
import com.paynest.users.enums.IdentifierType;
import com.paynest.users.repository.AccountRepository;
import com.paynest.users.repository.AccountIdentifierRepository;
import com.paynest.users.repository.WalletBalanceRepository;
import com.paynest.users.repository.WalletRepository;
import com.paynest.users.service.WalletCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CardPreAuthHoldService {

    private static final String HOLD_PREFIX = "PAH";
    private static final String DEBIT_PREFIX = "PAD";
    private static final String DEBIT_SERVICE_CODE = "CARDPAUTHDR";
    private static final String REQUEST_GATEWAY = "CMS";
    private static final String DEBITOR_ROLE = "debitor";
    private static final String CREDITOR_ROLE = "creditor";

    private final CardPreAuthHoldRepository cardPreAuthHoldRepository;
    private final AccountRepository accountRepository;
    private final AccountIdentifierRepository accountIdentifierRepository;
    private final WalletRepository walletRepository;
    private final WalletBalanceRepository walletBalanceRepository;
    private final TransactionsRepository transactionsRepository;
    private final TransactionDetailsRepository transactionDetailsRepository;
    private final WalletLedgerRepository walletLedgerRepository;
    private final WalletRestrictionValidator walletRestrictionValidator;
    private final TransactionNotificationEventPublisher transactionNotificationEventPublisher;
    private final SuccessfulPaymentEventPublisher successfulPaymentEventPublisher;
    private final WalletCacheService walletCacheService;
    private final PropertyReader propertyReader;
    private final ObjectMapper objectMapper;

    @Transactional
    public CardPreAuthHoldResponse createHold(CardPreAuthHoldRequest request) {
        normalize(request);
        BigDecimal holdAmount = toStoredAmount(request.getTransaction().getAmount());
        if (cardPreAuthHoldRepository.existsByCmsTransactionId(request.getCmsTransactionId())) {
            throw new ApplicationException(
                    PaymentErrorCode.PRE_AUTH_HOLD_ALREADY_EXISTS,
                    null,
                    Map.of("cmsTransactionId", request.getCmsTransactionId())
            );
        }

        AccountIdentifier debitorIdentifier = getMsisdnIdentifier(request.getDebitor());
        getActiveAccount(debitorIdentifier.getAccountId());
        Wallet wallet = getActiveWallet(
                debitorIdentifier.getAccountId(),
                request.getTransaction().getCurrency(),
                request.getDebitor().getWalletType().name(),
                DEBITOR_ROLE
        );
        WalletBalance balance = lockBalance(wallet.getWalletId());
        validateFreeBalance(balance, holdAmount, wallet.getWalletId());

        BigDecimal frozenAfter = balance.getFrozenBalance().add(holdAmount);
        balance.setFrozenBalance(frozenAfter);
        walletBalanceRepository.save(balance);

        CardPreAuthHold hold = new CardPreAuthHold();
        hold.setHoldId(IdGenerator.generateTransactionId(HOLD_PREFIX));
        hold.setCmsTransactionId(request.getCmsTransactionId());
        hold.setWalletId(wallet.getWalletId());
        hold.setAccountId(wallet.getAccountId());
        hold.setCurrency(wallet.getCurrency());
        hold.setWalletType(wallet.getWalletType());
        hold.setOriginalAmount(holdAmount);
        hold.setHoldAmount(holdAmount);
        hold.setStatus(CardPreAuthHoldStatus.HELD);
        hold.setCmsReference(normalizeOptional(request.getCmsReference()));
        hold.setMerchantId(normalizeOptional(request.getMerchantId()));
        hold.setComments(normalizeOptional(request.getComments()));
        hold.setAdditionalInfo(toJson(request.getAdditionalInfo()));
        cardPreAuthHoldRepository.save(hold);
        walletCacheService.refreshAccountWallets(wallet.getAccountId());

        return buildResponse(hold, frozenAfter, "PRE_AUTH_HOLD_CREATED", "Pre-auth hold created successfully");
    }

    @Transactional
    public CardPreAuthHoldResponse incrementHold(String holdId, CardPreAuthAdjustmentRequest request) {
        return adjustHold(holdId, request, true);
    }

    @Transactional
    public CardPreAuthHoldResponse decrementHold(String holdId, CardPreAuthAdjustmentRequest request) {
        return adjustHold(holdId, request, false);
    }

    @Transactional
    public CardPreAuthHoldResponse debitHold(String holdId, CardPreAuthDebitRequest request) {
        validateDebitRequest(holdId, request);
        BigDecimal debitAmount = toStoredAmount(request.getTransaction().getAmount());
        CardPreAuthHold hold = getLockedHold(holdId);
        if (hold.getStatus() != CardPreAuthHoldStatus.HELD) {
            throw new ApplicationException(
                    PaymentErrorCode.PRE_AUTH_HOLD_NOT_ACTIVE,
                    null,
                    Map.of("holdId", holdId, "status", hold.getStatus().name())
            );
        }

        AccountIdentifier debitorIdentifier = getMsisdnIdentifier(request.getDebitor());
        getActiveAccount(debitorIdentifier.getAccountId());
        Wallet debitorWallet = getActiveWallet(
                debitorIdentifier.getAccountId(),
                request.getTransaction().getCurrency(),
                request.getDebitor().getWalletType().name(),
                DEBITOR_ROLE
        );
        validateDebitHoldDebitor(hold, debitorWallet);
        AccountIdentifier creditorIdentifier = getCreditorIdentifier(request.getCreditor());
        getActiveAccount(creditorIdentifier.getAccountId());
        Wallet creditorWallet = getActiveWallet(
                creditorIdentifier.getAccountId(),
                request.getTransaction().getCurrency(),
                request.getCreditor().getWalletType().name(),
                CREDITOR_ROLE
        );
        validateDebitWallets(debitorWallet, creditorWallet);
        walletRestrictionValidator.validateTransfer(debitorWallet, creditorWallet, DEBIT_SERVICE_CODE);

        boolean lockDebitorFirst = debitorWallet.getWalletId() <= creditorWallet.getWalletId();
        WalletBalance firstLockedBalance = lockBalance(lockDebitorFirst
                ? debitorWallet.getWalletId()
                : creditorWallet.getWalletId());
        WalletBalance secondLockedBalance = lockBalance(lockDebitorFirst
                ? creditorWallet.getWalletId()
                : debitorWallet.getWalletId());
        WalletBalance debitorBalance = lockDebitorFirst ? firstLockedBalance : secondLockedBalance;
        WalletBalance creditorBalance = lockDebitorFirst ? secondLockedBalance : firstLockedBalance;

        if (debitorBalance.getFrozenBalance().compareTo(debitAmount) < 0) {
            throw new ApplicationException(
                    PaymentErrorCode.INSUFFICIENT_FROZEN_BALANCE,
                    null,
                    Map.of(
                        "walletId", hold.getWalletId(),
                            "frozenBalance", debitorBalance.getFrozenBalance().toPlainString(),
                            "debitAmount", debitAmount.toPlainString()
                    )
            );
        }
        if (hold.getHoldAmount().compareTo(debitAmount) < 0) {
            throw new ApplicationException(
                    PaymentErrorCode.PRE_AUTH_DECREMENT_EXCEEDS_HOLD,
                    null,
                    Map.of(
                            "holdId", holdId,
                            "holdAmount", hold.getHoldAmount().toPlainString(),
                            "debitAmount", debitAmount.toPlainString()
                    )
            );
        }
        BigDecimal debitAvailableBalance = debitorBalance.getAvailableBalance().subtract(debitorBalance.getFicBalance());
        if (debitAvailableBalance.compareTo(debitAmount) < 0) {
            throw new ApplicationException(
                    PaymentErrorCode.INSUFFICIENT_BALANCE,
                    null,
                    Map.of(
                            "walletId", hold.getWalletId(),
                            "availableBalance", debitorBalance.getAvailableBalance().toPlainString(),
                            "ficBalance", debitorBalance.getFicBalance().toPlainString(),
                            "debitAvailableBalance", debitAvailableBalance.toPlainString(),
                            "debitAmount", debitAmount.toPlainString()
                    )
            );
        }

        BigDecimal debitorBalanceBefore = debitorBalance.getAvailableBalance();
        BigDecimal debitorFrozenBefore = debitorBalance.getFrozenBalance();
        BigDecimal debitorFicBefore = debitorBalance.getFicBalance();
        BigDecimal debitorBalanceAfter = debitorBalanceBefore.subtract(debitAmount);
        BigDecimal debitorFrozenAfter = debitorFrozenBefore.subtract(debitAmount);

        BigDecimal creditorBalanceBefore = creditorBalance.getAvailableBalance();
        BigDecimal creditorFrozenBefore = creditorBalance.getFrozenBalance();
        BigDecimal creditorFicBefore = creditorBalance.getFicBalance();
        BigDecimal creditorBalanceAfter = creditorBalanceBefore.add(debitAmount);

        debitorBalance.setAvailableBalance(debitorBalanceAfter);
        debitorBalance.setFrozenBalance(debitorFrozenAfter);
        creditorBalance.setAvailableBalance(creditorBalanceAfter);
        walletBalanceRepository.save(debitorBalance);
        walletBalanceRepository.save(creditorBalance);

        BigDecimal holdAmountAfter = hold.getHoldAmount().subtract(debitAmount);
        hold.setHoldAmount(holdAmountAfter);
        hold.setComments(normalizeOptional(request.getComments()));
        hold.setAdditionalInfo(toJson(request.getAdditionalInfo()));
        if (holdAmountAfter.compareTo(BigDecimal.ZERO) == 0) {
            hold.setStatus(CardPreAuthHoldStatus.RELEASED);
        }
        cardPreAuthHoldRepository.save(hold);

        String transactionId = IdGenerator.generateTransactionId(DEBIT_PREFIX);
        Transactions transaction = createDebitTransaction(
                transactionId,
                hold,
                debitorWallet,
                creditorWallet,
                debitAmount,
                normalizeOptional(request.getPaymentReference()),
                normalizeOptional(request.getComments()),
                request.getAdditionalInfo()
        );
        createDebitTransactionDetail(
                transactionId,
                hold,
                debitorWallet,
                creditorWallet,
                debitAmount,
                debitorBalanceBefore,
                debitorBalanceAfter,
                debitorFrozenBefore,
                debitorFrozenAfter,
                debitorFicBefore,
                creditorBalanceBefore,
                creditorBalanceAfter,
                creditorFrozenBefore,
                creditorFicBefore
        );
        createDebitLedgerEntries(
                transactionId,
                hold,
                debitorWallet,
                creditorWallet,
                debitAmount,
                debitorBalanceBefore,
                debitorBalanceAfter,
                creditorBalanceBefore,
                creditorBalanceAfter
        );

        walletCacheService.refreshAccountWallets(hold.getAccountId());
        walletCacheService.refreshAccountWallets(creditorWallet.getAccountId());
        transactionNotificationEventPublisher.publish(transaction);
        successfulPaymentEventPublisher.publish(transaction);

        return buildResponse(
                hold,
                debitorFrozenAfter,
                "PRE_AUTH_HOLD_DEBITED",
                "Pre-auth hold debited successfully",
                transactionId
        );
    }

    private CardPreAuthHoldResponse adjustHold(
            String holdId,
            CardPreAuthAdjustmentRequest request,
            boolean increment
    ) {
        validateAdjustmentRequest(holdId, request);
        CardPreAuthHold hold = getLockedHold(holdId);
        if (hold.getStatus() != CardPreAuthHoldStatus.HELD) {
            throw new ApplicationException(
                    PaymentErrorCode.PRE_AUTH_HOLD_NOT_ACTIVE,
                    null,
                    Map.of("holdId", holdId, "status", hold.getStatus().name())
            );
        }

        WalletBalance balance = lockBalance(hold.getWalletId());
        BigDecimal amount = toStoredAmount(request.getAmount());
        BigDecimal holdAmountAfter;
        BigDecimal frozenAfter;
        String code;
        String message;

        if (increment) {
            validateFreeBalance(balance, amount, hold.getWalletId());
            holdAmountAfter = hold.getHoldAmount().add(amount);
            frozenAfter = balance.getFrozenBalance().add(amount);
            code = "PRE_AUTH_HOLD_INCREMENTED";
            message = "Pre-auth hold incremented successfully";
        } else {
            if (hold.getHoldAmount().compareTo(amount) < 0) {
                throw new ApplicationException(
                        PaymentErrorCode.PRE_AUTH_DECREMENT_EXCEEDS_HOLD,
                        null,
                        Map.of(
                                "holdId", holdId,
                                "holdAmount", hold.getHoldAmount().toPlainString(),
                                "decrementAmount", amount.toPlainString()
                        )
                );
            }
            if (balance.getFrozenBalance().compareTo(amount) < 0) {
                throw new ApplicationException(
                        PaymentErrorCode.INSUFFICIENT_FROZEN_BALANCE,
                        null,
                        Map.of(
                                "walletId", hold.getWalletId(),
                                "frozenBalance", balance.getFrozenBalance().toPlainString(),
                                "decrementAmount", amount.toPlainString()
                        )
                );
            }
            holdAmountAfter = hold.getHoldAmount().subtract(amount);
            frozenAfter = balance.getFrozenBalance().subtract(amount);
            code = "PRE_AUTH_HOLD_DECREMENTED";
            message = "Pre-auth hold decremented successfully";
            if (holdAmountAfter.compareTo(BigDecimal.ZERO) == 0) {
                hold.setStatus(CardPreAuthHoldStatus.RELEASED);
            }
        }

        balance.setFrozenBalance(frozenAfter);
        walletBalanceRepository.save(balance);

        hold.setHoldAmount(holdAmountAfter);
        hold.setComments(normalizeOptional(request.getComments()));
        hold.setAdditionalInfo(toJson(request.getAdditionalInfo()));
        cardPreAuthHoldRepository.save(hold);
        walletCacheService.refreshAccountWallets(hold.getAccountId());

        return buildResponse(hold, frozenAfter, code, message);
    }

    private void normalize(CardPreAuthHoldRequest request) {
        if (request == null) {
            throw new ApplicationException(PaymentErrorCode.INVALID_PRE_AUTH_HOLD_REQUEST);
        }
        request.setCmsTransactionId(normalizeRequired(request.getCmsTransactionId(), "cmsTransactionId"));
        validateDebitor(request.getDebitor());
        validateTransaction(request.getTransaction());
    }

    private void validateDebitor(Party debitor) {
        if (debitor == null) {
            throw new ApplicationException(
                    PaymentErrorCode.INVALID_PRE_AUTH_HOLD_REQUEST,
                    null,
                    Map.of("field", "debitor")
            );
        }
        if (debitor.getIdentifier() == null) {
            throw new ApplicationException(
                    PaymentErrorCode.INVALID_PRE_AUTH_HOLD_REQUEST,
                    null,
                    Map.of("field", "debitor.identifier")
            );
        }
        if (debitor.getIdentifier().getType() != IdentifierType.MSISDN) {
            throw new ApplicationException(
                    PaymentErrorCode.INVALID_PRE_AUTH_HOLD_REQUEST,
                    null,
                    Map.of("field", "debitor.identifier.type", "expectedType", IdentifierType.MSISDN.name())
            );
        }
        debitor.getIdentifier().setValue(normalizeRequired(debitor.getIdentifier().getValue(), "debitor.identifier.value"));
        if (debitor.getWalletType() == null) {
            throw new ApplicationException(
                    PaymentErrorCode.INVALID_PRE_AUTH_HOLD_REQUEST,
                    null,
                    Map.of("field", "debitor.walletType")
            );
        }
    }

    private void validateTransaction(TransactionInfo transaction) {
        if (transaction == null) {
            throw new ApplicationException(
                    PaymentErrorCode.INVALID_PRE_AUTH_HOLD_REQUEST,
                    null,
                    Map.of("field", "transaction")
            );
        }
        validatePositiveAmount(transaction.getAmount());
        transaction.setCurrency(normalizeRequired(transaction.getCurrency(), "transaction.currency").toUpperCase(Locale.ROOT));
    }

    private void validateAdjustmentRequest(String holdId, CardPreAuthAdjustmentRequest request) {
        normalizeRequired(holdId, "holdId");
        if (request == null) {
            throw new ApplicationException(PaymentErrorCode.INVALID_PRE_AUTH_HOLD_REQUEST);
        }
        validatePositiveAmount(request.getAmount());
    }

    private void validateDebitRequest(String holdId, CardPreAuthDebitRequest request) {
        normalizeRequired(holdId, "holdId");
        if (request == null) {
            throw new ApplicationException(PaymentErrorCode.INVALID_PRE_AUTH_HOLD_REQUEST);
        }
        validateDebitor(request.getDebitor());
        validateCreditor(request.getCreditor());
        validateTransaction(request.getTransaction());
        request.setPaymentReference(normalizeOptional(request.getPaymentReference()));
        request.setComments(normalizeOptional(request.getComments()));
    }

    private void validateDebitHoldDebitor(CardPreAuthHold hold, Wallet debitorWallet) {
        if (!hold.getWalletId().equals(debitorWallet.getWalletId())
                || !hold.getAccountId().equalsIgnoreCase(debitorWallet.getAccountId())) {
            throw new ApplicationException(
                    PaymentErrorCode.INVALID_PRE_AUTH_HOLD_REQUEST,
                    null,
                    Map.of(
                            "field", "debitor",
                            "holdId", hold.getHoldId(),
                            "holdWalletId", hold.getWalletId(),
                            "resolvedWalletId", debitorWallet.getWalletId()
                    )
            );
        }
    }

    private void validateCreditor(Party creditor) {
        if (creditor == null) {
            throw new ApplicationException(
                    PaymentErrorCode.INVALID_PRE_AUTH_HOLD_REQUEST,
                    null,
                    Map.of("field", "creditor")
            );
        }
        if (creditor.getIdentifier() == null) {
            throw new ApplicationException(
                    PaymentErrorCode.INVALID_PRE_AUTH_HOLD_REQUEST,
                    null,
                    Map.of("field", "creditor.identifier")
            );
        }
        IdentifierType identifierType = creditor.getIdentifier().getType();
        if (identifierType != IdentifierType.MSISDN && identifierType != IdentifierType.LOGINID) {
            throw new ApplicationException(
                    PaymentErrorCode.INVALID_PRE_AUTH_HOLD_REQUEST,
                    null,
                    Map.of("field", "creditor.identifier.type", "allowedTypes", "MSISDN, LOGINID")
            );
        }
        creditor.getIdentifier().setValue(normalizeRequired(creditor.getIdentifier().getValue(), "creditor.identifier.value"));
        if (creditor.getWalletType() == null) {
            throw new ApplicationException(
                    PaymentErrorCode.INVALID_PRE_AUTH_HOLD_REQUEST,
                    null,
                    Map.of("field", "creditor.walletType")
            );
        }
    }

    private void validatePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApplicationException(PaymentErrorCode.INVALID_AMOUNT);
        }
    }

    private void validateDebitWallets(Wallet debitorWallet, Wallet creditorWallet) {
        if (debitorWallet.getWalletId().equals(creditorWallet.getWalletId())) {
            throw new ApplicationException(PaymentErrorCode.SELF_TRANSFER_NOT_ALLOWED);
        }
        Account creditorAccount = getActiveAccount(creditorWallet.getAccountId());
        if (!"MERCHANT".equalsIgnoreCase(creditorAccount.getAccountType())) {
            throw new ApplicationException(
                    PaymentErrorCode.INVALID_CREDITOR_ACCOUNT_TYPE,
                    null,
                    Map.of(
                            "expectedType", "MERCHANT",
                            "actualType", creditorAccount.getAccountType()
                    )
            );
        }
        if (!debitorWallet.getCurrency().equalsIgnoreCase(creditorWallet.getCurrency())) {
            throw new ApplicationException(
                    PaymentErrorCode.INVALID_CURRENCY,
                    null,
                    Map.of(
                            "debitorCurrency", debitorWallet.getCurrency(),
                            "creditorCurrency", creditorWallet.getCurrency()
                    )
            );
        }
    }

    private BigDecimal toStoredAmount(BigDecimal amount) {
        BigDecimal currencyFactor = new BigDecimal(propertyReader.getPropertyValue("currency.factor"));
        return amount
                .multiply(currencyFactor)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private Wallet getActiveWallet(Long walletId) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new ApplicationException(
                        PaymentErrorCode.WALLET_NOT_FOUND,
                        null,
                        Map.of("walletId", walletId)
                ));

        if (!Constants.ACCOUNT_STATUS_ACTIVE.equalsIgnoreCase(wallet.getStatus())) {
            throw new ApplicationException(PaymentErrorCode.INVALID_WALLET, Map.of("walletId", walletId));
        }
        if (Boolean.TRUE.equals(wallet.getIsLocked())) {
            throw new ApplicationException(PaymentErrorCode.WALLET_LOCKED, Map.of("walletId", walletId));
        }
        getActiveAccount(wallet.getAccountId());
        return wallet;
    }

    private Wallet getActiveWallet(String accountId, String currency, String walletType, String role) {
        Wallet wallet = walletRepository
                .findByAccountIdAndCurrencyAndWalletType(accountId, currency, walletType)
                .orElseThrow(() -> new ApplicationException(
                        PaymentErrorCode.WALLET_NOT_FOUND,
                        null,
                        Map.of(
                                "accountId", accountId,
                                "currency", currency,
                                "walletType", walletType,
                                "role", role
                        )
                ));

        if (!Constants.ACCOUNT_STATUS_ACTIVE.equalsIgnoreCase(wallet.getStatus())) {
            throw new ApplicationException(PaymentErrorCode.INVALID_WALLET, Map.of("walletId", wallet.getWalletId()));
        }
        if (Boolean.TRUE.equals(wallet.getIsLocked())) {
            throw new ApplicationException(PaymentErrorCode.WALLET_LOCKED, Map.of("walletId", wallet.getWalletId()));
        }
        return wallet;
    }

    private AccountIdentifier getMsisdnIdentifier(Party party) {
        Identifier identifier = party.getIdentifier();
        return accountIdentifierRepository
                .findByIdentifierTypeAndIdentifierValueAndStatus(
                        IdentifierType.MOBILE.name(),
                        identifier.getValue().trim(),
                        Constants.ACCOUNT_STATUS_ACTIVE
                )
                .orElseThrow(() -> new ApplicationException(
                        PaymentErrorCode.ACCOUNT_IDENTIFIER_NOT_FOUND,
                        null,
                        Map.of("identifierValue", identifier.getValue())
                ));
    }

    private AccountIdentifier getCreditorIdentifier(Party party) {
        Identifier identifier = party.getIdentifier();
        return accountIdentifierRepository
                .findByIdentifierTypeAndIdentifierValueAndStatus(
                        resolveIdentifierTypeForLookup(identifier.getType()),
                        identifier.getValue(),
                        Constants.ACCOUNT_STATUS_ACTIVE
                )
                .orElseThrow(() -> new ApplicationException(
                        PaymentErrorCode.ACCOUNT_IDENTIFIER_NOT_FOUND,
                        null,
                        Map.of("identifierValue", identifier.getValue())
                ));
    }

    private String resolveIdentifierTypeForLookup(IdentifierType identifierType) {
        if (identifierType == IdentifierType.MSISDN) {
            return IdentifierType.MOBILE.name();
        }
        return identifierType.name();
    }

    private Account getActiveAccount(String accountId) {
        return accountRepository
                .findByAccountIdAndStatus(accountId, Constants.ACCOUNT_STATUS_ACTIVE)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ApplicationException(
                        PaymentErrorCode.ACCOUNT_NOT_FOUND,
                        null,
                        Map.of("accountId", accountId)
                ));
    }

    private CardPreAuthHold getLockedHold(String holdId) {
        String normalizedHoldId = normalizeRequired(holdId, "holdId");
        return cardPreAuthHoldRepository.findFirstByHoldId(normalizedHoldId)
                .orElseThrow(() -> new ApplicationException(
                        PaymentErrorCode.PRE_AUTH_HOLD_NOT_FOUND,
                        null,
                        Map.of("holdId", normalizedHoldId)
                ));
    }

    private WalletBalance lockBalance(Long walletId) {
        WalletBalance balance = walletBalanceRepository.lockBalance(walletId);
        if (balance == null) {
            throw new ApplicationException(
                    PaymentErrorCode.WALLET_BALANCE_NOT_FOUND,
                    null,
                    Map.of("walletId", walletId)
            );
        }
        if (balance.getFrozenBalance() == null) {
            balance.setFrozenBalance(BigDecimal.ZERO);
        }
        if (balance.getAvailableBalance() == null) {
            balance.setAvailableBalance(BigDecimal.ZERO);
        }
        if (balance.getFicBalance() == null) {
            balance.setFicBalance(BigDecimal.ZERO);
        }
        return balance;
    }

    private void validateFreeBalance(WalletBalance balance, BigDecimal amount, Long walletId) {
        BigDecimal frozenBalance = balance.getFrozenBalance() == null ? BigDecimal.ZERO : balance.getFrozenBalance();
        BigDecimal availableBalance = balance.getAvailableBalance() == null ? BigDecimal.ZERO : balance.getAvailableBalance();
        BigDecimal ficBalance = balance.getFicBalance() == null ? BigDecimal.ZERO : balance.getFicBalance();
        BigDecimal freeBalance = availableBalance.subtract(ficBalance).subtract(frozenBalance);

        if (freeBalance.compareTo(amount) < 0) {
            throw new ApplicationException(
                    PaymentErrorCode.INSUFFICIENT_BALANCE,
                    null,
                    Map.of(
                            "walletId", walletId,
                            "freeBalance", freeBalance.toPlainString(),
                            "ficBalance", ficBalance.toPlainString(),
                            "frozenBalance", frozenBalance.toPlainString(),
                            "amount", amount.toPlainString()
                    )
            );
        }
    }

    private String normalizeRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ApplicationException(
                    PaymentErrorCode.INVALID_PRE_AUTH_HOLD_REQUEST,
                    null,
                    Map.of("field", fieldName)
            );
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String toJson(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new ApplicationException(PaymentErrorCode.INVALID_PRE_AUTH_HOLD_REQUEST);
        }
    }

    private Transactions createDebitTransaction(
            String transactionId,
            CardPreAuthHold hold,
            Wallet debitorWallet,
            Wallet creditorWallet,
            BigDecimal amount,
            String paymentReference,
            String comments,
            Map<String, Object> additionalInfo
    ) {
        LocalDateTime now = TenantTime.now();
        Transactions transaction = new Transactions();
        transaction.setTransactionId(transactionId);
        transaction.setTransferOn(now);
        transaction.setTransactionValue(amount);
        transaction.setTransferStatus(Constants.TRANSACTION_SUCCESS);
        transaction.setRequestGateway(REQUEST_GATEWAY);
        transaction.setServiceCode(DEBIT_SERVICE_CODE);
        transaction.setTraceId(TraceContext.getTraceId());
        transaction.setPaymentReference(paymentReference);
        transaction.setComments(comments);
        transaction.setCreatedBy(hold.getAccountId());
        transaction.setCreatedOn(now);
        transaction.setModifiedBy(hold.getAccountId());
        transaction.setModifiedOn(now);
        transaction.setDebitorAccountId(hold.getAccountId());
        transaction.setCreditorAccountId(creditorWallet.getAccountId());
        transaction.setDebitorWalletType(debitorWallet.getWalletType());
        transaction.setDebitorCurrency(debitorWallet.getCurrency());
        transaction.setCreditorWalletType(creditorWallet.getWalletType());
        transaction.setCreditorCurrency(creditorWallet.getCurrency());
        transaction.setCreditorIdentifierType("ACCOUNT_ID");
        transaction.setCreditorIdentifierValue(creditorWallet.getAccountId());
        transaction.setDebitorIdentifierType("ACCOUNT_ID");
        transaction.setDebitorIdentifierValue(hold.getAccountId());
        transaction.setAttr1Name("hold_id");
        transaction.setAttr1Value(hold.getHoldId());
        transaction.setAttr2Name("cms_transaction_id");
        transaction.setAttr2Value(hold.getCmsTransactionId());
        transaction.setAttr3Name("cms_reference");
        transaction.setAttr3Value(hold.getCmsReference());
        transaction.setAttr4Name("merchant_id");
        transaction.setAttr4Value(hold.getMerchantId());
        transaction.setAdditionalInfo(toJson(additionalInfo));
        return transactionsRepository.save(transaction);
    }

    private void createDebitTransactionDetail(
            String transactionId,
            CardPreAuthHold hold,
            Wallet debitorWallet,
            Wallet creditorWallet,
            BigDecimal amount,
            BigDecimal debitorBalanceBefore,
            BigDecimal debitorBalanceAfter,
            BigDecimal debitorFrozenBefore,
            BigDecimal debitorFrozenAfter,
            BigDecimal debitorFicBalance,
            BigDecimal creditorBalanceBefore,
            BigDecimal creditorBalanceAfter,
            BigDecimal creditorFrozenBalance,
            BigDecimal creditorFicBalance
    ) {
        LocalDateTime now = TenantTime.now();
        TransactionDetails debitDetail = buildDebitTransactionDetail(
                transactionId,
                1L,
                hold.getAccountId(),
                "CARD",
                Constants.TXN_TYPE_DR,
                hold.getAccountId(),
                creditorWallet.getAccountId(),
                debitorWallet,
                amount,
                debitorBalanceBefore,
                debitorBalanceAfter,
                debitorFrozenBefore,
                debitorFrozenAfter,
                debitorFicBalance,
                debitorFicBalance,
                Constants.TXN_DETAIL_TYPE_MONEY_PAID,
                now,
                hold
        );
        TransactionDetails creditDetail = buildDebitTransactionDetail(
                transactionId,
                2L,
                creditorWallet.getAccountId(),
                "MERCHANT",
                Constants.TXN_TYPE_CR,
                creditorWallet.getAccountId(),
                hold.getAccountId(),
                creditorWallet,
                amount,
                creditorBalanceBefore,
                creditorBalanceAfter,
                creditorFrozenBalance,
                creditorFrozenBalance,
                creditorFicBalance,
                creditorFicBalance,
                Constants.TXN_DETAIL_TYPE_MONEY_RECEIVED,
                now,
                hold
        );
        transactionDetailsRepository.saveAll(java.util.List.of(debitDetail, creditDetail));
    }

    private TransactionDetails buildDebitTransactionDetail(
            String transactionId,
            Long sequenceNumber,
            String accountId,
            String userType,
            String entryType,
            String identifierId,
            String secondIdentifierId,
            Wallet wallet,
            BigDecimal amount,
            BigDecimal balanceBefore,
            BigDecimal balanceAfter,
            BigDecimal frozenBefore,
            BigDecimal frozenAfter,
            BigDecimal ficBefore,
            BigDecimal ficAfter,
            String transactionType,
            LocalDateTime transferOn,
            CardPreAuthHold hold
    ) {
        TransactionDetails detail = new TransactionDetails();
        detail.setId(new TransactionDetailsId(transactionId, sequenceNumber));
        detail.setAccountId(accountId);
        detail.setUserType(userType);
        detail.setEntryType(entryType);
        detail.setIdentifierId(identifierId);
        detail.setSecondIdentifierId(secondIdentifierId);
        detail.setTransactionValue(amount);
        detail.setApprovedValue(amount);
        detail.setPreviousBalance(balanceBefore);
        detail.setPostBalance(balanceAfter);
        detail.setPreviousFrozenBalance(frozenBefore);
        detail.setPostFrozenBalance(frozenAfter);
        detail.setPreviousFicBalance(ficBefore);
        detail.setPostFicBalance(ficAfter);
        detail.setTransferOn(transferOn);
        detail.setServiceCode(DEBIT_SERVICE_CODE);
        detail.setTransferStatus(Constants.TRANSACTION_SUCCESS);
        detail.setWalletNumber(wallet.getWalletId().toString());
        detail.setWalletType(wallet.getWalletType());
        detail.setCurrency(wallet.getCurrency());
        detail.setTransactionType(transactionType);
        detail.setAttr1Name("hold_id");
        detail.setAttr1Value(hold.getHoldId());
        detail.setAttr2Name("cms_transaction_id");
        detail.setAttr2Value(hold.getCmsTransactionId());
        detail.setAttr3Name("merchant_id");
        detail.setAttr3Value(hold.getMerchantId());
        return detail;
    }

    private void createDebitLedgerEntries(
            String transactionId,
            CardPreAuthHold hold,
            Wallet debitorWallet,
            Wallet creditorWallet,
            BigDecimal amount,
            BigDecimal debitorBalanceBefore,
            BigDecimal debitorBalanceAfter,
            BigDecimal creditorBalanceBefore,
            BigDecimal creditorBalanceAfter
    ) {
        walletLedgerRepository.save(buildDebitLedgerEntry(
                transactionId,
                hold,
                debitorWallet,
                Constants.TXN_TYPE_DR,
                amount,
                debitorBalanceBefore,
                debitorBalanceAfter
        ));
        walletLedgerRepository.save(buildDebitLedgerEntry(
                transactionId,
                hold,
                creditorWallet,
                Constants.TXN_TYPE_CR,
                amount,
                creditorBalanceBefore,
                creditorBalanceAfter
        ));
    }

    private WalletLedger buildDebitLedgerEntry(
            String transactionId,
            CardPreAuthHold hold,
            Wallet wallet,
            String entryType,
            BigDecimal amount,
            BigDecimal balanceBefore,
            BigDecimal balanceAfter
    ) {
        WalletLedger ledger = new WalletLedger();
        ledger.setTxnId(transactionId);
        ledger.setWalletId(wallet.getWalletId());
        ledger.setAccountId(wallet.getAccountId());
        ledger.setCurrency(wallet.getCurrency());
        ledger.setEntryType(entryType);
        ledger.setAmount(amount);
        ledger.setBalanceBefore(balanceBefore);
        ledger.setBalanceAfter(balanceAfter);
        ledger.setTxnType(DEBIT_SERVICE_CODE);
        ledger.setReferenceType("CARD_PRE_AUTH_DEBIT");
        ledger.setReferenceId(hold.getHoldId());
        ledger.setDescription("Card pre-auth debit");
        ledger.setAttr1(hold.getCmsTransactionId());
        ledger.setAttr2(hold.getCmsReference());
        ledger.setAttr3(hold.getMerchantId());
        return ledger;
    }

    private CardPreAuthHoldResponse buildResponse(
            CardPreAuthHold hold,
            BigDecimal frozenBalance,
            String code,
            String message
    ) {
        return buildResponse(hold, frozenBalance, code, message, null);
    }

    private CardPreAuthHoldResponse buildResponse(
            CardPreAuthHold hold,
            BigDecimal frozenBalance,
            String code,
            String message,
            String transactionId
    ) {
        return CardPreAuthHoldResponse.builder()
                .responseStatus("SUCCESS")
                .code(code)
                .message(message)
                .timestamp(TenantTime.now())
                .transactionId(transactionId)
                .holdId(hold.getHoldId())
                .cmsTransactionId(hold.getCmsTransactionId())
                .walletId(hold.getWalletId())
                .accountId(hold.getAccountId())
                .currency(hold.getCurrency())
                .walletType(hold.getWalletType())
                .originalAmount(hold.getOriginalAmount())
                .holdAmount(hold.getHoldAmount())
                .frozenBalance(frozenBalance)
                .status(hold.getStatus())
                .build();
    }
}
