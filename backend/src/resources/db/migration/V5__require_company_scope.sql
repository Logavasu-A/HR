-- Hibernate assigns ownership to entity inserts. This join table is managed
-- by Hibernate as a collection, so derive its ownership from the role.
create function assign_role_permission_company() returns trigger language plpgsql as $$
begin
    if new.tenant_id is null then
        select tenant_id into new.tenant_id from role where id = new.role_id;
    end if;
    return new;
end;
$$;

create trigger role_permission_assign_company
before insert on role_permission
for each row execute function assign_role_permission_company();

do $$
declare
    tenant_table text;
begin
    foreach tenant_table in array array[
        'role', 'role_permission', 'branch', 'department', 'designation',
        'holiday_list', 'holiday', 'employee', 'app_user', 'audit_log',
        'notification', 'leave_type', 'leave_allocation', 'leave_application', 'shift_type',
        'attendance', 'salary_component', 'salary_structure', 'salary_structure_line', 'salary_assignment',
        'payroll_run', 'salary_slip', 'salary_slip_line', 'job_opening', 'job_applicant',
        'interview', 'expense_type', 'expense_claim', 'expense_claim_line', 'employee_advance',
        'appraisal_cycle', 'goal', 'appraisal'
    ] loop
        execute format('alter table %I alter column tenant_id drop default', tenant_table);
    end loop;
end $$;

alter table role drop constraint role_name_key, add unique (tenant_id, name);
alter table branch drop constraint branch_name_key, add unique (tenant_id, name);
alter table department drop constraint department_name_key, add unique (tenant_id, name);
alter table designation drop constraint designation_name_key, add unique (tenant_id, name);
alter table employee drop constraint employee_employee_code_key, add unique (tenant_id, employee_code);
alter table app_user drop constraint app_user_username_key, add unique (tenant_id, username);
alter table leave_type drop constraint leave_type_name_key, add unique (tenant_id, name);
alter table shift_type drop constraint shift_type_name_key, add unique (tenant_id, name);
alter table salary_component drop constraint salary_component_name_key, add unique (tenant_id, name);
alter table salary_structure drop constraint salary_structure_name_key, add unique (tenant_id, name);
alter table expense_type drop constraint expense_type_name_key, add unique (tenant_id, name);
alter table payroll_run drop constraint payroll_run_period_year_period_month_key,
    add unique (tenant_id, period_year, period_month);
