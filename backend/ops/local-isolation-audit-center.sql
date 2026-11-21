\set ON_ERROR_STOP on
SELECT current_database() IN ('hrms_isolation_20261119','hrms_audit_rehearsal_20261121')
   AND (SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
        WHERE n.nspname='public' AND c.relkind='r' AND c.relrowsecurity AND c.relforcerowsecurity)=35
   AND (SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
        WHERE n.nspname='hrms_rls' AND c.relname IN ('finance_event','finance_approval') AND c.relforcerowsecurity)=2 AS approved
\gset
\if :approved
\else
\quit 3
\endif
BEGIN;
SET LOCAL lock_timeout='5s';
SELECT 'CREATE ROLE hrms_isolation_audit_reader NOLOGIN NOSUPERUSER NOBYPASSRLS'
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='hrms_isolation_audit_reader')
\gexec
DO $guard$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname='hrms_isolation_audit_reader'
               AND (rolcanlogin OR rolsuper OR rolbypassrls OR rolcreatedb OR rolcreaterole OR rolreplication))
       OR EXISTS (SELECT 1 FROM pg_auth_members m JOIN pg_roles r ON r.oid IN (m.member,m.roleid)
                  WHERE r.rolname='hrms_isolation_audit_reader') THEN
        RAISE EXCEPTION 'Audit reader requires an isolated non-login role';
    END IF;
END;
$guard$;

ALTER TABLE public.audit_log ADD COLUMN actor_key bigint, ADD COLUMN actor_role text, ADD COLUMN request_id uuid;
ALTER TABLE public.platform_audit_event ADD COLUMN actor_role text;
CREATE INDEX audit_center_company_time ON public.audit_log (at DESC, id DESC);
CREATE INDEX audit_center_platform_time ON public.platform_audit_event (occurred_at DESC, id DESC);
CREATE INDEX audit_center_finance_time ON hrms_rls.finance_event (occurred_at DESC, id DESC);
GRANT USAGE ON SCHEMA public,hrms_rls TO hrms_isolation_audit_reader;
GRANT SELECT (id,name,code) ON public.company TO hrms_isolation_audit_reader;
GRANT SELECT (id,tenant_id,username,actor_key,actor_role,at,action,method,path,status,ip,request_id)
    ON public.audit_log TO hrms_isolation_audit_reader;
GRANT SELECT (id,actor,actor_id,actor_role,tenant_id,workspace_id,action,occurred_at,request_method,request_path,
              http_status,request_id,remote_address) ON public.platform_audit_event TO hrms_isolation_audit_reader;
GRANT SELECT (id,tenant_id,actor,actor_role,workspace,action,related_id,path,method,status,ip,request_id,occurred_at)
    ON hrms_rls.finance_event TO hrms_isolation_audit_reader;
GRANT SELECT (id,tenant_id,maker,checker,reason,status,method,path,created_at,expires_at)
    ON hrms_rls.finance_approval TO hrms_isolation_audit_reader;
GRANT SELECT (id,user_id,expires_at) ON public.platform_sessions TO hrms_isolation_audit_reader;
GRANT SELECT (id,active) ON public.platform_users TO hrms_isolation_audit_reader;
GRANT SELECT (user_id,role) ON hrms_rls.platform_role TO hrms_isolation_audit_reader;
GRANT SELECT ON hrms_rls.signing_key TO hrms_isolation_audit_reader;
GRANT EXECUTE ON FUNCTION hrms_rls.hmac(bytea,bytea,text) TO hrms_isolation_audit_reader;
CREATE POLICY audit_center_read ON public.audit_log FOR SELECT TO hrms_isolation_audit_reader USING (true);
CREATE POLICY audit_center_read ON hrms_rls.finance_event FOR SELECT TO hrms_isolation_audit_reader USING (true);
CREATE POLICY audit_center_read ON hrms_rls.finance_approval FOR SELECT TO hrms_isolation_audit_reader USING (true);

