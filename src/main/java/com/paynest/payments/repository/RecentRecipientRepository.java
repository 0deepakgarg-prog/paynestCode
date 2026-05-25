package com.paynest.payments.repository;

import com.paynest.payments.entity.RecentRecipient;
import com.paynest.payments.entity.RecentRecipientId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RecentRecipientRepository extends JpaRepository<RecentRecipient, RecentRecipientId> {

    List<RecentRecipient> findByAccountIdOrderByLastPaidAtDesc(String accountId, Pageable pageable);

    List<RecentRecipient> findByAccountIdAndServiceCodeOrderByLastPaidAtDesc(
            String accountId,
            String serviceCode,
            Pageable pageable
    );

    @Modifying
    @Query(value = """
            INSERT INTO recent_recipients (
                account_id,
                recipient_account_id,
                service_code,
                currency,
                wallet_type,
                recipient_account_type,
                recipient_identifier_type,
                recipient_identifier_value,
                recipient_display_name,
                last_transaction_id,
                last_paid_at,
                payment_count,
                field1,
                field2,
                field3,
                field4,
                field5,
                created_on,
                modified_on
            ) VALUES (
                :accountId,
                :recipientAccountId,
                :serviceCode,
                :currency,
                :walletType,
                :recipientAccountType,
                :recipientIdentifierType,
                :recipientIdentifierValue,
                :recipientDisplayName,
                :lastTransactionId,
                :lastPaidAt,
                1,
                :field1,
                :field2,
                :field3,
                :field4,
                :field5,
                :now,
                :now
            )
            ON CONFLICT (account_id, recipient_account_id, service_code, currency, wallet_type)
            DO UPDATE SET
                recipient_account_type = EXCLUDED.recipient_account_type,
                recipient_identifier_type = EXCLUDED.recipient_identifier_type,
                recipient_identifier_value = EXCLUDED.recipient_identifier_value,
                recipient_display_name = EXCLUDED.recipient_display_name,
                last_transaction_id = EXCLUDED.last_transaction_id,
                last_paid_at = EXCLUDED.last_paid_at,
                payment_count = recent_recipients.payment_count + 1,
                field1 = EXCLUDED.field1,
                field2 = EXCLUDED.field2,
                field3 = EXCLUDED.field3,
                field4 = EXCLUDED.field4,
                field5 = EXCLUDED.field5,
                modified_on = EXCLUDED.modified_on
            """, nativeQuery = true)
    void upsertRecentRecipient(
            @Param("accountId") String accountId,
            @Param("recipientAccountId") String recipientAccountId,
            @Param("serviceCode") String serviceCode,
            @Param("currency") String currency,
            @Param("walletType") String walletType,
            @Param("recipientAccountType") String recipientAccountType,
            @Param("recipientIdentifierType") String recipientIdentifierType,
            @Param("recipientIdentifierValue") String recipientIdentifierValue,
            @Param("recipientDisplayName") String recipientDisplayName,
            @Param("lastTransactionId") String lastTransactionId,
            @Param("lastPaidAt") LocalDateTime lastPaidAt,
            @Param("field1") String field1,
            @Param("field2") String field2,
            @Param("field3") String field3,
            @Param("field4") String field4,
            @Param("field5") String field5,
            @Param("now") LocalDateTime now
    );
}
