-- Run using the restricted runtime account after seeding the local isolation fixtures.
\set ON_ERROR_STOP on
BEGIN;
DO $access$
DECLARE
    alpha uuid;
    bravo uuid;
    bravo_department bigint;
BEGIN
    IF current_database() <> 'hrms_isolation_20261119' OR current_user <> 'hrms_isolation_app'
       OR EXISTS (SELECT FROM pg_roles WHERE rolname = current_user AND
                  (rolsuper OR rolbypassrls OR rolcreatedb OR rolcreaterole OR rolreplication))
       OR pg_has_role(current_user, 'hrms_isolation_owner', 'MEMBER')
       OR has_table_privilege(current_user, 'public.flyway_schema_history', 'SELECT')
       OR has_table_privilege(current_user, 'public.audit_log', 'UPDATE, DELETE, TRUNCATE')
       OR has_table_privilege(current_user, 'public.platform_audit_event', 'UPDATE, DELETE, TRUNCATE') THEN
        RAISE EXCEPTION 'Unexpected runtime permissions';
    END IF;
    RAISE NOTICE 'Runtime is separate from the database and schema owner';

    BEGIN
        CREATE TABLE public.isolation_ddl_probe (id integer);
        RAISE EXCEPTION 'Runtime unexpectedly had DDL access';
    EXCEPTION WHEN insufficient_privilege THEN
        RAISE NOTICE 'Runtime DDL denied as expected';
    END;

    BEGIN
        UPDATE public.audit_log SET status = status WHERE false;
        RAISE EXCEPTION 'Runtime unexpectedly had audit modification access';
    EXCEPTION WHEN insufficient_privilege THEN
        RAISE NOTICE 'Runtime audit update denied as expected';
    END;

    IF to_regnamespace('hrms_rls') IS NOT NULL THEN
        IF EXISTS (SELECT FROM public.employee) OR EXISTS (SELECT FROM public.department) THEN
            RAISE EXCEPTION 'An unbound runtime connection exposed tenant data';
        END IF;
        RAISE NOTICE 'Unbound runtime sees no tenant rows; run TenantRowSecurityTest for signed FK probes';
        RETURN;
    END IF;

    SELECT id INTO STRICT alpha FROM public.company WHERE code = 'isolation-alpha';
    SELECT id INTO STRICT bravo FROM public.company WHERE code = 'isolation-bravo';
    SELECT id INTO STRICT bravo_department FROM public.department WHERE tenant_id = bravo;
    BEGIN
        UPDATE public.employee SET department_id = bravo_department
        WHERE tenant_id = alpha AND employee_code = 'VERIFY-ALPHA';
        RAISE EXCEPTION 'PostgreSQL accepted an employee relationship across companies';
    EXCEPTION WHEN foreign_key_violation THEN
        RAISE NOTICE 'Cross-company employee relationship rejected by the composite foreign key';
    END;
END;
$access$;
ROLLBACK;
