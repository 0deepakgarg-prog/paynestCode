package com.paynest.repository;

import com.paynest.entity.AuditApiLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditApiLogRepository extends JpaRepository<AuditApiLog, Long> {
}
