package com.paynest.config.repository;

import com.paynest.config.entity.AuditApiLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditApiLogRepository extends JpaRepository<AuditApiLog, Long> {
}

