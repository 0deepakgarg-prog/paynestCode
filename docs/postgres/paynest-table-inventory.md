# PayNest PostgreSQL Table Inventory

Generated from the complete tenant schema `tenant_e2etest` and the shared `public` schema, then cross-checked against JPA entities, startup schema initializers, SQL resource files, and bootstrap scripts.
Use `scripts/bootstrap-paynest-site.ps1` or the underlying bootstrap scripts for real onboarding; this file is the detailed table/column/index/constraint inventory.

## Summary

- `public`: 3 tables, 39 columns, 11 indexes, 1 sequences
- `tenant_e2etest`: 52 tables, 676 columns, 119 indexes, 38 sequences

## Sequences

| Schema | Sequence | Type | Start | Increment |
|---|---|---|---:|---:|
| `public` | `audit_api_logs_id_seq` | `bigint` | 1 | 1 |
| `tenant_e2etest` | `account_biller_info_biller_info_id_seq` | `bigint` | 1 | 1 |
| `tenant_e2etest` | `account_identifiers_id_seq` | `bigint` | 1 | 1 |
| `tenant_e2etest` | `account_merchant_info_merchant_info_id_seq` | `bigint` | 1 | 1 |
| `tenant_e2etest` | `account_merchant_mcc_merchant_mcc_id_seq` | `bigint` | 1 | 1 |
| `tenant_e2etest` | `account_notification_endpoint_account_endpoint_id_seq` | `bigint` | 1 | 1 |
| `tenant_e2etest` | `account_status_history_history_id_seq` | `bigint` | 1 | 1 |
| `tenant_e2etest` | `account_tags_id_seq` | `bigint` | 1 | 1 |
| `tenant_e2etest` | `audit_api_log_id_seq` | `bigint` | 1 | 1 |
| `tenant_e2etest` | `cashback_payout_cashback_payout_id_seq` | `bigint` | 1 | 1 |
| `tenant_e2etest` | `categories_category_id_seq` | `bigint` | 1 | 1 |
| `tenant_e2etest` | `city_id_seq` | `bigint` | 1 | 1 |
| `tenant_e2etest` | `country_subdivision_id_seq` | `bigint` | 1 | 1 |
| `tenant_e2etest` | `document_category_category_id_seq` | `bigint` | 1 | 1 |
| `tenant_e2etest` | `document_reference_document_reference_id_seq` | `bigint` | 1 | 1 |
| `tenant_e2etest` | `document_type_document_type_id_seq` | `bigint` | 1 | 1 |
| `tenant_e2etest` | `enumerations_id_seq` | `bigint` | 1 | 1 |
| `tenant_e2etest` | `fx_rates_rate_id_seq` | `bigint` | 1 | 1 |
| `tenant_e2etest` | `kyc_document_document_id_seq` | `bigint` | 1 | 1 |
| `tenant_e2etest` | `notification_outbox_notification_id_seq` | `bigint` | 1 | 1 |
| `tenant_e2etest` | `notification_template_template_id_seq` | `bigint` | 1 | 1 |
| `tenant_e2etest` | `otp_otp_id_seq` | `bigint` | 1 | 1 |
| `tenant_e2etest` | `passcode_passcode_id_seq` | `bigint` | 1 | 1 |
| `tenant_e2etest` | `permissions_permission_id_seq` | `bigint` | 1 | 1 |
| `tenant_e2etest` | `pricing_rules_id_seq` | `bigint` | 1 | 1 |
| `tenant_e2etest` | `role_permissions_id_seq` | `bigint` | 1 | 1 |
| `tenant_e2etest` | `roles_role_id_seq` | `bigint` | 1 | 1 |
| `tenant_e2etest` | `supported_languages_id_seq` | `bigint` | 1 | 1 |
| `tenant_e2etest` | `tag_types_tag_type_id_seq` | `bigint` | 1 | 1 |
| `tenant_e2etest` | `tags_tag_id_seq` | `bigint` | 1 | 1 |
| `tenant_e2etest` | `third_party_response_id_seq` | `bigint` | 1 | 1 |
| `tenant_e2etest` | `transaction_limit_profile_details_limit_details_id_seq` | `bigint` | 1 | 1 |
| `tenant_e2etest` | `transaction_limit_profile_limit_id_seq` | `bigint` | 1 | 1 |
| `tenant_e2etest` | `transaction_limit_profile_period_limit_period_id_seq` | `bigint` | 1 | 1 |
| `tenant_e2etest` | `transaction_limit_usage_usage_id_seq` | `bigint` | 1 | 1 |
| `tenant_e2etest` | `user_roles_id_seq` | `bigint` | 1 | 1 |
| `tenant_e2etest` | `wallet_ledger_ledger_id_seq` | `bigint` | 1 | 1 |
| `tenant_e2etest` | `wallet_restriction_history_history_id_seq` | `bigint` | 1 | 1 |
| `tenant_e2etest` | `wallet_wallet_id_seq` | `bigint` | 10000000000 | 1 |

## Tables

### `public.audit_api_logs`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `id` | `bigint` | NO | `nextval('audit_api_logs_id_seq'::regclass)` |
| 2 | `trace_id` | `character varying(100)` | YES | `` |
| 3 | `tenant_id` | `character varying(50)` | YES | `` |
| 4 | `http_method` | `character varying(10)` | YES | `` |
| 5 | `request_body` | `jsonb` | YES | `` |
| 6 | `response_body` | `jsonb` | YES | `` |
| 7 | `http_status` | `integer` | YES | `` |
| 8 | `processing_time_ms` | `bigint` | YES | `` |
| 9 | `created_at` | `timestamp without time zone` | YES | `CURRENT_TIMESTAMP` |
| 10 | `api_path` | `character varying(255)` | YES | `` |
| 11 | `account_id` | `character varying(50)` | YES | `` |
| 12 | `service_code` | `character varying(50)` | YES | `` |
| 13 | `reference_id` | `character varying(100)` | YES | `` |
| 14 | `transaction_id` | `character varying(50)` | YES | `` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `audit_api_logs_id_not_null` | NOT NULL | `NOT NULL id` |
| `audit_api_logs_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |

Indexes:

| Name | Definition |
|---|---|
| `audit_api_logs_pkey` | `CREATE UNIQUE INDEX audit_api_logs_pkey ON public.audit_api_logs USING btree (id)` |
| `idx_audit_api_logs_reference_id` | `CREATE INDEX idx_audit_api_logs_reference_id ON public.audit_api_logs USING btree (reference_id)` |
| `idx_audit_api_logs_service_code` | `CREATE INDEX idx_audit_api_logs_service_code ON public.audit_api_logs USING btree (service_code)` |
| `idx_audit_api_logs_tenant_path_created` | `CREATE INDEX idx_audit_api_logs_tenant_path_created ON public.audit_api_logs USING btree (tenant_id, api_path, created_at DESC)` |
| `idx_audit_api_logs_trace_id` | `CREATE INDEX idx_audit_api_logs_trace_id ON public.audit_api_logs USING btree (trace_id)` |
| `idx_audit_api_logs_transaction_id` | `CREATE INDEX idx_audit_api_logs_transaction_id ON public.audit_api_logs USING btree (transaction_id)` |

### `public.system_config`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `config_id` | `bigint` | NO | `nextval('system_config_config_id_seq'::regclass)` |
| 2 | `config_key` | `text` | NO | `` |
| 3 | `config_value` | `text` | NO | `` |
| 4 | `config_type` | `text` | NO | `` |
| 5 | `description` | `text` | YES | `` |
| 6 | `is_active` | `boolean` | YES | `true` |
| 7 | `created_at` | `timestamp without time zone` | YES | `now()` |
| 8 | `updated_at` | `timestamp without time zone` | YES | `now()` |
| 9 | `updated_by` | `text` | YES | `` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `system_config_config_id_not_null` | NOT NULL | `NOT NULL config_id` |
| `system_config_config_key_not_null` | NOT NULL | `NOT NULL config_key` |
| `system_config_config_type_not_null` | NOT NULL | `NOT NULL config_type` |
| `system_config_config_value_not_null` | NOT NULL | `NOT NULL config_value` |
| `system_config_pkey` | PRIMARY KEY | `PRIMARY KEY (config_id)` |
| `system_config_config_key_key` | UNIQUE | `UNIQUE (config_key)` |

Indexes:

| Name | Definition |
|---|---|
| `system_config_config_key_key` | `CREATE UNIQUE INDEX system_config_config_key_key ON public.system_config USING btree (config_key)` |
| `system_config_pkey` | `CREATE UNIQUE INDEX system_config_pkey ON public.system_config USING btree (config_id)` |

### `public.tenant_registry`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `tenant_id` | `character varying(50)` | NO | `` |
| 2 | `tenant_name` | `character varying(100)` | YES | `` |
| 3 | `schema_name` | `character varying(100)` | YES | `` |
| 4 | `status` | `character varying(20)` | YES | `` |
| 5 | `created_at` | `timestamp without time zone` | YES | `now()` |
| 6 | `updated_at` | `timestamp without time zone` | YES | `` |
| 7 | `iana_time_zone` | `character varying(100)` | YES | `` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `tenant_registry_tenant_id_not_null` | NOT NULL | `NOT NULL tenant_id` |
| `tenant_registry_pkey` | PRIMARY KEY | `PRIMARY KEY (tenant_id)` |

Indexes:

| Name | Definition |
|---|---|
| `tenant_registry_pkey` | `CREATE UNIQUE INDEX tenant_registry_pkey ON public.tenant_registry USING btree (tenant_id)` |

### `tenant_e2etest.account`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `account_id` | `text` | NO | `` |
| 2 | `account_type` | `character varying(50)` | NO | `` |
| 3 | `account_code` | `character varying(100)` | YES | `` |
| 4 | `first_name` | `character varying(255)` | YES | `` |
| 5 | `last_name` | `character varying(255)` | YES | `` |
| 6 | `mobile_number` | `character varying(50)` | YES | `` |
| 7 | `email` | `character varying(255)` | YES | `` |
| 8 | `address` | `text` | YES | `` |
| 9 | `gender` | `character varying(50)` | YES | `` |
| 10 | `date_of_birth` | `date` | YES | `` |
| 11 | `preferred_lang` | `character varying(20)` | YES | `` |
| 12 | `nationality` | `character varying(100)` | YES | `` |
| 13 | `ssn` | `character varying(100)` | YES | `` |
| 14 | `remarks` | `text` | YES | `` |
| 15 | `attr1` | `text` | YES | `` |
| 16 | `attr2` | `text` | YES | `` |
| 17 | `attr3` | `text` | YES | `` |
| 18 | `attr4` | `text` | YES | `` |
| 19 | `attr5` | `text` | YES | `` |
| 20 | `attr6` | `text` | YES | `` |
| 21 | `attr7` | `text` | YES | `` |
| 22 | `attr8` | `text` | YES | `` |
| 23 | `attr9` | `text` | YES | `` |
| 24 | `attr10` | `text` | YES | `` |
| 25 | `kyc_status` | `character varying(50)` | YES | `` |
| 26 | `status` | `character varying(20)` | YES | `` |
| 27 | `created_at` | `timestamp without time zone` | YES | `CURRENT_TIMESTAMP` |
| 28 | `updated_at` | `timestamp without time zone` | YES | `` |
| 29 | `created_by` | `character varying(255)` | YES | `` |
| 30 | `updated_by` | `character varying(255)` | YES | `` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `account_account_id_not_null` | NOT NULL | `NOT NULL account_id` |
| `account_account_type_not_null` | NOT NULL | `NOT NULL account_type` |
| `account_pkey` | PRIMARY KEY | `PRIMARY KEY (account_id)` |

Indexes:

| Name | Definition |
|---|---|
| `account_pkey` | `CREATE UNIQUE INDEX account_pkey ON tenant_e2etest.account USING btree (account_id)` |
| `uk_account_account_code_active` | `CREATE UNIQUE INDEX uk_account_account_code_active ON tenant_e2etest.account USING btree (account_code) WHERE ((account_code IS NOT NULL) AND ((status)::text = 'ACTIVE'::text))` |
| `uk_account_mobile_number` | `CREATE UNIQUE INDEX uk_account_mobile_number ON tenant_e2etest.account USING btree (mobile_number) WHERE (mobile_number IS NOT NULL)` |

### `tenant_e2etest.account_auth`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `auth_id` | `bigint` | NO | `` |
| 2 | `auth_hash` | `character varying(255)` | YES | `` |
| 3 | `auth_value` | `character varying(255)` | YES | `` |
| 4 | `auth_type` | `character varying(20)` | NO | `'PIN'::character varying` |
| 5 | `status` | `character varying(20)` | NO | `'ACTIVE'::character varying` |
| 6 | `failed_attempts` | `integer` | YES | `0` |
| 7 | `is_first_time_login` | `boolean` | YES | `false` |
| 8 | `last_failed_at` | `timestamp without time zone` | YES | `` |
| 9 | `last_login_at` | `timestamp without time zone` | YES | `` |
| 10 | `last_login_ip` | `character varying(50)` | YES | `` |
| 11 | `password_changed_at` | `timestamp without time zone` | YES | `` |
| 12 | `created_at` | `timestamp without time zone` | YES | `CURRENT_TIMESTAMP` |
| 13 | `updated_at` | `timestamp without time zone` | YES | `` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `account_auth_auth_id_not_null` | NOT NULL | `NOT NULL auth_id` |
| `account_auth_auth_type_not_null` | NOT NULL | `NOT NULL auth_type` |
| `account_auth_status_not_null` | NOT NULL | `NOT NULL status` |
| `account_auth_pkey` | PRIMARY KEY | `PRIMARY KEY (auth_id)` |

Indexes:

| Name | Definition |
|---|---|
| `account_auth_pkey` | `CREATE UNIQUE INDEX account_auth_pkey ON tenant_e2etest.account_auth USING btree (auth_id)` |

### `tenant_e2etest.account_biller_info`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `biller_info_id` | `bigint` | NO | `nextval('tenant_e2etest.account_biller_info_biller_info_id_seq'::regclass)` |
| 2 | `account_id` | `text` | NO | `` |
| 3 | `biller_category` | `character varying(50)` | NO | `` |
| 4 | `biller_code` | `character varying(100)` | NO | `` |
| 5 | `biller_sub_category` | `character varying(100)` | YES | `` |
| 6 | `biller_config` | `jsonb` | YES | `` |
| 7 | `biller_settings` | `jsonb` | YES | `` |
| 8 | `created_on` | `timestamp without time zone` | YES | `CURRENT_TIMESTAMP` |
| 9 | `created_by` | `character varying(100)` | YES | `` |
| 10 | `modified_on` | `timestamp without time zone` | YES | `` |
| 11 | `modified_by` | `character varying(100)` | YES | `` |
| 12 | `field1` | `character varying(250)` | YES | `` |
| 13 | `field2` | `character varying(250)` | YES | `` |
| 14 | `field3` | `character varying(250)` | YES | `` |
| 15 | `field4` | `character varying(250)` | YES | `` |
| 16 | `field5` | `character varying(250)` | YES | `` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `fk_account_biller_info_account` | FOREIGN KEY | `FOREIGN KEY (account_id) REFERENCES tenant_e2etest.account(account_id)` |
| `account_biller_info_account_id_not_null` | NOT NULL | `NOT NULL account_id` |
| `account_biller_info_biller_category_not_null` | NOT NULL | `NOT NULL biller_category` |
| `account_biller_info_biller_code_not_null` | NOT NULL | `NOT NULL biller_code` |
| `account_biller_info_biller_info_id_not_null` | NOT NULL | `NOT NULL biller_info_id` |
| `account_biller_info_pkey` | PRIMARY KEY | `PRIMARY KEY (biller_info_id)` |

Indexes:

| Name | Definition |
|---|---|
| `account_biller_info_pkey` | `CREATE UNIQUE INDEX account_biller_info_pkey ON tenant_e2etest.account_biller_info USING btree (biller_info_id)` |
| `uk_account_biller_info_account` | `CREATE UNIQUE INDEX uk_account_biller_info_account ON tenant_e2etest.account_biller_info USING btree (account_id)` |
| `uk_account_biller_info_biller_code` | `CREATE UNIQUE INDEX uk_account_biller_info_biller_code ON tenant_e2etest.account_biller_info USING btree (lower((biller_code)::text))` |

### `tenant_e2etest.account_identifiers`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `id` | `bigint` | NO | `nextval('tenant_e2etest.account_identifiers_id_seq'::regclass)` |
| 2 | `account_id` | `text` | NO | `` |
| 3 | `auth_id` | `bigint` | NO | `` |
| 4 | `identifier_type` | `character varying(50)` | NO | `` |
| 5 | `identifier_value` | `character varying(255)` | NO | `` |
| 6 | `status` | `character varying(20)` | NO | `` |
| 7 | `created_at` | `timestamp without time zone` | NO | `CURRENT_TIMESTAMP` |
| 8 | `updated_at` | `timestamp without time zone` | YES | `` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `fk_account_identifiers_account` | FOREIGN KEY | `FOREIGN KEY (account_id) REFERENCES tenant_e2etest.account(account_id)` |
| `fk_account_identifiers_auth` | FOREIGN KEY | `FOREIGN KEY (auth_id) REFERENCES tenant_e2etest.account_auth(auth_id)` |
| `account_identifiers_account_id_not_null` | NOT NULL | `NOT NULL account_id` |
| `account_identifiers_auth_id_not_null` | NOT NULL | `NOT NULL auth_id` |
| `account_identifiers_created_at_not_null` | NOT NULL | `NOT NULL created_at` |
| `account_identifiers_id_not_null` | NOT NULL | `NOT NULL id` |
| `account_identifiers_identifier_type_not_null` | NOT NULL | `NOT NULL identifier_type` |
| `account_identifiers_identifier_value_not_null` | NOT NULL | `NOT NULL identifier_value` |
| `account_identifiers_status_not_null` | NOT NULL | `NOT NULL status` |
| `account_identifiers_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |

