# PayNest API Request and Response Reference

This document describes the REST APIs exposed by the PayNest Spring Boot service as implemented in the current codebase.

## Common Conventions

### Headers

| Header | Required | Description | Possible values |
| --- | --- | --- | --- |
| `Content-Type` | For JSON request bodies | Request body format. | `application/json` |
| `Authorization` | For secured APIs | Bearer access token returned by login. The JWT contains the account and tenant claims. | `Bearer <accessToken>` |
| `X-Tenant-Id` | Required when no bearer token is present | Logical tenant identifier resolved through `public.tenant_registry`. | Tenant IDs registered in bootstrap, for example `movii` |

Financial endpoints under `/api/v1/pay/**` and transaction endpoints under `/api/v1/transaction/**` rely on JWT tenant resolution when a bearer token is supplied. Non-authenticated tenant-aware APIs require `X-Tenant-Id`.

### Common response wrappers

`ApiResponse` is used by most administration/catalog APIs:

| Field | Type | Description |
| --- | --- | --- |
| `status` | string | Operation status, normally `SUCCESS`. |
| `code` | string | Optional response code. |
| `message` | string | Human-readable message. |
| dynamic body field | object/array | The response payload, keyed by endpoint, for example `pricing`, `tags`, `wallets`, `accountDetails`. |

`ApiErrorResponse` is used for standardized failures:

| Field | Type | Description |
| --- | --- | --- |
| `responseStatus` | string | Transaction-oriented status, for example `FAILURE`. |
| `code` / `errorCode` | string | Error code, for example `INVALID_TOKEN`, `ACCESS_DENIED`, `WALLET_NOT_FOUND`. |
| `message` / `errorMessage` | string | Localized or catalog-backed error message. |
| `traceId` | string | Per-request trace ID. |
| `timestamp` | datetime | Error timestamp in the tenant context. |
| `success` | boolean | Always `false` for errors. |

### Common enum values

| Concept | Values |
| --- | --- |
| User account type | `SUBSCRIBER`, `MERCHANT`, `ADMIN`, `AGENT` |
| Payment account type | `SUBSCRIBER`, `MERCHANT`, `AGENT`, `BILLER`, `BUSINESS` |
| Identifier type | `ACCOUNT_ID`, `MOBILE`, `MSISDN`, `LOGINID` |
| Auth type | `PIN`, `PASSWORD`, `OTP` |
| Wallet type enum | `MAIN`, `BONUS`, `SALARY` |
| System wallet types present in DB | `MAIN`, `BANK`, `SC`, `COMMDIS`, `TAX`, plus configured account wallet types |
| Request gateway | `MOBILE`, `WEB` |
| Initiated by | `DEBITOR`, `CREDITOR` |
| Transaction response status | `SUCCESS`, `FAILURE`, `PENDING` |
| Internal transfer status codes | `TI`, `TP`, `TA`, `TS`, `TF` |
| Account status | `ACTIVE`, `INACTIVE`, `BLOCKED`, `LOCKED`, `SUSPENDED` |
| KYC status | `VERIFIED`, `PENDING`, `REJECTED`, `EXPIRED`, `PENDING_APPROVAL` |
| Bill payment status | `PENDING`, `SUCCESS`, `FAILED` |
| Detail transaction type | `MP`, `MR`, `SCP`, `SCR`, `CP`, `CR`, `DP`, `DR` |

`transaction_details.transaction_type` uses:

| Code | Meaning |
| --- | --- |
| `MP` | Money paid |
| `MR` | Money received |
| `SCP` | Service charge paid |
| `SCR` | Service charge received |
| `CP` | Commission paid |
| `CR` | Commission received |
| `DP` | Discount paid |
| `DR` | Discount received |

## QR Payment APIs

QR APIs are exposed under `/api/v1/qr`. They support static reusable QR codes and dynamic single-use QR intents. QR payment execution delegates to the normal `U2U` or `MERCHANTPAY` services and marks the created transaction with `payment_via_qr = true`.

### `POST /api/v1/qr/generate`

Generates a static or dynamic QR payload and base64 PNG QR image.

For `DYNAMIC` QR, the system creates a row in `qr_payment_intent` with status `ACTIVE`. For `STATIC` QR, no intent row is created.

Request:

