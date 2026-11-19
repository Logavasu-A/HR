-- Synthetic records for the dedicated local test restore. Run as its database administrator.
\set ON_ERROR_STOP on
\getenv fixture_hash HRMS_ISOLATION_FIXTURE_HASH
SELECT current_database() = 'hrms_isolation_20261119' AS approved_restore
\gset
\if :approved_restore
\else
    \echo This script requires the verified local isolation restore.
    \quit 3
\endif

BEGIN;
SELECT set_config('hrms_test.fixture_hash', :'fixture_hash', true) IS NOT NULL AS fixture_hash_loaded;
DO $fixtures$
DECLARE
    suffix text;
    company_key uuid;
    role_key bigint;
    reader_role bigint;
    writer_role bigint;
    user_key bigint;
    employee_key bigint;
    colleague_key bigint;
    department_key bigint;
    leave_key bigint;
    run_key bigint;
    today date := current_date;
    password_hash text := current_setting('hrms_test.fixture_hash');
BEGIN
    IF EXISTS (SELECT 1 FROM company WHERE code IN ('isolation-alpha', 'isolation-bravo')) THEN
        RAISE EXCEPTION 'Isolation fixtures already exist';
    END IF;
    FOREACH suffix IN ARRAY ARRAY['alpha', 'bravo'] LOOP
        INSERT INTO company (code, name, status, timezone, currency)
        VALUES ('isolation-' || suffix, 'Isolation ' || initcap(suffix), 'ACTIVE', 'UTC', 'USD')
        RETURNING id INTO company_key;
        INSERT INTO department (tenant_id, name)
        VALUES (company_key, initcap(suffix) || ' Operations') RETURNING id INTO department_key;
        INSERT INTO employee (tenant_id, employee_code, first_name, last_name, department_id, status, date_of_joining, bank_name, bank_account_no)
        VALUES (company_key, 'VERIFY-' || upper(suffix), initcap(suffix), 'Tester', department_key, 'ACTIVE',
                today - 365, initcap(suffix) || ' Test Bank', 'FIXTURE-' || upper(suffix))
        RETURNING id INTO employee_key;
        INSERT INTO employee (tenant_id, employee_code, first_name, last_name, department_id, status, date_of_joining)
        VALUES (company_key, 'VERIFY-' || upper(suffix) || '-COLLEAGUE', initcap(suffix), 'Colleague', department_key, 'ACTIVE', today - 120)
        RETURNING id INTO colleague_key;
        INSERT INTO role (tenant_id, name) VALUES (company_key, 'Isolation HR') RETURNING id INTO role_key;
        INSERT INTO role_permission (role_id, permission) VALUES (role_key, '*');
        INSERT INTO app_user (tenant_id, username, password_hash, role_id, employee_id, active)
        VALUES (company_key, 'isolation.' || suffix || '.hr', password_hash, role_key, employee_key, true)
        RETURNING id INTO user_key;
        INSERT INTO notification (tenant_id, user_id, title, created_at)
        VALUES (company_key, user_key, initcap(suffix) || ' onboarding notification', clock_timestamp());
        INSERT INTO attendance (tenant_id, employee_id, attendance_date, status, in_time, out_time, working_hours)
        VALUES (company_key, employee_key, today, 'PRESENT', '09:00', '17:00', 8);
        INSERT INTO leave_type (tenant_id, name) VALUES (company_key, 'Annual Leave') RETURNING id INTO leave_key;
        INSERT INTO leave_application (tenant_id, employee_id, leave_type_id, from_date, to_date, total_days, status)
        VALUES (company_key, colleague_key, leave_key, today + 1, today + 1, 1, 'PENDING');
        INSERT INTO payroll_run (tenant_id, period_year, period_month, status, slip_count, total_net)
        VALUES (company_key, extract(year FROM today), extract(month FROM today), 'SUBMITTED', 2, 9000)
        RETURNING id INTO run_key;
        INSERT INTO salary_slip (tenant_id, run_id, employee_id, period_year, period_month, status, gross_pay, total_deduction, net_pay)
        VALUES (company_key, run_key, employee_key, extract(year FROM today), extract(month FROM today), 'SUBMITTED', 5500, 1000, 4500),
               (company_key, run_key, colleague_key, extract(year FROM today), extract(month FROM today), 'SUBMITTED', 5500, 1000, 4500);

        INSERT INTO role (tenant_id, name) VALUES (company_key, 'Isolation Bank Reader') RETURNING id INTO reader_role;
        INSERT INTO role_permission (role_id, permission) VALUES (reader_role, '*'), (reader_role, 'employee-bank:read');
        INSERT INTO role (tenant_id, name) VALUES (company_key, 'Isolation Bank Editor') RETURNING id INTO writer_role;
        INSERT INTO role_permission (role_id, permission)
        VALUES (writer_role, '*'), (writer_role, 'employee-bank:read'), (writer_role, 'employee-bank:write');
        INSERT INTO app_user (tenant_id, username, password_hash, role_id, active)
        VALUES (company_key, 'isolation.' || suffix || '.reader', password_hash, reader_role, true),
               (company_key, 'isolation.' || suffix || '.bank', password_hash, writer_role, true);
    END LOOP;
END;
$fixtures$;
COMMIT;
