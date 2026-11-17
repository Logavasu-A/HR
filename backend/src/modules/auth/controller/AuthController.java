package modules.auth.controller;

import modules.auth.model.ChangePasswordRequest;
import modules.auth.model.LoginRequest;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import shared.common.ApiException;
import modules.audit.model.AuditLog;
import modules.audit.repository.AuditLogRepo;
import modules.admin.model.AppUser;
import modules.admin.repository.UserRepo;
import shared.security.JwtService;
import shared.security.CurrentUser;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepo users;
    private final AuditLogRepo auditLogs;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest req, HttpServletRequest http) {
        String username = req.username() == null ? "" : req.username().trim();
        AppUser user = users.findByUsernameIgnoreCase(username).orElse(null);
        boolean ok = user != null && user.isActive() && req.password() != null
                && encoder.matches(req.password(), user.getPasswordHash());
        auditLogs.save(AuditLog.of(username, ok ? "LOGIN" : "LOGIN_FAILED", "POST", "/api/auth/login",
                ok ? 200 : 401, http.getRemoteAddr()));
        if (!ok) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Wrong username or password");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("token", jwt.create(user.getUsername()));
        out.put("user", userInfo(user));
        return out;
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
