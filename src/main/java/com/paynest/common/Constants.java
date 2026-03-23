package com.paynest.common;

    public final class Constants {

        private Constants() {
            // prevent instantiation
        }

        public static final Boolean TRUE = true;
        public static final Boolean FALSE = false;
        // Account Status
        public static final String ACCOUNT_STATUS_ACTIVE = "ACTIVE";
        public static final String ACCOUNT_STATUS_INACTIVE = "INACTIVE";
        public static final String ACCOUNT_STATUS_BLOCKED = "BLOCKED";

        // KYC Status
        public static final String KYC_STATUS_VERIFIED = "VERIFIED";
        public static final String KYC_STATUS_PENDING = "PENDING";
        public static final String KYC_STATUS_REJECTED = "REJECTED";
        public static final String KYC_STATUS_EXPIRED = "EXPIRED";
        public static final String KYC_STATUS_PENDING_APPROVAL = "PENDING_APPROVAL";;

        // Transaction Type
        public static final String TXN_TYPE_DR = "DR";
        public static final String TXN_TYPE_CR = "CR";

        //Transaction Status
        public static final String TRANSACTION_INITIATED = "TI";
        public static final String TRANSACTION_PENDING = "TP";
        public static final String TRANSACTION_AMBIGUOUS = "TA";
        public static final String TRANSACTION_SUCCESS = "TS";
        public static final String TRANSACTION_FAILED = "TF";
    }

