# Company workspace migration

## Schema expansion

`V3__add_company_ownership.sql` adds the initial company and assigns existing rows to it. It covers the 33 company-owned tables in V1, including role permissions, audit logs, notifications, and payroll and expense line items. Existing primary keys, global uniqueness rules, nullable relationships, and delete cascades are retained. Composite foreign keys prevent a row from linking to a parent in another company.

V5 removes the initial tenant defaults and replaces global business-key constraints with company-scoped constraints. Hibernate sets ownership and automatically scopes entity queries and direct ID loads. PostgreSQL enforces tenant ownership on relationships, including roles and permission rows. Company credentials and platform owner credentials use separate authorization boundaries.

## Company sign-in and sessions

`V4__company_sessions.sql` adds server-side sessions scoped to a company and user. The sign-in form defaults to company code `initial` and accepts the existing username or an unambiguous account email. Older username-only JWTs expire at cutover and require users to sign in again. Logout revokes the current session when the API is reachable. A database trigger revokes every session for a company when its status changes, so reactivation cannot restore old sessions.

New workspaces are disabled by default. V6 adds company details, hashed invitations and platform audit events. The company stays pending until its first admin accepts. A failed email leaves the invitation retryable. A resend revokes the previous link.

## Configure the owner console

V7 adds platform-only users and sessions. At startup, HRMS creates the owner account `logavasu` when it can read the configured bootstrap password file. Supply a fresh password as one UTF-8 line of at least 12 characters and at most 72 bytes. Keep the file outside the repository with access limited to the backend process. After the first successful startup, remove the bootstrap setting and unmount the file. Bootstrap preserves any existing owner's password.

Set these properties in your deployment configuration. Generate and store an independent random platform JWT key of at least 32 bytes, Base64-encoded.

```properties
app.platform.enabled=true
app.platform.jwt-secret=${HRMS_PLATFORM_JWT_SECRET}
app.platform.bootstrap-password-file=${HRMS_PLATFORM_PASSWORD_FILE:}
app.platform.public-origin=https://your-hrms-domain
app.cors.allowed-origins=https://your-hrms-domain
app.tenancy.allow-additional-companies=true
spring.mail.host=YOUR_SMTP_HOST
spring.mail.username=YOUR_SMTP_USERNAME
spring.mail.password=${HRMS_SMTP_PASSWORD}
app.mail.from=YOUR_INVITATION_SENDER
```

Serve the Vue application and API under the same HTTPS origin in production. Set `app.cors.allowed-origins` to the actual browser origin, including its scheme and any non-default port. The loopback defaults are for development. Open `/platform/login` and sign in with `logavasu` and the configured password. Five incorrect attempts lock the account for five minutes. The platform session expires after 20 minutes. Sensitive company operations require sign-in in the preceding five minutes. Configure staging SMTP and verify delivery to an approved test inbox before onboarding customers.

## Database verification

For the SQL-only upgrade rehearsal, apply V1 and V2 to a disposable PostgreSQL database, insert a department named `Legacy HR`, and apply V3 and V4. Run `src/test/resources/sql/company_ownership.sql` and `src/test/resources/sql/company_sessions.sql` with psql and `ON_ERROR_STOP=1`. Their fixtures roll back. Then apply V5 through V7 and verify that existing records retain their identifiers, ownership, and relationships.

For the application integration tests, point `HRMS_TEST_DATABASE_URL` at an empty local database named `hrms_test` and run `mvn test`. Provide `HRMS_TEST_DATABASE_USERNAME` and `HRMS_TEST_DATABASE_PASSWORD` when needed. The suite provisions two companies, exercises workflows through HTTP controllers, and upgrades a separate schema from V2 to V7 with historical records. Mail is simulated in these Maven tests.

For an actual browser and SMTP transport check, build the frontend and run `node frontend/test/browser-onboarding.mjs` with Playwright and Nodemailer's `smtp-server` available to Node. Use a disposable backend with platform owner login enabled, a fixture owner password, and a disposable database. Pass the loopback backend URL, fixture password, and unused loopback SMTP and frontend ports through `HRMS_BROWSER_BACKEND`, `HRMS_BROWSER_OWNER_PASSWORD`, `HRMS_BROWSER_SMTP_PORT`, and `HRMS_BROWSER_FRONTEND_PORT`. Point the fixture backend's mail sender at the loopback SMTP port, use `hrms@example.invalid` as the sender, set the public invitation origin to `https://hrms-test.example.invalid`, and allow the local test frontend origin. The SMTP listener only accepts `first-admin@example.invalid` and captures messages in memory.

Before a production upgrade, take a verified backup and rehearse a restore in isolation. Record the schema version and representative row counts and compare existing records and relationships after migration. Use the pre-upgrade backup and the matching application version for a rollback rehearsal. Plan how to preserve any writes made after backup before choosing restoration during a live incident.

## Follow-up features

- Onboarding checklist and employee import with preview and validation.
- Company admin handover with last-admin protection.
- Configurable approvers and delegation.
- Per-company entitlements and usage limits.
- Approved, time-limited and audited support access.
