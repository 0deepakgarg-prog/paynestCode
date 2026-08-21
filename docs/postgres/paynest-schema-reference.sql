--
-- PostgreSQL database dump
--
-- PayNest schema reference generated from public + complete tenant_e2etest.
-- Cross-check source: JPA entities, startup schema initializers, SQL resources, and bootstrap scripts.
-- Use scripts/bootstrap-paynest-site.ps1 for deployments.
--

\restrict BbGzSIuyrLttdQ4NnErTGyRVrTHpjano2rIwgyvV9VfMH39cX52TJbSlbpGTaYT

-- Dumped from database version 18.2
-- Dumped by pg_dump version 18.2

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: public; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA public;


--
-- Name: SCHEMA public; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON SCHEMA public IS 'standard public schema';


--
-- Name: tenant_e2etest; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA tenant_e2etest;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: audit_api_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_api_logs (
    id bigint NOT NULL,
    trace_id character varying(100),
    tenant_id character varying(50),
    http_method character varying(10),
    request_body jsonb,
    response_body jsonb,
    http_status integer,
    processing_time_ms bigint,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    api_path character varying(255),
    account_id character varying(50),
    service_code character varying(50),
    reference_id character varying(100),
    transaction_id character varying(50)
);


--
-- Name: audit_api_logs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.audit_api_logs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: audit_api_logs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.audit_api_logs_id_seq OWNED BY public.audit_api_logs.id;


--
-- Name: system_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_config (
    config_id bigint NOT NULL,
    config_key text NOT NULL,
    config_value text NOT NULL,
    config_type text NOT NULL,
    description text,
    is_active boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    updated_by text
);


--
-- Name: system_config_config_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_config_config_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_config_config_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.system_config_config_id_seq OWNED BY public.system_config.config_id;


--
-- Name: tenant_registry; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tenant_registry (
    tenant_id character varying(50) NOT NULL,
    tenant_name character varying(100),
    schema_name character varying(100),
    status character varying(20),
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone,
    iana_time_zone character varying(100)
);


--
-- Name: account; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.account (
    account_id text NOT NULL,
    account_type character varying(50) NOT NULL,
    account_code character varying(100),
    first_name character varying(255),
    last_name character varying(255),
    mobile_number character varying(50),
    email character varying(255),
    address text,
    gender character varying(50),
    date_of_birth date,
    preferred_lang character varying(20),
    nationality character varying(100),
    ssn character varying(100),
    remarks text,
    attr1 text,
    attr2 text,
    attr3 text,
    attr4 text,
    attr5 text,
    attr6 text,
    attr7 text,
    attr8 text,
    attr9 text,
    attr10 text,
    kyc_status character varying(50),
    status character varying(20),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone,
    created_by character varying(255),
    updated_by character varying(255)
);


--
-- Name: account_auth; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.account_auth (
    auth_id bigint NOT NULL,
    auth_hash character varying(255),
    auth_value character varying(255),
    auth_type character varying(20) DEFAULT 'PIN'::character varying NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    failed_attempts integer DEFAULT 0,
    is_first_time_login boolean DEFAULT false,
    last_failed_at timestamp without time zone,
    last_login_at timestamp without time zone,
    last_login_ip character varying(50),
    password_changed_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone
);


--
-- Name: account_biller_info; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.account_biller_info (
    biller_info_id bigint NOT NULL,
    account_id text NOT NULL,
    biller_category character varying(50) NOT NULL,
    biller_code character varying(100) NOT NULL,
    biller_sub_category character varying(100),
    biller_config jsonb,
    biller_settings jsonb,
    created_on timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    created_by character varying(100),
    modified_on timestamp without time zone,
    modified_by character varying(100),
    field1 character varying(250),
    field2 character varying(250),
    field3 character varying(250),
    field4 character varying(250),
    field5 character varying(250)
);


--
-- Name: account_biller_info_biller_info_id_seq; Type: SEQUENCE; Schema: tenant_e2etest; Owner: -
--

CREATE SEQUENCE tenant_e2etest.account_biller_info_biller_info_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: account_biller_info_biller_info_id_seq; Type: SEQUENCE OWNED BY; Schema: tenant_e2etest; Owner: -
--

ALTER SEQUENCE tenant_e2etest.account_biller_info_biller_info_id_seq OWNED BY tenant_e2etest.account_biller_info.biller_info_id;


--
-- Name: account_identifiers; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.account_identifiers (
    id bigint NOT NULL,
    account_id text NOT NULL,
    auth_id bigint NOT NULL,
    identifier_type character varying(50) NOT NULL,
    identifier_value character varying(255) NOT NULL,
    status character varying(20) NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone
);


--
-- Name: account_identifiers_id_seq; Type: SEQUENCE; Schema: tenant_e2etest; Owner: -
--

CREATE SEQUENCE tenant_e2etest.account_identifiers_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: account_identifiers_id_seq; Type: SEQUENCE OWNED BY; Schema: tenant_e2etest; Owner: -
--

ALTER SEQUENCE tenant_e2etest.account_identifiers_id_seq OWNED BY tenant_e2etest.account_identifiers.id;


--
-- Name: account_merchant_info; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.account_merchant_info (
    merchant_info_id bigint NOT NULL,
    account_id text NOT NULL,
    merchant_code character varying(100) NOT NULL,
    merchant_config jsonb,
    created_on timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    created_by character varying(100),
    modified_on timestamp without time zone,
    modified_by character varying(100),
    field1 character varying(250),
    field2 character varying(250),
    field3 character varying(250),
    field4 character varying(250),
    field5 character varying(250)
);


--
-- Name: account_merchant_info_merchant_info_id_seq; Type: SEQUENCE; Schema: tenant_e2etest; Owner: -
--

CREATE SEQUENCE tenant_e2etest.account_merchant_info_merchant_info_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: account_merchant_info_merchant_info_id_seq; Type: SEQUENCE OWNED BY; Schema: tenant_e2etest; Owner: -
--

ALTER SEQUENCE tenant_e2etest.account_merchant_info_merchant_info_id_seq OWNED BY tenant_e2etest.account_merchant_info.merchant_info_id;


--
-- Name: account_merchant_mcc; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.account_merchant_mcc (
    merchant_mcc_id bigint NOT NULL,
    merchant_info_id bigint NOT NULL,
    mcc_code character varying(4) NOT NULL,
    created_on timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    created_by character varying(100),
    modified_on timestamp without time zone,
    modified_by character varying(100),
    field1 character varying(250),
    field2 character varying(250),
    field3 character varying(250),
    field4 character varying(250),
    field5 character varying(250)
);


--
-- Name: account_merchant_mcc_merchant_mcc_id_seq; Type: SEQUENCE; Schema: tenant_e2etest; Owner: -
--

CREATE SEQUENCE tenant_e2etest.account_merchant_mcc_merchant_mcc_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: account_merchant_mcc_merchant_mcc_id_seq; Type: SEQUENCE OWNED BY; Schema: tenant_e2etest; Owner: -
--

ALTER SEQUENCE tenant_e2etest.account_merchant_mcc_merchant_mcc_id_seq OWNED BY tenant_e2etest.account_merchant_mcc.merchant_mcc_id;


--
-- Name: account_notification_endpoint; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.account_notification_endpoint (
    account_endpoint_id bigint NOT NULL,
    account_id character varying(100) NOT NULL,
    endpoint_type character varying(50) NOT NULL,
    endpoint_value character varying(2000) NOT NULL,
    is_primary boolean DEFAULT false,
    status character varying(30) DEFAULT 'ACTIVE'::character varying,
    created_on timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    field1 character varying(250),
    field2 character varying(250),
    field3 character varying(250),
    field4 character varying(250),
    field5 character varying(250)
);


--
-- Name: account_notification_endpoint_account_endpoint_id_seq; Type: SEQUENCE; Schema: tenant_e2etest; Owner: -
--

CREATE SEQUENCE tenant_e2etest.account_notification_endpoint_account_endpoint_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: account_notification_endpoint_account_endpoint_id_seq; Type: SEQUENCE OWNED BY; Schema: tenant_e2etest; Owner: -
--

ALTER SEQUENCE tenant_e2etest.account_notification_endpoint_account_endpoint_id_seq OWNED BY tenant_e2etest.account_notification_endpoint.account_endpoint_id;


