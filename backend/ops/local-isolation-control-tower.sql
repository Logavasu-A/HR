-- Rehearse and apply only on the verified local restores.
\set ON_ERROR_STOP on
SELECT current_database() IN ('hrms_isolation_20261119', 'hrms_tower_rehearsal_20261121')
   AND (SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
        WHERE n.nspname='public' AND c.relkind='r' AND c.relrowsecurity AND c.relforcerowsecurity) = 35 AS approved
\gset
\if :approved
\else
    \echo Control Tower requires a verified local restore with forced tenant RLS.
    \quit 3
\endif

BEGIN;
SET LOCAL lock_timeout = '5s';
SELECT 'CREATE ROLE hrms_isolation_metrics NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS'
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname='hrms_isolation_metrics')
\gexec
DO $role$
BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname='hrms_isolation_metrics' AND
               (rolcanlogin OR rolsuper OR rolbypassrls OR rolcreatedb OR rolcreaterole OR rolreplication))
       OR EXISTS (SELECT FROM pg_auth_members m JOIN pg_roles r ON r.oid IN (m.roleid,m.member)
                  WHERE r.rolname='hrms_isolation_metrics') THEN
        RAISE EXCEPTION 'The aggregate role must be isolated and non-login';
    END IF;
END;
$role$;

GRANT USAGE ON SCHEMA public, hrms_rls TO hrms_isolation_metrics;
GRANT SELECT (id, name, code, status, currency, timezone) ON public.company TO hrms_isolation_metrics;
GRANT SELECT (tenant_id, status, department_id, date_of_joining, relieving_date) ON public.employee TO hrms_isolation_metrics;
GRANT SELECT (tenant_id, id, name) ON public.department TO hrms_isolation_metrics;
GRANT SELECT (tenant_id, attendance_date, status) ON public.attendance TO hrms_isolation_metrics;
GRANT SELECT (tenant_id, from_date, status, total_days) ON public.leave_application TO hrms_isolation_metrics;
GRANT SELECT (tenant_id, period_year, period_month, status, total_net) ON public.payroll_run TO hrms_isolation_metrics;
GRANT SELECT (tenant_id, status, posted_on, vacancies) ON public.job_opening TO hrms_isolation_metrics;
GRANT SELECT (tenant_id, applied_on) ON public.job_applicant TO hrms_isolation_metrics;
GRANT SELECT (tenant_id, is_read) ON public.notification TO hrms_isolation_metrics;
GRANT SELECT (tenant_id, created_at, id, delivery_status, accepted_at, revoked_at, expires_at)
    ON public.company_admin_invitation TO hrms_isolation_metrics;

DO $policies$
DECLARE relation text;
BEGIN
    FOREACH relation IN ARRAY ARRAY['employee','department','attendance','leave_application','payroll_run',
        'job_opening','job_applicant','notification','company_admin_invitation']
    LOOP
        EXECUTE format('CREATE POLICY platform_metrics_read ON public.%I FOR SELECT TO hrms_isolation_metrics USING (true)', relation);
    END LOOP;
END;
$policies$;

