create table company_session (
    id uuid primary key,
    tenant_id uuid not null,
    user_id bigint not null,
    expires_at timestamptz not null,
    foreign key (tenant_id, user_id) references app_user(tenant_id, id) on delete cascade
);

create index company_session_user_idx on company_session(tenant_id, user_id);
create index company_session_expires_idx on company_session(expires_at);

create function revoke_company_sessions() returns trigger language plpgsql as $$
begin
    delete from company_session where tenant_id = new.id;
    return new;
end;
$$;

create trigger company_status_revoke_sessions
after update of status on company
for each row when (old.status is distinct from new.status)
execute function revoke_company_sessions();
