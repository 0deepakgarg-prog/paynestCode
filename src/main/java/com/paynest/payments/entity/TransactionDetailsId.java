package com.paynest.payments.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Data;

import java.io.Serializable;

@Data
@Embeddable
public class TransactionDetailsId implements Serializable {
    @Column(name = "transaction_id", nullable = false, length = 30)
    private String transactionId;

    @Column(name = "txn_sequence_number", nullable = false, precision = 10)
    private Long txnSequenceNumber;

    public TransactionDetailsId() {}

    public TransactionDetailsId(String transactionId, long l) {
        this.transactionId = transactionId;
        this.txnSequenceNumber = l;
    }
}

