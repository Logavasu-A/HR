-- Synthetic draft in the role verification company.
\set ON_ERROR_STOP on
SELECT current_database() IN ('hrms_isolation_20261119','hrms_sensitive_rehearsal_20261121') AS approved
\gset
\if :approved
\else
\quit 3
\endif
WITH inserted AS (
    INSERT INTO public.payroll_run (tenant_id,period_year,period_month,status,slip_count,total_net)
    SELECT id,2098,12,'DRAFT',1,4500 FROM public.company
    WHERE code='isolation-access'
      AND NOT EXISTS (SELECT 1 FROM public.payroll_run WHERE (tenant_id,period_year,period_month)=(company.id,2098,12))
    RETURNING id,tenant_id
)
INSERT INTO public.salary_slip (tenant_id,run_id,employee_id,period_year,period_month,status,gross_pay,total_deduction,net_pay)
SELECT r.tenant_id,r.id,e.id,2098,12,'DRAFT',5000,500,4500
FROM inserted r JOIN public.employee e ON (e.tenant_id,e.employee_code)=(r.tenant_id,'VERIFY-ACCESS-PEER');
