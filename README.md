# HRMS

A multi-tenant human resources management system with a company workspace and a separate administration platform. The Vue 3 frontend uses Vite. The backend uses Java 21, Spring Boot, Spring Security, Flyway, and PostgreSQL.

## What you can do

| Area | Features |
| --- | --- |
| Company workspace | Dashboard, employees, departments, designations, attendance, leave, payroll, recruitment, expenses, performance, notifications, and employee self-service |
| Platform administration | Control Tower, company registration and lifecycle management, administrator invitations, Access Center, and Audit Center |
| Admin View | Open a company workspace from Companies, provide an access reason, and switch companies from the workspace header |

Company users sign in with a company code, an email or username, and a password. Platform administrators use the separate platform sign-in. Admin View retains the platform administrator's identity and uses a company-scoped grant. The backend checks company context, permissions, and record scope. Sensitive operations such as payroll access can require password verification, approvals, and audit logging where configured.

## Local setup

Install Java 21, Maven, and Node.js 22 with npm. Use an existing local PostgreSQL 18 instance or install one. The Windows launcher at [backend/mvn.cmd](backend/mvn.cmd) uses your local Maven installation.

| Local service | Port | Database or API target |
| --- | --- | --- |
| PostgreSQL | 5432 | Local PostgreSQL instance |
| Default backend | 8080 | `hrms` by default |
| Backend with the `local-isolation` profile | 8081 | Provisioned local RLS restore, `hrms_isolation_20261119` |
| Vite frontend in the sample below | 5174 | Proxies API calls to port 8081 by default |

The default backend uses the Flyway migrations checked into the repository. The local isolation profile expects a PostgreSQL restore with the RLS policies, runtime roles, and platform objects provisioned separately. See the [tenant migration notes](backend/TENANT_MIGRATION.md) and [local isolation scripts](backend/ops) for setup details. Review the expected database target before running any SQL setup script.

Supply credentials and signing keys through environment variables or `backend/application-local.properties`. Spring loads that optional configuration file when started from `backend`. The default backend uses `DB_PASSWORD` and `JWT_SECRET`. Configure `APP_PLATFORM_JWT_SECRET` separately for platform sign-in. The isolation profile also requires `HRMS_ISOLATION_RUNTIME_PASSWORD` and `HRMS_ISOLATION_RLS_KEY`.

Start the isolation backend in one PowerShell terminal from the repository root.

```powershell
Set-Location backend
$env:SPRING_PROFILES_ACTIVE = 'local-isolation'
.\mvn.cmd spring-boot:run
```

Start the frontend in a second terminal from the repository root.

```powershell
Set-Location frontend
npm ci
npm run dev -- --host 127.0.0.1 --port 5174 --strictPort
```

Open [Company sign-in](http://127.0.0.1:5174/login) or [Platform sign-in](http://127.0.0.1:5174/platform/login). For the default backend, start Maven without the isolation profile and set `$env:HRMS_ISOLATION_FRONTEND = '0'` before starting Vite to proxy API requests to port 8080.

## Platform navigation

| Page | Frontend route |
| --- | --- |
| Control Tower | `/platform/control-tower` |
| Companies | `/platform/companies` |
| Company workspaces | `/platform/workspace` |
| Access Center | `/platform/access` |
| Audit Center | `/platform/audit` |

From **Companies**, select **Open workspace**, enter an access reason, and open **Admin View**. Use the workspace sidebar to open Employees, Attendance, and other available modules. Use the company picker to switch workspaces or return to the Control Tower.

## Project structure

| Path | Purpose |
| --- | --- |
| [frontend/src/modules](frontend/src/modules) | Vue feature pages, including company and platform sign-in |
| [frontend/src/router](frontend/src/router) | Company and platform route registration |
| [frontend/public](frontend/public) | HRMS branding and other public assets |
| [backend/src/main/java/com/hrms](backend/src/main/java/com/hrms) | Spring Boot controllers, services, authorization, and persistence |
| [backend/src/main/resources/db/migration](backend/src/main/resources/db/migration) | Flyway migrations |
| [backend/ops](backend/ops) | Local RLS setup, fixtures, and verification scripts |
| [frontend/test](frontend/test) and [backend/src/test](backend/src/test) | Frontend, browser, backend, and RLS regression tests |

## Build and verify

Run these from `frontend`.

```powershell
npm ci
npm run build
node --test test/workspace-client.test.mjs
```

Run these from `backend`.

```powershell
.\mvn.cmd test
.\mvn.cmd package
```

Use a disposable database for browser and database-backed verification. The [onboarding test](frontend/test/browser-onboarding.mjs) uses Playwright, `smtp-server`, and a local test backend. The [Admin View regression](frontend/test/browser-admin-view.mjs) uses installed Chrome and the configured test frontend. Their environment settings are documented at the top of each script. The [RLS suite](backend/src/test/java/com/hrms/shared/tenancy/TenantRowSecurityTest.java) is opt-in via `HRMS_RLS_TEST_DB` and expects the provisioned fixture data and isolation runtime settings.

## Local files

Use `backend/.local/` for private setup files or scratch work. The empty `.gitkeep` preserves the folder, and the ignore rules cover its contents, private environment files, logs, build output, and coverage or browser test reports.
