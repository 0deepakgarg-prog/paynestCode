# PayNest Functional Specification

This document describes the functional behavior of the PayNest wallet and payment platform as implemented in the current project.

## System Overview

PayNest is a multi-tenant digital wallet platform. It supports subscriber wallets, business users, agents, merchants, billers, system/operator wallets, financial transfers, catalog-based pricing, transaction history, statements, account suspension, and audit-oriented operational data.

The application is implemented as a Spring Boot service with tenant-aware database access. Most business data lives in tenant schemas, while tenant lookup data is stored in `public.tenant_registry`.

## Multi-Tenancy

Multi-tenancy is a core system feature.

### Tenant registry

The public table `tenant_registry` maps a logical tenant ID to a physical PostgreSQL schema:

| Field | Purpose |
| --- | --- |
| `tenant_id` | Tenant identifier used by clients and JWTs. |
| `tenant_name` | Human-readable tenant name. |
| `schema_name` | Physical tenant schema. |
| `iana_time_zone` | Tenant-local time zone. |
| `status` | Tenant status, normally `ACTIVE`. |

Bootstrap creates the tenant schema and registers the mapping.

### Tenant resolution

Tenant resolution happens in filters before controller logic:

- Requests without a bearer token use `X-Tenant-Id`.
- JWT-authenticated requests use the tenant claim from the token.
- Financial endpoints under `/api/v1/pay/**` and transaction endpoints under `/api/v1/transaction/**` are allowed to proceed to JWT tenant resolution when a bearer token is present.
- Unknown or missing tenants are rejected before business logic runs.

### Tenant context

`TenantContext` stores the current schema, tenant ID, and time zone in thread-local context. Hibernate multi-tenancy uses this to route repository operations to the correct schema. `TenantTime` uses the tenant time zone for timestamps such as transaction time, audit history, and payout processing.

### Data isolation

Each tenant schema contains its own operational tables, including accounts, wallets, balances, transactions, transaction details, pricing rules, tags, cashback payouts, FX rates, notification templates, wallet restrictions, and audit logs. This keeps tenant data isolated at schema level while still allowing a shared application instance.

### Bootstrap behavior

The bootstrap script creates:

- Tenant registry entry and tenant schema.
- Account, auth, identifier, KYC, wallet, wallet balance, wallet restriction, and history tables.
- Transaction, transaction details, wallet ledger, bill payment status, cashback payout, and service catalog tables.
- Pricing, tag, category, role, permission, notification, error catalog, language, and FX rate support tables.
- Default admin account and `SYS0001` system account.
- System wallets for `SYS0001`: `MAIN`, `BANK`, `SC`, `COMMDIS`, `TAX` across `USD`, `INR`, and `EUR`.

## Account Management

### Account types

The user domain supports `SUBSCRIBER`, `MERCHANT`, `ADMIN`, and `AGENT`. Payment flows also support `BILLER` and `BUSINESS` account types for cash-in, cash-out, merchant payment, and bill payment participants.

### Self-registration

Subscriber self-registration is OTP-based:

1. Client calls `selfGenOtp` with a mobile number.
2. System creates an OTP reference.
3. Client calls `selfWithOtp` with the mobile number and OTP.
4. System creates account, identifiers, auth data, and wallets.

Testing mode in bootstrap supports fixed credentials for test automation.

### Admin/business registration

Admin registration can create operational users such as agents and merchants. The request supports personal/contact details, login ID, role assignment, and account type.

### Authentication

The login flow accepts identifier type/value plus an auth factor. Supported auth factor types are `PIN`, `PASSWORD`, and `OTP`. Successful login returns a bearer JWT with account and tenant claims. The JWT is then used for tenant resolution and security context.

### Profile and KYC

Accounts can update profile fields and add KYC documents. KYC data includes document type/value, issue and expiry date, primary flag, and image URL. KYC statuses supported by constants are `VERIFIED`, `PENDING`, `REJECTED`, `EXPIRED`, and `PENDING_APPROVAL`.

### Account suspension and resume

Admins can suspend and resume accounts.

Suspension changes account status to `SUSPENDED`, updates related tables, blocks financial and profile-modifying actions, and refreshes wallet cache. Suspended accounts can still log in and view only their own wallet balances.

Resume changes the account status back to `ACTIVE` and restores normal access. Both actions create rows in `account_status_history` with account, previous/new status, action type, performer, performer type, reason, remarks, and timestamp.