Indexes:

| Name | Definition |
|---|---|
| `account_identifiers_pkey` | `CREATE UNIQUE INDEX account_identifiers_pkey ON tenant_e2etest.account_identifiers USING btree (id)` |
| `uk_account_identifiers_active` | `CREATE UNIQUE INDEX uk_account_identifiers_active ON tenant_e2etest.account_identifiers USING btree (identifier_type, identifier_value, status)` |

### `tenant_e2etest.account_merchant_info`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `merchant_info_id` | `bigint` | NO | `nextval('tenant_e2etest.account_merchant_info_merchant_info_id_seq'::regclass)` |
| 2 | `account_id` | `text` | NO | `` |
| 3 | `merchant_code` | `character varying(100)` | NO | `` |
| 4 | `merchant_config` | `jsonb` | YES | `` |
| 5 | `created_on` | `timestamp without time zone` | YES | `CURRENT_TIMESTAMP` |
| 6 | `created_by` | `character varying(100)` | YES | `` |
| 7 | `modified_on` | `timestamp without time zone` | YES | `` |
| 8 | `modified_by` | `character varying(100)` | YES | `` |
| 9 | `field1` | `character varying(250)` | YES | `` |
| 10 | `field2` | `character varying(250)` | YES | `` |
| 11 | `field3` | `character varying(250)` | YES | `` |
| 12 | `field4` | `character varying(250)` | YES | `` |
| 13 | `field5` | `character varying(250)` | YES | `` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `fk_account_merchant_info_account` | FOREIGN KEY | `FOREIGN KEY (account_id) REFERENCES tenant_e2etest.account(account_id)` |
| `account_merchant_info_account_id_not_null` | NOT NULL | `NOT NULL account_id` |
| `account_merchant_info_merchant_code_not_null` | NOT NULL | `NOT NULL merchant_code` |
| `account_merchant_info_merchant_info_id_not_null` | NOT NULL | `NOT NULL merchant_info_id` |
| `account_merchant_info_pkey` | PRIMARY KEY | `PRIMARY KEY (merchant_info_id)` |

Indexes:

| Name | Definition |
|---|---|
| `account_merchant_info_pkey` | `CREATE UNIQUE INDEX account_merchant_info_pkey ON tenant_e2etest.account_merchant_info USING btree (merchant_info_id)` |
| `uk_account_merchant_info_account` | `CREATE UNIQUE INDEX uk_account_merchant_info_account ON tenant_e2etest.account_merchant_info USING btree (account_id)` |
| `uk_account_merchant_info_merchant_code` | `CREATE UNIQUE INDEX uk_account_merchant_info_merchant_code ON tenant_e2etest.account_merchant_info USING btree (lower((merchant_code)::text))` |

### `tenant_e2etest.account_merchant_mcc`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `merchant_mcc_id` | `bigint` | NO | `nextval('tenant_e2etest.account_merchant_mcc_merchant_mcc_id_seq'::regclass)` |
| 2 | `merchant_info_id` | `bigint` | NO | `` |
| 3 | `mcc_code` | `character varying(4)` | NO | `` |
| 4 | `created_on` | `timestamp without time zone` | YES | `CURRENT_TIMESTAMP` |
| 5 | `created_by` | `character varying(100)` | YES | `` |
| 6 | `modified_on` | `timestamp without time zone` | YES | `` |
| 7 | `modified_by` | `character varying(100)` | YES | `` |
| 8 | `field1` | `character varying(250)` | YES | `` |
| 9 | `field2` | `character varying(250)` | YES | `` |
| 10 | `field3` | `character varying(250)` | YES | `` |
| 11 | `field4` | `character varying(250)` | YES | `` |
| 12 | `field5` | `character varying(250)` | YES | `` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `fk_account_merchant_mcc_info` | FOREIGN KEY | `FOREIGN KEY (merchant_info_id) REFERENCES tenant_e2etest.account_merchant_info(merchant_info_id)` |
| `account_merchant_mcc_mcc_code_not_null` | NOT NULL | `NOT NULL mcc_code` |
| `account_merchant_mcc_merchant_info_id_not_null` | NOT NULL | `NOT NULL merchant_info_id` |
| `account_merchant_mcc_merchant_mcc_id_not_null` | NOT NULL | `NOT NULL merchant_mcc_id` |
| `account_merchant_mcc_pkey` | PRIMARY KEY | `PRIMARY KEY (merchant_mcc_id)` |

Indexes:

| Name | Definition |
|---|---|
| `account_merchant_mcc_pkey` | `CREATE UNIQUE INDEX account_merchant_mcc_pkey ON tenant_e2etest.account_merchant_mcc USING btree (merchant_mcc_id)` |
| `idx_account_merchant_mcc_code` | `CREATE INDEX idx_account_merchant_mcc_code ON tenant_e2etest.account_merchant_mcc USING btree (mcc_code)` |
| `uk_account_merchant_mcc_code` | `CREATE UNIQUE INDEX uk_account_merchant_mcc_code ON tenant_e2etest.account_merchant_mcc USING btree (merchant_info_id, mcc_code)` |

### `tenant_e2etest.account_notification_endpoint`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `account_endpoint_id` | `bigint` | NO | `nextval('tenant_e2etest.account_notification_endpoint_account_endpoint_id_seq'::regclass)` |
| 2 | `account_id` | `character varying(100)` | NO | `` |
| 3 | `endpoint_type` | `character varying(50)` | NO | `` |
| 4 | `endpoint_value` | `character varying(2000)` | NO | `` |
| 5 | `is_primary` | `boolean` | YES | `false` |
| 6 | `status` | `character varying(30)` | YES | `'ACTIVE'::character varying` |
| 7 | `created_on` | `timestamp without time zone` | YES | `now()` |
| 8 | `updated_at` | `timestamp without time zone` | YES | `now()` |
| 9 | `field1` | `character varying(250)` | YES | `` |
| 10 | `field2` | `character varying(250)` | YES | `` |
| 11 | `field3` | `character varying(250)` | YES | `` |
| 12 | `field4` | `character varying(250)` | YES | `` |
| 13 | `field5` | `character varying(250)` | YES | `` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `account_notification_endpoint_account_endpoint_id_not_null` | NOT NULL | `NOT NULL account_endpoint_id` |
| `account_notification_endpoint_account_id_not_null` | NOT NULL | `NOT NULL account_id` |
| `account_notification_endpoint_endpoint_type_not_null` | NOT NULL | `NOT NULL endpoint_type` |
| `account_notification_endpoint_endpoint_value_not_null` | NOT NULL | `NOT NULL endpoint_value` |
| `account_notification_endpoint_pkey` | PRIMARY KEY | `PRIMARY KEY (account_endpoint_id)` |

Indexes:

| Name | Definition |
|---|---|
| `account_notification_endpoint_pkey` | `CREATE UNIQUE INDEX account_notification_endpoint_pkey ON tenant_e2etest.account_notification_endpoint USING btree (account_endpoint_id)` |
| `idx_account_notification_endpoint_account_type` | `CREATE INDEX idx_account_notification_endpoint_account_type ON tenant_e2etest.account_notification_endpoint USING btree (account_id, endpoint_type)` |
| `uq_account_notification_endpoint_primary` | `CREATE UNIQUE INDEX uq_account_notification_endpoint_primary ON tenant_e2etest.account_notification_endpoint USING btree (account_id, endpoint_type) WHERE (is_primary = true)` |

### `tenant_e2etest.account_status_history`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `history_id` | `bigint` | NO | `nextval('tenant_e2etest.account_status_history_history_id_seq'::regclass)` |
| 2 | `account_id` | `character varying(100)` | NO | `` |
| 3 | `account_type` | `character varying(50)` | YES | `` |
| 4 | `action_type` | `character varying(50)` | NO | `` |
| 5 | `previous_status` | `character varying(50)` | YES | `` |
| 6 | `new_status` | `character varying(50)` | NO | `` |
| 7 | `performed_by` | `character varying(100)` | NO | `` |
| 8 | `performed_by_type` | `character varying(50)` | YES | `` |
| 9 | `reason` | `character varying(500)` | YES | `` |
| 10 | `remarks` | `character varying(1000)` | YES | `` |
| 11 | `performed_at` | `timestamp without time zone` | NO | `CURRENT_TIMESTAMP` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `account_status_history_account_id_not_null` | NOT NULL | `NOT NULL account_id` |
| `account_status_history_action_type_not_null` | NOT NULL | `NOT NULL action_type` |
| `account_status_history_history_id_not_null` | NOT NULL | `NOT NULL history_id` |
| `account_status_history_new_status_not_null` | NOT NULL | `NOT NULL new_status` |
| `account_status_history_performed_at_not_null` | NOT NULL | `NOT NULL performed_at` |
| `account_status_history_performed_by_not_null` | NOT NULL | `NOT NULL performed_by` |
| `account_status_history_pkey` | PRIMARY KEY | `PRIMARY KEY (history_id)` |

Indexes:

| Name | Definition |
|---|---|
| `account_status_history_pkey` | `CREATE UNIQUE INDEX account_status_history_pkey ON tenant_e2etest.account_status_history USING btree (history_id)` |
| `idx_account_status_history_account` | `CREATE INDEX idx_account_status_history_account ON tenant_e2etest.account_status_history USING btree (account_id, performed_at DESC)` |

### `tenant_e2etest.account_tags`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `id` | `bigint` | NO | `nextval('tenant_e2etest.account_tags_id_seq'::regclass)` |
| 2 | `account_id` | `text` | NO | `` |
| 3 | `tag_id` | `bigint` | NO | `` |
| 4 | `status` | `character varying(20)` | YES | `'ACTIVE'::character varying` |
| 5 | `created_at` | `timestamp without time zone` | YES | `CURRENT_TIMESTAMP` |
| 6 | `created_by` | `character varying(50)` | YES | `` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `fk_account_tags_account` | FOREIGN KEY | `FOREIGN KEY (account_id) REFERENCES tenant_e2etest.account(account_id)` |
| `fk_account_tags_tag` | FOREIGN KEY | `FOREIGN KEY (tag_id) REFERENCES tenant_e2etest.tags(tag_id)` |
| `account_tags_account_id_not_null` | NOT NULL | `NOT NULL account_id` |
| `account_tags_id_not_null` | NOT NULL | `NOT NULL id` |
| `account_tags_tag_id_not_null` | NOT NULL | `NOT NULL tag_id` |
| `account_tags_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |

Indexes:

| Name | Definition |
|---|---|
| `account_tags_pkey` | `CREATE UNIQUE INDEX account_tags_pkey ON tenant_e2etest.account_tags USING btree (id)` |
| `uk_account_tags_account_tag` | `CREATE UNIQUE INDEX uk_account_tags_account_tag ON tenant_e2etest.account_tags USING btree (account_id, tag_id)` |

### `tenant_e2etest.audit_api_log`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `id` | `bigint` | NO | `nextval('tenant_e2etest.audit_api_log_id_seq'::regclass)` |
| 2 | `request_id` | `character varying(255)` | YES | `` |
| 3 | `trace_id` | `character varying(255)` | YES | `` |
| 4 | `tenant_id` | `character varying(255)` | YES | `` |
| 5 | `http_method` | `character varying(20)` | YES | `` |
| 6 | `endpoint` | `text` | YES | `` |
| 7 | `status_code` | `integer` | YES | `` |
| 8 | `request_payload` | `text` | YES | `` |
| 9 | `response_payload` | `text` | YES | `` |
| 10 | `error_message` | `text` | YES | `` |
| 11 | `created_at` | `timestamp without time zone` | YES | `CURRENT_TIMESTAMP` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `audit_api_log_id_not_null` | NOT NULL | `NOT NULL id` |
| `audit_api_log_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |

Indexes:

| Name | Definition |
|---|---|
| `audit_api_log_pkey` | `CREATE UNIQUE INDEX audit_api_log_pkey ON tenant_e2etest.audit_api_log USING btree (id)` |

### `tenant_e2etest.auth_challenge`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `challenge_id` | `uuid` | NO | `` |
| 2 | `account_id` | `text` | YES | `` |
| 3 | `challenge_value` | `text` | NO | `` |
| 4 | `challenge_type` | `character varying(30)` | NO | `` |
| 5 | `issued_at` | `timestamp without time zone` | NO | `CURRENT_TIMESTAMP` |
| 6 | `expires_at` | `timestamp without time zone` | NO | `` |
| 7 | `used` | `boolean` | YES | `false` |
| 8 | `used_at` | `timestamp without time zone` | YES | `` |
| 9 | `ip_address` | `character varying(50)` | YES | `` |
| 10 | `status` | `character varying(20)` | YES | `'ACTIVE'::character varying` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `fk_auth_challenge_account` | FOREIGN KEY | `FOREIGN KEY (account_id) REFERENCES tenant_e2etest.account(account_id)` |
| `auth_challenge_challenge_id_not_null` | NOT NULL | `NOT NULL challenge_id` |
| `auth_challenge_challenge_type_not_null` | NOT NULL | `NOT NULL challenge_type` |
| `auth_challenge_challenge_value_not_null` | NOT NULL | `NOT NULL challenge_value` |
| `auth_challenge_expires_at_not_null` | NOT NULL | `NOT NULL expires_at` |
| `auth_challenge_issued_at_not_null` | NOT NULL | `NOT NULL issued_at` |
| `auth_challenge_pkey` | PRIMARY KEY | `PRIMARY KEY (challenge_id)` |

Indexes:

| Name | Definition |
|---|---|
| `auth_challenge_pkey` | `CREATE UNIQUE INDEX auth_challenge_pkey ON tenant_e2etest.auth_challenge USING btree (challenge_id)` |

