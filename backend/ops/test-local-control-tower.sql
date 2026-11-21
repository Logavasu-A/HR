-- Reconcile the aggregate contract with independent queries as the local DBA.
\set ON_ERROR_STOP on
BEGIN;
DO $check$
DECLARE
    first_day date := date_trunc('year',current_date)::date;
    last_day date := (date_trunc('year',current_date) + interval '1 year')::date;
    report jsonb;
    item jsonb;
    company_key uuid;
    costs jsonb;
    companies_checked integer := 0;
BEGIN
    IF current_database() NOT IN ('hrms_isolation_20261119','hrms_tower_rehearsal_20261121') THEN
        RAISE EXCEPTION 'This verification requires the local restore';
    END IF;
    -- Exercise distinct currencies and roll back the temporary rehearsal change.
    IF current_database() = 'hrms_tower_rehearsal_20261121' THEN
        UPDATE public.company SET currency='EUR' WHERE code='isolation-bravo';
    END IF;
    report := hrms_rls.control_tower(first_day,12,NULL);
    IF jsonb_array_length(report->'companies') <> (SELECT count(*) FROM public.company)
       OR jsonb_array_length(report->'months') <> 12 THEN
        RAISE EXCEPTION 'Company or reporting period coverage differs';
    END IF;

    FOR item IN SELECT value FROM jsonb_array_elements(report->'companies') LOOP
        company_key := (item->>'id')::uuid;
        IF (item->>'employees')::bigint <> (SELECT count(*) FROM public.employee WHERE tenant_id=company_key)
           OR (item->>'activeEmployees')::bigint <> (SELECT count(*) FROM public.employee WHERE tenant_id=company_key AND status='ACTIVE')
           OR (item->>'employees')::bigint <> (SELECT coalesce(sum((value->>'employees')::bigint),0) FROM jsonb_array_elements(item->'departments'))
           OR coalesce((item->'counts'->>'attendanceRecords')::bigint,0) <>
              (SELECT count(*) FROM public.attendance WHERE tenant_id=company_key AND attendance_date>=first_day AND attendance_date<last_day)
           OR coalesce((item->'counts'->>'leaveDays')::numeric,0) <>
              (SELECT coalesce(sum(total_days),0) FROM public.leave_application WHERE tenant_id=company_key AND status='APPROVED'
               AND from_date>=first_day AND from_date<last_day)
           OR coalesce((item->'counts'->>'applicants')::bigint,0) <>
              (SELECT count(*) FROM public.job_applicant WHERE tenant_id=company_key AND applied_on>=first_day AND applied_on<last_day)
           OR (item->>'unread')::bigint <> (SELECT count(*) FROM public.notification WHERE tenant_id=company_key AND NOT is_read)
           OR (item->>'netPayroll')::numeric IS DISTINCT FROM
              (SELECT sum(total_net) FROM public.payroll_run WHERE tenant_id=company_key AND status='SUBMITTED'
                AND period_month BETWEEN 1 AND 12 AND period_year=extract(year FROM first_day)) THEN
            RAISE EXCEPTION 'Report does not reconcile for company %', company_key;
        END IF;
        IF hrms_rls.control_tower(first_day,12,company_key)->'companies' IS DISTINCT FROM jsonb_build_array(item) THEN
            RAISE EXCEPTION 'Company drill-down differs from the global rollup';
        END IF;
        companies_checked := companies_checked + 1;
    END LOOP;

    SELECT coalesce(jsonb_object_agg(currency, net), '{}') INTO costs FROM (
        SELECT c.currency, sum(p.total_net) AS net
        FROM public.company c JOIN public.payroll_run p ON p.tenant_id=c.id WHERE p.status='SUBMITTED'
          AND p.period_month BETWEEN 1 AND 12 AND p.period_year=extract(year FROM first_day)
        GROUP BY c.currency
    ) per_currency;
    IF costs <> (SELECT coalesce(jsonb_object_agg(value->>'currency',(value->>'net')::numeric),'{}')
                  FROM jsonb_array_elements(report->'payroll')) THEN
        RAISE EXCEPTION 'Mixed-currency payroll reconciliation failed';
    END IF;
    IF current_database() = 'hrms_tower_rehearsal_20261121'
       AND NOT (costs ? 'USD' AND costs ? 'EUR') THEN
        RAISE EXCEPTION 'The mixed-currency rehearsal did not exercise USD and EUR';
    END IF;
    IF jsonb_array_length(hrms_rls.control_tower(first_day,2,NULL)->'companies') <> 0
       OR jsonb_array_length(hrms_rls.control_tower(first_day + 1,1,NULL)->'months') <> 0 THEN
        RAISE EXCEPTION 'The aggregate accepts unsupported date ranges';
    END IF;
    IF has_column_privilege('hrms_isolation_metrics','public.employee','bank_account_no','SELECT')
       OR has_column_privilege('hrms_isolation_metrics','public.employee','first_name','SELECT')
       OR has_column_privilege('hrms_isolation_metrics','public.notification','message','SELECT')
       OR has_table_privilege('hrms_isolation_metrics','public.employee','INSERT,UPDATE,DELETE')
       OR pg_has_role('hrms_isolation_app','hrms_isolation_metrics','MEMBER') THEN
        RAISE EXCEPTION 'The aggregate role has unexpected privileges';
    END IF;
    RAISE NOTICE 'Reconciled % companies, all drill-downs, currency totals and the reporting role', companies_checked;
END;
$check$;
ROLLBACK;
