package com.paynest.payments.bulkpayment.service;

import com.paynest.Utilities.IdGenerator;
import com.paynest.common.Constants;
import com.paynest.config.PropertyReader;
import com.paynest.enums.RequestGateway;
import com.paynest.exception.ApplicationException;
import com.paynest.exception.PaymentErrorCode;
import com.paynest.payments.bulkpayment.dto.BulkPaymentInternalTransferRequest;
import com.paynest.payments.bulkpayment.dto.SalaryPaymentInternalRequest;
import com.paynest.payments.dto.BasePaymentResponse;
import com.paynest.payments.enums.InitiatedBy;
import com.paynest.payments.enums.TransactionStatus;
import com.paynest.payments.service.BalanceService;
import com.paynest.payments.service.TransactionsService;
import com.paynest.users.entity.Account;
import com.paynest.users.entity.AccountIdentifier;
import com.paynest.users.entity.Wallet;
import com.paynest.users.repository.AccountIdentifierRepository;
import com.paynest.users.repository.AccountRepository;
import com.paynest.users.repository.WalletRepository;
import com.paynest.config.tenant.TenantTime;
import com.paynest.config.tenant.TraceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class BulkPaymentInternalPaymentService {

    private static final String SYSTEM_ACCOUNT_ID = "SYS0001";
    private static final String TRANSIENT_WALLET_TYPE = "SALARY";
    private static final String SYSTEM_ACCOUNT_TYPE = "SYSTEM";
    private static final String SERVICE_BULK_PREFUND = "BULKP";
    private static final String SERVICE_SALARY_PAYMENT = "SALPAY";
    private static final String SERVICE_BULK_REFUND = "BULKR";
    private static final String PREFIX_BULK_PREFUND = "BP";
    private static final String PREFIX_SALARY_PAYMENT = "SP";
    private static final String PREFIX_BULK_REFUND = "BR";

    private final AccountRepository accountRepository;
    private final AccountIdentifierRepository accountIdentifierRepository;
    private final WalletRepository walletRepository;
    private final PropertyReader propertyReader;
    private final TransactionsService transactionsService;
    private final BalanceService balanceService;

    public BasePaymentResponse prefund(BulkPaymentInternalTransferRequest request) {
        Account enterpriseAccount = getActiveAccount(request.getAccountId());
        AccountIdentifier enterpriseIdentifier = getPrimaryIdentifier(enterpriseAccount.getAccountId());
        Wallet enterpriseWallet = getWallet(
                enterpriseAccount.getAccountId(),
                request.getCurrency(),
                request.getWalletType()
        );
        Wallet salaryWallet = getSalaryTransientWallet(request.getCurrency());
        AccountIdentifier salaryIdentifier = systemIdentifier(request.getCurrency());

        return transfer(
                request.getAmount(),
                request.getCurrency(),
                requestGateway(request.getRequestGateway()),
                SERVICE_BULK_PREFUND,
                PREFIX_BULK_PREFUND,
                request.getPreferredLang(),
                enterpriseIdentifier,
                salaryIdentifier,
                enterpriseAccount.getAccountType(),
                SYSTEM_ACCOUNT_TYPE,
                enterpriseWallet,
                salaryWallet,
                InitiatedBy.DEBITOR,
                request.getPaymentReference(),
                request.getComments()
        );
    }

    public BasePaymentResponse paySalary(SalaryPaymentInternalRequest request) {
        AccountIdentifier creditorIdentifier = getIdentifier(
                request.getCreditorIdentifierType(),
                request.getCreditorIdentifierValue()
        );
        Account creditorAccount = getActiveAccount(creditorIdentifier.getAccountId());
        Wallet salaryWallet = getSalaryTransientWallet(request.getCurrency());
        AccountIdentifier salaryIdentifier = systemIdentifier(request.getCurrency());
        Wallet creditorWallet = getWallet(
                creditorAccount.getAccountId(),
                request.getCurrency(),
                request.getCreditorWalletType()
        );

        return transfer(
                request.getAmount(),
                request.getCurrency(),
                requestGateway(request.getRequestGateway()),
                SERVICE_SALARY_PAYMENT,
                PREFIX_SALARY_PAYMENT,
                request.getPreferredLang(),
                salaryIdentifier,
                creditorIdentifier,
                SYSTEM_ACCOUNT_TYPE,
                creditorAccount.getAccountType(),
                salaryWallet,
                creditorWallet,
                InitiatedBy.DEBITOR,
                request.getPaymentReference(),
                request.getComments()
        );
    }

    public BasePaymentResponse refund(BulkPaymentInternalTransferRequest request) {
        Account enterpriseAccount = getActiveAccount(request.getAccountId());
        AccountIdentifier enterpriseIdentifier = getPrimaryIdentifier(enterpriseAccount.getAccountId());
        Wallet salaryWallet = getSalaryTransientWallet(request.getCurrency());
        AccountIdentifier salaryIdentifier = systemIdentifier(request.getCurrency());
        Wallet enterpriseWallet = getWallet(
                enterpriseAccount.getAccountId(),
                request.getCurrency(),
                request.getWalletType()
        );

        return transfer(
                request.getAmount(),
                request.getCurrency(),
                requestGateway(request.getRequestGateway()),
                SERVICE_BULK_REFUND,
                PREFIX_BULK_REFUND,
                request.getPreferredLang(),
                salaryIdentifier,
                enterpriseIdentifier,
                SYSTEM_ACCOUNT_TYPE,
                enterpriseAccount.getAccountType(),
                salaryWallet,
                enterpriseWallet,
                InitiatedBy.DEBITOR,
                request.getPaymentReference(),
                request.getComments()
        );
    }

    private BasePaymentResponse transfer(
            BigDecimal amount,
            String currency,
            String requestGateway,
            String serviceCode,
            String transactionPrefix,
            String language,
            AccountIdentifier debitorIdentifier,
            AccountIdentifier creditorIdentifier,
            String debitorAccountType,
            String creditorAccountType,
            Wallet debitorWallet,
            Wallet creditorWallet,
            InitiatedBy initiatedBy,
            String paymentReference,
            String comments
    ) {
        String transactionId = IdGenerator.generateTransactionId(
                transactionPrefix,
                getRequiredServerInstance()
        );

        try {
            transactionsService.generateTransactionRecord(
                    transactionId,
                    amount,
                    requestGateway,
                    serviceCode,
                    language,
                    debitorIdentifier,
                    creditorIdentifier,
                    debitorAccountType,
                    creditorAccountType,
                    debitorWallet,
                    creditorWallet,
                    initiatedBy,
                    paymentReference,
                    comments
            );

            balanceService.transferWalletAmount(
                    debitorWallet,
                    creditorWallet,
                    amount,
                    serviceCode,
                    initiatedBy,
                    transactionId
            );
        } catch (ApplicationException ex) {
            throw ex.withTransactionId(transactionId);
        }

        return BasePaymentResponse.builder()
                .responseStatus(TransactionStatus.SUCCESS)
                .operationType(serviceCode)
                .code("PAYMENT_SUCCESS")
                .message(serviceCode + " payment successful")
                .timestamp(TenantTime.now())
                .traceId(TraceContext.getTraceId())
                .transactionId(transactionId)
                .amount(amount)
                .currency(currency)
                .build();
    }

    private Account getActiveAccount(String accountId) {
        List<Account> accounts = accountRepository.findByAccountIdAndStatus(
                accountId,
                Constants.ACCOUNT_STATUS_ACTIVE
        );
        if (accounts == null || accounts.isEmpty()) {
            throw new ApplicationException(
                    PaymentErrorCode.ACCOUNT_NOT_FOUND,
                    null,
                    Map.of("accountId", accountId)
            );
        }
        return accounts.get(0);
    }

    private AccountIdentifier getPrimaryIdentifier(String accountId) {
        List<AccountIdentifier> identifiers = accountIdentifierRepository.findByAccountIdAndStatus(
                accountId,
                Constants.ACCOUNT_STATUS_ACTIVE
        );
        if (identifiers == null || identifiers.isEmpty()) {
            return accountIdentifier(accountId, "ACCOUNT_ID", accountId);
        }
        return identifiers.get(0);
    }

    private AccountIdentifier getIdentifier(String identifierType, String identifierValue) {
        return accountIdentifierRepository
                .findByIdentifierTypeAndIdentifierValueAndStatus(
                        normalize(identifierType),
                        identifierValue,
                        Constants.ACCOUNT_STATUS_ACTIVE
                )
                .orElseThrow(() -> new ApplicationException(
                        PaymentErrorCode.ACCOUNT_IDENTIFIER_NOT_FOUND,
                        null,
                        Map.of(
                                "identifierType", identifierType,
                                "identifierValue", identifierValue
                        )
                ));
    }

    private Wallet getWallet(String accountId, String currency, String walletType) {
        return walletRepository
                .findByAccountIdAndCurrencyAndWalletType(
                        accountId,
                        normalize(currency),
                        normalize(walletType)
                )
                .orElseThrow(() -> new ApplicationException(
                        PaymentErrorCode.WALLET_NOT_FOUND,
                        null,
                        Map.of(
                                "accountId", accountId,
                                "currency", currency,
                                "walletType", walletType
                        )
                ));
    }

    private Wallet getSalaryTransientWallet(String currency) {
        return getWallet(SYSTEM_ACCOUNT_ID, currency, TRANSIENT_WALLET_TYPE);
    }

    private AccountIdentifier systemIdentifier(String currency) {
        return accountIdentifier(
                SYSTEM_ACCOUNT_ID,
                "WALLET_TYPE",
                TRANSIENT_WALLET_TYPE + ":" + normalize(currency)
        );
    }

    private AccountIdentifier accountIdentifier(String accountId, String identifierType, String identifierValue) {
        AccountIdentifier identifier = new AccountIdentifier();
        identifier.setAccountId(accountId);
        identifier.setIdentifierType(identifierType);
        identifier.setIdentifierValue(identifierValue);
        identifier.setStatus(Constants.ACCOUNT_STATUS_ACTIVE);
        return identifier;
    }

    private String requestGateway(RequestGateway requestGateway) {
        return requestGateway == null ? RequestGateway.WEB.name() : requestGateway.name();
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String getRequiredServerInstance() {
        String serverInstance = propertyReader.getPropertyValue("server.instance");
        if (serverInstance == null || serverInstance.isBlank()) {
            throw new IllegalStateException("server.instance is not configured");
        }
        return serverInstance.trim();
    }
}