| Field | Type | Required | Description | Possible values |
| --- | --- | --- | --- | --- |
| `qrType` | string | Yes | QR lifecycle type. | `STATIC`, `DYNAMIC` |
| `operationType` | string | Yes | Payment service to execute when paid. | `U2U`, `MERCHANTPAY` |
| `creditor.identifierType` | string | Yes | Creditor identifier type. | `MOBILE`, `MSISDN`, `LOGINID`, `ACCOUNT_ID` |
| `creditor.identifierValue` | string | Yes | Creditor identifier value. | Mobile/login/account ID |
| `creditor.accountType` | string | Yes | Creditor account type. | `SUBSCRIBER`, `MERCHANT` |
| `creditor.walletType` | string | Yes | Creditor wallet type. | `MAIN`, `BONUS`, `SALARY` |
| `currency` | string | Yes | Transaction currency. | `USD`, `INR`, configured currencies |
| `amount` | number | Dynamic QR: yes | Fixed amount for dynamic QR. Static QR can omit it. | Positive decimal |
| `expiresInMinutes` | integer | No | Dynamic QR expiry. Defaults to 15 minutes, capped at 1440. | `1` to `1440` |

Example dynamic subscriber QR:

```json
{
  "qrType": "DYNAMIC",
  "operationType": "U2U",
  "creditor": {
    "identifierType": "MOBILE",
    "identifierValue": "8888888888",
    "accountType": "SUBSCRIBER",
    "walletType": "MAIN"
  },
  "currency": "USD",
  "amount": 5.00,
  "expiresInMinutes": 30
}
```

Example dynamic merchant QR:

```json
{
  "qrType": "DYNAMIC",
  "operationType": "MERCHANTPAY",
  "creditor": {
    "identifierType": "LOGINID",
    "identifierValue": "merchant-login",
    "accountType": "MERCHANT",
    "walletType": "MAIN"
  },
  "currency": "INR",
  "amount": 75.00,
  "expiresInMinutes": 30
}
```

Response:

| Field | Type | Description |
| --- | --- | --- |
| `qrType` | string | `STATIC` or `DYNAMIC`. |
| `qrIntentId` | string/null | Present for dynamic QR only. |
| `operationType` | string | `U2U` or `MERCHANTPAY`. |
| `payload` | string | Signed JSON payload used by scan/pay. |
| `qrImageBase64` | string | Base64 PNG image content. |
| `expiresAt` | datetime/null | Dynamic QR expiry timestamp. |

### `GET /api/v1/qr/my-static`

Generates the authenticated user's reusable static QR.

Query parameters:

| Name | Required | Description |
| --- | --- | --- |
| `currency` | Yes | Currency to encode in the QR payload. |
| `walletType` | No | Wallet type. Defaults to `MAIN`. |

Behavior:

- Subscriber users receive a static `U2U` QR.
- Merchant users receive a static `MERCHANTPAY` QR.
- No `qr_payment_intent` row is created because static QR is reusable.

### `POST /api/v1/qr/scan`

Validates a signed QR payload and returns a payment preview.

Request:

```json
{
  "payload": "<signed-payload>"
}
```

Response fields include QR type, intent ID when dynamic, operation type, creditor identifier, creditor account type, wallet type, currency, amount, and expiry.

### `POST /api/v1/qr/pay`

Executes payment for a static or dynamic QR.

Request:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `payload` | string | Usually yes | Signed QR payload. |
| `qrIntentId` | string | Optional | Dynamic intent ID lookup when payload is not supplied. |
| `requestGateway` | string | Yes | Request source, for example `MOBILE`. |
| `preferredLang` | string | No | Preferred language. |
| `initiatedBy` | string | No | Defaults to `DEBITOR`. |
| `debitor` | object | Yes | Paying party. |
| `amount` | number | Static QR: yes | Amount supplied by payer for static QR. Dynamic QR uses the persisted intent amount. |
| `currency` | string | Static QR: optional | Static QR currency override. |
| `paymentReference` | string | No | Optional reference. |
| `comments` | string | No | Optional comments. |
| `metadata` | object | No | Caller metadata. QR service adds `paymentViaQr = true`. |
| `additionalInfo` | object | No | Optional additional information. |

Example:

