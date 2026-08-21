package com.paynest.notifications.repository;

import com.paynest.notifications.entity.NotificationOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {

    List<NotificationOutbox> findTop100ByStatusOrderByCreatedOnAsc(String status);

    @Query("""
            FROM NotificationOutbox notification
            WHERE notification.status = :status
              AND (notification.nextAttemptAt IS NULL OR notification.nextAttemptAt <= :now)
            ORDER BY notification.createdOn ASC
            """)
    List<NotificationOutbox> findDueNotifications(
            @Param("status") String status,
            @Param("now") LocalDateTime now
    );
}
