package com.hrms.modules.audit.interceptor;

import com.hrms.modules.audit.model.AuditLog;
import com.hrms.modules.audit.repository.AuditLogRepo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.hrms.shared.security.CurrentUser;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/** Writes an audit_log row for every API call that changes data. */
@Component
@RequiredArgsConstructor
public class AuditInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuditInterceptor.class);

    private final AuditLogRepo auditLogs;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if ("GET".equals(method) || "OPTIONS".equals(method)) {
            return;
        }
        try {
            auditLogs.save(AuditLog.of(CurrentUser.usernameOrSystem(), actionOf(method, path), method, path,
                    response.getStatus(), request.getRemoteAddr()));
        } catch (RuntimeException e) {
            log.warn("Could not write audit log for {} {}", method, path, e);
        }
    }

    private static String actionOf(String method, String path) {
        if ("PUT".equals(method)) {
            return "UPDATE";
        }
        if ("DELETE".equals(method)) {
            return "DELETE";
        }
        String last = path.substring(path.lastIndexOf('/') + 1);
        String[] seg = path.split("/");
        // POST /api/module/resource -> CREATE ; POST /api/module/resource/5/approve -> APPROVE
        return seg.length > 4 && !last.isEmpty() && !Character.isDigit(last.charAt(0))
                ? last.toUpperCase().replace('-', '_')
                : "CREATE";
    }
}
