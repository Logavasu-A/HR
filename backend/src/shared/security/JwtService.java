package shared.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expiryHours;
    private final CompanySessions sessions;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiry-hours}") long expiryHours, CompanySessions sessions) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET is missing or shorter than 32 characters. Set it in backend/application-local.properties");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiryHours = expiryHours;
        this.sessions = sessions;
    }

    public record CompanyToken(long userId, UUID tenantId, UUID sessionId) {}

    public String create(CompanySessions.Account account) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(expiryHours, ChronoUnit.HOURS);
        UUID sessionId = UUID.randomUUID();
        String jwt = Jwts.builder()
                .issuer("hrms-company")
                .audience().add("hrms-company-api").and()
                .subject(Long.toString(account.userId()))
                .id(sessionId.toString())
                .claim("tenant_id", account.tenantId().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        sessions.create(sessionId, account, expiresAt);
        return jwt;
    }

    public CompanyToken parseCompanyToken(String token) {
        try {
            var claims = Jwts.parser().verifyWith(key)
                    .requireIssuer("hrms-company").requireAudience("hrms-company-api")
                    .build().parseSignedClaims(token).getPayload();
            if (claims.getId() == null || claims.get("tenant_id") == null || claims.getExpiration() == null) return null;
            return new CompanyToken(Long.parseLong(claims.getSubject()),
                    UUID.fromString(claims.get("tenant_id", String.class)), UUID.fromString(claims.getId()));
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
