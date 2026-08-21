# PayNest PostgreSQL Onboarding

This guide is for provisioning a fresh PostgreSQL database for a new PayNest site or tenant.

Use the bootstrap scripts for real deployments. The generated files under `docs/postgres` are reference material for review and audit.

## Files

| File | Purpose |
|---|---|
| `scripts/bootstrap-paynest-site.ps1` | One-command wrapper for public plus tenant setup. |
| `scripts/bootstrap-paynest-master-db.ps1` | Creates shared `public` objects, app DB role, grants, and default privileges. |
| `scripts/bootstrap-paynest-db.ps1` | Creates one tenant schema, tenant registry row, tenant tables, indexes, seed/reference data, system wallets, grants, and default privileges. |
| `docs/postgres/paynest-schema-reference.sql` | Schema-only SQL reference generated from `public` and the complete tenant schema `tenant_e2etest`. |
| `docs/postgres/paynest-table-inventory.md` | Full table inventory: columns, defaults, constraints, indexes, and sequences. |
| `docs/postgres/paynest-schema-crosscheck.md` | Code-vs-bootstrap cross-check covering JPA entities, startup schema initializers, SQL resources, and bootstrap-only tables. |

## One Command Setup

Run this from the repository root on the target server:

```powershell
.\scripts\bootstrap-paynest-site.ps1 `
  -DbHost "localhost" `
  -DbPort 5432 `
  -Database "postgres" `
  -DbLoginUser "postgres" `
  -DatabasePwd "postgres" `
  -DbUser "paynest_app" `
  -DbPassword "paynest_app" `
  -TenantId "movii" `
  -TenantName "Movii" `
  -TenantSchema "tenant_movii" `
  -TenantTimeZone "Asia/Kolkata" `
  -DefaultAdminLoginId "superadmin" `
  -DefaultAdminPassword "Admin@123"
```

Change the tenant values for each site. The application resolves the tenant from `public.tenant_registry`, so `tenant_id` and `schema_name` must match the deployment headers/JWT tenant behavior.

## Public Schema

The master bootstrap creates these shared tables:

| Table | Purpose |
|---|---|
| `public.tenant_registry` | Maps tenant ID to tenant schema, name, timezone, and status. |
| `public.system_config` | Shared runtime/system configuration. |
| `public.audit_api_logs` | API request/response audit log. |

It also creates or updates the app role and permissions:

```sql
GRANT USAGE ON SCHEMA public TO paynest_app;
GRANT CREATE ON SCHEMA public TO paynest_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO paynest_app;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO paynest_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO paynest_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO paynest_app;
```

## Tenant Schema

The tenant bootstrap creates 52 tenant tables. This count was cross-checked against JPA entities, startup schema initializers, SQL resource files, and the complete `tenant_e2etest` schema.

The complete table list is:

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
audit_api_log
auth_challenge
bill_payment_status
cashback_payout
categories
city
country_subdivision
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

The tenant script also seeds required reference data, including:

| Seed Area | Examples |
|---|---|
| Tenant registry | `tenant_id`, `tenant_name`, `schema_name`, timezone, active status. |
| Enumerations/config | account type, identifier type, wallet type, KYC status, operation types, languages, system config. |
| Roles/permissions | admin and application permission model. |
| Error catalog | localized API/payment error messages. |
| Service catalog | U2U, cash-in, cash-out, bill pay, merchant pay, O2C, wallet transfer service definitions. |
| Pricing/limits | pricing rules and transaction limit profiles. |
| System account/wallets | `SYS0001` and system posting wallets such as `MAIN`, `BANK`, `SC`, `COMMDIS`, `TAX`, `HOLDING`. |
| Default admin | configurable admin account/login/auth record. |

Tenant permissions:

```sql
GRANT USAGE, CREATE ON SCHEMA tenant_movii TO paynest_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA tenant_movii TO paynest_app;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA tenant_movii TO paynest_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.tenant_registry TO paynest_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA tenant_movii
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO paynest_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA tenant_movii
    GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO paynest_app;
```

Use the actual tenant schema and app role names for a deployment.

## Validation Queries

After running the wrapper, use these checks:

```sql
SELECT table_schema, COUNT(*) AS table_count
FROM information_schema.tables
WHERE table_schema IN ('public', 'tenant_movii')
  AND table_type = 'BASE TABLE'
GROUP BY table_schema
ORDER BY table_schema;
```

Expected:

```text
public        3
tenant_movii 52
```

Check the tenant registry:

```sql
SELECT tenant_id, tenant_name, schema_name, iana_time_zone, status
FROM public.tenant_registry
ORDER BY tenant_id;
```

Check system service-charge wallets:

```sql
SELECT w.wallet_id, w.account_id, trim(w.currency) AS currency, w.wallet_type,
       w.status, w.is_locked, b.available_balance
FROM tenant_movii.wallet w
JOIN tenant_movii.wallet_balance b ON b.wallet_id = w.wallet_id
WHERE w.account_id = 'SYS0001'
  AND w.wallet_type IN ('MAIN', 'BANK', 'SC', 'COMMDIS', 'TAX', 'HOLDING')
ORDER BY trim(w.currency), w.wallet_type;
```

Check app role access:

```sql
SELECT table_schema, privilege_type, COUNT(*) AS privilege_count
FROM information_schema.table_privileges
WHERE grantee = 'paynest_app'
  AND table_schema IN ('public', 'tenant_movii')
GROUP BY table_schema, privilege_type
ORDER BY table_schema, privilege_type;
```

## Notes

- Do not use the current `tenant_movii` schema as a template; it was stale during local debugging. The complete reference tenant is `tenant_e2etest`, and it matches `scripts/bootstrap-paynest-db.ps1`.
- `docs/postgres/paynest-schema-reference.sql` is a review artifact generated from `tenant_e2etest`. It is useful for inspecting raw PostgreSQL `CREATE TABLE`, sequence, constraint, index, and grant statements.
- `docs/postgres/paynest-schema-crosscheck.md` explains which tables are backed by JPA code, runtime initializers, SQL resources, or bootstrap-only reference data.
- For deployment, prefer `scripts/bootstrap-paynest-site.ps1` because it also handles tenant-specific values and required seed data.
