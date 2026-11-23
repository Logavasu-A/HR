package modules.platform;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import modules.admin.interceptor.AuditCapture;
import shared.common.ApiException;
import shared.tenancy.TenantDatabaseScope;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local-isolation")
@ConditionalOnProperty(name = {"app.platform.enabled", "app.platform.audit-center.enabled"}, havingValue = "true")
@RequestMapping("/api/platform/audit")
public class PlatformAuditCenter {
    private static final int PAGE_SIZE = 50;
    private static final Set<String> SOURCES = Set.of("", "PLATFORM", "COMPANY", "FINANCE");
    private static final Set<String> OUTCOMES = Set.of("", "SUCCESS", "DENIED", "FAILED");

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final SecretKeySpec key;

    public PlatformAuditCenter(JdbcTemplate jdbc, ObjectMapper json, TenantDatabaseScope scope,
                               @Value("${app.tenancy.rls.key}") String secret) {
        if (!scope.abacEnabled()) throw new IllegalStateException("Audit Center requires tenant RLS.");
        this.jdbc = jdbc;
        this.json = json;
        this.key = new SecretKeySpec(HexFormat.of().parseHex(secret), "HmacSHA256");
    }

    @PostConstruct
    void verifyMigration() {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                select to_regprocedure('hrms_rls.audit_center(uuid,uuid,text,timestamptz,timestamptz,uuid,text,text,uuid,text,integer)')
                    is not null
                """, Boolean.class))) throw new IllegalStateException("Audit Center requires its local migration.");
    }

    @GetMapping
    @Transactional(timeout = 10)
    public ResponseEntity<Map<String, Object>> events(@AuthenticationPrincipal PlatformAuth.Owner owner,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime until,
            @RequestParam(required = false) UUID companyId,
            @RequestParam(defaultValue = "") String source, @RequestParam(defaultValue = "") String outcome,
            @RequestParam(required = false) UUID approvalId,
            @RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "0") int page,
            HttpServletRequest request) throws JsonProcessingException {
        if (owner == null || owner.role() != PlatformRole.GLOBAL_SUPER_ADMIN) {
            throw ApiException.forbidden("Only Global Super Admin can view the Audit Center.");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime end = until == null || until.isAfter(now) ? now : until;
        OffsetDateTime start = from == null ? end.minusDays(30) : from;
        if (!start.isBefore(end) || Duration.between(start, end).compareTo(Duration.ofDays(366)) > 0 ||
                !SOURCES.contains(source) || !OUTCOMES.contains(outcome) || page < 0 || page > 200 || search.length() > 80) {
            throw ApiException.badRequest("Choose a valid audit range, filters and page.");
        }
        String transaction = jdbc.queryForObject("select pg_backend_pid()::text || ':' || pg_current_xact_id()::text", String.class);
        String entries = jdbc.queryForObject("""
                select hrms_rls.audit_center(?,?,?,?,?,?,?,?,?,?,?)::text
                """, String.class, owner.sessionId(), owner.id(), proof(owner, transaction), start, end, companyId,
                source, outcome, approvalId, search.strip(), page * PAGE_SIZE);
        var events = (ArrayNode) json.readTree(entries);
        boolean hasNext = events.size() > PAGE_SIZE && page < 200;
        if (events.size() > PAGE_SIZE) events.remove(PAGE_SIZE);
        UUID requestId = AuditCapture.requestId(request);
        jdbc.update("""
                insert into public.platform_audit_event(actor,actor_id,actor_role,tenant_id,action,request_method,
                    request_path,http_status,request_id,remote_address)
                values (?,?,?,?,'AUDIT_CENTER_READ','GET','/api/platform/audit',200,?,?)
                """, owner.username(), owner.id(), owner.role().name(), knownCompany(companyId), requestId,
                request.getRemoteAddr());
        List<Map<String, Object>> companies = page == 0
                ? jdbc.queryForList("select id,name from public.company order by lower(name),id")
                : List.of();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Request-Id", requestId.toString())
                .body(Map.of("events", events, "hasNext", hasNext, "page", page, "companies", companies,
                        "snapshotEnd", end.toString()));
    }

    private UUID knownCompany(UUID id) {
        if (id == null) return null;
        return jdbc.query("select id from public.company where id=?", (row, index) -> row.getObject(1, UUID.class), id)
                .stream().findFirst().orElse(null);
    }

    private String proof(PlatformAuth.Owner owner, String transaction) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            String scope = "audit-center:" + owner.sessionId() + ":" + owner.id() + ":" + transaction;
            return HexFormat.of().formatHex(mac.doFinal(scope.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Cannot authorize the Audit Center database read.", error);
        }
    }
}
