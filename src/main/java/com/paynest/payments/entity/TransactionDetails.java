package com.paynest.payments.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transaction_details")
@Data
public class TransactionDetails {
    @EmbeddedId
    private TransactionDetailsId id;

    @Column(name = "account_id", nullable = false, length = 30)
    private String accountId;

    @Column(name = "user_type", nullable = false, length = 10)
    private String userType;

    @Column(name = "entry_type", nullable = false, length = 5)
    private String entryType;

    @Column(name = "identifier_id", nullable = false, length = 80)
    private String identifierId;

    @Column(name = "second_identifier_id", nullable = false, length = 80)
    private String secondIdentifierId;

    @Column(name = "transaction_value")
    private BigDecimal transactionValue;

    @Column(name = "approved_value")
    private BigDecimal approvedValue;

    @Column(name = "previous_balance")
    private BigDecimal previousBalance;

    @Column(name = "post_balance")
    private BigDecimal postBalance;

    @Column(name = "transfer_on")
    private LocalDateTime transferOn;

    @Column(name = "service_code", nullable = false, length = 15)
    private String serviceCode;

    @Column(name = "transfer_status", length = 3)
    private String transferStatus;

    @Column(name = "wallet_number", length = 25)
    private String walletNumber;

    @Column(name = "wallet_type", length = 50)
    private String walletType;

    @Column(name = "currency", length = 10)
    private String currency;

    @Column(name = "transaction_type", length = 50)
    private String transactionType;

    @Column(name = "previous_fic_balance")
    private BigDecimal previousFicBalance;

    @Column(name = "post_fic_balance")
    private BigDecimal postFicBalance;

    @Column(name = "previous_frozen_balance")
    private BigDecimal previousFrozenBalance;

    @Column(name = "post_frozen_balance")
    private BigDecimal postFrozenBalance;

    @Column(name = "attr_1_name", length = 255)
    private String attr1Name;

    @Column(name = "attr_1_value", length = 255)
    private String attr1Value;

    @Column(name = "attr_2_name", length = 255)
    private String attr2Name;

    @Column(name = "attr_2_value", length = 255)
    private String attr2Value;

    @Column(name = "attr_3_name", length = 255)
    private String attr3Name;

    @Column(name = "attr_3_value", length = 255)
    private String attr3Value;

    @Column(name = "attr_4_name", length = 255)
    private String attr4Name;

    @Column(name = "attr_4_value", length = 255)
    private String attr4Value;

    @Column(name = "attr_5_name", length = 255)
    private String attr5Name;

    @Column(name = "attr_5_value", length = 255)
    private String attr5Value;

    @Column(name = "attr_6_name", length = 255)
    private String attr6Name;

    @Column(name = "attr_6_value", length = 255)
    private String attr6Value;

}

