# HRMS

An HR management system: Java 21 + Spring Boot backend, PostgreSQL 18 database, Vue 3 frontend.

## Modules

| Module | What it does |
|---|---|
| Core HR | Employees, departments, designations, branches |
| Leave | Leave types, allocations, applications with approval, holiday lists, balances |
| Attendance | Daily records, check-in / check-out, shift types, monthly summary |
| Payroll | Salary components, structures, assignments, monthly payroll runs, payslips (printable) |
| Recruitment | Job openings, applicants, interviews, one-click hire into an employee record |
| Expenses | Expense claims with approval and payment, employee advances |
| Performance | Appraisal cycles, goals, appraisals with a calculated final score |
| Self-service | Each employee's own leaves, attendance, payslips, expenses, goals, appraisals |
| Dashboard | Headcount, attrition, leave trend, attendance today, payroll cost charts |
| Administration | Users, roles with per-module permissions, audit log, notifications (in-app + email) |

## What you need installed

- JDK 21 or newer (https://adoptium.net)
- Maven 3.9 or newer (https://maven.apache.org/download.cgi)
- Node.js 20 or newer
- PostgreSQL 18 running on `localhost:5433` with a database named `hrms`

## Run it

Backend (creates all tables on first start):

```bash
cd backend
mvn spring-boot:run
```

Frontend, in a second terminal:

```bash
cd frontend
npm install
npm run dev
```

Then open http://localhost:5173.

## First login

- Username: `admin`
- Password: the value of `ADMIN_INITIAL_PASSWORD` in `backend/application-local.properties`

Change it after signing in (click your name at the top right).

## Configuration

`backend/application-local.properties` holds the secrets and is not committed to git:

| Key | Meaning |
|---|---|
| `DB_PASSWORD` | Password of the `postgres` database user |
| `JWT_SECRET` | Signing key for login tokens, 32+ characters |
| `ADMIN_INITIAL_PASSWORD` | Password of the first `admin` login; only used while the user table is empty |

To send emails as well as in-app notifications, uncomment the `app.mail.*` and `spring.mail.*`
lines in that file and fill in your SMTP account.

The database address and port are in `backend/src/main/resources/application.yml`.

## Roles and permissions

Every API path is `/api/{module}/...` and needs the permission `{module}:{action}`:

- `read`: view the module's screens
- `write`: add, change and delete records
- `approve`: approve, reject, pay, submit

`{module}:*` grants all three and `*` grants everything. Four roles are created on first start
(ADMIN, HR_MANAGER, MANAGER, EMPLOYEE); edit them or add more under Administration → Roles.
The self-service screens need no permission, only a user that is linked to an employee.

## Where things are

```
backend/src/main/java/com/myself/hrms/
  common/       shared base classes (BaseEntity, CrudController, errors)
  security/     login tokens and the permission check
  admin/        users, roles, audit log, notifications, starter data
  core/ leave/ attendance/ payroll/ recruitment/ expense/ performance/
  me/           self-service endpoints
  dashboard/    analytics
backend/src/main/resources/db/migration/   database schema (Flyway)

frontend/src/
  resources.js  every list-and-form screen is declared here
  components/   CrudPage (generic list + form), Layout, payslip view
  pages/        login, dashboard, payroll runs and the other custom screens
```
