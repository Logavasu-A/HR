package com.hrms.modules.platform;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hrms.shared.common.ApiException;
import com.hrms.shared.tenancy.TenantDatabaseScope;

@Service
@Profile("local-isolation")
@ConditionalOnProperty(name = "app.platform.admin-view.enabled", havingValue = "true")
public class OwnerWorkspaces {
    private static final String CURRENT = """
            select w.id, c.id company_id, c.name, c.code, c.status, c.timezone, c.currency,
                   w.reason, w.opened_at, s.expires_at
              from public.platform_workspace w
              join public.company c on c.id = w.company_id
              join public.platform_sessions s on s.id = w.session_id
              join public.platform_users u on u.id = s.user_id
             where w.session_id = ? and s.user_id = ? and u.active and s.expires_at > clock_timestamp()
            """;

    public record Workspace(UUID id, UUID companyId, String name, String code, String status, String timezone,
                            String currency, String reason, OffsetDateTime openedAt, OffsetDateTime expiresAt) {}

    public record Request(UUID id, String method, String path, String remoteAddress) {}

    private final JdbcTemplate jdbc;
    private final TenantDatabaseScope scope;
    private final PlatformAuth auth;

    public OwnerWorkspaces(JdbcTemplate jdbc, TenantDatabaseScope scope, PlatformAuth auth) {
        this.jdbc = jdbc;
        this.scope = scope;
        this.auth = auth;
    }

    @PostConstruct
    void verify() {
        if (!scope.enabled() || !Boolean.TRUE.equals(jdbc.queryForObject(
                """
                select to_regclass('public.platform_workspace') is not null
                   and to_regprocedure('hrms_rls.workspace_lock_session(uuid,uuid)') is not null
                """, Boolean.class))) {
            throw new IllegalStateException("Admin View requires the local workspace migration and tenant RLS.");
        }
    }

    TenantDatabaseScope scope() {
        return scope;
    }

    PlatformAuth.Owner authenticate(String token) {
        var owner = auth.authenticate(token);
        return owner != null && owner.role() == PlatformRole.GLOBAL_SUPER_ADMIN ? owner : null;
    }

    public Workspace current(PlatformAuth.Owner owner) {
        return jdbc.query(CURRENT, OwnerWorkspaces::workspace, owner.sessionId(), owner.id())
                .stream().findFirst().orElse(null);
    }

    Workspace resolve(PlatformAuth.Owner owner, UUID grant, boolean lock) {
        String sql = CURRENT + " and w.id = ?" + (lock ? " for share of w, c" : "");
        return jdbc.query(sql, OwnerWorkspaces::workspace, owner.sessionId(), owner.id(), grant)
                .stream().findFirst().orElse(null);
    }

    @Transactional
    public Workspace open(PlatformAuth.Owner owner, UUID companyId, UUID expected, String reason, Request request) {
        String why = reason.strip();
        if (why.length() < 5 || why.chars().anyMatch(Character::isISOControl)) {
            throw ApiException.badRequest("Enter an access reason using 5 to 300 characters on one line.");
        }
        lockSession(owner);
        Workspace old = current(owner);
        if (!Objects.equals(expected, old == null ? null : old.id())) throw replaced();
        if (!Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select from public.company where id = ?)", Boolean.class, companyId))) {
            throw ApiException.notFound("Company not found");
        }
        UUID grant = UUID.randomUUID();
        jdbc.update("""
                insert into public.platform_workspace (session_id, id, company_id, reason) values (?, ?, ?, ?)
                on conflict (session_id) do update
                   set id = excluded.id, company_id = excluded.company_id, reason = excluded.reason,
                       opened_at = current_timestamp
                """, owner.sessionId(), grant, companyId, why);
        Workspace active = current(owner);
        if (active == null) throw expired();
        record(owner, active, grant, old == null ? "WORKSPACE_OPEN" : "WORKSPACE_SWITCH",
                old == null ? null : old.companyId(), request, 200);
        return active;
    }

    @Transactional
    public void close(PlatformAuth.Owner owner, UUID grant, Request request) {
        lockSession(owner);
        Workspace current = current(owner);
        if (current == null || !grant.equals(current.id())) throw replaced();
        jdbc.update("delete from public.platform_workspace where session_id = ? and id = ?", owner.sessionId(), grant);
        record(owner, current, grant, "WORKSPACE_CLOSE", null, request, 204);
    }

    private void lockSession(PlatformAuth.Owner owner) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("select hrms_rls.workspace_lock_session(?, ?)",
                Boolean.class, owner.sessionId(), owner.id()))) {
            throw expired();
        }
    }

    static boolean fresh(PlatformAuth.Owner owner) {
        return !owner.authenticatedAt().isBefore(Instant.now().minusSeconds(300));
    }

    void record(PlatformAuth.Owner owner, Workspace workspace, UUID grant, String action, UUID previous,
                Request request, int status) {
        jdbc.update("""
                insert into public.platform_audit_event
                    (actor, actor_id, tenant_id, workspace_id, action, previous_tenant_id, reason,
                     request_method, request_path, http_status, request_id, remote_address)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, owner.username(), owner.id(), workspace == null ? null : workspace.companyId(), grant, action,
                previous, workspace == null ? null : workspace.reason(), limit(request.method(), 8),
                limit(request.path(), 500), status, request.id(), limit(request.remoteAddress(), 60));
    }

    private static String limit(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    static ApiException replaced() {
        return new ApiException(HttpStatus.GONE, "This Admin View was closed or replaced. Reopen the selected company.");
    }

    private static ApiException expired() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Your platform session has ended.");
    }

    private static Workspace workspace(ResultSet rs, int row) throws SQLException {
        return new Workspace(rs.getObject("id", UUID.class), rs.getObject("company_id", UUID.class),
                rs.getString("name"), rs.getString("code"), rs.getString("status"), rs.getString("timezone"),
                rs.getString("currency"), rs.getString("reason"), rs.getObject("opened_at", OffsetDateTime.class),
                rs.getObject("expires_at", OffsetDateTime.class));
    }
}
