package com.company.hrms.infrastructure.audit.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.company.hrms.infrastructure.audit.entity.AuditLog;

@Repository
public interface AuditLogRepo extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findTop500ByOrderByAtDesc();
}
