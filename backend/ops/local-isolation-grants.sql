-- Run as the local database administrator against a verified disposable restore.
-- Supply HRMS_ISOLATION_RUNTIME_PASSWORD in the process environment.
\set ON_ERROR_STOP on
\getenv runtime_password HRMS_ISOLATION_RUNTIME_PASSWORD
SELECT current_database() = 'hrms_isolation_20261119' AS approved_restore
     , NOT EXISTS (
         SELECT FROM pg_roles WHERE rolname IN ('hrms_isolation_owner', 'hrms_isolation_app')
       ) AS roles_available
\gset
\if :approved_restore
\else
    \echo This script requires the verified local isolation restore.
    \quit 3
\endif
\if :roles_available
\else
    \echo The isolation roles already exist. Inspect them before changing grants.
    \quit 3
\endif

BEGIN;
CREATE ROLE hrms_isolation_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
CREATE ROLE hrms_isolation_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS
    PASSWORD :'runtime_password';
ALTER DATABASE hrms_isolation_20261119 OWNER TO hrms_isolation_owner;
REVOKE ALL ON DATABASE hrms_isolation_20261119 FROM PUBLIC;
GRANT CONNECT ON DATABASE hrms_isolation_20261119 TO hrms_isolation_app;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO hrms_isolation_app;

SELECT format('ALTER TABLE %I.%I OWNER TO hrms_isolation_owner;', schemaname, tablename)
FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename
\gexec
SELECT format('ALTER SEQUENCE %I.%I OWNER TO hrms_isolation_owner;', schemaname, sequencename)
FROM pg_sequences WHERE schemaname = 'public' AND sequenceowner <> 'hrms_isolation_owner'
\gexec
ALTER FUNCTION public.assign_role_permission_company() OWNER TO hrms_isolation_owner;
ALTER FUNCTION public.revoke_company_sessions() OWNER TO hrms_isolation_owner;

GRANT SELECT, INSERT, UPDATE, DELETE ON
    public.app_user, public.appraisal, public.appraisal_cycle, public.attendance, public.branch,
    public.department, public.designation, public.employee, public.employee_advance,
    public.expense_claim, public.expense_claim_line, public.expense_type, public.goal,
    public.holiday, public.holiday_list, public.interview, public.job_applicant, public.job_opening,
    public.leave_allocation, public.leave_application, public.leave_type, public.notification,
    public.payroll_run, public.role, public.role_permission, public.salary_assignment,
    public.salary_component, public.salary_slip, public.salary_slip_line, public.salary_structure,
    public.salary_structure_line, public.shift_type
TO hrms_isolation_app;
GRANT SELECT, INSERT, UPDATE ON
    public.company, public.company_admin_invitation, public.platform_users
TO hrms_isolation_app;
GRANT SELECT, INSERT, DELETE ON public.company_session, public.platform_sessions TO hrms_isolation_app;
GRANT SELECT, INSERT ON public.audit_log, public.platform_audit_event TO hrms_isolation_app;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO hrms_isolation_app;
COMMIT;
