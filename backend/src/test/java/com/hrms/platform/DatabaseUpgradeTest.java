package com.hrms.platform;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "HRMS_TEST_DATABASE_URL",
        matches = "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/hrms_test")
class DatabaseUpgradeTest {
    @Test
    void existingSingleCompanyRecordsSurviveUpgrade() throws Exception {
        String url = System.getenv("HRMS_TEST_DATABASE_URL");
        String user = System.getenv().getOrDefault("HRMS_TEST_DATABASE_USERNAME", "hrms_test");
        String password = System.getenv().getOrDefault("HRMS_TEST_DATABASE_PASSWORD", "");
        String schema = "legacy_" + UUID.randomUUID().toString().replace("-", "");
        Flyway.configure().dataSource(url, user, password).schemas(schema).target("2").load().migrate();

        try (var connection = DriverManager.getConnection(url, user, password); var sql = connection.createStatement()) {
            connection.setSchema(schema);
            sql.executeUpdate("insert into department (name) values ('Historical department')");
            sql.executeUpdate("insert into role (name) values ('Historical reviewer')");
            sql.executeUpdate("insert into role_permission (role_id, permission) values (1, 'core:read')");
            sql.executeUpdate("""
                    insert into employee (employee_code, first_name, date_of_joining, status, department_id)
                    values ('LEGACY-1', 'Historical employee', date '2024-01-01', 'ACTIVE', 1)
                    """);
            sql.executeUpdate("""
                    insert into attendance (employee_id, attendance_date, status, working_hours)
                    values (1, date '2024-01-02', 'PRESENT', 8.00)
                    """);
            sql.executeUpdate("""
                    insert into payroll_run (period_year, period_month, status, slip_count, total_net)
                    values (2024, 1, 'SUBMITTED', 1, 1234.56)
                    """);
            sql.executeUpdate("""
                    insert into salary_slip (run_id, employee_id, period_year, period_month, status, net_pay)
                    values (1, 1, 2024, 1, 'SUBMITTED', 1234.56)
                    """);
        }

        Flyway upgraded = Flyway.configure().dataSource(url, user, password).schemas(schema).load();
        assertEquals(5, upgraded.migrate().migrationsExecuted);
        upgraded.validate();
        assertEquals(0, upgraded.migrate().migrationsExecuted);

        try (var connection = DriverManager.getConnection(url, user, password); var sql = connection.createStatement()) {
            connection.setSchema(schema);
            try (var rows = sql.executeQuery("""
                    select c.code, e.id, e.employee_code, d.name, a.working_hours, s.net_pay, p.status
                    from employee e join company c on c.id = e.tenant_id
                    join department d on (d.tenant_id, d.id) = (e.tenant_id, e.department_id)
                    join attendance a on (a.tenant_id, a.employee_id) = (e.tenant_id, e.id)
                    join salary_slip s on (s.tenant_id, s.employee_id) = (e.tenant_id, e.id)
                    join payroll_run p on (p.tenant_id, p.id) = (s.tenant_id, s.run_id)
                    """)) {
                assertTrue(rows.next());
                assertEquals("initial", rows.getString("code"));
                assertEquals(1, rows.getLong("id"));
                assertEquals("LEGACY-1", rows.getString("employee_code"));
                assertEquals("Historical department", rows.getString("name"));
                assertEquals(new BigDecimal("8.00"), rows.getBigDecimal("working_hours"));
                assertEquals(new BigDecimal("1234.56"), rows.getBigDecimal("net_pay"));
                assertEquals("SUBMITTED", rows.getString("status"));
                assertTrue(!rows.next());
            }
            try (var rows = sql.executeQuery("""
                    select permission from role_permission rp
                    join role r on (r.tenant_id, r.id) = (rp.tenant_id, rp.role_id)
                    """)) {
                assertTrue(rows.next());
                assertEquals("core:read", rows.getString("permission"));
            }
            sql.executeUpdate("""
                    insert into department (tenant_id, name)
                    select id, 'Other department' from company where code = 'initial'
                    """);
            sql.executeUpdate("insert into company (code, name) values ('another', 'Another company')");
            sql.executeUpdate("""
                    insert into department (tenant_id, name)
                    select id, 'Historical department' from company where code = 'another'
                    """);
            var failure = assertThrows(SQLException.class,
                    () -> sql.executeUpdate("update employee set department_id = 3 where id = 1"));
            assertEquals("23503", failure.getSQLState());
        }
    }
}
