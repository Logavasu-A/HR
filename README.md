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

## Login

All users and roles are stored in the database (tables `app_user` and `role`); the application does not create any data itself. Sign in with a user that already exists there, and change the password from the top right after signing in.

## Configuration

`backend/application-local.properties` holds the secrets and is not committed to git:

| Key | Meaning |
|---|---|
| `DB_PASSWORD` | Password of the `postgres` database user |
| `JWT_SECRET` | Signing key for login tokens, 32+ characters |

To send emails as well as in-app notifications, uncomment the `app.mail.*` and `spring.mail.*`
lines in that file and fill in your SMTP account.

The database address and port are in `backend/src/resources/application.yml`.

## Roles and permissions

Every API path is `/api/{module}/...` and needs the permission `{module}:{action}`:

- `read`: view the module's screens
- `write`: add, change and delete records
- `approve`: approve, reject, pay, submit

`{module}:*` grants all three and `*` grants everything. Roles are stored in the database; edit them or add more under Administration → Roles.
The self-service screens need no permission, only a user that is linked to an employee.

## Where things are

```
backend/
  pom.xml
  src/
    app/         HrmsApplication (start-up class)
    modules/     one folder per feature (admin, attendance, audit, auth, dashboard, employee, expense,
                 leave, notification, organization, payroll, performance, recruitment, selfservice),
                 each with controller / model / repository / service
    shared/      common (base classes, errors), security (tokens, permissions), config
    resources/   application.yml and db/migration (database schema, Flyway)
  test/          tests

frontend/src/
  modules/       one folder per feature, with pages / models / services / routes as needed
                 (models = the list-and-form screen definitions)
  shared/        CrudPage (generic list + form), Layout, payslip view, api, auth, utils
  router/        routes, built from the modules
```