### `tenant_e2etest.bill_payment_status`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `transaction_id` | `character varying(255)` | NO | `` |
| 2 | `status` | `character varying(50)` | NO | `` |
| 3 | `subscriber_account_id` | `character varying(255)` | NO | `` |
| 4 | `biller_account_id` | `character varying(255)` | NO | `` |
| 5 | `trace_id` | `character varying(255)` | NO | `` |
| 6 | `comments` | `text` | YES | `` |
| 7 | `additional_info` | `text` | YES | `` |
| 8 | `rollback_transaction_id` | `character varying(255)` | YES | `` |
| 9 | `settled_by` | `character varying(255)` | YES | `` |
| 10 | `settled_on` | `timestamp without time zone` | YES | `` |
| 11 | `created_on` | `timestamp without time zone` | NO | `` |
| 12 | `modified_on` | `timestamp without time zone` | NO | `` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `bill_payment_status_biller_account_id_not_null` | NOT NULL | `NOT NULL biller_account_id` |
| `bill_payment_status_created_on_not_null` | NOT NULL | `NOT NULL created_on` |
| `bill_payment_status_modified_on_not_null` | NOT NULL | `NOT NULL modified_on` |
| `bill_payment_status_status_not_null` | NOT NULL | `NOT NULL status` |
| `bill_payment_status_subscriber_account_id_not_null` | NOT NULL | `NOT NULL subscriber_account_id` |
| `bill_payment_status_trace_id_not_null` | NOT NULL | `NOT NULL trace_id` |
| `bill_payment_status_transaction_id_not_null` | NOT NULL | `NOT NULL transaction_id` |
| `bill_payment_status_pkey` | PRIMARY KEY | `PRIMARY KEY (transaction_id)` |

Indexes:

| Name | Definition |
|---|---|
| `bill_payment_status_pkey` | `CREATE UNIQUE INDEX bill_payment_status_pkey ON tenant_e2etest.bill_payment_status USING btree (transaction_id)` |

### `tenant_e2etest.cashback_payout`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `cashback_payout_id` | `bigint` | NO | `nextval('tenant_e2etest.cashback_payout_cashback_payout_id_seq'::regclass)` |
| 2 | `original_transaction_id` | `character varying(30)` | NO | `` |
| 3 | `payout_transaction_id` | `character varying(30)` | YES | `` |
| 4 | `service_code` | `character varying(15)` | NO | `` |
| 5 | `beneficiary_account_id` | `character varying(30)` | NO | `` |
| 6 | `beneficiary_party` | `character varying(20)` | YES | `` |
| 7 | `amount` | `numeric(19,4)` | NO | `` |
| 8 | `currency` | `character varying(10)` | NO | `` |
| 9 | `payment_schedule` | `character varying(30)` | NO | `` |
| 10 | `pay_at` | `timestamp without time zone` | NO | `` |
| 11 | `status` | `character varying(20)` | NO | `` |
| 12 | `pricing_rule_details` | `character varying(4000)` | YES | `` |
| 13 | `failure_reason` | `character varying(300)` | YES | `` |
| 14 | `created_on` | `timestamp without time zone` | NO | `` |
| 15 | `modified_on` | `timestamp without time zone` | NO | `` |
| 16 | `version` | `bigint` | YES | `` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `cashback_payout_amount_not_null` | NOT NULL | `NOT NULL amount` |
| `cashback_payout_beneficiary_account_id_not_null` | NOT NULL | `NOT NULL beneficiary_account_id` |
| `cashback_payout_cashback_payout_id_not_null` | NOT NULL | `NOT NULL cashback_payout_id` |
| `cashback_payout_created_on_not_null` | NOT NULL | `NOT NULL created_on` |
| `cashback_payout_currency_not_null` | NOT NULL | `NOT NULL currency` |
| `cashback_payout_modified_on_not_null` | NOT NULL | `NOT NULL modified_on` |
| `cashback_payout_original_transaction_id_not_null` | NOT NULL | `NOT NULL original_transaction_id` |
| `cashback_payout_pay_at_not_null` | NOT NULL | `NOT NULL pay_at` |
| `cashback_payout_payment_schedule_not_null` | NOT NULL | `NOT NULL payment_schedule` |
| `cashback_payout_service_code_not_null` | NOT NULL | `NOT NULL service_code` |
| `cashback_payout_status_not_null` | NOT NULL | `NOT NULL status` |
| `cashback_payout_pkey` | PRIMARY KEY | `PRIMARY KEY (cashback_payout_id)` |

Indexes:

| Name | Definition |
|---|---|
| `cashback_payout_pkey` | `CREATE UNIQUE INDEX cashback_payout_pkey ON tenant_e2etest.cashback_payout USING btree (cashback_payout_id)` |
| `idx_cashback_payout_due` | `CREATE INDEX idx_cashback_payout_due ON tenant_e2etest.cashback_payout USING btree (status, pay_at)` |

### `tenant_e2etest.categories`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `category_id` | `bigint` | NO | `nextval('tenant_e2etest.categories_category_id_seq'::regclass)` |
| 2 | `category_code` | `character varying(50)` | NO | `` |
| 3 | `category_name` | `character varying(100)` | NO | `` |
| 4 | `description` | `text` | YES | `` |
| 5 | `status` | `text` | YES | `` |
| 6 | `created_at` | `timestamp without time zone` | YES | `CURRENT_TIMESTAMP` |
| 7 | `updated_at` | `timestamp without time zone` | YES | `CURRENT_TIMESTAMP` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `categories_category_code_not_null` | NOT NULL | `NOT NULL category_code` |
| `categories_category_id_not_null` | NOT NULL | `NOT NULL category_id` |
| `categories_category_name_not_null` | NOT NULL | `NOT NULL category_name` |
| `categories_pkey` | PRIMARY KEY | `PRIMARY KEY (category_id)` |
| `categories_category_code_key` | UNIQUE | `UNIQUE (category_code)` |

Indexes:

| Name | Definition |
|---|---|
| `categories_category_code_key` | `CREATE UNIQUE INDEX categories_category_code_key ON tenant_e2etest.categories USING btree (category_code)` |
| `categories_pkey` | `CREATE UNIQUE INDEX categories_pkey ON tenant_e2etest.categories USING btree (category_id)` |

### `tenant_e2etest.city`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `id` | `bigint` | NO | `nextval('tenant_e2etest.city_id_seq'::regclass)` |
| 2 | `country_id` | `bigint` | NO | `` |
| 3 | `subdivision_id` | `bigint` | YES | `` |
| 4 | `name` | `character varying(150)` | NO | `` |
| 5 | `code` | `character varying(100)` | YES | `` |
| 6 | `latitude` | `numeric(10,6)` | YES | `` |
| 7 | `longitude` | `numeric(10,6)` | YES | `` |
| 8 | `timezone` | `character varying(100)` | YES | `` |
| 9 | `status` | `character varying(20)` | NO | `'ACTIVE'::character varying` |
| 10 | `created_at` | `timestamp without time zone` | YES | `CURRENT_TIMESTAMP` |
| 11 | `updated_at` | `timestamp without time zone` | YES | `` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `fk_city_country` | FOREIGN KEY | `FOREIGN KEY (country_id) REFERENCES tenant_e2etest.enumerations(id)` |
| `fk_city_subdivision` | FOREIGN KEY | `FOREIGN KEY (subdivision_id) REFERENCES tenant_e2etest.country_subdivision(id)` |
| `city_country_id_not_null` | NOT NULL | `NOT NULL country_id` |
| `city_id_not_null` | NOT NULL | `NOT NULL id` |
| `city_name_not_null` | NOT NULL | `NOT NULL name` |
| `city_status_not_null` | NOT NULL | `NOT NULL status` |
| `city_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |

Indexes:

| Name | Definition |
|---|---|
| `city_pkey` | `CREATE UNIQUE INDEX city_pkey ON tenant_e2etest.city USING btree (id)` |
| `idx_city_country` | `CREATE INDEX idx_city_country ON tenant_e2etest.city USING btree (country_id)` |
| `idx_city_subdivision` | `CREATE INDEX idx_city_subdivision ON tenant_e2etest.city USING btree (subdivision_id)` |
| `uk_city_country_subdivision_code` | `CREATE UNIQUE INDEX uk_city_country_subdivision_code ON tenant_e2etest.city USING btree (country_id, COALESCE(subdivision_id, (0)::bigint), code) WHERE (code IS NOT NULL)` |

### `tenant_e2etest.country_subdivision`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `id` | `bigint` | NO | `nextval('tenant_e2etest.country_subdivision_id_seq'::regclass)` |
| 2 | `country_id` | `bigint` | NO | `` |
| 3 | `code` | `character varying(50)` | NO | `` |
| 4 | `name` | `character varying(150)` | NO | `` |
| 5 | `type` | `character varying(50)` | NO | `` |
| 6 | `parent_id` | `bigint` | YES | `` |
| 7 | `status` | `character varying(20)` | NO | `'ACTIVE'::character varying` |
| 8 | `created_at` | `timestamp without time zone` | YES | `CURRENT_TIMESTAMP` |
| 9 | `updated_at` | `timestamp without time zone` | YES | `` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `fk_country_subdivision_country` | FOREIGN KEY | `FOREIGN KEY (country_id) REFERENCES tenant_e2etest.enumerations(id)` |
| `fk_country_subdivision_parent` | FOREIGN KEY | `FOREIGN KEY (parent_id) REFERENCES tenant_e2etest.country_subdivision(id)` |
| `country_subdivision_code_not_null` | NOT NULL | `NOT NULL code` |
| `country_subdivision_country_id_not_null` | NOT NULL | `NOT NULL country_id` |
| `country_subdivision_id_not_null` | NOT NULL | `NOT NULL id` |
| `country_subdivision_name_not_null` | NOT NULL | `NOT NULL name` |
| `country_subdivision_status_not_null` | NOT NULL | `NOT NULL status` |
| `country_subdivision_type_not_null` | NOT NULL | `NOT NULL type` |
| `country_subdivision_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |

Indexes:

| Name | Definition |
|---|---|
| `country_subdivision_pkey` | `CREATE UNIQUE INDEX country_subdivision_pkey ON tenant_e2etest.country_subdivision USING btree (id)` |
| `idx_country_subdivision_country` | `CREATE INDEX idx_country_subdivision_country ON tenant_e2etest.country_subdivision USING btree (country_id)` |
| `uk_country_subdivision_country_code` | `CREATE UNIQUE INDEX uk_country_subdivision_country_code ON tenant_e2etest.country_subdivision USING btree (country_id, code)` |

### `tenant_e2etest.document_category`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `category_id` | `bigint` | NO | `nextval('tenant_e2etest.document_category_category_id_seq'::regclass)` |
| 2 | `category_code` | `character varying(50)` | NO | `` |
| 3 | `category_name` | `character varying(100)` | NO | `` |
| 4 | `description` | `character varying(255)` | YES | `` |
| 5 | `is_active` | `boolean` | NO | `true` |
| 6 | `created_at` | `timestamp without time zone` | NO | `CURRENT_TIMESTAMP` |
| 7 | `updated_at` | `timestamp without time zone` | NO | `CURRENT_TIMESTAMP` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `document_category_category_code_not_null` | NOT NULL | `NOT NULL category_code` |
| `document_category_category_id_not_null` | NOT NULL | `NOT NULL category_id` |
| `document_category_category_name_not_null` | NOT NULL | `NOT NULL category_name` |
| `document_category_created_at_not_null` | NOT NULL | `NOT NULL created_at` |
| `document_category_is_active_not_null` | NOT NULL | `NOT NULL is_active` |
| `document_category_updated_at_not_null` | NOT NULL | `NOT NULL updated_at` |
| `document_category_pkey` | PRIMARY KEY | `PRIMARY KEY (category_id)` |
| `document_category_category_code_key` | UNIQUE | `UNIQUE (category_code)` |

Indexes:

| Name | Definition |
|---|---|
| `document_category_category_code_key` | `CREATE UNIQUE INDEX document_category_category_code_key ON tenant_e2etest.document_category USING btree (category_code)` |
| `document_category_pkey` | `CREATE UNIQUE INDEX document_category_pkey ON tenant_e2etest.document_category USING btree (category_id)` |

### `tenant_e2etest.document_reference`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `document_reference_id` | `bigint` | NO | `nextval('tenant_e2etest.document_reference_document_reference_id_seq'::regclass)` |
| 2 | `document_id` | `uuid` | NO | `` |
| 3 | `entity_type` | `character varying(30)` | NO | `` |
| 4 | `entity_id` | `character varying(100)` | NO | `` |
| 5 | `reference_role` | `character varying(30)` | NO | `'OWNER'::character varying` |
| 6 | `is_primary` | `boolean` | NO | `false` |
| 7 | `is_active` | `boolean` | NO | `true` |
| 8 | `created_at` | `timestamp without time zone` | NO | `CURRENT_TIMESTAMP` |
| 9 | `updated_at` | `timestamp without time zone` | NO | `CURRENT_TIMESTAMP` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `chk_document_reference_entity` | CHECK | `CHECK (entity_type::text = ANY (ARRAY['CUSTOMER'::character varying, 'MERCHANT'::character varying, 'AGENT'::character varying, 'TRANSACTION'::character varying]::text[]))` |
| `chk_document_reference_role` | CHECK | `CHECK (reference_role::text = ANY (ARRAY['OWNER'::character varying, 'SUBJECT'::character varying, 'ATTACHMENT'::character varying, 'AUTHORIZED_VIEWER'::character varying]::text[]))` |
| `fk_document_reference_document` | FOREIGN KEY | `FOREIGN KEY (document_id) REFERENCES tenant_e2etest.stored_document(document_id)` |
| `document_reference_created_at_not_null` | NOT NULL | `NOT NULL created_at` |
| `document_reference_document_id_not_null` | NOT NULL | `NOT NULL document_id` |
| `document_reference_document_reference_id_not_null` | NOT NULL | `NOT NULL document_reference_id` |
| `document_reference_entity_id_not_null` | NOT NULL | `NOT NULL entity_id` |
| `document_reference_entity_type_not_null` | NOT NULL | `NOT NULL entity_type` |
| `document_reference_is_active_not_null` | NOT NULL | `NOT NULL is_active` |
| `document_reference_is_primary_not_null` | NOT NULL | `NOT NULL is_primary` |
| `document_reference_reference_role_not_null` | NOT NULL | `NOT NULL reference_role` |
| `document_reference_updated_at_not_null` | NOT NULL | `NOT NULL updated_at` |
| `document_reference_pkey` | PRIMARY KEY | `PRIMARY KEY (document_reference_id)` |
| `uq_document_reference` | UNIQUE | `UNIQUE (document_id, entity_type, entity_id, reference_role)` |

Indexes:

| Name | Definition |
|---|---|
| `document_reference_pkey` | `CREATE UNIQUE INDEX document_reference_pkey ON tenant_e2etest.document_reference USING btree (document_reference_id)` |
| `idx_document_reference_document` | `CREATE INDEX idx_document_reference_document ON tenant_e2etest.document_reference USING btree (document_id)` |
| `idx_document_reference_entity` | `CREATE INDEX idx_document_reference_entity ON tenant_e2etest.document_reference USING btree (entity_type, entity_id, is_active)` |
| `uq_document_reference` | `CREATE UNIQUE INDEX uq_document_reference ON tenant_e2etest.document_reference USING btree (document_id, entity_type, entity_id, reference_role)` |

### `tenant_e2etest.document_type`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `document_type_id` | `bigint` | NO | `nextval('tenant_e2etest.document_type_document_type_id_seq'::regclass)` |
| 2 | `category_id` | `bigint` | NO | `` |
| 3 | `type_code` | `character varying(75)` | NO | `` |
| 4 | `type_name` | `character varying(150)` | NO | `` |
| 5 | `description` | `character varying(255)` | YES | `` |
| 6 | `multiple_allowed` | `boolean` | NO | `true` |
| 7 | `verification_required` | `boolean` | NO | `false` |
| 8 | `is_active` | `boolean` | NO | `true` |
| 9 | `created_at` | `timestamp without time zone` | NO | `CURRENT_TIMESTAMP` |
| 10 | `updated_at` | `timestamp without time zone` | NO | `CURRENT_TIMESTAMP` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `fk_document_type_category` | FOREIGN KEY | `FOREIGN KEY (category_id) REFERENCES tenant_e2etest.document_category(category_id)` |
| `document_type_category_id_not_null` | NOT NULL | `NOT NULL category_id` |
| `document_type_created_at_not_null` | NOT NULL | `NOT NULL created_at` |
| `document_type_document_type_id_not_null` | NOT NULL | `NOT NULL document_type_id` |
| `document_type_is_active_not_null` | NOT NULL | `NOT NULL is_active` |
| `document_type_multiple_allowed_not_null` | NOT NULL | `NOT NULL multiple_allowed` |
| `document_type_type_code_not_null` | NOT NULL | `NOT NULL type_code` |
| `document_type_type_name_not_null` | NOT NULL | `NOT NULL type_name` |
| `document_type_updated_at_not_null` | NOT NULL | `NOT NULL updated_at` |
| `document_type_verification_required_not_null` | NOT NULL | `NOT NULL verification_required` |
| `document_type_pkey` | PRIMARY KEY | `PRIMARY KEY (document_type_id)` |
| `document_type_type_code_key` | UNIQUE | `UNIQUE (type_code)` |

