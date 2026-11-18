package modules.admin.controller;

import modules.admin.model.AppUser;
import modules.admin.model.Role;
import modules.admin.repository.RoleRepo;
import modules.admin.repository.UserRepo;

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
import modules.audit.model.AuditLog;
import modules.audit.repository.AuditLogRepo;
import shared.security.CurrentUser;

public class AdminControllers {

    /** Modules that can appear in a permission, used to build the role editor. */
    public static final List<String> MODULES = List.of(
            "core", "leave", "attendance", "payroll", "recruitment", "expense", "performance", "dashboard", "admin");

    @RestController
    @RequestMapping("/api/admin/users")
    public static class Users extends CrudController<AppUser> {

        private final PasswordEncoder encoder;

        public Users(UserRepo repo, PasswordEncoder encoder) {
            super(repo);
            this.encoder = encoder;
        }

        @Override
        protected AppUser doCreate(AppUser body) {
            if (body.getPassword() == null || body.getPassword().length() < 8) {
                throw ApiException.badRequest("Password must be at least 8 characters");
            }
            body.setPasswordHash(encoder.encode(body.getPassword()));
            return repo.save(body);
        }

        @Override
        protected AppUser doUpdate(AppUser existing, AppUser body) {
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
            if (existing.getId().equals(CurrentUser.get().id())) {
                throw ApiException.badRequest("You cannot delete your own login");
            }
            repo.delete(existing);
        }
    }

    @RestController
    @RequestMapping("/api/admin/roles")
    public static class Roles extends CrudController<Role> {
        public Roles(RoleRepo repo) {
            super(repo);
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
            return out;
        }
    }
}