## Wallets and Balances

Each account can own multiple wallets by currency and wallet type. The wallet table stores wallet metadata, and `wallet_balance` stores available, frozen, and FIC balances with optimistic versioning.

Important wallet concepts:

- `availableBalance`: spendable wallet value.
- `frozenBalance`: held value.
- `ficBalance`: float/in-clearance style value tracked separately.
- `isLocked`: blocks wallet activity when true.
- `status`: active wallets are required for financial operations.

System account `SYS0001` provides treasury and settlement wallets. Service charge uses `SC`; commission, discount, and cashback funding use `COMMDIS`.

Wallet cache is refreshed after direct balance/status updates so API reads do not return stale state.

## Wallet Restrictions

Wallet restrictions are stored as JSON per wallet and versioned. They can block wallet send/receive behavior by service. Every restriction change is copied into `wallet_restriction_history`, which provides an audit trail with version, action type, changed by, and timestamp.

## Financial Services

PayNest stores a service catalog. Bootstrap seeds:

| Service code | Function |
| --- | --- |
| `U2U` | User-to-user wallet transfer. |
| `MERCHANTPAY` | Subscriber/customer payment to merchant. |
| `CASHIN` | Agent/business assisted cash-in. |
| `CASHOUT` | Agent/business assisted cash-out. |
| `BILLPAY` | Bill payment to biller. |
| `O2C` | Operator to channel transfer. |
| `ACCOUNT_DELETION` | Balance transfer during subscriber deletion. |
| `INTRAWALLET` | Same-account wallet transfer across wallet types/currencies. |

### Common payment behavior

Financial APIs validate parties, identifiers, wallet type, active account status, wallet status, wallet lock state, currency, amount, duplicate transactions, and wallet restrictions. They create:

- A header row in `transactions`.
- Detail rows in `transaction_details`.
- Ledger rows in `wallet_ledger`.
- Balance updates in `wallet_balance`.
- Pricing detail metadata when service charge, commission, discount, or cashback applies.

Transaction details include wallet type, currency, and semantic transaction type such as money paid/received, service charge paid/received, commission, and discount entries.

### U2U

U2U transfers value from a sender to a receiver. Pricing can apply service charge, discount, commission, or cashback depending on active rules and tags. The response can expose total amount and pricing adjustment details.

### Cash-in

Cash-in credits subscriber wallet value through an agent/business flow. Commission rules can reward the participating party, and service charge rules can collect fees where configured.

### Cash-out

Cash-out debits subscriber wallet value and credits/settles the agent/business side depending on the configured flow. Commission and service charge can apply.

### Merchant payment

Merchant payment debits the customer and credits the merchant. The implementation supports service charge and discount rules; commission/cashback can also be selected by the pricing engine when configured. Discounts are funded from the system commission/disbursement wallet and credited to the configured party.

### Bill payment

Bill payment debits the subscriber and credits the biller flow. It also maintains bill payment status records. Cashback rules can create pending cashback payout rows for later disbursement.

### O2C and stock

Operator-to-channel and stock flows support initiating stock movement and approving/rejecting it later through status update APIs. Stock creation and reimbursement follow pending-to-final status workflows.

### Intra-wallet transfer

Intra-wallet transfer moves funds between wallets of the same account. It supports source and target currencies and uses FX rates where needed. Bootstrap includes a configurable bonus-to-main percentage.

## Pricing Engine

The pricing engine supports four rule types:

- `SERVICE_CHARGE`
- `COMMISSION`
- `DISCOUNT`
- `CASHBACK`

Current accepted pricing types are `STATIC` and `CAMPAIGN`. `ALLTAGS` is used as a tag selector for users without active tags or for all-tag rules; it is not currently accepted as a `pricingType` value by the service validator.

### Tags and rule selection

Accounts can be linked to tags. Pricing rules match on:

- Service code.
- Sender tag key.
- Receiver tag key.
- Currency.
- Rule type.
- Active status and validity dates.

If an account has no active tags, the pricing resolver uses `ALLTAGS`. Campaign logic additionally uses `ALL` as a campaign-wide selector.

### Service charge

Service charge is collected from the configured affected party and credited to the `SYS0001` `SC` wallet in the same currency. When multiple matching service charge rules exist, selection favors the lower applicable charge for service charge behavior.

### Commission

Commission debits the `SYS0001` `COMMDIS` wallet and credits the other party's commission/main wallet according to the transaction and available wallet setup. Among multiple matching commission rules, the engine selects the higher commission amount.

