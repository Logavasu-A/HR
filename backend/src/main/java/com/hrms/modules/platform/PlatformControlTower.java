package com.hrms.modules.platform;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import java.util.Map;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hrms.shared.common.ApiException;
import com.hrms.shared.tenancy.TenantDatabaseScope;
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
@ConditionalOnProperty(name = {"app.platform.enabled", "app.platform.control-tower.enabled"}, havingValue = "true")
@RequestMapping("/api/platform/control-tower")
public class PlatformControlTower {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TenantDatabaseScope scope;
    private final PlatformAuth auth;
    private final boolean finance;

    public PlatformControlTower(JdbcTemplate jdbc, ObjectMapper json, TenantDatabaseScope scope, PlatformAuth auth,
                                @Value("${app.finance.controls:false}") boolean finance) {
        this.jdbc = jdbc;
        this.json = json;
        this.scope = scope;
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
        // Procedure check is optional; fallback Java reporting is used if hrms_rls is absent.
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
        boolean hasProcedure = scope.enabled() && Boolean.TRUE.equals(jdbc.queryForObject(
                "select to_regprocedure('hrms_rls.control_tower(date,integer,uuid)') is not null", Boolean.class));
        ObjectNode result;
        if (hasProcedure) {
            String document = jdbc.queryForObject(
                    "select hrms_rls.control_tower(?, ?, ?)::text", String.class, period.atDay(1), months, companyId);
            result = (ObjectNode) json.readTree(document);
        } else {
            result = buildControlTowerReport(period, months, companyId);
        }
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

    private ObjectNode buildControlTowerReport(YearMonth period, int months, UUID companyFilter) {
        ObjectNode root = json.createObjectNode();
        ArrayNode companiesNode = root.putArray("companies");
        ArrayNode monthsNode = root.putArray("months");

        String companySql = "select id, name, code, status, currency, timezone from company"
                + (companyFilter != null ? " where id = ?" : " order by name asc");
        List<Map<String, Object>> companyRows = companyFilter != null
                ? jdbc.queryForList(companySql, companyFilter)
                : jdbc.queryForList(companySql);

        for (var row : companyRows) {
            UUID cid = (UUID) row.get("id");
            ObjectNode cNode = companiesNode.addObject();
            cNode.put("id", cid.toString());
            cNode.put("name", (String) row.get("name"));
            cNode.put("code", (String) row.get("code"));
            cNode.put("status", (String) row.get("status"));
            cNode.put("currency", row.get("currency") != null ? (String) row.get("currency") : "USD");
            cNode.put("timezone", row.get("timezone") != null ? (String) row.get("timezone") : "UTC");

            Long empCount = queryCount("select count(*) from employee where tenant_id = ?", cid);
            Long activeEmpCount = queryCount("select count(*) from employee where tenant_id = ? and status = 'ACTIVE'", cid);
            cNode.put("employees", empCount);
            cNode.put("activeEmployees", activeEmpCount);

            ObjectNode counts = cNode.putObject("counts");
            counts.put("attendanceRecords", queryCount("select count(*) from attendance where tenant_id = ?", cid));
            counts.put("worked", queryCount("select count(*) from attendance where tenant_id = ? and status in ('PRESENT', 'LATE', 'HALF_DAY')", cid));
            counts.put("absences", queryCount("select count(*) from attendance where tenant_id = ? and status = 'ABSENT'", cid));
            counts.put("leaveRequests", queryCount("select count(*) from leave_application where tenant_id = ?", cid));
            counts.put("leaveApproved", queryCount("select count(*) from leave_application where tenant_id = ? and status = 'APPROVED'", cid));
            counts.put("jobsPosted", queryCount("select count(*) from job_opening where tenant_id = ? and status = 'OPEN'", cid));
            counts.put("applicants", queryCount("select count(*) from job_applicant where tenant_id = ?", cid));

            ArrayNode depts = cNode.putArray("departments");
            List<Map<String, Object>> deptRows = jdbc.queryForList("""
                    select coalesce(d.name, 'Unassigned') as name, count(*) as employees,
                           count(*) filter (where e.status = 'ACTIVE') as active
                      from employee e left join department d on d.id = e.department_id and d.tenant_id = e.tenant_id
                     where e.tenant_id = ?
                     group by d.name order by active desc, name
                    """, cid);
            for (var d : deptRows) {
                ObjectNode dNode = depts.addObject();
                dNode.put("name", (String) d.get("name"));
                dNode.put("employees", ((Number) d.get("employees")).longValue());
                dNode.put("active", ((Number) d.get("active")).longValue());
            }

            Double netPayroll = queryDouble("select sum(total_net) from payroll_run where tenant_id = ? and status = 'SUBMITTED'", cid);
            cNode.put("netPayroll", netPayroll != null ? netPayroll : 0.0);
        }

        for (int i = 0; i < months; i++) {
            YearMonth ym = period.plusMonths(i);
            ObjectNode mNode = monthsNode.addObject();
            mNode.put("month", ym.toString());
            ObjectNode mCounts = mNode.putObject("counts");

            String start = ym.atDay(1).toString();
            String end = ym.atEndOfMonth().toString();
            String cidCond = companyFilter != null ? " and tenant_id = ? " : " ";

            mCounts.put("worked", queryCountPeriod("select count(*) from attendance where status in ('PRESENT', 'LATE', 'HALF_DAY') and attendance_date between ?::date and ?::date" + cidCond, start, end, companyFilter));
            mCounts.put("absences", queryCountPeriod("select count(*) from attendance where status = 'ABSENT' and attendance_date between ?::date and ?::date" + cidCond, start, end, companyFilter));
            mCounts.put("leaveRequests", queryCountPeriod("select count(*) from leave_application where (from_date between ?::date and ?::date or to_date between ?::date and ?::date)" + cidCond, start, end, companyFilter));
            mCounts.put("leaveApproved", queryCountPeriod("select count(*) from leave_application where status = 'APPROVED' and (from_date between ?::date and ?::date or to_date between ?::date and ?::date)" + cidCond, start, end, companyFilter));
            mCounts.put("jobsPosted", queryCountPeriod("select count(*) from job_opening where created_at between ?::timestamp and ?::timestamp" + cidCond, start + " 00:00:00", end + " 23:59:59", companyFilter));
            mCounts.put("applicants", queryCountPeriod("select count(*) from job_applicant where created_at between ?::timestamp and ?::timestamp" + cidCond, start + " 00:00:00", end + " 23:59:59", companyFilter));
            mCounts.put("joiners", queryCountPeriod("select count(*) from employee where date_of_joining between ?::date and ?::date" + cidCond, start, end, companyFilter));
            mCounts.put("leavers", queryCountPeriod("select count(*) from employee where date_of_relieving between ?::date and ?::date" + cidCond, start, end, companyFilter));
        }

        return root;
    }

    private Long queryCount(String sql, UUID companyId) {
        try {
            Long count = jdbc.queryForObject(sql, Long.class, companyId);
            return count == null ? 0L : count;
        } catch (Exception e) {
            return 0L;
        }
    }

    private Long queryCountPeriod(String sql, String start, String end, UUID companyId) {
        try {
            Long count = companyId != null
                    ? jdbc.queryForObject(sql, Long.class, start, end, companyId)
                    : jdbc.queryForObject(sql, Long.class, start, end);
            return count == null ? 0L : count;
        } catch (Exception e) {
            return 0L;
        }
    }

    private Double queryDouble(String sql, UUID companyId) {
        try {
            return jdbc.queryForObject(sql, Double.class, companyId);
        } catch (Exception e) {
            return 0.0;
        }
    }
}
