package com.paynest.users.repository;

import com.paynest.users.entity.WalletRestrictionHistory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class WalletRestrictionHistoryRepository {

    private final EntityManager entityManager;

    public WalletRestrictionHistory insert(WalletRestrictionHistory history) {
        entityManager.persist(history);
        return history;
    }

    public List<WalletRestrictionHistory> findByWalletIdOrderByVersionDesc(Long walletId) {
        return entityManager
                .createQuery("""
                        SELECT history
                        FROM WalletRestrictionHistory history
                        WHERE history.walletId = :walletId
                        ORDER BY history.version DESC
                        """, WalletRestrictionHistory.class)
                .setParameter("walletId", walletId)
                .getResultList();
    }
}
