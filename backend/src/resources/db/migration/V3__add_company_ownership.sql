-- Expand the schema while the application still serves a single company.
-- Keep existing write paths working until the tenant-aware cutover.
-- Remove the tenant defaults before enabling company provisioning.
create table company (
    id uuid primary key default gen_random_uuid(),
    code varchar(40) not null unique,
    name varchar(160) not null,
    contact_email varchar(254),
    status varchar(16) not null default 'PENDING',
    created_at timestamptz not null default current_timestamp,
    constraint company_status_check check (status in ('PENDING', 'ACTIVE', 'SUSPENDED'))
);

insert into company (id, code, name, status)
values ('00000000-0000-0000-0000-000000000001', 'initial', 'Initial workspace', 'ACTIVE');

-- PostgreSQL backfills existing rows with the constant default.
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
        execute format(
            'alter table %I add column tenant_id uuid not null default %L references company(id)',
            tenant_table, '00000000-0000-0000-0000-000000000001'
        );
    end loop;
end $$;

-- Preserve primary keys and add keys for tenant-aware relationships.
alter table app_user add constraint app_user_tenant_id_id_key unique (tenant_id, id);
alter table appraisal_cycle add constraint appraisal_cycle_tenant_id_id_key unique (tenant_id, id);
alter table branch add constraint branch_tenant_id_id_key unique (tenant_id, id);
alter table department add constraint department_tenant_id_id_key unique (tenant_id, id);
alter table designation add constraint designation_tenant_id_id_key unique (tenant_id, id);
alter table employee add constraint employee_tenant_id_id_key unique (tenant_id, id);
alter table expense_claim add constraint expense_claim_tenant_id_id_key unique (tenant_id, id);
alter table expense_type add constraint expense_type_tenant_id_id_key unique (tenant_id, id);
alter table holiday_list add constraint holiday_list_tenant_id_id_key unique (tenant_id, id);
alter table job_applicant add constraint job_applicant_tenant_id_id_key unique (tenant_id, id);
alter table job_opening add constraint job_opening_tenant_id_id_key unique (tenant_id, id);
alter table leave_type add constraint leave_type_tenant_id_id_key unique (tenant_id, id);
alter table payroll_run add constraint payroll_run_tenant_id_id_key unique (tenant_id, id);
alter table role add constraint role_tenant_id_id_key unique (tenant_id, id);
alter table salary_component add constraint salary_component_tenant_id_id_key unique (tenant_id, id);
alter table salary_slip add constraint salary_slip_tenant_id_id_key unique (tenant_id, id);
alter table salary_structure add constraint salary_structure_tenant_id_id_key unique (tenant_id, id);
alter table shift_type add constraint shift_type_tenant_id_id_key unique (tenant_id, id);

-- Replace relationship constraints, preserving nullable links and cascades.
alter table role_permission
    drop constraint role_permission_role_id_fkey,
    add constraint role_permission_role_id_tenant_fkey
        foreign key (tenant_id, role_id) references role(tenant_id, id) on delete cascade;

alter table holiday
    drop constraint holiday_holiday_list_id_fkey,
    add constraint holiday_holiday_list_id_tenant_fkey
        foreign key (tenant_id, holiday_list_id) references holiday_list(tenant_id, id) on delete cascade;

alter table employee
    drop constraint employee_department_id_fkey,
    add constraint employee_department_id_tenant_fkey
        foreign key (tenant_id, department_id) references department(tenant_id, id),
    drop constraint employee_designation_id_fkey,
    add constraint employee_designation_id_tenant_fkey
        foreign key (tenant_id, designation_id) references designation(tenant_id, id),
    drop constraint employee_branch_id_fkey,
    add constraint employee_branch_id_tenant_fkey
        foreign key (tenant_id, branch_id) references branch(tenant_id, id),
    drop constraint employee_reports_to_id_fkey,
    add constraint employee_reports_to_id_tenant_fkey
        foreign key (tenant_id, reports_to_id) references employee(tenant_id, id),
    drop constraint employee_holiday_list_id_fkey,
    add constraint employee_holiday_list_id_tenant_fkey
        foreign key (tenant_id, holiday_list_id) references holiday_list(tenant_id, id);

alter table app_user
    drop constraint app_user_role_id_fkey,
    add constraint app_user_role_id_tenant_fkey
        foreign key (tenant_id, role_id) references role(tenant_id, id),
    drop constraint app_user_employee_id_fkey,
    add constraint app_user_employee_id_tenant_fkey
        foreign key (tenant_id, employee_id) references employee(tenant_id, id);

alter table notification
    drop constraint notification_user_id_fkey,
    add constraint notification_user_id_tenant_fkey
        foreign key (tenant_id, user_id) references app_user(tenant_id, id) on delete cascade;

alter table leave_allocation
    drop constraint leave_allocation_employee_id_fkey,
    add constraint leave_allocation_employee_id_tenant_fkey
        foreign key (tenant_id, employee_id) references employee(tenant_id, id),
    drop constraint leave_allocation_leave_type_id_fkey,
    add constraint leave_allocation_leave_type_id_tenant_fkey
        foreign key (tenant_id, leave_type_id) references leave_type(tenant_id, id);