-- Return approved route labels only. Never forward verbatim path segments, reasons, query strings or fragments.
CREATE FUNCTION hrms_rls.audit_operation(raw text) RETURNS text
LANGUAGE sql IMMUTABLE SET search_path=pg_catalog AS $fn$
    SELECT CASE
      WHEN raw ~ '^/api/core/employees/[0-9]+/bank-details([/#?]|$)' THEN 'Employee bank details'
      WHEN raw ~ '^/api/payroll/runs/[0-9]+/submit([/#?]|$)' THEN 'Payroll submission'
      WHEN raw ~ '^/api/payroll/runs/[0-9]+/slips([/#?]|$)' THEN 'Payroll slips'
      WHEN raw ~ '^/api/payroll/runs([/#?]|$)' THEN 'Payroll runs'
      WHEN raw ~ '^/api/payroll/(structures|components|assignments)([/#?]|$)' THEN 'Salary configuration'
      WHEN raw ~ '^/api/me/payslips([/#?]|$)' THEN 'Employee payslips'
      WHEN raw ~ '^/api/sensitive/approvals([/#?]|$)' THEN 'Financial approval'
      WHEN raw ~ '^/api/sensitive/step-up([/#?]|$)' THEN 'Financial verification'
      WHEN raw ~ '^/api/platform/audit([/#?]|$)' THEN 'Audit Center'
      WHEN raw ~ '^/api/platform/control-tower([/#?]|$)' THEN 'Control Tower'
      WHEN raw ~ '^/api/platform/workspace([/#?]|$)' THEN 'Admin View'
      WHEN raw ~ '^/api/platform/access([/#?]|$)' THEN 'Platform role access'
      WHEN raw ~ '^/api/platform/companies([/#?]|$)' THEN 'Company registry'
      WHEN raw ~ '^/api/(platform/)?auth([/#?]|$)' THEN 'Authentication'
      WHEN raw ~ '^/api/core/employees([/#?]|$)' THEN 'Employee records'
      WHEN raw ~ '^/api/core([/#?]|$)' THEN 'Core HR'
      WHEN raw ~ '^/api/admin([/#?]|$)' THEN 'Company administration'
      WHEN raw ~ '^/api/attendance([/#?]|$)' THEN 'Attendance'
      WHEN raw ~ '^/api/leave([/#?]|$)' THEN 'Leave'
      WHEN raw ~ '^/api/expense([/#?]|$)' THEN 'Expenses'
      WHEN raw ~ '^/api/recruitment([/#?]|$)' THEN 'Recruitment'
      WHEN raw ~ '^/api/performance([/#?]|$)' THEN 'Performance'
      WHEN raw ~ '^/api/(dashboard|me|notifications)([/#?]|$)' THEN 'Company workspace'
      ELSE 'Restricted audit metadata' END
$fn$;

