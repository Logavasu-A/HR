-- Apply as the local DBA on the verified rehearsal or isolation restore only.
-- Supply a 32-byte hexadecimal key through HRMS_ISOLATION_RLS_KEY.
\set ON_ERROR_STOP on
\getenv rls_key HRMS_ISOLATION_RLS_KEY
SELECT current_database() IN ('hrms_isolation_20261119', 'hrms_rls_rehearsal_20261119') AS approved_restore
\gset
\if :approved_restore
\else
    \echo RLS rollout requires the verified local restore.
    \quit 3
\endif

BEGIN;
SET LOCAL lock_timeout = '5s';
SELECT 'CREATE ROLE hrms_isolation_auth_lookup NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS'
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'hrms_isolation_auth_lookup')
\gexec

CREATE SCHEMA hrms_rls AUTHORIZATION hrms_isolation_owner;
REVOKE ALL ON SCHEMA hrms_rls FROM PUBLIC;
GRANT USAGE ON SCHEMA hrms_rls TO hrms_isolation_app, hrms_isolation_auth_lookup;
CREATE EXTENSION pgcrypto WITH SCHEMA hrms_rls;

CREATE TABLE hrms_rls.signing_key (
    singleton boolean PRIMARY KEY CHECK (singleton),
    secret bytea NOT NULL CHECK (octet_length(secret) = 32)
);
ALTER TABLE hrms_rls.signing_key OWNER TO hrms_isolation_owner;
REVOKE ALL ON TABLE hrms_rls.signing_key FROM PUBLIC;
INSERT INTO hrms_rls.signing_key VALUES (true, decode(:'rls_key', 'hex'));

CREATE FUNCTION hrms_rls.current_tenant() RETURNS uuid
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = pg_catalog AS $function$
DECLARE
    claim text := current_setting('hrms.tenant_binding', true);
    transaction_id xid8 := pg_current_xact_id_if_assigned();
    candidate text;
    secret_key bytea;
    expected bytea;
    supplied bytea;
    mismatch integer := 0;
