\set ON_ERROR_STOP on
SELECT current_database() IN ('hrms_isolation_20261119','hrms_access_rehearsal_20261121')
AND (SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
     WHERE n.nspname='public' AND c.relkind='r' AND c.relrowsecurity AND c.relforcerowsecurity)=35 AS approved
\gset
\if :approved
\else
\quit 3
\endif
BEGIN;
SET LOCAL lock_timeout='5s';
DO $guard$
BEGIN
    IF (SELECT array_agg(username ORDER BY username) FROM public.platform_users WHERE active)
          IS DISTINCT FROM ARRAY['logavasu']::varchar[] THEN
        RAISE EXCEPTION 'Existing active platform accounts require approved role assignments';
    END IF;
END;
$guard$;

CREATE TABLE hrms_rls.platform_role (
    user_id uuid PRIMARY KEY REFERENCES public.platform_users(id) ON DELETE CASCADE,
    role text NOT NULL CHECK (role IN ('GLOBAL_SUPER_ADMIN','PLATFORM_ADMINISTRATOR','SECURITY_ADMINISTRATOR'))
);
ALTER TABLE hrms_rls.platform_role OWNER TO hrms_isolation_owner;
CREATE UNIQUE INDEX one_global_super_admin ON hrms_rls.platform_role (role) WHERE role='GLOBAL_SUPER_ADMIN';
INSERT INTO hrms_rls.platform_role SELECT id,'GLOBAL_SUPER_ADMIN' FROM public.platform_users WHERE username='logavasu' AND active;
GRANT SELECT,INSERT,UPDATE,DELETE ON hrms_rls.platform_role TO hrms_isolation_app;

CREATE FUNCTION hrms_rls.verified_access_claim() RETURNS text[]
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = pg_catalog AS $fn$
DECLARE
    setting text := current_setting('hrms.tenant_binding',true);
    txid xid8 := pg_current_xact_id_if_assigned();
    pieces text[];
    msg text;
    secret_key bytea;
    expected bytea;
    supplied bytea;
    mismatch integer := 0;
