package com.hrms.modules.platform;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hrms.shared.common.ApiException;

@RestController
@RequestMapping("/api/platform")
@ConditionalOnProperty(name = "app.platform.enabled", havingValue = "true")
public class PlatformCompanies {
    private static final int COMPANY_PAGE_SIZE = 20;
    private static final int ACTIVITY_PAGE_SIZE = 10;

    private final CompanyOnboarding onboarding;
    private final JdbcTemplate jdbc;
    private final ObjectProvider<JavaMailSender> mail;
    private final String mailFrom;
    private final String origin;

    public PlatformCompanies(CompanyOnboarding onboarding, JdbcTemplate jdbc, ObjectProvider<JavaMailSender> mail,
                             @Value("${app.mail.from}") String mailFrom,
                             @Value("${app.platform.public-origin}") URI publicOrigin) {
        if (!"https".equals(publicOrigin.getScheme()) || publicOrigin.getHost() == null
                || publicOrigin.getUserInfo() != null || publicOrigin.getQuery() != null || publicOrigin.getFragment() != null
                || (!publicOrigin.getPath().isEmpty() && !"/".equals(publicOrigin.getPath()))) {
            throw new IllegalArgumentException("Platform public origin must be an HTTPS origin");
        }
        this.onboarding = onboarding;
        this.jdbc = jdbc;
        this.mail = mail;
        this.mailFrom = mailFrom;
        this.origin = publicOrigin.resolve("/").toString();
    }

    public record CreateCompany(@Valid @NotNull CompanyOnboarding.CompanyDetails company,
                                @NotBlank @Email @Size(max = 80) String adminEmail) {}

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(@AuthenticationPrincipal PlatformAuth.Owner owner) {
        return Map.of("counts", jdbc.queryForMap("""
                select count(*) as total, count(*) filter (where status = 'PENDING') as pending,
                       count(*) filter (where status = 'ACTIVE') as active,
                       count(*) filter (where status = 'SUSPENDED') as suspended from company
                """),
                "activity", owner.role() == PlatformRole.GLOBAL_SUPER_ADMIN ? jdbc.queryForList("""
                        select a.action, a.occurred_at, c.name as company_name
                          from platform_audit_event a left join company c on c.id = a.tenant_id
                         order by a.id desc limit 10
                        """) : List.of());
    }

    @GetMapping("/companies")
    public Map<String, Object> companies(@RequestParam(defaultValue = "") String search,
                                         @RequestParam(defaultValue = "") String status,
                                         @RequestParam(defaultValue = "newest") String sort,
                                         @RequestParam(defaultValue = "0") int page) {
        int index = Math.max(page, 0);
        String keyword = search.strip();
        String order = switch (sort) {
            case "newest" -> "created_at desc, id";
            case "oldest" -> "created_at, id";
            case "name-asc" -> "lower(name), id";
            case "name-desc" -> "lower(name) desc, id";
            default -> throw ApiException.badRequest("Choose a valid company sort order.");
        };
        String condition = """
                 where (? = '' or status = ?) and (
                     strpos(lower(name), lower(?)) > 0 or strpos(lower(code), lower(?)) > 0
                     or strpos(lower(coalesce(contact_email, '')), lower(?)) > 0)
                """;
        Long total = jdbc.queryForObject("select count(*) from company" + condition, Long.class,
                status, status, keyword, keyword, keyword);
        List<Map<String, Object>> records = jdbc.queryForList("""
                select id, name, code, status, contact_email, created_at, timezone, currency from company
                """ + condition + " order by " + order + " limit ? offset ?",
                status, status, keyword, keyword, keyword, COMPANY_PAGE_SIZE, (long) index * COMPANY_PAGE_SIZE);
        return page(records, index, COMPANY_PAGE_SIZE, total);
    }

