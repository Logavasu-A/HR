package com.hrms.shared.config;

import java.util.UUID;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import com.hrms.shared.tenancy.TenantDatabaseScope;

@Component
@Profile("local-isolation")
public class IsolationRuntimeGuard {
    private final JdbcTemplate jdbc;
    private final TenantDatabaseScope tenantScope;

    public IsolationRuntimeGuard(JdbcTemplate jdbc, TenantDatabaseScope tenantScope) {
        this.jdbc = jdbc;
        this.tenantScope = tenantScope;
    }

    @PostConstruct
    public void verify() {
        Boolean safe = jdbc.queryForObject("""
                select current_database() = 'hrms_isolation_20261119'
                    and current_user = 'hrms_isolation_app'
                    and not (r.rolsuper or r.rolbypassrls or r.rolcreaterole or r.rolcreatedb or r.rolreplication)
                    and not pg_has_role(r.oid, db.datdba, 'MEMBER')
                    and not has_schema_privilege(current_user, 'public', 'CREATE')
                    and not has_database_privilege(current_user, current_database(), 'CREATE')
                    and not exists (select 1 from pg_auth_members where member = r.oid)
                    and to_regclass('hrms_rls.platform_role') is not null
                    and to_regprocedure('hrms_rls.record_access()') is not null
                    and (
                        select count(*) from pg_policy p join pg_class c on c.oid = p.polrelid
                        join pg_namespace n on n.oid = c.relnamespace
                        where n.nspname = 'public' and p.polname = 'company_record_scope'
                          and not p.polpermissive and c.relrowsecurity and c.relforcerowsecurity
                    ) = 21
                    and not exists (
                        select 1 from pg_class c join pg_namespace n on n.oid = c.relnamespace
                        where n.nspname = 'public' and c.relowner = r.oid
                    )
                    and (
                        select count(*) from pg_class c join pg_namespace n on n.oid = c.relnamespace
                        join pg_attribute a on a.attrelid = c.oid
                        where n.nspname = 'public' and c.relkind = 'r' and a.attname = 'tenant_id'
                          and a.attnotnull and not a.attisdropped and c.relrowsecurity and c.relforcerowsecurity
                          and exists (select 1 from pg_policy p where p.polrelid = c.oid and p.polname = 'tenant_rows')
                    ) = 35
                from pg_roles r join pg_database db on db.datname = current_database()
                where r.rolname = current_user
                """, Boolean.class);
        if (!Boolean.TRUE.equals(safe)) {
            throw new IllegalStateException("The local isolation profile requires the restricted role on the verified restore.");
        }
        tenantScope.transaction(UUID.fromString("00000000-0000-0000-0000-000000000001"), status -> null);
    }
}
