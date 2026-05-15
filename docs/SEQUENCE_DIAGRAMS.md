# PayNest Sequence Diagrams

The diagrams use Mermaid syntax and focus on the main runtime flows exposed by the API.

## Login

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant AuthController
    participant AuthService
    participant TenantContext
    participant AccountRepo
    participant JwtService

    Client->>AuthController: POST /api/v1/auth/login
    AuthController->>AuthService: login(request)
    AuthService->>TenantContext: resolve tenant from X-Tenant-Id
    AuthService->>AccountRepo: find account identifier and auth record
    AccountRepo-->>AuthService: account/auth data
    AuthService->>AuthService: validate PIN/password/OTP and account status
    AuthService->>JwtService: create access token with account and tenant claims
    JwtService-->>AuthService: JWT
    AuthService-->>AuthController: AuthLoginResponse
    AuthController-->>Client: 200 OK
```

## Self Registration

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant AccountController
    participant AccountService
    participant OtpStore
    participant AccountRepo
    participant WalletService

    Client->>AccountController: POST /api/v1/account/register/selfGenOtp
    AccountController->>AccountService: generateOtpForRegistration()
    AccountService->>OtpStore: persist OTP reference
    AccountController-->>Client: OTP generated

    Client->>AccountController: POST /api/v1/account/register/selfWithOtp
    AccountController->>AccountService: registerUser(request)
    AccountService->>OtpStore: validate OTP
    AccountService->>AccountRepo: create account, identifiers, auth data
    AccountService->>WalletService: create default wallets and balances
    AccountController-->>Client: RegistrationResponse(accountId)
```

## U2U Payment

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant PayController as FinancialTransactionController
    participant U2U as U2UPaymentService
    participant PricingService
    participant TransactionsService
    participant BalanceService
    participant WalletLedger

    Client->>PayController: POST /api/v1/pay/U2U
    PayController->>U2U: processPayment(request, validateJWT=true)
    U2U->>U2U: validate parties, JWT, auth, wallets, limits
    U2U->>PricingService: calculatePricingAmounts(request)
    PricingService-->>U2U: charges/discount/commission/cashback
    U2U->>TransactionsService: generateTransactionRecord()
    U2U->>BalanceService: transferWalletAmount or transferWalletAmountWithPricing()
    BalanceService->>WalletLedger: debit payer, credit receiver, persist ledger/details
    BalanceService-->>U2U: balances updated
    U2U-->>PayController: U2UPaymentResponse
    PayController-->>Client: 200 OK
```

## Merchant Payment

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant PayController as FinancialTransactionController
    participant MerchantPay as MerchPayPaymentService
    participant PricingService
    participant TransactionsService
    participant BalanceService

    Client->>PayController: POST /api/v1/pay/MERCHANTPAY
    PayController->>MerchantPay: processPayment(request, validateJWT=true)
    MerchantPay->>MerchantPay: validate subscriber debtor and merchant creditor
    MerchantPay->>PricingService: calculatePricingAmounts(request)
    MerchantPay->>TransactionsService: generateTransactionRecord()
    MerchantPay->>BalanceService: transfer wallet amount
    BalanceService-->>MerchantPay: transfer complete
    MerchantPay-->>PayController: MerchpayPaymentResponse
    PayController-->>Client: 200 OK
```

## Static QR Payment

```mermaid
sequenceDiagram
    autonumber
    participant CreditorApp
    participant PayerApp
    participant QrController
    participant QrService
    participant PaymentService as U2U/MerchantPay Service
    participant TransactionsService
    participant BalanceService

    CreditorApp->>QrController: GET /api/v1/qr/my-static?currency=USD&walletType=MAIN
    QrController->>QrService: generateMyStaticQr()
    QrService->>QrService: resolve current account and sign static payload
    QrService-->>CreditorApp: payload + QR image

    PayerApp->>QrController: POST /api/v1/qr/scan
    QrController->>QrService: scan(payload)
    QrService->>QrService: verify signature and decode creditor
    QrService-->>PayerApp: payment preview

    PayerApp->>QrController: POST /api/v1/qr/pay with amount
    QrController->>QrService: pay(request)
    QrService->>PaymentService: processPayment(metadata.paymentViaQr=true)
    PaymentService->>TransactionsService: create transaction with payment_via_qr=true
    PaymentService->>BalanceService: move wallet funds
    PaymentService-->>QrService: payment response
    QrService-->>PayerApp: U2U or MERCHANTPAY response
```

