-- Performance indexes for foreign keys, filters, and sort orders across all modules

-- ---------- admin & audit ----------
create index if not exists idx_app_user_role on app_user(role_id);
create index if not exists idx_app_user_employee on app_user(employee_id);
create index if not exists idx_app_user_active on app_user(active);
create index if not exists idx_app_user_username_lower on app_user(lower(username));
create index if not exists idx_audit_log_username on audit_log(username);
create index if not exists idx_audit_log_action on audit_log(action);

-- ---------- notification ----------
create index if not exists idx_notification_user_created on notification(user_id, created_at desc);

-- ---------- core hr & organization ----------
create index if not exists idx_holiday_list_default on holiday_list(default_list);
create index if not exists idx_holiday_list_date on holiday(holiday_list_id, holiday_date);
create index if not exists idx_employee_status on employee(status);
create index if not exists idx_employee_department on employee(department_id);
create index if not exists idx_employee_designation on employee(designation_id);
create index if not exists idx_employee_branch on employee(branch_id);
create index if not exists idx_employee_reports_to on employee(reports_to_id);
create index if not exists idx_employee_holiday_list on employee(holiday_list_id);
create index if not exists idx_employee_joining_date on employee(date_of_joining);
create index if not exists idx_employee_relieving_date on employee(relieving_date);

-- ---------- leave ----------
create index if not exists idx_leave_alloc_employee_dates on leave_allocation(employee_id, from_date, to_date);
create index if not exists idx_leave_alloc_type on leave_allocation(leave_type_id);
create index if not exists idx_leave_app_emp_from on leave_application(employee_id, from_date desc);
create index if not exists idx_leave_app_emp_status on leave_application(employee_id, status);
create index if not exists idx_leave_app_status_dates on leave_application(status, from_date, to_date);
create index if not exists idx_leave_app_type on leave_application(leave_type_id);

-- ---------- attendance ----------
create index if not exists idx_attendance_emp_date_desc on attendance(employee_id, attendance_date desc);
create index if not exists idx_attendance_date_desc on attendance(attendance_date desc);
create index if not exists idx_attendance_shift on attendance(shift_id);
create index if not exists idx_attendance_status on attendance(status);

-- ---------- payroll ----------
create index if not exists idx_salary_struct_line_struct on salary_structure_line(structure_id);
create index if not exists idx_salary_struct_line_comp on salary_structure_line(component_id);
create index if not exists idx_salary_assign_emp_from on salary_assignment(employee_id, from_date desc);
create index if not exists idx_salary_assign_struct on salary_assignment(structure_id);
create index if not exists idx_payroll_run_period on payroll_run(period_year desc, period_month desc);
create index if not exists idx_salary_slip_run on salary_slip(run_id);
create index if not exists idx_salary_slip_emp_status_period on salary_slip(employee_id, status, period_year desc, period_month desc);
create index if not exists idx_salary_slip_line_slip on salary_slip_line(slip_id);

-- ---------- recruitment ----------
create index if not exists idx_job_opening_status on job_opening(status);
create index if not exists idx_job_opening_dept on job_opening(department_id);
create index if not exists idx_job_opening_desig on job_opening(designation_id);
create index if not exists idx_job_applicant_opening on job_applicant(job_opening_id);
create index if not exists idx_job_applicant_status on job_applicant(status);
create index if not exists idx_interview_applicant on interview(applicant_id);
create index if not exists idx_interview_interviewer on interview(interviewer_id);
create index if not exists idx_interview_scheduled on interview(scheduled_at);

-- ---------- expense ----------
create index if not exists idx_expense_claim_emp_date on expense_claim(employee_id, claim_date desc);
create index if not exists idx_expense_claim_status on expense_claim(status);
create index if not exists idx_expense_claim_line_claim on expense_claim_line(claim_id);
create index if not exists idx_expense_claim_line_type on expense_claim_line(expense_type_id);
create index if not exists idx_employee_advance_emp on employee_advance(employee_id);
create index if not exists idx_employee_advance_status on employee_advance(status);

-- ---------- performance ----------
create index if not exists idx_appraisal_cycle_status on appraisal_cycle(status);
create index if not exists idx_goal_emp_id on goal(employee_id, id desc);
create index if not exists idx_goal_cycle on goal(cycle_id);
create index if not exists idx_goal_status on goal(status);
create index if not exists idx_appraisal_emp_id on appraisal(employee_id, id desc);
create index if not exists idx_appraisal_cycle on appraisal(cycle_id);
create index if not exists idx_appraisal_status on appraisal(status);