BEGIN
    IF transaction_id IS NULL OR claim IS NULL OR length(claim) <> 101 OR claim !~
        '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}:[0-9a-f]{64}$' THEN
        RETURN NULL;
    END IF;
    candidate := left(claim, 36);
    SELECT secret INTO STRICT secret_key FROM hrms_rls.signing_key WHERE singleton;
    expected := hrms_rls.hmac(
        convert_to(candidate || ':' || pg_backend_pid() || ':' || transaction_id, 'UTF8'), secret_key, 'sha256');
    supplied := decode(right(claim, 64), 'hex');
    FOR position IN 0..31 LOOP
        mismatch := mismatch | (get_byte(expected, position) # get_byte(supplied, position));
    END LOOP;
    RETURN CASE WHEN mismatch = 0 THEN candidate::uuid ELSE NULL END;
END;
$function$;
ALTER FUNCTION hrms_rls.current_tenant() OWNER TO hrms_isolation_owner;

DO $policies$
DECLARE
    relation text;
    covered integer := 0;
BEGIN
    FOR relation IN
        SELECT c.relname
          FROM pg_attribute a JOIN pg_class c ON c.oid = a.attrelid
          JOIN pg_namespace n ON n.oid = c.relnamespace
         WHERE n.nspname = 'public' AND c.relkind = 'r' AND a.attname = 'tenant_id'
           AND a.attnotnull AND NOT a.attisdropped
         ORDER BY c.relname
    LOOP
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', relation);
        EXECUTE format('ALTER TABLE public.%I FORCE ROW LEVEL SECURITY', relation);
        EXECUTE format(
            'CREATE POLICY tenant_rows ON public.%I TO hrms_isolation_app
             USING (tenant_id = (SELECT hrms_rls.current_tenant()))
             WITH CHECK (tenant_id = (SELECT hrms_rls.current_tenant()))', relation);
        covered := covered + 1;
    END LOOP;
    IF covered <> 35 THEN
        RAISE EXCEPTION 'Expected 35 company-owned tables, found %; review policy coverage', covered;
    END IF;
END;
$policies$;

-- Authentication can identify an account or validate an opaque proof before binding a tenant.
-- The definer can read only the columns named here and cannot sign a tenant context.
GRANT USAGE ON SCHEMA public TO hrms_isolation_auth_lookup;
GRANT SELECT (id, code, status) ON public.company TO hrms_isolation_auth_lookup;
GRANT SELECT (id, tenant_id, username, email) ON public.app_user TO hrms_isolation_auth_lookup;
GRANT SELECT (id, tenant_id, user_id, expires_at) ON public.company_session TO hrms_isolation_auth_lookup;
GRANT SELECT (tenant_id, secret_hash, accepted_at, revoked_at, expires_at)
    ON public.company_admin_invitation TO hrms_isolation_auth_lookup;
CREATE POLICY auth_lookup ON public.app_user FOR SELECT TO hrms_isolation_auth_lookup USING (true);
CREATE POLICY auth_lookup ON public.company_session FOR SELECT TO hrms_isolation_auth_lookup USING (true);
CREATE POLICY auth_lookup ON public.company_admin_invitation FOR SELECT TO hrms_isolation_auth_lookup USING (true);

CREATE FUNCTION hrms_rls.lookup_account(allow_additional boolean, company_code text, identifier text)
RETURNS TABLE(user_id bigint, tenant_id uuid)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog AS $function$
    SELECT u.id, u.tenant_id FROM public.app_user u JOIN public.company c ON c.id = u.tenant_id
     WHERE (allow_additional OR c.id = '00000000-0000-0000-0000-000000000001'::uuid)
       AND lower(c.code) = lower(company_code) AND c.status = 'ACTIVE'
       AND (lower(u.username) = lower(identifier) OR lower(u.email) = lower(identifier))
     ORDER BY CASE WHEN lower(u.username) = lower(identifier) THEN 0 ELSE 1 END
     FETCH FIRST 1 ROW WITH TIES
$function$;

CREATE FUNCTION hrms_rls.session_active(session_id uuid, company_id uuid, actor_id bigint) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog AS $function$
    SELECT EXISTS (
        SELECT FROM public.company_session s JOIN public.company c ON c.id = s.tenant_id
         WHERE s.id = session_id AND s.tenant_id = company_id AND s.user_id = actor_id
           AND s.expires_at > current_timestamp AND c.status = 'ACTIVE')
$function$;

CREATE FUNCTION hrms_rls.invitation_company(digest text) RETURNS uuid
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog AS $function$
    SELECT i.tenant_id FROM public.company_admin_invitation i
     WHERE length(digest) = 64 AND digest ~ '^[0-9a-f]+$' AND i.secret_hash = digest
       AND i.accepted_at IS NULL AND i.revoked_at IS NULL AND i.expires_at > current_timestamp
$function$;

ALTER FUNCTION hrms_rls.lookup_account(boolean, text, text) OWNER TO hrms_isolation_auth_lookup;
ALTER FUNCTION hrms_rls.session_active(uuid, uuid, bigint) OWNER TO hrms_isolation_auth_lookup;
ALTER FUNCTION hrms_rls.invitation_company(text) OWNER TO hrms_isolation_auth_lookup;

REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA hrms_rls FROM PUBLIC;
GRANT EXECUTE ON FUNCTION hrms_rls.hmac(bytea, bytea, text) TO hrms_isolation_owner;
GRANT EXECUTE ON FUNCTION hrms_rls.current_tenant(),
    hrms_rls.lookup_account(boolean, text, text), hrms_rls.session_active(uuid, uuid, bigint),
    hrms_rls.invitation_company(text) TO hrms_isolation_app;
COMMIT;
