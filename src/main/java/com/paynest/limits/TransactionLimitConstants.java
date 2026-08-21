package com.paynest.limits;

import java.util.List;
import java.util.Set;

public final class TransactionLimitConstants {

    private TransactionLimitConstants() {
    }

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_INACTIVE = "INACTIVE";
    public static final String STATUS_DELETED = "DELETED";

    public static final String LIMIT_TYPE_GLOBAL = "GLOBAL";
    public static final String LIMIT_TYPE_SERVICE = "SERVICE";

    public static final String PARTY_DEBITOR = "DEBITOR";
    public static final String PARTY_CREDITOR = "CREDITOR";

    public static final String OPERATION_ALL = "ALL";
    public static final String REQUEST_GATEWAY_ALL = "ALL";

    public static final String PERIOD_DAILY = "DAILY";
    public static final String PERIOD_MONTHLY = "MONTHLY";

    public static final String SUBJECT_ACCOUNT_ID = "ACCOUNT_ID";
    public static final String SUBJECT_MSISDN = "MSISDN";
    public static final String SUBJECT_MOBILE = "MOBILE";
    public static final String SUBJECT_SSN = "SSN";
    public static final String SUBJECT_PAN = "PAN";
    public static final String SUBJECT_NATIONAL_ID = "NATIONAL_ID";
    public static final String SUBJECT_AADHAAR = "AADHAAR";

    public static final Set<String> ALLOWED_STATUSES = Set.of(
            STATUS_ACTIVE,
            STATUS_INACTIVE,
            STATUS_DELETED
    );

    public static final Set<String> ALLOWED_LIMIT_TYPES = Set.of(
            LIMIT_TYPE_GLOBAL,
            LIMIT_TYPE_SERVICE
    );

    public static final Set<String> ALLOWED_PARTY_TYPES = Set.of(
            PARTY_DEBITOR,
            PARTY_CREDITOR
    );

    public static final Set<String> ALLOWED_PERIOD_TYPES = Set.of(
            PERIOD_DAILY,
            PERIOD_MONTHLY
    );

    public static final Set<String> ALLOWED_SUBJECT_KEYS = Set.of(
            SUBJECT_ACCOUNT_ID,
            SUBJECT_MSISDN,
            SUBJECT_MOBILE,
            SUBJECT_SSN,
            SUBJECT_PAN,
            SUBJECT_NATIONAL_ID,
            SUBJECT_AADHAAR
    );

    public static final List<String> REFERENCE_WALLET_TYPES = List.of(
            "MAIN",
            "SALARY",
            "BONUS",
            "COMMISSION",
            "BANK",
            "COMMDIS",
            "SC"
    );
}
