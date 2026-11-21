package com.hrms.shared.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.hrms.modules.platform.OwnerWorkspacePrincipal;
import com.hrms.shared.common.ApiException;
import com.hrms.shared.tenancy.TenantDatabaseScope;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.finance.controls", havingValue = "true")
public class FinanceControls {
    private static final Set<String> REASONS = Set.of("REGULAR_PROCESSING", "RECORD_CORRECTION", "AUTHORIZED_CHANGE");
    private static final Map<String, String> TABLES = Map.of(
            "assignments", "salary_assignment", "structures", "salary_structure", "components", "salary_component",
            "runs", "payroll_run");
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final SecretKeySpec key;

    public FinanceControls(JdbcTemplate jdbc, PasswordEncoder passwords, TenantDatabaseScope scope,
                           @Value("${app.tenancy.rls.key}") String secret) {
        if (!scope.abacEnabled()) throw new IllegalStateException("Financial controls require enforced tenant RLS.");
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.key = new SecretKeySpec(HexFormat.of().parseHex(secret), "HmacSHA256");
    }

    @jakarta.annotation.PostConstruct
    void verifyDatabase() {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                select count(*)=2 from pg_class c join pg_namespace n on n.oid=c.relnamespace
                where n.nspname='hrms_rls' and c.relname in ('finance_approval','finance_event')
                  and c.relrowsecurity and c.relforcerowsecurity
                  and exists (select 1 from pg_policy p where p.polrelid=c.oid and p.polname='finance_tenant')
                """, Boolean.class))) throw new IllegalStateException("Financial tables require forced RLS.");
    }

    record Actor(UUID tenant, String id, String role, UUID session, String workspace) {}
    record Review(UUID id, String status, String maker, String method, String path) {}

    Actor actor(HttpServletRequest request) {
        var principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof OwnerWorkspacePrincipal owner) {
            return new Actor(owner.tenantId(), "platform:" + owner.owner().id(), owner.owner().role().name(),
                    owner.owner().sessionId(), request.getHeader("X-HRMS-Workspace"));
        }
        var user = CurrentUser.get();
        return new Actor(user.tenantId(), "company:" + user.id(), user.role(), user.sessionId(), "");
    }

    boolean stepUp(Actor actor, String password, HttpServletRequest request) {
        lock(actor.tenant() + ":" + actor.id());
        Long failures = jdbc.queryForObject("""
                select count(*) from hrms_rls.finance_event where tenant_id=? and actor=?
                and action='STEP_UP_FAILED' and occurred_at > clock_timestamp() - interval '5 minutes'
                """, Long.class, actor.tenant(), actor.id());
        if (failures >= 5) {
            event(actor, "STEP_UP_THROTTLED", null, request, 429);
            return false;
        }
        String[] identity = actor.id().split(":", 2);
        String hash = "platform".equals(identity[0])
                ? jdbc.queryForObject("select password_hash from public.platform_users where id=? and active",
                    String.class, UUID.fromString(identity[1]))
                : jdbc.queryForObject("select password_hash from public.app_user where tenant_id=? and id=? and active",
                    String.class, actor.tenant(), Long.valueOf(identity[1]));
        boolean verified = password != null && password.getBytes(StandardCharsets.UTF_8).length <= 72
                && hash != null && passwords.matches(password, hash);
        event(actor, verified ? "STEP_UP_GRANTED" : "STEP_UP_FAILED", null, request, verified ? 200 : 401);
        return verified;
    }

    public boolean unlocked(HttpServletRequest request) {
        return unlocked(actor(request));
    }

    boolean unlocked(Actor actor) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists (select 1 from hrms_rls.finance_event where tenant_id=? and actor=?
                and session_id=? and workspace=? and action='STEP_UP_GRANTED'
                and occurred_at > clock_timestamp() - interval '5 minutes')
                """, Boolean.class, actor.tenant(), actor.id(), actor.session(), actor.workspace()));
    }

    Review requestApproval(Actor actor, String method, String path, byte[] body, String contentType, String reason) {
        if (reason == null || !REASONS.contains(reason)) throw ApiException.badRequest("Choose a financial change reason.");
        lock(actor.tenant() + ":" + actor.id());
        String fingerprint = fingerprint(actor.tenant(), method, path, body, contentType);
        var prior = jdbc.query("""
                select id,status,maker,method,path from hrms_rls.finance_approval
                where tenant_id=? and maker=? and method=? and path=? and fingerprint=?
                  and expires_at>clock_timestamp() and status in ('PENDING','APPROVED')
                order by created_at desc limit 1 for update
                """, (row, n) -> review(row), actor.tenant(), actor.id(), method, path, fingerprint);
        if (!prior.isEmpty()) return prior.getFirst();
        UUID id = jdbc.queryForObject("""
                insert into hrms_rls.finance_approval(tenant_id,maker,method,path,fingerprint,reason)
                values (?,?,?,?,?,?) returning id
                """, UUID.class, actor.tenant(), actor.id(), method, path, fingerprint, reason);
        return new Review(id, "PENDING", actor.id(), method, path);
    }

    void execute(Actor actor, Review review) {
        jdbc.update("update hrms_rls.finance_approval set status='EXECUTED' where tenant_id=? and id=?",
                actor.tenant(), review.id());
    }

    List<Map<String, Object>> queue(Actor actor) {
        var rows = jdbc.queryForList("""
                select id, maker, method, path, reason, status, checker, created_at, expires_at
                from hrms_rls.finance_approval where tenant_id=? and expires_at > clock_timestamp()
                  and (maker=? or (status in ('PENDING','APPROVED') and (
                        (path like '/api/payroll/%' and ?) or (path like '/api/core/%' and ?))))
                order by created_at desc limit 100
                """, actor.tenant(), actor.id(), canReview("/api/payroll/"), canReview("/api/core/"));
        rows.forEach(row -> row.put("canDecide", "PENDING".equals(row.get("status"))
                && !actor.id().equals(row.get("maker")) && canReview((String) row.get("path"))));
        return rows;
    }

    void decide(Actor actor, UUID id, boolean approved, HttpServletRequest request) {
        var row = jdbc.query("""
                select id,status,maker,method,path from hrms_rls.finance_approval
                where tenant_id=? and id=? and expires_at>clock_timestamp() for update
                """, (rs, n) -> review(rs), actor.tenant(), id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Financial approval not found."));
        if (!canReview(row.path()) || row.maker().equals(actor.id())) {
            throw ApiException.forbidden("A different authorized reviewer must decide this request.");
        }
        if (!"PENDING".equals(row.status())) throw ApiException.badRequest("This approval is already decided.");
        jdbc.update("update hrms_rls.finance_approval set checker=?, status=? where tenant_id=? and id=?",
                actor.id(), approved ? "APPROVED" : "REJECTED", actor.tenant(), id);
        event(actor, approved ? "CHANGE_APPROVED" : "CHANGE_REJECTED", id, request, 204);
    }

    boolean canReview(String path) {
        if (CurrentUser.isPlatformWorkspace()) return true;
        return CurrentUser.hasExplicitPermission("sensitive:approve") && (path.startsWith("/api/payroll/")
                ? CurrentUser.get().can("payroll:approve")
                : CurrentUser.hasExplicitPermission("employee-bank:read"));
    }

    void event(Actor actor, String action, UUID related, HttpServletRequest request, int status) {
        UUID id = (UUID) request.getAttribute(FinanceControls.class.getName());
        jdbc.update("""
                insert into hrms_rls.finance_event(tenant_id,actor,actor_role,session_id,workspace,action,
                     related_id,path,method,status,ip,request_id) values (?,?,?,?,?,?,?,?,?,?,?,?)
                """, actor.tenant(), actor.id(), actor.role(), actor.session(), actor.workspace(), action, related,
                request.getServletPath(), request.getMethod(), status, request.getRemoteAddr(), id);
    }

    private void lock(String key) {
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> {}, key);
    }

    private String fingerprint(UUID tenant, String method, String path, byte[] body, String contentType) {
        String[] pieces = path.split("/");
        String version = "";
        if (pieces.length >= 5) {
            String table = "core".equals(pieces[2]) ? "employee" : TABLES.get(pieces[3]);
            long id;
            try {
                id = Long.parseLong(pieces[4]);
            } catch (NumberFormatException e) {
                throw ApiException.notFound("Financial record not found.");
            }
            version = jdbc.query("select xmin::text from public." + table + " where tenant_id=? and id=? for update",
                    (rs, n) -> rs.getString(1), tenant, id).stream().findFirst()
                    .orElseThrow(() -> ApiException.notFound("Financial record not found."));
        }
        try {
            Mac signer = Mac.getInstance("HmacSHA256");
            signer.init(key);
            signer.update(("financial-change:" + tenant + ":" + method + ":" + path + ":" + version + ":" + contentType + "\n")
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(signer.doFinal(body));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot bind financial approval.", e);
        }
    }

    private static Review review(java.sql.ResultSet row) throws java.sql.SQLException {
        return new Review(row.getObject("id", UUID.class), row.getString("status"), row.getString("maker"),
                row.getString("method"), row.getString("path"));
    }
}
