create table platform_users (
    id uuid primary key default gen_random_uuid(),
    username varchar(80) not null unique check (username = lower(username)),
    password_hash varchar(60) not null check (password_hash like '$2%'),
    active boolean not null default true,
    failures integer not null default 0,
    locked_until timestamptz,
    created_at timestamptz not null default current_timestamp
);

create table platform_sessions (
    id uuid primary key,
    user_id uuid not null references platform_users(id) on delete cascade,
    expires_at timestamptz not null
);
