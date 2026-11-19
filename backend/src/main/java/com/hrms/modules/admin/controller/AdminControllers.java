package com.hrms.modules.admin.controller;

import com.hrms.modules.admin.model.AppUser;
import com.hrms.modules.admin.model.Role;
import com.hrms.modules.admin.repository.RoleRepo;
import com.hrms.modules.admin.repository.UserRepo;
import com.hrms.modules.employee.service.EmployeeBankService;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hrms.shared.common.CrudController;
import com.hrms.shared.common.ApiException;
import com.hrms.shared.common.PageResponse;
import com.hrms.modules.audit.model.AuditLog;
import com.hrms.modules.audit.repository.AuditLogRepo;
import com.hrms.shared.security.CurrentUser;

public class AdminControllers {

    /** Modules that can appear in a permission, used to build the role editor. */
    public static final List<String> MODULES = List.of(
            "core", "leave", "attendance", "payroll", "recruitment", "expense", "performance", "dashboard", "admin");

    @RestController
    @RequestMapping("/api/admin/users")
    public static class Users extends CrudController<AppUser> {

        private final PasswordEncoder encoder;
        private final RoleRepo roles;

        public Users(UserRepo repo, PasswordEncoder encoder, RoleRepo roles) {
            super(repo);
            this.encoder = encoder;
            this.roles = roles;
        }

        @Override
        protected AppUser doCreate(AppUser body) {
            validateAssignedRole(body);
            if (body.getPassword() == null || body.getPassword().length() < 8) {
                throw ApiException.badRequest("Password must be at least 8 characters");
            }
            body.setPasswordHash(encoder.encode(body.getPassword()));
            return repo.save(body);
        }

        @Override
        protected AppUser doUpdate(AppUser existing, AppUser body) {
            EmployeeBankService.checkDelegation(existing.getRole().getPermissions());
            validateAssignedRole(body);
            if (body.getPassword() != null && !body.getPassword().isBlank()) {
                if (body.getPassword().length() < 8) {
                    throw ApiException.badRequest("Password must be at least 8 characters");
                }
                body.setPasswordHash(encoder.encode(body.getPassword()));
            } else {
                body.setPasswordHash(existing.getPasswordHash());
            }
            return repo.save(body);
        }

        @Override
        protected void doDelete(AppUser existing) {
            EmployeeBankService.checkDelegation(existing.getRole().getPermissions());
            if (existing.getId().equals(CurrentUser.get().id())) {
                throw ApiException.badRequest("You cannot delete your own login");
            }
            repo.delete(existing);
        }

        private void validateAssignedRole(AppUser user) {
            var roleId = user.getRole().getId();
            if (roleId == null) throw ApiException.badRequest("A role is required.");
            var role = roles.findById(roleId).orElseThrow(() -> ApiException.notFound("Role not found"));
            EmployeeBankService.checkDelegation(role.getPermissions());
            user.setRole(role);
        }
    }

    @RestController
    @RequestMapping("/api/admin/roles")
    public static class Roles extends CrudController<Role> {
        public Roles(RoleRepo repo) {
            super(repo);
        }

        @Override
        protected Role doCreate(Role role) {
            EmployeeBankService.checkDelegation(role.getPermissions());
            return super.doCreate(role);
        }

        @Override
        protected Role doUpdate(Role existing, Role update) {
            EmployeeBankService.checkDelegation(existing.getPermissions());
            EmployeeBankService.checkDelegation(update.getPermissions());
            return super.doUpdate(existing, update);
        }

        @Override
        protected void doDelete(Role role) {
            EmployeeBankService.checkDelegation(role.getPermissions());
            super.doDelete(role);
        }
    }

    @RestController
    @RequestMapping("/api/admin")
    public static class Misc {

        private final AuditLogRepo auditLogs;

        public Misc(AuditLogRepo auditLogs) {
            this.auditLogs = auditLogs;
        }

        @GetMapping("/audit-logs")
        public Object auditLogs(@RequestParam(required = false) Integer page,
                                @RequestParam(defaultValue = "20") int size) {
            if (page != null) {
                return PageResponse.of(auditLogs.findAllByOrderByAtDesc(PageResponse.pageable(page, size)));
            }
            return auditLogs.findTop500ByOrderByAtDesc();
        }

        /** Every permission value a role can hold. */
        @GetMapping("/permissions")
        public List<String> permissions() {
            List<String> out = new ArrayList<>();
            out.add("*");
            for (String m : MODULES) {
                out.add(m + ":read");
                if (!m.equals("dashboard")) {
                    out.add(m + ":write");
                }
                if (List.of("leave", "expense", "payroll", "performance").contains(m)) {
                    out.add(m + ":approve");
                }
            }
            var held = CurrentUser.get().permissions();
            if (held.contains(EmployeeBankService.READ)) out.add(EmployeeBankService.READ);
            if (held.contains(EmployeeBankService.WRITE)) out.add(EmployeeBankService.WRITE);
            return out;
        }
    }
}