Indexes:

| Name | Definition |
|---|---|
| `document_type_pkey` | `CREATE UNIQUE INDEX document_type_pkey ON tenant_e2etest.document_type USING btree (document_type_id)` |
| `document_type_type_code_key` | `CREATE UNIQUE INDEX document_type_type_code_key ON tenant_e2etest.document_type USING btree (type_code)` |
| `idx_document_type_category` | `CREATE INDEX idx_document_type_category ON tenant_e2etest.document_type USING btree (category_id)` |

### `tenant_e2etest.document_type_entity`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `document_type_id` | `bigint` | NO | `` |
| 2 | `entity_type` | `character varying(30)` | NO | `` |
| 3 | `created_at` | `timestamp without time zone` | NO | `CURRENT_TIMESTAMP` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `chk_document_type_entity` | CHECK | `CHECK (entity_type::text = ANY (ARRAY['CUSTOMER'::character varying, 'MERCHANT'::character varying, 'AGENT'::character varying, 'TRANSACTION'::character varying]::text[]))` |
| `fk_document_type_entity_type` | FOREIGN KEY | `FOREIGN KEY (document_type_id) REFERENCES tenant_e2etest.document_type(document_type_id)` |
| `document_type_entity_created_at_not_null` | NOT NULL | `NOT NULL created_at` |
| `document_type_entity_document_type_id_not_null` | NOT NULL | `NOT NULL document_type_id` |
| `document_type_entity_entity_type_not_null` | NOT NULL | `NOT NULL entity_type` |
| `document_type_entity_pkey` | PRIMARY KEY | `PRIMARY KEY (document_type_id, entity_type)` |

Indexes:

| Name | Definition |
|---|---|
| `document_type_entity_pkey` | `CREATE UNIQUE INDEX document_type_entity_pkey ON tenant_e2etest.document_type_entity USING btree (document_type_id, entity_type)` |

### `tenant_e2etest.enumerations`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `id` | `bigint` | NO | `nextval('tenant_e2etest.enumerations_id_seq'::regclass)` |
| 2 | `enum_type` | `character varying(50)` | NO | `` |
| 3 | `enum_code` | `character varying(50)` | NO | `` |
| 4 | `enum_value` | `character varying(100)` | NO | `` |
| 5 | `parent_enum_id` | `bigint` | YES | `` |
| 6 | `description` | `character varying(255)` | YES | `` |
| 7 | `display_order` | `integer` | YES | `0` |
| 8 | `is_active` | `boolean` | YES | `true` |
| 9 | `is_system` | `boolean` | YES | `true` |
| 10 | `created_at` | `timestamp without time zone` | YES | `CURRENT_TIMESTAMP` |
| 11 | `updated_at` | `timestamp without time zone` | YES | `` |
| 12 | `field1` | `character varying(250)` | YES | `` |
| 13 | `field2` | `character varying(250)` | YES | `` |
| 14 | `field3` | `character varying(250)` | YES | `` |
| 15 | `field4` | `character varying(250)` | YES | `` |
| 16 | `field5` | `character varying(250)` | YES | `` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `fk_enumerations_parent_enum` | FOREIGN KEY | `FOREIGN KEY (parent_enum_id) REFERENCES tenant_e2etest.enumerations(id)` |
| `enumerations_enum_code_not_null` | NOT NULL | `NOT NULL enum_code` |
| `enumerations_enum_type_not_null` | NOT NULL | `NOT NULL enum_type` |
| `enumerations_enum_value_not_null` | NOT NULL | `NOT NULL enum_value` |
| `enumerations_id_not_null` | NOT NULL | `NOT NULL id` |
| `enumerations_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uk_enumerations_type_code` | UNIQUE | `UNIQUE (enum_type, enum_code)` |

Indexes:

| Name | Definition |
|---|---|
| `enumerations_pkey` | `CREATE UNIQUE INDEX enumerations_pkey ON tenant_e2etest.enumerations USING btree (id)` |
| `uk_enumerations_type_code` | `CREATE UNIQUE INDEX uk_enumerations_type_code ON tenant_e2etest.enumerations USING btree (enum_type, enum_code)` |

### `tenant_e2etest.error_catalog`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `id` | `bigint` | NO | `` |
| 2 | `error_code` | `character varying(100)` | NO | `` |
| 3 | `language_code` | `character varying(10)` | NO | `` |
| 4 | `message_template` | `text` | NO | `` |
| 5 | `http_status` | `integer` | NO | `400` |
| 6 | `category` | `character varying(30)` | YES | `` |
| 7 | `module` | `character varying(30)` | YES | `` |
| 8 | `is_active` | `boolean` | NO | `true` |
| 9 | `created_at` | `timestamp without time zone` | NO | `CURRENT_TIMESTAMP` |
| 10 | `updated_at` | `timestamp without time zone` | NO | `CURRENT_TIMESTAMP` |
| 11 | `updated_by` | `text` | YES | `` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `error_catalog_http_status_check` | CHECK | `CHECK (http_status >= 100 AND http_status <= 599)` |
| `error_catalog_created_at_not_null` | NOT NULL | `NOT NULL created_at` |
| `error_catalog_error_code_not_null` | NOT NULL | `NOT NULL error_code` |
| `error_catalog_http_status_not_null` | NOT NULL | `NOT NULL http_status` |
| `error_catalog_id_not_null` | NOT NULL | `NOT NULL id` |
| `error_catalog_is_active_not_null` | NOT NULL | `NOT NULL is_active` |
| `error_catalog_language_code_not_null` | NOT NULL | `NOT NULL language_code` |
| `error_catalog_message_template_not_null` | NOT NULL | `NOT NULL message_template` |
| `error_catalog_updated_at_not_null` | NOT NULL | `NOT NULL updated_at` |
| `error_catalog_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_error_catalog_code_language` | UNIQUE | `UNIQUE (error_code, language_code)` |

Indexes:

| Name | Definition |
|---|---|
| `error_catalog_pkey` | `CREATE UNIQUE INDEX error_catalog_pkey ON tenant_e2etest.error_catalog USING btree (id)` |
| `idx_error_catalog_lookup` | `CREATE INDEX idx_error_catalog_lookup ON tenant_e2etest.error_catalog USING btree (error_code, language_code)` |
| `uq_error_catalog_code_language` | `CREATE UNIQUE INDEX uq_error_catalog_code_language ON tenant_e2etest.error_catalog USING btree (error_code, language_code)` |

### `tenant_e2etest.fx_rates`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `rate_id` | `bigint` | NO | `nextval('tenant_e2etest.fx_rates_rate_id_seq'::regclass)` |
| 2 | `target_currency` | `character(3)` | NO | `` |
| 3 | `usd_rate` | `numeric(20,10)` | NO | `` |
| 4 | `rate_type` | `character varying(20)` | NO | `'MID'::character varying` |
| 5 | `provider` | `character varying(50)` | NO | `` |
| 6 | `valid_from` | `timestamp without time zone` | NO | `` |
| 7 | `version_no` | `bigint` | NO | `` |
| 8 | `is_active` | `boolean` | NO | `true` |
| 9 | `created_at` | `timestamp without time zone` | NO | `CURRENT_TIMESTAMP` |
| 10 | `created_by` | `character varying(50)` | NO | `` |
| 11 | `field1` | `character varying(100)` | YES | `` |
| 12 | `field2` | `character varying(100)` | YES | `` |
| 13 | `field3` | `character varying(100)` | YES | `` |
| 14 | `field4` | `character varying(100)` | YES | `` |
| 15 | `field5` | `character varying(100)` | YES | `` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `fx_rates_created_at_not_null` | NOT NULL | `NOT NULL created_at` |
| `fx_rates_created_by_not_null` | NOT NULL | `NOT NULL created_by` |
| `fx_rates_is_active_not_null` | NOT NULL | `NOT NULL is_active` |
| `fx_rates_provider_not_null` | NOT NULL | `NOT NULL provider` |
| `fx_rates_rate_id_not_null` | NOT NULL | `NOT NULL rate_id` |
| `fx_rates_rate_type_not_null` | NOT NULL | `NOT NULL rate_type` |
| `fx_rates_target_currency_not_null` | NOT NULL | `NOT NULL target_currency` |
| `fx_rates_usd_rate_not_null` | NOT NULL | `NOT NULL usd_rate` |
| `fx_rates_valid_from_not_null` | NOT NULL | `NOT NULL valid_from` |
| `fx_rates_version_no_not_null` | NOT NULL | `NOT NULL version_no` |
| `fx_rates_pkey` | PRIMARY KEY | `PRIMARY KEY (rate_id)` |
| `uq_fx_active` | UNIQUE | `UNIQUE (target_currency, version_no)` |

Indexes:

| Name | Definition |
|---|---|
| `fx_rates_pkey` | `CREATE UNIQUE INDEX fx_rates_pkey ON tenant_e2etest.fx_rates USING btree (rate_id)` |
| `uq_fx_active` | `CREATE UNIQUE INDEX uq_fx_active ON tenant_e2etest.fx_rates USING btree (target_currency, version_no)` |

### `tenant_e2etest.kyc_document`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `document_id` | `bigint` | NO | `nextval('tenant_e2etest.kyc_document_document_id_seq'::regclass)` |
| 2 | `account_id` | `text` | NO | `` |
| 3 | `document_type` | `character varying(50)` | NO | `` |
| 4 | `document_number` | `character varying(100)` | NO | `` |
| 5 | `issue_date` | `date` | YES | `` |
| 6 | `expiry_date` | `date` | YES | `` |
| 7 | `document_url` | `text` | NO | `` |
| 8 | `verification_status` | `character varying(50)` | NO | `'PENDING'::character varying` |
| 9 | `verified_by` | `character varying(255)` | YES | `` |
| 10 | `verified_at` | `timestamp without time zone` | YES | `` |
| 11 | `rejection_reason` | `text` | YES | `` |
| 12 | `is_primary` | `boolean` | YES | `false` |
| 13 | `is_active` | `boolean` | YES | `true` |
| 14 | `created_at` | `timestamp without time zone` | YES | `CURRENT_TIMESTAMP` |
| 15 | `updated_at` | `timestamp without time zone` | YES | `CURRENT_TIMESTAMP` |
| 16 | `stored_document_id` | `uuid` | YES | `` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `fk_kyc_document_account` | FOREIGN KEY | `FOREIGN KEY (account_id) REFERENCES tenant_e2etest.account(account_id)` |
| `kyc_document_stored_document_id_fkey` | FOREIGN KEY | `FOREIGN KEY (stored_document_id) REFERENCES tenant_e2etest.stored_document(document_id)` |
| `kyc_document_account_id_not_null` | NOT NULL | `NOT NULL account_id` |
| `kyc_document_document_id_not_null` | NOT NULL | `NOT NULL document_id` |
| `kyc_document_document_number_not_null` | NOT NULL | `NOT NULL document_number` |
| `kyc_document_document_type_not_null` | NOT NULL | `NOT NULL document_type` |
| `kyc_document_document_url_not_null` | NOT NULL | `NOT NULL document_url` |
| `kyc_document_verification_status_not_null` | NOT NULL | `NOT NULL verification_status` |
| `kyc_document_pkey` | PRIMARY KEY | `PRIMARY KEY (document_id)` |

Indexes:

| Name | Definition |
|---|---|
| `kyc_document_pkey` | `CREATE UNIQUE INDEX kyc_document_pkey ON tenant_e2etest.kyc_document USING btree (document_id)` |

### `tenant_e2etest.notification_outbox`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `notification_id` | `bigint` | NO | `nextval('tenant_e2etest.notification_outbox_notification_id_seq'::regclass)` |
| 2 | `transaction_id` | `character varying(30)` | YES | `` |
| 3 | `account_id` | `character varying(100)` | YES | `` |
| 4 | `party_role` | `character varying(20)` | YES | `` |
| 5 | `channel` | `character varying(50)` | NO | `` |
| 6 | `recipient` | `character varying(2000)` | NO | `` |
| 7 | `recipient_masked` | `character varying(200)` | YES | `` |
| 8 | `template_code` | `character varying(200)` | YES | `` |
| 9 | `subject` | `character varying(500)` | YES | `` |
| 10 | `title` | `character varying(500)` | YES | `` |
| 11 | `notification_text` | `text` | NO | `` |
| 12 | `payload` | `jsonb` | YES | `` |
| 13 | `status` | `character varying(30)` | NO | `'PENDING'::character varying` |
| 14 | `attempt_count` | `integer` | NO | `0` |
| 15 | `next_attempt_at` | `timestamp without time zone` | YES | `` |
| 16 | `last_error` | `character varying(1000)` | YES | `` |
| 17 | `service_code` | `character varying(15)` | YES | `` |
| 18 | `transfer_status` | `character varying(10)` | YES | `` |
| 19 | `trace_id` | `character varying(100)` | YES | `` |
| 20 | `created_on` | `timestamp without time zone` | NO | `now()` |
| 21 | `modified_on` | `timestamp without time zone` | NO | `now()` |
| 22 | `sent_on` | `timestamp without time zone` | YES | `` |
| 23 | `version` | `bigint` | YES | `` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `notification_outbox_attempt_count_not_null` | NOT NULL | `NOT NULL attempt_count` |
| `notification_outbox_channel_not_null` | NOT NULL | `NOT NULL channel` |
| `notification_outbox_created_on_not_null` | NOT NULL | `NOT NULL created_on` |
| `notification_outbox_modified_on_not_null` | NOT NULL | `NOT NULL modified_on` |
| `notification_outbox_notification_id_not_null` | NOT NULL | `NOT NULL notification_id` |
| `notification_outbox_notification_text_not_null` | NOT NULL | `NOT NULL notification_text` |
| `notification_outbox_recipient_not_null` | NOT NULL | `NOT NULL recipient` |
| `notification_outbox_status_not_null` | NOT NULL | `NOT NULL status` |
| `notification_outbox_pkey` | PRIMARY KEY | `PRIMARY KEY (notification_id)` |

Indexes:

| Name | Definition |
|---|---|
| `idx_notification_outbox_channel_status` | `CREATE INDEX idx_notification_outbox_channel_status ON tenant_e2etest.notification_outbox USING btree (channel, status, created_on)` |
| `idx_notification_outbox_pending` | `CREATE INDEX idx_notification_outbox_pending ON tenant_e2etest.notification_outbox USING btree (status, next_attempt_at, created_on)` |
| `idx_notification_outbox_transaction` | `CREATE INDEX idx_notification_outbox_transaction ON tenant_e2etest.notification_outbox USING btree (transaction_id)` |
| `notification_outbox_pkey` | `CREATE UNIQUE INDEX notification_outbox_pkey ON tenant_e2etest.notification_outbox USING btree (notification_id)` |

