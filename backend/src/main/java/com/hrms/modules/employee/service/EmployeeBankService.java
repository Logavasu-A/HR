package com.hrms.modules.employee.service;

import java.util.Set;

import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hrms.modules.audit.model.AuditLog;
import com.hrms.modules.audit.repository.AuditLogRepo;
import com.hrms.shared.common.ApiException;
import com.hrms.shared.security.CurrentUser;

@Service
public class EmployeeBankService {
    public static final String READ = "employee-bank:read";
    public static final String WRITE = "employee-bank:write";

    private final JdbcTemplate jdbc;
    private final AuditLogRepo audit;

    public EmployeeBankService(JdbcTemplate jdbc, AuditLogRepo audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    public record Details(@Size(max = 120) String bankName, @Size(max = 60) String bankAccountNo) {}

    public static void checkDelegation(Set<String> permissions) {
        if (permissions != null &&
                ((permissions.contains(READ) && !CurrentUser.hasExplicitPermission(READ)) ||
                 (permissions.contains(WRITE) && !CurrentUser.hasExplicitPermission(WRITE)))) {
            throw ApiException.forbidden("You cannot administer a banking permission you do not hold.");
        }
    }

    @Transactional
    public Details read(long id, String path, String ip) {
        if (!CurrentUser.hasExplicitPermission(READ)) {
            throw ApiException.forbidden("Employee banking read permission is required.");
        }
        Details details = jdbc.query("""
                select bank_name, bank_account_no from employee where tenant_id = ? and id = ?
                """, (rs, row) -> new Details(rs.getString(1), rs.getString(2)), CurrentUser.tenantId(), id)
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("Employee not found"));
        audit.save(AuditLog.of(CurrentUser.usernameOrSystem(), "READ_BANK_DETAILS", "GET", path, 200, ip));
        return details;
    }

    @Transactional
    public void update(long id, Details details, String path, String ip) {
        if (!CurrentUser.hasExplicitPermission(WRITE)) {
            throw ApiException.forbidden("Employee banking write permission is required.");
        }
        int changed = jdbc.update("""
                update employee
                   set bank_name = ?, bank_account_no = ?, updated_at = localtimestamp, updated_by = ?
                 where tenant_id = ? and id = ?
                """, details.bankName(), details.bankAccountNo(), CurrentUser.usernameOrSystem(), CurrentUser.tenantId(), id);
        if (changed != 1) throw ApiException.notFound("Employee not found");
        audit.save(AuditLog.of(CurrentUser.usernameOrSystem(), "UPDATE_BANK_DETAILS", "PUT", path, 204, ip));
    }
}
