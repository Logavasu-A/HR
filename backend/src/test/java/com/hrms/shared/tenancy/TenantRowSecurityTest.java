package com.hrms.shared.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "HRMS_RLS_TEST_DB",
        matches = "hrms_((isolation|rls_rehearsal)_20261119|(access|sensitive)_rehearsal_20261121)")
class TenantRowSecurityTest {
    private static final String RUNTIME = "hrms_isolation_app";
    private static final String DATABASE = System.getenv("HRMS_RLS_TEST_DB");

    @Test
    void restrictedRoleWithoutContextCannotAccessTenantRowsOrForgeContext() throws Exception {
        try (var connection = connect()) {
            assertThat(value(connection, "select current_user")).isEqualTo(RUNTIME);
            assertThat(value(connection, "select current_database()")).isEqualTo(DATABASE);
            assertThat(value(connection, """
                    select not (rolsuper or rolbypassrls) and not pg_has_role(current_user, 'hrms_isolation_owner', 'MEMBER')
                       and not pg_has_role(current_user, 'hrms_isolation_auth_lookup', 'MEMBER')
                    from pg_roles where rolname = current_user
                    """)).isEqualTo(Boolean.TRUE);
            var tables = coveredTables(connection);
            assertUnbound(connection, tables);
            assertThat(update(connection, "update public.employee set first_name = first_name")).isZero();
            assertThat(update(connection, "delete from public.employee where employee_code like 'VERIFY-%'")).isZero();

            var alpha = company(connection, "alpha");
            value(connection, "select pg_current_xact_id()");
            setClaim(connection, alpha.toString());
            assertUnbound(connection, tables);
            setClaim(connection, alpha + ":" + "0".repeat(64));
            assertUnbound(connection, tables);

            rejected(connection, "42501", () -> value(connection, "select 1 from hrms_rls.signing_key"));
            rejected(connection, "42501", () -> update(connection, "set local role hrms_isolation_owner"));
            rejected(connection, "42501", () -> {
                update(connection, "set local row_security = off");
                value(connection, "select count(*) from public.employee");
            });
            connection.rollback();
        }
    }