--
-- Name: account_status_history; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.account_status_history (
    history_id bigint NOT NULL,
    account_id character varying(100) NOT NULL,
    account_type character varying(50),
    action_type character varying(50) NOT NULL,
    previous_status character varying(50),
    new_status character varying(50) NOT NULL,
    performed_by character varying(100) NOT NULL,
    performed_by_type character varying(50),
    reason character varying(500),
    remarks character varying(1000),
    performed_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: account_status_history_history_id_seq; Type: SEQUENCE; Schema: tenant_e2etest; Owner: -
--

CREATE SEQUENCE tenant_e2etest.account_status_history_history_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: account_status_history_history_id_seq; Type: SEQUENCE OWNED BY; Schema: tenant_e2etest; Owner: -
--

ALTER SEQUENCE tenant_e2etest.account_status_history_history_id_seq OWNED BY tenant_e2etest.account_status_history.history_id;


--
-- Name: account_tags; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.account_tags (
    id bigint NOT NULL,
    account_id text NOT NULL,
    tag_id bigint NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    created_by character varying(50)
);


--
-- Name: account_tags_id_seq; Type: SEQUENCE; Schema: tenant_e2etest; Owner: -
--

CREATE SEQUENCE tenant_e2etest.account_tags_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: account_tags_id_seq; Type: SEQUENCE OWNED BY; Schema: tenant_e2etest; Owner: -
--

ALTER SEQUENCE tenant_e2etest.account_tags_id_seq OWNED BY tenant_e2etest.account_tags.id;


--
-- Name: audit_api_log; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.audit_api_log (
    id bigint NOT NULL,
    request_id character varying(255),
    trace_id character varying(255),
    tenant_id character varying(255),
    http_method character varying(20),
    endpoint text,
    status_code integer,
    request_payload text,
    response_payload text,
    error_message text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: audit_api_log_id_seq; Type: SEQUENCE; Schema: tenant_e2etest; Owner: -
--

CREATE SEQUENCE tenant_e2etest.audit_api_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: audit_api_log_id_seq; Type: SEQUENCE OWNED BY; Schema: tenant_e2etest; Owner: -
--

ALTER SEQUENCE tenant_e2etest.audit_api_log_id_seq OWNED BY tenant_e2etest.audit_api_log.id;


--
-- Name: auth_challenge; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.auth_challenge (
    challenge_id uuid NOT NULL,
    account_id text,
    challenge_value text NOT NULL,
    challenge_type character varying(30) NOT NULL,
    issued_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    expires_at timestamp without time zone NOT NULL,
    used boolean DEFAULT false,
    used_at timestamp without time zone,
    ip_address character varying(50),
    status character varying(20) DEFAULT 'ACTIVE'::character varying
);


--
-- Name: bill_payment_status; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.bill_payment_status (
    transaction_id character varying(255) NOT NULL,
    status character varying(50) NOT NULL,
    subscriber_account_id character varying(255) NOT NULL,
    biller_account_id character varying(255) NOT NULL,
    trace_id character varying(255) NOT NULL,
    comments text,
    additional_info text,
    rollback_transaction_id character varying(255),
    settled_by character varying(255),
    settled_on timestamp without time zone,
    created_on timestamp without time zone NOT NULL,
    modified_on timestamp without time zone NOT NULL
);


--
-- Name: cashback_payout; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.cashback_payout (
    cashback_payout_id bigint NOT NULL,
    original_transaction_id character varying(30) NOT NULL,
    payout_transaction_id character varying(30),
    service_code character varying(15) NOT NULL,
    beneficiary_account_id character varying(30) NOT NULL,
    beneficiary_party character varying(20),
    amount numeric(19,4) NOT NULL,
    currency character varying(10) NOT NULL,
    payment_schedule character varying(30) NOT NULL,
    pay_at timestamp without time zone NOT NULL,
    status character varying(20) NOT NULL,
    pricing_rule_details character varying(4000),
    failure_reason character varying(300),
    created_on timestamp without time zone NOT NULL,
    modified_on timestamp without time zone NOT NULL,
    version bigint
);


--
-- Name: cashback_payout_cashback_payout_id_seq; Type: SEQUENCE; Schema: tenant_e2etest; Owner: -
--

CREATE SEQUENCE tenant_e2etest.cashback_payout_cashback_payout_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: cashback_payout_cashback_payout_id_seq; Type: SEQUENCE OWNED BY; Schema: tenant_e2etest; Owner: -
--

ALTER SEQUENCE tenant_e2etest.cashback_payout_cashback_payout_id_seq OWNED BY tenant_e2etest.cashback_payout.cashback_payout_id;


--
-- Name: categories; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.categories (
    category_id bigint NOT NULL,
    category_code character varying(50) NOT NULL,
    category_name character varying(100) NOT NULL,
    description text,
    status text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: categories_category_id_seq; Type: SEQUENCE; Schema: tenant_e2etest; Owner: -
--

CREATE SEQUENCE tenant_e2etest.categories_category_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: categories_category_id_seq; Type: SEQUENCE OWNED BY; Schema: tenant_e2etest; Owner: -
--

ALTER SEQUENCE tenant_e2etest.categories_category_id_seq OWNED BY tenant_e2etest.categories.category_id;


--
-- Name: city; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.city (
    id bigint NOT NULL,
    country_id bigint NOT NULL,
    subdivision_id bigint,
    name character varying(150) NOT NULL,
    code character varying(100),
    latitude numeric(10,6),
    longitude numeric(10,6),
    timezone character varying(100),
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone
);


--
-- Name: city_id_seq; Type: SEQUENCE; Schema: tenant_e2etest; Owner: -
--

CREATE SEQUENCE tenant_e2etest.city_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: city_id_seq; Type: SEQUENCE OWNED BY; Schema: tenant_e2etest; Owner: -
--

ALTER SEQUENCE tenant_e2etest.city_id_seq OWNED BY tenant_e2etest.city.id;


--
-- Name: country_subdivision; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.country_subdivision (
    id bigint NOT NULL,
    country_id bigint NOT NULL,
    code character varying(50) NOT NULL,
    name character varying(150) NOT NULL,
    type character varying(50) NOT NULL,
    parent_id bigint,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone
);


--
-- Name: country_subdivision_id_seq; Type: SEQUENCE; Schema: tenant_e2etest; Owner: -
--

CREATE SEQUENCE tenant_e2etest.country_subdivision_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: country_subdivision_id_seq; Type: SEQUENCE OWNED BY; Schema: tenant_e2etest; Owner: -
--

ALTER SEQUENCE tenant_e2etest.country_subdivision_id_seq OWNED BY tenant_e2etest.country_subdivision.id;


--
-- Name: document_category; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.document_category (
    category_id bigint NOT NULL,
    category_code character varying(50) NOT NULL,
    category_name character varying(100) NOT NULL,
    description character varying(255),
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: document_category_category_id_seq; Type: SEQUENCE; Schema: tenant_e2etest; Owner: -
--

CREATE SEQUENCE tenant_e2etest.document_category_category_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: document_category_category_id_seq; Type: SEQUENCE OWNED BY; Schema: tenant_e2etest; Owner: -
--

ALTER SEQUENCE tenant_e2etest.document_category_category_id_seq OWNED BY tenant_e2etest.document_category.category_id;


--
-- Name: document_reference; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.document_reference (
    document_reference_id bigint NOT NULL,
    document_id uuid NOT NULL,
    entity_type character varying(30) NOT NULL,
    entity_id character varying(100) NOT NULL,
    reference_role character varying(30) DEFAULT 'OWNER'::character varying NOT NULL,
    is_primary boolean DEFAULT false NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_document_reference_entity CHECK (((entity_type)::text = ANY ((ARRAY['CUSTOMER'::character varying, 'MERCHANT'::character varying, 'AGENT'::character varying, 'TRANSACTION'::character varying])::text[]))),
    CONSTRAINT chk_document_reference_role CHECK (((reference_role)::text = ANY ((ARRAY['OWNER'::character varying, 'SUBJECT'::character varying, 'ATTACHMENT'::character varying, 'AUTHORIZED_VIEWER'::character varying])::text[])))
);


--
-- Name: document_reference_document_reference_id_seq; Type: SEQUENCE; Schema: tenant_e2etest; Owner: -
--

CREATE SEQUENCE tenant_e2etest.document_reference_document_reference_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: document_reference_document_reference_id_seq; Type: SEQUENCE OWNED BY; Schema: tenant_e2etest; Owner: -
--

ALTER SEQUENCE tenant_e2etest.document_reference_document_reference_id_seq OWNED BY tenant_e2etest.document_reference.document_reference_id;


--
-- Name: document_type; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.document_type (
    document_type_id bigint NOT NULL,
    category_id bigint NOT NULL,
    type_code character varying(75) NOT NULL,
    type_name character varying(150) NOT NULL,
    description character varying(255),
    multiple_allowed boolean DEFAULT true NOT NULL,
    verification_required boolean DEFAULT false NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: document_type_document_type_id_seq; Type: SEQUENCE; Schema: tenant_e2etest; Owner: -
--

CREATE SEQUENCE tenant_e2etest.document_type_document_type_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: document_type_document_type_id_seq; Type: SEQUENCE OWNED BY; Schema: tenant_e2etest; Owner: -
--

ALTER SEQUENCE tenant_e2etest.document_type_document_type_id_seq OWNED BY tenant_e2etest.document_type.document_type_id;


--
-- Name: document_type_entity; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.document_type_entity (
    document_type_id bigint NOT NULL,
    entity_type character varying(30) NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_document_type_entity CHECK (((entity_type)::text = ANY ((ARRAY['CUSTOMER'::character varying, 'MERCHANT'::character varying, 'AGENT'::character varying, 'TRANSACTION'::character varying])::text[])))
);


--
-- Name: enumerations; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.enumerations (
    id bigint NOT NULL,
    enum_type character varying(50) NOT NULL,
    enum_code character varying(50) NOT NULL,
    enum_value character varying(100) NOT NULL,
    parent_enum_id bigint,
    description character varying(255),
    display_order integer DEFAULT 0,
    is_active boolean DEFAULT true,
    is_system boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone,
    field1 character varying(250),
    field2 character varying(250),
    field3 character varying(250),
    field4 character varying(250),
    field5 character varying(250)
);


--
-- Name: enumerations_id_seq; Type: SEQUENCE; Schema: tenant_e2etest; Owner: -
--

CREATE SEQUENCE tenant_e2etest.enumerations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: enumerations_id_seq; Type: SEQUENCE OWNED BY; Schema: tenant_e2etest; Owner: -
--

ALTER SEQUENCE tenant_e2etest.enumerations_id_seq OWNED BY tenant_e2etest.enumerations.id;


--
-- Name: error_catalog; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.error_catalog (
    id bigint NOT NULL,
    error_code character varying(100) NOT NULL,
    language_code character varying(10) NOT NULL,
    message_template text NOT NULL,
    http_status integer DEFAULT 400 NOT NULL,
    category character varying(30),
    module character varying(30),
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by text,
    CONSTRAINT error_catalog_http_status_check CHECK (((http_status >= 100) AND (http_status <= 599)))
);


--
-- Name: error_catalog_id_seq; Type: SEQUENCE; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE tenant_e2etest.error_catalog ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME tenant_e2etest.error_catalog_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: fx_rates; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.fx_rates (
    rate_id bigint NOT NULL,
    target_currency character(3) NOT NULL,
    usd_rate numeric(20,10) NOT NULL,
    rate_type character varying(20) DEFAULT 'MID'::character varying NOT NULL,
    provider character varying(50) NOT NULL,
    valid_from timestamp without time zone NOT NULL,
    version_no bigint NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(50) NOT NULL,
    field1 character varying(100),
    field2 character varying(100),
    field3 character varying(100),
    field4 character varying(100),
    field5 character varying(100)
);


--
-- Name: fx_rates_rate_id_seq; Type: SEQUENCE; Schema: tenant_e2etest; Owner: -
--

CREATE SEQUENCE tenant_e2etest.fx_rates_rate_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: fx_rates_rate_id_seq; Type: SEQUENCE OWNED BY; Schema: tenant_e2etest; Owner: -
--

ALTER SEQUENCE tenant_e2etest.fx_rates_rate_id_seq OWNED BY tenant_e2etest.fx_rates.rate_id;


--
-- Name: kyc_document; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.kyc_document (
    document_id bigint NOT NULL,
    account_id text NOT NULL,
    document_type character varying(50) NOT NULL,
    document_number character varying(100) NOT NULL,
    issue_date date,
    expiry_date date,
    document_url text NOT NULL,
    verification_status character varying(50) DEFAULT 'PENDING'::character varying NOT NULL,
    verified_by character varying(255),
    verified_at timestamp without time zone,
    rejection_reason text,
    is_primary boolean DEFAULT false,
    is_active boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    stored_document_id uuid
);


--
-- Name: kyc_document_document_id_seq; Type: SEQUENCE; Schema: tenant_e2etest; Owner: -
--

CREATE SEQUENCE tenant_e2etest.kyc_document_document_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: kyc_document_document_id_seq; Type: SEQUENCE OWNED BY; Schema: tenant_e2etest; Owner: -
--

ALTER SEQUENCE tenant_e2etest.kyc_document_document_id_seq OWNED BY tenant_e2etest.kyc_document.document_id;


--
-- Name: notification_outbox; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.notification_outbox (
    notification_id bigint NOT NULL,
    transaction_id character varying(30),
    account_id character varying(100),
    party_role character varying(20),
    channel character varying(50) NOT NULL,
    recipient character varying(2000) NOT NULL,
    recipient_masked character varying(200),
    template_code character varying(200),
    subject character varying(500),
    title character varying(500),
    notification_text text NOT NULL,
    payload jsonb,
    status character varying(30) DEFAULT 'PENDING'::character varying NOT NULL,
    attempt_count integer DEFAULT 0 NOT NULL,
    next_attempt_at timestamp without time zone,
    last_error character varying(1000),
    service_code character varying(15),
    transfer_status character varying(10),
    trace_id character varying(100),
    created_on timestamp without time zone DEFAULT now() NOT NULL,
    modified_on timestamp without time zone DEFAULT now() NOT NULL,
    sent_on timestamp without time zone,
    version bigint
);


--
-- Name: notification_outbox_notification_id_seq; Type: SEQUENCE; Schema: tenant_e2etest; Owner: -
--

CREATE SEQUENCE tenant_e2etest.notification_outbox_notification_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: notification_outbox_notification_id_seq; Type: SEQUENCE OWNED BY; Schema: tenant_e2etest; Owner: -
--

ALTER SEQUENCE tenant_e2etest.notification_outbox_notification_id_seq OWNED BY tenant_e2etest.notification_outbox.notification_id;


--
-- Name: notification_template; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.notification_template (
    template_id bigint NOT NULL,
    template_code character varying(200) NOT NULL,
    template_definition jsonb NOT NULL,
    status character varying(30) DEFAULT 'ACTIVE'::character varying NOT NULL,
    description character varying(500),
    created_by character varying(100),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: notification_template_template_id_seq; Type: SEQUENCE; Schema: tenant_e2etest; Owner: -
--

CREATE SEQUENCE tenant_e2etest.notification_template_template_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: notification_template_template_id_seq; Type: SEQUENCE OWNED BY; Schema: tenant_e2etest; Owner: -
--

ALTER SEQUENCE tenant_e2etest.notification_template_template_id_seq OWNED BY tenant_e2etest.notification_template.template_id;


--
-- Name: otp; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.otp (
    otp_id bigint NOT NULL,
    reference_type character varying(30) NOT NULL,
    reference_id character varying(100),
    mobile_number character varying(20),
    otp_value integer,
    status character varying(20),
    attempt_count integer DEFAULT 0,
    max_attempt integer DEFAULT 3,
    expires_at timestamp without time zone NOT NULL,
    verified_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone
);


--
-- Name: otp_otp_id_seq; Type: SEQUENCE; Schema: tenant_e2etest; Owner: -
--

CREATE SEQUENCE tenant_e2etest.otp_otp_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: otp_otp_id_seq; Type: SEQUENCE OWNED BY; Schema: tenant_e2etest; Owner: -
--

ALTER SEQUENCE tenant_e2etest.otp_otp_id_seq OWNED BY tenant_e2etest.otp.otp_id;


--
-- Name: passcode; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.passcode (
    passcode_id bigint NOT NULL,
    transaction_id character varying(30) NOT NULL,
    cashout_transaction_id character varying(30),
    amount numeric(19,0) NOT NULL,
    currency character varying(10) NOT NULL,
    unregistered_msisdn character varying(30) NOT NULL,
    first_name character varying(100),
    last_name character varying(100),
    kyc_document_id character varying(100),
    sender_msisdn character varying(30),
    sender_account_id character varying(30) NOT NULL,
    passcode character varying(10) NOT NULL,
    status character varying(20) NOT NULL,
    created_on timestamp without time zone NOT NULL,
    modified_on timestamp without time zone NOT NULL,
    redeemed_on timestamp without time zone,
    field1 character varying(250),
    field2 character varying(250),
    field3 character varying(250),
    field4 character varying(250),
    field5 character varying(250),
    version bigint
);


--
-- Name: passcode_passcode_id_seq; Type: SEQUENCE; Schema: tenant_e2etest; Owner: -
--

CREATE SEQUENCE tenant_e2etest.passcode_passcode_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: passcode_passcode_id_seq; Type: SEQUENCE OWNED BY; Schema: tenant_e2etest; Owner: -
--

ALTER SEQUENCE tenant_e2etest.passcode_passcode_id_seq OWNED BY tenant_e2etest.passcode.passcode_id;


--
-- Name: permissions; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.permissions (
    permission_id bigint NOT NULL,
    permission_code character varying(100) NOT NULL,
    module character varying(50),
    description text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: permissions_permission_id_seq; Type: SEQUENCE; Schema: tenant_e2etest; Owner: -
--

CREATE SEQUENCE tenant_e2etest.permissions_permission_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: permissions_permission_id_seq; Type: SEQUENCE OWNED BY; Schema: tenant_e2etest; Owner: -
--

ALTER SEQUENCE tenant_e2etest.permissions_permission_id_seq OWNED BY tenant_e2etest.permissions.permission_id;


--
-- Name: pricing_rules; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.pricing_rules (
    id bigint NOT NULL,
    pricing_name character varying(100) NOT NULL,
    service_code character varying(50) NOT NULL,
    rule_type character varying(30) NOT NULL,
    pricing_type character varying(20),
    payer character varying(20) NOT NULL,
    pay_by character varying(20),
    payer_split jsonb,
    sender_tag_key character varying(255) NOT NULL,
    receiver_tag_key character varying(255) NOT NULL,
    currency character varying(10) NOT NULL,
    pricing_config jsonb NOT NULL,
    status character varying(50) NOT NULL,
    valid_from timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    valid_to timestamp without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone,
    created_by character varying(255),
    updated_by character varying(255)
);


--
-- Name: pricing_rules_id_seq; Type: SEQUENCE; Schema: tenant_e2etest; Owner: -
--

CREATE SEQUENCE tenant_e2etest.pricing_rules_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pricing_rules_id_seq; Type: SEQUENCE OWNED BY; Schema: tenant_e2etest; Owner: -
--

ALTER SEQUENCE tenant_e2etest.pricing_rules_id_seq OWNED BY tenant_e2etest.pricing_rules.id;


--
-- Name: qr_payment_intent; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.qr_payment_intent (
    qr_intent_id character varying(40) NOT NULL,
    operation_type character varying(20) NOT NULL,
    creditor_identifier_type character varying(30) NOT NULL,
    creditor_identifier_value character varying(30) NOT NULL,
    creditor_account_type character varying(30) NOT NULL,
    creditor_wallet_type character varying(50) NOT NULL,
    currency character varying(10) NOT NULL,
    amount numeric(19,2),
    status character varying(20) NOT NULL,
    expires_at timestamp without time zone NOT NULL,
    transaction_id character varying(30),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone,
    version bigint
);


--
-- Name: recent_recipients; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.recent_recipients (
    account_id character varying(30) NOT NULL,
    recipient_account_id character varying(30) NOT NULL,
    service_code character varying(15) NOT NULL,
    currency character varying(10) NOT NULL,
    wallet_type character varying(50) NOT NULL,
    recipient_account_type character varying(50),
    recipient_identifier_type character varying(30),
    recipient_identifier_value character varying(100),
    recipient_display_name character varying(200),
    last_transaction_id character varying(30),
    last_paid_at timestamp without time zone NOT NULL,
    payment_count bigint DEFAULT 1 NOT NULL,
    field1 character varying(250),
    field2 character varying(250),
    field3 character varying(250),
    field4 character varying(250),
    field5 character varying(250),
    created_on timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    modified_on timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: role_permissions; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.role_permissions (
    id bigint NOT NULL,
    role_id bigint NOT NULL,
    permission_id bigint NOT NULL
);


--
-- Name: role_permissions_id_seq; Type: SEQUENCE; Schema: tenant_e2etest; Owner: -
--

CREATE SEQUENCE tenant_e2etest.role_permissions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: role_permissions_id_seq; Type: SEQUENCE OWNED BY; Schema: tenant_e2etest; Owner: -
--

ALTER SEQUENCE tenant_e2etest.role_permissions_id_seq OWNED BY tenant_e2etest.role_permissions.id;


--
-- Name: roles; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.roles (
    role_id bigint NOT NULL,
    role_code character varying(50) NOT NULL,
    role_name character varying(100) NOT NULL,
    role_type character varying(30) NOT NULL,
    description text,
    status character varying(20) DEFAULT 'ACTIVE'::character varying,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: roles_role_id_seq; Type: SEQUENCE; Schema: tenant_e2etest; Owner: -
--

CREATE SEQUENCE tenant_e2etest.roles_role_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: roles_role_id_seq; Type: SEQUENCE OWNED BY; Schema: tenant_e2etest; Owner: -
--

ALTER SEQUENCE tenant_e2etest.roles_role_id_seq OWNED BY tenant_e2etest.roles.role_id;


--
-- Name: service_catalog; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.service_catalog (
    service_code character varying(50) NOT NULL,
    service_name character varying(100) NOT NULL,
    description character varying(255),
    service_category character varying(50),
    transaction_type character varying(50),
    is_financial boolean DEFAULT true NOT NULL,
    send_to_integrator boolean DEFAULT false NOT NULL,
    requires_confirmation boolean DEFAULT false NOT NULL,
    integrator_call_mode character varying(20) DEFAULT 'SYNC'::character varying NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    display_order integer DEFAULT 0 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone
);


--
-- Name: stored_document; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.stored_document (
    document_id uuid NOT NULL,
    tenant_id character varying(100) NOT NULL,
    document_type_id bigint NOT NULL,
    document_name character varying(255) NOT NULL,
    original_file_name character varying(255) NOT NULL,
    content_type character varying(150) NOT NULL,
    file_size_bytes bigint NOT NULL,
    checksum_sha256 character varying(64),
    gridfs_bucket_name character varying(100) DEFAULT 'fs'::character varying NOT NULL,
    gridfs_file_id character varying(64) NOT NULL,
    thumbnail_gridfs_file_id character varying(64),
    thumbnail_content_type character varying(150),
    thumbnail_size_bytes bigint,
    status character varying(30) DEFAULT 'ACTIVE'::character varying NOT NULL,
    uploaded_by character varying(100) NOT NULL,
    uploaded_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted_at timestamp without time zone,
    CONSTRAINT chk_stored_document_size CHECK ((file_size_bytes >= 0)),
    CONSTRAINT chk_stored_document_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'DELETED'::character varying, 'QUARANTINED'::character varying, 'UPLOAD_FAILED'::character varying])::text[]))),
    CONSTRAINT chk_stored_document_thumbnail_size CHECK (((thumbnail_size_bytes IS NULL) OR (thumbnail_size_bytes >= 0)))
);


--
-- Name: supported_languages; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.supported_languages (
    id bigint NOT NULL,
    language_code character varying(10) NOT NULL,
    language_name character varying(100) NOT NULL,
    display_order integer DEFAULT 0,
    is_default boolean DEFAULT false,
    is_active boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone
);


--
-- Name: supported_languages_id_seq; Type: SEQUENCE; Schema: tenant_e2etest; Owner: -
--

CREATE SEQUENCE tenant_e2etest.supported_languages_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: supported_languages_id_seq; Type: SEQUENCE OWNED BY; Schema: tenant_e2etest; Owner: -
--

ALTER SEQUENCE tenant_e2etest.supported_languages_id_seq OWNED BY tenant_e2etest.supported_languages.id;


--
-- Name: tag_types; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.tag_types (
    tag_type_id bigint NOT NULL,
    type_code character varying(50) NOT NULL,
    type_name character varying(100) NOT NULL,
    description text,
    status text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: tag_types_tag_type_id_seq; Type: SEQUENCE; Schema: tenant_e2etest; Owner: -
--

CREATE SEQUENCE tenant_e2etest.tag_types_tag_type_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tag_types_tag_type_id_seq; Type: SEQUENCE OWNED BY; Schema: tenant_e2etest; Owner: -
--

ALTER SEQUENCE tenant_e2etest.tag_types_tag_type_id_seq OWNED BY tenant_e2etest.tag_types.tag_type_id;


--
-- Name: tags; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.tags (
    tag_id bigint NOT NULL,
    tag_code character varying(50) NOT NULL,
    tag_name character varying(100) NOT NULL,
    category character varying(50),
    is_default boolean DEFAULT false NOT NULL,
    tag_type character varying(50),
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: tags_tag_id_seq; Type: SEQUENCE; Schema: tenant_e2etest; Owner: -
--

CREATE SEQUENCE tenant_e2etest.tags_tag_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tags_tag_id_seq; Type: SEQUENCE OWNED BY; Schema: tenant_e2etest; Owner: -
--

ALTER SEQUENCE tenant_e2etest.tags_tag_id_seq OWNED BY tenant_e2etest.tags.tag_id;


--
-- Name: third_party_response; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.third_party_response (
    id bigint NOT NULL,
    transaction_id character varying(50) NOT NULL,
    service_code character varying(50),
    integrator_name character varying(100),
    request_body jsonb NOT NULL,
    response_body jsonb,
    status character varying(30) DEFAULT 'PENDING'::character varying NOT NULL,
    error_message text,
    created_on timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    modified_on timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: third_party_response_id_seq; Type: SEQUENCE; Schema: tenant_e2etest; Owner: -
--

CREATE SEQUENCE tenant_e2etest.third_party_response_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: third_party_response_id_seq; Type: SEQUENCE OWNED BY; Schema: tenant_e2etest; Owner: -
--

ALTER SEQUENCE tenant_e2etest.third_party_response_id_seq OWNED BY tenant_e2etest.third_party_response.id;


--
-- Name: transaction_details; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.transaction_details (
    transaction_id character varying(30) NOT NULL,
    txn_sequence_number bigint NOT NULL,
    account_id character varying(30) NOT NULL,
    user_type character varying(10) NOT NULL,
    entry_type character varying(5) NOT NULL,
    identifier_id character varying(80) NOT NULL,
    second_identifier_id character varying(80) NOT NULL,
    transaction_value numeric(19,0),
    approved_value numeric(19,0),
    previous_balance numeric(19,0),
    post_balance numeric(19,0),
    transfer_on timestamp without time zone,
    service_code character varying(15) NOT NULL,
    transfer_status character varying(3),
    wallet_number character varying(25),
    wallet_type character varying(50),
    currency character varying(10),
    transaction_type character varying(50),
    previous_fic_balance numeric(19,0),
    post_fic_balance numeric(19,0),
    previous_frozen_balance numeric(19,0),
    post_frozen_balance numeric(19,0),
    attr_1_name character varying(255),
    attr_1_value character varying(255),
    attr_2_name character varying(255),
    attr_2_value character varying(255),
    attr_3_name character varying(255),
    attr_3_value character varying(255),
    attr_4_name character varying(255),
    attr_4_value character varying(255),
    attr_5_name character varying(255),
    attr_5_value character varying(255),
    attr_6_name character varying(255),
    attr_6_value character varying(255)
);


--
-- Name: transaction_limit_profile; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.transaction_limit_profile (
    limit_id bigint NOT NULL,
    limit_name character varying(150) NOT NULL,
    tag_id bigint NOT NULL,
    limit_type character varying(20) NOT NULL,
    subject_key character varying(50) NOT NULL,
    details jsonb,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    wallet_type character varying(50) NOT NULL,
    currency character varying(10) NOT NULL,
    min_residual_balance numeric(19,0),
    max_balance numeric(19,0),
    created_by character varying(100),
    created_on timestamp without time zone DEFAULT now() NOT NULL,
    modified_by character varying(100),
    modified_on timestamp without time zone DEFAULT now() NOT NULL,
    version bigint
);


--
-- Name: transaction_limit_profile_details; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.transaction_limit_profile_details (
    limit_details_id bigint NOT NULL,
    limit_id bigint NOT NULL,
    party_type character varying(20) NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    operation_type character varying(50) DEFAULT 'ALL'::character varying NOT NULL,
    request_gateway character varying(50) DEFAULT 'ALL'::character varying NOT NULL,
    min_txn_amount numeric(19,0),
    max_txn_amount numeric(19,0),
    created_on timestamp without time zone DEFAULT now() NOT NULL,
    modified_on timestamp without time zone DEFAULT now() NOT NULL,
    version bigint
);


--
-- Name: transaction_limit_profile_details_limit_details_id_seq; Type: SEQUENCE; Schema: tenant_e2etest; Owner: -
--

CREATE SEQUENCE tenant_e2etest.transaction_limit_profile_details_limit_details_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: transaction_limit_profile_details_limit_details_id_seq; Type: SEQUENCE OWNED BY; Schema: tenant_e2etest; Owner: -
--

ALTER SEQUENCE tenant_e2etest.transaction_limit_profile_details_limit_details_id_seq OWNED BY tenant_e2etest.transaction_limit_profile_details.limit_details_id;


--
-- Name: transaction_limit_profile_limit_id_seq; Type: SEQUENCE; Schema: tenant_e2etest; Owner: -
--

CREATE SEQUENCE tenant_e2etest.transaction_limit_profile_limit_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: transaction_limit_profile_limit_id_seq; Type: SEQUENCE OWNED BY; Schema: tenant_e2etest; Owner: -
--

ALTER SEQUENCE tenant_e2etest.transaction_limit_profile_limit_id_seq OWNED BY tenant_e2etest.transaction_limit_profile.limit_id;


--
-- Name: transaction_limit_profile_period; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.transaction_limit_profile_period (
    limit_period_id bigint NOT NULL,
    limit_details_id bigint NOT NULL,
    period_type character varying(20) NOT NULL,
    max_count integer,
    max_amount numeric(19,0),
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_on timestamp without time zone DEFAULT now() NOT NULL,
    modified_on timestamp without time zone DEFAULT now() NOT NULL,
    version bigint
);


--
-- Name: transaction_limit_profile_period_limit_period_id_seq; Type: SEQUENCE; Schema: tenant_e2etest; Owner: -
--

CREATE SEQUENCE tenant_e2etest.transaction_limit_profile_period_limit_period_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: transaction_limit_profile_period_limit_period_id_seq; Type: SEQUENCE OWNED BY; Schema: tenant_e2etest; Owner: -
--

ALTER SEQUENCE tenant_e2etest.transaction_limit_profile_period_limit_period_id_seq OWNED BY tenant_e2etest.transaction_limit_profile_period.limit_period_id;


--
-- Name: transaction_limit_usage; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.transaction_limit_usage (
    usage_id bigint NOT NULL,
    subject_key character varying(50) NOT NULL,
    subject_value character varying(200) NOT NULL,
    account_id character varying(100),
    limit_id bigint NOT NULL,
    limit_details_id bigint NOT NULL,
    tag_id bigint NOT NULL,
    period_type character varying(20) NOT NULL,
    operation_type character varying(50) NOT NULL,
    request_gateway character varying(50) NOT NULL,
    payer_count integer DEFAULT 0 NOT NULL,
    payer_amount numeric(19,0) DEFAULT 0 NOT NULL,
    payee_count integer DEFAULT 0 NOT NULL,
    payee_amount numeric(19,0) DEFAULT 0 NOT NULL,
    last_transaction_id character varying(30),
    last_transaction_date timestamp without time zone
);


--
-- Name: transaction_limit_usage_usage_id_seq; Type: SEQUENCE; Schema: tenant_e2etest; Owner: -
--

CREATE SEQUENCE tenant_e2etest.transaction_limit_usage_usage_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: transaction_limit_usage_usage_id_seq; Type: SEQUENCE OWNED BY; Schema: tenant_e2etest; Owner: -
--

ALTER SEQUENCE tenant_e2etest.transaction_limit_usage_usage_id_seq OWNED BY tenant_e2etest.transaction_limit_usage.usage_id;


--
-- Name: transactions; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.transactions (
    transaction_id character varying(30) NOT NULL,
    transfer_on timestamp without time zone,
    transaction_value numeric(19,0),
    error_code character varying(200),
    transfer_status character varying(3),
    request_gateway character varying(10),
    service_code character varying(15),
    trace_id character varying(50),
    payment_reference character varying(100),
    reconciliation_done character varying(3),
    reconciliation_date timestamp without time zone,
    reconciliation_by character varying(30),
    language character varying(10),
    country character varying(20),
    created_by character varying(30) NOT NULL,
    created_on timestamp without time zone NOT NULL,
    modified_by character varying(30) NOT NULL,
    modified_on timestamp without time zone NOT NULL,
    comments character varying(300),
    debitor_account_id character varying(30),
    creditor_account_id character varying(30),
    debitor_wallet_type character varying(50),
    debitor_currency character varying(10),
    creditor_wallet_type character varying(50),
    creditor_currency character varying(10),
    fees_details character varying(4000),
    additional_info character varying(4000),
    metadata character varying(4000),
    attr_1_name character varying(255),
    attr_1_value character varying(255),
    attr_2_name character varying(255),
    attr_2_value character varying(255),
    attr_3_name character varying(255),
    attr_3_value character varying(255),
    attr_4_name character varying(255),
    attr_4_value character varying(255),
    attr_5_name character varying(255),
    attr_5_value character varying(255),
    attr_6_name character varying(255),
    attr_6_value character varying(255),
    field1 character varying(100),
    field2 character varying(100),
    field3 character varying(100),
    field4 character varying(100),
    field5 character varying(100),
    field6 character varying(100),
    field7 character varying(100),
    field8 character varying(100),
    field9 character varying(100),
    field10 character varying(100),
    core_service_code character varying(100),
    debitor_identifier_type character varying(30),
    debitor_identifier_value character varying(30),
    creditor_identifier_type character varying(30),
    creditor_identifier_value character varying(30),
    previous_status character varying(5),
    payment_via_qr boolean DEFAULT false NOT NULL
);


--
-- Name: user_roles; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.user_roles (
    id bigint NOT NULL,
    user_id text NOT NULL,
    role_id bigint NOT NULL,
    assigned_by character varying(50),
    assigned_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: user_roles_id_seq; Type: SEQUENCE; Schema: tenant_e2etest; Owner: -
--

CREATE SEQUENCE tenant_e2etest.user_roles_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: user_roles_id_seq; Type: SEQUENCE OWNED BY; Schema: tenant_e2etest; Owner: -
--

ALTER SEQUENCE tenant_e2etest.user_roles_id_seq OWNED BY tenant_e2etest.user_roles.id;


--
-- Name: wallet_wallet_id_seq; Type: SEQUENCE; Schema: tenant_e2etest; Owner: -
--

CREATE SEQUENCE tenant_e2etest.wallet_wallet_id_seq
    START WITH 10000000000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: wallet; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.wallet (
    wallet_id bigint DEFAULT nextval('tenant_e2etest.wallet_wallet_id_seq'::regclass) NOT NULL,
    account_id text NOT NULL,
    currency character varying(10) NOT NULL,
    wallet_type character varying(50) NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying,
    is_default boolean DEFAULT false,
    is_locked boolean DEFAULT false,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone,
    remarks text
);


--
-- Name: wallet_balance; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.wallet_balance (
    wallet_id bigint NOT NULL,
    available_balance numeric(19,0) DEFAULT 0 NOT NULL,
    frozen_balance numeric(19,0) DEFAULT 0 NOT NULL,
    fic_balance numeric(19,0) DEFAULT 0 NOT NULL,
    version bigint DEFAULT 0,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: wallet_ledger; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.wallet_ledger (
    ledger_id bigint NOT NULL,
    txn_id character varying(255) NOT NULL,
    wallet_id bigint NOT NULL,
    account_id text NOT NULL,
    entry_type character varying(2) NOT NULL,
    amount numeric(19,0) NOT NULL,
    currency character varying(10) NOT NULL,
    balance_before numeric(19,0),
    balance_after numeric(19,0),
    txn_type character varying(255),
    reference_type character varying(255),
    reference_id character varying(255),
    description text,
    attr1 text,
    attr2 text,
    attr3 text,
    attr4 text,
    attr5 text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: wallet_ledger_ledger_id_seq; Type: SEQUENCE; Schema: tenant_e2etest; Owner: -
--

CREATE SEQUENCE tenant_e2etest.wallet_ledger_ledger_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: wallet_ledger_ledger_id_seq; Type: SEQUENCE OWNED BY; Schema: tenant_e2etest; Owner: -
--

ALTER SEQUENCE tenant_e2etest.wallet_ledger_ledger_id_seq OWNED BY tenant_e2etest.wallet_ledger.ledger_id;


--
-- Name: wallet_restriction; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.wallet_restriction (
    wallet_id bigint NOT NULL,
    restrictions jsonb NOT NULL,
    version bigint DEFAULT 0,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_by character varying(100)
);


--
-- Name: wallet_restriction_history; Type: TABLE; Schema: tenant_e2etest; Owner: -
--

CREATE TABLE tenant_e2etest.wallet_restriction_history (
    history_id bigint NOT NULL,
    wallet_id bigint NOT NULL,
    version bigint NOT NULL,
    restrictions jsonb NOT NULL,
    action_type character varying(50),
    changed_by character varying(100),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: wallet_restriction_history_history_id_seq; Type: SEQUENCE; Schema: tenant_e2etest; Owner: -
--

CREATE SEQUENCE tenant_e2etest.wallet_restriction_history_history_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: wallet_restriction_history_history_id_seq; Type: SEQUENCE OWNED BY; Schema: tenant_e2etest; Owner: -
--

ALTER SEQUENCE tenant_e2etest.wallet_restriction_history_history_id_seq OWNED BY tenant_e2etest.wallet_restriction_history.history_id;


--
-- Name: audit_api_logs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_api_logs ALTER COLUMN id SET DEFAULT nextval('public.audit_api_logs_id_seq'::regclass);


--
-- Name: system_config config_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_config ALTER COLUMN config_id SET DEFAULT nextval('public.system_config_config_id_seq'::regclass);


--
-- Name: account_biller_info biller_info_id; Type: DEFAULT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.account_biller_info ALTER COLUMN biller_info_id SET DEFAULT nextval('tenant_e2etest.account_biller_info_biller_info_id_seq'::regclass);


--
-- Name: account_identifiers id; Type: DEFAULT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.account_identifiers ALTER COLUMN id SET DEFAULT nextval('tenant_e2etest.account_identifiers_id_seq'::regclass);


--
-- Name: account_merchant_info merchant_info_id; Type: DEFAULT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.account_merchant_info ALTER COLUMN merchant_info_id SET DEFAULT nextval('tenant_e2etest.account_merchant_info_merchant_info_id_seq'::regclass);


--
-- Name: account_merchant_mcc merchant_mcc_id; Type: DEFAULT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.account_merchant_mcc ALTER COLUMN merchant_mcc_id SET DEFAULT nextval('tenant_e2etest.account_merchant_mcc_merchant_mcc_id_seq'::regclass);


--
-- Name: account_notification_endpoint account_endpoint_id; Type: DEFAULT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.account_notification_endpoint ALTER COLUMN account_endpoint_id SET DEFAULT nextval('tenant_e2etest.account_notification_endpoint_account_endpoint_id_seq'::regclass);


--
-- Name: account_status_history history_id; Type: DEFAULT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.account_status_history ALTER COLUMN history_id SET DEFAULT nextval('tenant_e2etest.account_status_history_history_id_seq'::regclass);


--
-- Name: account_tags id; Type: DEFAULT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.account_tags ALTER COLUMN id SET DEFAULT nextval('tenant_e2etest.account_tags_id_seq'::regclass);


--
-- Name: audit_api_log id; Type: DEFAULT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.audit_api_log ALTER COLUMN id SET DEFAULT nextval('tenant_e2etest.audit_api_log_id_seq'::regclass);


--
-- Name: cashback_payout cashback_payout_id; Type: DEFAULT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.cashback_payout ALTER COLUMN cashback_payout_id SET DEFAULT nextval('tenant_e2etest.cashback_payout_cashback_payout_id_seq'::regclass);


--
-- Name: categories category_id; Type: DEFAULT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.categories ALTER COLUMN category_id SET DEFAULT nextval('tenant_e2etest.categories_category_id_seq'::regclass);


--
-- Name: city id; Type: DEFAULT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.city ALTER COLUMN id SET DEFAULT nextval('tenant_e2etest.city_id_seq'::regclass);


--
-- Name: country_subdivision id; Type: DEFAULT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.country_subdivision ALTER COLUMN id SET DEFAULT nextval('tenant_e2etest.country_subdivision_id_seq'::regclass);


--
-- Name: document_category category_id; Type: DEFAULT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.document_category ALTER COLUMN category_id SET DEFAULT nextval('tenant_e2etest.document_category_category_id_seq'::regclass);


--
-- Name: document_reference document_reference_id; Type: DEFAULT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.document_reference ALTER COLUMN document_reference_id SET DEFAULT nextval('tenant_e2etest.document_reference_document_reference_id_seq'::regclass);


--
-- Name: document_type document_type_id; Type: DEFAULT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.document_type ALTER COLUMN document_type_id SET DEFAULT nextval('tenant_e2etest.document_type_document_type_id_seq'::regclass);


--
-- Name: enumerations id; Type: DEFAULT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.enumerations ALTER COLUMN id SET DEFAULT nextval('tenant_e2etest.enumerations_id_seq'::regclass);


--
-- Name: fx_rates rate_id; Type: DEFAULT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.fx_rates ALTER COLUMN rate_id SET DEFAULT nextval('tenant_e2etest.fx_rates_rate_id_seq'::regclass);


--
-- Name: kyc_document document_id; Type: DEFAULT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.kyc_document ALTER COLUMN document_id SET DEFAULT nextval('tenant_e2etest.kyc_document_document_id_seq'::regclass);


--
-- Name: notification_outbox notification_id; Type: DEFAULT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.notification_outbox ALTER COLUMN notification_id SET DEFAULT nextval('tenant_e2etest.notification_outbox_notification_id_seq'::regclass);


--
-- Name: notification_template template_id; Type: DEFAULT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.notification_template ALTER COLUMN template_id SET DEFAULT nextval('tenant_e2etest.notification_template_template_id_seq'::regclass);


--
-- Name: otp otp_id; Type: DEFAULT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.otp ALTER COLUMN otp_id SET DEFAULT nextval('tenant_e2etest.otp_otp_id_seq'::regclass);


--
-- Name: passcode passcode_id; Type: DEFAULT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.passcode ALTER COLUMN passcode_id SET DEFAULT nextval('tenant_e2etest.passcode_passcode_id_seq'::regclass);


--
-- Name: permissions permission_id; Type: DEFAULT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.permissions ALTER COLUMN permission_id SET DEFAULT nextval('tenant_e2etest.permissions_permission_id_seq'::regclass);


--
-- Name: pricing_rules id; Type: DEFAULT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.pricing_rules ALTER COLUMN id SET DEFAULT nextval('tenant_e2etest.pricing_rules_id_seq'::regclass);


--
-- Name: role_permissions id; Type: DEFAULT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.role_permissions ALTER COLUMN id SET DEFAULT nextval('tenant_e2etest.role_permissions_id_seq'::regclass);


--
-- Name: roles role_id; Type: DEFAULT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.roles ALTER COLUMN role_id SET DEFAULT nextval('tenant_e2etest.roles_role_id_seq'::regclass);


--
-- Name: supported_languages id; Type: DEFAULT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.supported_languages ALTER COLUMN id SET DEFAULT nextval('tenant_e2etest.supported_languages_id_seq'::regclass);


--
-- Name: tag_types tag_type_id; Type: DEFAULT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.tag_types ALTER COLUMN tag_type_id SET DEFAULT nextval('tenant_e2etest.tag_types_tag_type_id_seq'::regclass);


--
-- Name: tags tag_id; Type: DEFAULT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.tags ALTER COLUMN tag_id SET DEFAULT nextval('tenant_e2etest.tags_tag_id_seq'::regclass);


--
-- Name: third_party_response id; Type: DEFAULT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.third_party_response ALTER COLUMN id SET DEFAULT nextval('tenant_e2etest.third_party_response_id_seq'::regclass);


--
-- Name: transaction_limit_profile limit_id; Type: DEFAULT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.transaction_limit_profile ALTER COLUMN limit_id SET DEFAULT nextval('tenant_e2etest.transaction_limit_profile_limit_id_seq'::regclass);


--
-- Name: transaction_limit_profile_details limit_details_id; Type: DEFAULT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.transaction_limit_profile_details ALTER COLUMN limit_details_id SET DEFAULT nextval('tenant_e2etest.transaction_limit_profile_details_limit_details_id_seq'::regclass);


--
-- Name: transaction_limit_profile_period limit_period_id; Type: DEFAULT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.transaction_limit_profile_period ALTER COLUMN limit_period_id SET DEFAULT nextval('tenant_e2etest.transaction_limit_profile_period_limit_period_id_seq'::regclass);


--
-- Name: transaction_limit_usage usage_id; Type: DEFAULT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.transaction_limit_usage ALTER COLUMN usage_id SET DEFAULT nextval('tenant_e2etest.transaction_limit_usage_usage_id_seq'::regclass);


--
-- Name: user_roles id; Type: DEFAULT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.user_roles ALTER COLUMN id SET DEFAULT nextval('tenant_e2etest.user_roles_id_seq'::regclass);


--
-- Name: wallet_ledger ledger_id; Type: DEFAULT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.wallet_ledger ALTER COLUMN ledger_id SET DEFAULT nextval('tenant_e2etest.wallet_ledger_ledger_id_seq'::regclass);


--
-- Name: wallet_restriction_history history_id; Type: DEFAULT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.wallet_restriction_history ALTER COLUMN history_id SET DEFAULT nextval('tenant_e2etest.wallet_restriction_history_history_id_seq'::regclass);


--
-- Name: audit_api_logs audit_api_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_api_logs
    ADD CONSTRAINT audit_api_logs_pkey PRIMARY KEY (id);


--
-- Name: system_config system_config_config_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_config
    ADD CONSTRAINT system_config_config_key_key UNIQUE (config_key);


--
-- Name: system_config system_config_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_config
    ADD CONSTRAINT system_config_pkey PRIMARY KEY (config_id);


--
-- Name: tenant_registry tenant_registry_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_registry
    ADD CONSTRAINT tenant_registry_pkey PRIMARY KEY (tenant_id);


--
-- Name: account_auth account_auth_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.account_auth
    ADD CONSTRAINT account_auth_pkey PRIMARY KEY (auth_id);


--
-- Name: account_biller_info account_biller_info_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.account_biller_info
    ADD CONSTRAINT account_biller_info_pkey PRIMARY KEY (biller_info_id);


--
-- Name: account_identifiers account_identifiers_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.account_identifiers
    ADD CONSTRAINT account_identifiers_pkey PRIMARY KEY (id);


--
-- Name: account_merchant_info account_merchant_info_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.account_merchant_info
    ADD CONSTRAINT account_merchant_info_pkey PRIMARY KEY (merchant_info_id);


--
-- Name: account_merchant_mcc account_merchant_mcc_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.account_merchant_mcc
    ADD CONSTRAINT account_merchant_mcc_pkey PRIMARY KEY (merchant_mcc_id);


--
-- Name: account_notification_endpoint account_notification_endpoint_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.account_notification_endpoint
    ADD CONSTRAINT account_notification_endpoint_pkey PRIMARY KEY (account_endpoint_id);


--
-- Name: account account_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.account
    ADD CONSTRAINT account_pkey PRIMARY KEY (account_id);


--
-- Name: account_status_history account_status_history_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.account_status_history
    ADD CONSTRAINT account_status_history_pkey PRIMARY KEY (history_id);


--
-- Name: account_tags account_tags_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.account_tags
    ADD CONSTRAINT account_tags_pkey PRIMARY KEY (id);


--
-- Name: audit_api_log audit_api_log_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.audit_api_log
    ADD CONSTRAINT audit_api_log_pkey PRIMARY KEY (id);


--
-- Name: auth_challenge auth_challenge_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.auth_challenge
    ADD CONSTRAINT auth_challenge_pkey PRIMARY KEY (challenge_id);


--
-- Name: bill_payment_status bill_payment_status_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.bill_payment_status
    ADD CONSTRAINT bill_payment_status_pkey PRIMARY KEY (transaction_id);


--
-- Name: cashback_payout cashback_payout_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.cashback_payout
    ADD CONSTRAINT cashback_payout_pkey PRIMARY KEY (cashback_payout_id);


--
-- Name: categories categories_category_code_key; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.categories
    ADD CONSTRAINT categories_category_code_key UNIQUE (category_code);


--
-- Name: categories categories_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.categories
    ADD CONSTRAINT categories_pkey PRIMARY KEY (category_id);


--
-- Name: city city_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.city
    ADD CONSTRAINT city_pkey PRIMARY KEY (id);


--
-- Name: country_subdivision country_subdivision_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.country_subdivision
    ADD CONSTRAINT country_subdivision_pkey PRIMARY KEY (id);


--
-- Name: document_category document_category_category_code_key; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.document_category
    ADD CONSTRAINT document_category_category_code_key UNIQUE (category_code);


--
-- Name: document_category document_category_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.document_category
    ADD CONSTRAINT document_category_pkey PRIMARY KEY (category_id);


--
-- Name: document_reference document_reference_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.document_reference
    ADD CONSTRAINT document_reference_pkey PRIMARY KEY (document_reference_id);


--
-- Name: document_type_entity document_type_entity_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.document_type_entity
    ADD CONSTRAINT document_type_entity_pkey PRIMARY KEY (document_type_id, entity_type);


--
-- Name: document_type document_type_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.document_type
    ADD CONSTRAINT document_type_pkey PRIMARY KEY (document_type_id);


--
-- Name: document_type document_type_type_code_key; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.document_type
    ADD CONSTRAINT document_type_type_code_key UNIQUE (type_code);


--
-- Name: enumerations enumerations_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.enumerations
    ADD CONSTRAINT enumerations_pkey PRIMARY KEY (id);


--
-- Name: error_catalog error_catalog_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.error_catalog
    ADD CONSTRAINT error_catalog_pkey PRIMARY KEY (id);


--
-- Name: fx_rates fx_rates_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.fx_rates
    ADD CONSTRAINT fx_rates_pkey PRIMARY KEY (rate_id);


--
-- Name: kyc_document kyc_document_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.kyc_document
    ADD CONSTRAINT kyc_document_pkey PRIMARY KEY (document_id);


--
-- Name: notification_outbox notification_outbox_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.notification_outbox
    ADD CONSTRAINT notification_outbox_pkey PRIMARY KEY (notification_id);


--
-- Name: notification_template notification_template_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.notification_template
    ADD CONSTRAINT notification_template_pkey PRIMARY KEY (template_id);


--
-- Name: otp otp_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.otp
    ADD CONSTRAINT otp_pkey PRIMARY KEY (otp_id);


--
-- Name: passcode passcode_passcode_key; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.passcode
    ADD CONSTRAINT passcode_passcode_key UNIQUE (passcode);


--
-- Name: passcode passcode_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.passcode
    ADD CONSTRAINT passcode_pkey PRIMARY KEY (passcode_id);


--
-- Name: permissions permissions_permission_code_key; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.permissions
    ADD CONSTRAINT permissions_permission_code_key UNIQUE (permission_code);


--
-- Name: permissions permissions_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.permissions
    ADD CONSTRAINT permissions_pkey PRIMARY KEY (permission_id);


--
-- Name: pricing_rules pricing_rules_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.pricing_rules
    ADD CONSTRAINT pricing_rules_pkey PRIMARY KEY (id);


--
-- Name: qr_payment_intent qr_payment_intent_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.qr_payment_intent
    ADD CONSTRAINT qr_payment_intent_pkey PRIMARY KEY (qr_intent_id);


--
-- Name: recent_recipients recent_recipients_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.recent_recipients
    ADD CONSTRAINT recent_recipients_pkey PRIMARY KEY (account_id, recipient_account_id, service_code, currency, wallet_type);


--
-- Name: role_permissions role_permissions_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.role_permissions
    ADD CONSTRAINT role_permissions_pkey PRIMARY KEY (id);


--
-- Name: roles roles_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (role_id);


--
-- Name: roles roles_role_code_key; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.roles
    ADD CONSTRAINT roles_role_code_key UNIQUE (role_code);


--
-- Name: service_catalog service_catalog_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.service_catalog
    ADD CONSTRAINT service_catalog_pkey PRIMARY KEY (service_code);


--
-- Name: stored_document stored_document_gridfs_file_id_key; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.stored_document
    ADD CONSTRAINT stored_document_gridfs_file_id_key UNIQUE (gridfs_file_id);


--
-- Name: stored_document stored_document_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.stored_document
    ADD CONSTRAINT stored_document_pkey PRIMARY KEY (document_id);


--
-- Name: supported_languages supported_languages_language_code_key; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.supported_languages
    ADD CONSTRAINT supported_languages_language_code_key UNIQUE (language_code);


--
-- Name: supported_languages supported_languages_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.supported_languages
    ADD CONSTRAINT supported_languages_pkey PRIMARY KEY (id);


--
-- Name: tag_types tag_types_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.tag_types
    ADD CONSTRAINT tag_types_pkey PRIMARY KEY (tag_type_id);


--
-- Name: tag_types tag_types_type_code_key; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.tag_types
    ADD CONSTRAINT tag_types_type_code_key UNIQUE (type_code);


--
-- Name: tags tags_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.tags
    ADD CONSTRAINT tags_pkey PRIMARY KEY (tag_id);


--
-- Name: tags tags_tag_code_key; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.tags
    ADD CONSTRAINT tags_tag_code_key UNIQUE (tag_code);


--
-- Name: third_party_response third_party_response_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.third_party_response
    ADD CONSTRAINT third_party_response_pkey PRIMARY KEY (id);


--
-- Name: third_party_response third_party_response_transaction_id_key; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.third_party_response
    ADD CONSTRAINT third_party_response_transaction_id_key UNIQUE (transaction_id);


--
-- Name: transaction_details transaction_details_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.transaction_details
    ADD CONSTRAINT transaction_details_pkey PRIMARY KEY (transaction_id, txn_sequence_number);


--
-- Name: transaction_limit_profile_details transaction_limit_profile_details_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.transaction_limit_profile_details
    ADD CONSTRAINT transaction_limit_profile_details_pkey PRIMARY KEY (limit_details_id);


--
-- Name: transaction_limit_profile_period transaction_limit_profile_period_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.transaction_limit_profile_period
    ADD CONSTRAINT transaction_limit_profile_period_pkey PRIMARY KEY (limit_period_id);


--
-- Name: transaction_limit_profile transaction_limit_profile_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.transaction_limit_profile
    ADD CONSTRAINT transaction_limit_profile_pkey PRIMARY KEY (limit_id);


--
-- Name: transaction_limit_usage transaction_limit_usage_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.transaction_limit_usage
    ADD CONSTRAINT transaction_limit_usage_pkey PRIMARY KEY (usage_id);


--
-- Name: transactions transactions_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.transactions
    ADD CONSTRAINT transactions_pkey PRIMARY KEY (transaction_id);


--
-- Name: enumerations uk_enumerations_type_code; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.enumerations
    ADD CONSTRAINT uk_enumerations_type_code UNIQUE (enum_type, enum_code);


--
-- Name: role_permissions uk_role_permissions_role_permission; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.role_permissions
    ADD CONSTRAINT uk_role_permissions_role_permission UNIQUE (role_id, permission_id);


--
-- Name: user_roles uk_user_roles_user_role; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.user_roles
    ADD CONSTRAINT uk_user_roles_user_role UNIQUE (user_id, role_id);


--
-- Name: document_reference uq_document_reference; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.document_reference
    ADD CONSTRAINT uq_document_reference UNIQUE (document_id, entity_type, entity_id, reference_role);


--
-- Name: error_catalog uq_error_catalog_code_language; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.error_catalog
    ADD CONSTRAINT uq_error_catalog_code_language UNIQUE (error_code, language_code);


--
-- Name: fx_rates uq_fx_active; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.fx_rates
    ADD CONSTRAINT uq_fx_active UNIQUE (target_currency, version_no);


--
-- Name: user_roles user_roles_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.user_roles
    ADD CONSTRAINT user_roles_pkey PRIMARY KEY (id);


--
-- Name: wallet_balance wallet_balance_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.wallet_balance
    ADD CONSTRAINT wallet_balance_pkey PRIMARY KEY (wallet_id);


--
-- Name: wallet_ledger wallet_ledger_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.wallet_ledger
    ADD CONSTRAINT wallet_ledger_pkey PRIMARY KEY (ledger_id);


--
-- Name: wallet wallet_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.wallet
    ADD CONSTRAINT wallet_pkey PRIMARY KEY (wallet_id);


--
-- Name: wallet_restriction_history wallet_restriction_history_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.wallet_restriction_history
    ADD CONSTRAINT wallet_restriction_history_pkey PRIMARY KEY (history_id);


--
-- Name: wallet_restriction_history wallet_restriction_history_wallet_id_version_key; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.wallet_restriction_history
    ADD CONSTRAINT wallet_restriction_history_wallet_id_version_key UNIQUE (wallet_id, version);


--
-- Name: wallet_restriction wallet_restriction_pkey; Type: CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.wallet_restriction
    ADD CONSTRAINT wallet_restriction_pkey PRIMARY KEY (wallet_id);


--
-- Name: idx_audit_api_logs_reference_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_api_logs_reference_id ON public.audit_api_logs USING btree (reference_id);


--
-- Name: idx_audit_api_logs_service_code; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_api_logs_service_code ON public.audit_api_logs USING btree (service_code);


--
-- Name: idx_audit_api_logs_tenant_path_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_api_logs_tenant_path_created ON public.audit_api_logs USING btree (tenant_id, api_path, created_at DESC);


--
-- Name: idx_audit_api_logs_trace_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_api_logs_trace_id ON public.audit_api_logs USING btree (trace_id);


--
-- Name: idx_audit_api_logs_transaction_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_api_logs_transaction_id ON public.audit_api_logs USING btree (transaction_id);


--
-- Name: idx_account_merchant_mcc_code; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE INDEX idx_account_merchant_mcc_code ON tenant_e2etest.account_merchant_mcc USING btree (mcc_code);


--
-- Name: idx_account_notification_endpoint_account_type; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE INDEX idx_account_notification_endpoint_account_type ON tenant_e2etest.account_notification_endpoint USING btree (account_id, endpoint_type);


--
-- Name: idx_account_status_history_account; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE INDEX idx_account_status_history_account ON tenant_e2etest.account_status_history USING btree (account_id, performed_at DESC);


--
-- Name: idx_cashback_payout_due; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE INDEX idx_cashback_payout_due ON tenant_e2etest.cashback_payout USING btree (status, pay_at);


--
-- Name: idx_city_country; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE INDEX idx_city_country ON tenant_e2etest.city USING btree (country_id);


--
-- Name: idx_city_subdivision; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE INDEX idx_city_subdivision ON tenant_e2etest.city USING btree (subdivision_id);


--
-- Name: idx_country_subdivision_country; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE INDEX idx_country_subdivision_country ON tenant_e2etest.country_subdivision USING btree (country_id);


--
-- Name: idx_document_reference_document; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE INDEX idx_document_reference_document ON tenant_e2etest.document_reference USING btree (document_id);


--
-- Name: idx_document_reference_entity; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE INDEX idx_document_reference_entity ON tenant_e2etest.document_reference USING btree (entity_type, entity_id, is_active);


--
-- Name: idx_document_type_category; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE INDEX idx_document_type_category ON tenant_e2etest.document_type USING btree (category_id);


--
-- Name: idx_error_catalog_lookup; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE INDEX idx_error_catalog_lookup ON tenant_e2etest.error_catalog USING btree (error_code, language_code);


--
-- Name: idx_notification_template_code_status; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE INDEX idx_notification_template_code_status ON tenant_e2etest.notification_template USING btree (template_code, status);


--
-- Name: idx_notification_outbox_channel_status; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE INDEX idx_notification_outbox_channel_status ON tenant_e2etest.notification_outbox USING btree (channel, status, created_on);


--
-- Name: idx_notification_outbox_pending; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE INDEX idx_notification_outbox_pending ON tenant_e2etest.notification_outbox USING btree (status, next_attempt_at, created_on);


--
-- Name: idx_notification_outbox_transaction; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE INDEX idx_notification_outbox_transaction ON tenant_e2etest.notification_outbox USING btree (transaction_id);


--
-- Name: idx_passcode_lookup; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE INDEX idx_passcode_lookup ON tenant_e2etest.passcode USING btree (passcode, unregistered_msisdn, status);


--
-- Name: idx_qr_payment_intent_status_expiry; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE INDEX idx_qr_payment_intent_status_expiry ON tenant_e2etest.qr_payment_intent USING btree (status, expires_at);


--
-- Name: idx_recent_recipients_account_last_paid; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE INDEX idx_recent_recipients_account_last_paid ON tenant_e2etest.recent_recipients USING btree (account_id, last_paid_at DESC);


--
-- Name: idx_recent_recipients_account_service_last_paid; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE INDEX idx_recent_recipients_account_service_last_paid ON tenant_e2etest.recent_recipients USING btree (account_id, service_code, last_paid_at DESC);


--
-- Name: idx_stored_document_checksum; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE INDEX idx_stored_document_checksum ON tenant_e2etest.stored_document USING btree (checksum_sha256);


--
-- Name: idx_stored_document_tenant_type; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE INDEX idx_stored_document_tenant_type ON tenant_e2etest.stored_document USING btree (tenant_id, document_type_id, uploaded_at DESC);


--
-- Name: idx_third_party_response_service_status; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE INDEX idx_third_party_response_service_status ON tenant_e2etest.third_party_response USING btree (service_code, status);


--
-- Name: idx_tlp_tag_id; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE INDEX idx_tlp_tag_id ON tenant_e2etest.transaction_limit_profile USING btree (tag_id);


--
-- Name: idx_tlpd_limit_id; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE INDEX idx_tlpd_limit_id ON tenant_e2etest.transaction_limit_profile_details USING btree (limit_id);


--
-- Name: idx_tlpp_limit_details_id; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE INDEX idx_tlpp_limit_details_id ON tenant_e2etest.transaction_limit_profile_period USING btree (limit_details_id);


--
-- Name: idx_tlu_limit_details_id; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE INDEX idx_tlu_limit_details_id ON tenant_e2etest.transaction_limit_usage USING btree (limit_details_id);


--
-- Name: idx_tlu_limit_id; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE INDEX idx_tlu_limit_id ON tenant_e2etest.transaction_limit_usage USING btree (limit_id);


--
-- Name: idx_tlu_tag_id; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE INDEX idx_tlu_tag_id ON tenant_e2etest.transaction_limit_usage USING btree (tag_id);


--
-- Name: idx_transaction_limit_details_profile; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE INDEX idx_transaction_limit_details_profile ON tenant_e2etest.transaction_limit_profile_details USING btree (limit_id, party_type, status);


--
-- Name: idx_transaction_limit_period_details; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE INDEX idx_transaction_limit_period_details ON tenant_e2etest.transaction_limit_profile_period USING btree (limit_details_id, period_type, status);


--
-- Name: idx_transaction_limit_profile_tag; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE INDEX idx_transaction_limit_profile_tag ON tenant_e2etest.transaction_limit_profile USING btree (tag_id, status, created_on DESC);


--
-- Name: idx_transaction_limit_profile_type; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE INDEX idx_transaction_limit_profile_type ON tenant_e2etest.transaction_limit_profile USING btree (limit_type, status, wallet_type, currency);


--
-- Name: idx_transaction_limit_usage_account_period; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE INDEX idx_transaction_limit_usage_account_period ON tenant_e2etest.transaction_limit_usage USING btree (account_id, period_type, last_transaction_date DESC);


--
-- Name: idx_transaction_limit_usage_subject; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE INDEX idx_transaction_limit_usage_subject ON tenant_e2etest.transaction_limit_usage USING btree (subject_key, subject_value, last_transaction_date DESC);


--
-- Name: uk_account_account_code_active; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE UNIQUE INDEX uk_account_account_code_active ON tenant_e2etest.account USING btree (account_code) WHERE ((account_code IS NOT NULL) AND ((status)::text = 'ACTIVE'::text));


--
-- Name: uk_account_biller_info_account; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE UNIQUE INDEX uk_account_biller_info_account ON tenant_e2etest.account_biller_info USING btree (account_id);


--
-- Name: uk_account_biller_info_biller_code; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE UNIQUE INDEX uk_account_biller_info_biller_code ON tenant_e2etest.account_biller_info USING btree (lower((biller_code)::text));


--
-- Name: uk_account_identifiers_active; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE UNIQUE INDEX uk_account_identifiers_active ON tenant_e2etest.account_identifiers USING btree (identifier_type, identifier_value, status);


--
-- Name: uk_account_merchant_info_account; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE UNIQUE INDEX uk_account_merchant_info_account ON tenant_e2etest.account_merchant_info USING btree (account_id);


--
-- Name: uk_account_merchant_info_merchant_code; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE UNIQUE INDEX uk_account_merchant_info_merchant_code ON tenant_e2etest.account_merchant_info USING btree (lower((merchant_code)::text));


--
-- Name: uk_account_merchant_mcc_code; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE UNIQUE INDEX uk_account_merchant_mcc_code ON tenant_e2etest.account_merchant_mcc USING btree (merchant_info_id, mcc_code);


--
-- Name: uk_account_mobile_number; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE UNIQUE INDEX uk_account_mobile_number ON tenant_e2etest.account USING btree (mobile_number) WHERE (mobile_number IS NOT NULL);


--
-- Name: uk_account_tags_account_tag; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE UNIQUE INDEX uk_account_tags_account_tag ON tenant_e2etest.account_tags USING btree (account_id, tag_id);


--
-- Name: uk_city_country_subdivision_code; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE UNIQUE INDEX uk_city_country_subdivision_code ON tenant_e2etest.city USING btree (country_id, COALESCE(subdivision_id, (0)::bigint), code) WHERE (code IS NOT NULL);


--
-- Name: uk_country_subdivision_country_code; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE UNIQUE INDEX uk_country_subdivision_country_code ON tenant_e2etest.country_subdivision USING btree (country_id, code);


--
-- Name: uk_stored_document_thumbnail_gridfs_file_id; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE UNIQUE INDEX uk_stored_document_thumbnail_gridfs_file_id ON tenant_e2etest.stored_document USING btree (thumbnail_gridfs_file_id) WHERE (thumbnail_gridfs_file_id IS NOT NULL);


--
-- Name: uq_account_notification_endpoint_primary; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE UNIQUE INDEX uq_account_notification_endpoint_primary ON tenant_e2etest.account_notification_endpoint USING btree (account_id, endpoint_type) WHERE (is_primary = true);


--
-- Name: uq_transaction_limit_usage_bucket; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE UNIQUE INDEX uq_transaction_limit_usage_bucket ON tenant_e2etest.transaction_limit_usage USING btree (subject_key, subject_value, limit_id, limit_details_id, period_type, operation_type, request_gateway);


--
-- Name: ux_third_party_response_txn; Type: INDEX; Schema: tenant_e2etest; Owner: -
--

CREATE UNIQUE INDEX ux_third_party_response_txn ON tenant_e2etest.third_party_response USING btree (transaction_id);


--
-- Name: account_biller_info fk_account_biller_info_account; Type: FK CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.account_biller_info
    ADD CONSTRAINT fk_account_biller_info_account FOREIGN KEY (account_id) REFERENCES tenant_e2etest.account(account_id);


--
-- Name: account_identifiers fk_account_identifiers_account; Type: FK CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.account_identifiers
    ADD CONSTRAINT fk_account_identifiers_account FOREIGN KEY (account_id) REFERENCES tenant_e2etest.account(account_id);


--
-- Name: account_identifiers fk_account_identifiers_auth; Type: FK CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.account_identifiers
    ADD CONSTRAINT fk_account_identifiers_auth FOREIGN KEY (auth_id) REFERENCES tenant_e2etest.account_auth(auth_id);


--
-- Name: account_merchant_info fk_account_merchant_info_account; Type: FK CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.account_merchant_info
    ADD CONSTRAINT fk_account_merchant_info_account FOREIGN KEY (account_id) REFERENCES tenant_e2etest.account(account_id);


--
-- Name: account_merchant_mcc fk_account_merchant_mcc_info; Type: FK CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.account_merchant_mcc
    ADD CONSTRAINT fk_account_merchant_mcc_info FOREIGN KEY (merchant_info_id) REFERENCES tenant_e2etest.account_merchant_info(merchant_info_id);


--
-- Name: account_tags fk_account_tags_account; Type: FK CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.account_tags
    ADD CONSTRAINT fk_account_tags_account FOREIGN KEY (account_id) REFERENCES tenant_e2etest.account(account_id);


--
-- Name: account_tags fk_account_tags_tag; Type: FK CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.account_tags
    ADD CONSTRAINT fk_account_tags_tag FOREIGN KEY (tag_id) REFERENCES tenant_e2etest.tags(tag_id);


--
-- Name: auth_challenge fk_auth_challenge_account; Type: FK CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.auth_challenge
    ADD CONSTRAINT fk_auth_challenge_account FOREIGN KEY (account_id) REFERENCES tenant_e2etest.account(account_id);


--
-- Name: city fk_city_country; Type: FK CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.city
    ADD CONSTRAINT fk_city_country FOREIGN KEY (country_id) REFERENCES tenant_e2etest.enumerations(id);


--
-- Name: city fk_city_subdivision; Type: FK CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.city
    ADD CONSTRAINT fk_city_subdivision FOREIGN KEY (subdivision_id) REFERENCES tenant_e2etest.country_subdivision(id);


--
-- Name: country_subdivision fk_country_subdivision_country; Type: FK CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.country_subdivision
    ADD CONSTRAINT fk_country_subdivision_country FOREIGN KEY (country_id) REFERENCES tenant_e2etest.enumerations(id);


--
-- Name: country_subdivision fk_country_subdivision_parent; Type: FK CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.country_subdivision
    ADD CONSTRAINT fk_country_subdivision_parent FOREIGN KEY (parent_id) REFERENCES tenant_e2etest.country_subdivision(id);


--
-- Name: document_reference fk_document_reference_document; Type: FK CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.document_reference
    ADD CONSTRAINT fk_document_reference_document FOREIGN KEY (document_id) REFERENCES tenant_e2etest.stored_document(document_id);


--
-- Name: document_type fk_document_type_category; Type: FK CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.document_type
    ADD CONSTRAINT fk_document_type_category FOREIGN KEY (category_id) REFERENCES tenant_e2etest.document_category(category_id);


--
-- Name: document_type_entity fk_document_type_entity_type; Type: FK CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.document_type_entity
    ADD CONSTRAINT fk_document_type_entity_type FOREIGN KEY (document_type_id) REFERENCES tenant_e2etest.document_type(document_type_id);


--
-- Name: enumerations fk_enumerations_parent_enum; Type: FK CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.enumerations
    ADD CONSTRAINT fk_enumerations_parent_enum FOREIGN KEY (parent_enum_id) REFERENCES tenant_e2etest.enumerations(id);


--
-- Name: kyc_document fk_kyc_document_account; Type: FK CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.kyc_document
    ADD CONSTRAINT fk_kyc_document_account FOREIGN KEY (account_id) REFERENCES tenant_e2etest.account(account_id);


--
-- Name: role_permissions fk_role_permissions_permission; Type: FK CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.role_permissions
    ADD CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES tenant_e2etest.permissions(permission_id);


--
-- Name: role_permissions fk_role_permissions_role; Type: FK CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.role_permissions
    ADD CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES tenant_e2etest.roles(role_id);


--
-- Name: stored_document fk_stored_document_type; Type: FK CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.stored_document
    ADD CONSTRAINT fk_stored_document_type FOREIGN KEY (document_type_id) REFERENCES tenant_e2etest.document_type(document_type_id);


--
-- Name: transaction_limit_profile_details fk_transaction_limit_details_profile; Type: FK CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.transaction_limit_profile_details
    ADD CONSTRAINT fk_transaction_limit_details_profile FOREIGN KEY (limit_id) REFERENCES tenant_e2etest.transaction_limit_profile(limit_id);


--
-- Name: transaction_limit_profile_period fk_transaction_limit_period_details; Type: FK CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.transaction_limit_profile_period
    ADD CONSTRAINT fk_transaction_limit_period_details FOREIGN KEY (limit_details_id) REFERENCES tenant_e2etest.transaction_limit_profile_details(limit_details_id);


--
-- Name: transaction_limit_profile fk_transaction_limit_profile_tag; Type: FK CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.transaction_limit_profile
    ADD CONSTRAINT fk_transaction_limit_profile_tag FOREIGN KEY (tag_id) REFERENCES tenant_e2etest.tags(tag_id);


--
-- Name: transaction_limit_usage fk_transaction_limit_usage_details; Type: FK CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.transaction_limit_usage
    ADD CONSTRAINT fk_transaction_limit_usage_details FOREIGN KEY (limit_details_id) REFERENCES tenant_e2etest.transaction_limit_profile_details(limit_details_id);


--
-- Name: transaction_limit_usage fk_transaction_limit_usage_profile; Type: FK CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.transaction_limit_usage
    ADD CONSTRAINT fk_transaction_limit_usage_profile FOREIGN KEY (limit_id) REFERENCES tenant_e2etest.transaction_limit_profile(limit_id);


--
-- Name: transaction_limit_usage fk_transaction_limit_usage_tag; Type: FK CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.transaction_limit_usage
    ADD CONSTRAINT fk_transaction_limit_usage_tag FOREIGN KEY (tag_id) REFERENCES tenant_e2etest.tags(tag_id);


--
-- Name: user_roles fk_user_roles_account; Type: FK CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.user_roles
    ADD CONSTRAINT fk_user_roles_account FOREIGN KEY (user_id) REFERENCES tenant_e2etest.account(account_id);


--
-- Name: user_roles fk_user_roles_role; Type: FK CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.user_roles
    ADD CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES tenant_e2etest.roles(role_id);


--
-- Name: wallet fk_wallet_account; Type: FK CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.wallet
    ADD CONSTRAINT fk_wallet_account FOREIGN KEY (account_id) REFERENCES tenant_e2etest.account(account_id);


--
-- Name: wallet_balance fk_wallet_balance_wallet; Type: FK CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.wallet_balance
    ADD CONSTRAINT fk_wallet_balance_wallet FOREIGN KEY (wallet_id) REFERENCES tenant_e2etest.wallet(wallet_id);


--
-- Name: kyc_document kyc_document_stored_document_id_fkey; Type: FK CONSTRAINT; Schema: tenant_e2etest; Owner: -
--

ALTER TABLE ONLY tenant_e2etest.kyc_document
    ADD CONSTRAINT kyc_document_stored_document_id_fkey FOREIGN KEY (stored_document_id) REFERENCES tenant_e2etest.stored_document(document_id);


--
-- Name: SCHEMA public; Type: ACL; Schema: -; Owner: -
--

GRANT ALL ON SCHEMA public TO paynest_app;


--
-- Name: SCHEMA tenant_e2etest; Type: ACL; Schema: -; Owner: -
--

GRANT ALL ON SCHEMA tenant_e2etest TO paynest_app;


--
-- Name: TABLE audit_api_logs; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE public.audit_api_logs TO paynest_app;


--
-- Name: SEQUENCE audit_api_logs_id_seq; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,USAGE ON SEQUENCE public.audit_api_logs_id_seq TO paynest_app;


--
-- Name: TABLE system_config; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE public.system_config TO paynest_app;


--
-- Name: TABLE tenant_registry; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE public.tenant_registry TO paynest_app;


--
-- Name: TABLE account; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.account TO paynest_app;


--
-- Name: TABLE account_auth; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.account_auth TO paynest_app;


--
-- Name: TABLE account_biller_info; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.account_biller_info TO paynest_app;


--
-- Name: SEQUENCE account_biller_info_biller_info_id_seq; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT ALL ON SEQUENCE tenant_e2etest.account_biller_info_biller_info_id_seq TO paynest_app;


--
-- Name: TABLE account_identifiers; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.account_identifiers TO paynest_app;


--
-- Name: SEQUENCE account_identifiers_id_seq; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT ALL ON SEQUENCE tenant_e2etest.account_identifiers_id_seq TO paynest_app;


--
-- Name: TABLE account_merchant_info; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.account_merchant_info TO paynest_app;


--
-- Name: SEQUENCE account_merchant_info_merchant_info_id_seq; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT ALL ON SEQUENCE tenant_e2etest.account_merchant_info_merchant_info_id_seq TO paynest_app;


--
-- Name: TABLE account_merchant_mcc; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.account_merchant_mcc TO paynest_app;


--
-- Name: SEQUENCE account_merchant_mcc_merchant_mcc_id_seq; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT ALL ON SEQUENCE tenant_e2etest.account_merchant_mcc_merchant_mcc_id_seq TO paynest_app;


--
-- Name: TABLE account_notification_endpoint; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.account_notification_endpoint TO paynest_app;


--
-- Name: SEQUENCE account_notification_endpoint_account_endpoint_id_seq; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT ALL ON SEQUENCE tenant_e2etest.account_notification_endpoint_account_endpoint_id_seq TO paynest_app;


--
-- Name: TABLE account_status_history; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.account_status_history TO paynest_app;


--
-- Name: SEQUENCE account_status_history_history_id_seq; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT ALL ON SEQUENCE tenant_e2etest.account_status_history_history_id_seq TO paynest_app;


--
-- Name: TABLE account_tags; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.account_tags TO paynest_app;


--
-- Name: SEQUENCE account_tags_id_seq; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT ALL ON SEQUENCE tenant_e2etest.account_tags_id_seq TO paynest_app;


--
-- Name: TABLE audit_api_log; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.audit_api_log TO paynest_app;


--
-- Name: SEQUENCE audit_api_log_id_seq; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT ALL ON SEQUENCE tenant_e2etest.audit_api_log_id_seq TO paynest_app;


--
-- Name: TABLE auth_challenge; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.auth_challenge TO paynest_app;


--
-- Name: TABLE bill_payment_status; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.bill_payment_status TO paynest_app;


--
-- Name: TABLE cashback_payout; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.cashback_payout TO paynest_app;


--
-- Name: SEQUENCE cashback_payout_cashback_payout_id_seq; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT ALL ON SEQUENCE tenant_e2etest.cashback_payout_cashback_payout_id_seq TO paynest_app;


--
-- Name: TABLE categories; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.categories TO paynest_app;


--
-- Name: SEQUENCE categories_category_id_seq; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT ALL ON SEQUENCE tenant_e2etest.categories_category_id_seq TO paynest_app;


--
-- Name: TABLE city; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.city TO paynest_app;


--
-- Name: SEQUENCE city_id_seq; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT ALL ON SEQUENCE tenant_e2etest.city_id_seq TO paynest_app;


--
-- Name: TABLE country_subdivision; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.country_subdivision TO paynest_app;


--
-- Name: SEQUENCE country_subdivision_id_seq; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT ALL ON SEQUENCE tenant_e2etest.country_subdivision_id_seq TO paynest_app;


--
-- Name: TABLE document_category; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.document_category TO paynest_app;


--
-- Name: SEQUENCE document_category_category_id_seq; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT ALL ON SEQUENCE tenant_e2etest.document_category_category_id_seq TO paynest_app;


--
-- Name: TABLE document_reference; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.document_reference TO paynest_app;


--
-- Name: SEQUENCE document_reference_document_reference_id_seq; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT ALL ON SEQUENCE tenant_e2etest.document_reference_document_reference_id_seq TO paynest_app;


--
-- Name: TABLE document_type; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.document_type TO paynest_app;


--
-- Name: SEQUENCE document_type_document_type_id_seq; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT ALL ON SEQUENCE tenant_e2etest.document_type_document_type_id_seq TO paynest_app;


--
-- Name: TABLE document_type_entity; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.document_type_entity TO paynest_app;


--
-- Name: TABLE enumerations; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.enumerations TO paynest_app;


--
-- Name: SEQUENCE enumerations_id_seq; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT ALL ON SEQUENCE tenant_e2etest.enumerations_id_seq TO paynest_app;


--
-- Name: TABLE error_catalog; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.error_catalog TO paynest_app;


--
-- Name: SEQUENCE error_catalog_id_seq; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT ALL ON SEQUENCE tenant_e2etest.error_catalog_id_seq TO paynest_app;


--
-- Name: TABLE fx_rates; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.fx_rates TO paynest_app;


--
-- Name: SEQUENCE fx_rates_rate_id_seq; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT ALL ON SEQUENCE tenant_e2etest.fx_rates_rate_id_seq TO paynest_app;


--
-- Name: TABLE kyc_document; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.kyc_document TO paynest_app;


--
-- Name: SEQUENCE kyc_document_document_id_seq; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT ALL ON SEQUENCE tenant_e2etest.kyc_document_document_id_seq TO paynest_app;


--
-- Name: TABLE notification_outbox; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.notification_outbox TO paynest_app;


--
-- Name: SEQUENCE notification_outbox_notification_id_seq; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT ALL ON SEQUENCE tenant_e2etest.notification_outbox_notification_id_seq TO paynest_app;


--
-- Name: TABLE notification_template; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.notification_template TO paynest_app;


--
-- Name: SEQUENCE notification_template_template_id_seq; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT ALL ON SEQUENCE tenant_e2etest.notification_template_template_id_seq TO paynest_app;


--
-- Name: TABLE otp; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.otp TO paynest_app;


--
-- Name: SEQUENCE otp_otp_id_seq; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT ALL ON SEQUENCE tenant_e2etest.otp_otp_id_seq TO paynest_app;


--
-- Name: TABLE passcode; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.passcode TO paynest_app;


--
-- Name: SEQUENCE passcode_passcode_id_seq; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT ALL ON SEQUENCE tenant_e2etest.passcode_passcode_id_seq TO paynest_app;


--
-- Name: TABLE permissions; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.permissions TO paynest_app;


--
-- Name: SEQUENCE permissions_permission_id_seq; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT ALL ON SEQUENCE tenant_e2etest.permissions_permission_id_seq TO paynest_app;


--
-- Name: TABLE pricing_rules; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.pricing_rules TO paynest_app;


--
-- Name: SEQUENCE pricing_rules_id_seq; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT ALL ON SEQUENCE tenant_e2etest.pricing_rules_id_seq TO paynest_app;


--
-- Name: TABLE qr_payment_intent; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.qr_payment_intent TO paynest_app;


--
-- Name: TABLE recent_recipients; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.recent_recipients TO paynest_app;


--
-- Name: TABLE role_permissions; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.role_permissions TO paynest_app;


--
-- Name: SEQUENCE role_permissions_id_seq; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT ALL ON SEQUENCE tenant_e2etest.role_permissions_id_seq TO paynest_app;


--
-- Name: TABLE roles; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.roles TO paynest_app;


--
-- Name: SEQUENCE roles_role_id_seq; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT ALL ON SEQUENCE tenant_e2etest.roles_role_id_seq TO paynest_app;


--
-- Name: TABLE service_catalog; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.service_catalog TO paynest_app;


--
-- Name: TABLE stored_document; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.stored_document TO paynest_app;


--
-- Name: TABLE supported_languages; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.supported_languages TO paynest_app;


--
-- Name: SEQUENCE supported_languages_id_seq; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT ALL ON SEQUENCE tenant_e2etest.supported_languages_id_seq TO paynest_app;


--
-- Name: TABLE tag_types; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.tag_types TO paynest_app;


--
-- Name: SEQUENCE tag_types_tag_type_id_seq; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT ALL ON SEQUENCE tenant_e2etest.tag_types_tag_type_id_seq TO paynest_app;


--
-- Name: TABLE tags; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.tags TO paynest_app;


--
-- Name: SEQUENCE tags_tag_id_seq; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT ALL ON SEQUENCE tenant_e2etest.tags_tag_id_seq TO paynest_app;


--
-- Name: TABLE third_party_response; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.third_party_response TO paynest_app;


--
-- Name: SEQUENCE third_party_response_id_seq; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT ALL ON SEQUENCE tenant_e2etest.third_party_response_id_seq TO paynest_app;


--
-- Name: TABLE transaction_details; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.transaction_details TO paynest_app;


--
-- Name: TABLE transaction_limit_profile; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.transaction_limit_profile TO paynest_app;


--
-- Name: TABLE transaction_limit_profile_details; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.transaction_limit_profile_details TO paynest_app;


--
-- Name: SEQUENCE transaction_limit_profile_details_limit_details_id_seq; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT ALL ON SEQUENCE tenant_e2etest.transaction_limit_profile_details_limit_details_id_seq TO paynest_app;


--
-- Name: SEQUENCE transaction_limit_profile_limit_id_seq; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT ALL ON SEQUENCE tenant_e2etest.transaction_limit_profile_limit_id_seq TO paynest_app;


--
-- Name: TABLE transaction_limit_profile_period; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.transaction_limit_profile_period TO paynest_app;


--
-- Name: SEQUENCE transaction_limit_profile_period_limit_period_id_seq; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT ALL ON SEQUENCE tenant_e2etest.transaction_limit_profile_period_limit_period_id_seq TO paynest_app;


--
-- Name: TABLE transaction_limit_usage; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.transaction_limit_usage TO paynest_app;


--
-- Name: SEQUENCE transaction_limit_usage_usage_id_seq; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT ALL ON SEQUENCE tenant_e2etest.transaction_limit_usage_usage_id_seq TO paynest_app;


--
-- Name: TABLE transactions; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.transactions TO paynest_app;


--
-- Name: TABLE user_roles; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.user_roles TO paynest_app;


--
-- Name: SEQUENCE user_roles_id_seq; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT ALL ON SEQUENCE tenant_e2etest.user_roles_id_seq TO paynest_app;


--
-- Name: SEQUENCE wallet_wallet_id_seq; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT ALL ON SEQUENCE tenant_e2etest.wallet_wallet_id_seq TO paynest_app;


--
-- Name: TABLE wallet; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.wallet TO paynest_app;


--
-- Name: TABLE wallet_balance; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.wallet_balance TO paynest_app;


--
-- Name: TABLE wallet_ledger; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.wallet_ledger TO paynest_app;


--
-- Name: SEQUENCE wallet_ledger_ledger_id_seq; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT ALL ON SEQUENCE tenant_e2etest.wallet_ledger_ledger_id_seq TO paynest_app;


--
-- Name: TABLE wallet_restriction; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.wallet_restriction TO paynest_app;


--
-- Name: TABLE wallet_restriction_history; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE tenant_e2etest.wallet_restriction_history TO paynest_app;


--
-- Name: SEQUENCE wallet_restriction_history_history_id_seq; Type: ACL; Schema: tenant_e2etest; Owner: -
--

GRANT ALL ON SEQUENCE tenant_e2etest.wallet_restriction_history_history_id_seq TO paynest_app;


--
-- PostgreSQL database dump complete
--

\unrestrict BbGzSIuyrLttdQ4NnErTGyRVrTHpjano2rIwgyvV9VfMH39cX52TJbSlbpGTaYT