```json
{
  "payload": "<signed-payload>",
  "requestGateway": "MOBILE",
  "preferredLang": "en",
  "initiatedBy": "DEBITOR",
  "debitor": {
    "accountType": "SUBSCRIBER",
    "walletType": "MAIN",
    "identifier": {
      "type": "MOBILE",
      "value": "9999999999"
    },
    "authentication": {
      "type": "PIN",
      "value": "2468"
    }
  },
  "amount": 5.00,
  "currency": "USD",
  "paymentReference": "qr-pay-001",
  "comments": "QR payment"
}
```

Successful response is either `U2UPaymentResponse` or `MerchpayPaymentResponse`, depending on the QR payload operation type.

## Authentication APIs

### `POST /api/v1/auth/login`

Authenticates an account by identifier and credential.

Request:

| Field | Type | Required | Description | Possible values |
| --- | --- | --- | --- | --- |
| `requestId` | string | yes | Client-generated idempotency/correlation ID. | Any unique string |
| `user.identifierType` | string | yes | Identifier lookup type. | `ACCOUNT_ID`, `MOBILE`, `MSISDN`, `LOGINID` |
| `user.identifierValue` | string | yes | Identifier value. | Account ID, mobile number, login ID |
| `authFactor.authType` | string | yes | Credential type. | `PIN`, `PASSWORD`, `OTP` |
| `authFactor.credential` | string | yes | Secret or OTP value. | Plain credential submitted by client |

Response:

| Field | Type | Description |
| --- | --- | --- |
| `status` | string | Authentication status. |
| `message` | string | Result message. |
| `accountId` | string | Authenticated account ID. |
| `tokenType` | string | Token type, normally `Bearer`. |
| `accessToken` | string | JWT access token. |
| `expiresInSeconds` | number | Token TTL. |

Suspended accounts are still allowed to log in, but the suspension filter limits them to viewing their own wallet balance.

### `GET /api/v1/auth/challenge-token`

Generates a challenge token for an authenticated account.

Headers: `Authorization: Bearer <accessToken>`.

Response fields: `status`, `message`, `accountId`, `tokenType`, `challengeToken`, `expiresInSeconds`.

## Account APIs

### `POST /api/v1/account/register/selfGenOtp`

Starts self-registration and generates an OTP.

Request:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `requestId` | string | yes | Client request identifier. |
| `user.mobile` | string | yes | Mobile number to register. |

Response: `RegistrationResponse` with `status`, `requestId`, `message`, `accountId`.

### `POST /api/v1/account/register/selfWithOtp`

Completes self-registration with OTP.

Request:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `requestId` | string | yes | Client request identifier. |
| `user.mobile` | string | yes | Mobile number. |
| `user.otp` | string | yes | OTP generated by `selfGenOtp`. |

Response: `RegistrationResponse`.

### `POST /api/v1/account/registerUser`

Admin/business user registration endpoint.

Request:

| Field | Type | Required | Description | Possible values |
| --- | --- | --- | --- | --- |
| `requestId` | string | yes | Client request identifier. | Any unique string |
| `user.mobileNumber` | string | yes | Mobile number. | Digits/string |
| `user.accountType` | string | yes | Type of account being created. | `SUBSCRIBER`, `MERCHANT`, `ADMIN`, `AGENT`; payment flows also support `BILLER`, `BUSINESS` |
| `user.firstName` | string | no | First name or business contact name. | Text |
| `user.lastName` | string | no | Last name. | Text |
| `user.email` | string | no | Email address. | Email |
| `user.address` | string | no | Address. | Text |
| `user.gender` | string | no | Gender. | `MALE`, `FEMALE`, `OTHER` |
| `user.dateOfBirth` | date | no | Date of birth. | `YYYY-MM-DD` |
| `user.preferredLang` | string | no | Preferred language. | For example `en` |
| `user.nationality` | string | no | Nationality. | Text |
| `user.ssn` | string | no | National ID/SSN. | Text |
| `user.remarks` | string | no | Remarks. | Text |
| `user.loginId` | string | no | Login identifier. | Text |
| `user.role` | string | no | Role to assign. | `SUPERADMIN`, `ADMIN`, `AGENT`, `MERCHANT`, `SUBSCRIBER`, etc. |

Response: `RegistrationResponse`.

### `POST /api/v1/account/pin/changeDefault` and `POST /api/v1/account/pin/change`

Changes the default or existing PIN.

Request fields:

