package com.hrms.modules.auth.controller;

import com.hrms.modules.auth.model.ChangePasswordRequest;
import com.hrms.modules.auth.model.LoginRequest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hrms.shared.common.ApiException;
import com.hrms.modules.audit.model.AuditLog;
import com.hrms.modules.audit.repository.AuditLogRepo;
import com.hrms.modules.admin.model.AppUser;
import com.hrms.modules.admin.repository.UserRepo;
import com.hrms.shared.security.JwtService;
import com.hrms.shared.security.CurrentUser;
import com.hrms.shared.security.CompanySessions;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import com.hrms.shared.tenancy.CompanyTenancy;
import com.hrms.shared.tenancy.TenantDatabaseScope;
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

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest req, HttpServletRequest http) {
        String username = req.username() == null ? "" : req.username().trim();
        String companyCode = req.companyCode() == null ? "initial" : req.companyCode().trim();
        var account = sessions.findAccount(companyCode, username).orElse(null);
        if (account == null) throw invalidLogin();

        if (tenantScope.enabled()) {
            Map<String, Object> result = tenantScope.transaction(account.tenantId(), status -> {
                Map<String, Object> user = verify(users.findById(account.userId()).orElse(null), req, http, audit::save);
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
        return Map.of("token", jwt.create(account), "user", info);
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
        return userInfo(users.findById(CurrentUser.get().id())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Not signed in")));
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

    private static Map<String, Object> userInfo(AppUser u) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", u.getId());
        map.put("username", u.getUsername());
        map.put("email", u.getEmail());
        map.put("role", u.getRole().getName());
        map.put("permissions", u.getRole().getPermissions());
        if (u.getEmployee() != null) {
            map.put("employeeId", u.getEmployee().getId());
            map.put("employeeName", u.getEmployee().getFullName());
            map.put("employeeCode", u.getEmployee().getEmployeeCode());
        }
        return map;
    }
}
