package com.hrms.modules.audit.model;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** One row per change made through the API: who, what, when, from where. */
@Entity
@Table(name = "audit_log")
@Getter
@Setter
public class AuditLog extends com.hrms.shared.tenancy.TenantOwned {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
    private String action;
    private String method;
    private String path;
    private Integer status;
    private String ip;
    private LocalDateTime at;

    public static AuditLog of(String username, String action, String method, String path, Integer status, String ip) {
        AuditLog log = new AuditLog();
        log.username = username;
        log.action = action;
        log.method = method;
        log.path = path != null && path.length() > 300 ? path.substring(0, 300) : path;
        log.status = status;
        log.ip = ip;
        log.at = LocalDateTime.now();
        return log;
    }
}
