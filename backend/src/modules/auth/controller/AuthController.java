package modules.auth.controller;

import modules.auth.model.ChangePasswordRequest;
import modules.auth.model.LoginRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import shared.common.ApiException;
import modules.admin.model.AuditLog;
import modules.admin.repository.AuditLogRepo;
import modules.admin.model.AppUser;
import modules.admin.repository.UserRepo;
import shared.security.JwtService;
import shared.security.CurrentUser;
import shared.security.CompanySessions;
import shared.security.CompanyRecordScope;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import shared.tenancy.CompanyTenancy;
import shared.tenancy.TenantDatabaseScope;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepo users;
    private final EntityManagerFactory entities;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final CompanySessions sessions;
    private final TenantDatabaseScope tenantScope;
    private final AuditLogRepo audit;
    private final JdbcTemplate jdbc;

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest req, HttpServletRequest http) {
        String username = req.username() == null ? "" : req.username().trim();
        String companyCode = (req.companyCode() == null || req.companyCode().trim().isEmpty()) ? "initial" : req.companyCode().trim();
        var account = sessions.findAccount(companyCode, username).orElse(null);
        if (account == null) throw invalidLogin();

        if (tenantScope.enabled()) {
            Map<String, Object> result = tenantScope.transaction(account.tenantId(), status -> {
                Map<String, Object> user = verify(users.findById(account.userId()).orElse(null), req, http, audit::save);
                if (user != null) enrichCompany(user, account.tenantId());
                return user == null ? null : Map.of("token", jwt.create(account), "user", user);
            });
            if (result == null) throw invalidLogin();
            return result;
        }

        var factory = entities.unwrap(SessionFactory.class);
        Map<String, Object> info;
        CompanyTenancy.attach(account.tenantId());
        try {
            info = factory.fromTransaction(session -> verify(
                    session.find(AppUser.class, account.userId()), req, http, session::persist));
        } finally {
            CompanyTenancy.clear();
        }
        if (info == null) throw invalidLogin();
        enrichCompany(info, account.tenantId());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("token", jwt.create(account));
        response.put("user", info);
        return response;
    }

    private void enrichCompany(Map<String, Object> userMap, UUID tenantId) {
        if (tenantId == null) return;
        userMap.put("companyId", tenantId);
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "select name, code from company where id = ?", tenantId);
            if (!rows.isEmpty()) {
                userMap.put("companyName", rows.getFirst().get("name"));
                userMap.put("companyCode", rows.getFirst().get("code"));
            }
        } catch (Exception ignored) {}
    }

    private Map<String, Object> verify(AppUser user, LoginRequest request, HttpServletRequest http,
                                       Consumer<AuditLog> writeAudit) {
        boolean ok = user != null && user.isActive() && request.password() != null
                && encoder.matches(request.password(), user.getPasswordHash());
        writeAudit.accept(AuditLog.of(user == null ? null : user.getUsername(),
                ok ? "LOGIN" : "LOGIN_FAILED", "POST", "/api/auth/login",
                ok ? 200 : 401, http.getRemoteAddr()));
        return ok ? userInfo(user) : null;
    }

    private ApiException invalidLogin() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Invalid company code or sign-in credentials");
    }

    @PostMapping("/logout")
    public void logout() {
        sessions.revoke(CurrentUser.get());
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        AppUser u = users.findById(CurrentUser.get().id())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Not signed in"));
        Map<String, Object> info = userInfo(u);
        enrichCompany(info, CurrentUser.tenantId());
        return info;
    }

    @PostMapping("/change-password")
    public void changePassword(@RequestBody ChangePasswordRequest req) {
        AppUser user = users.findById(CurrentUser.get().id())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Not signed in"));
        if (req.currentPassword() == null || !encoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("Current password is wrong");
        }
        if (req.newPassword() == null || req.newPassword().length() < 8) {
            throw ApiException.badRequest("New password must be at least 8 characters");
        }
        user.setPasswordHash(encoder.encode(req.newPassword()));
        users.save(user);
    }

    public static Map<String, Object> userInfo(AppUser u) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", u.getId());
        map.put("username", u.getUsername());
        map.put("email", u.getEmail());
        map.put("role", u.getRole().getName());
        var scope = CompanyRecordScope.of(u.getRole().getPermissions());
        map.put("recordScope", scope.name());
        map.put("permissions", scope.displayPermissions(u.getRole().getPermissions()));
        if (u.getEmployee() != null) {
            map.put("employeeId", u.getEmployee().getId());
            map.put("employeeName", u.getEmployee().getFullName());
            map.put("employeeCode", u.getEmployee().getEmployeeCode());
        }
        return map;
    }
}
