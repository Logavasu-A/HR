-- Synthetic role and employee relationships for local verification only.
\set ON_ERROR_STOP on
SELECT current_database() IN ('hrms_isolation_20261119','hrms_access_rehearsal_20261121') AS approved
\gset
\if :approved
\else
\quit 3
\endif
BEGIN;
DO $fixtures$
DECLARE
    company_key uuid;
    people bigint;
    operations bigint;
    department_manager bigint;
    report_manager bigint;
    direct_report bigint;
    leave_key bigint;
    run_key bigint;
    role_key bigint;
    platform_key uuid;
    fixture record;
    today date := current_date;
    fixture_hash text;
BEGIN
    SELECT u.password_hash INTO STRICT fixture_hash FROM app_user u JOIN company c ON c.id=u.tenant_id
    WHERE c.code='isolation-alpha' AND u.username='isolation.alpha.hr';
    INSERT INTO company (code,name,status,timezone,currency)
    VALUES ('isolation-access','Access Verification','ACTIVE','UTC','USD') RETURNING id INTO company_key;
    INSERT INTO department (tenant_id,name) VALUES (company_key,'People') RETURNING id INTO people;
    INSERT INTO department (tenant_id,name) VALUES (company_key,'Operations') RETURNING id INTO operations;
    INSERT INTO employee (tenant_id,employee_code,first_name,department_id,status,date_of_joining)
    VALUES (company_key,'VERIFY-ACCESS-DEPT','Department Manager',people,'ACTIVE',today) RETURNING id INTO department_manager;
    INSERT INTO employee (tenant_id,employee_code,first_name,department_id,status,date_of_joining)
    VALUES (company_key,'VERIFY-ACCESS-REPORT','Reporting Manager',operations,'ACTIVE',today) RETURNING id INTO report_manager;
    INSERT INTO employee (tenant_id,employee_code,first_name,department_id,status,date_of_joining,reports_to_id)
    VALUES (company_key,'VERIFY-ACCESS-DIRECT','Direct Report',people,'ACTIVE',today,report_manager) RETURNING id INTO direct_report;
    INSERT INTO employee (tenant_id,employee_code,first_name,department_id,status,date_of_joining,reports_to_id)
    VALUES (company_key,'VERIFY-ACCESS-INDIRECT','Indirect Report',operations,'ACTIVE',today,direct_report),
           (company_key,'VERIFY-ACCESS-PEER','Department Peer',people,'ACTIVE',today,NULL);

    FOR fixture IN SELECT * FROM (VALUES
        ('dept','Department Manager',department_manager,ARRAY['scope:department','core:read','leave:read','leave:approve','attendance:read']),
        ('report','Reporting Manager',report_manager,ARRAY['scope:direct-reports','core:read','leave:read','leave:approve','attendance:read']),
        ('employee','Employee',direct_report,ARRAY['scope:self','core:read']),
        ('unlinked','Unlinked Manager',NULL,ARRAY['scope:department','core:read']),
        ('hr','HR Administrator',NULL,ARRAY['admin:read','admin:write','core:read','leave:read']),
        ('admin','Company Administrator',NULL,ARRAY['*'])
    ) AS v(suffix,name,employee,permissions)
    LOOP
        INSERT INTO role (tenant_id,name) VALUES (company_key,fixture.name) RETURNING id INTO role_key;
        INSERT INTO role_permission (role_id,permission) SELECT role_key,unnest(fixture.permissions);
        INSERT INTO app_user (tenant_id,username,password_hash,role_id,employee_id,active)
        VALUES (company_key,'isolation.access.'||fixture.suffix,fixture_hash,role_key,fixture.employee,true);
    END LOOP;

    INSERT INTO leave_type (tenant_id,name,allow_negative)
    VALUES (company_key,'Access Verification Leave',true) RETURNING id INTO leave_key;
    INSERT INTO leave_application (tenant_id,employee_id,leave_type_id,from_date,to_date,total_days,status,reason)
    SELECT company_key,e.id,leave_key,today+7,today+7,1,'PENDING',e.employee_code
    FROM employee e WHERE e.tenant_id=company_key;
    INSERT INTO attendance (tenant_id,employee_id,attendance_date,status)
    SELECT company_key,e.id,today,'PRESENT' FROM employee e WHERE e.tenant_id=company_key;
    INSERT INTO payroll_run (tenant_id,period_year,period_month,status,slip_count,total_net)
    VALUES (company_key,extract(year FROM today),extract(month FROM today),'SUBMITTED',2,9000)
    RETURNING id INTO run_key;
    INSERT INTO salary_slip (tenant_id,run_id,employee_id,period_year,period_month,status,gross_pay,total_deduction,net_pay)
    SELECT company_key,run_key,e.id,extract(year FROM today),extract(month FROM today),'SUBMITTED',5000,500,4500
    FROM employee e WHERE e.tenant_id=company_key AND e.id IN (department_manager,direct_report);

    FOR fixture IN SELECT * FROM (VALUES
        ('isolation-platform-admin','PLATFORM_ADMINISTRATOR'),
        ('isolation-security-admin','SECURITY_ADMINISTRATOR'),
        ('isolation-platform-candidate',NULL)
    ) AS v(username,role)
    LOOP
        INSERT INTO platform_users (username,password_hash) VALUES (fixture.username,fixture_hash) RETURNING id INTO platform_key;
        IF fixture.role IS NOT NULL THEN
            INSERT INTO hrms_rls.platform_role VALUES (platform_key,fixture.role);
        END IF;
    END LOOP;
END;
$fixtures$;
COMMIT;