| Field | Type | Description |
| --- | --- | --- |
| `oldPin` | string | Existing/default PIN. |
| `newPin` | string | New PIN. |
| `accountId` | string | Account ID. |
| `identifierType` | string | Identifier type. |
| `identifierValue` | string | Identifier value. |

Response: simple success/failure response from the controller.

### `POST /api/v1/account/password/changeDefault` and `POST /api/v1/account/password/change`

Changes default or existing password.

Request fields:

| Field | Type | Description |
| --- | --- | --- |
| `requestId` | string | Client request ID. |
| `user.identifierType` | string | `ACCOUNT_ID`, `MOBILE`, `MSISDN`, `LOGINID`. |
| `user.identifierValue` | string | Identifier value. |
| `authFactorOld.authType` | string | Usually `PASSWORD`. |
| `authFactorOld.credential` | string | Current/default password. |
| `authFactorNew.authType` | string | Usually `PASSWORD`. |
| `authFactorNew.credential` | string | New password. |

### `PUT /api/v1/account/updateSelf`

Updates profile information for the authenticated account.

Request fields:

| Field | Type | Description |
| --- | --- | --- |
| `requestId` | string | Client request ID. |
| `user.firstName`, `user.lastName`, `user.email`, `user.address` | string | Profile data. |
| `user.gender` | string | `MALE`, `FEMALE`, `OTHER`. |
| `user.nationality`, `user.ssn` | string | Identity data. |
| `user.dob` | date | Date of birth. |
| `user.preferredLanguage` | string | Preferred language. |
| `user.attr1` ... `user.attr10` | string | Extension attributes. |

Suspended users are blocked from this API.

### `POST /api/v1/account/addKyc`

Adds KYC data.

Request fields:

| Field | Type | Description |
| --- | --- | --- |
| `requestId` | string | Client request ID. |
| `kycData.kycType` | string | KYC document type. |
| `kycData.kycValue` | string | Document number/value. |
| `kycData.issueDate` | date | Issue date. |
| `kycData.expiryDate` | date | Expiry date. |
| `kycData.isPrimary` | boolean | Whether this is the primary KYC document. |
| `kycData.kycImageUrl` | string | Document image URL. |

Suspended users are blocked from this API.

### `GET /api/v1/account/getAccountDetails/{accountId}`

Returns account, KYC documents, and identifiers.

Response body field: `accountDetails`.

### `POST /api/v1/account/{accountId}/suspend`

Admin operation to suspend an account.

Request body:

| Field | Type | Description |
| --- | --- | --- |
| `reason` | string | Audit reason for suspension. |
| `remarks` | string | Additional audit remarks. |

Response body field `accountStatus` contains:

| Field | Type | Description |
| --- | --- | --- |
| `accountId` | string | Suspended account. |
| `accountType` | string | Account type. |
| `previousStatus` | string | Status before suspension. |
| `newStatus` | string | `SUSPENDED`. |
| `actionType` | string | Suspension action. |
| `performedBy` | string | Admin account that performed the change. |
| `performedAt` | datetime | Tenant-local timestamp. |

The service also updates related account tables and refreshes wallet cache after DB updates.

### `POST /api/v1/account/{accountId}/resume`

Admin operation to resume a suspended account. Request and response shape is the same as suspend, with `newStatus = ACTIVE`.

### `GET /api/v1/account/{accountId}/status-history`

Returns audit history from `account_status_history`.

History fields: `historyId`, `accountId`, `accountType`, `actionType`, `previousStatus`, `newStatus`, `performedBy`, `performedByType`, `reason`, `remarks`, `performedAt`.

### `DELETE /api/v1/account/subscriber/{accountId}`

Deletes a subscriber account through the account deletion workflow. Response is a simple success message.

## Wallet APIs

### `GET /api/v1/wallet/getAccountWallets/{accountId}`

Returns wallets and balances for an account.

Response body field: `wallets`.

Wallet fields include `walletId`, `accountId`, `currency`, `walletType`, `status`, `isDefault`, `isLocked`, `createdAt`, `updatedAt`, `remarks`.

Balance fields include `walletType`, `currency`, `availableBalance`, `frozenBalance`, `ficBalance`.

Suspended accounts can only call this endpoint for their own account ID.

### `GET /api/v1/wallet/restrictions/{walletId}`

Returns wallet restriction JSON.