alter table leave_application
    drop constraint leave_application_employee_id_fkey,
    add constraint leave_application_employee_id_tenant_fkey
        foreign key (tenant_id, employee_id) references employee(tenant_id, id),
    drop constraint leave_application_leave_type_id_fkey,
    add constraint leave_application_leave_type_id_tenant_fkey
        foreign key (tenant_id, leave_type_id) references leave_type(tenant_id, id);

alter table attendance
    drop constraint attendance_employee_id_fkey,
    add constraint attendance_employee_id_tenant_fkey
        foreign key (tenant_id, employee_id) references employee(tenant_id, id),
    drop constraint attendance_shift_id_fkey,
    add constraint attendance_shift_id_tenant_fkey
        foreign key (tenant_id, shift_id) references shift_type(tenant_id, id);

alter table salary_structure_line
    drop constraint salary_structure_line_structure_id_fkey,
    add constraint salary_structure_line_structure_id_tenant_fkey
        foreign key (tenant_id, structure_id) references salary_structure(tenant_id, id) on delete cascade,
    drop constraint salary_structure_line_component_id_fkey,
    add constraint salary_structure_line_component_id_tenant_fkey
        foreign key (tenant_id, component_id) references salary_component(tenant_id, id);

alter table salary_assignment
    drop constraint salary_assignment_employee_id_fkey,
    add constraint salary_assignment_employee_id_tenant_fkey
        foreign key (tenant_id, employee_id) references employee(tenant_id, id),
    drop constraint salary_assignment_structure_id_fkey,
    add constraint salary_assignment_structure_id_tenant_fkey
        foreign key (tenant_id, structure_id) references salary_structure(tenant_id, id);

alter table salary_slip
    drop constraint salary_slip_run_id_fkey,
    add constraint salary_slip_run_id_tenant_fkey
        foreign key (tenant_id, run_id) references payroll_run(tenant_id, id) on delete cascade,
    drop constraint salary_slip_employee_id_fkey,
    add constraint salary_slip_employee_id_tenant_fkey
        foreign key (tenant_id, employee_id) references employee(tenant_id, id);

alter table salary_slip_line
    drop constraint salary_slip_line_slip_id_fkey,
    add constraint salary_slip_line_slip_id_tenant_fkey
        foreign key (tenant_id, slip_id) references salary_slip(tenant_id, id) on delete cascade;

alter table job_opening
    drop constraint job_opening_department_id_fkey,
    add constraint job_opening_department_id_tenant_fkey
        foreign key (tenant_id, department_id) references department(tenant_id, id),
    drop constraint job_opening_designation_id_fkey,
    add constraint job_opening_designation_id_tenant_fkey
        foreign key (tenant_id, designation_id) references designation(tenant_id, id);

alter table job_applicant
    drop constraint job_applicant_job_opening_id_fkey,
    add constraint job_applicant_job_opening_id_tenant_fkey
        foreign key (tenant_id, job_opening_id) references job_opening(tenant_id, id);

alter table interview
    drop constraint interview_applicant_id_fkey,
    add constraint interview_applicant_id_tenant_fkey
        foreign key (tenant_id, applicant_id) references job_applicant(tenant_id, id) on delete cascade,
    drop constraint interview_interviewer_id_fkey,
    add constraint interview_interviewer_id_tenant_fkey
        foreign key (tenant_id, interviewer_id) references employee(tenant_id, id);

alter table expense_claim
    drop constraint expense_claim_employee_id_fkey,
    add constraint expense_claim_employee_id_tenant_fkey
        foreign key (tenant_id, employee_id) references employee(tenant_id, id);

alter table expense_claim_line
    drop constraint expense_claim_line_claim_id_fkey,
    add constraint expense_claim_line_claim_id_tenant_fkey
        foreign key (tenant_id, claim_id) references expense_claim(tenant_id, id) on delete cascade,
    drop constraint expense_claim_line_expense_type_id_fkey,
    add constraint expense_claim_line_expense_type_id_tenant_fkey
        foreign key (tenant_id, expense_type_id) references expense_type(tenant_id, id);

alter table employee_advance
    drop constraint employee_advance_employee_id_fkey,
    add constraint employee_advance_employee_id_tenant_fkey
        foreign key (tenant_id, employee_id) references employee(tenant_id, id);

alter table goal
    drop constraint goal_employee_id_fkey,
    add constraint goal_employee_id_tenant_fkey
        foreign key (tenant_id, employee_id) references employee(tenant_id, id),
    drop constraint goal_cycle_id_fkey,
    add constraint goal_cycle_id_tenant_fkey
        foreign key (tenant_id, cycle_id) references appraisal_cycle(tenant_id, id);

alter table appraisal
    drop constraint appraisal_employee_id_fkey,
    add constraint appraisal_employee_id_tenant_fkey
        foreign key (tenant_id, employee_id) references employee(tenant_id, id),
    drop constraint appraisal_cycle_id_fkey,
    add constraint appraisal_cycle_id_tenant_fkey
        foreign key (tenant_id, cycle_id) references appraisal_cycle(tenant_id, id);