### `tenant_e2etest.notification_template`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `template_id` | `bigint` | NO | `nextval('tenant_e2etest.notification_template_template_id_seq'::regclass)` |
| 2 | `template_code` | `character varying(200)` | NO | `` |
| 3 | `template_definition` | `jsonb` | NO | `` |
| 4 | `status` | `character varying(30)` | NO | `'ACTIVE'::character varying` |
| 5 | `description` | `character varying(500)` | YES | `` |
| 6 | `created_by` | `character varying(100)` | YES | `` |
| 7 | `created_at` | `timestamp without time zone` | NO | `now()` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `notification_template_created_at_not_null` | NOT NULL | `NOT NULL created_at` |
| `notification_template_status_not_null` | NOT NULL | `NOT NULL status` |
| `notification_template_template_code_not_null` | NOT NULL | `NOT NULL template_code` |
| `notification_template_template_definition_not_null` | NOT NULL | `NOT NULL template_definition` |
| `notification_template_template_id_not_null` | NOT NULL | `NOT NULL template_id` |
| `notification_template_pkey` | PRIMARY KEY | `PRIMARY KEY (template_id)` |

Indexes:

| Name | Definition |
|---|---|
| `idx_notification_template_code_status` | `CREATE INDEX idx_notification_template_code_status ON tenant_e2etest.notification_template USING btree (template_code, status)` |
| `notification_template_pkey` | `CREATE UNIQUE INDEX notification_template_pkey ON tenant_e2etest.notification_template USING btree (template_id)` |

### `tenant_e2etest.otp`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `otp_id` | `bigint` | NO | `nextval('tenant_e2etest.otp_otp_id_seq'::regclass)` |
| 2 | `reference_type` | `character varying(30)` | NO | `` |
| 3 | `reference_id` | `character varying(100)` | YES | `` |
| 4 | `mobile_number` | `character varying(20)` | YES | `` |
| 5 | `otp_value` | `integer` | YES | `` |
| 6 | `status` | `character varying(20)` | YES | `` |
| 7 | `attempt_count` | `integer` | YES | `0` |
| 8 | `max_attempt` | `integer` | YES | `3` |
| 9 | `expires_at` | `timestamp without time zone` | NO | `` |
| 10 | `verified_at` | `timestamp without time zone` | YES | `` |
| 11 | `created_at` | `timestamp without time zone` | YES | `CURRENT_TIMESTAMP` |
| 12 | `updated_at` | `timestamp without time zone` | YES | `` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `otp_expires_at_not_null` | NOT NULL | `NOT NULL expires_at` |
| `otp_otp_id_not_null` | NOT NULL | `NOT NULL otp_id` |
| `otp_reference_type_not_null` | NOT NULL | `NOT NULL reference_type` |
| `otp_pkey` | PRIMARY KEY | `PRIMARY KEY (otp_id)` |

Indexes:

| Name | Definition |
|---|---|
| `otp_pkey` | `CREATE UNIQUE INDEX otp_pkey ON tenant_e2etest.otp USING btree (otp_id)` |

### `tenant_e2etest.passcode`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `passcode_id` | `bigint` | NO | `nextval('tenant_e2etest.passcode_passcode_id_seq'::regclass)` |
| 2 | `transaction_id` | `character varying(30)` | NO | `` |
| 3 | `cashout_transaction_id` | `character varying(30)` | YES | `` |
| 4 | `amount` | `numeric(19,0)` | NO | `` |
| 5 | `currency` | `character varying(10)` | NO | `` |
| 6 | `unregistered_msisdn` | `character varying(30)` | NO | `` |
| 7 | `first_name` | `character varying(100)` | YES | `` |
| 8 | `last_name` | `character varying(100)` | YES | `` |
| 9 | `kyc_document_id` | `character varying(100)` | YES | `` |
| 10 | `sender_msisdn` | `character varying(30)` | YES | `` |
| 11 | `sender_account_id` | `character varying(30)` | NO | `` |
| 12 | `passcode` | `character varying(10)` | NO | `` |
| 13 | `status` | `character varying(20)` | NO | `` |
| 14 | `created_on` | `timestamp without time zone` | NO | `` |
| 15 | `modified_on` | `timestamp without time zone` | NO | `` |
| 16 | `redeemed_on` | `timestamp without time zone` | YES | `` |
| 17 | `field1` | `character varying(250)` | YES | `` |
| 18 | `field2` | `character varying(250)` | YES | `` |
| 19 | `field3` | `character varying(250)` | YES | `` |
| 20 | `field4` | `character varying(250)` | YES | `` |
| 21 | `field5` | `character varying(250)` | YES | `` |
| 22 | `version` | `bigint` | YES | `` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `passcode_amount_not_null` | NOT NULL | `NOT NULL amount` |
| `passcode_created_on_not_null` | NOT NULL | `NOT NULL created_on` |
| `passcode_currency_not_null` | NOT NULL | `NOT NULL currency` |
| `passcode_modified_on_not_null` | NOT NULL | `NOT NULL modified_on` |
| `passcode_passcode_id_not_null` | NOT NULL | `NOT NULL passcode_id` |
| `passcode_passcode_not_null` | NOT NULL | `NOT NULL passcode` |
| `passcode_sender_account_id_not_null` | NOT NULL | `NOT NULL sender_account_id` |
| `passcode_status_not_null` | NOT NULL | `NOT NULL status` |
| `passcode_transaction_id_not_null` | NOT NULL | `NOT NULL transaction_id` |
| `passcode_unregistered_msisdn_not_null` | NOT NULL | `NOT NULL unregistered_msisdn` |
| `passcode_pkey` | PRIMARY KEY | `PRIMARY KEY (passcode_id)` |
| `passcode_passcode_key` | UNIQUE | `UNIQUE (passcode)` |

Indexes:

| Name | Definition |
|---|---|
| `idx_passcode_lookup` | `CREATE INDEX idx_passcode_lookup ON tenant_e2etest.passcode USING btree (passcode, unregistered_msisdn, status)` |
| `passcode_passcode_key` | `CREATE UNIQUE INDEX passcode_passcode_key ON tenant_e2etest.passcode USING btree (passcode)` |
| `passcode_pkey` | `CREATE UNIQUE INDEX passcode_pkey ON tenant_e2etest.passcode USING btree (passcode_id)` |

### `tenant_e2etest.permissions`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `permission_id` | `bigint` | NO | `nextval('tenant_e2etest.permissions_permission_id_seq'::regclass)` |
| 2 | `permission_code` | `character varying(100)` | NO | `` |
| 3 | `module` | `character varying(50)` | YES | `` |
| 4 | `description` | `text` | YES | `` |
| 5 | `created_at` | `timestamp without time zone` | YES | `CURRENT_TIMESTAMP` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `permissions_permission_code_not_null` | NOT NULL | `NOT NULL permission_code` |
| `permissions_permission_id_not_null` | NOT NULL | `NOT NULL permission_id` |
| `permissions_pkey` | PRIMARY KEY | `PRIMARY KEY (permission_id)` |
| `permissions_permission_code_key` | UNIQUE | `UNIQUE (permission_code)` |

Indexes:

| Name | Definition |
|---|---|
| `permissions_permission_code_key` | `CREATE UNIQUE INDEX permissions_permission_code_key ON tenant_e2etest.permissions USING btree (permission_code)` |
| `permissions_pkey` | `CREATE UNIQUE INDEX permissions_pkey ON tenant_e2etest.permissions USING btree (permission_id)` |

