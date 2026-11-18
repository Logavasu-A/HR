-- Run after V4 on a disposable database.
begin;

do $$
declare
    tenant constant uuid := '00000000-0000-0000-0000-000000000001';
    session_id constant uuid := '2d65ec8f-ed0c-4c77-8b9e-c868b4ec74cc';
    role_id bigint;
    user_id bigint;
begin
    insert into role (name) values ('Session test') returning id into role_id;
    insert into app_user (username, password_hash, role_id, active)
    values ('session-check', 'unused-test-value', role_id, true) returning id into user_id;

    insert into company_session (id, tenant_id, user_id, expires_at)
    select session_id, c.id, user_id, now() + interval '1 hour'
      from company c where c.id = tenant and c.status = 'ACTIVE' for share;
    if not found then
        raise exception 'Expected a session for the active company';
    end if;

    update company set status = 'SUSPENDED' where id = tenant;
    update company set status = 'ACTIVE' where id = tenant;
    if exists (select 1 from company_session where id = session_id) then
        raise exception 'Company session survived suspension and reactivation';
    end if;
end $$;

rollback;
