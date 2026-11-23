create unique index company_code_casefold_idx on company (lower(code));

alter table company
    add constraint company_reserved_id_check check (id <> '00000000-0000-0000-0000-000000000000'),
    add column timezone varchar(80) not null default 'UTC',
    add column currency varchar(3) not null default 'USD';

create table company_admin_invitation (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references company(id),
    email varchar(80) not null,
    secret_hash char(64) not null unique,
    created_at timestamptz not null default current_timestamp,
    expires_at timestamptz not null,
    accepted_at timestamptz,
    revoked_at timestamptz,
    delivery_status varchar(12) not null default 'PENDING',
    constraint company_admin_invitation_delivery_check check (delivery_status in ('PENDING', 'SENT', 'FAILED'))
);

create index company_admin_invitation_company_idx on company_admin_invitation(tenant_id, created_at desc);
create unique index company_admin_invitation_outstanding_idx on company_admin_invitation(tenant_id)
    where accepted_at is null and revoked_at is null;

create table platform_audit_event (
    id bigint generated always as identity primary key,
    actor varchar(255) not null,
    tenant_id uuid references company(id),
    action varchar(64) not null,
    occurred_at timestamptz not null default current_timestamp
);
