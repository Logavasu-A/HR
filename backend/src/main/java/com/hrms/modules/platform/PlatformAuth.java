package com.hrms.modules.platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

import javax.crypto.SecretKey;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "app.platform.enabled", havingValue = "true")
public class PlatformAuth implements ApplicationRunner {
    private static final String ISSUER = "hrms-platform";
    private static final String AUDIENCE = "hrms-platform-api";
    private static final String INITIAL_USERNAME = "logavasu";

    public record Owner(UUID id, String username, UUID sessionId, Instant authenticatedAt, PlatformRole role) {}
    private record Account(UUID id, String username, String hash, boolean active, OffsetDateTime lockedUntil) {}

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final SecretKey key;
    private final String bootstrapFile;
    private final boolean rbac;

    public PlatformAuth(JdbcTemplate jdbc, PasswordEncoder passwords,
                        @Value("${app.platform.jwt-secret}") String secret,
                        @Value("${app.jwt.secret}") String companySecret,
                        @Value("${app.platform.bootstrap-password-file:}") String bootstrapFile,
                        @Value("${app.platform.rbac.enabled:false}") boolean rbac) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.bootstrapFile = bootstrapFile;
        this.rbac = rbac;
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Platform JWT key must be valid Base64");
        }
        if (decoded.length < 32 || MessageDigest.isEqual(decoded, companySecret.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalStateException("Platform JWT key requires at least 32 bytes and must differ from the company key");
        }
        this.key = Keys.hmacShaKeyFor(decoded);
    }

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        if (bootstrapFile.isBlank() || Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from platform_users where username = ?)", Boolean.class, INITIAL_USERNAME))) {
            return;
        }
        String password;
        try {
            password = Files.readString(Path.of(bootstrapFile)).replaceFirst("\\r?\\n\\z", "");
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the platform bootstrap password file");
        }
        if (password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72
                || password.isBlank() || password.indexOf('\n') >= 0 || password.indexOf('\r') >= 0) {
            throw new IllegalStateException("Platform bootstrap password requires a single line of 12 or more characters and at most 72 UTF-8 bytes");
        }
        jdbc.update("insert into platform_users (username, password_hash) values (?, ?) on conflict (username) do nothing",
                INITIAL_USERNAME, passwords.encode(password));
    }

    @Transactional
    public String login(String username, String password) {
        if (password == null || password.getBytes(StandardCharsets.UTF_8).length > 72) return null;
        if (username == null || username.isBlank()) return null;
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        Account account = jdbc.query("""
                select id, username, password_hash, active, locked_until
                from platform_users where username = ? for update
                """, (rs, row) -> new Account(rs.getObject("id", UUID.class), rs.getString("username"),
                rs.getString("password_hash"), rs.getBoolean("active"),
                rs.getObject("locked_until", OffsetDateTime.class)), normalized)
                .stream().findFirst().orElse(null);
        Instant now = Instant.now();
        if (account == null || !account.active() ||
                (account.lockedUntil() != null && account.lockedUntil().toInstant().isAfter(now))) return null;
        if (!passwords.matches(password, account.hash())) {
            failedPassword(account.id());
            return null;
        }
        if (rbac && !Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from hrms_rls.platform_role where user_id = ?)", Boolean.class, account.id()))) return null;
        jdbc.update("update platform_users set failures = 0, locked_until = null where id = ?", account.id());
        jdbc.update("delete from platform_sessions where expires_at <= current_timestamp");
        UUID id = UUID.randomUUID();
        Instant expires = now.plusSeconds(1200);
        jdbc.update("insert into platform_sessions (id, user_id, expires_at) values (?, ?, ?)",
                id, account.id(), OffsetDateTime.ofInstant(expires, ZoneOffset.UTC));
        String action = !rbac || Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists (select 1 from hrms_rls.platform_role where user_id = ? and role = 'GLOBAL_SUPER_ADMIN')
                """, Boolean.class, account.id())) ? "OWNER_LOGIN" : "PLATFORM_LOGIN";
        jdbc.update("insert into platform_audit_event (actor, action) values (?, ?)", account.username(), action);
        return Jwts.builder().issuer(ISSUER).audience().add(AUDIENCE).and()
                .subject(account.id().toString()).id(id.toString())
                .issuedAt(Date.from(now)).expiration(Date.from(expires)).signWith(key).compact();
    }

    public Owner authenticate(String token) {
        try {
            var claims = Jwts.parser().verifyWith(key).requireIssuer(ISSUER).requireAudience(AUDIENCE)
                    .build().parseSignedClaims(token).getPayload();
            if (claims.getSubject() == null || claims.getId() == null
                    || claims.getIssuedAt() == null || claims.getExpiration() == null) return null;
            UUID userId = UUID.fromString(claims.getSubject());
            UUID sessionId = UUID.fromString(claims.getId());
            String role = rbac ? "r.role" : "'GLOBAL_SUPER_ADMIN'";
            String join = rbac ? " join hrms_rls.platform_role r on r.user_id = u.id " : " ";
            return jdbc.query("select u.username, " + role + " from platform_sessions s join platform_users u on s.user_id = u.id"
                    + join + """
                    where s.id = ? and u.id = ? and u.active and s.expires_at > current_timestamp
                    """, (rs, row) -> new Owner(userId, rs.getString(1), sessionId, claims.getIssuedAt().toInstant(),
                    PlatformRole.valueOf(rs.getString(2))),
                    sessionId, userId).stream().findFirst().orElse(null);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    boolean rbacEnabled() {
        return rbac;
    }

    @Transactional
    public boolean confirmGlobalFinance(Owner owner, String password, UUID requestId) {
        var account = jdbc.query("""
                select id,username,password_hash,active,locked_until from platform_users where id=? for update
                """, (rs, row) -> new Account(rs.getObject("id", UUID.class), rs.getString("username"),
                rs.getString("password_hash"), rs.getBoolean("active"),
                rs.getObject("locked_until", OffsetDateTime.class)), owner.id()).stream().findFirst().orElse(null);
        boolean available = account != null && account.active()
                && (account.lockedUntil() == null || !account.lockedUntil().toInstant().isAfter(Instant.now()));
        boolean verified = available && password != null && password.getBytes(StandardCharsets.UTF_8).length <= 72
                && passwords.matches(password, account.hash());
        if (verified) {
            jdbc.update("update platform_users set failures=0,locked_until=null where id=?", owner.id());
            verified = jdbc.update("""
                    update platform_sessions set finance_verified_at=clock_timestamp()
                    where id=? and user_id=? and expires_at>clock_timestamp()
                    """, owner.sessionId(), owner.id()) == 1;
        } else if (available) {
            failedPassword(owner.id());
        }
        jdbc.update("""
                insert into platform_audit_event(actor,actor_id,action,request_id,request_path,request_method,http_status)
                values (?,?,?,?,'/api/platform/control-tower/step-up','POST',?)
                """, owner.username(), owner.id(), verified ? "FINANCE_STEP_UP" : "FINANCE_STEP_UP_FAILED",
                requestId, verified ? 200 : 401);
        return verified;
    }

    private void failedPassword(UUID userId) {
        jdbc.update("""
                update platform_users
                set failures = case when failures >= 4 then 0 else failures + 1 end,
                    locked_until = case when failures >= 4 then current_timestamp + interval '5 minutes' end
                where id = ?
                """, userId);
    }

    public void logout(Owner owner) {
        jdbc.update("delete from platform_sessions where id = ? and user_id = ?", owner.sessionId(), owner.id());
    }
}
