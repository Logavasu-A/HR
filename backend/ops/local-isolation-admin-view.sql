-- Apply only to the verified local restore or its Admin View rehearsal.
\set ON_ERROR_STOP on
SELECT current_database() IN ('hrms_isolation_20261119', 'hrms_admin_view_rehearsal_20261121')
       AND (SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
            WHERE n.nspname='public' AND c.relkind='r' AND c.relrowsecurity AND c.relforcerowsecurity) = 35
       AS approved_restore
\gset
\if :approved_restore
\else
    \echo Admin View requires the verified local restore with forced tenant RLS.
    \quit 3
\endif

BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL ROLE hrms_isolation_owner;

CREATE TABLE public.platform_workspace (
    session_id uuid PRIMARY KEY REFERENCES public.platform_sessions(id) ON DELETE CASCADE,
    id uuid NOT NULL UNIQUE,
    company_id uuid NOT NULL REFERENCES public.company(id),
    reason varchar(300) NOT NULL CHECK (length(btrim(reason)) >= 5),
    opened_at timestamptz NOT NULL DEFAULT current_timestamp
);
GRANT SELECT, INSERT, UPDATE, DELETE ON public.platform_workspace TO hrms_isolation_app;

ALTER TABLE public.platform_audit_event
    ADD COLUMN actor_id uuid,
    ADD COLUMN workspace_id uuid,
    ADD COLUMN previous_tenant_id uuid REFERENCES public.company(id),
    ADD COLUMN reason varchar(300),
    ADD COLUMN request_method varchar(8),
    ADD COLUMN request_path varchar(500),
    ADD COLUMN http_status smallint CHECK (http_status BETWEEN 100 AND 599),
    ADD COLUMN request_id uuid,
    ADD COLUMN remote_address varchar(60);

CREATE INDEX platform_audit_workspace_idx ON public.platform_audit_event (workspace_id, id DESC)
    WHERE workspace_id IS NOT NULL;

-- The runtime has no UPDATE grant on platform sessions.
CREATE FUNCTION hrms_rls.workspace_lock_session(owner_session uuid, owner_id uuid) RETURNS boolean
LANGUAGE sql VOLATILE SECURITY DEFINER SET search_path = pg_catalog AS $function$
    SELECT EXISTS (
        SELECT FROM public.platform_sessions s JOIN public.platform_users u ON u.id = s.user_id
         WHERE s.id = owner_session AND s.user_id = owner_id AND u.active AND s.expires_at > clock_timestamp()
         FOR UPDATE OF s
    )
$function$;
REVOKE EXECUTE ON FUNCTION hrms_rls.workspace_lock_session(uuid, uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION hrms_rls.workspace_lock_session(uuid, uuid) TO hrms_isolation_app;
COMMIT;
