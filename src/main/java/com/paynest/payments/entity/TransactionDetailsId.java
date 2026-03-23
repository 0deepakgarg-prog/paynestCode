package com.paynest.payments.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Data;

import java.io.Serializable;

@Data
@Embeddable
public class TransactionDetailsId implements Serializable {
    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "txn_sequence_number")
    private Long txnSequenceNumber;

    public TransactionDetailsId() {}

    public TransactionDetailsId(String transactionId, long l) {
        this.transactionId = transactionId;
        this.txnSequenceNumber = l;
    }
}

