package com.paynest.payments.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "transaction_details")
@Data
public class TransactionDetails {
    @EmbeddedId
    private TransactionDetailsId id;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "user_type", nullable = false)
    private String userType;

    @Column(name = "entry_type", nullable = false)
    private String entryType;

    @Column(name = "identifier_id", nullable = false)
    private String identifierId;

    @Column(name = "second_identifier_id", nullable = false)
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

    @Column(name = "service_code", nullable = false)
    private String serviceCode;

    @Column(name = "transfer_status")
    private String transferStatus;

    @Column(name = "wallet_number")
    private String walletNumber;

    @Column(name = "previous_fic_balance")
    private BigDecimal previousFicBalance;

    @Column(name = "post_fic_balance")
    private BigDecimal postFicBalance;

    @Column(name = "previous_frozen_balance")
    private BigDecimal previousFrozenBalance;

    @Column(name = "post_frozen_balance")
    private BigDecimal postFrozenBalance;

}

