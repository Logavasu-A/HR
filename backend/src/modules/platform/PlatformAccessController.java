package modules.platform;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shared.common.ApiException;

@RestController
@RequestMapping("/api/platform/access")
@RequiredArgsConstructor
@ConditionalOnProperty(name = {"app.platform.enabled", "app.platform.rbac.enabled"}, havingValue = "true")
public class PlatformAccessController {
    private final JdbcTemplate jdbc;

    public record Change(@NotBlank String role, @NotBlank @Size(max = 200) String reason) {}

    @GetMapping
    public List<Map<String, Object>> list() {
        return jdbc.queryForList("""
                select u.id, u.username, u.active, r.role from public.platform_users u
                left join hrms_rls.platform_role r on r.user_id = u.id order by u.username
                """);
    }

    @PutMapping("/{id}")
    @Transactional
    public void change(@PathVariable UUID id, @Valid @RequestBody Change change,
                       @AuthenticationPrincipal PlatformAuth.Owner actor, HttpServletRequest request) {
        if (!OwnerWorkspaces.fresh(actor)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Sign in again to change platform roles.");
        }
        if (actor.id().equals(id)) throw ApiException.forbidden("You cannot change your own platform role.");
        var locked = jdbc.queryForList("""
                select id from public.platform_users where id in (?, ?) order by id for update
                """, UUID.class, actor.id(), id);
        if (locked.size() != 2) throw ApiException.notFound("Platform account not found.");
        var actorRole = role(actor.id());
        var previous = role(id);
        PlatformRole next;
        try {
            next = "UNASSIGNED".equals(change.role()) ? null : PlatformRole.valueOf(change.role());
        } catch (IllegalArgumentException invalidRole) {
            throw ApiException.badRequest("Choose a supported platform role.");
        }
        if (actorRole != actor.role() || previous == PlatformRole.GLOBAL_SUPER_ADMIN || next == PlatformRole.GLOBAL_SUPER_ADMIN
                || (actorRole != PlatformRole.GLOBAL_SUPER_ADMIN && (actorRole != PlatformRole.SECURITY_ADMINISTRATOR
                    || previous == PlatformRole.SECURITY_ADMINISTRATOR || next == PlatformRole.SECURITY_ADMINISTRATOR))) {
            throw ApiException.forbidden("You cannot administer this platform role.");
        }
        if (previous == next) return;
        if (next == null) {
            jdbc.update("delete from hrms_rls.platform_role where user_id = ?", id);
        } else {
            jdbc.update("""
                    insert into hrms_rls.platform_role (user_id, role) values (?, ?)
                    on conflict (user_id) do update set role = excluded.role
                    """, id, next.name());
        }
        jdbc.update("delete from public.platform_sessions where user_id = ?", id);
        jdbc.update("""
                insert into public.platform_audit_event
                    (actor, actor_id, action, reason, request_method, request_path, http_status, request_id, remote_address)
                values (?, ?, 'PLATFORM_ROLE_CHANGE', ?, 'PUT', ?, 200, ?, ?)
                """, actor.username(), actor.id(),
                (previous == null ? "UNASSIGNED" : previous.name()) + " → "
                        + (next == null ? "UNASSIGNED" : next.name()) + " (" + actorRole.name() + ") " + change.reason().strip(),
                request.getServletPath(), UUID.randomUUID(), request.getRemoteAddr());
    }

    private PlatformRole role(UUID id) {
        return jdbc.query("select role from hrms_rls.platform_role where user_id = ?",
                (row, index) -> PlatformRole.valueOf(row.getString(1)), id).stream().findFirst().orElse(null);
    }
}