CREATE FUNCTION hrms_rls.control_tower(start_month date, month_count integer, company_filter uuid)
RETURNS jsonb LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog AS $report$
WITH bounds AS (
    SELECT start_month AS first_day, (start_month + make_interval(months => month_count))::date AS last_day
    WHERE month_count IN (1,12) AND extract(day from start_month) = 1
),
companies AS MATERIALIZED (
    SELECT c.id, c.name, c.code, c.status, c.currency, c.timezone
    FROM public.company c CROSS JOIN bounds
    WHERE company_filter IS NULL OR c.id = company_filter
),
departments AS (
    SELECT e.tenant_id, e.department_id, d.name, count(*) AS employees,
           count(*) FILTER (WHERE e.status = 'ACTIVE') AS active
    FROM public.employee e JOIN companies c ON c.id = e.tenant_id
    LEFT JOIN public.department d ON (d.tenant_id,d.id) = (e.tenant_id,e.department_id)
    GROUP BY e.tenant_id,e.department_id,d.name
),
workforce AS (
    SELECT tenant_id, sum(employees) AS employees, sum(active) AS active,
           jsonb_agg(jsonb_build_object('name',coalesce(name,'Unassigned'),'employees',employees,'active',active)
               ORDER BY active DESC, name NULLS LAST, department_id) AS departments
    FROM departments GROUP BY tenant_id
),
open_roles AS (
    SELECT j.tenant_id, count(*) AS openings, sum(coalesce(j.vacancies,1)) AS vacancies
    FROM public.job_opening j JOIN companies c ON c.id=j.tenant_id WHERE j.status='OPEN' GROUP BY j.tenant_id
),
unread AS (
    SELECT n.tenant_id,count(*) AS notifications
    FROM public.notification n JOIN companies c ON c.id=n.tenant_id WHERE NOT n.is_read GROUP BY n.tenant_id
),
invitations AS (
    SELECT DISTINCT ON (i.tenant_id) i.tenant_id,
           CASE WHEN i.accepted_at IS NULL AND i.revoked_at IS NULL THEN
               CASE WHEN i.expires_at <= current_timestamp THEN 'EXPIRED'
                    WHEN i.delivery_status='FAILED' THEN 'DELIVERY_FAILED' END END AS alert
    FROM public.company_admin_invitation i JOIN companies c ON c.id=i.tenant_id
    WHERE c.status='PENDING' ORDER BY i.tenant_id,i.created_at DESC,i.id
),
facts AS (
    SELECT e.tenant_id, date_trunc('month', m.day)::date AS month, m.metric, count(*)::numeric AS value
    FROM public.employee e JOIN companies c ON c.id=e.tenant_id CROSS JOIN bounds b
    CROSS JOIN LATERAL (VALUES (e.date_of_joining,'joiners'),(e.relieving_date,'leavers')) m(day,metric)
    WHERE m.day >= b.first_day AND m.day < b.last_day GROUP BY 1,2,3
    UNION ALL
    SELECT a.tenant_id,date_trunc('month',a.attendance_date)::date,m.metric,sum(m.value)
    FROM public.attendance a JOIN companies c ON c.id=a.tenant_id CROSS JOIN bounds b
    CROSS JOIN LATERAL (VALUES ('attendanceRecords',1),('worked',
        CASE WHEN a.status IN ('PRESENT','WORK_FROM_HOME','HALF_DAY') THEN 1 ELSE 0 END),
        ('absences',CASE WHEN a.status='ABSENT' THEN 1 ELSE 0 END)) m(metric,value)
    WHERE a.attendance_date >= b.first_day AND a.attendance_date < b.last_day GROUP BY 1,2,3
    UNION ALL
    SELECT l.tenant_id,date_trunc('month',l.from_date)::date,m.metric,sum(m.value)
    FROM public.leave_application l JOIN companies c ON c.id=l.tenant_id CROSS JOIN bounds b
    CROSS JOIN LATERAL (VALUES ('leaveRequests',1),('leaveApproved',CASE WHEN l.status='APPROVED' THEN 1 ELSE 0 END),
        ('leavePending',CASE WHEN l.status='PENDING' THEN 1 ELSE 0 END),
        ('leaveDays',CASE WHEN l.status='APPROVED' THEN l.total_days ELSE 0 END)) m(metric,value)
    WHERE l.from_date >= b.first_day AND l.from_date < b.last_day GROUP BY 1,2,3
    UNION ALL
    SELECT j.tenant_id,date_trunc('month',j.posted_on)::date,'jobsPosted',count(*)
    FROM public.job_opening j JOIN companies c ON c.id=j.tenant_id CROSS JOIN bounds b
    WHERE j.posted_on >= b.first_day AND j.posted_on < b.last_day GROUP BY 1,2
    UNION ALL
    SELECT a.tenant_id,date_trunc('month',a.applied_on)::date,'applicants',count(*)
    FROM public.job_applicant a JOIN companies c ON c.id=a.tenant_id CROSS JOIN bounds b
    WHERE a.applied_on >= b.first_day AND a.applied_on < b.last_day GROUP BY 1,2
),
company_facts AS (
    SELECT tenant_id,jsonb_object_agg(metric,value) AS counts
    FROM (SELECT tenant_id,metric,sum(value) AS value FROM facts GROUP BY 1,2) per_metric GROUP BY tenant_id
),
monthly_facts AS (
    SELECT month,jsonb_object_agg(metric,value) AS counts
    FROM (SELECT month,metric,sum(value) AS value FROM facts GROUP BY 1,2) per_metric GROUP BY month
),
payroll AS (
    SELECT p.tenant_id,c.currency,p.period_year,p.period_month,count(*) AS runs,
           count(p.total_net) AS valued_runs,sum(p.total_net) AS net
    FROM public.payroll_run p JOIN companies c ON c.id=p.tenant_id CROSS JOIN bounds b
    WHERE p.status='SUBMITTED' AND p.period_month BETWEEN 1 AND 12
      AND (p.period_year,p.period_month) >= (extract(year from b.first_day),extract(month from b.first_day))
      AND (p.period_year,p.period_month) < (extract(year from b.last_day),extract(month from b.last_day))
    GROUP BY 1,2,3,4
),
company_payroll AS (
    SELECT tenant_id,sum(runs) AS runs,sum(valued_runs) AS valued_runs,sum(net)::text AS net FROM payroll GROUP BY 1
),
monthly_payroll AS (
    SELECT period_year,period_month,jsonb_agg(jsonb_build_object('currency',currency,'net',net,'runs',runs)
        ORDER BY currency) AS amounts
    FROM (SELECT period_year,period_month,currency,sum(net)::text AS net,sum(runs) AS runs
          FROM payroll GROUP BY 1,2,3) per_currency GROUP BY 1,2
),
global_payroll AS (
    SELECT jsonb_agg(jsonb_build_object('currency',currency,'net',net,'runs',runs,'valuedRuns',valued_runs)
                     ORDER BY currency) AS amounts
    FROM (SELECT currency,sum(net)::text AS net,sum(runs) AS runs,sum(valued_runs) AS valued_runs
          FROM payroll GROUP BY currency) per_currency
)
SELECT jsonb_build_object(
    'companies',coalesce((
        SELECT jsonb_agg(jsonb_build_object('id',c.id,'name',c.name,'code',c.code,'status',c.status,'currency',c.currency,
            'timezone',c.timezone,'employees',coalesce(w.employees,0),'activeEmployees',coalesce(w.active,0),
            'departments',coalesce(w.departments,'[]'),'counts',coalesce(f.counts,'{}'),
            'openJobs',coalesce(j.openings,0),'vacancies',coalesce(j.vacancies,0),'unread',coalesce(n.notifications,0),
            'invitationAlert',i.alert,'payrollRuns',coalesce(p.runs,0),'valuedPayrollRuns',coalesce(p.valued_runs,0),'netPayroll',p.net)
            ORDER BY lower(c.name),c.id)
        FROM companies c LEFT JOIN workforce w ON w.tenant_id=c.id LEFT JOIN company_facts f ON f.tenant_id=c.id
        LEFT JOIN open_roles j ON j.tenant_id=c.id LEFT JOIN unread n ON n.tenant_id=c.id
        LEFT JOIN invitations i ON i.tenant_id=c.id LEFT JOIN company_payroll p ON p.tenant_id=c.id
    ),'[]'),
    'payroll',coalesce((SELECT amounts FROM global_payroll),'[]'),
    'months',coalesce((
        SELECT jsonb_agg(jsonb_build_object('month',to_char(m.month,'YYYY-MM'),'counts',coalesce(f.counts,'{}'),
             'payroll',coalesce(p.amounts,'[]')) ORDER BY m.month)
        FROM bounds b CROSS JOIN LATERAL generate_series(b.first_day::timestamp,b.last_day::timestamp - interval '1 month',
                                                       interval '1 month') m(month)
        LEFT JOIN monthly_facts f ON f.month=m.month::date
        LEFT JOIN monthly_payroll p ON (p.period_year,p.period_month)=(extract(year from m.month),extract(month from m.month))
    ),'[]'))
$report$;

ALTER FUNCTION hrms_rls.control_tower(date,integer,uuid) OWNER TO hrms_isolation_metrics;
REVOKE EXECUTE ON FUNCTION hrms_rls.control_tower(date,integer,uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION hrms_rls.control_tower(date,integer,uuid) TO hrms_isolation_app;
COMMIT;