CREATE FUNCTION hrms_rls.audit_center(
    session_key uuid, actor_key uuid, proof text, from_time timestamptz, until_time timestamptz,
    company_filter uuid, source_filter text, outcome_filter text, approval_filter uuid, search_filter text, page_start integer
) RETURNS jsonb
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $fn$
DECLARE tx xid8 := pg_current_xact_id_if_assigned();
BEGIN
    IF tx IS NULL OR session_key IS NULL OR actor_key IS NULL OR proof IS NULL OR proof !~ '^[0-9a-f]{64}$'
       OR proof IS DISTINCT FROM (SELECT encode(hrms_rls.hmac(convert_to(
           'audit-center:'||session_key||':'||actor_key||':'||pg_backend_pid()||':'||tx,'UTF8'), secret,'sha256'),'hex')
           FROM hrms_rls.signing_key WHERE singleton)
       OR NOT EXISTS (SELECT 1 FROM public.platform_sessions s JOIN public.platform_users u ON u.id=s.user_id
                      JOIN hrms_rls.platform_role r ON r.user_id=u.id WHERE s.id=session_key AND u.id=actor_key
                      AND s.expires_at>statement_timestamp() AND u.active AND r.role='GLOBAL_SUPER_ADMIN') THEN
        RAISE EXCEPTION 'Audit Center authorization required' USING ERRCODE='42501';
    END IF;
    IF from_time IS NULL OR until_time IS NULL OR from_time>=until_time OR until_time-from_time>interval '366 days'
       OR page_start IS NULL OR page_start NOT BETWEEN 0 AND 10000 OR length(search_filter)>80
       OR source_filter NOT IN ('','PLATFORM','COMPANY','FINANCE')
       OR outcome_filter NOT IN ('','SUCCESS','DENIED','FAILED') THEN
        RAISE EXCEPTION 'Invalid audit query' USING ERRCODE='22023';
    END IF;

    RETURN (
      WITH entries AS (
        SELECT 'PLATFORM' AS source, 'p:'||p.id AS key, p.occurred_at AS occurred, p.tenant_id,
               p.actor AS actor, p.actor_id::text AS actor_id, p.actor_role, p.workspace_id::text AS workspace,
               p.action, p.request_path AS path, p.request_method AS method, p.request_id, p.remote_address AS ip,
               coalesce(p.http_status, CASE WHEN p.action LIKE '%FAILED' THEN 401
                    WHEN p.action LIKE '%DENIED' THEN 403 ELSE 200 END) AS status,
               NULL::uuid AS approval_id, NULL::text AS approval_state, NULL::text AS reason,
               NULL::text AS maker, NULL::text AS checker
          FROM public.platform_audit_event p
          WHERE source_filter IN ('','PLATFORM') AND p.occurred_at>=from_time AND p.occurred_at<until_time
            AND (company_filter IS NULL OR p.tenant_id=company_filter) AND approval_filter IS NULL
        UNION ALL
        SELECT 'COMPANY', 'c:'||a.id, a.at AT TIME ZONE 'Asia/Kolkata', a.tenant_id,
               a.username, a.actor_key::text, a.actor_role, NULL, a.action, a.path, a.method,
               coalesce(a.request_id,substring(a.path from '#([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$')::uuid),
               a.ip, coalesce(a.status,200),
               NULL,NULL,NULL,NULL,NULL
          FROM public.audit_log a
          WHERE source_filter IN ('','COMPANY') AND a.at>=from_time AT TIME ZONE 'Asia/Kolkata'
            AND a.at<until_time AT TIME ZONE 'Asia/Kolkata'
            AND (company_filter IS NULL OR a.tenant_id=company_filter) AND approval_filter IS NULL
        UNION ALL
        SELECT 'FINANCE', 'f:'||f.id, f.occurred_at, f.tenant_id, f.actor, f.actor, f.actor_role,
               nullif(f.workspace,''), f.action, coalesce(a.path,f.path), f.method, f.request_id, f.ip, f.status,
               f.related_id,a.status,a.reason,a.maker,a.checker
          FROM hrms_rls.finance_event f
          LEFT JOIN hrms_rls.finance_approval a ON (a.tenant_id,a.id)=(f.tenant_id,f.related_id)
          WHERE source_filter IN ('','FINANCE') AND f.occurred_at>=from_time AND f.occurred_at<until_time
            AND (company_filter IS NULL OR f.tenant_id=company_filter)
            AND (approval_filter IS NULL OR f.related_id=approval_filter)
      ), projected AS (
        SELECT e.source,e.key,e.occurred,e.tenant_id,c.name AS company,e.actor,e.actor_id,e.actor_role,e.workspace,
               CASE WHEN e.action IN ('ACCEPT_ADMIN_INVITATION','CONTROL_TOWER_READ','CREATE_COMPANY','EDIT_COMPANY',
                   'ISSUE_ADMIN_INVITATION','OWNER_LOGIN','PLATFORM_LOGIN','PLATFORM_ROLE_CHANGE','REACTIVATE_COMPANY',
                   'REVOKE_ADMIN_INVITATION','SUSPEND_COMPANY','WORKSPACE_CLOSE','WORKSPACE_OPEN','WORKSPACE_REQUEST',
                   'WORKSPACE_REQUEST_DENIED','WORKSPACE_SWITCH','APPROVE','CREATE','DECISION','DELETE','FINANCIAL_DENIAL',
                   'LOGIN','LOGIN_FAILED','READ_BANK_DETAILS','REJECT','SUBMIT','UPDATE','UPDATE_BANK_DETAILS','VIEW',
                   'CHANGE_APPROVED','CHANGE_EXECUTED','CHANGE_QUEUED','CHANGE_REJECTED','FINANCIAL_READ',
                   'STEP_UP_FAILED','STEP_UP_GRANTED','STEP_UP_THROTTLED','FINANCE_STEP_UP','FINANCE_STEP_UP_FAILED',
                   'AUTHORIZATION_DENIED','PLATFORM_REQUEST','AUDIT_CENTER_READ','REQUEST_COMPLETED') THEN e.action
                   ELSE 'REQUEST_COMPLETED' END AS action,
               hrms_rls.audit_operation(e.path) AS operation,
               CASE WHEN e.method IN ('GET','POST','PUT','DELETE','PATCH','HEAD') THEN e.method END AS method,
               e.request_id, CASE WHEN ip ~ '^[0-9a-fA-F:.]{2,45}$' THEN ip END AS ip,
               e.status, CASE WHEN e.status<400 THEN 'SUCCESS' WHEN e.status<500 THEN 'DENIED' ELSE 'FAILED' END AS outcome,
               e.approval_id,e.approval_state,e.reason,e.maker,e.checker
          FROM entries e LEFT JOIN public.company c ON c.id=e.tenant_id
      ), page AS (
        SELECT * FROM projected p WHERE (outcome_filter='' OR p.outcome=outcome_filter)
          AND (search_filter='' OR strpos(lower(concat_ws(' ',p.action,p.operation,p.actor,p.company,
                  p.request_id::text,p.approval_id::text)),lower(search_filter))>0)
        ORDER BY p.occurred DESC,p.key DESC LIMIT 51 OFFSET page_start
      )
      SELECT coalesce(jsonb_agg(to_jsonb(p) ORDER BY p.occurred DESC,p.key DESC),'[]'::jsonb) FROM page p
    );
END;
$fn$;
ALTER FUNCTION hrms_rls.audit_center(uuid,uuid,text,timestamptz,timestamptz,uuid,text,text,uuid,text,integer)
    OWNER TO hrms_isolation_audit_reader;
REVOKE ALL ON FUNCTION hrms_rls.audit_center(uuid,uuid,text,timestamptz,timestamptz,uuid,text,text,uuid,text,integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION hrms_rls.audit_center(uuid,uuid,text,timestamptz,timestamptz,uuid,text,text,uuid,text,integer)
    TO hrms_isolation_app;
COMMIT;
