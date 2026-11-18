package com.hrms.modules.audit.repository;

import com.hrms.modules.audit.model.AuditLog;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepo extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findTop500ByOrderByAtDesc();

    Page<AuditLog> findAllByOrderByAtDesc(Pageable pageable);
}
