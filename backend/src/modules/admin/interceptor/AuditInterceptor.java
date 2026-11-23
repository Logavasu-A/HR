package modules.admin.interceptor;

import java.util.UUID;

import modules.admin.model.AuditLog;
import modules.admin.repository.AuditLogRepo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import shared.security.CurrentUser;
import shared.tenancy.TenantDatabaseScope;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/** Writes an audit_log row for every API call that changes data. */
@Component
@RequiredArgsConstructor
public class AuditInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuditInterceptor.class);
    private static final String REJECTED_OPERATION = AuditInterceptor.class.getName() + ".rejectedOperation";

    private final AuditLogRepo auditLogs;
    private final TenantDatabaseScope tenantScope;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if ("GET".equals(method) || "OPTIONS".equals(method)) {
            return;
        }
        var entry = AuditLog.of(CurrentUser.usernameOrSystem(), actionOf(method, path), method, path,
                response.getStatus(), request.getRemoteAddr());
        if (tenantScope.enabled() && response.getStatus() >= 400) {
            request.setAttribute(REJECTED_OPERATION, entry);
            return;
        }
        try {
            auditLogs.save(entry);
        } catch (RuntimeException e) {
            log.warn("Could not write audit log for {} {}", method, path, e);
        }
    }

    /** Saves a rejected operation after the request transaction has rolled back. */
    public void flushRejected(HttpServletRequest request, UUID tenant) {
        if (!(request.getAttribute(REJECTED_OPERATION) instanceof AuditLog entry)) return;
        request.removeAttribute(REJECTED_OPERATION);
        try {
            tenantScope.transaction(tenant, status -> auditLogs.save(entry));
        } catch (RuntimeException e) {
            log.warn("Could not write audit log for {} {}", request.getMethod(), request.getRequestURI(), e);
        }
    }

    public void deferFinancialDenial(HttpServletRequest request, int status, UUID correlation) {
        request.setAttribute(REJECTED_OPERATION, AuditLog.of(CurrentUser.usernameOrSystem(),
                "FINANCIAL_DENIAL", request.getMethod(), request.getServletPath() + "#" + correlation,
                status, request.getRemoteAddr()));
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
