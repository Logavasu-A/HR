package modules.platform;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import shared.common.ApiException;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = {"app.platform.enabled", "app.platform.control-tower.enabled", "app.tenancy.rls.enabled"},
        havingValue = "true")
@RequestMapping("/api/platform/control-tower")
public class PlatformControlTower {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final PlatformAuth auth;
    private final boolean finance;

    public PlatformControlTower(JdbcTemplate jdbc, ObjectMapper json, PlatformAuth auth,
                                @Value("${app.finance.controls:false}") boolean finance) {
        this.jdbc = jdbc;
        this.json = json;
        this.auth = auth;
        this.finance = finance;
    }

    public record Password(@NotNull @Size(max = 72) String password) {}

    @PostMapping("/step-up")
    public ResponseEntity<Map<String, Boolean>> stepUp(@AuthenticationPrincipal PlatformAuth.Owner owner,
                                                       @Valid @RequestBody Password password) {
        if (!finance) throw ApiException.notFound("Financial verification is unavailable.");
        UUID id = UUID.randomUUID();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Request-Id", id.toString())
                .body(Map.of("verified", auth.confirmGlobalFinance(owner, password.password(), id)));
    }

    @PostConstruct
    void verify() {
        if (!Boolean.TRUE.equals(jdbc.queryForObject(
                "select to_regprocedure('hrms_rls.control_tower(date,integer,uuid)') is not null", Boolean.class))) {
            throw new IllegalStateException("Control Tower requires the provisioned RLS reporting function.");
        }
    }

    @GetMapping
    @Transactional(timeout = 10)
    public ResponseEntity<JsonNode> report(@RequestParam(required = false) Integer year,
                                           @RequestParam(required = false) Integer month,
                                           @RequestParam(required = false) UUID companyId,
                                           @AuthenticationPrincipal PlatformAuth.Owner owner,
                                           HttpServletRequest request) throws JsonProcessingException {
        int selectedYear = year == null ? LocalDate.now().getYear() : year;
        if (selectedYear < 1 || selectedYear > 9999 || (month != null && (month < 1 || month > 12))) {
            throw ApiException.badRequest("Choose a valid reporting year and month.");
        }

        YearMonth period = YearMonth.of(selectedYear, month == null ? 1 : month);
        int months = month == null ? 12 : 1;
        String document = jdbc.queryForObject(
                "select hrms_rls.control_tower(?, ?, ?)::text", String.class, period.atDay(1), months, companyId);
        ObjectNode result = (ObjectNode) json.readTree(document);
        if (companyId != null && result.path("companies").isEmpty()) throw ApiException.notFound("Company not found");
        boolean payrollAllowed = !finance || Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from platform_sessions where id=? and user_id=? and expires_at>clock_timestamp()
                  and finance_verified_at>clock_timestamp()-interval '5 minutes')
                """, Boolean.class, owner.sessionId(), owner.id()));
        result.put("payrollLocked", !payrollAllowed);
        if (!payrollAllowed) {
            result.putArray("payroll");
            result.path("months").forEach(monthRow -> ((ObjectNode) monthRow).putArray("payroll"));
            result.path("companies").forEach(company -> ((ObjectNode) company).putNull("netPayroll"));
        }

        UUID requestId = UUID.randomUUID();
        jdbc.update("""
                insert into public.platform_audit_event
                    (actor, actor_id, tenant_id, action, reason, request_method, request_path, http_status, request_id, remote_address)
                values (?, ?, ?, 'CONTROL_TOWER_READ', ?, 'GET', '/api/platform/control-tower', 200, ?, ?)
                """, owner.username(), owner.id(), companyId,
                "Reporting period " + period + " through " + period.plusMonths(months - 1), requestId, request.getRemoteAddr());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("X-Request-Id", requestId.toString()).body(result);
    }
}
