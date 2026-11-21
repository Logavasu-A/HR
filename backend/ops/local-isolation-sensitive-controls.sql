\set ON_ERROR_STOP on
SELECT current_database() IN ('hrms_isolation_20261119','hrms_sensitive_rehearsal_20261121')
  AND (SELECT count(*) FROM pg_policy WHERE polname='company_record_scope' AND NOT polpermissive)=21 AS approved
\gset
\if :approved
\else
\quit 3
\endif
BEGIN;
SET LOCAL lock_timeout='5s';
ALTER TABLE public.platform_sessions ADD COLUMN finance_verified_at timestamptz;
GRANT UPDATE (finance_verified_at) ON public.platform_sessions TO hrms_isolation_app;
CREATE TABLE hrms_rls.finance_event (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES public.company(id),
    actor text NOT NULL,
    actor_role text NOT NULL,
    session_id uuid NOT NULL,
    workspace text NOT NULL,
    action text NOT NULL,
    related_id uuid,
    path text NOT NULL,
    method text NOT NULL,
    status integer NOT NULL,
    ip text,
    request_id uuid NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX finance_event_step ON hrms_rls.finance_event (tenant_id,actor,action,occurred_at DESC);

CREATE TABLE hrms_rls.finance_approval (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES public.company(id),
    maker text NOT NULL,
    method text NOT NULL CHECK (method IN ('POST','PUT','DELETE')),
    path text NOT NULL,
    fingerprint varchar(64) NOT NULL,
    reason text NOT NULL CHECK (reason IN ('REGULAR_PROCESSING','RECORD_CORRECTION','AUTHORIZED_CHANGE')),
    status text NOT NULL CHECK (status IN ('PENDING','APPROVED','REJECTED','EXECUTED')) DEFAULT 'PENDING',
    checker text,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    expires_at timestamptz NOT NULL DEFAULT (clock_timestamp() + interval '24 hours'),
    CONSTRAINT finance_separate_checker CHECK (checker IS NULL OR checker <> maker),
    CONSTRAINT finance_checker_state CHECK ((status='PENDING') = (checker IS NULL))
);
CREATE INDEX finance_approval_queue ON hrms_rls.finance_approval (tenant_id, created_at DESC);
CREATE INDEX finance_approval_match ON hrms_rls.finance_approval (tenant_id,maker,path,method,fingerprint,created_at DESC);
DO $tables$
DECLARE name text;
BEGIN
    FOREACH name IN ARRAY ARRAY['finance_event','finance_approval'] LOOP
        EXECUTE format('ALTER TABLE hrms_rls.%I OWNER TO hrms_isolation_owner',name);
        EXECUTE format('ALTER TABLE hrms_rls.%I ENABLE ROW LEVEL SECURITY',name);
        EXECUTE format('ALTER TABLE hrms_rls.%I FORCE ROW LEVEL SECURITY',name);
        EXECUTE format('CREATE POLICY finance_tenant ON hrms_rls.%I TO hrms_isolation_app
             USING (tenant_id=(SELECT hrms_rls.current_tenant()))
             WITH CHECK (tenant_id=(SELECT hrms_rls.current_tenant()))',name);
    END LOOP;
END;
$tables$;
GRANT SELECT,INSERT ON hrms_rls.finance_event,hrms_rls.finance_approval TO hrms_isolation_app;
GRANT UPDATE (status,checker) ON hrms_rls.finance_approval TO hrms_isolation_app;
COMMIT;