Response body field: `restriction`, containing `walletId`, `restrictions`, `version`, `updatedAt`, `updatedBy`.

### `GET /api/v1/wallet/restrictions/{walletId}/history`

Returns restriction history with `historyId`, `walletId`, `version`, `restrictions`, `actionType`, `changedBy`, `createdAt`.

### `POST /api/v1/wallet/restrictions`

Creates wallet restrictions.

Request:

| Field | Type | Description |
| --- | --- | --- |
| `walletId` | number | Target wallet. |
| `restrictions` | object | JSON restriction definition. |

### `PUT /api/v1/wallet/restrictions/{walletId}`

Updates restrictions for a wallet. Request body contains `walletId` and `restrictions`; path `walletId` is authoritative.

## Payment APIs

### Common party object

Used in U2U, cash-in, cash-out, merchant payment, bill payment, O2C, and stock reimbursement flows.

| Field | Type | Required | Description | Possible values |
| --- | --- | --- | --- | --- |
| `accountType` | string | yes | Party account type. | `SUBSCRIBER`, `MERCHANT`, `AGENT`, `BILLER`, `BUSINESS` |
| `identifier.type` | string | yes | Lookup identifier type. | `ACCOUNT_ID`, `MOBILE`, `MSISDN`, `LOGINID` |
| `identifier.value` | string | yes | Identifier value. | Account/mobile/login |
| `walletType` | string | yes | Wallet used for the transaction. | `MAIN`, `BONUS`, `SALARY` and configured DB wallet types |
| `authentication.type` | string | conditional | Auth type when the party must authenticate. | `PIN`, `PASSWORD`, `OTP` |
| `authentication.value` | string | conditional | Auth credential. | Secret/OTP |

### Common transaction object

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `amount` | decimal | yes | Transaction amount. API amounts are decimal; wallet balances are stored in DB scaled integer form. |
| `currency` | string | yes | Currency code. Supported bootstrap currencies include `USD`, `EUR`, `INR`; `COP` is present but inactive. |
| `initiatedTransactionId` | string | no | Existing transaction ID used by resume/status workflows. |

### Common payment request fields

U2U, CASHIN, CASHOUT, MERCHANTPAY, and BILLPAY share this request shape:

| Field | Type | Description |
| --- | --- | --- |
| `operationType` | string | Service code; should match endpoint, for example `U2U`, `CASHIN`, `MERCHANTPAY`. |
| `requestGateway` | string | `MOBILE` or `WEB`. |
| `preferredLang` | string | Preferred response/notification language. |
| `initiatedBy` | string | `DEBITOR` or `CREDITOR`. |
| `paymentReference` | string | External/client reference, max 100 chars. |
| `comments` | string | Free text, max 300 chars. |
| `debitor` | object | Party being debited. |
| `creditor` | object | Party being credited. |
| `transaction` | object | Amount/currency data. |
| `metadata` | object | Arbitrary metadata persisted with transaction. |
| `additionalInfo` | object | Arbitrary business data. |

### Common payment response fields

| Field | Type | Description |
| --- | --- | --- |
| `responseStatus` | string | `SUCCESS`, `FAILURE`, or `PENDING`. |
| `operationType` | string | Service code. |
| `code` | string | Response code. |
| `message` | string | Result message. |
| `timestamp` | datetime | Tenant-local timestamp. |
| `traceId` | string | Trace/correlation ID. |
| `transactionId` | string | PayNest transaction ID. |
| `amount` | decimal | Transaction amount. |
| `currency` | string | Transaction currency. |

`U2UPaymentResponse` additionally exposes `totalAmount`, `serviceCharge`, `discount`, and `cashback` details when pricing applies. The pricing detail objects contain `amount`, party/payer fields, and `currency`.

### `POST /api/v1/pay/U2U`

Performs user-to-user transfer. Uses common payment request and `U2UPaymentResponse`.

Service code: `U2U`.

Blocked when either authenticated account is suspended or the wallets/restrictions disallow the transfer.

### `POST /api/v1/pay/calculatePricing`

Calculates applicable service charge, commission, discount, and cashback for a payment request without performing the transfer.

Request: same as `U2UPaymentRequest`.

Response body field: pricing computation with `senderTagKey`, `receiverTagKey`, affected party fields, amount totals, selected rule details, and `cashbackPayBy`.