### Discount

Discount debits the `SYS0001` `COMMDIS` wallet and credits the configured party's main wallet. Among multiple matching discount rules, the engine selects the higher discount amount.

### Cashback

Cashback is calculated during the original transaction but paid as a separate transaction. A matching cashback rule creates a row in `cashback_payout` with the beneficiary, amount, schedule, due time, status, and selected rule details. Among multiple cashback rules, the engine selects the higher cashback amount.

## Cashback Payout

Cashback payout is separated from the original transaction.

### Payout table

`cashback_payout` records who should be paid, how much, and when:

- Original transaction ID.
- Payout transaction ID after payment.
- Service code.
- Beneficiary account and party.
- Amount and currency.
- Payment schedule.
- Due timestamp `pay_at`.
- Status `PENDING`, `PAID`, or `FAILED`.
- Pricing rule details.
- Failure reason.

### Hourly payout scheduler

`CashbackPayoutScheduler` runs every hour. It iterates over all loaded tenants, sets tenant context, and pays due pending cashback rows.

`CashbackPayoutService` pays cashback by:

1. Debiting `SYS0001` `COMMDIS` wallet for the payout currency.
2. Crediting the beneficiary `BONUS` wallet if it exists and is active.
3. Falling back to beneficiary `MAIN` wallet when bonus wallet does not exist.
4. Creating a separate `CASHBACK` transaction, transaction detail rows, and wallet ledger rows.
5. Updating the payout row to `PAID` with `payout_transaction_id`, or `FAILED` with `failure_reason`.
6. Refreshing wallet cache for the system and beneficiary accounts.

## Transaction Ledger and Details

The transaction model has three major layers:

- `transactions`: transaction header with service code, amount, status, parties, metadata, wallet/currency data, and references.
- `transaction_details`: account-facing detail rows with sequence number, entry type, wallet, currency, balances, status, and semantic `transaction_type`.
- `wallet_ledger`: wallet accounting entries with before/after balances and reference metadata.

`transaction_details.attr_6_name` and `attr_6_value` are used to persist pricing metadata such as service charge, commission, and discount names and amounts. This makes transaction history understandable without recalculating pricing later.

## Transaction History and Receipts

Payment history APIs provide filtered history by account, date range, service/payment type, status, sort order, offset, and limit. Detail APIs return party, balance, wallet, currency, and detail-entry information for a single transaction.

Receipt download renders a transaction receipt as PDF using transaction and party data, with optional language and account context.

## FX Rates

FX rates are stored per tenant. A rate has target currency, USD rate, rate type, provider, validity timestamp, active/version data, and extension fields. Intra-wallet transfer uses FX rates when source and target currencies differ.

## Tags and Segmentation

Tags provide customer/account segmentation for pricing and campaign behavior. Bootstrap creates default categories and base tags for subscribers, merchants, agents, and partners. Admin tag APIs manage:

- Tags.
- Categories.
- Tag types.
- Account/tag links.
- Reverse lookup from tag to accounts.

Pricing relies on active account tags to select applicable rules.

## Notifications and Audit

Bootstrap creates notification templates for transfer success, login alerts, and bill payment failure. The payment system publishes transaction notification events after successful wallet movement and cashback payout.

API audit logging captures request and response metadata into tenant audit tables, including request ID, trace ID, tenant ID, method, endpoint, status code, payload, error message, and timestamp.

## Security and Access Control

Security is enforced through:

- JWT validation.
- Tenant claim validation against the resolved tenant schema.
- Spring Security context.
- Account suspension filter.
- Admin-scoped controller checks for tag and account status operations.

Suspended users can authenticate but cannot perform financial transactions, KYC/profile updates, or other state-changing operations. They can only view their own wallet balance.

## Operational Notes

Important operational behavior:

- Tenant cache is loaded from `public.tenant_registry`.
- Tenant-local timestamps use `TenantTime`.
- Balance updates use wallet locking and ledger/detail persistence to preserve accounting traceability.
- System wallets must exist for each active transaction currency, especially `SYS0001` `SC` for service charge and `SYS0001` `COMMDIS` for commission, discount, and cashback payout.
- Pricing rule status and validity windows should be managed carefully in tests; older active all-tag rules can affect newer expected pricing outcomes.
- Wallet cache should be refreshed after direct DB updates to account status, wallet status, or balances.