    @GetMapping("/activity")
    public Map<String, Object> activity(@RequestParam(defaultValue = "") String action,
                                        @RequestParam(defaultValue = "0") int page) {
        int index = Math.max(page, 0);
        String condition = " where (? = '' or a.action = ?)";
        Long total = jdbc.queryForObject("select count(*) from platform_audit_event a" + condition,
                Long.class, action, action);
        List<Map<String, Object>> events = jdbc.queryForList("""
                select a.id, a.actor, a.action, a.occurred_at, a.tenant_id as company_id, c.name as company_name,
                       to_jsonb(a)->>'reason' as reason, to_jsonb(a)->>'request_method' as request_method,
                       to_jsonb(a)->>'request_path' as request_path, to_jsonb(a)->>'http_status' as http_status,
                       to_jsonb(a)->>'request_id' as request_id
                  from platform_audit_event a left join company c on c.id = a.tenant_id
                """ + condition + " order by a.id desc limit ? offset ?",
                action, action, ACTIVITY_PAGE_SIZE, (long) index * ACTIVITY_PAGE_SIZE);
        List<String> actions = jdbc.queryForList(
                "select distinct action from platform_audit_event order by action", String.class);
        return Map.of("events", page(events, index, ACTIVITY_PAGE_SIZE, total), "actions", actions);
    }

    private static Map<String, Object> page(List<Map<String, Object>> records, int index, int size, long total) {
        return Map.of("content", records, "page", index, "size", size, "totalElements", total,
                "totalPages", (total + size - 1) / size, "first", index == 0, "last", (index + 1L) * size >= total);
    }

    @GetMapping("/companies/{id}")
    public Map<String, Object> company(@PathVariable UUID id) {
        return onboarding.details(id);
    }

    @PostMapping("/companies")
    public Map<String, Boolean> create(@Valid @RequestBody CreateCompany request, @AuthenticationPrincipal PlatformAuth.Owner owner) {
        return Map.of("delivered", deliver(onboarding.create(request.company(), request.adminEmail(), fresh(owner))));
    }

    @PutMapping("/companies/{id}")
    public void edit(@PathVariable UUID id, @Valid @RequestBody CompanyOnboarding.CompanyDetails request,
                     @AuthenticationPrincipal PlatformAuth.Owner owner) {
        onboarding.edit(id, request, fresh(owner));
    }

    @PostMapping("/companies/{id}/suspend")
    public void suspend(@PathVariable UUID id, @AuthenticationPrincipal PlatformAuth.Owner owner) {
        onboarding.changeStatus(id, true, fresh(owner));
    }

    @PostMapping("/companies/{id}/reactivate")
    public void reactivate(@PathVariable UUID id, @AuthenticationPrincipal PlatformAuth.Owner owner) {
        onboarding.changeStatus(id, false, fresh(owner));
    }

    @PostMapping("/companies/{id}/invitation/resend")
    public Map<String, Boolean> resend(@PathVariable UUID id, @AuthenticationPrincipal PlatformAuth.Owner owner) {
        return Map.of("delivered", deliver(onboarding.resend(id, fresh(owner))));
    }

    @PostMapping("/companies/{id}/invitation/revoke")
    public void revoke(@PathVariable UUID id, @AuthenticationPrincipal PlatformAuth.Owner owner) {
        onboarding.revoke(id, fresh(owner));
    }

    private String fresh(PlatformAuth.Owner owner) {
        if (owner == null || owner.authenticatedAt().isBefore(Instant.now().minusSeconds(300))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Sign in again to make this change");
        }
        return owner.username();
    }

    private boolean deliver(CompanyOnboarding.Invitation invitation) {
        boolean sent = false;
        try {
            JavaMailSender sender = mail.getIfAvailable();
            if (sender != null) {
                var message = new SimpleMailMessage();
                message.setFrom(mailFrom);
                message.setTo(invitation.recipient());
                message.setSubject("Set up your HRMS company admin account");
                message.setText("Use this one-time link within 48 hours to set your password:\n"
                        + origin + "accept-admin-invite#" + invitation.secret());
                sender.send(message);
                sent = true;
            }
        } catch (RuntimeException ignored) {
            // Record the delivery failure without exposing recipient details or the invitation URL.
        }
        onboarding.deliveryResult(invitation, sent);
        return sent;
    }
}