### `POST /api/v1/pay/INTRAWALLET`

Transfers value between wallets of the same account, optionally across currencies.

Request:

| Field | Type | Description |
| --- | --- | --- |
| `requestGateway` | string | `MOBILE`, `WEB`. |
| `preferredLang` | string | Preferred language. |
| `paymentReference` | string | External reference. |
| `comments` | string | Comments. |
| `party` | object | Account party. |
| `sourceWalletType` | string | Source wallet type. |
| `targetWalletType` | string | Target wallet type. |
| `amount` | decimal | Source amount. |
| `sourceCurrency` | string | Source currency. |
| `targetCurrency` | string | Target currency. |
| `metadata` | object | Metadata. |
| `additionalInfo` | object | Additional info. |

Response includes source/target amounts, wallet types, currencies, `exchangeRate`, and `bonusToMainPercentage`.

### `POST /api/v1/pay/MERCHANTPAY`

Pays a merchant. Uses common payment request. Service code: `MERCHANTPAY`.

Pricing may apply service charge, commission, discount, and cashback depending on active pricing rules and tags.

### `POST /api/v1/pay/CASHIN`

Performs cash-in. Uses common payment request. Service code: `CASHIN`.

Typical parties: agent/business as one side and subscriber as the other. Account type `BUSINESS` is supported in payment account type enum.

### `POST /api/v1/pay/CASHOUT`

Performs cash-out. Uses common payment request. Service code: `CASHOUT`.

### `POST /api/v1/pay/BILLPAY`

Pays a biller. Uses common payment request. Service code: `BILLPAY`.

Response includes `billStatus` with `PENDING`, `SUCCESS`, or `FAILED`. Cashback rules can create rows in `cashback_payout` for later payout.

### `POST /api/v1/pay/o2c/initiate`

Initiates operator-to-channel stock transfer.

Request:

| Field | Type | Description |
| --- | --- | --- |
| `operationType` | string | `O2C`. |
| `requestGateway` | string | `MOBILE`, `WEB`. |
| `preferredLang` | string | Preferred language. |
| `paymentReference` | string | External reference. |
| `comments` | string | Comments. |
| `channel` | object | Channel party receiving stock. |
| `transaction` | object | Amount/currency. |
| `metadata` | object | Metadata. |
| `additionalInfo` | object | Additional info. |

Response: `BasePaymentResponse`.

### `POST /api/v1/pay/o2c/status`

Updates O2C status.

Request fields: `transactionId`, `status`, `comments`.

Possible status values follow transaction status conventions such as success/failure/pending values used by the implementation.

### `POST /api/v1/pay/stockInitiate`

Initiates stock creation.

Request fields: `operationType`, `paymentReference`, `comments`, `transaction`, `metadata`, `additionalInfo`.

Response: `BasePaymentResponse`.

### `POST /api/v1/pay/stockStatusUpdate`

Updates stock creation status.

Request fields: `transactionId`, `status`, `errorCode`, `comments`.

### `POST /api/v1/pay/stockReimbursementInitiate`

Initiates stock reimbursement.

Request fields: `paymentReference`, `comments`, `debitor`, `transactor`, `transaction`, `metadata`, `additionalInfo`.

### `POST /api/v1/pay/stockReimbursementStatusUpdate`

Updates stock reimbursement status. Request shape is `StockApprovalRequest`: `transactionId`, `status`, `errorCode`, `comments`.

## Internal Settlement API

### `POST /api/v1/internal/settletxn`

Settles or fails a pending transaction by trace ID.

Request:

| Field | Type | Description |
| --- | --- | --- |
| `traceId` | string | Trace ID of the original transaction. |
| `settlementStatus` | boolean | `true` for settlement success, `false` for failed settlement. |
| `comments` | string | Settlement comments. |
| `additionalInfo` | object | Additional settlement data. |

Response fields: `responseStatus`, `operationType`, `code`, `message`, `timestamp`, `traceId`, `transactionId`, `transactionTraceId`, `serviceCode`, `transferStatus`.

## Transaction Query APIs

### `GET /api/v1/transaction/{accountId}/{transactionId}`

Returns transaction details for an account.

Response fields:

| Field | Type | Description |
| --- | --- | --- |
| `transactionId`, `transferOn`, `accountId` | string | Identity and time fields. |
| `serviceCode`, `serviceName` | string | Service metadata. |
| `transferStatus`, `status`, `errorCode` | string | Transaction status fields. |
| `entryType` | string | `DR` or `CR`. |
| `transactionAmount`, `approvedAmount`, `requestedAmount` | decimal | Monetary values. |
| `previousBalance`, `postBalance` | decimal | Main balance movement. |
| `previousFicBalance`, `postFicBalance` | decimal | FIC balance movement. |
| `previousFrozenBalance`, `postFrozenBalance` | decimal | Frozen balance movement. |
| `paymentReference`, `requestGateway`, `traceId`, `initiatedBy`, `remarks` | string | Request metadata. |
| `debitor`, `creditor` | object | Party details including account, wallet, currency, and entry type. |
| `entries` | array | Ledger-style detail rows. Includes `walletType`, `currency`, and `transaction_type` backed data. |
| `additionalInfo` | object | Parsed additional info. |
| `responseTimestamp` | datetime | Response timestamp. |

### `GET /api/v1/transaction/history` and `GET /api/v1/payment/history`

Query payment history.

Query params:

| Param | Type | Description |
| --- | --- | --- |
| `accountId` | string | Account to filter by. |
| `fromDate` | string | Start date/time filter. |
| `toDate` | string | End date/time filter. |
| `offset` | number | Pagination offset. |
| `limit` | number | Page size. |
| `paymentMethodType` | string | Service/payment method filter. |
| `order` | string | Sort order. |
| `status` | string | Transaction status filter. |

Response: `totalRecords`, `transactions`, `traceId`, `responseTimestamp`.

Each transaction includes account, counterparty, wallet type, currency, balances, service, status, reference, gateway, trace, remarks, and `additionalInfo`.

## Pricing APIs

### `POST /api/v1/pricing`

Creates a pricing rule.

Request:

| Field | Type | Required | Description | Possible values |
| --- | --- | --- | --- | --- |
| `pricingName` | string | yes | Human-readable rule name. | Text |
| `serviceCode` | string | yes | Service where rule applies. | `U2U`, `CASHIN`, `CASHOUT`, `MERCHANTPAY`, `BILLPAY`, `O2C`, `INTRAWALLET`, etc. |
| `ruleType` | string | yes | Pricing component. | `SERVICE_CHARGE`, `COMMISSION`, `DISCOUNT`, `CASHBACK` |
| `pricingType` | string | yes | Rule selection type. | Current code accepts `STATIC`, `CAMPAIGN`. `ALLTAGS` is a tag key, not a pricing type. |
| `payer` | string | yes | Party affected by service charge/commission/discount/cashback. | `SENDER`, `RECEIVER`, `SYSTEM`, `SPLIT` |
| `payBy` | string | conditional | Funding party for commission/discount/cashback. Defaults to `SYSTEM` for non-service-charge logic. | `SYSTEM`, `SENDER`, `RECEIVER` |
| `payerSplit` | object | when `payer=SPLIT` | Split configuration JSON. | JSON |
| `senderTagKey` | string | yes | Sender tag selector. | Tag code such as `SUBSCRIBER_BASE`, `ALLTAGS`; campaign rules require `ALL`. |
| `receiverTagKey` | string | yes | Receiver tag selector. | Tag code such as `MERCHANT_BASE`, `ALLTAGS`; campaign rules require `ALL`. |
| `currency` | string | yes | Currency. | `USD`, `INR`, `EUR`, active configured currency |
| `pricingConfig` | object | yes | Calculation definition. | See below |
| `status` | string | no | Rule status. Defaults to `ACTIVE`. | `ACTIVE`, `INACTIVE` |
| `validFrom` | datetime | no | Start timestamp. | ISO datetime |
| `validTo` | datetime | no | End timestamp. | ISO datetime |

Flat pricing example:

```json
{
  "pricingName": "E2E tag service charge P2P",
  "serviceCode": "U2U",
  "ruleType": "SERVICE_CHARGE",
  "pricingType": "STATIC",
  "payer": "SENDER",
  "senderTagKey": "ALLTAGS",
  "receiverTagKey": "ALLTAGS",
  "currency": "USD",
  "pricingConfig": {
    "basedOn": "TXNAMOUNT",
    "charging_strategy": "FLAT",
    "calc": {
      "type": "FLAT",
      "value": 1.00
    }
  },
  "status": "ACTIVE"
}
```