## Dynamic QR Payment

```mermaid
sequenceDiagram
    autonumber
    participant CreditorApp
    participant PayerApp
    participant QrController
    participant QrService
    participant QrIntentRepo
    participant PaymentService as U2U/MerchantPay Service
    participant TransactionsService

    CreditorApp->>QrController: POST /api/v1/qr/generate (DYNAMIC)
    QrController->>QrService: generate(request)
    QrService->>QrIntentRepo: save ACTIVE intent(amount,currency,expiry)
    QrIntentRepo-->>QrService: qrIntentId
    QrService->>QrService: sign dynamic payload
    QrService-->>CreditorApp: qrIntentId + payload + QR image

    PayerApp->>QrController: POST /api/v1/qr/scan
    QrController->>QrService: scan(payload)
    QrService->>QrIntentRepo: load intent
    QrService->>QrService: reject expired/paid/tampered QR
    QrService-->>PayerApp: amount-bound preview

    PayerApp->>QrController: POST /api/v1/qr/pay
    QrController->>QrService: pay(request)
    QrService->>QrIntentRepo: load ACTIVE intent
    QrService->>PaymentService: processPayment(metadata.paymentViaQr=true)
    PaymentService->>TransactionsService: create transaction with payment_via_qr=true
    PaymentService-->>QrService: transactionId
    QrService->>QrIntentRepo: status=PAID, transactionId=transactionId
    QrService-->>PayerApp: payment response
```

## Stock and O2C Funding

```mermaid
sequenceDiagram
    autonumber
    participant AdminA
    participant AdminB
    participant PayController
    participant StockService
    participant O2CService
    participant TransactionsService
    participant BalanceService

    AdminA->>PayController: POST /api/v1/pay/stockInitiate
    PayController->>StockService: initiateStock()
    StockService->>TransactionsService: create PENDING stock transaction
    PayController-->>AdminA: STOCK_INITIATED

    AdminB->>PayController: POST /api/v1/pay/stockStatusUpdate APPROVED
    PayController->>StockService: updateStockTransactionStatus()
    StockService->>BalanceService: fund system/operator wallet
    PayController-->>AdminB: STOCK_APPROVED

    AdminA->>PayController: POST /api/v1/pay/o2c/initiate
    PayController->>O2CService: processPayment()
    O2CService->>TransactionsService: create PENDING O2C transaction
    PayController-->>AdminA: O2C_INITIATED

    AdminB->>PayController: POST /api/v1/pay/o2c/status APPROVED
    PayController->>O2CService: updateO2CTransactionStatus()
    O2CService->>BalanceService: move stock from operator to channel user
    PayController-->>AdminB: O2C_APPROVED
```

## Transaction History and Receipt

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant TransactionController
    participant PaymentHistoryService
    participant TransactionDetailService
    participant ReceiptController
    participant ReceiptService
    participant Database

    Client->>TransactionController: GET /api/v1/payment/history
    TransactionController->>PaymentHistoryService: getPaymentHistory(filters)
    PaymentHistoryService->>Database: query transactions and details
    PaymentHistoryService-->>Client: PaymentHistoryResponse

    Client->>TransactionController: GET /api/v1/transaction/{accountId}/{transactionId}
    TransactionController->>TransactionDetailService: getTransactionDetail()
    TransactionDetailService->>Database: query transaction, parties, balances
    TransactionDetailService-->>Client: TransactionDetailResponse

    Client->>ReceiptController: GET /api/v1/download/receipt
    ReceiptController->>ReceiptService: downloadReceipt(transactionId, language, accountId)
    ReceiptService->>Database: load transaction detail
    ReceiptService-->>Client: PDF bytes
```