### `tenant_e2etest.pricing_rules`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `id` | `bigint` | NO | `nextval('tenant_e2etest.pricing_rules_id_seq'::regclass)` |
| 2 | `pricing_name` | `character varying(100)` | NO | `` |
| 3 | `service_code` | `character varying(50)` | NO | `` |
| 4 | `rule_type` | `character varying(30)` | NO | `` |
| 5 | `pricing_type` | `character varying(20)` | YES | `` |
| 6 | `payer` | `character varying(20)` | NO | `` |
| 7 | `pay_by` | `character varying(20)` | YES | `` |
| 8 | `payer_split` | `jsonb` | YES | `` |
| 9 | `sender_tag_key` | `character varying(255)` | NO | `` |
| 10 | `receiver_tag_key` | `character varying(255)` | NO | `` |
| 11 | `currency` | `character varying(10)` | NO | `` |
| 12 | `pricing_config` | `jsonb` | NO | `` |
| 13 | `status` | `character varying(50)` | NO | `` |
| 14 | `valid_from` | `timestamp without time zone` | NO | `CURRENT_TIMESTAMP` |
| 15 | `valid_to` | `timestamp without time zone` | YES | `` |
| 16 | `created_at` | `timestamp without time zone` | YES | `CURRENT_TIMESTAMP` |
| 17 | `updated_at` | `timestamp without time zone` | YES | `` |
| 18 | `created_by` | `character varying(255)` | YES | `` |
| 19 | `updated_by` | `character varying(255)` | YES | `` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `pricing_rules_currency_not_null` | NOT NULL | `NOT NULL currency` |
| `pricing_rules_id_not_null` | NOT NULL | `NOT NULL id` |
| `pricing_rules_payer_not_null` | NOT NULL | `NOT NULL payer` |
| `pricing_rules_pricing_config_not_null` | NOT NULL | `NOT NULL pricing_config` |
| `pricing_rules_pricing_name_not_null` | NOT NULL | `NOT NULL pricing_name` |
| `pricing_rules_receiver_tag_key_not_null` | NOT NULL | `NOT NULL receiver_tag_key` |
| `pricing_rules_rule_type_not_null` | NOT NULL | `NOT NULL rule_type` |
| `pricing_rules_sender_tag_key_not_null` | NOT NULL | `NOT NULL sender_tag_key` |
| `pricing_rules_service_code_not_null` | NOT NULL | `NOT NULL service_code` |
| `pricing_rules_status_not_null` | NOT NULL | `NOT NULL status` |
| `pricing_rules_valid_from_not_null` | NOT NULL | `NOT NULL valid_from` |
| `pricing_rules_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |

Indexes:

| Name | Definition |
|---|---|
| `pricing_rules_pkey` | `CREATE UNIQUE INDEX pricing_rules_pkey ON tenant_e2etest.pricing_rules USING btree (id)` |

### `tenant_e2etest.qr_payment_intent`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `qr_intent_id` | `character varying(40)` | NO | `` |
| 2 | `operation_type` | `character varying(20)` | NO | `` |
| 3 | `creditor_identifier_type` | `character varying(30)` | NO | `` |
| 4 | `creditor_identifier_value` | `character varying(30)` | NO | `` |
| 5 | `creditor_account_type` | `character varying(30)` | NO | `` |
| 6 | `creditor_wallet_type` | `character varying(50)` | NO | `` |
| 7 | `currency` | `character varying(10)` | NO | `` |
| 8 | `amount` | `numeric(19,2)` | YES | `` |
| 9 | `status` | `character varying(20)` | NO | `` |
| 10 | `expires_at` | `timestamp without time zone` | NO | `` |
| 11 | `transaction_id` | `character varying(30)` | YES | `` |
| 12 | `created_at` | `timestamp without time zone` | NO | `CURRENT_TIMESTAMP` |
| 13 | `updated_at` | `timestamp without time zone` | YES | `` |
| 14 | `version` | `bigint` | YES | `` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `qr_payment_intent_created_at_not_null` | NOT NULL | `NOT NULL created_at` |
| `qr_payment_intent_creditor_account_type_not_null` | NOT NULL | `NOT NULL creditor_account_type` |
| `qr_payment_intent_creditor_identifier_type_not_null` | NOT NULL | `NOT NULL creditor_identifier_type` |
| `qr_payment_intent_creditor_identifier_value_not_null` | NOT NULL | `NOT NULL creditor_identifier_value` |
| `qr_payment_intent_creditor_wallet_type_not_null` | NOT NULL | `NOT NULL creditor_wallet_type` |
| `qr_payment_intent_currency_not_null` | NOT NULL | `NOT NULL currency` |
| `qr_payment_intent_expires_at_not_null` | NOT NULL | `NOT NULL expires_at` |
| `qr_payment_intent_operation_type_not_null` | NOT NULL | `NOT NULL operation_type` |
| `qr_payment_intent_qr_intent_id_not_null` | NOT NULL | `NOT NULL qr_intent_id` |
| `qr_payment_intent_status_not_null` | NOT NULL | `NOT NULL status` |
| `qr_payment_intent_pkey` | PRIMARY KEY | `PRIMARY KEY (qr_intent_id)` |

Indexes:

| Name | Definition |
|---|---|
| `idx_qr_payment_intent_status_expiry` | `CREATE INDEX idx_qr_payment_intent_status_expiry ON tenant_e2etest.qr_payment_intent USING btree (status, expires_at)` |
| `qr_payment_intent_pkey` | `CREATE UNIQUE INDEX qr_payment_intent_pkey ON tenant_e2etest.qr_payment_intent USING btree (qr_intent_id)` |

### `tenant_e2etest.recent_recipients`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `account_id` | `character varying(30)` | NO | `` |
| 2 | `recipient_account_id` | `character varying(30)` | NO | `` |
| 3 | `service_code` | `character varying(15)` | NO | `` |
| 4 | `currency` | `character varying(10)` | NO | `` |
| 5 | `wallet_type` | `character varying(50)` | NO | `` |
| 6 | `recipient_account_type` | `character varying(50)` | YES | `` |
| 7 | `recipient_identifier_type` | `character varying(30)` | YES | `` |
| 8 | `recipient_identifier_value` | `character varying(100)` | YES | `` |
| 9 | `recipient_display_name` | `character varying(200)` | YES | `` |
| 10 | `last_transaction_id` | `character varying(30)` | YES | `` |
| 11 | `last_paid_at` | `timestamp without time zone` | NO | `` |
| 12 | `payment_count` | `bigint` | NO | `1` |
| 13 | `field1` | `character varying(250)` | YES | `` |
| 14 | `field2` | `character varying(250)` | YES | `` |
| 15 | `field3` | `character varying(250)` | YES | `` |
| 16 | `field4` | `character varying(250)` | YES | `` |
| 17 | `field5` | `character varying(250)` | YES | `` |
| 18 | `created_on` | `timestamp without time zone` | NO | `CURRENT_TIMESTAMP` |
| 19 | `modified_on` | `timestamp without time zone` | NO | `CURRENT_TIMESTAMP` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `recent_recipients_account_id_not_null` | NOT NULL | `NOT NULL account_id` |
| `recent_recipients_created_on_not_null` | NOT NULL | `NOT NULL created_on` |
| `recent_recipients_currency_not_null` | NOT NULL | `NOT NULL currency` |
| `recent_recipients_last_paid_at_not_null` | NOT NULL | `NOT NULL last_paid_at` |
| `recent_recipients_modified_on_not_null` | NOT NULL | `NOT NULL modified_on` |
| `recent_recipients_payment_count_not_null` | NOT NULL | `NOT NULL payment_count` |
| `recent_recipients_recipient_account_id_not_null` | NOT NULL | `NOT NULL recipient_account_id` |
| `recent_recipients_service_code_not_null` | NOT NULL | `NOT NULL service_code` |
| `recent_recipients_wallet_type_not_null` | NOT NULL | `NOT NULL wallet_type` |
| `recent_recipients_pkey` | PRIMARY KEY | `PRIMARY KEY (account_id, recipient_account_id, service_code, currency, wallet_type)` |

Indexes:

| Name | Definition |
|---|---|
| `idx_recent_recipients_account_last_paid` | `CREATE INDEX idx_recent_recipients_account_last_paid ON tenant_e2etest.recent_recipients USING btree (account_id, last_paid_at DESC)` |
| `idx_recent_recipients_account_service_last_paid` | `CREATE INDEX idx_recent_recipients_account_service_last_paid ON tenant_e2etest.recent_recipients USING btree (account_id, service_code, last_paid_at DESC)` |
| `recent_recipients_pkey` | `CREATE UNIQUE INDEX recent_recipients_pkey ON tenant_e2etest.recent_recipients USING btree (account_id, recipient_account_id, service_code, currency, wallet_type)` |

### `tenant_e2etest.role_permissions`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `id` | `bigint` | NO | `nextval('tenant_e2etest.role_permissions_id_seq'::regclass)` |
| 2 | `role_id` | `bigint` | NO | `` |
| 3 | `permission_id` | `bigint` | NO | `` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `fk_role_permissions_permission` | FOREIGN KEY | `FOREIGN KEY (permission_id) REFERENCES tenant_e2etest.permissions(permission_id)` |
| `fk_role_permissions_role` | FOREIGN KEY | `FOREIGN KEY (role_id) REFERENCES tenant_e2etest.roles(role_id)` |
| `role_permissions_id_not_null` | NOT NULL | `NOT NULL id` |
| `role_permissions_permission_id_not_null` | NOT NULL | `NOT NULL permission_id` |
| `role_permissions_role_id_not_null` | NOT NULL | `NOT NULL role_id` |
| `role_permissions_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uk_role_permissions_role_permission` | UNIQUE | `UNIQUE (role_id, permission_id)` |

Indexes:

| Name | Definition |
|---|---|
| `role_permissions_pkey` | `CREATE UNIQUE INDEX role_permissions_pkey ON tenant_e2etest.role_permissions USING btree (id)` |
| `uk_role_permissions_role_permission` | `CREATE UNIQUE INDEX uk_role_permissions_role_permission ON tenant_e2etest.role_permissions USING btree (role_id, permission_id)` |

### `tenant_e2etest.roles`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `role_id` | `bigint` | NO | `nextval('tenant_e2etest.roles_role_id_seq'::regclass)` |
| 2 | `role_code` | `character varying(50)` | NO | `` |
| 3 | `role_name` | `character varying(100)` | NO | `` |
| 4 | `role_type` | `character varying(30)` | NO | `` |
| 5 | `description` | `text` | YES | `` |
| 6 | `status` | `character varying(20)` | YES | `'ACTIVE'::character varying` |
| 7 | `created_at` | `timestamp without time zone` | YES | `CURRENT_TIMESTAMP` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `roles_role_code_not_null` | NOT NULL | `NOT NULL role_code` |
| `roles_role_id_not_null` | NOT NULL | `NOT NULL role_id` |
| `roles_role_name_not_null` | NOT NULL | `NOT NULL role_name` |
| `roles_role_type_not_null` | NOT NULL | `NOT NULL role_type` |
| `roles_pkey` | PRIMARY KEY | `PRIMARY KEY (role_id)` |
| `roles_role_code_key` | UNIQUE | `UNIQUE (role_code)` |

Indexes:

| Name | Definition |
|---|---|
| `roles_pkey` | `CREATE UNIQUE INDEX roles_pkey ON tenant_e2etest.roles USING btree (role_id)` |
| `roles_role_code_key` | `CREATE UNIQUE INDEX roles_role_code_key ON tenant_e2etest.roles USING btree (role_code)` |

### `tenant_e2etest.service_catalog`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `service_code` | `character varying(50)` | NO | `` |
| 2 | `service_name` | `character varying(100)` | NO | `` |
| 3 | `description` | `character varying(255)` | YES | `` |
| 4 | `service_category` | `character varying(50)` | YES | `` |
| 5 | `transaction_type` | `character varying(50)` | YES | `` |
| 6 | `is_financial` | `boolean` | NO | `true` |
| 7 | `send_to_integrator` | `boolean` | NO | `false` |
| 8 | `requires_confirmation` | `boolean` | NO | `false` |
| 9 | `integrator_call_mode` | `character varying(20)` | NO | `'SYNC'::character varying` |
| 10 | `is_active` | `boolean` | NO | `true` |
| 11 | `display_order` | `integer` | NO | `0` |
| 12 | `created_at` | `timestamp without time zone` | NO | `CURRENT_TIMESTAMP` |
| 13 | `updated_at` | `timestamp without time zone` | YES | `` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `service_catalog_created_at_not_null` | NOT NULL | `NOT NULL created_at` |
| `service_catalog_display_order_not_null` | NOT NULL | `NOT NULL display_order` |
| `service_catalog_integrator_call_mode_not_null` | NOT NULL | `NOT NULL integrator_call_mode` |
| `service_catalog_is_active_not_null` | NOT NULL | `NOT NULL is_active` |
| `service_catalog_is_financial_not_null` | NOT NULL | `NOT NULL is_financial` |
| `service_catalog_requires_confirmation_not_null` | NOT NULL | `NOT NULL requires_confirmation` |
| `service_catalog_send_to_integrator_not_null` | NOT NULL | `NOT NULL send_to_integrator` |
| `service_catalog_service_code_not_null` | NOT NULL | `NOT NULL service_code` |
| `service_catalog_service_name_not_null` | NOT NULL | `NOT NULL service_name` |
| `service_catalog_pkey` | PRIMARY KEY | `PRIMARY KEY (service_code)` |

Indexes:

| Name | Definition |
|---|---|
| `service_catalog_pkey` | `CREATE UNIQUE INDEX service_catalog_pkey ON tenant_e2etest.service_catalog USING btree (service_code)` |

### `tenant_e2etest.stored_document`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `document_id` | `uuid` | NO | `` |
| 2 | `tenant_id` | `character varying(100)` | NO | `` |
| 3 | `document_type_id` | `bigint` | NO | `` |
| 4 | `document_name` | `character varying(255)` | NO | `` |
| 5 | `original_file_name` | `character varying(255)` | NO | `` |
| 6 | `content_type` | `character varying(150)` | NO | `` |
| 7 | `file_size_bytes` | `bigint` | NO | `` |
| 8 | `checksum_sha256` | `character varying(64)` | YES | `` |
| 9 | `gridfs_bucket_name` | `character varying(100)` | NO | `'fs'::character varying` |
| 10 | `gridfs_file_id` | `character varying(64)` | NO | `` |
| 11 | `thumbnail_gridfs_file_id` | `character varying(64)` | YES | `` |
| 12 | `thumbnail_content_type` | `character varying(150)` | YES | `` |
| 13 | `thumbnail_size_bytes` | `bigint` | YES | `` |
| 14 | `status` | `character varying(30)` | NO | `'ACTIVE'::character varying` |
| 15 | `uploaded_by` | `character varying(100)` | NO | `` |
| 16 | `uploaded_at` | `timestamp without time zone` | NO | `CURRENT_TIMESTAMP` |
| 17 | `updated_at` | `timestamp without time zone` | NO | `CURRENT_TIMESTAMP` |
| 18 | `deleted_at` | `timestamp without time zone` | YES | `` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `chk_stored_document_size` | CHECK | `CHECK (file_size_bytes >= 0)` |
| `chk_stored_document_status` | CHECK | `CHECK (status::text = ANY (ARRAY['ACTIVE'::character varying, 'DELETED'::character varying, 'QUARANTINED'::character varying, 'UPLOAD_FAILED'::character varying]::text[]))` |
| `chk_stored_document_thumbnail_size` | CHECK | `CHECK (thumbnail_size_bytes IS NULL OR thumbnail_size_bytes >= 0)` |
| `fk_stored_document_type` | FOREIGN KEY | `FOREIGN KEY (document_type_id) REFERENCES tenant_e2etest.document_type(document_type_id)` |
| `stored_document_content_type_not_null` | NOT NULL | `NOT NULL content_type` |
| `stored_document_document_id_not_null` | NOT NULL | `NOT NULL document_id` |
| `stored_document_document_name_not_null` | NOT NULL | `NOT NULL document_name` |
| `stored_document_document_type_id_not_null` | NOT NULL | `NOT NULL document_type_id` |
| `stored_document_file_size_bytes_not_null` | NOT NULL | `NOT NULL file_size_bytes` |
| `stored_document_gridfs_bucket_name_not_null` | NOT NULL | `NOT NULL gridfs_bucket_name` |
| `stored_document_gridfs_file_id_not_null` | NOT NULL | `NOT NULL gridfs_file_id` |
| `stored_document_original_file_name_not_null` | NOT NULL | `NOT NULL original_file_name` |
| `stored_document_status_not_null` | NOT NULL | `NOT NULL status` |
| `stored_document_tenant_id_not_null` | NOT NULL | `NOT NULL tenant_id` |
| `stored_document_updated_at_not_null` | NOT NULL | `NOT NULL updated_at` |
| `stored_document_uploaded_at_not_null` | NOT NULL | `NOT NULL uploaded_at` |
| `stored_document_uploaded_by_not_null` | NOT NULL | `NOT NULL uploaded_by` |
| `stored_document_pkey` | PRIMARY KEY | `PRIMARY KEY (document_id)` |
| `stored_document_gridfs_file_id_key` | UNIQUE | `UNIQUE (gridfs_file_id)` |

Indexes:

| Name | Definition |
|---|---|
| `idx_stored_document_checksum` | `CREATE INDEX idx_stored_document_checksum ON tenant_e2etest.stored_document USING btree (checksum_sha256)` |
| `idx_stored_document_tenant_type` | `CREATE INDEX idx_stored_document_tenant_type ON tenant_e2etest.stored_document USING btree (tenant_id, document_type_id, uploaded_at DESC)` |
| `stored_document_gridfs_file_id_key` | `CREATE UNIQUE INDEX stored_document_gridfs_file_id_key ON tenant_e2etest.stored_document USING btree (gridfs_file_id)` |
| `stored_document_pkey` | `CREATE UNIQUE INDEX stored_document_pkey ON tenant_e2etest.stored_document USING btree (document_id)` |
| `uk_stored_document_thumbnail_gridfs_file_id` | `CREATE UNIQUE INDEX uk_stored_document_thumbnail_gridfs_file_id ON tenant_e2etest.stored_document USING btree (thumbnail_gridfs_file_id) WHERE (thumbnail_gridfs_file_id IS NOT NULL)` |

### `tenant_e2etest.supported_languages`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `id` | `bigint` | NO | `nextval('tenant_e2etest.supported_languages_id_seq'::regclass)` |
| 2 | `language_code` | `character varying(10)` | NO | `` |
| 3 | `language_name` | `character varying(100)` | NO | `` |
| 4 | `display_order` | `integer` | YES | `0` |
| 5 | `is_default` | `boolean` | YES | `false` |
| 6 | `is_active` | `boolean` | YES | `true` |
| 7 | `created_at` | `timestamp without time zone` | YES | `CURRENT_TIMESTAMP` |
| 8 | `updated_at` | `timestamp without time zone` | YES | `` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `supported_languages_id_not_null` | NOT NULL | `NOT NULL id` |
| `supported_languages_language_code_not_null` | NOT NULL | `NOT NULL language_code` |
| `supported_languages_language_name_not_null` | NOT NULL | `NOT NULL language_name` |
| `supported_languages_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `supported_languages_language_code_key` | UNIQUE | `UNIQUE (language_code)` |

Indexes:

| Name | Definition |
|---|---|
| `supported_languages_language_code_key` | `CREATE UNIQUE INDEX supported_languages_language_code_key ON tenant_e2etest.supported_languages USING btree (language_code)` |
| `supported_languages_pkey` | `CREATE UNIQUE INDEX supported_languages_pkey ON tenant_e2etest.supported_languages USING btree (id)` |

### `tenant_e2etest.tag_types`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `tag_type_id` | `bigint` | NO | `nextval('tenant_e2etest.tag_types_tag_type_id_seq'::regclass)` |
| 2 | `type_code` | `character varying(50)` | NO | `` |
| 3 | `type_name` | `character varying(100)` | NO | `` |
| 4 | `description` | `text` | YES | `` |
| 5 | `status` | `text` | YES | `` |
| 6 | `created_at` | `timestamp without time zone` | YES | `CURRENT_TIMESTAMP` |
| 7 | `updated_at` | `timestamp without time zone` | YES | `CURRENT_TIMESTAMP` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `tag_types_tag_type_id_not_null` | NOT NULL | `NOT NULL tag_type_id` |
| `tag_types_type_code_not_null` | NOT NULL | `NOT NULL type_code` |
| `tag_types_type_name_not_null` | NOT NULL | `NOT NULL type_name` |
| `tag_types_pkey` | PRIMARY KEY | `PRIMARY KEY (tag_type_id)` |
| `tag_types_type_code_key` | UNIQUE | `UNIQUE (type_code)` |

Indexes:

| Name | Definition |
|---|---|
| `tag_types_pkey` | `CREATE UNIQUE INDEX tag_types_pkey ON tenant_e2etest.tag_types USING btree (tag_type_id)` |
| `tag_types_type_code_key` | `CREATE UNIQUE INDEX tag_types_type_code_key ON tenant_e2etest.tag_types USING btree (type_code)` |

### `tenant_e2etest.tags`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `tag_id` | `bigint` | NO | `nextval('tenant_e2etest.tags_tag_id_seq'::regclass)` |
| 2 | `tag_code` | `character varying(50)` | NO | `` |
| 3 | `tag_name` | `character varying(100)` | NO | `` |
| 4 | `category` | `character varying(50)` | YES | `` |
| 5 | `is_default` | `boolean` | NO | `false` |
| 6 | `tag_type` | `character varying(50)` | YES | `` |
| 7 | `status` | `character varying(20)` | NO | `'ACTIVE'::character varying` |
| 8 | `created_at` | `timestamp without time zone` | YES | `CURRENT_TIMESTAMP` |
| 9 | `updated_at` | `timestamp without time zone` | YES | `CURRENT_TIMESTAMP` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `tags_is_default_not_null` | NOT NULL | `NOT NULL is_default` |
| `tags_status_not_null` | NOT NULL | `NOT NULL status` |
| `tags_tag_code_not_null` | NOT NULL | `NOT NULL tag_code` |
| `tags_tag_id_not_null` | NOT NULL | `NOT NULL tag_id` |
| `tags_tag_name_not_null` | NOT NULL | `NOT NULL tag_name` |
| `tags_pkey` | PRIMARY KEY | `PRIMARY KEY (tag_id)` |
| `tags_tag_code_key` | UNIQUE | `UNIQUE (tag_code)` |

Indexes:

| Name | Definition |
|---|---|
| `tags_pkey` | `CREATE UNIQUE INDEX tags_pkey ON tenant_e2etest.tags USING btree (tag_id)` |
| `tags_tag_code_key` | `CREATE UNIQUE INDEX tags_tag_code_key ON tenant_e2etest.tags USING btree (tag_code)` |

