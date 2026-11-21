\set ON_ERROR_STOP on
SELECT current_database() IN ('hrms_isolation_20261119','hrms_audit_rehearsal_20261121') AS approved
\gset
\if :approved
\else
\quit 3
\endif
BEGIN;
CREATE TEMP TABLE audit_probe ON COMMIT DROP AS
SELECT u.id AS actor, gen_random_uuid() AS session_id, gen_random_uuid() AS correlation,
       (SELECT id FROM public.company WHERE code='isolation-alpha') AS alpha
FROM public.platform_users u WHERE u.username='logavasu' AND u.active;
INSERT INTO public.platform_sessions(id,user_id,expires_at)
SELECT session_id,actor,clock_timestamp()+interval '1 minute' FROM audit_probe;
INSERT INTO public.platform_audit_event(actor,actor_id,action,reason,request_path,request_id,http_status)
SELECT 'logavasu',actor,'WORKSPACE_REQUEST_DENIED','PRIVATE-AUDIT-REASON-PROBE',
       '/api/core/employees/123/bank-details?account=PRIVATE-AUDIT-URL-PROBE',correlation,403 FROM audit_probe;
ALTER TABLE audit_probe ADD COLUMN proof text;
UPDATE audit_probe p SET proof=(SELECT encode(hrms_rls.hmac(convert_to(
       'audit-center:'||p.session_id||':'||p.actor||':'||pg_backend_pid()||':'||pg_current_xact_id(),'UTF8'),
       secret,'sha256'),'hex') FROM hrms_rls.signing_key WHERE singleton);
GRANT SELECT ON audit_probe TO hrms_isolation_app;
SET LOCAL ROLE hrms_isolation_app;
DO $test$
DECLARE ctx record; document jsonb;
BEGIN
  SELECT * INTO STRICT ctx FROM pg_temp.audit_probe;
  IF (SELECT count(*) FROM public.audit_log) <> 0 OR (SELECT count(*) FROM hrms_rls.finance_event) <> 0
     OR (SELECT count(*) FROM hrms_rls.finance_approval) <> 0
     OR has_table_privilege(current_user,'public.audit_log','UPDATE')
     OR has_table_privilege(current_user,'public.platform_audit_event','DELETE')
     OR has_table_privilege(current_user,'hrms_rls.finance_event','DELETE')
     OR pg_has_role(current_user,'hrms_isolation_audit_reader','MEMBER') THEN
    RAISE EXCEPTION 'Audit runtime access exceeds the restricted policy';
  END IF;
  BEGIN
    PERFORM hrms_rls.audit_center(ctx.session_id,ctx.actor,repeat('0',64),now()-interval '1 day',now(),
        NULL,'','',NULL,'',0);
    RAISE EXCEPTION 'Unsigned audit access was accepted';
  EXCEPTION WHEN insufficient_privilege THEN NULL;
  END;
  document := hrms_rls.audit_center(ctx.session_id,ctx.actor,ctx.proof,now()-interval '1 day',clock_timestamp(),
       NULL,'PLATFORM','DENIED',NULL,ctx.correlation::text,0);
  IF jsonb_array_length(document)<>1 OR document #>> '{0,operation}' <> 'Employee bank details'
       OR document #>> '{0,outcome}' <> 'DENIED' OR document::text LIKE '%PRIVATE-AUDIT-%' THEN
    RAISE EXCEPTION 'Audit projection returned unexpected fields';
  END IF;
  document := hrms_rls.audit_center(ctx.session_id,ctx.actor,ctx.proof,now()-interval '60 days',clock_timestamp(),
      ctx.alpha,'FINANCE','',NULL,'',0);
  IF EXISTS (SELECT 1 FROM jsonb_array_elements(document) e WHERE e->>'tenant_id' <> ctx.alpha::text)
       OR jsonb_array_length(document)=0 OR document::text LIKE '%fingerprint%' THEN
    RAISE EXCEPTION 'Audit company filter or financial projection failed';
  END IF;
END;
$test$;
ROLLBACK;
\echo Verified forged-proof denial, audit redaction, enforced RLS, append-only event access and scoped financial projection.
