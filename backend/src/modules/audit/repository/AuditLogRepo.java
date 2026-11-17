package com.company.hrms.audit.repository;

import com.company.hrms.audit.model.AuditLog;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface AuditLogRepo extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findTop500ByOrderByAtDesc();
}
