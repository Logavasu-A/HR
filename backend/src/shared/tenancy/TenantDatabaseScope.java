package shared.tenancy;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class TenantDatabaseScope {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final SecretKeySpec key;
    private final boolean abac;

    public TenantDatabaseScope(JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
                               @Value("${app.tenancy.rls.enabled:false}") boolean enabled,
                               @Value("${app.tenancy.rls.key:}") String configuredKey,
                               @Value("${app.tenancy.abac.enabled:false}") boolean abac) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
        if (enabled && !configuredKey.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalStateException("Tenant RLS requires a 32-byte hexadecimal signing key.");
        }
        this.key = enabled ? new SecretKeySpec(HexFormat.of().parseHex(configuredKey), "HmacSHA256") : null;
        this.abac = abac;
        if (abac && !enabled) throw new IllegalStateException("Company record scope requires tenant RLS.");
    }

    public boolean enabled() {
        return key != null;
    }

    public boolean abacEnabled() {
        return abac;
    }

    public <T> T transaction(UUID tenant, TransactionCallback<T> work) {
        return transaction(tenant, null, work);
    }

    public <T> T transaction(UUID tenant, Long actor, TransactionCallback<T> work) {
        if (!enabled() || TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Tenant request scope requires a new RLS transaction.");
        }
        CompanyTenancy.attach(tenant);
        try {
            return transactions.execute(status -> {
                bind(tenant, abac ? actor : null);
                return work.doInTransaction(status);
            });
        } finally {
            CompanyTenancy.clear();
        }
    }

    /** Binds trusted company context for JDBC workflows inside their existing transaction. */
    public void bind(UUID tenant) {
        bind(tenant, null);
    }

    private void bind(UUID tenant, Long actor) {
        if (!enabled()) return;
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Tenant RLS context requires an active transaction.");
        }
        UUID bound = jdbc.queryForObject("select hrms_rls.current_tenant()", UUID.class);
        if (tenant.equals(bound)) return;
        if (bound != null) throw new IllegalStateException("A different tenant is already bound to this transaction.");

        String transaction = jdbc.queryForObject("""
                select pg_backend_pid()::text || ':' || pg_current_xact_id()::text
                """, String.class);
        String message = tenant + ":" + transaction;
        String claim = tenant.toString();
        if (actor != null) {
            message = "company-user:" + message + ":" + actor;
            claim += ":" + actor;
        }
        jdbc.queryForObject("select set_config('hrms.tenant_binding', ?, true)", String.class, claim + ":" + sign(message));
        if (!tenant.equals(jdbc.queryForObject("select hrms_rls.current_tenant()", UUID.class))) {
            throw new IllegalStateException("The database rejected the tenant transaction binding.");
        }
    }

    private String sign(String message) {
        try {
            Mac hmac = Mac.getInstance(key.getAlgorithm());
            hmac.init(key);
            return HexFormat.of().formatHex(hmac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not sign tenant database context.", e);
        }
    }
}