BEGIN
    IF txid IS NULL OR setting IS NULL OR length(setting)>121 OR setting !~
       '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}:([1-9][0-9]{0,18}:)?[0-9a-f]{64}$' THEN
        RETURN NULL;
    END IF;
    pieces := string_to_array(setting,':');
    msg := pieces[1] || ':' || pg_backend_pid() || ':' || txid;
    IF cardinality(pieces)=3 THEN msg := 'company-user:' || msg || ':' || pieces[2]; END IF;
    SELECT secret INTO STRICT secret_key FROM hrms_rls.signing_key WHERE singleton;
    expected := hrms_rls.hmac(convert_to(msg,'UTF8'),secret_key,'sha256');
    supplied := decode(pieces[cardinality(pieces)],'hex');
    FOR position IN 0..31 LOOP
        mismatch := mismatch | (get_byte(expected,position) # get_byte(supplied,position));
    END LOOP;
    RETURN CASE WHEN mismatch=0 THEN pieces[1:cardinality(pieces)-1] ELSE NULL END;
END;
$fn$;
ALTER FUNCTION hrms_rls.verified_access_claim() OWNER TO hrms_isolation_owner;
REVOKE ALL ON FUNCTION hrms_rls.verified_access_claim() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION hrms_rls.verified_access_claim() TO hrms_isolation_auth_lookup;

CREATE OR REPLACE FUNCTION hrms_rls.current_tenant() RETURNS uuid
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $fn$
    SELECT (hrms_rls.verified_access_claim())[1]::uuid
$fn$;

-- This existing non-login lookup role can resolve role markers and employee relationships.
GRANT SELECT (employee_id,role_id,active) ON public.app_user TO hrms_isolation_auth_lookup;
GRANT SELECT (tenant_id,role_id,permission) ON public.role_permission TO hrms_isolation_auth_lookup;
GRANT SELECT (tenant_id,id,department_id,reports_to_id) ON public.employee TO hrms_isolation_auth_lookup;
CREATE POLICY record_scope_lookup ON public.role_permission FOR SELECT TO hrms_isolation_auth_lookup USING (true);
CREATE POLICY record_scope_lookup ON public.employee FOR SELECT TO hrms_isolation_auth_lookup USING (true);

CREATE FUNCTION hrms_rls.record_access() RETURNS TABLE(mode text, actor_id bigint, employee_id bigint, department_id bigint)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $fn$
    WITH claim AS MATERIALIZED (SELECT hrms_rls.verified_access_claim() AS v)
    SELECT CASE WHEN claim.v IS NULL THEN 'deny'
          WHEN cardinality(claim.v)=1 THEN 'company'
          WHEN u.id IS NULL THEN 'deny'
          WHEN markers.total=0 THEN 'company'
          WHEN markers.total=1 AND markers.marker IN ('scope:department','scope:direct-reports','scope:self')
            THEN split_part(markers.marker,':',2)
          ELSE 'deny' END,
          u.id,u.employee_id,e.department_id
    FROM claim
    LEFT JOIN public.app_user u ON (u.tenant_id,u.id)=(claim.v[1]::uuid,claim.v[2]::bigint) AND u.active
    LEFT JOIN public.employee e ON (e.tenant_id,e.id)=(u.tenant_id,u.employee_id)
    CROSS JOIN LATERAL (
        SELECT count(*) AS total,min(p.permission) AS marker FROM public.role_permission p
        WHERE (p.tenant_id,p.role_id)=(u.tenant_id,u.role_id) AND p.permission LIKE 'scope:%'
    ) markers
$fn$;
ALTER FUNCTION hrms_rls.record_access() OWNER TO hrms_isolation_auth_lookup;
REVOKE ALL ON FUNCTION hrms_rls.record_access() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION hrms_rls.record_access() TO hrms_isolation_app;

CREATE FUNCTION hrms_rls.visible_employees() RETURNS SETOF bigint
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $fn$
    SELECT e.id FROM hrms_rls.record_access() a JOIN public.employee e ON e.tenant_id=hrms_rls.current_tenant()
    WHERE e.id=a.employee_id OR (a.mode='department' AND e.department_id=a.department_id)
       OR (a.mode='direct-reports' AND e.reports_to_id=a.employee_id)
$fn$;
ALTER FUNCTION hrms_rls.visible_employees() OWNER TO hrms_isolation_auth_lookup;
REVOKE ALL ON FUNCTION hrms_rls.visible_employees() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION hrms_rls.visible_employees() TO hrms_isolation_app;
GRANT EXECUTE ON FUNCTION hrms_rls.current_tenant() TO hrms_isolation_auth_lookup;

DO $policies$
DECLARE entry record;
BEGIN
    FOR entry IN SELECT * FROM (VALUES
        ('employee','id = (SELECT employee_id FROM hrms_rls.record_access())
            OR ((SELECT mode FROM hrms_rls.record_access()) = ''department''
                AND department_id = (SELECT department_id FROM hrms_rls.record_access()))
            OR ((SELECT mode FROM hrms_rls.record_access()) = ''direct-reports''
                AND reports_to_id = (SELECT employee_id FROM hrms_rls.record_access()))'),
        ('department','id IN (SELECT department_id FROM public.employee)'),
        ('app_user','id=(SELECT actor_id FROM hrms_rls.record_access()) OR employee_id IN (SELECT hrms_rls.visible_employees())'),
        ('attendance','employee_id IN (SELECT hrms_rls.visible_employees())'),
        ('leave_application','employee_id IN (SELECT hrms_rls.visible_employees())'),
        ('leave_allocation','employee_id IN (SELECT hrms_rls.visible_employees())'),
        ('expense_claim','employee_id IN (SELECT hrms_rls.visible_employees())'),
        ('expense_claim_line','claim_id IN (SELECT id FROM public.expense_claim)'),
        ('employee_advance','employee_id IN (SELECT hrms_rls.visible_employees())'),
        ('goal','employee_id IN (SELECT hrms_rls.visible_employees())'),
        ('appraisal','employee_id IN (SELECT hrms_rls.visible_employees())'),
        ('salary_slip','employee_id=(SELECT employee_id FROM hrms_rls.record_access())'),
        ('salary_slip_line','slip_id IN (SELECT id FROM public.salary_slip)'),
        ('salary_assignment','false'),('salary_component','false'),('salary_structure','false'),
        ('salary_structure_line','false'),('payroll_run','false'),('job_opening','false'),
        ('job_applicant','false'),('interview','false')
    ) AS v(relation,predicate) LOOP
        EXECUTE format('CREATE POLICY company_record_scope ON public.%I AS RESTRICTIVE TO hrms_isolation_app
            USING ((SELECT mode FROM hrms_rls.record_access()) = ''company'' OR (
                (SELECT mode FROM hrms_rls.record_access()) IN (''department'',''direct-reports'',''self'') AND (%s)))
            WITH CHECK ((SELECT mode FROM hrms_rls.record_access()) = ''company'' OR (
                (SELECT mode FROM hrms_rls.record_access()) IN (''department'',''direct-reports'',''self'') AND (%s)))',
            entry.relation,entry.predicate,entry.predicate);
    END LOOP;
END;
$policies$;
COMMIT;
