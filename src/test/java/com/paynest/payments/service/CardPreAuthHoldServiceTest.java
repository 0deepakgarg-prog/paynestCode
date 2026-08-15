package com.paynest.payments.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paynest.common.Constants;
import com.paynest.config.PropertyReader;
import com.paynest.exception.ApplicationException;
import com.paynest.notifications.service.TransactionNotificationEventPublisher;
import com.paynest.payments.dto.CardPreAuthDebitRequest;
import com.paynest.payments.dto.CardPreAuthHoldRequest;
import com.paynest.payments.dto.CardPreAuthHoldResponse;
import com.paynest.payments.dto.Identifier;
import com.paynest.payments.dto.Party;
import com.paynest.payments.dto.TransactionInfo;
import com.paynest.payments.entity.CardPreAuthHold;
import com.paynest.payments.entity.TransactionDetails;
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
import com.paynest.users.enums.WalletType;
import com.paynest.users.repository.AccountRepository;
import com.paynest.users.repository.AccountIdentifierRepository;
import com.paynest.users.repository.WalletBalanceRepository;
import com.paynest.users.repository.WalletRepository;
import com.paynest.users.service.WalletCacheService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardPreAuthHoldServiceTest {

    @Mock
    private CardPreAuthHoldRepository cardPreAuthHoldRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountIdentifierRepository accountIdentifierRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletBalanceRepository walletBalanceRepository;

    @Mock
    private TransactionsRepository transactionsRepository;

    @Mock
    private TransactionDetailsRepository transactionDetailsRepository;

    @Mock
    private WalletLedgerRepository walletLedgerRepository;

    @Mock
    private WalletRestrictionValidator walletRestrictionValidator;

    @Mock
    private TransactionNotificationEventPublisher transactionNotificationEventPublisher;

    @Mock
    private SuccessfulPaymentEventPublisher successfulPaymentEventPublisher;

    @Mock
    private WalletCacheService walletCacheService;

    @Mock
    private PropertyReader propertyReader;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createHold_shouldResolveWalletByMsisdnCurrencyAndWalletType() {
        CardPreAuthHoldService service = cardPreAuthHoldService();
        CardPreAuthHoldRequest request = holdRequest();
        AccountIdentifier debitorIdentifier = identifier("sub-1", "9999999999");
        Wallet wallet = wallet(101L, "sub-1", "USD", "MAIN");
        WalletBalance balance = balance(101L, "100.00", "0.00", "0.00");

        when(propertyReader.getPropertyValue("currency.factor")).thenReturn("1");
        when(cardPreAuthHoldRepository.existsByCmsTransactionId("cms-txn-1")).thenReturn(false);
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus(
                "MOBILE",
                "9999999999",
                Constants.ACCOUNT_STATUS_ACTIVE
        )).thenReturn(Optional.of(debitorIdentifier));
        when(accountRepository.findByAccountIdAndStatus("sub-1", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(List.of(account("sub-1", "SUBSCRIBER")));
        when(walletRepository.findByAccountIdAndCurrencyAndWalletType("sub-1", "USD", "MAIN"))
                .thenReturn(Optional.of(wallet));
        when(walletBalanceRepository.lockBalance(101L)).thenReturn(balance);
        when(cardPreAuthHoldRepository.save(any(CardPreAuthHold.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CardPreAuthHoldResponse response = service.createHold(request);

        assertEquals("SUCCESS", response.getResponseStatus());
        assertEquals("PRE_AUTH_HOLD_CREATED", response.getCode());
        assertEquals(101L, response.getWalletId());
        assertEquals("sub-1", response.getAccountId());
        assertEquals("USD", response.getCurrency());
        assertEquals("MAIN", response.getWalletType());
        assertEquals(new BigDecimal("25.00"), response.getOriginalAmount());
        assertEquals(new BigDecimal("25.00"), response.getHoldAmount());
        assertEquals(new BigDecimal("25.00"), response.getFrozenBalance());
        assertEquals(new BigDecimal("25.00"), balance.getFrozenBalance());

        ArgumentCaptor<CardPreAuthHold> holdCaptor = ArgumentCaptor.forClass(CardPreAuthHold.class);
        verify(cardPreAuthHoldRepository).save(holdCaptor.capture());
        CardPreAuthHold savedHold = holdCaptor.getValue();
        assertEquals("cms-txn-1", savedHold.getCmsTransactionId());
        assertEquals(101L, savedHold.getWalletId());
        assertEquals("sub-1", savedHold.getAccountId());
        assertEquals(CardPreAuthHoldStatus.HELD, savedHold.getStatus());
        verify(walletCacheService).refreshAccountWallets("sub-1");
    }

    @Test
    void debitHold_shouldReleaseFrozenFundsCreditMerchantAndWriteDebitCreditLedgers() {
        CardPreAuthHoldService service = cardPreAuthHoldService();
        CardPreAuthHold hold = hold(new BigDecimal("25.00"));
        Wallet debitorWallet = wallet(101L, "sub-1", "USD", "MAIN");
        Wallet creditorWallet = wallet(202L, "merchant-1", "USD", "MAIN");
        WalletBalance debitorBalance = balance(101L, "100.00", "25.00", "0.00");
        WalletBalance creditorBalance = balance(202L, "10.00", "0.00", "0.00");
        CardPreAuthDebitRequest request = debitRequest(new BigDecimal("25.00"));
        request.setPaymentReference("cms-pay-1");
        request.setComments("capture full auth");
        request.setAdditionalInfo(Map.of("terminalId", "POS-1"));

        when(propertyReader.getPropertyValue("currency.factor")).thenReturn("1");
        when(cardPreAuthHoldRepository.findFirstByHoldId("PAH-1")).thenReturn(Optional.of(hold));
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus(
                "MOBILE",
                "9999999999",
                Constants.ACCOUNT_STATUS_ACTIVE
        )).thenReturn(Optional.of(identifier("sub-1", "9999999999")));
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus(
                "LOGINID",
                "merchant-login",
                Constants.ACCOUNT_STATUS_ACTIVE
        )).thenReturn(Optional.of(identifier("merchant-1", "merchant-login", IdentifierType.LOGINID)));
        when(accountRepository.findByAccountIdAndStatus("sub-1", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(List.of(account("sub-1", "SUBSCRIBER")));
        when(accountRepository.findByAccountIdAndStatus("merchant-1", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(List.of(account("merchant-1", "MERCHANT")));
        when(walletRepository.findByAccountIdAndCurrencyAndWalletType("sub-1", "USD", "MAIN"))
                .thenReturn(Optional.of(debitorWallet));
        when(walletRepository.findByAccountIdAndCurrencyAndWalletType("merchant-1", "USD", "MAIN"))
                .thenReturn(Optional.of(creditorWallet));
        doNothing().when(walletRestrictionValidator).validateTransfer(debitorWallet, creditorWallet, "CARDPAUTHDR");
        when(walletBalanceRepository.lockBalance(101L)).thenReturn(debitorBalance);
        when(walletBalanceRepository.lockBalance(202L)).thenReturn(creditorBalance);
        when(transactionsRepository.save(any(Transactions.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CardPreAuthHoldResponse response = service.debitHold("PAH-1", request);

        assertEquals("SUCCESS", response.getResponseStatus());
        assertEquals("PRE_AUTH_HOLD_DEBITED", response.getCode());
        assertNotNull(response.getTransactionId());
        assertEquals(CardPreAuthHoldStatus.RELEASED, response.getStatus());
        assertEquals(new BigDecimal("0.00"), response.getHoldAmount());
        assertEquals(new BigDecimal("0.00"), response.getFrozenBalance());
        assertEquals(new BigDecimal("75.00"), debitorBalance.getAvailableBalance());
        assertEquals(new BigDecimal("0.00"), debitorBalance.getFrozenBalance());
        assertEquals(new BigDecimal("35.00"), creditorBalance.getAvailableBalance());

        ArgumentCaptor<WalletLedger> ledgerCaptor = ArgumentCaptor.forClass(WalletLedger.class);
        verify(walletLedgerRepository, times(2)).save(ledgerCaptor.capture());
        List<WalletLedger> ledgers = ledgerCaptor.getAllValues();
        assertEquals(Constants.TXN_TYPE_DR, ledgers.get(0).getEntryType());
        assertEquals(101L, ledgers.get(0).getWalletId());
        assertEquals(Constants.TXN_TYPE_CR, ledgers.get(1).getEntryType());
        assertEquals(202L, ledgers.get(1).getWalletId());

        ArgumentCaptor<Transactions> transactionCaptor = ArgumentCaptor.forClass(Transactions.class);
        verify(transactionsRepository).save(transactionCaptor.capture());
        Transactions transaction = transactionCaptor.getValue();
        assertEquals("CARDPAUTHDR", transaction.getServiceCode());
        assertEquals("cms-pay-1", transaction.getPaymentReference());
        assertEquals("PAH-1", transaction.getAttr1Value());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TransactionDetails>> detailsCaptor = ArgumentCaptor.forClass(List.class);
        verify(transactionDetailsRepository).saveAll(detailsCaptor.capture());
        List<TransactionDetails> details = detailsCaptor.getValue();
        assertEquals(Constants.TXN_TYPE_DR, details.get(0).getEntryType());
        assertEquals(Constants.TXN_TYPE_CR, details.get(1).getEntryType());

        verify(cardPreAuthHoldRepository).save(hold);
        verify(walletCacheService).refreshAccountWallets("sub-1");
        verify(walletCacheService).refreshAccountWallets("merchant-1");
        verify(transactionNotificationEventPublisher).publish(transaction);
        verify(successfulPaymentEventPublisher).publish(transaction);
    }

    @Test
    void debitHold_shouldRejectDebitAboveRemainingHold() {
        CardPreAuthHoldService service = cardPreAuthHoldService();
        CardPreAuthHold hold = hold(new BigDecimal("10.00"));
        Wallet debitorWallet = wallet(101L, "sub-1", "USD", "MAIN");
        Wallet creditorWallet = wallet(202L, "merchant-1", "USD", "MAIN");
        CardPreAuthDebitRequest request = debitRequest(new BigDecimal("25.00"));

        when(propertyReader.getPropertyValue("currency.factor")).thenReturn("1");
        when(cardPreAuthHoldRepository.findFirstByHoldId("PAH-1")).thenReturn(Optional.of(hold));
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus(
                "MOBILE",
                "9999999999",
                Constants.ACCOUNT_STATUS_ACTIVE
        )).thenReturn(Optional.of(identifier("sub-1", "9999999999")));
        when(accountIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndStatus(
                "LOGINID",
                "merchant-login",
                Constants.ACCOUNT_STATUS_ACTIVE
        )).thenReturn(Optional.of(identifier("merchant-1", "merchant-login", IdentifierType.LOGINID)));
        when(accountRepository.findByAccountIdAndStatus("sub-1", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(List.of(account("sub-1", "SUBSCRIBER")));
        when(accountRepository.findByAccountIdAndStatus("merchant-1", Constants.ACCOUNT_STATUS_ACTIVE))
                .thenReturn(List.of(account("merchant-1", "MERCHANT")));
        when(walletRepository.findByAccountIdAndCurrencyAndWalletType("sub-1", "USD", "MAIN"))
                .thenReturn(Optional.of(debitorWallet));
        when(walletRepository.findByAccountIdAndCurrencyAndWalletType("merchant-1", "USD", "MAIN"))
                .thenReturn(Optional.of(creditorWallet));
        when(walletBalanceRepository.lockBalance(101L)).thenReturn(balance(101L, "100.00", "25.00", "0.00"));
        when(walletBalanceRepository.lockBalance(202L)).thenReturn(balance(202L, "10.00", "0.00", "0.00"));

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> service.debitHold("PAH-1", request)
        );

        assertEquals("PRE_AUTH_DECREMENT_EXCEEDS_HOLD", exception.getErrorCode());
    }

    private CardPreAuthHoldService cardPreAuthHoldService() {
        return new CardPreAuthHoldService(
                cardPreAuthHoldRepository,
                accountRepository,
                accountIdentifierRepository,
                walletRepository,
                walletBalanceRepository,
                transactionsRepository,
                transactionDetailsRepository,
                walletLedgerRepository,
                walletRestrictionValidator,
                transactionNotificationEventPublisher,
                successfulPaymentEventPublisher,
                walletCacheService,
                propertyReader,
                objectMapper
        );
    }

    private CardPreAuthHoldRequest holdRequest() {
        CardPreAuthHoldRequest request = new CardPreAuthHoldRequest();
        request.setCmsTransactionId("cms-txn-1");
        request.setCmsReference("cms-ref-1");
        request.setMerchantId("merchant-code-1");
        request.setComments("initial auth");
        request.setDebitor(msisdnParty("9999999999"));

        TransactionInfo transaction = new TransactionInfo();
        transaction.setAmount(new BigDecimal("25.00"));
        transaction.setCurrency("USD");
        request.setTransaction(transaction);
        return request;
    }

    private CardPreAuthDebitRequest debitRequest(BigDecimal amount) {
        CardPreAuthDebitRequest request = new CardPreAuthDebitRequest();
        request.setDebitor(msisdnParty("9999999999"));
        request.setCreditor(merchantParty("merchant-login"));

        TransactionInfo transaction = new TransactionInfo();
        transaction.setAmount(amount);
        transaction.setCurrency("USD");
        request.setTransaction(transaction);
        return request;
    }

    private Party merchantParty(String loginId) {
        Party party = new Party();
        party.setWalletType(WalletType.MAIN);

        Identifier identifier = new Identifier();
        identifier.setType(IdentifierType.LOGINID);
        identifier.setValue(loginId);
        party.setIdentifier(identifier);
        return party;
    }

    private Party msisdnParty(String msisdn) {
        Party party = new Party();
        party.setWalletType(WalletType.MAIN);

        Identifier identifier = new Identifier();
        identifier.setType(IdentifierType.MSISDN);
        identifier.setValue(msisdn);
        party.setIdentifier(identifier);
        return party;
    }

    private AccountIdentifier identifier(String accountId, String value) {
        return identifier(accountId, value, IdentifierType.MOBILE);
    }

    private AccountIdentifier identifier(String accountId, String value, IdentifierType identifierType) {
        AccountIdentifier identifier = new AccountIdentifier();
        identifier.setAccountId(accountId);
        identifier.setIdentifierType(identifierType.name());
        identifier.setIdentifierValue(value);
        identifier.setStatus(Constants.ACCOUNT_STATUS_ACTIVE);
        return identifier;
    }

    private CardPreAuthHold hold(BigDecimal amount) {
        CardPreAuthHold hold = new CardPreAuthHold();
        hold.setHoldId("PAH-1");
        hold.setCmsTransactionId("cms-txn-1");
        hold.setWalletId(101L);
        hold.setAccountId("sub-1");
        hold.setCurrency("USD");
        hold.setWalletType("MAIN");
        hold.setOriginalAmount(amount);
        hold.setHoldAmount(amount);
        hold.setStatus(CardPreAuthHoldStatus.HELD);
        hold.setCmsReference("cms-ref-1");
        hold.setMerchantId("merchant-code-1");
        return hold;
    }

    private Wallet wallet(Long walletId, String accountId, String currency, String walletType) {
        Wallet wallet = new Wallet();
        wallet.setWalletId(walletId);
        wallet.setAccountId(accountId);
        wallet.setCurrency(currency);
        wallet.setWalletType(walletType);
        wallet.setStatus(Constants.ACCOUNT_STATUS_ACTIVE);
        wallet.setIsLocked(false);
        return wallet;
    }

    private WalletBalance balance(Long walletId, String available, String frozen, String fic) {
        WalletBalance balance = new WalletBalance();
        balance.setWalletId(walletId);
        balance.setAvailableBalance(new BigDecimal(available));
        balance.setFrozenBalance(new BigDecimal(frozen));
        balance.setFicBalance(new BigDecimal(fic));
        return balance;
    }

    private Account account(String accountId, String accountType) {
        Account account = new Account();
        account.setAccountId(accountId);
        account.setAccountType(accountType);
        account.setStatus(Constants.ACCOUNT_STATUS_ACTIVE);
        return account;
    }
}
