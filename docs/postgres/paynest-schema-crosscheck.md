# PayNest Schema Cross-Check

This cross-check compares the deployable bootstrap scripts against application code.

Sources checked:

- JPA entities under `src/main/java/com/paynest/**/entity`
- Spring startup schema initializers
- SQL resources under `src/main/resources/db`
- Public bootstrap: `scripts/bootstrap-paynest-master-db.ps1`
- Tenant bootstrap: `scripts/bootstrap-paynest-db.ps1`

## Result

No JPA entity table, startup-initializer table, or SQL-resource table is missing from the deployable bootstrap scripts.

## Public Schema

Bootstrap creates 3 public tables.

| Table | Code-backed | Notes |
|---|---:|---|
| `public.audit_api_logs` | Yes | JPA entity: `AuditApiLog`; API audit filter writes here. |
| `public.tenant_registry` | Yes | JPA entity: `TenantRegistry`; tenant resolution depends on it. |
| `public.system_config` | No | Bootstrap/shared configuration table. No current JPA entity found. |

## Tenant Schema

Bootstrap creates 52 tenant tables.

49 tenant tables are backed by JPA entities:

```text
account
account_auth
account_biller_info
account_identifiers
account_merchant_info
account_merchant_mcc
account_notification_endpoint
account_status_history
account_tags
auth_challenge
bill_payment_status
cashback_payout
categories
document_category
document_reference
document_type
document_type_entity
enumerations
error_catalog
fx_rates
kyc_document
notification_outbox
notification_template
otp
passcode
permissions
pricing_rules
qr_payment_intent
recent_recipients
role_permissions
roles
service_catalog
stored_document
supported_languages
tag_types
tags
third_party_response
transaction_details
transaction_limit_profile
transaction_limit_profile_details
transaction_limit_profile_period
transaction_limit_usage
transactions
user_roles
wallet
wallet_balance
wallet_ledger
wallet_restriction
wallet_restriction_history
```

3 tenant tables are bootstrap-only or reference tables:

| Table | Reason Kept |
|---|---|
| `country_subdivision` | Seeded reference data for ISO-style country/region subdivisions. |
| `city` | Seeded reference data for country and subdivision capitals/timezones. |
| `audit_api_log` | Legacy tenant audit table. Current code writes API audit logs to `public.audit_api_logs`, but the tenant bootstrap still creates this table for compatibility. |

## Startup Initializers

These tables are also ensured at application startup by code, and all are included in the tenant bootstrap:

```text
account_notification_endpoint
bill_payment_status
cashback_payout
notification_outbox
notification_template
recent_recipients
service_catalog
transaction_limit_profile
transaction_limit_profile_details
transaction_limit_profile_period
transaction_limit_usage
```

## SQL Resource Tables

These tables are present in SQL resource files, and all are included in the tenant bootstrap:

```text
account_tags
cashback_payout
categories
notification_outbox
passcode
pricing_rules
qr_payment_intent
recent_recipients
tag_types
tags
transaction_limit_profile
transaction_limit_profile_details
transaction_limit_profile_period
transaction_limit_usage
```

## Deployment Notes

- Do not use local `tenant_movii` as the source of truth; it was stale during debugging.
- The complete source of truth for creating a site is `scripts/bootstrap-paynest-site.ps1`, which calls the public and tenant bootstraps in order.
- The generated SQL reference and table inventory were generated from the complete tenant schema `tenant_e2etest`, then cross-checked against code and bootstrap scripts.
- `Category` and `TagType` entities were corrected to remove hardcoded `schema = "tenant_movii"` so they route through the active tenant schema.
