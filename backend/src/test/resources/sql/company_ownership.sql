-- Run in an isolated database after V3. Fixtures are rolled back.
begin;

do $$
declare
    initial_tenant constant uuid := '00000000-0000-0000-0000-000000000001';
    second_tenant uuid;
    owned_department bigint;
    existing_department bigint;
    rejected_constraint text;
begin
    select id into strict existing_department from department where name = 'Legacy HR'
        and tenant_id = initial_tenant;

    insert into company (code, name) values ('migration-test', 'Migration test') returning id into second_tenant;
    insert into department (name, tenant_id) values ('Migration test', second_tenant) returning id into owned_department;

    insert into employee (tenant_id, employee_code, first_name, date_of_joining, status, department_id)
    values (second_tenant, 'MIGRATION-TEST', 'Migration test', date '2025-01-01', 'ACTIVE', owned_department);

    begin
        update employee set department_id = existing_department where employee_code = 'MIGRATION-TEST';
        raise exception 'Expected cross-company employee reference to be rejected';
    exception when foreign_key_violation then
        get stacked diagnostics rejected_constraint = constraint_name;
        if rejected_constraint <> 'employee_department_id_tenant_fkey' then
            raise;
        end if;
    end;
end $$;

rollback;
