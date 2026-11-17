-- HRMS initial schema

-- ---------- admin ----------
create table role (
    id bigserial primary key,
    name varchar(60) not null unique,
    description varchar(255),
    created_at timestamp, updated_at timestamp, created_by varchar(100), updated_by varchar(100)
);

create table role_permission (
    role_id bigint not null references role(id) on delete cascade,
    permission varchar(100) not null,
    primary key (role_id, permission)
);

-- ---------- core ----------
create table branch (
    id bigserial primary key,
    name varchar(120) not null unique,
    description varchar(255),
    created_at timestamp, updated_at timestamp, created_by varchar(100), updated_by varchar(100)
);

create table department (
    id bigserial primary key,
    name varchar(120) not null unique,
    description varchar(255),
    created_at timestamp, updated_at timestamp, created_by varchar(100), updated_by varchar(100)
);

create table designation (
    id bigserial primary key,
    name varchar(120) not null unique,
    description varchar(255),
    created_at timestamp, updated_at timestamp, created_by varchar(100), updated_by varchar(100)
);

create table holiday_list (
    id bigserial primary key,
    name varchar(120) not null,
    from_date date not null,
    to_date date not null,
    weekly_offs varchar(100),
    default_list boolean not null default false,
    created_at timestamp, updated_at timestamp, created_by varchar(100), updated_by varchar(100)
);

create table holiday (
    id bigserial primary key,
    holiday_list_id bigint references holiday_list(id) on delete cascade,
    holiday_date date not null,
    description varchar(255)
);

create table employee (
    id bigserial primary key,
    employee_code varchar(30) not null unique,
    first_name varchar(100) not null,
    last_name varchar(100),
    email varchar(160),
    phone varchar(40),
    gender varchar(30),
    date_of_birth date,
    date_of_joining date not null,
    relieving_date date,
    status varchar(30) not null,
    employment_type varchar(30),
    department_id bigint references department(id),
    designation_id bigint references designation(id),
    branch_id bigint references branch(id),
    reports_to_id bigint references employee(id),
    holiday_list_id bigint references holiday_list(id),
    address varchar(500),
    bank_name varchar(120),
    bank_account_no varchar(60),
    created_at timestamp, updated_at timestamp, created_by varchar(100), updated_by varchar(100)
);

create table app_user (
    id bigserial primary key,
    username varchar(80) not null unique,
    email varchar(160),
    password_hash varchar(120) not null,
    role_id bigint not null references role(id),
    employee_id bigint references employee(id),
    active boolean not null default true,
    created_at timestamp, updated_at timestamp, created_by varchar(100), updated_by varchar(100)
);

create table audit_log (
    id bigserial primary key,
    username varchar(80),
    action varchar(30),
    method varchar(10),
    path varchar(300),
    status integer,
    ip varchar(60),
    at timestamp not null
);
create index idx_audit_log_at on audit_log(at desc);

create table notification (
    id bigserial primary key,
    user_id bigint not null references app_user(id) on delete cascade,
    title varchar(200) not null,
    message text,
    is_read boolean not null default false,
    created_at timestamp not null
);
create index idx_notification_user on notification(user_id, is_read);

-- ---------- leave ----------
create table leave_type (
    id bigserial primary key,
    name varchar(120) not null unique,
    max_days_per_year numeric(6,1),
    carry_forward boolean not null default false,
    unpaid boolean not null default false,
    allow_negative boolean not null default false,
    created_at timestamp, updated_at timestamp, created_by varchar(100), updated_by varchar(100)
);

create table leave_allocation (
    id bigserial primary key,
    employee_id bigint not null references employee(id),
    leave_type_id bigint not null references leave_type(id),
    from_date date not null,
    to_date date not null,
    days numeric(6,1) not null,
    created_at timestamp, updated_at timestamp, created_by varchar(100), updated_by varchar(100)
);

create table leave_application (
    id bigserial primary key,
    employee_id bigint not null references employee(id),
    leave_type_id bigint not null references leave_type(id),
    from_date date not null,
    to_date date not null,
    half_day boolean not null default false,
    total_days numeric(6,1) not null,
    reason text,
    status varchar(30) not null,
    decided_by varchar(100),
    decided_at timestamp,
    remarks varchar(500),
    created_at timestamp, updated_at timestamp, created_by varchar(100), updated_by varchar(100)
);
create index idx_leave_app_employee on leave_application(employee_id);

-- ---------- attendance ----------
create table shift_type (
    id bigserial primary key,
    name varchar(120) not null unique,
    start_time time,
    end_time time,
    grace_minutes integer,
    created_at timestamp, updated_at timestamp, created_by varchar(100), updated_by varchar(100)
);

create table attendance (
    id bigserial primary key,
    employee_id bigint not null references employee(id),
    attendance_date date not null,
    status varchar(30) not null,
    in_time time,
    out_time time,
    working_hours numeric(5,2),
    shift_id bigint references shift_type(id),
    remarks varchar(500),
    created_at timestamp, updated_at timestamp, created_by varchar(100), updated_by varchar(100),
    unique (employee_id, attendance_date)
);
create index idx_attendance_date on attendance(attendance_date);

-- ---------- payroll ----------
create table salary_component (
    id bigserial primary key,
    name varchar(120) not null unique,
    abbr varchar(20),
    type varchar(30) not null,
    depends_on_payment_days boolean not null default true,
    created_at timestamp, updated_at timestamp, created_by varchar(100), updated_by varchar(100)
);

