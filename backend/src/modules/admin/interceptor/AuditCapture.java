package modules.admin.interceptor;

import java.util.UUID;
import java.util.regex.Pattern;

import modules.platform.PlatformAuth;
import shared.security.AuthUser;
import shared.tenancy.TenantDatabaseScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.platform.audit-center.enabled", havingValue = "true")
public class AuditCapture {
    private static final String REQUEST_ID = AuditCapture.class.getName() + ".requestId";
    private static final Pattern SAFE_ROUTE = Pattern.compile(
            "^(/api/(?:platform/(?:audit|auth|control-tower|workspace|access|companies)|"
            + "payroll/(?:runs|structures|components|assignments)|sensitive/(?:approvals|step-up)|"
            + "me/payslips|core/employees|auth|core|admin|attendance|leave|expense|recruitment|"
            + "performance|dashboard|me|notifications))(?:/|$)");
    private final JdbcTemplate jdbc;
    private final TenantDatabaseScope scope;

    public static UUID requestId(HttpServletRequest request) {
        if (request.getAttribute(REQUEST_ID) instanceof UUID id) return id;
        UUID id = UUID.randomUUID();
        request.setAttribute(REQUEST_ID, id);
        return id;
    }

    public void start(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("X-Request-Id", requestId(request).toString());
    }

    public void company(AuthUser user, HttpServletRequest request, int status) {
        String method = request.getMethod();
        String path = request.getServletPath();
        if (!path.startsWith("/api/") || "OPTIONS".equals(method)) return;
        // Existing MVC and financial audit writers record successful mutations.
        if (status < 400 && !"GET".equals(method) && !"HEAD".equals(method)) return;
        scope.transaction(user.tenantId(), tx -> {
            jdbc.update("""
                    insert into public.audit_log(tenant_id,username,actor_key,actor_role,request_id,action,method,path,status,ip,at)
                    values (?,?,?,?,?,?,?,?,?,?,localtimestamp)
                    """, user.tenantId(), user.username(), user.id(), user.role(), requestId(request),
                    status == 401 || status == 403 ? "AUTHORIZATION_DENIED" : "REQUEST_COMPLETED",
                    method, safeRoute(path), status, request.getRemoteAddr());
            return null;
        });
    }

    public void platform(PlatformAuth.Owner owner, HttpServletRequest request, int status) {
        if ("OPTIONS".equals(request.getMethod()) || status < 400 && !"POST".equals(request.getMethod())
                && !"PUT".equals(request.getMethod()) && !"DELETE".equals(request.getMethod())) return;
        String path = request.getServletPath();
        jdbc.update("""
                insert into public.platform_audit_event(actor,actor_id,actor_role,action,request_method,request_path,
                     http_status,request_id,remote_address)
                values (?,?,?,?,?,?,?,?,?)
                """, owner == null ? "anonymous" : owner.username(), owner == null ? null : owner.id(),
                owner == null ? null : owner.role().name(),
                status == 401 || status == 403 ? "AUTHORIZATION_DENIED" : "PLATFORM_REQUEST",
                request.getMethod(), safeRoute(path), status,
                requestId(request), request.getRemoteAddr());
    }

    private static String safeRoute(String path) {
        if (path.matches("/api/core/employees/[0-9]+/bank-details/?")) return "/api/core/employees/0/bank-details";
        if (path.matches("/api/payroll/runs/[0-9]+/submit/?")) return "/api/payroll/runs/0/submit";
        if (path.matches("/api/payroll/runs/[0-9]+/slips/?")) return "/api/payroll/runs/0/slips";
        var match = SAFE_ROUTE.matcher(path);
        return match.find() ? match.group(1) : "/api/restricted";
    }
}