### `tenant_e2etest.third_party_response`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `id` | `bigint` | NO | `nextval('tenant_e2etest.third_party_response_id_seq'::regclass)` |
| 2 | `transaction_id` | `character varying(50)` | NO | `` |
| 3 | `service_code` | `character varying(50)` | YES | `` |
| 4 | `integrator_name` | `character varying(100)` | YES | `` |
| 5 | `request_body` | `jsonb` | NO | `` |
| 6 | `response_body` | `jsonb` | YES | `` |
| 7 | `status` | `character varying(30)` | NO | `'PENDING'::character varying` |
| 8 | `error_message` | `text` | YES | `` |
| 9 | `created_on` | `timestamp without time zone` | NO | `CURRENT_TIMESTAMP` |
| 10 | `modified_on` | `timestamp without time zone` | NO | `CURRENT_TIMESTAMP` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `third_party_response_created_on_not_null` | NOT NULL | `NOT NULL created_on` |
| `third_party_response_id_not_null` | NOT NULL | `NOT NULL id` |
| `third_party_response_modified_on_not_null` | NOT NULL | `NOT NULL modified_on` |
| `third_party_response_request_body_not_null` | NOT NULL | `NOT NULL request_body` |
| `third_party_response_status_not_null` | NOT NULL | `NOT NULL status` |
| `third_party_response_transaction_id_not_null` | NOT NULL | `NOT NULL transaction_id` |
| `third_party_response_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `third_party_response_transaction_id_key` | UNIQUE | `UNIQUE (transaction_id)` |

Indexes:

| Name | Definition |
|---|---|
| `idx_third_party_response_service_status` | `CREATE INDEX idx_third_party_response_service_status ON tenant_e2etest.third_party_response USING btree (service_code, status)` |
| `third_party_response_pkey` | `CREATE UNIQUE INDEX third_party_response_pkey ON tenant_e2etest.third_party_response USING btree (id)` |
| `third_party_response_transaction_id_key` | `CREATE UNIQUE INDEX third_party_response_transaction_id_key ON tenant_e2etest.third_party_response USING btree (transaction_id)` |
| `ux_third_party_response_txn` | `CREATE UNIQUE INDEX ux_third_party_response_txn ON tenant_e2etest.third_party_response USING btree (transaction_id)` |

### `tenant_e2etest.transaction_details`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `transaction_id` | `character varying(30)` | NO | `` |
| 2 | `txn_sequence_number` | `bigint` | NO | `` |
| 3 | `account_id` | `character varying(30)` | NO | `` |
| 4 | `user_type` | `character varying(10)` | NO | `` |
| 5 | `entry_type` | `character varying(5)` | NO | `` |
| 6 | `identifier_id` | `character varying(80)` | NO | `` |
| 7 | `second_identifier_id` | `character varying(80)` | NO | `` |
| 8 | `transaction_value` | `numeric(19,0)` | YES | `` |
| 9 | `approved_value` | `numeric(19,0)` | YES | `` |
| 10 | `previous_balance` | `numeric(19,0)` | YES | `` |
| 11 | `post_balance` | `numeric(19,0)` | YES | `` |
| 12 | `transfer_on` | `timestamp without time zone` | YES | `` |
| 13 | `service_code` | `character varying(15)` | NO | `` |
| 14 | `transfer_status` | `character varying(3)` | YES | `` |
| 15 | `wallet_number` | `character varying(25)` | YES | `` |
| 16 | `wallet_type` | `character varying(50)` | YES | `` |
| 17 | `currency` | `character varying(10)` | YES | `` |
| 18 | `transaction_type` | `character varying(50)` | YES | `` |
| 19 | `previous_fic_balance` | `numeric(19,0)` | YES | `` |
| 20 | `post_fic_balance` | `numeric(19,0)` | YES | `` |
| 21 | `previous_frozen_balance` | `numeric(19,0)` | YES | `` |
| 22 | `post_frozen_balance` | `numeric(19,0)` | YES | `` |
| 23 | `attr_1_name` | `character varying(255)` | YES | `` |
| 24 | `attr_1_value` | `character varying(255)` | YES | `` |
| 25 | `attr_2_name` | `character varying(255)` | YES | `` |
| 26 | `attr_2_value` | `character varying(255)` | YES | `` |
| 27 | `attr_3_name` | `character varying(255)` | YES | `` |
| 28 | `attr_3_value` | `character varying(255)` | YES | `` |
| 29 | `attr_4_name` | `character varying(255)` | YES | `` |
| 30 | `attr_4_value` | `character varying(255)` | YES | `` |
| 31 | `attr_5_name` | `character varying(255)` | YES | `` |
| 32 | `attr_5_value` | `character varying(255)` | YES | `` |
| 33 | `attr_6_name` | `character varying(255)` | YES | `` |
| 34 | `attr_6_value` | `character varying(255)` | YES | `` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `transaction_details_account_id_not_null` | NOT NULL | `NOT NULL account_id` |
| `transaction_details_entry_type_not_null` | NOT NULL | `NOT NULL entry_type` |
| `transaction_details_identifier_id_not_null` | NOT NULL | `NOT NULL identifier_id` |
| `transaction_details_second_identifier_id_not_null` | NOT NULL | `NOT NULL second_identifier_id` |
| `transaction_details_service_code_not_null` | NOT NULL | `NOT NULL service_code` |
| `transaction_details_transaction_id_not_null` | NOT NULL | `NOT NULL transaction_id` |
| `transaction_details_txn_sequence_number_not_null` | NOT NULL | `NOT NULL txn_sequence_number` |
| `transaction_details_user_type_not_null` | NOT NULL | `NOT NULL user_type` |
| `transaction_details_pkey` | PRIMARY KEY | `PRIMARY KEY (transaction_id, txn_sequence_number)` |

Indexes:

| Name | Definition |
|---|---|
| `transaction_details_pkey` | `CREATE UNIQUE INDEX transaction_details_pkey ON tenant_e2etest.transaction_details USING btree (transaction_id, txn_sequence_number)` |

### `tenant_e2etest.transaction_limit_profile`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `limit_id` | `bigint` | NO | `nextval('tenant_e2etest.transaction_limit_profile_limit_id_seq'::regclass)` |
| 2 | `limit_name` | `character varying(150)` | NO | `` |
| 3 | `tag_id` | `bigint` | NO | `` |
| 4 | `limit_type` | `character varying(20)` | NO | `` |
| 5 | `subject_key` | `character varying(50)` | NO | `` |
| 6 | `details` | `jsonb` | YES | `` |
| 7 | `status` | `character varying(20)` | NO | `'ACTIVE'::character varying` |
| 8 | `wallet_type` | `character varying(50)` | NO | `` |
| 9 | `currency` | `character varying(10)` | NO | `` |
| 10 | `min_residual_balance` | `numeric(19,0)` | YES | `` |
| 11 | `max_balance` | `numeric(19,0)` | YES | `` |
| 12 | `created_by` | `character varying(100)` | YES | `` |
| 13 | `created_on` | `timestamp without time zone` | NO | `now()` |
| 14 | `modified_by` | `character varying(100)` | YES | `` |
| 15 | `modified_on` | `timestamp without time zone` | NO | `now()` |
| 16 | `version` | `bigint` | YES | `` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `fk_transaction_limit_profile_tag` | FOREIGN KEY | `FOREIGN KEY (tag_id) REFERENCES tenant_e2etest.tags(tag_id)` |
| `transaction_limit_profile_created_on_not_null` | NOT NULL | `NOT NULL created_on` |
| `transaction_limit_profile_currency_not_null` | NOT NULL | `NOT NULL currency` |
| `transaction_limit_profile_limit_id_not_null` | NOT NULL | `NOT NULL limit_id` |
| `transaction_limit_profile_limit_name_not_null` | NOT NULL | `NOT NULL limit_name` |
| `transaction_limit_profile_limit_type_not_null` | NOT NULL | `NOT NULL limit_type` |
| `transaction_limit_profile_modified_on_not_null` | NOT NULL | `NOT NULL modified_on` |
| `transaction_limit_profile_status_not_null` | NOT NULL | `NOT NULL status` |
| `transaction_limit_profile_subject_key_not_null` | NOT NULL | `NOT NULL subject_key` |
| `transaction_limit_profile_tag_id_not_null` | NOT NULL | `NOT NULL tag_id` |
| `transaction_limit_profile_wallet_type_not_null` | NOT NULL | `NOT NULL wallet_type` |
| `transaction_limit_profile_pkey` | PRIMARY KEY | `PRIMARY KEY (limit_id)` |

Indexes:

| Name | Definition |
|---|---|
| `idx_tlp_tag_id` | `CREATE INDEX idx_tlp_tag_id ON tenant_e2etest.transaction_limit_profile USING btree (tag_id)` |
| `idx_transaction_limit_profile_tag` | `CREATE INDEX idx_transaction_limit_profile_tag ON tenant_e2etest.transaction_limit_profile USING btree (tag_id, status, created_on DESC)` |
| `idx_transaction_limit_profile_type` | `CREATE INDEX idx_transaction_limit_profile_type ON tenant_e2etest.transaction_limit_profile USING btree (limit_type, status, wallet_type, currency)` |
| `transaction_limit_profile_pkey` | `CREATE UNIQUE INDEX transaction_limit_profile_pkey ON tenant_e2etest.transaction_limit_profile USING btree (limit_id)` |

### `tenant_e2etest.transaction_limit_profile_details`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `limit_details_id` | `bigint` | NO | `nextval('tenant_e2etest.transaction_limit_profile_details_limit_details_id_seq'::regclass)` |
| 2 | `limit_id` | `bigint` | NO | `` |
| 3 | `party_type` | `character varying(20)` | NO | `` |
| 4 | `status` | `character varying(20)` | NO | `'ACTIVE'::character varying` |
| 5 | `operation_type` | `character varying(50)` | NO | `'ALL'::character varying` |
| 6 | `request_gateway` | `character varying(50)` | NO | `'ALL'::character varying` |
| 7 | `min_txn_amount` | `numeric(19,0)` | YES | `` |
| 8 | `max_txn_amount` | `numeric(19,0)` | YES | `` |
| 9 | `created_on` | `timestamp without time zone` | NO | `now()` |
| 10 | `modified_on` | `timestamp without time zone` | NO | `now()` |
| 11 | `version` | `bigint` | YES | `` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `fk_transaction_limit_details_profile` | FOREIGN KEY | `FOREIGN KEY (limit_id) REFERENCES tenant_e2etest.transaction_limit_profile(limit_id)` |
| `transaction_limit_profile_details_created_on_not_null` | NOT NULL | `NOT NULL created_on` |
| `transaction_limit_profile_details_limit_details_id_not_null` | NOT NULL | `NOT NULL limit_details_id` |
| `transaction_limit_profile_details_limit_id_not_null` | NOT NULL | `NOT NULL limit_id` |
| `transaction_limit_profile_details_modified_on_not_null` | NOT NULL | `NOT NULL modified_on` |
| `transaction_limit_profile_details_operation_type_not_null` | NOT NULL | `NOT NULL operation_type` |
| `transaction_limit_profile_details_party_type_not_null` | NOT NULL | `NOT NULL party_type` |
| `transaction_limit_profile_details_request_gateway_not_null` | NOT NULL | `NOT NULL request_gateway` |
| `transaction_limit_profile_details_status_not_null` | NOT NULL | `NOT NULL status` |
| `transaction_limit_profile_details_pkey` | PRIMARY KEY | `PRIMARY KEY (limit_details_id)` |

Indexes:

| Name | Definition |
|---|---|
| `idx_tlpd_limit_id` | `CREATE INDEX idx_tlpd_limit_id ON tenant_e2etest.transaction_limit_profile_details USING btree (limit_id)` |
| `idx_transaction_limit_details_profile` | `CREATE INDEX idx_transaction_limit_details_profile ON tenant_e2etest.transaction_limit_profile_details USING btree (limit_id, party_type, status)` |
| `transaction_limit_profile_details_pkey` | `CREATE UNIQUE INDEX transaction_limit_profile_details_pkey ON tenant_e2etest.transaction_limit_profile_details USING btree (limit_details_id)` |

### `tenant_e2etest.transaction_limit_profile_period`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `limit_period_id` | `bigint` | NO | `nextval('tenant_e2etest.transaction_limit_profile_period_limit_period_id_seq'::regclass)` |
| 2 | `limit_details_id` | `bigint` | NO | `` |
| 3 | `period_type` | `character varying(20)` | NO | `` |
| 4 | `max_count` | `integer` | YES | `` |
| 5 | `max_amount` | `numeric(19,0)` | YES | `` |
| 6 | `status` | `character varying(20)` | NO | `'ACTIVE'::character varying` |
| 7 | `created_on` | `timestamp without time zone` | NO | `now()` |
| 8 | `modified_on` | `timestamp without time zone` | NO | `now()` |
| 9 | `version` | `bigint` | YES | `` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `fk_transaction_limit_period_details` | FOREIGN KEY | `FOREIGN KEY (limit_details_id) REFERENCES tenant_e2etest.transaction_limit_profile_details(limit_details_id)` |
| `transaction_limit_profile_period_created_on_not_null` | NOT NULL | `NOT NULL created_on` |
| `transaction_limit_profile_period_limit_details_id_not_null` | NOT NULL | `NOT NULL limit_details_id` |
| `transaction_limit_profile_period_limit_period_id_not_null` | NOT NULL | `NOT NULL limit_period_id` |
| `transaction_limit_profile_period_modified_on_not_null` | NOT NULL | `NOT NULL modified_on` |
| `transaction_limit_profile_period_period_type_not_null` | NOT NULL | `NOT NULL period_type` |
| `transaction_limit_profile_period_status_not_null` | NOT NULL | `NOT NULL status` |
| `transaction_limit_profile_period_pkey` | PRIMARY KEY | `PRIMARY KEY (limit_period_id)` |

Indexes:

| Name | Definition |
|---|---|
| `idx_tlpp_limit_details_id` | `CREATE INDEX idx_tlpp_limit_details_id ON tenant_e2etest.transaction_limit_profile_period USING btree (limit_details_id)` |
| `idx_transaction_limit_period_details` | `CREATE INDEX idx_transaction_limit_period_details ON tenant_e2etest.transaction_limit_profile_period USING btree (limit_details_id, period_type, status)` |
| `transaction_limit_profile_period_pkey` | `CREATE UNIQUE INDEX transaction_limit_profile_period_pkey ON tenant_e2etest.transaction_limit_profile_period USING btree (limit_period_id)` |

### `tenant_e2etest.transaction_limit_usage`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `usage_id` | `bigint` | NO | `nextval('tenant_e2etest.transaction_limit_usage_usage_id_seq'::regclass)` |
| 2 | `subject_key` | `character varying(50)` | NO | `` |
| 3 | `subject_value` | `character varying(200)` | NO | `` |
| 4 | `account_id` | `character varying(100)` | YES | `` |
| 5 | `limit_id` | `bigint` | NO | `` |
| 6 | `limit_details_id` | `bigint` | NO | `` |
| 7 | `tag_id` | `bigint` | NO | `` |
| 8 | `period_type` | `character varying(20)` | NO | `` |
| 9 | `operation_type` | `character varying(50)` | NO | `` |
| 10 | `request_gateway` | `character varying(50)` | NO | `` |
| 11 | `payer_count` | `integer` | NO | `0` |
| 12 | `payer_amount` | `numeric(19,0)` | NO | `0` |
| 13 | `payee_count` | `integer` | NO | `0` |
| 14 | `payee_amount` | `numeric(19,0)` | NO | `0` |
| 15 | `last_transaction_id` | `character varying(30)` | YES | `` |
| 16 | `last_transaction_date` | `timestamp without time zone` | YES | `` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `fk_transaction_limit_usage_details` | FOREIGN KEY | `FOREIGN KEY (limit_details_id) REFERENCES tenant_e2etest.transaction_limit_profile_details(limit_details_id)` |
| `fk_transaction_limit_usage_profile` | FOREIGN KEY | `FOREIGN KEY (limit_id) REFERENCES tenant_e2etest.transaction_limit_profile(limit_id)` |
| `fk_transaction_limit_usage_tag` | FOREIGN KEY | `FOREIGN KEY (tag_id) REFERENCES tenant_e2etest.tags(tag_id)` |
| `transaction_limit_usage_limit_details_id_not_null` | NOT NULL | `NOT NULL limit_details_id` |
| `transaction_limit_usage_limit_id_not_null` | NOT NULL | `NOT NULL limit_id` |
| `transaction_limit_usage_operation_type_not_null` | NOT NULL | `NOT NULL operation_type` |
| `transaction_limit_usage_payee_amount_not_null` | NOT NULL | `NOT NULL payee_amount` |
| `transaction_limit_usage_payee_count_not_null` | NOT NULL | `NOT NULL payee_count` |
| `transaction_limit_usage_payer_amount_not_null` | NOT NULL | `NOT NULL payer_amount` |
| `transaction_limit_usage_payer_count_not_null` | NOT NULL | `NOT NULL payer_count` |
| `transaction_limit_usage_period_type_not_null` | NOT NULL | `NOT NULL period_type` |
| `transaction_limit_usage_request_gateway_not_null` | NOT NULL | `NOT NULL request_gateway` |
| `transaction_limit_usage_subject_key_not_null` | NOT NULL | `NOT NULL subject_key` |
| `transaction_limit_usage_subject_value_not_null` | NOT NULL | `NOT NULL subject_value` |
| `transaction_limit_usage_tag_id_not_null` | NOT NULL | `NOT NULL tag_id` |
| `transaction_limit_usage_usage_id_not_null` | NOT NULL | `NOT NULL usage_id` |
| `transaction_limit_usage_pkey` | PRIMARY KEY | `PRIMARY KEY (usage_id)` |

Indexes:

| Name | Definition |
|---|---|
| `idx_tlu_limit_details_id` | `CREATE INDEX idx_tlu_limit_details_id ON tenant_e2etest.transaction_limit_usage USING btree (limit_details_id)` |
| `idx_tlu_limit_id` | `CREATE INDEX idx_tlu_limit_id ON tenant_e2etest.transaction_limit_usage USING btree (limit_id)` |
| `idx_tlu_tag_id` | `CREATE INDEX idx_tlu_tag_id ON tenant_e2etest.transaction_limit_usage USING btree (tag_id)` |
| `idx_transaction_limit_usage_account_period` | `CREATE INDEX idx_transaction_limit_usage_account_period ON tenant_e2etest.transaction_limit_usage USING btree (account_id, period_type, last_transaction_date DESC)` |
| `idx_transaction_limit_usage_subject` | `CREATE INDEX idx_transaction_limit_usage_subject ON tenant_e2etest.transaction_limit_usage USING btree (subject_key, subject_value, last_transaction_date DESC)` |
| `transaction_limit_usage_pkey` | `CREATE UNIQUE INDEX transaction_limit_usage_pkey ON tenant_e2etest.transaction_limit_usage USING btree (usage_id)` |
| `uq_transaction_limit_usage_bucket` | `CREATE UNIQUE INDEX uq_transaction_limit_usage_bucket ON tenant_e2etest.transaction_limit_usage USING btree (subject_key, subject_value, limit_id, limit_details_id, period_type, operation_type, request_gateway)` |

### `tenant_e2etest.transactions`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `transaction_id` | `character varying(30)` | NO | `` |
| 2 | `transfer_on` | `timestamp without time zone` | YES | `` |
| 3 | `transaction_value` | `numeric(19,0)` | YES | `` |
| 4 | `error_code` | `character varying(200)` | YES | `` |
| 5 | `transfer_status` | `character varying(3)` | YES | `` |
| 6 | `request_gateway` | `character varying(10)` | YES | `` |
| 7 | `service_code` | `character varying(15)` | YES | `` |
| 8 | `trace_id` | `character varying(50)` | YES | `` |
| 9 | `payment_reference` | `character varying(100)` | YES | `` |
| 10 | `reconciliation_done` | `character varying(3)` | YES | `` |
| 11 | `reconciliation_date` | `timestamp without time zone` | YES | `` |
| 12 | `reconciliation_by` | `character varying(30)` | YES | `` |
| 13 | `language` | `character varying(10)` | YES | `` |
| 14 | `country` | `character varying(20)` | YES | `` |
| 15 | `created_by` | `character varying(30)` | NO | `` |
| 16 | `created_on` | `timestamp without time zone` | NO | `` |
| 17 | `modified_by` | `character varying(30)` | NO | `` |
| 18 | `modified_on` | `timestamp without time zone` | NO | `` |
| 19 | `comments` | `character varying(300)` | YES | `` |
| 20 | `debitor_account_id` | `character varying(30)` | YES | `` |
| 21 | `creditor_account_id` | `character varying(30)` | YES | `` |
| 22 | `debitor_wallet_type` | `character varying(50)` | YES | `` |
| 23 | `debitor_currency` | `character varying(10)` | YES | `` |
| 24 | `creditor_wallet_type` | `character varying(50)` | YES | `` |
| 25 | `creditor_currency` | `character varying(10)` | YES | `` |
| 26 | `fees_details` | `character varying(4000)` | YES | `` |
| 27 | `additional_info` | `character varying(4000)` | YES | `` |
| 28 | `metadata` | `character varying(4000)` | YES | `` |
| 29 | `attr_1_name` | `character varying(255)` | YES | `` |
| 30 | `attr_1_value` | `character varying(255)` | YES | `` |
| 31 | `attr_2_name` | `character varying(255)` | YES | `` |
| 32 | `attr_2_value` | `character varying(255)` | YES | `` |
| 33 | `attr_3_name` | `character varying(255)` | YES | `` |
| 34 | `attr_3_value` | `character varying(255)` | YES | `` |
| 35 | `attr_4_name` | `character varying(255)` | YES | `` |
| 36 | `attr_4_value` | `character varying(255)` | YES | `` |
| 37 | `attr_5_name` | `character varying(255)` | YES | `` |
| 38 | `attr_5_value` | `character varying(255)` | YES | `` |
| 39 | `attr_6_name` | `character varying(255)` | YES | `` |
| 40 | `attr_6_value` | `character varying(255)` | YES | `` |
| 41 | `field1` | `character varying(100)` | YES | `` |
| 42 | `field2` | `character varying(100)` | YES | `` |
| 43 | `field3` | `character varying(100)` | YES | `` |
| 44 | `field4` | `character varying(100)` | YES | `` |
| 45 | `field5` | `character varying(100)` | YES | `` |
| 46 | `field6` | `character varying(100)` | YES | `` |
| 47 | `field7` | `character varying(100)` | YES | `` |
| 48 | `field8` | `character varying(100)` | YES | `` |
| 49 | `field9` | `character varying(100)` | YES | `` |
| 50 | `field10` | `character varying(100)` | YES | `` |
| 51 | `core_service_code` | `character varying(100)` | YES | `` |
| 52 | `debitor_identifier_type` | `character varying(30)` | YES | `` |
| 53 | `debitor_identifier_value` | `character varying(30)` | YES | `` |
| 54 | `creditor_identifier_type` | `character varying(30)` | YES | `` |
| 55 | `creditor_identifier_value` | `character varying(30)` | YES | `` |
| 56 | `previous_status` | `character varying(5)` | YES | `` |
| 57 | `payment_via_qr` | `boolean` | NO | `false` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `transactions_created_by_not_null` | NOT NULL | `NOT NULL created_by` |
| `transactions_created_on_not_null` | NOT NULL | `NOT NULL created_on` |
| `transactions_modified_by_not_null` | NOT NULL | `NOT NULL modified_by` |
| `transactions_modified_on_not_null` | NOT NULL | `NOT NULL modified_on` |
| `transactions_payment_via_qr_not_null` | NOT NULL | `NOT NULL payment_via_qr` |
| `transactions_transaction_id_not_null` | NOT NULL | `NOT NULL transaction_id` |
| `transactions_pkey` | PRIMARY KEY | `PRIMARY KEY (transaction_id)` |

Indexes:

| Name | Definition |
|---|---|
| `transactions_pkey` | `CREATE UNIQUE INDEX transactions_pkey ON tenant_e2etest.transactions USING btree (transaction_id)` |

### `tenant_e2etest.user_roles`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `id` | `bigint` | NO | `nextval('tenant_e2etest.user_roles_id_seq'::regclass)` |
| 2 | `user_id` | `text` | NO | `` |
| 3 | `role_id` | `bigint` | NO | `` |
| 4 | `assigned_by` | `character varying(50)` | YES | `` |
| 5 | `assigned_at` | `timestamp without time zone` | YES | `CURRENT_TIMESTAMP` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `fk_user_roles_account` | FOREIGN KEY | `FOREIGN KEY (user_id) REFERENCES tenant_e2etest.account(account_id)` |
| `fk_user_roles_role` | FOREIGN KEY | `FOREIGN KEY (role_id) REFERENCES tenant_e2etest.roles(role_id)` |
| `user_roles_id_not_null` | NOT NULL | `NOT NULL id` |
| `user_roles_role_id_not_null` | NOT NULL | `NOT NULL role_id` |
| `user_roles_user_id_not_null` | NOT NULL | `NOT NULL user_id` |
| `user_roles_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uk_user_roles_user_role` | UNIQUE | `UNIQUE (user_id, role_id)` |

Indexes:

| Name | Definition |
|---|---|
| `uk_user_roles_user_role` | `CREATE UNIQUE INDEX uk_user_roles_user_role ON tenant_e2etest.user_roles USING btree (user_id, role_id)` |
| `user_roles_pkey` | `CREATE UNIQUE INDEX user_roles_pkey ON tenant_e2etest.user_roles USING btree (id)` |

### `tenant_e2etest.wallet`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `wallet_id` | `bigint` | NO | `nextval('tenant_e2etest.wallet_wallet_id_seq'::regclass)` |
| 2 | `account_id` | `text` | NO | `` |
| 3 | `currency` | `character varying(10)` | NO | `` |
| 4 | `wallet_type` | `character varying(50)` | NO | `` |
| 5 | `status` | `character varying(20)` | YES | `'ACTIVE'::character varying` |
| 6 | `is_default` | `boolean` | YES | `false` |
| 7 | `is_locked` | `boolean` | YES | `false` |
| 8 | `created_at` | `timestamp without time zone` | YES | `CURRENT_TIMESTAMP` |
| 9 | `updated_at` | `timestamp without time zone` | YES | `` |
| 10 | `remarks` | `text` | YES | `` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `fk_wallet_account` | FOREIGN KEY | `FOREIGN KEY (account_id) REFERENCES tenant_e2etest.account(account_id)` |
| `wallet_account_id_not_null` | NOT NULL | `NOT NULL account_id` |
| `wallet_currency_not_null` | NOT NULL | `NOT NULL currency` |
| `wallet_wallet_id_not_null` | NOT NULL | `NOT NULL wallet_id` |
| `wallet_wallet_type_not_null` | NOT NULL | `NOT NULL wallet_type` |
| `wallet_pkey` | PRIMARY KEY | `PRIMARY KEY (wallet_id)` |

Indexes:

| Name | Definition |
|---|---|
| `wallet_pkey` | `CREATE UNIQUE INDEX wallet_pkey ON tenant_e2etest.wallet USING btree (wallet_id)` |

### `tenant_e2etest.wallet_balance`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `wallet_id` | `bigint` | NO | `` |
| 2 | `available_balance` | `numeric(19,0)` | NO | `0` |
| 3 | `frozen_balance` | `numeric(19,0)` | NO | `0` |
| 4 | `fic_balance` | `numeric(19,0)` | NO | `0` |
| 5 | `version` | `bigint` | YES | `0` |
| 6 | `updated_at` | `timestamp without time zone` | YES | `CURRENT_TIMESTAMP` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `fk_wallet_balance_wallet` | FOREIGN KEY | `FOREIGN KEY (wallet_id) REFERENCES tenant_e2etest.wallet(wallet_id)` |
| `wallet_balance_available_balance_not_null` | NOT NULL | `NOT NULL available_balance` |
| `wallet_balance_fic_balance_not_null` | NOT NULL | `NOT NULL fic_balance` |
| `wallet_balance_frozen_balance_not_null` | NOT NULL | `NOT NULL frozen_balance` |
| `wallet_balance_wallet_id_not_null` | NOT NULL | `NOT NULL wallet_id` |
| `wallet_balance_pkey` | PRIMARY KEY | `PRIMARY KEY (wallet_id)` |

Indexes:

| Name | Definition |
|---|---|
| `wallet_balance_pkey` | `CREATE UNIQUE INDEX wallet_balance_pkey ON tenant_e2etest.wallet_balance USING btree (wallet_id)` |

### `tenant_e2etest.wallet_ledger`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `ledger_id` | `bigint` | NO | `nextval('tenant_e2etest.wallet_ledger_ledger_id_seq'::regclass)` |
| 2 | `txn_id` | `character varying(255)` | NO | `` |
| 3 | `wallet_id` | `bigint` | NO | `` |
| 4 | `account_id` | `text` | NO | `` |
| 5 | `entry_type` | `character varying(2)` | NO | `` |
| 6 | `amount` | `numeric(19,0)` | NO | `` |
| 7 | `currency` | `character varying(10)` | NO | `` |
| 8 | `balance_before` | `numeric(19,0)` | YES | `` |
| 9 | `balance_after` | `numeric(19,0)` | YES | `` |
| 10 | `txn_type` | `character varying(255)` | YES | `` |
| 11 | `reference_type` | `character varying(255)` | YES | `` |
| 12 | `reference_id` | `character varying(255)` | YES | `` |
| 13 | `description` | `text` | YES | `` |
| 14 | `attr1` | `text` | YES | `` |
| 15 | `attr2` | `text` | YES | `` |
| 16 | `attr3` | `text` | YES | `` |
| 17 | `attr4` | `text` | YES | `` |
| 18 | `attr5` | `text` | YES | `` |
| 19 | `created_at` | `timestamp without time zone` | YES | `CURRENT_TIMESTAMP` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `wallet_ledger_account_id_not_null` | NOT NULL | `NOT NULL account_id` |
| `wallet_ledger_amount_not_null` | NOT NULL | `NOT NULL amount` |
| `wallet_ledger_currency_not_null` | NOT NULL | `NOT NULL currency` |
| `wallet_ledger_entry_type_not_null` | NOT NULL | `NOT NULL entry_type` |
| `wallet_ledger_ledger_id_not_null` | NOT NULL | `NOT NULL ledger_id` |
| `wallet_ledger_txn_id_not_null` | NOT NULL | `NOT NULL txn_id` |
| `wallet_ledger_wallet_id_not_null` | NOT NULL | `NOT NULL wallet_id` |
| `wallet_ledger_pkey` | PRIMARY KEY | `PRIMARY KEY (ledger_id)` |

Indexes:

| Name | Definition |
|---|---|
| `wallet_ledger_pkey` | `CREATE UNIQUE INDEX wallet_ledger_pkey ON tenant_e2etest.wallet_ledger USING btree (ledger_id)` |

### `tenant_e2etest.wallet_restriction`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `wallet_id` | `bigint` | NO | `` |
| 2 | `restrictions` | `jsonb` | NO | `` |
| 3 | `version` | `bigint` | YES | `0` |
| 4 | `updated_at` | `timestamp without time zone` | YES | `CURRENT_TIMESTAMP` |
| 5 | `updated_by` | `character varying(100)` | YES | `` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `wallet_restriction_restrictions_not_null` | NOT NULL | `NOT NULL restrictions` |
| `wallet_restriction_wallet_id_not_null` | NOT NULL | `NOT NULL wallet_id` |
| `wallet_restriction_pkey` | PRIMARY KEY | `PRIMARY KEY (wallet_id)` |

Indexes:

| Name | Definition |
|---|---|
| `wallet_restriction_pkey` | `CREATE UNIQUE INDEX wallet_restriction_pkey ON tenant_e2etest.wallet_restriction USING btree (wallet_id)` |

### `tenant_e2etest.wallet_restriction_history`

| # | Column | Type | Nullable | Default |
|---:|---|---|---|---|
| 1 | `history_id` | `bigint` | NO | `nextval('tenant_e2etest.wallet_restriction_history_history_id_seq'::regclass)` |
| 2 | `wallet_id` | `bigint` | NO | `` |
| 3 | `version` | `bigint` | NO | `` |
| 4 | `restrictions` | `jsonb` | NO | `` |
| 5 | `action_type` | `character varying(50)` | YES | `` |
| 6 | `changed_by` | `character varying(100)` | YES | `` |
| 7 | `created_at` | `timestamp without time zone` | NO | `CURRENT_TIMESTAMP` |

Constraints:

| Name | Type | Definition |
|---|---|---|
| `wallet_restriction_history_created_at_not_null` | NOT NULL | `NOT NULL created_at` |
| `wallet_restriction_history_history_id_not_null` | NOT NULL | `NOT NULL history_id` |
| `wallet_restriction_history_restrictions_not_null` | NOT NULL | `NOT NULL restrictions` |
| `wallet_restriction_history_version_not_null` | NOT NULL | `NOT NULL version` |
| `wallet_restriction_history_wallet_id_not_null` | NOT NULL | `NOT NULL wallet_id` |
| `wallet_restriction_history_pkey` | PRIMARY KEY | `PRIMARY KEY (history_id)` |
| `wallet_restriction_history_wallet_id_version_key` | UNIQUE | `UNIQUE (wallet_id, version)` |

Indexes:

| Name | Definition |
|---|---|
| `wallet_restriction_history_pkey` | `CREATE UNIQUE INDEX wallet_restriction_history_pkey ON tenant_e2etest.wallet_restriction_history USING btree (history_id)` |
| `wallet_restriction_history_wallet_id_version_key` | `CREATE UNIQUE INDEX wallet_restriction_history_wallet_id_version_key ON tenant_e2etest.wallet_restriction_history USING btree (wallet_id, version)` |