Response body field: `pricing`, containing `PricingRuleResponse`.

### `GET /api/v1/pricing`

Returns all pricing rules ordered by creation time descending. Response body field: `pricingRules`.

### `GET /api/v1/pricing/{id}`

Returns one pricing rule. Response body field: `pricing`.

### `PATCH /api/v1/pricing/{id}`

Updates mutable fields: `pricingName`, `payer`, `payBy`, `payerSplit`, `pricingConfig`, `status`, `validFrom`, `validTo`.

### `PATCH /api/v1/pricing/{id}/status`

Updates only `status`. Possible values: `ACTIVE`, `INACTIVE`.

## Tag APIs

All tag endpoints are intended for admin scope.

### `GET /api/v1/tags`

Returns all tags in body field `tags`.

Tag fields: `tagId`, `tagCode`, `tagName`, `category`, `isDefault`, `tagType`, `status`, `createdAt`, `updatedAt`.

### `POST /api/v1/tags`

Creates a tag.

Request fields: `tagCode`, `tagName`, `category`, `tagType`.

Response body field: `tag`.

### `GET /api/v1/tags/accounts/{accountId}`

Returns tags linked to an account in body field `accountTags`.

### `POST /api/v1/tags/{tagId}/accounts/{accountId}`

Links a tag to an account. Response body field: `accountTag`.

### `DELETE /api/v1/tags/{tagId}/accounts/{accountId}`

Unlinks a tag from an account.

### `DELETE /api/v1/tags/{tagId}`

Deletes or deactivates a tag through the tag service.

### `GET /api/v1/tags/categories`

Returns category catalog in body field `categories`.

Category fields: `categoryId`, `categoryCode`, `categoryName`, `description`, `status`, `createdAt`, `updatedAt`.

### `GET /api/v1/tags/types`

Returns tag types in body field `tagTypes`.

Tag type fields: `tagTypeId`, `typeCode`, `typeName`, `description`, `status`, `createdAt`, `updatedAt`.

### `GET /api/v1/tags/{tagId}/accounts`

Returns accounts linked to a tag in body field `tagAccounts`.

## FX Rate API

### `POST /api/v1/fx-rates`

Creates a new FX rate version.

Request:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `targetCurrency` | string | yes | Currency being priced against USD. |
| `usdRate` | decimal | yes | Rate value. |
| `rateType` | string | no | Rate type, default in DB is `MID`. |
| `provider` | string | yes | Rate provider/source. |
| `validFrom` | datetime | no | Effective timestamp. |
| `field1` ... `field5` | string | no | Extension fields. |

Response body field `fxRate`: `rateId`, `targetCurrency`, `usdRate`, `rateType`, `provider`, `validFrom`, `versionNo`, `isActive`, `createdAt`, `createdBy`, `field1` ... `field5`.

## Receipt API

### `GET /api/v1/download/receipt`

Downloads a transaction receipt PDF.

Query params:

| Param | Required | Description |
| --- | --- | --- |
| `transactionId` | yes | Transaction to render. |
| `language` | no | Receipt language. |
| `accountId` | no | Account context for party-specific receipt data. |

Response: `application/pdf` byte stream with attachment disposition.

## Cashback Payout Data

Cashback rules do not directly credit the subscriber during the original transaction. They create `cashback_payout` rows for later payment.

Important table fields:

| Field | Description |
| --- | --- |
| `cashback_payout_id` | Primary key. |
| `original_transaction_id` | Transaction that earned cashback. |
| `payout_transaction_id` | Separate payout transaction once paid. |
| `service_code` | Original service, for example `BILLPAY`. |
| `beneficiary_account_id` | Account to receive cashback. |
| `beneficiary_party` | Sender/receiver party selected by pricing. |
| `amount`, `currency` | Cashback amount and currency. |
| `payment_schedule` | When payout should occur. |
| `pay_at` | Due timestamp checked by the hourly scheduler. |
| `status` | `PENDING`, `PAID`, `FAILED`. |
| `pricing_rule_details` | Serialized selected pricing rule details. |
| `failure_reason` | Failure message if payout fails. |

The hourly scheduler pays due pending cashback from `SYS0001` `COMMDIS` wallet to the subscriber `BONUS` wallet if present, otherwise the subscriber `MAIN` wallet.
