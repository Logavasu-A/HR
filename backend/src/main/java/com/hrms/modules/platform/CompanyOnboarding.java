package com.hrms.modules.platform;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Currency;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import com.hrms.shared.common.ApiException;

@Service
@RequiredArgsConstructor
public class CompanyOnboarding {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final SecureRandom random = new SecureRandom();

    public record CompanyDetails(@NotBlank @Size(max = 160) String name,
                                 @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-]{1,39}") String code,
                                 @NotBlank @Email @Size(max = 254) String contactEmail,
                                 @NotBlank @Size(max = 80) String timezone,
                                 @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency) {}
    public record Invitation(UUID id, String recipient, String secret) {}

    @Transactional
    public Invitation create(CompanyDetails details, String adminEmail, String actor) {
        validateLocale(details);
        UUID companyId = jdbc.queryForObject("""
                insert into company (name, code, contact_email, timezone, currency)
                values (?, ?, ?, ?, ?) returning id
                """, UUID.class, details.name().trim(), details.code(), details.contactEmail(),
                details.timezone(), details.currency());
        Long roleId = jdbc.queryForObject("insert into role (name, tenant_id) values ('Company Admin', ?) returning id",
                Long.class, companyId);
        jdbc.update("insert into role_permission (role_id, permission) values (?, '*')", roleId);
        audit(actor, companyId, "CREATE_COMPANY");
        return invite(companyId, adminEmail, actor);
    }

    @Transactional
    public Invitation resend(UUID companyId, String actor) {
        if (!"PENDING".equals(lockCompany(companyId))) throw ApiException.badRequest("Company is not pending");
        String email = jdbc.query("""
                select email from company_admin_invitation where tenant_id = ? order by created_at desc limit 1
                """, (rs, row) -> rs.getString(1), companyId).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Invitation not found"));
        return invite(companyId, email, actor);
    }

    private Invitation invite(UUID companyId, String email, String actor) {
        revokeOutstanding(companyId);
        byte[] entropy = new byte[32];
        random.nextBytes(entropy);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
        UUID id = jdbc.queryForObject("""
                insert into company_admin_invitation (tenant_id, email, secret_hash, expires_at)
                values (?, ?, ?, current_timestamp + interval '48 hours') returning id
                """, UUID.class, companyId, email, digest(secret));
        audit(actor, companyId, "ISSUE_ADMIN_INVITATION");
        return new Invitation(id, email, secret);
    }

    @Transactional
    public void revoke(UUID companyId, String actor) {
        if (!"PENDING".equals(lockCompany(companyId))) throw ApiException.badRequest("Company is not pending");
        revokeOutstanding(companyId);
        audit(actor, companyId, "REVOKE_ADMIN_INVITATION");
    }

    private void revokeOutstanding(UUID companyId) {
        jdbc.update("""
                update company_admin_invitation set revoked_at = current_timestamp
                 where tenant_id = ? and accepted_at is null and revoked_at is null
                """, companyId);
    }

    @Transactional
    public String accept(String secret, String password) {
        if (!secret.matches("[A-Za-z0-9_-]{43}") || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw ApiException.badRequest("Invalid invitation or password");
        }
        String hash = digest(secret);
        UUID companyId = jdbc.query("select tenant_id from company_admin_invitation where secret_hash = ?",
                (rs, row) -> rs.getObject(1, UUID.class), hash).stream().findFirst()
                .orElseThrow(() -> ApiException.badRequest("Invitation is invalid or expired"));
        if (!"PENDING".equals(lockCompany(companyId))) throw ApiException.badRequest("Invitation is no longer available");
        String email = jdbc.query("""
                update company_admin_invitation set accepted_at = current_timestamp
                 where tenant_id = ? and secret_hash = ? and accepted_at is null and revoked_at is null
                   and expires_at > current_timestamp returning email
                """, (rs, row) -> rs.getString(1), companyId, hash).stream().findFirst()
                .orElseThrow(() -> ApiException.badRequest("Invitation is invalid or expired"));
        int inserted = jdbc.update("""
                insert into app_user (tenant_id, username, email, password_hash, role_id)
                select ?, ?, ?, ?, id from role where tenant_id = ? and name = 'Company Admin'
                """, companyId, email, email, passwords.encode(password), companyId);
        if (inserted != 1) throw new IllegalStateException("Initial admin role is missing");
        jdbc.update("update company set status = 'ACTIVE' where id = ?", companyId);
        audit("initial-admin", companyId, "ACCEPT_ADMIN_INVITATION");
        return jdbc.queryForObject("select code from company where id = ?", String.class, companyId);
    }

    @Transactional
    public void edit(UUID companyId, CompanyDetails details, String actor) {
        validateLocale(details);
        lockCompany(companyId);
        jdbc.update("update company set name = ?, code = ?, contact_email = ?, timezone = ?, currency = ? where id = ?",
                details.name().trim(), details.code(), details.contactEmail(), details.timezone(), details.currency(), companyId);
        audit(actor, companyId, "EDIT_COMPANY");
    }

    @Transactional
    public void changeStatus(UUID companyId, boolean suspend, String actor) {
        String expected = suspend ? "ACTIVE" : "SUSPENDED";
        if (!expected.equals(lockCompany(companyId))) throw ApiException.badRequest("Company status cannot be changed");
        jdbc.update("update company set status = ? where id = ?", suspend ? "SUSPENDED" : "ACTIVE", companyId);
        audit(actor, companyId, suspend ? "SUSPEND_COMPANY" : "REACTIVATE_COMPANY");
    }

    public void deliveryResult(UUID id, boolean delivered) {
        jdbc.update("update company_admin_invitation set delivery_status = ? where id = ?", delivered ? "SENT" : "FAILED", id);
    }

    public Map<String, Object> details(UUID id) {
        return jdbc.queryForList("""
                select c.*, i.email as admin_email, i.delivery_status,
                       case when i.accepted_at is not null then 'ACCEPTED'
                            when i.revoked_at is not null then 'REVOKED'
                            when i.expires_at <= current_timestamp then 'EXPIRED'
                            when i.id is not null then 'PENDING' end as invitation_status
                  from company c left join lateral (
                      select * from company_admin_invitation where tenant_id = c.id order by created_at desc limit 1
                  ) i on true where c.id = ?
                """, id).stream().findFirst().orElseThrow(() -> ApiException.notFound("Company not found"));
    }

    private String lockCompany(UUID id) {
        return jdbc.query("select status from company where id = ? for update", (rs, row) -> rs.getString(1), id)
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("Company not found"));
    }

    private void audit(String actor, UUID company, String action) {
        jdbc.update("insert into platform_audit_event (actor, tenant_id, action) values (?, ?, ?)", actor, company, action);
    }

    private void validateLocale(CompanyDetails details) {
        try {
            ZoneId.of(details.timezone());
            Currency.getInstance(details.currency());
        } catch (RuntimeException e) {
            throw ApiException.badRequest("Provide a valid time zone and currency");
        }
    }

    private String digest(String secret) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
