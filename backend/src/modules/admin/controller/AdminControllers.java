package modules.admin.controller;

import modules.admin.model.AppUser;
import modules.admin.model.Role;
import modules.admin.repository.RoleRepo;
import modules.admin.repository.UserRepo;
import modules.employee.service.EmployeeBankService;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import shared.common.CrudController;
import shared.common.ApiException;
import shared.common.PageResponse;
import modules.admin.repository.AuditLogRepo;
import shared.security.CurrentUser;
import shared.security.CompanyRecordScope;
import shared.tenancy.TenantDatabaseScope;

public class AdminControllers {

    /** Modules that can appear in a permission, used to build the role editor. */
    public static final List<String> MODULES = List.of(
            "core", "leave", "attendance", "payroll", "recruitment", "expense", "performance", "dashboard", "admin");

    @RestController
    @RequestMapping("/api/admin/users")
    public static class Users extends CrudController<AppUser> {

        private final PasswordEncoder encoder;
        private final RoleRepo roles;
        private final TenantDatabaseScope scope;

        public Users(UserRepo repo, PasswordEncoder encoder, RoleRepo roles, TenantDatabaseScope scope) {
            super(repo);
            this.encoder = encoder;
            this.roles = roles;
            this.scope = scope;
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
            check(existing.getRole(), scope);
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
            check(existing.getRole(), scope);
            if (CurrentUser.isCompanyUser(existing.getId())) {
                throw ApiException.badRequest("You cannot delete your own login");
            }
            repo.delete(existing);
        }

        private void validateAssignedRole(AppUser user) {
            var roleId = user.getRole().getId();
            if (roleId == null) throw ApiException.badRequest("A role is required.");
            var role = roles.findById(roleId).orElseThrow(() -> ApiException.notFound("Role not found"));
            check(role, scope);
            user.setRole(role);
        }
    }

    @RestController
    @RequestMapping("/api/admin/roles")
    public static class Roles extends CrudController<Role> {
        private final TenantDatabaseScope scope;

        public Roles(RoleRepo repo, TenantDatabaseScope scope) {
            super(repo);
            this.scope = scope;
        }

        @Override
        protected Role doCreate(Role role) {
            check(role, scope);
            return super.doCreate(role);
        }

        @Override
        protected Role doUpdate(Role existing, Role update) {
            check(existing, scope);
            check(update, scope);
            return super.doUpdate(existing, update);
        }

        @Override
        protected void doDelete(Role role) {
            check(role, scope);
            super.doDelete(role);
        }
    }

    @RestController
    @RequestMapping("/api/admin")
    public static class Misc {

        private final AuditLogRepo auditLogs;
        private final TenantDatabaseScope scope;

        public Misc(AuditLogRepo auditLogs, TenantDatabaseScope scope) {
            this.auditLogs = auditLogs;
            this.scope = scope;
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
            if (CurrentUser.hasExplicitPermission(EmployeeBankService.READ)) out.add(EmployeeBankService.READ);
            if (CurrentUser.hasExplicitPermission(EmployeeBankService.WRITE)) out.add(EmployeeBankService.WRITE);
            if (CurrentUser.isPlatformWorkspace() || CurrentUser.hasExplicitPermission("sensitive:approve")) {
                out.add("sensitive:approve");
            }
            if (scope.abacEnabled()) out.addAll(CompanyRecordScope.MARKERS);
            return out;
        }
    }       
                     
    private static void check(Role role, TenantDatabaseScope scope) {
        CompanyRecordScope.checkDelegation(role.getPermissions(), scope.abacEnabled());
        EmployeeBankService.checkDelegation(role.getPermissions());
        if (role.getPermissions().contains("sensitive:approve") && !CurrentUser.isPlatformWorkspace()
                && !CurrentUser.hasExplicitPermission("sensitive:approve")) {
            throw ApiException.forbidden("You cannot administer the financial approval permission.");
        }
    }
}
