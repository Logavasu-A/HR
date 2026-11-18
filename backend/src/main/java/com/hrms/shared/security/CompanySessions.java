package com.hrms.shared.security;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import com.hrms.shared.common.ApiException;

@Service
@RequiredArgsConstructor
public class CompanySessions {
    // Only the migrated workspace can authenticate until company module queries are scoped.
    private static final UUID INITIAL_TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final JdbcTemplate jdbc;

    @Value("${app.tenancy.allow-additional-companies:false}")
    private boolean allowAdditionalCompanies;

    public record Account(long userId, UUID tenantId) {}

    public Optional<Account> findAccount(String companyCode, String identifier) {
        List<Account> matches = jdbc.query("""
                select u.id, u.tenant_id
                  from app_user u join company c on c.id = u.tenant_id
                 where (? or c.id = ?) and lower(c.code) = lower(?) and c.status = 'ACTIVE'
                   and (lower(u.username) = lower(?) or lower(u.email) = lower(?))
                 order by case when lower(u.username) = lower(?) then 0 else 1 end
                 fetch first 1 row with ties
                """, (rs, row) -> new Account(rs.getLong("id"), rs.getObject("tenant_id", UUID.class)),
                allowAdditionalCompanies, INITIAL_TENANT, companyCode, identifier, identifier, identifier);
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    public void create(UUID sessionId, Account account, Instant expiresAt) {
        jdbc.update("delete from company_session where expires_at <= current_timestamp");
        int inserted = jdbc.update("""
                insert into company_session (id, tenant_id, user_id, expires_at)
                select ?, c.id, ?, ? from company c where c.id = ? and c.status = 'ACTIVE' for share
                """, sessionId, account.userId(), OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC), account.tenantId());
        if (inserted == 0) throw new ApiException(HttpStatus.UNAUTHORIZED, "Company is unavailable");
    }

    public boolean isActive(JwtService.CompanyToken token) {
        if (!allowAdditionalCompanies && !INITIAL_TENANT.equals(token.tenantId())) return false;
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists (
                    select 1 from company_session s join company c on c.id = s.tenant_id
                     where s.id = ? and s.tenant_id = ? and s.user_id = ?
                       and s.expires_at > current_timestamp and c.status = 'ACTIVE'
                )
                """, Boolean.class, token.sessionId(), token.tenantId(), token.userId()));
    }

    public void revoke(AuthUser user) {
        jdbc.update("delete from company_session where id = ? and tenant_id = ? and user_id = ?",
                user.sessionId(), user.tenantId(), user.id());
    }
}