    @Test
    void realRuntimeConnectionIsolatesBothCompaniesAndCannotReplayAcrossTransactions() throws Exception {
        try (var connection = connect()) {
            var tables = coveredTables(connection);
            var alpha = company(connection, "alpha");
            var bravo = company(connection, "bravo");
            long backend = ((Number) value(connection, "select pg_backend_pid()")).longValue();

            String firstClaim = bind(connection, alpha);
            assertScoped(connection, tables, alpha, bravo, "ALPHA");
            long alphaDepartment = department(connection, alpha);
            connection.commit();
            assertUnbound(connection, tables);
            value(connection, "select pg_current_xact_id()");
            setClaim(connection, firstClaim);
            assertUnbound(connection, tables);
            connection.rollback();

            String secondClaim = bind(connection, bravo);
            assertScoped(connection, tables, bravo, alpha, "BRAVO");
            long bravoDepartment = department(connection, bravo);
            rejected(connection, "23503", () -> update(connection,
                    "update public.employee set department_id = ? where employee_code = 'VERIFY-BRAVO'", alphaDepartment));
            connection.rollback();
            assertUnbound(connection, tables);
            value(connection, "select pg_current_xact_id()");
            setClaim(connection, secondClaim);
            assertUnbound(connection, tables);
            connection.rollback();

            bind(connection, alpha);
            rejected(connection, "23503", () -> update(connection,
                    "update public.employee set department_id = ? where employee_code = 'VERIFY-ALPHA'", bravoDepartment));
            assertThat(update(connection, "update public.employee set first_name = first_name where employee_code = 'VERIFY-ALPHA'"))
                    .isEqualTo(1);
            connection.rollback();
            assertThat(((Number) value(connection, "select pg_backend_pid()")).longValue()).isEqualTo(backend);
            assertUnbound(connection, tables);
            connection.rollback();
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "HRMS_TEST_RECORD_SCOPES", matches = "true")
    void actorBoundPoliciesLimitDepartmentDirectReportsAndOwnRows() throws Exception {
        try (var connection = connect()) {
            var tenant = company(connection, "access");
            long departmentManager = actor(connection, "dept");
            long reportingManager = actor(connection, "report");
            long employee = actor(connection, "employee");

            bind(connection, tenant, departmentManager);
            assertCodes(connection, "VERIFY-ACCESS-DEPT,VERIFY-ACCESS-DIRECT,VERIFY-ACCESS-PEER");
            long peerLeave = ((Number) value(connection,
                    "select id from leave_application where reason = 'VERIFY-ACCESS-PEER'")).longValue();
            assertThat(update(connection, "update employee set first_name = first_name where employee_code = 'VERIFY-ACCESS-REPORT'")).isZero();
            rejected(connection, "42501", () -> update(connection, """
                    update employee set department_id = (select min(id) from department where name = 'Operations')
                    where employee_code = 'VERIFY-ACCESS-PEER'
                    """));
            connection.rollback();

            String reportClaim = bind(connection, tenant, reportingManager);
            assertCodes(connection, "VERIFY-ACCESS-DIRECT,VERIFY-ACCESS-REPORT");
            assertThat(value(connection, "select count(distinct employee_id) from attendance")).isEqualTo(2L);
            long managerId = ((Number) value(connection,
                    "select id from employee where employee_code = 'VERIFY-ACCESS-REPORT'")).longValue();
            long directReportId = ((Number) value(connection,
                    "select id from employee where employee_code = 'VERIFY-ACCESS-DIRECT'")).longValue();
            assertThat(value(connection, """
                    select count(*) from leave_application
                    where tenant_id is distinct from ? or employee_id is null or employee_id not in (?, ?)
                    """, tenant, managerId, directReportId)).isEqualTo(0L);
            assertThat(value(connection, "select count(*) from leave_application where id = ?", peerLeave))
                    .isEqualTo(0L);
            assertThat(value(connection, """
                    select string_agg(status, ',') from leave_application
                    where employee_id = ?
                    """, managerId)).isEqualTo("PENDING");
            assertThat(value(connection, """
                    select string_agg(status, ',') from leave_application
                    where employee_id = ? and reason = 'VERIFY-ACCESS-DIRECT'
                    """, directReportId)).isIn("PENDING", "APPROVED");
            assertThat(value(connection, """
                    select count(*) from leave_application
                    where employee_id = ? and reason is distinct from 'VERIFY-ACCESS-DIRECT'
                        and status is distinct from 'CANCELLED'
                    """, directReportId)).isEqualTo(0L);
            assertThat(value(connection, "select count(*) from payroll_run")).isEqualTo(0L);
            assertThat(value(connection, "select count(*) from employee where tenant_id <> ?", tenant)).isEqualTo(0L);
            setClaim(connection, reportClaim.replace(":" + reportingManager + ":", ":" + departmentManager + ":"));
            assertUnbound(connection, coveredTables(connection));
            connection.rollback();

            bind(connection, tenant, employee);
            assertCodes(connection, "VERIFY-ACCESS-DIRECT");
            assertThat(value(connection, "select count(*) from salary_slip")).isEqualTo(1L);
            connection.rollback();
            bind(connection, tenant, actor(connection, "unlinked"));
            assertThat(value(connection, "select count(*) from employee")).isEqualTo(0L);
        }
    }

    private static long actor(Connection connection, String username) throws SQLException {
        return ((Number) value(connection, "select user_id from hrms_rls.lookup_account(true, 'isolation-access', ?)",
                "isolation.access." + username)).longValue();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "HRMS_TEST_FINANCE", matches = "true")
    void financialAuditAndReviewTablesEnforceForcedRlsAndAppendOnlyAudit() throws Exception {
        try (var connection = connect()) {
            var alpha = company(connection, "alpha");
            var bravo = company(connection, "bravo");
            var review = UUID.randomUUID();
            bind(connection, alpha);
            assertThat(update(connection, """
                    insert into hrms_rls.finance_approval(id,tenant_id,maker,method,path,fingerprint,reason)
                    values (?,?,'company:0','POST','/api/payroll/runs',?,'REGULAR_PROCESSING')
                    """, review, alpha, "0".repeat(64))).isOne();
            rejected(connection, "42501", () -> update(connection, """
                    insert into hrms_rls.finance_approval(tenant_id,maker,method,path,fingerprint,reason)
                    values (?,'company:0','POST','/api/payroll/runs',?,'REGULAR_PROCESSING')
                    """, bravo, "0".repeat(64)));
            rejected(connection, "42501", () -> update(connection, "delete from hrms_rls.finance_event"));
            rejected(connection, "42501", () -> update(connection, "update hrms_rls.finance_event set status=200"));
            assertThat(value(connection, "select count(*) from hrms_rls.finance_approval where tenant_id=?", bravo)).isEqualTo(0L);
            assertThat(value(connection, "select count(*) from hrms_rls.finance_event where tenant_id=?", bravo)).isEqualTo(0L);
            connection.rollback();
            bind(connection, bravo);
            assertThat(value(connection, "select count(*) from hrms_rls.finance_approval where tenant_id=?", alpha)).isEqualTo(0L);
            assertThat(value(connection, "select count(*) from hrms_rls.finance_event where tenant_id=?", alpha)).isEqualTo(0L);
            connection.rollback();
            assertThat(value(connection, "select count(*) from hrms_rls.finance_approval")).isEqualTo(0L);
            assertThat(value(connection, "select count(*) from hrms_rls.finance_event")).isEqualTo(0L);
        }
    }

    private static void assertCodes(Connection connection, String codes) throws SQLException {
        assertThat(value(connection, "select string_agg(employee_code, ',' order by employee_code) from employee"))
                .isEqualTo(codes);
    }

    private static Connection connect() throws SQLException {
        var connection = DriverManager.getConnection("jdbc:postgresql://127.0.0.1:5432/" + DATABASE,
                RUNTIME, System.getenv("HRMS_ISOLATION_RUNTIME_PASSWORD"));
        connection.setAutoCommit(false);
        return connection;
    }

    private static List<String> coveredTables(Connection connection) throws SQLException {
        var tables = new ArrayList<String>();
        try (var statement = connection.prepareStatement("""
                select c.relname, c.relrowsecurity, c.relforcerowsecurity
                from pg_class c join pg_namespace n on n.oid = c.relnamespace
                join pg_attribute a on a.attrelid = c.oid and a.attname = 'tenant_id'
                where n.nspname = 'public' and c.relkind = 'r' and a.attnotnull and not a.attisdropped
                order by c.relname
                """); var rows = statement.executeQuery()) {
            while (rows.next()) {
                String table = rows.getString(1);
                assertThat(table).matches("[a-z_]+");
                assertThat(rows.getBoolean(2)).as("%s has RLS enabled", table).isTrue();
                assertThat(rows.getBoolean(3)).as("%s has RLS forced", table).isTrue();
                tables.add(table);
            }
        }
        assertThat(tables).hasSize(35);
        return tables;
    }

    private static void assertUnbound(Connection connection, List<String> tables) throws SQLException {
        assertThat(value(connection, "select hrms_rls.current_tenant()")).isNull();
        for (String table : tables) {
            assertThat(value(connection, "select count(*) from public." + table)).as("unbound %s rows", table).isEqualTo(0L);
        }
    }

    private static void assertScoped(Connection connection, List<String> tables, UUID tenant,
                                     UUID other, String suffix) throws SQLException {
        assertThat(value(connection, "select hrms_rls.current_tenant()")).isEqualTo(tenant);
        for (String table : tables) {
            assertThat(value(connection, "select count(*) from public." + table + " where tenant_id <> ?", tenant))
                    .as("foreign rows visible in %s", table).isEqualTo(0L);
        }
        assertThat(value(connection, "select count(*) from public.employee where employee_code = ?", "VERIFY-" + suffix))
                .isEqualTo(1L);
        assertThat(value(connection, "select count(*) from public.salary_slip")).isEqualTo(2L);
        assertThat(update(connection, "update public.employee set first_name = first_name where tenant_id = ?", other)).isZero();
        assertThat(update(connection, "delete from public.employee where tenant_id = ?", other)).isZero();
        rejected(connection, "42501", () -> update(connection, """
                insert into public.department (tenant_id, name) values (?, 'Rejected RLS probe')
                """, other));
        rejected(connection, "42501", () -> update(connection, """
                update public.employee set tenant_id = ? where employee_code = ?
                """, other, "VERIFY-" + suffix));
    }

    private static UUID company(Connection connection, String suffix) throws SQLException {
        return (UUID) value(connection, "select id from public.company where code = ?", "isolation-" + suffix);
    }

    private static long department(Connection connection, UUID company) throws SQLException {
        return ((Number) value(connection, "select id from public.department where tenant_id = ?", company)).longValue();
    }

    private static String bind(Connection connection, UUID tenant) throws Exception {
        return bind(connection, tenant, null);
    }

    private static String bind(Connection connection, UUID tenant, Long actor) throws Exception {
        String transaction = (String) value(connection, "select pg_backend_pid()::text || ':' || pg_current_xact_id()::text");
        var signer = Mac.getInstance("HmacSHA256");
        signer.init(new SecretKeySpec(HexFormat.of().parseHex(System.getenv("HRMS_ISOLATION_RLS_KEY")), "HmacSHA256"));
        String binding = tenant + ":" + transaction;
        if (actor != null) binding = "company-user:" + binding + ":" + actor;
        String claim = tenant + ":" + (actor == null ? "" : actor + ":")
                + HexFormat.of().formatHex(signer.doFinal(binding.getBytes(StandardCharsets.UTF_8)));
        setClaim(connection, claim);
        return claim;
    }

    private static void setClaim(Connection connection, String claim) throws SQLException {
        value(connection, "select set_config('hrms.tenant_binding', ?, true)", claim);
    }

    private static Object value(Connection connection, String sql, Object... parameters) throws SQLException {
        try (var query = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) query.setObject(i + 1, parameters[i]);
            try (var rows = query.executeQuery()) {
                assertThat(rows.next()).as("expected one result row").isTrue();
                return rows.getObject(1);
            }
        }
    }

    private static int update(Connection connection, String sql, Object... parameters) throws SQLException {
        try (var command = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) command.setObject(i + 1, parameters[i]);
            return command.executeUpdate();
        }
    }

    private static void rejected(Connection connection, String state, SqlAction action) throws SQLException {
        var savepoint = connection.setSavepoint();
        try {
            assertThat(assertThrows(SQLException.class, action::run).getSQLState()).isEqualTo(state);
        } finally {
            connection.rollback(savepoint);
            connection.releaseSavepoint(savepoint);
        }
    }

    @FunctionalInterface
    private interface SqlAction {
        void run() throws SQLException;
    }
}