create table salary_structure (
    id bigserial primary key,
    name varchar(120) not null unique,
    active boolean not null default true,
    created_at timestamp, updated_at timestamp, created_by varchar(100), updated_by varchar(100)
);

create table salary_structure_line (
    id bigserial primary key,
    structure_id bigint references salary_structure(id) on delete cascade,
    component_id bigint not null references salary_component(id),
    amount_type varchar(30) not null,
    amount_value numeric(14,2) not null
);

create table salary_assignment (
    id bigserial primary key,
    employee_id bigint not null references employee(id),
    structure_id bigint not null references salary_structure(id),
    base_amount numeric(14,2) not null,
    from_date date not null,
    created_at timestamp, updated_at timestamp, created_by varchar(100), updated_by varchar(100)
);

create table payroll_run (
    id bigserial primary key,
    period_year integer not null,
    period_month integer not null,
    status varchar(30) not null,
    slip_count integer,
    total_net numeric(16,2),
    created_at timestamp, updated_at timestamp, created_by varchar(100), updated_by varchar(100),
    unique (period_year, period_month)
);

create table salary_slip (
    id bigserial primary key,
    run_id bigint not null references payroll_run(id) on delete cascade,
    employee_id bigint not null references employee(id),
    period_year integer not null,
    period_month integer not null,
    working_days numeric(6,1),
    payment_days numeric(6,1),
    lwp_days numeric(6,1),
    gross_pay numeric(14,2),
    total_deduction numeric(14,2),
    net_pay numeric(14,2),
    status varchar(30) not null,
    created_at timestamp, updated_at timestamp, created_by varchar(100), updated_by varchar(100)
);
create index idx_salary_slip_employee on salary_slip(employee_id);

create table salary_slip_line (
    id bigserial primary key,
    slip_id bigint references salary_slip(id) on delete cascade,
    component_name varchar(120) not null,
    type varchar(30) not null,
    amount numeric(14,2) not null
);

-- ---------- recruitment ----------
create table job_opening (
    id bigserial primary key,
    title varchar(200) not null,
    department_id bigint references department(id),
    designation_id bigint references designation(id),
    vacancies integer,
    description text,
    status varchar(30) not null,
    posted_on date,
    closes_on date,
    created_at timestamp, updated_at timestamp, created_by varchar(100), updated_by varchar(100)
);

create table job_applicant (
    id bigserial primary key,
    job_opening_id bigint references job_opening(id),
    name varchar(200) not null,
    email varchar(160),
    phone varchar(40),
    source varchar(100),
    resume_url varchar(500),
    status varchar(30) not null,
    rating integer,
    applied_on date,
    offered_ctc numeric(14,2),
    notes text,
    created_at timestamp, updated_at timestamp, created_by varchar(100), updated_by varchar(100)
);

create table interview (
    id bigserial primary key,
    applicant_id bigint not null references job_applicant(id) on delete cascade,
    scheduled_at timestamp,
    interviewer_id bigint references employee(id),
    mode varchar(30),
    status varchar(30) not null,
    feedback text,
    rating integer,
    created_at timestamp, updated_at timestamp, created_by varchar(100), updated_by varchar(100)
);

-- ---------- expenses ----------
create table expense_type (
    id bigserial primary key,
    name varchar(120) not null unique,
    description varchar(255),
    created_at timestamp, updated_at timestamp, created_by varchar(100), updated_by varchar(100)
);

create table expense_claim (
    id bigserial primary key,
    employee_id bigint not null references employee(id),
    claim_date date not null,
    status varchar(30) not null,
    total_amount numeric(14,2),
    remarks varchar(500),
    decided_by varchar(100),
    decided_at timestamp,
    created_at timestamp, updated_at timestamp, created_by varchar(100), updated_by varchar(100)
);

create table expense_claim_line (
    id bigserial primary key,
    claim_id bigint references expense_claim(id) on delete cascade,
    expense_type_id bigint references expense_type(id),
    expense_date date,
    description varchar(500),
    amount numeric(14,2) not null
);

create table employee_advance (
    id bigserial primary key,
    employee_id bigint not null references employee(id),
    advance_date date not null,
    amount numeric(14,2) not null,
    purpose varchar(500),
    status varchar(30) not null,
    created_at timestamp, updated_at timestamp, created_by varchar(100), updated_by varchar(100)
);

-- ---------- performance ----------
create table appraisal_cycle (
    id bigserial primary key,
    name varchar(120) not null,
    start_date date,
    end_date date,
    status varchar(30) not null,
    created_at timestamp, updated_at timestamp, created_by varchar(100), updated_by varchar(100)
);

create table goal (
    id bigserial primary key,
    employee_id bigint not null references employee(id),
    cycle_id bigint references appraisal_cycle(id),
    title varchar(200) not null,
    description text,
    weightage numeric(5,2),
    progress integer,
    status varchar(30) not null,
    created_at timestamp, updated_at timestamp, created_by varchar(100), updated_by varchar(100)
);

create table appraisal (
    id bigserial primary key,
    employee_id bigint not null references employee(id),
    cycle_id bigint not null references appraisal_cycle(id),
    self_rating numeric(3,1),
    manager_rating numeric(3,1),
    final_score numeric(3,1),
    feedback text,
    status varchar(30) not null,
    created_at timestamp, updated_at timestamp, created_by varchar(100), updated_by varchar(100)
);
