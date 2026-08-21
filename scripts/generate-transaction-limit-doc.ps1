param(
    [string]$OutputDocx = "docs/PAYNEST_TRANSACTION_LIMIT_MODULE_DOCUMENTATION.docx"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$converter = Join-Path $PSScriptRoot "convert-paynest-api-doc-to-docx.ps1"
$outputPath = if ([System.IO.Path]::IsPathRooted($OutputDocx)) {
    $OutputDocx
} else {
    Join-Path $projectRoot $OutputDocx
}
$outputDirectory = Split-Path -Parent $outputPath
$converterOutputDocx = if ([System.IO.Path]::IsPathRooted($OutputDocx)) {
    [System.IO.Path]::GetRelativePath($projectRoot, $outputPath)
} else {
    $OutputDocx
}

if (-not (Test-Path -LiteralPath $converter)) {
    throw "DOCX converter not found: $converter"
}
if (-not (Test-Path -LiteralPath $outputDirectory)) {
    New-Item -ItemType Directory -Path $outputDirectory | Out-Null
}

$tempMarkdown = Join-Path ([System.IO.Path]::GetTempPath()) ("paynest-transaction-limit-doc-{0}.md" -f ([guid]::NewGuid()))

$markdown = @'
# PayNest Transaction Limits And Threshold Module

## 1. Document Control

| Field | Value |
|---|---|
| Document | PayNest Transaction Limits And Threshold Module |
| Module | Limits and threshold enforcement |
| Current scope | Global limits |
| Future scope | Service-level limits using the same tables |
| Output file | docs/PAYNEST_TRANSACTION_LIMIT_MODULE_DOCUMENTATION.docx |
| Source generator | scripts/generate-transaction-limit-doc.ps1 |

## 2. Purpose

This module controls how much a customer can send or receive within configured periods such as daily and monthly. It also controls transaction minimum amount, transaction maximum amount, minimum residual balance for the sender, and maximum wallet balance for the receiver.

The first implementation is for GLOBAL limits. SERVICE limits are supported in the data model and API design, but payment validation currently resolves GLOBAL profiles only. This lets the system start with one global policy per customer tag, wallet type, and currency, then later add service-specific rules without changing the core table design.

## 3. Business Decisions Implemented

| Decision | Implemented behavior |
|---|---|
| Customer grouping | Limits are assigned through account tags. The customer must have an active tag in account_tags and the tag must be active in tags. |
| Missing tag | Payment is blocked with LIMIT_TAG_NOT_FOUND. |
| Missing profile | Payment is blocked with LIMIT_PROFILE_NOT_FOUND. |
| Missing subject key | Payment is blocked with LIMIT_SUBJECT_KEY_MISSING. |
| Missing subject value | Payment is blocked with LIMIT_SUBJECT_VALUE_NOT_FOUND. |
| KYC level | No kyc_level column is used. KYC grouping is represented by tag assignment, for example NO_KYC or FULL_KYC tags. |
| Wallet type | No ALL wallet type is used. A profile is created for a concrete wallet type such as MAIN or BONUS. |
| Debitor and creditor | Both DEBITOR and CREDITOR limits are supported and checked independently. |
| Usage reset | Usage rows are reused. When the next day or next month starts, the same usage row is reset to zero before the new transaction is counted. |
| Subject change mid-period | If a profile subject changes from MSISDN to SSN/PAN/ACCOUNT_ID, the next transaction uses the new subject and starts a new usage bucket if no row exists. |
| Subject value storage | transaction_limit_usage stores the normalized raw subject_value so admins can directly see the bucket being consumed. |

## 4. Key Concepts

### 4.1 Tag

A tag is the business group used to decide which limit profile applies to an account. Existing tag tables are used:

| Table | Use |
|---|---|
| tags | Defines tags such as SUBSCRIBER_BASE, MERCHANT_BASE, FULL_KYC, NO_KYC, HIGH_RISK. |
| account_tags | Links an account_id to one or more tags. |

When a payment is processed, the validator reads account_tags for the sender and receiver. If there is no active tag, the transaction is rejected.

### 4.2 Wallet Type

wallet_type means the wallet bucket being moved, for example MAIN, BONUS, SALARY, COMMISSION. The module does not use ALL for wallet type because limits must be explicit for the wallet bucket.

### 4.3 Subject Key

subject_key defines what identity is used to aggregate usage. This is configurable per profile.

| subject_key | Source |
|---|---|
| ACCOUNT_ID | account.account_id |
| MSISDN | account.mobile_number |
| MOBILE | Alias accepted by API and normalized to MSISDN |
| SSN | account.ssn |
| PAN | Approved active KYC document with document_type PAN |
| NATIONAL_ID | Approved active KYC document with document_type NATIONAL_ID |
| AADHAAR | Approved active KYC document with document_type AADHAAR |

Example: If subject_key is PAN and the same customer opens two accounts with two mobile numbers but the same PAN, both accounts share one usage bucket for that PAN.

## 5. Data Model

### 5.1 transaction_limit_profile

This is the top-level limit profile.

| Column | Description |
|---|---|
| limit_id | Primary key. |
| limit_name | Admin readable profile name. |
| tag_id | Tag that this profile applies to. |
| limit_type | GLOBAL or SERVICE. Current validator uses GLOBAL. |
| subject_key | Identity used for usage aggregation. |
| details | JSONB free-form metadata for GUI/audit. |
| status | ACTIVE, INACTIVE, DELETED. |
| wallet_type | Concrete wallet type such as MAIN or BONUS. |
| currency | Currency such as USD, INR, EUR. |
| min_residual_balance | Minimum sender balance after debit as a whole stored currency-factor amount. Applies to DEBITOR. |
| max_balance | Maximum receiver balance after credit as a whole stored currency-factor amount. Applies to CREDITOR. |
| created_by, created_on, modified_by, modified_on, version | Audit and optimistic locking fields. |

If multiple active profiles match the same account tag, wallet type, currency, and limit type, the latest created_on profile is selected.

### 5.2 transaction_limit_profile_details

This stores party-side rules under a profile.

| Column | Description |
|---|---|
| limit_details_id | Primary key. |
| limit_id | Parent profile. |
| party_type | DEBITOR or CREDITOR. |
| status | ACTIVE, INACTIVE, DELETED. |
| operation_type | ALL for global now. Later this can hold service/operation codes such as U2U or BILLPAY. |
| request_gateway | ALL for global now. Can be MOBILE, WEB, API, USSD, PORTAL. |
| min_txn_amount | Minimum transaction amount as a whole stored currency-factor amount. |
| max_txn_amount | Maximum transaction amount as a whole stored currency-factor amount. |
| created_on, modified_on, version | Audit and optimistic locking fields. |

### 5.3 transaction_limit_profile_period

This stores period limits under a detail row.

| Column | Description |
|---|---|
| limit_period_id | Primary key. |
| limit_details_id | Parent detail row. |
| period_type | DAILY or MONTHLY. |
| max_count | Maximum transaction count in the period. |
| max_amount | Maximum transaction amount in the period as a whole stored currency-factor amount. |
| status | ACTIVE, INACTIVE, DELETED. |
| created_on, modified_on, version | Audit and optimistic locking fields. |

### 5.4 transaction_limit_usage

This stores utilization. It is the threshold tracking table.

| Column | Description |
|---|---|
| usage_id | Primary key. |
| subject_key | The configured subject type used for this usage bucket. |
| subject_value | Normalized raw subject value, for example account_id, MSISDN, PAN, SSN, NATIONAL_ID, or AADHAAR. |
| account_id | Last account that used this bucket. Not part of the unique bucket key. |
| limit_id, limit_details_id | Profile and detail row that created this usage. |
| tag_id | Tag that matched the customer. |
| period_type | DAILY or MONTHLY. |
| operation_type, request_gateway | Operation and gateway bucket. GLOBAL currently uses ALL. |
| payer_count, payer_amount | DEBITOR utilization. payer_amount is a whole stored currency-factor amount. |
| payee_count, payee_amount | CREDITOR utilization. payee_amount is a whole stored currency-factor amount. |
| last_transaction_id, last_transaction_date | Last transaction that changed the row. |

The unique usage bucket is:

```text
subject_key + subject_value + limit_id + limit_details_id + period_type + operation_type + request_gateway
```

account_id is intentionally not part of the unique bucket key. This allows PAN/SSN/MSISDN based limits to be shared across more than one account if the profile is configured that way.

period_start and period_end are not stored. The row's last_transaction_date tells the validator whether the existing counters belong to the current day/month. If not, payer and payee counters are reset on the same row before the new payment is counted.

## 6. Runtime Enforcement Flow

1. Payment service locks wallet balances and calculates balance after debit/credit.
2. BalanceService calls TransactionLimitValidator.validateAndReserve.
3. Validator checks DEBITOR first, then CREDITOR.
4. System wallet account SYS0001 is skipped.
5. Account is loaded from account table.
6. Active tags are loaded from account_tags and tags.
7. Active GLOBAL profile is selected by tag_id, wallet_type, currency, and latest created_on.
8. Subject key is checked.
9. Profile detail is selected by party_type plus operation_type/request_gateway. GLOBAL uses ALL.
10. Active period rows are loaded, for example DAILY and MONTHLY.
11. Transaction min/max amount is checked.
12. Balance constraints are checked.
13. Subject value is resolved and normalized.
14. For each period, usage row is selected with PESSIMISTIC_WRITE.
15. If no usage row exists, an empty usage row is inserted with ON CONFLICT DO NOTHING, then selected with lock.
16. If last_transaction_date is outside the current DAILY or MONTHLY period, payer and payee counters are reset on the same row.
17. Next count and amount are calculated.
18. If any count or amount limit would be exceeded, payment is blocked.
19. Usage row is updated and saved before wallet balances and ledger are saved.

## 7. Amount Storage Rule

The validator receives stored database amounts from BalanceService. This means request amount is already multiplied by currency.factor and stored as a whole number.

Example:

| Request amount | currency.factor | Stored limit amount |
|---:|---:|---:|
| 10.00 | 100 | 1000 |
| 250.50 | 100 | 25050 |

Admin APIs currently accept the stored amount. GUI should multiply the human amount before submitting, or backend can later add a display conversion wrapper if needed.

## 8. Default Bootstrap Behavior

The database bootstrap creates generous default GLOBAL profiles for seeded base tags so existing seeded tenants continue to work after strict limit validation is enabled.

Default seeded tags:

| Tag |
|---|
| SUBSCRIBER_BASE |
| MERCHANT_BASE |
| AGENT_BASE |
| BILLER_BASE |
| PARTNER_BASE |

Default seeded wallet/currency combinations:

| Wallet type | Currencies |
|---|---|
| MAIN | USD, EUR, INR |
| BONUS | USD, EUR, INR |

Default seeded profiles use subject_key ACCOUNT_ID and very high daily/monthly count/amount. These defaults are safety defaults only. Real product limits should be configured through the admin APIs.

## 9. Admin API Overview

Base path:

```text
/api/v1/transaction-limits
```

Security:

| Requirement | Value |
|---|---|
| Header | Authorization: Bearer token |
| Tenant header | X-Tenant-Id |
| Role | ADMIN |

Response wrapper:

```json
{
  "status": "SUCCESS",
  "message": "Transaction limit profile fetched successfully",
  "limitProfile": {}
}
```

## 9.1 Financial Transaction Integration

The limit validator is wired into the shared balance movement layer, so financial services that move money are validated before wallet balances and ledgers are committed.

| Area | Limit behavior |
|---|---|
| U2U/P2P subscriber transfer | DEBITOR and CREDITOR global limits are checked. |
| Cash-in | Agent DEBITOR and subscriber CREDITOR global limits are checked. |
| Cash-out | Subscriber DEBITOR and agent CREDITOR global limits are checked. |
| Merchant payment | Subscriber DEBITOR and merchant CREDITOR global limits are checked. |
| Bill payment and generic IPS financial services | Payer and payee global limits are checked through BalanceService. |
| Intra-wallet transfer | Source and target wallet movements are checked because money moves between wallet balances. |
| Stock, commission, discount, cashback payout, service charge settlement | Checked when they call the shared balance service for actual wallet movement. |
| O2C | Exempt by design. O2C is operator-to-channel funding and is not treated as a customer financial limit transaction. |

Non-financial APIs such as login, OTP, registration, document upload, catalog lookup, QR generation, reference-data, and admin configuration do not consume limits because no wallet money movement happens.

Runtime class: `src/main/java/com/paynest/limits/service/TransactionLimitValidator.java`.
Integration points: `BalanceService`, `WalletService`, `StockService`, and payout/adjustment services that call the validator before committing wallet updates.

## 10. API: Get Reference Data

### Request

```http
GET /api/v1/transaction-limits/reference-data
Authorization: Bearer <admin-token>
X-Tenant-Id: tenant-1
```

### Description

Returns values needed by GUI dropdowns: limit types, subject keys, wallet types, party types, period types, request gateways, statuses, and tags.

### Success Response Example

```json
{
  "status": "SUCCESS",
  "message": "Transaction limit reference data fetched successfully",
  "referenceData": {
    "limitTypes": ["GLOBAL", "SERVICE"],
    "subjectKeys": ["AADHAAR", "ACCOUNT_ID", "MSISDN", "NATIONAL_ID", "PAN", "SSN"],
    "walletTypes": ["MAIN", "SALARY", "BONUS", "COMMISSION", "BANK", "COMMDIS", "SC"],
    "partyTypes": ["CREDITOR", "DEBITOR"],
    "periodTypes": ["DAILY", "MONTHLY"],
    "requestGateways": ["ALL", "MOBILE", "WEB", "API", "USSD", "PORTAL"],
    "statuses": ["ACTIVE", "DELETED", "INACTIVE"],
    "tags": [
      {
        "tagId": 1,
        "tagCode": "SUBSCRIBER_BASE",
        "tagName": "Subscriber Base"
      }
    ]
  }
}
```

## 11. API: List Profiles

### Request

```http
GET /api/v1/transaction-limits/profiles?limitType=GLOBAL&status=ACTIVE&walletType=MAIN&currency=USD
Authorization: Bearer <admin-token>
X-Tenant-Id: tenant-1
```

### Query Parameters

| Name | Required | Description |
|---|---|---|
| limitType | No | GLOBAL, SERVICE, or comma-separated GLOBAL,SERVICE. |
| status | No | ACTIVE, INACTIVE, DELETED. |
| tagId | No | Filter by tag id. |
| walletType | No | Filter by wallet type. |
| currency | No | Filter by currency. |
| subjectKey | No | Filter by subject key. |

### Success Response Example

```json
{
  "status": "SUCCESS",
  "message": "Transaction limit profiles fetched successfully",
  "limitProfiles": [
    {
      "limitId": 10,
      "limitName": "Full KYC Global MAIN USD",
      "tagId": 1,
      "tagCode": "FULL_KYC",
      "limitType": "GLOBAL",
      "subjectKey": "PAN",
      "status": "ACTIVE",
      "walletType": "MAIN",
      "currency": "USD"
    }
  ]
}
```

## 12. API: Create Profile

### Request

```http
POST /api/v1/transaction-limits/profiles
Authorization: Bearer <admin-token>
X-Tenant-Id: tenant-1
Content-Type: application/json
```

### Request Body Example

```json
{
  "limitName": "Full KYC Global MAIN USD",
  "tagId": 1,
  "limitType": "GLOBAL",
  "subjectKey": "PAN",
  "details": {
    "businessDescription": "Full KYC customer global limit"
  },
  "status": "ACTIVE",
  "walletType": "MAIN",
  "currency": "USD",
  "minResidualBalance": 0,
  "maxBalance": 50000000,
  "limitDetails": [
    {
      "partyType": "DEBITOR",
      "status": "ACTIVE",
      "operationType": "ALL",
      "requestGateway": "ALL",
      "minTxnAmount": 100,
      "maxTxnAmount": 1000000,
      "periods": [
        {
          "periodType": "DAILY",
          "maxCount": 10,
          "maxAmount": 5000000,
          "status": "ACTIVE"
        },
        {
          "periodType": "MONTHLY",
          "maxCount": 100,
          "maxAmount": 20000000,
          "status": "ACTIVE"
        }
      ]
    },
    {
      "partyType": "CREDITOR",
      "status": "ACTIVE",
      "operationType": "ALL",
      "requestGateway": "ALL",
      "minTxnAmount": 100,
      "maxTxnAmount": 1000000,
      "periods": [
        {
          "periodType": "DAILY",
          "maxCount": 20,
          "maxAmount": 10000000,
          "status": "ACTIVE"
        },
        {
          "periodType": "MONTHLY",
          "maxCount": 200,
          "maxAmount": 40000000,
          "status": "ACTIVE"
        }
      ]
    }
  ]
}
```

### Field Rules

| Field | Rule |
|---|---|
| tagId | Must exist in tags and status must be ACTIVE. |
| limitType | Must be GLOBAL or SERVICE. |
| subjectKey | Must be supported. MOBILE is accepted and normalized to MSISDN. |
| walletType | Required and concrete. Do not send ALL. |
| currency | Required. |
| minResidualBalance, maxBalance | Cannot be negative. |
| partyType | DEBITOR or CREDITOR. |
| operationType, requestGateway | Blank becomes ALL. |
| maxCount, maxAmount | At least one is required for each period. |
| status | Blank becomes ACTIVE. |

### Success Response Example

```json
{
  "status": "SUCCESS",
  "message": "Transaction limit profile created successfully",
  "limitProfile": {
    "limitId": 10,
    "limitName": "Full KYC Global MAIN USD",
    "tagId": 1,
    "tagCode": "FULL_KYC",
    "limitType": "GLOBAL",
    "subjectKey": "PAN",
    "status": "ACTIVE",
    "walletType": "MAIN",
    "currency": "USD",
    "limitDetails": [
      {
        "limitDetailsId": 20,
        "partyType": "DEBITOR",
        "operationType": "ALL",
        "requestGateway": "ALL",
        "periods": [
          {
            "limitPeriodId": 30,
            "periodType": "DAILY",
            "maxCount": 10,
            "maxAmount": 5000000
          }
        ]
      }
    ]
  }
}
```

## 13. API: Get Full Profile Details

### Request

```http
GET /api/v1/transaction-limits/profiles/{limitId}
Authorization: Bearer <admin-token>
X-Tenant-Id: tenant-1
```

### Description

Returns the profile plus all rows from:

| Table |
|---|
| transaction_limit_profile |
| transaction_limit_profile_details |
| transaction_limit_profile_period |

This is the API the GUI should call when an admin opens a profile for editing.

## 14. API: Update Full Profile

### Request

```http
PUT /api/v1/transaction-limits/profiles/{limitId}
Authorization: Bearer <admin-token>
X-Tenant-Id: tenant-1
Content-Type: application/json
```

### Description

Updates profile fields and upserts child detail/period rows.

Important behavior:

| Behavior | Description |
|---|---|
| Existing child update | Send limitDetailsId or limitPeriodId to update an existing row. |
| New child insert | Omit id to insert a new detail or period. |
| Missing child in request | Existing child rows are not deleted automatically. Use status INACTIVE/DELETED if the GUI needs to disable a child row. |
| Subject key change | Next transaction uses the new subject and a new usage bucket if no matching row exists. |
| Profile selection change | If more than one active profile matches, the profile with the latest created_on is used on the next payment. |

### Request Body

Same shape as Create Profile. Include IDs for rows that must be updated.

## 15. API: Update Profile Status

### Request

```http
PATCH /api/v1/transaction-limits/profiles/{limitId}/status
Authorization: Bearer <admin-token>
X-Tenant-Id: tenant-1
Content-Type: application/json
```

### Request Body

```json
{
  "status": "INACTIVE"
}
```

### Description

Updates only profile status. Valid statuses are ACTIVE, INACTIVE, DELETED.

## 16. API: Delete Profile

### Request

```http
DELETE /api/v1/transaction-limits/profiles/{limitId}
Authorization: Bearer <admin-token>
X-Tenant-Id: tenant-1
```

### Description

This is a soft delete. It sets:

| Table | Status set to |
|---|---|
| transaction_limit_profile | DELETED |
| transaction_limit_profile_details | DELETED |
| transaction_limit_profile_period | DELETED |

Historical usage rows remain unchanged.

### Success Response Example

```json
{
  "status": "SUCCESS",
  "message": "Transaction limit profile deleted successfully"
}
```

## 17. API: Get Customer Utilization

### Request By Account

```http
GET /api/v1/transaction-limits/utilization?accountId=AC10001&periodType=DAILY
Authorization: Bearer <admin-token>
X-Tenant-Id: tenant-1
```

### Request By Identifier

```http
GET /api/v1/transaction-limits/utilization?identifierType=MSISDN&identifierValue=919999999999&periodType=MONTHLY
Authorization: Bearer <admin-token>
X-Tenant-Id: tenant-1
```

### Query Parameters

| Name | Required | Description |
|---|---|---|
| accountId | Conditional | Direct account lookup. |
| identifierType | Conditional | Used with identifierValue when accountId is not supplied. MSISDN is normalized to MOBILE for account identifier lookup. |
| identifierValue | Conditional | Identifier value. |
| periodType | No | DAILY or MONTHLY. If omitted, all usage rows for the currently resolved active profile subjects are returned. |

### Success Response Example

```json
{
  "status": "SUCCESS",
  "message": "Transaction limit utilization fetched successfully",
  "limitUtilization": [
    {
      "usageId": 100,
      "subjectKey": "PAN",
      "subjectValue": "ABCDE1234F",
      "accountId": "AC10001",
      "limitId": 10,
      "limitName": "Full KYC Global MAIN USD",
      "partyType": "DEBITOR",
      "periodType": "DAILY",
      "usedCount": 2,
      "maxCount": 10,
      "remainingCount": 8,
      "usedAmount": 250000,
      "maxAmount": 5000000,
      "remainingAmount": 4750000,
      "lastTransactionId": "TXN0001"
    }
  ]
}
```

The API resolves the customer's current active tags, active GLOBAL limit profiles, and configured subject_key before reading utilization. For shared subjects such as PAN or SSN, utilization is read by subject_key plus subject_value, not by transaction_limit_usage.account_id. account_id on usage is only the last account that consumed that bucket.

If the profile subject_key changes in the middle of a day or month, the utilization API uses the new subject immediately. If there is no usage row for the new subject bucket yet, this API returns an empty list. A row is created only when a transaction first uses that bucket.

## 18. Error Catalog Entries

The following errors are seeded into error_catalog by scripts/bootstrap-paynest-db.ps1.

| Error Code | HTTP | Meaning |
|---|---:|---|
| LIMIT_TAG_NOT_FOUND | 400 | No active tag found for the account. |
| LIMIT_SUBJECT_KEY_MISSING | 400 | Limit profile is missing subject_key. |
| LIMIT_SUBJECT_VALUE_NOT_FOUND | 400 | Configured subject value was not found on account/KYC. |
| LIMIT_PROFILE_NOT_FOUND | 400 | No active profile matched tag, wallet type, currency, and GLOBAL type. |
| LIMIT_PROFILE_DETAILS_NOT_FOUND | 400 | No active DEBITOR/CREDITOR detail matched. |
| LIMIT_PERIOD_NOT_CONFIGURED | 400 | No active DAILY/MONTHLY period rows are configured. |
| LIMIT_MIN_TRANSACTION_AMOUNT_NOT_MET | 400 | Transaction amount is below min_txn_amount. |
| LIMIT_MAX_TRANSACTION_AMOUNT_EXCEEDED | 400 | Transaction amount exceeds max_txn_amount. |
| LIMIT_DAILY_COUNT_EXCEEDED | 400 | Daily count would be exceeded. |
| LIMIT_DAILY_AMOUNT_EXCEEDED | 400 | Daily amount would be exceeded. |
| LIMIT_MONTHLY_COUNT_EXCEEDED | 400 | Monthly count would be exceeded. |
| LIMIT_MONTHLY_AMOUNT_EXCEEDED | 400 | Monthly amount would be exceeded. |
| LIMIT_MIN_RESIDUAL_BALANCE_NOT_MET | 400 | Sender balance after payment would go below min_residual_balance. |
| LIMIT_MAX_BALANCE_EXCEEDED | 400 | Receiver balance after payment would exceed max_balance. |

## 19. Example Error Response

```json
{
  "status": "FAILED",
  "code": "LIMIT_DAILY_AMOUNT_EXCEEDED",
  "message": "Daily transaction amount limit exceeded",
  "params": {
    "periodType": "DAILY",
    "limitDetailsId": 20
  }
}
```

## 20. SQL Seed For Error Catalog

```sql
INSERT INTO error_catalog (error_code, language_code, message_template, http_status, error_type, domain, is_active)
VALUES
    ('LIMIT_TAG_NOT_FOUND', 'en', 'No active limit tag found for {partyType} account', 400, 'BUSINESS', 'LIMIT', TRUE),
    ('LIMIT_SUBJECT_KEY_MISSING', 'en', 'Limit subject key is not configured for limit profile {limitId}', 400, 'CONFIGURATION', 'LIMIT', TRUE),
    ('LIMIT_SUBJECT_VALUE_NOT_FOUND', 'en', 'Required limit subject value {subjectKey} was not found for {partyType} account', 400, 'BUSINESS', 'LIMIT', TRUE),
    ('LIMIT_PROFILE_NOT_FOUND', 'en', 'No active transaction limit profile found for {partyType} account', 400, 'BUSINESS', 'LIMIT', TRUE),
    ('LIMIT_PROFILE_DETAILS_NOT_FOUND', 'en', 'No active transaction limit details found for {partyType} account', 400, 'CONFIGURATION', 'LIMIT', TRUE),
    ('LIMIT_PERIOD_NOT_CONFIGURED', 'en', 'No active {periodType} limit period configured for limit details {limitDetailsId}', 400, 'CONFIGURATION', 'LIMIT', TRUE),
    ('LIMIT_MIN_TRANSACTION_AMOUNT_NOT_MET', 'en', 'Transaction amount is below the minimum allowed amount', 400, 'BUSINESS', 'LIMIT', TRUE),
    ('LIMIT_MAX_TRANSACTION_AMOUNT_EXCEEDED', 'en', 'Transaction amount exceeds the maximum allowed amount', 400, 'BUSINESS', 'LIMIT', TRUE),
    ('LIMIT_DAILY_COUNT_EXCEEDED', 'en', 'Daily transaction count limit exceeded', 400, 'BUSINESS', 'LIMIT', TRUE),
    ('LIMIT_DAILY_AMOUNT_EXCEEDED', 'en', 'Daily transaction amount limit exceeded', 400, 'BUSINESS', 'LIMIT', TRUE),
    ('LIMIT_MONTHLY_COUNT_EXCEEDED', 'en', 'Monthly transaction count limit exceeded', 400, 'BUSINESS', 'LIMIT', TRUE),
    ('LIMIT_MONTHLY_AMOUNT_EXCEEDED', 'en', 'Monthly transaction amount limit exceeded', 400, 'BUSINESS', 'LIMIT', TRUE),
    ('LIMIT_MIN_RESIDUAL_BALANCE_NOT_MET', 'en', 'Wallet balance after transaction would be below minimum residual balance', 400, 'BUSINESS', 'LIMIT', TRUE),
    ('LIMIT_MAX_BALANCE_EXCEEDED', 'en', 'Wallet balance after transaction would exceed maximum allowed balance', 400, 'BUSINESS', 'LIMIT', TRUE)
ON CONFLICT (error_code, language_code) DO UPDATE
SET message_template = EXCLUDED.message_template,
    http_status = EXCLUDED.http_status,
    error_type = EXCLUDED.error_type,
    domain = EXCLUDED.domain,
    is_active = EXCLUDED.is_active;
```

## 21. Implementation Files

| Area | Files |
|---|---|
| Constants and errors | src/main/java/com/paynest/limits/TransactionLimitConstants.java, src/main/java/com/paynest/limits/TransactionLimitErrorCode.java |
| Entities | src/main/java/com/paynest/limits/entity |
| Repositories | src/main/java/com/paynest/limits/repository |
| Admin APIs | src/main/java/com/paynest/limits/controller/TransactionLimitController.java |
| Admin service | src/main/java/com/paynest/limits/service/TransactionLimitService.java |
| Runtime validation | src/main/java/com/paynest/limits/service/TransactionLimitValidator.java |
| Subject resolution | src/main/java/com/paynest/limits/service/TransactionLimitSubjectResolver.java |
| Schema initialization | src/main/java/com/paynest/limits/service/TransactionLimitSchemaInitializer.java |
| Payment integration | src/main/java/com/paynest/payments/service/BalanceService.java |
| Bootstrap DDL and seeds | scripts/bootstrap-paynest-db.ps1 |
| Static schema file | src/main/resources/db/transaction-limit-schema.sql |
| Tests | src/test/java/com/paynest/limits/service |

## 22. Future Service-Level Plan

The table structure already supports SERVICE limit profiles. To enable service limits later:

1. Add validator lookup for SERVICE profiles before or after GLOBAL profiles based on the required business order.
2. Store service code in transaction_limit_profile_details.operation_type.
3. Store gateway-specific rules in request_gateway when required.
4. Decide whether SERVICE limit should consume in addition to GLOBAL or replace GLOBAL for matching services.
5. Add GUI filters for service code and gateway.
6. Add API validation for service-specific operation codes.

Recommended future behavior:

| Rule | Recommendation |
|---|---|
| GLOBAL plus SERVICE | Check both. GLOBAL protects total customer exposure; SERVICE protects one payment type. |
| Missing SERVICE profile | If service-level mode is enabled for that service, block; otherwise use GLOBAL only. |
| Usage bucket | Keep operation_type/service_code in usage unique key so service usage stays separate. |

## 23. Operational Notes

| Topic | Note |
|---|---|
| Concurrency | Usage row lookup uses pessimistic write locking. Insert uses ON CONFLICT DO NOTHING to avoid duplicate buckets. |
| Timezone | Period windows use TenantTime, so daily/monthly windows follow tenant time configuration. |
| Manual updates | Profile changes take effect on the next transaction. Existing usage rows are not migrated. |
| Audit | Profile/detail/period have version fields for optimistic locking. |
| Subject visibility | Raw normalized subject values are stored in transaction_limit_usage for admin traceability. |
| Bootstrap | Default generous profiles prevent seeded tenants from being blocked immediately, but production limits should be configured explicitly. |

## 24. Example Admin Workflow

1. Create or verify customer tags such as NO_KYC and FULL_KYC.
2. Assign customers to the correct tags.
3. Open reference-data API and select the tag, wallet type, currency, subject key, party types, and periods.
4. Create a GLOBAL profile for MAIN/USD with DEBITOR and CREDITOR details.
5. Add DAILY and MONTHLY period limits for both party types.
6. Test with one transaction below the limit.
7. Check utilization API for that customer.
8. Try a transaction that exceeds the configured limit and confirm the expected LIMIT_* error.

## 25. Payment-Time Examples

### Example A: Daily debit amount still available

| Field | Value |
|---|---|
| Existing payer_amount | 400000 |
| New transaction debit amount | 50000 |
| Daily max_amount | 500000 |
| Result | Allowed. New payer_amount becomes 450000. |

### Example B: Daily debit amount exceeded

| Field | Value |
|---|---|
| Existing payer_amount | 490000 |
| New transaction debit amount | 20000 |
| Daily max_amount | 500000 |
| Result | Blocked with LIMIT_DAILY_AMOUNT_EXCEEDED. |

### Example C: Subject key changed

| Step | Behavior |
|---|---|
| Current profile subject_key | MSISDN |
| Existing usage row | Stored under subject_key MSISDN and the normalized mobile number. |
| Admin changes subject_key | PAN |
| Next transaction | Validator resolves PAN and searches PAN bucket. |
| No PAN usage row exists | New PAN row starts from 0 for the current period. |

## 26. Complete Admin API Examples

This section gives concrete examples for every transaction limit admin API. All examples assume:

```http
Authorization: Bearer <admin-token>
X-Tenant-Id: tenant-1
Content-Type: application/json
```

Only ADMIN users can call these APIs.

### 26.1 Get Reference Data

Use this API when loading the create/edit screen. It provides dropdown values and active tags.

#### Request

```http
GET /api/v1/transaction-limits/reference-data
Authorization: Bearer <admin-token>
X-Tenant-Id: tenant-1
```

#### Success Response

```json
{
  "status": "SUCCESS",
  "message": "Transaction limit reference data fetched successfully",
  "referenceData": {
    "limitTypes": ["GLOBAL", "SERVICE"],
    "subjectKeys": ["AADHAAR", "ACCOUNT_ID", "MSISDN", "NATIONAL_ID", "PAN", "SSN"],
    "walletTypes": ["MAIN", "SALARY", "BONUS", "COMMISSION", "BANK", "COMMDIS", "SC"],
    "partyTypes": ["CREDITOR", "DEBITOR"],
    "periodTypes": ["DAILY", "MONTHLY"],
    "requestGateways": ["ALL", "MOBILE", "WEB", "API", "USSD", "PORTAL"],
    "statuses": ["ACTIVE", "DELETED", "INACTIVE"],
    "tags": [
      {
        "tagId": 1,
        "tagCode": "FULL_KYC",
        "tagName": "Full KYC"
      },
      {
        "tagId": 2,
        "tagCode": "NO_KYC",
        "tagName": "No KYC"
      }
    ]
  }
}
```

#### GUI Behavior

| Field | Source |
|---|---|
| limitType dropdown | referenceData.limitTypes |
| subjectKey dropdown | referenceData.subjectKeys |
| walletType dropdown | referenceData.walletTypes |
| partyType dropdown | referenceData.partyTypes |
| periodType dropdown | referenceData.periodTypes |
| requestGateway dropdown | referenceData.requestGateways |
| tag selector | referenceData.tags |

### 26.2 List Profiles

Use this API to show the limit profile table in admin GUI.

#### Example A: List All Profiles

```http
GET /api/v1/transaction-limits/profiles
Authorization: Bearer <admin-token>
X-Tenant-Id: tenant-1
```

```json
{
  "status": "SUCCESS",
  "message": "Transaction limit profiles fetched successfully",
  "limitProfiles": [
    {
      "limitId": 10,
      "limitName": "Full KYC Global MAIN USD",
      "tagId": 1,
      "tagCode": "FULL_KYC",
      "tagName": "Full KYC",
      "limitType": "GLOBAL",
      "subjectKey": "PAN",
      "status": "ACTIVE",
      "walletType": "MAIN",
      "currency": "USD",
      "minResidualBalance": 0,
      "maxBalance": 50000000,
      "createdOn": "2026-06-20T10:00:00",
      "modifiedOn": "2026-06-20T10:00:00"
    }
  ]
}
```

#### Example B: List Only Active Global MAIN/USD Profiles

```http
GET /api/v1/transaction-limits/profiles?limitType=GLOBAL&status=ACTIVE&walletType=MAIN&currency=USD
Authorization: Bearer <admin-token>
X-Tenant-Id: tenant-1
```

#### Example C: List Profiles For One Tag

```http
GET /api/v1/transaction-limits/profiles?tagId=1
Authorization: Bearer <admin-token>
X-Tenant-Id: tenant-1
```

#### Example D: List Profiles By Subject Key

```http
GET /api/v1/transaction-limits/profiles?subjectKey=PAN
Authorization: Bearer <admin-token>
X-Tenant-Id: tenant-1
```

#### Example E: List Both Global And Service Profiles

```http
GET /api/v1/transaction-limits/profiles?limitType=GLOBAL,SERVICE&status=ACTIVE
Authorization: Bearer <admin-token>
X-Tenant-Id: tenant-1
```

#### Empty Response

```json
{
  "status": "SUCCESS",
  "message": "Transaction limit profiles fetched successfully",
  "limitProfiles": []
}
```

### 26.3 Create Profile

Use this API to create a complete profile with parent profile, DEBITOR/CREDITOR details, and DAILY/MONTHLY limits.

#### Example A: Create GLOBAL Profile With ACCOUNT_ID Subject

This is the simplest customer-specific profile. Each account gets its own usage bucket.

```http
POST /api/v1/transaction-limits/profiles
Authorization: Bearer <admin-token>
X-Tenant-Id: tenant-1
Content-Type: application/json
```

```json
{
  "limitName": "Subscriber Global MAIN USD",
  "tagId": 1,
  "limitType": "GLOBAL",
  "subjectKey": "ACCOUNT_ID",
  "details": {
    "description": "Default subscriber global limit for MAIN USD"
  },
  "status": "ACTIVE",
  "walletType": "MAIN",
  "currency": "USD",
  "minResidualBalance": 0,
  "maxBalance": 50000000,
  "limitDetails": [
    {
      "partyType": "DEBITOR",
      "status": "ACTIVE",
      "operationType": "ALL",
      "requestGateway": "ALL",
      "minTxnAmount": 100,
      "maxTxnAmount": 1000000,
      "periods": [
        {
          "periodType": "DAILY",
          "maxCount": 10,
          "maxAmount": 5000000,
          "status": "ACTIVE"
        },
        {
          "periodType": "MONTHLY",
          "maxCount": 100,
          "maxAmount": 20000000,
          "status": "ACTIVE"
        }
      ]
    },
    {
      "partyType": "CREDITOR",
      "status": "ACTIVE",
      "operationType": "ALL",
      "requestGateway": "ALL",
      "minTxnAmount": 100,
      "maxTxnAmount": 1000000,
      "periods": [
        {
          "periodType": "DAILY",
          "maxCount": 20,
          "maxAmount": 10000000,
          "status": "ACTIVE"
        },
        {
          "periodType": "MONTHLY",
          "maxCount": 200,
          "maxAmount": 40000000,
          "status": "ACTIVE"
        }
      ]
    }
  ]
}
```

#### Success Response

```json
{
  "status": "SUCCESS",
  "message": "Transaction limit profile created successfully",
  "limitProfile": {
    "limitId": 10,
    "limitName": "Subscriber Global MAIN USD",
    "tagId": 1,
    "tagCode": "FULL_KYC",
    "tagName": "Full KYC",
    "limitType": "GLOBAL",
    "subjectKey": "ACCOUNT_ID",
    "details": {
      "description": "Default subscriber global limit for MAIN USD"
    },
    "status": "ACTIVE",
    "walletType": "MAIN",
    "currency": "USD",
    "minResidualBalance": 0,
    "maxBalance": 50000000,
    "createdBy": "ADMIN001",
    "createdOn": "2026-06-20T10:00:00",
    "modifiedBy": "ADMIN001",
    "modifiedOn": "2026-06-20T10:00:00",
    "limitDetails": [
      {
        "limitDetailsId": 20,
        "limitId": 10,
        "partyType": "DEBITOR",
        "status": "ACTIVE",
        "operationType": "ALL",
        "requestGateway": "ALL",
        "minTxnAmount": 100,
        "maxTxnAmount": 1000000,
        "periods": [
          {
            "limitPeriodId": 30,
            "limitDetailsId": 20,
            "periodType": "DAILY",
            "maxCount": 10,
            "maxAmount": 5000000,
            "status": "ACTIVE"
          },
          {
            "limitPeriodId": 31,
            "limitDetailsId": 20,
            "periodType": "MONTHLY",
            "maxCount": 100,
            "maxAmount": 20000000,
            "status": "ACTIVE"
          }
        ]
      }
    ]
  }
}
```

#### Example B: Create Shared PAN-Based Limit

Use this when one real customer may have more than one account/mobile number but the same PAN. Usage is shared by PAN.

```json
{
  "limitName": "Full KYC PAN Global MAIN INR",
  "tagId": 1,
  "limitType": "GLOBAL",
  "subjectKey": "PAN",
  "details": {
    "description": "Shared PAN limit for Full KYC users"
  },
  "status": "ACTIVE",
  "walletType": "MAIN",
  "currency": "INR",
  "minResidualBalance": 0,
  "maxBalance": 100000000,
  "limitDetails": [
    {
      "partyType": "DEBITOR",
      "status": "ACTIVE",
      "operationType": "ALL",
      "requestGateway": "ALL",
      "minTxnAmount": 100,
      "maxTxnAmount": 5000000,
      "periods": [
        {
          "periodType": "DAILY",
          "maxCount": 25,
          "maxAmount": 25000000,
          "status": "ACTIVE"
        },
        {
          "periodType": "MONTHLY",
          "maxCount": 300,
          "maxAmount": 200000000,
          "status": "ACTIVE"
        }
      ]
    },
    {
      "partyType": "CREDITOR",
      "status": "ACTIVE",
      "operationType": "ALL",
      "requestGateway": "ALL",
      "minTxnAmount": 0,
      "maxTxnAmount": 10000000,
      "periods": [
        {
          "periodType": "DAILY",
          "maxCount": 50,
          "maxAmount": 50000000,
          "status": "ACTIVE"
        },
        {
          "periodType": "MONTHLY",
          "maxCount": 600,
          "maxAmount": 400000000,
          "status": "ACTIVE"
        }
      ]
    }
  ]
}
```

#### Example C: Create MSISDN-Based Limit

Use this when usage should be grouped by mobile number.

```json
{
  "limitName": "No KYC MSISDN Global MAIN USD",
  "tagId": 2,
  "limitType": "GLOBAL",
  "subjectKey": "MSISDN",
  "status": "ACTIVE",
  "walletType": "MAIN",
  "currency": "USD",
  "minResidualBalance": 0,
  "maxBalance": 1000000,
  "limitDetails": [
    {
      "partyType": "DEBITOR",
      "status": "ACTIVE",
      "operationType": "ALL",
      "requestGateway": "ALL",
      "minTxnAmount": 100,
      "maxTxnAmount": 100000,
      "periods": [
        {
          "periodType": "DAILY",
          "maxCount": 3,
          "maxAmount": 200000,
          "status": "ACTIVE"
        }
      ]
    },
    {
      "partyType": "CREDITOR",
      "status": "ACTIVE",
      "operationType": "ALL",
      "requestGateway": "ALL",
      "periods": [
        {
          "periodType": "DAILY",
          "maxCount": 5,
          "maxAmount": 300000,
          "status": "ACTIVE"
        }
      ]
    }
  ]
}
```

#### Create Profile Field Notes

| Field | Notes |
|---|---|
| tagId | Must be an ACTIVE tag. If a customer is not assigned to this tag, this profile will not apply to that customer. |
| subjectKey | Controls the usage bucket identity. ACCOUNT_ID means per account. PAN/SSN/MSISDN can share usage across accounts depending on the value. |
| minResidualBalance | Sender balance after debit cannot go below this value. |
| maxBalance | Receiver balance after credit cannot exceed this value. |
| minTxnAmount/maxTxnAmount | Checked per payment before daily/monthly accumulation. |
| maxCount/maxAmount | At least one should be configured for each period. |
| operationType/requestGateway | Use ALL for current GLOBAL limits. |

### 26.4 Get Full Profile

Use this API when admin opens a profile for view/edit. It returns the profile plus all detail and period rows.

#### Request

```http
GET /api/v1/transaction-limits/profiles/10
Authorization: Bearer <admin-token>
X-Tenant-Id: tenant-1
```

#### Success Response

```json
{
  "status": "SUCCESS",
  "message": "Transaction limit profile fetched successfully",
  "limitProfile": {
    "limitId": 10,
    "limitName": "Full KYC PAN Global MAIN INR",
    "tagId": 1,
    "tagCode": "FULL_KYC",
    "tagName": "Full KYC",
    "limitType": "GLOBAL",
    "subjectKey": "PAN",
    "status": "ACTIVE",
    "walletType": "MAIN",
    "currency": "INR",
    "minResidualBalance": 0,
    "maxBalance": 100000000,
    "limitDetails": [
      {
        "limitDetailsId": 20,
        "limitId": 10,
        "partyType": "DEBITOR",
        "status": "ACTIVE",
        "operationType": "ALL",
        "requestGateway": "ALL",
        "minTxnAmount": 100,
        "maxTxnAmount": 5000000,
        "periods": [
          {
            "limitPeriodId": 30,
            "limitDetailsId": 20,
            "periodType": "DAILY",
            "maxCount": 25,
            "maxAmount": 25000000,
            "status": "ACTIVE"
          },
          {
            "limitPeriodId": 31,
            "limitDetailsId": 20,
            "periodType": "MONTHLY",
            "maxCount": 300,
            "maxAmount": 200000000,
            "status": "ACTIVE"
          }
        ]
      },
      {
        "limitDetailsId": 21,
        "limitId": 10,
        "partyType": "CREDITOR",
        "status": "ACTIVE",
        "operationType": "ALL",
        "requestGateway": "ALL",
        "minTxnAmount": 0,
        "maxTxnAmount": 10000000,
        "periods": [
          {
            "limitPeriodId": 32,
            "limitDetailsId": 21,
            "periodType": "DAILY",
            "maxCount": 50,
            "maxAmount": 50000000,
            "status": "ACTIVE"
          }
        ]
      }
    ]
  }
}
```

#### Not Found Example

```json
{
  "status": "FAILED",
  "code": "INVALID_REQUEST",
  "message": "Limit profile not found"
}
```

### 26.5 Update Full Profile

Use this API to update profile values, child detail rows, and period rows.

Important rules:

| Request shape | Result |
|---|---|
| Send existing limitDetailsId | Updates that detail row. |
| Omit limitDetailsId | Inserts a new detail row. |
| Send existing limitPeriodId | Updates that period row. |
| Omit limitPeriodId | Inserts a new period row. |
| Omit an existing child row from request | Existing child row is not deleted. Use status INACTIVE or DELETED if it should stop applying. |

#### Example A: Update Daily Amount And Count

```http
PUT /api/v1/transaction-limits/profiles/10
Authorization: Bearer <admin-token>
X-Tenant-Id: tenant-1
Content-Type: application/json
```

```json
{
  "limitName": "Full KYC PAN Global MAIN INR",
  "tagId": 1,
  "limitType": "GLOBAL",
  "subjectKey": "PAN",
  "status": "ACTIVE",
  "walletType": "MAIN",
  "currency": "INR",
  "minResidualBalance": 0,
  "maxBalance": 100000000,
  "limitDetails": [
    {
      "limitDetailsId": 20,
      "partyType": "DEBITOR",
      "status": "ACTIVE",
      "operationType": "ALL",
      "requestGateway": "ALL",
      "minTxnAmount": 100,
      "maxTxnAmount": 5000000,
      "periods": [
        {
          "limitPeriodId": 30,
          "periodType": "DAILY",
          "maxCount": 30,
          "maxAmount": 30000000,
          "status": "ACTIVE"
        },
        {
          "limitPeriodId": 31,
          "periodType": "MONTHLY",
          "maxCount": 300,
          "maxAmount": 200000000,
          "status": "ACTIVE"
        }
      ]
    }
  ]
}
```

#### Success Response

```json
{
  "status": "SUCCESS",
  "message": "Transaction limit profile updated successfully",
  "limitProfile": {
    "limitId": 10,
    "limitName": "Full KYC PAN Global MAIN INR",
    "tagId": 1,
    "limitType": "GLOBAL",
    "subjectKey": "PAN",
    "status": "ACTIVE",
    "walletType": "MAIN",
    "currency": "INR",
    "limitDetails": [
      {
        "limitDetailsId": 20,
        "partyType": "DEBITOR",
        "periods": [
          {
            "limitPeriodId": 30,
            "periodType": "DAILY",
            "maxCount": 30,
            "maxAmount": 30000000,
            "status": "ACTIVE"
          }
        ]
      }
    ]
  }
}
```

#### Example B: Add A Monthly Period To Existing Detail

In this example, limitDetailsId is present, but limitPeriodId is omitted for MONTHLY. That inserts a new monthly period row.

```json
{
  "limitName": "No KYC MSISDN Global MAIN USD",
  "tagId": 2,
  "limitType": "GLOBAL",
  "subjectKey": "MSISDN",
  "status": "ACTIVE",
  "walletType": "MAIN",
  "currency": "USD",
  "minResidualBalance": 0,
  "maxBalance": 1000000,
  "limitDetails": [
    {
      "limitDetailsId": 40,
      "partyType": "DEBITOR",
      "status": "ACTIVE",
      "operationType": "ALL",
      "requestGateway": "ALL",
      "minTxnAmount": 100,
      "maxTxnAmount": 100000,
      "periods": [
        {
          "limitPeriodId": 50,
          "periodType": "DAILY",
          "maxCount": 3,
          "maxAmount": 200000,
          "status": "ACTIVE"
        },
        {
          "periodType": "MONTHLY",
          "maxCount": 20,
          "maxAmount": 1000000,
          "status": "ACTIVE"
        }
      ]
    }
  ]
}
```

#### Example C: Deactivate One Period

```json
{
  "limitName": "No KYC MSISDN Global MAIN USD",
  "tagId": 2,
  "limitType": "GLOBAL",
  "subjectKey": "MSISDN",
  "status": "ACTIVE",
  "walletType": "MAIN",
  "currency": "USD",
  "limitDetails": [
    {
      "limitDetailsId": 40,
      "partyType": "DEBITOR",
      "status": "ACTIVE",
      "operationType": "ALL",
      "requestGateway": "ALL",
      "periods": [
        {
          "limitPeriodId": 50,
          "periodType": "DAILY",
          "maxCount": 3,
          "maxAmount": 200000,
          "status": "INACTIVE"
        }
      ]
    }
  ]
}
```

#### Example D: Change Subject Key From MSISDN To PAN

```json
{
  "limitName": "Full KYC PAN Global MAIN USD",
  "tagId": 1,
  "limitType": "GLOBAL",
  "subjectKey": "PAN",
  "status": "ACTIVE",
  "walletType": "MAIN",
  "currency": "USD",
  "minResidualBalance": 0,
  "maxBalance": 50000000,
  "limitDetails": [
    {
      "limitDetailsId": 20,
      "partyType": "DEBITOR",
      "status": "ACTIVE",
      "operationType": "ALL",
      "requestGateway": "ALL",
      "periods": [
        {
          "limitPeriodId": 30,
          "periodType": "DAILY",
          "maxCount": 10,
          "maxAmount": 5000000,
          "status": "ACTIVE"
        }
      ]
    }
  ]
}
```

After this update, the next transaction resolves PAN and uses the PAN usage bucket. If no PAN usage row exists, the bucket starts from zero.

### 26.6 Update Profile Status

Use this API for quick activation/deactivation without sending the full profile body.

#### Example A: Deactivate Profile

```http
PATCH /api/v1/transaction-limits/profiles/10/status
Authorization: Bearer <admin-token>
X-Tenant-Id: tenant-1
Content-Type: application/json
```

```json
{
  "status": "INACTIVE"
}
```

```json
{
  "status": "SUCCESS",
  "message": "Transaction limit profile status updated successfully",
  "limitProfile": {
    "limitId": 10,
    "limitName": "Full KYC PAN Global MAIN INR",
    "status": "INACTIVE",
    "limitType": "GLOBAL",
    "subjectKey": "PAN",
    "walletType": "MAIN",
    "currency": "INR"
  }
}
```

#### Example B: Reactivate Profile

```json
{
  "status": "ACTIVE"
}
```

#### Example C: Mark Profile Deleted

```json
{
  "status": "DELETED"
}
```

### 26.7 Delete Profile

This is a soft delete. It sets the profile, details, and periods to DELETED.

#### Request

```http
DELETE /api/v1/transaction-limits/profiles/10
Authorization: Bearer <admin-token>
X-Tenant-Id: tenant-1
```

#### Success Response

```json
{
  "status": "SUCCESS",
  "message": "Transaction limit profile deleted successfully"
}
```

#### Database Effect

| Table | Effect |
|---|---|
| transaction_limit_profile | status becomes DELETED |
| transaction_limit_profile_details | status becomes DELETED |
| transaction_limit_profile_period | status becomes DELETED |
| transaction_limit_usage | unchanged |

### 26.8 Get Customer Utilization

Use this API to view usage consumed by a customer. This is read-only. It does not create usage rows and does not reset counters. Counter reset happens during the next payment transaction.

#### Example A: Utilization By Account ID For Daily Limit

```http
GET /api/v1/transaction-limits/utilization?accountId=AC10001&periodType=DAILY
Authorization: Bearer <admin-token>
X-Tenant-Id: tenant-1
```

```json
{
  "status": "SUCCESS",
  "message": "Transaction limit utilization fetched successfully",
  "limitUtilization": [
    {
      "usageId": 100,
      "subjectKey": "ACCOUNT_ID",
      "subjectValue": "AC10001",
      "accountId": "AC10001",
      "limitId": 10,
      "limitName": "Subscriber Global MAIN USD",
      "limitDetailsId": 20,
      "limitPeriodId": 30,
      "tagId": 1,
      "tagCode": "FULL_KYC",
      "partyType": "DEBITOR",
      "walletType": "MAIN",
      "currency": "USD",
      "periodType": "DAILY",
      "operationType": "ALL",
      "requestGateway": "ALL",
      "usedCount": 2,
      "maxCount": 10,
      "remainingCount": 8,
      "usedAmount": 250000,
      "maxAmount": 5000000,
      "remainingAmount": 4750000,
      "lastTransactionId": "TXN0001",
      "lastTransactionDate": "2026-06-20T11:30:00"
    }
  ]
}
```

#### Example B: Utilization By MSISDN Identifier

identifierType MSISDN is used only to find the account through account_identifiers. Internally it maps to identifier_type MOBILE.

```http
GET /api/v1/transaction-limits/utilization?identifierType=MSISDN&identifierValue=919999999999&periodType=MONTHLY
Authorization: Bearer <admin-token>
X-Tenant-Id: tenant-1
```

```json
{
  "status": "SUCCESS",
  "message": "Transaction limit utilization fetched successfully",
  "limitUtilization": [
    {
      "usageId": 120,
      "subjectKey": "MSISDN",
      "subjectValue": "919999999999",
      "accountId": "AC10001",
      "limitId": 11,
      "limitName": "No KYC MSISDN Global MAIN USD",
      "partyType": "DEBITOR",
      "walletType": "MAIN",
      "currency": "USD",
      "periodType": "MONTHLY",
      "usedCount": 8,
      "maxCount": 20,
      "remainingCount": 12,
      "usedAmount": 600000,
      "maxAmount": 1000000,
      "remainingAmount": 400000,
      "lastTransactionId": "TXN0099",
      "lastTransactionDate": "2026-06-20T09:15:00"
    }
  ]
}
```

#### Example C: PAN Shared Utilization

If profile subjectKey is PAN, utilization is grouped by PAN even if the admin searches by accountId or mobile.

```http
GET /api/v1/transaction-limits/utilization?accountId=AC10001&periodType=DAILY
Authorization: Bearer <admin-token>
X-Tenant-Id: tenant-1
```

```json
{
  "status": "SUCCESS",
  "message": "Transaction limit utilization fetched successfully",
  "limitUtilization": [
    {
      "usageId": 150,
      "subjectKey": "PAN",
      "subjectValue": "ABCDE1234F",
      "accountId": "AC20002",
      "limitId": 12,
      "limitName": "Full KYC PAN Global MAIN INR",
      "partyType": "DEBITOR",
      "walletType": "MAIN",
      "currency": "INR",
      "periodType": "DAILY",
      "usedCount": 4,
      "maxCount": 25,
      "remainingCount": 21,
      "usedAmount": 1000000,
      "maxAmount": 25000000,
      "remainingAmount": 24000000,
      "lastTransactionId": "TXN0100",
      "lastTransactionDate": "2026-06-20T12:00:00"
    }
  ]
}
```

In this example, accountId is AC20002 because it is the last account that used the PAN bucket. The bucket still belongs to subjectValue ABCDE1234F and can be shared by another account with the same PAN.

#### Example D: Utilization For All Periods

Omit periodType to return both DAILY and MONTHLY usage rows.

```http
GET /api/v1/transaction-limits/utilization?accountId=AC10001
Authorization: Bearer <admin-token>
X-Tenant-Id: tenant-1
```

```json
{
  "status": "SUCCESS",
  "message": "Transaction limit utilization fetched successfully",
  "limitUtilization": [
    {
      "usageId": 100,
      "subjectKey": "ACCOUNT_ID",
      "subjectValue": "AC10001",
      "periodType": "DAILY",
      "usedCount": 2,
      "maxCount": 10,
      "remainingCount": 8,
      "usedAmount": 250000,
      "maxAmount": 5000000,
      "remainingAmount": 4750000
    },
    {
      "usageId": 101,
      "subjectKey": "ACCOUNT_ID",
      "subjectValue": "AC10001",
      "periodType": "MONTHLY",
      "usedCount": 8,
      "maxCount": 100,
      "remainingCount": 92,
      "usedAmount": 1200000,
      "maxAmount": 20000000,
      "remainingAmount": 18800000
    }
  ]
}
```

#### Example E: Empty Utilization

This happens when the customer has a valid active profile, but no transaction has created the usage bucket yet.

```json
{
  "status": "SUCCESS",
  "message": "Transaction limit utilization fetched successfully",
  "limitUtilization": []
}
```

#### Utilization Error Examples

Missing accountId and identifier:

```json
{
  "status": "FAILED",
  "code": "INVALID_REQUEST",
  "message": "accountId or identifierType/identifierValue is required"
}
```

Identifier not found:

```json
{
  "status": "FAILED",
  "code": "ACCOUNT_IDENTIFIER_NOT_FOUND",
  "message": "Active account identifier not found",
  "params": {
    "identifierType": "MOBILE"
  }
}
```

Customer has no active tag:

```json
{
  "status": "FAILED",
  "code": "LIMIT_TAG_NOT_FOUND",
  "message": "No active limit tag found for CUSTOMER account"
}
```

Customer tag has no matching active profile:

```json
{
  "status": "FAILED",
  "code": "LIMIT_PROFILE_NOT_FOUND",
  "message": "No active transaction limit profile found for CUSTOMER account"
}
```

### 26.9 Common Validation Error Examples

#### Missing Required Field During Create

```json
{
  "status": "FAILED",
  "code": "VALIDATION_ERROR",
  "message": "limitName is required"
}
```

#### Invalid limitType

```json
{
  "status": "FAILED",
  "code": "INVALID_REQUEST",
  "message": "Invalid limitType"
}
```

#### Invalid subjectKey

```json
{
  "status": "FAILED",
  "code": "INVALID_REQUEST",
  "message": "Invalid subjectKey"
}
```

#### Invalid status

```json
{
  "status": "FAILED",
  "code": "INVALID_REQUEST",
  "message": "Invalid status"
}
```

#### Inactive Or Missing Tag

```json
{
  "status": "FAILED",
  "code": "TAG_NOT_FOUND",
  "message": "Active tag not found"
}
```

#### Invalid Period Configuration

```json
{
  "status": "FAILED",
  "code": "INVALID_REQUEST",
  "message": "maxCount or maxAmount is required"
}
```

### 26.10 API Summary Matrix

| API | Creates row | Updates row | Reads row | Soft deletes | Runtime payment impact |
|---|---:|---:|---:|---:|---|
| GET /reference-data | No | No | Yes | No | No direct impact. |
| GET /profiles | No | No | Yes | No | No direct impact. |
| POST /profiles | Yes | No | Yes | No | New ACTIVE profile can apply to next payment. |
| GET /profiles/{limitId} | No | No | Yes | No | No direct impact. |
| PUT /profiles/{limitId} | Possible child insert | Yes | Yes | No | Updated profile/detail/period applies to next payment. |
| PATCH /profiles/{limitId}/status | No | Yes | Yes | No | ACTIVE/INACTIVE/DELETED affects next payment. |
| DELETE /profiles/{limitId} | No | Yes | No | Yes | Deleted profile no longer applies. |
| GET /utilization | No | No | Yes | No | No direct impact; does not reset counters. |

### 26.11 Recommended Admin GUI Flow

1. Call reference-data.
2. Show profile list using GET /profiles.
3. For create, submit POST /profiles with both DEBITOR and CREDITOR details.
4. For edit, first call GET /profiles/{limitId}.
5. Submit PUT /profiles/{limitId} with existing limitDetailsId and limitPeriodId.
6. Use PATCH /status for quick activate/deactivate.
7. Use DELETE only for soft deletion.
8. Use GET /utilization when admin wants to see customer consumed limits.

'@

try {
    Set-Content -LiteralPath $tempMarkdown -Value $markdown -Encoding UTF8
    Push-Location $projectRoot
    try {
        & $converter `
            -InputMarkdown $tempMarkdown `
            -OutputDocx $converterOutputDocx `
            -Title "PayNest Transaction Limits And Threshold Module"
    } finally {
        Pop-Location
    }
} finally {
    if (Test-Path -LiteralPath $tempMarkdown) {
        Remove-Item -LiteralPath $tempMarkdown -Force
    }
}
