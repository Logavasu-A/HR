# HRMS isolated staging deployment

This runbook prepares an isolated staging release. Deployment requires explicit approval of both the archive SHA-256 and the staging environment. The scope excludes production infrastructure and data. Record commands, timestamps, outcomes, and redacted evidence in the release audit.

## Approval and environment checklist

- [ ] Verify the source ZIP hash against the release manifest. Verify every extracted file against its SHA-256, including V1–V7. Approve that exact archive digest and the staging environment before deployment.
- [ ] Provision a private staging PostgreSQL instance or database with network access restricted to staging workloads. Use an empty database or an approved sanitized fixture. Create a dedicated database login with a fresh password and only the required privileges. Confirm its schema ownership or migration privileges, preferably separating migration and runtime roles where tooling permits. Record the tested PostgreSQL and Flyway versions and resolve version warnings before sign-off.
- [ ] Use the Spring `prod` profile for staging. Inject `DB_URL`, `DB_USER`, `DB_PASSWORD`, and `JWT_SECRET` from staging configuration and the approved secret store. Require verified database TLS for remote connections. Generate a fresh high-entropy company signing key and a separate Base64-encoded owner key containing at least 32 random bytes. Inject the owner key as `APP_PLATFORM_JWTSECRET`. Use distinct credentials for local, staging, and production.
- [ ] Enable `APP_PLATFORM_ENABLED` and `APP_TENANCY_ALLOWADDITIONALCOMPANIES` for the isolated multi-company rehearsal. If the staging owner is not provisioned, mount a restricted, one-time password file using `APP_PLATFORM_BOOTSTRAPPASSWORDFILE`, then remove the mount after account creation.
- [ ] Approve the staging HTTPS hostname, certificate, and ingress. Set `APP_PLATFORM_PUBLICORIGIN` and `APP_CORS_ALLOWEDORIGINS` to that exact HTTPS origin, including any non-default port. Serve the Vite production build from that origin, forward `/api/*` to the private Spring backend, and use SPA fallback only for frontend routes. Terminate TLS at the trusted ingress, enforce HTTPS, and strip untrusted forwarding headers. Keep the backend unavailable directly from the public network.
- [ ] Verify servlet cookie defaults under the `prod` profile and inspect any ingress cookies for `Secure`, `HttpOnly`, and an appropriate `SameSite` policy. The current HRMS login APIs use bearer tokens and should issue no servlet session cookie.
- [ ] Keep outbound SMTP disabled and omit `spring.mail.host` and staging mail credentials while SMTP is blocked. `app.mail.enabled=false` alone is insufficient for platform invitations if a mail sender is configured. Permit a real send only after the staging SMTP credentials, controlled inbox, and explicit send authorization have been recorded.
- [ ] Record the source archive SHA-256, the resulting JAR and frontend artifact digests, the approved target, and the previous matched artifacts available for rollback.

## Build and deployment order

1. Extract the approved archive to a clean directory. Use Java 21, Maven 3.9+, and a supported Node.js version. Run `npm ci` and `npm run build` from `frontend`. Run `mvn verify` from `backend` with `HRMS_TEST_DATABASE_URL` pointing to an empty disposable loopback database named `hrms_test` for the integration tests. Use an isolated database account for this step.
2. Back up the pre-upgrade staging database if it contains records and rehearse restoring it. Inspect the configured database name and destination before starting the backend. Flyway runs on startup, so the staging connection must be verified before launching the JAR.
3. After approval, deploy the packaged Spring Boot JAR and `frontend/dist` behind the staging ingress. Record startup and migration logs with values redacted. Check that a second startup has zero pending migrations. Verify the frontend origin and proxy before signing in.

## Repeatable staging checks

Use two synthetic companies, A and B, with one admin each and at least two employee identities in A. Use distinct synthetic business records and verify the expected outcomes below. Run `PlatformOnboardingTest` and `DatabaseUpgradeTest` as a preflight on disposable loopback PostgreSQL. Those tests complement the deployed checks.

| Check | Procedure and expected outcome |
|---|---|
| TLS, ingress, and API routing | Request the approved host over HTTP without following redirects. Expect a redirect to the same HTTPS host. Connect over HTTPS using default certificate validation. Expect valid hostname and chain and the configured HSTS header. Open `/login`, `/platform/login`, and `/accept-admin-invite` directly. Expect the SPA to load and unauthenticated `GET /api/platform/session` to return 401 without returning SPA HTML. |
| CORS | Send the following preflight first with the approved origin, then with `https://unapproved.example.invalid`. Expect the approved origin echoed exactly and the unapproved origin rejected without an allow-origin header. Verify platform UI calls are same-origin. |
| Company login throttling | Record the approved ingress threshold, time window, or cooldown. From an approved staging test client, submit a bounded series of failed sign-ins against a synthetic account up to that threshold. Expect the documented rate-limit response and successful valid sign-in after the cooldown. Check that other legitimate accounts can still sign in. |
| Platform Owner authorization | Verify owner sign-in and session lookup. From separate browser sessions, confirm tenant credentials cannot access `/api/platform/companies` and the owner token cannot access company data endpoints. Verify owner logout invalidates its previous session. |
| Two-company isolation | Create distinct departments, employees, and financial records in A and B. Check list totals and detail access for each company. Cross-company reads and changes should be denied and leave both datasets unchanged. Check same-company self-service scope with A's two employees. |
| Session revocation | Sign in and check an A session, then suspend A. Verify that session and new A sign-ins are rejected. Reactivate A, verify the previous session remains invalid, and sign in again. Check that B is unaffected. |
| Attendance and leave | Check in and out as a fixture employee. Enter a completed attendance record, then approve and cancel leave for that date. Verify the existing work hours remain intact and the balance is restored. Check approval, rejection, and one unauthorized decision path. |
| Expenses and payroll | Submit a claim, verify the line total, approve then pay, and check that a different tenant cannot read it. Recalculate a draft payroll with known earnings and deductions, verify the net amount, submit it, and confirm the employee can see only the submitted payslip. |
| Payroll concurrency and hiring | Submit the same draft payroll concurrently from two valid staging sessions. Repeat for hiring a single applicant. Expect one success and one rejected action in each case. Confirm one finalized run and one employee for the applicant. |
| Invitation lifecycle | Use a controlled SMTP capture service if approved for staging with no external delivery. Verify an expired token is rejected without activating the company, a revoked or replaced token is rejected, and a valid token works once. Reuse must fail. The disposable suite makes expiry deterministic. Real inbox verification remains blocked until separately authorized. |
| Backup, restore, migration and rollback | Capture Flyway history and representative counts, take a protected pre-upgrade backup, and restore into a disposable target. Verify permissions, historical records, and counts. Upgrade that copy, run the smoke tests, then restore the pre-upgrade backup to a separate target with the matching previous app. Record restore time, recovery point, and handling of writes made after backup. Never rehearse against production. |

Example ingress probes after staging approval. Set `STAGING_ORIGIN` to the approved HTTPS origin.

```sh
curl -sS -D - -o /dev/null "${STAGING_ORIGIN}/platform/login"
curl -sS -D - -o /dev/null "${STAGING_ORIGIN}/api/platform/session"
for origin in "$STAGING_ORIGIN" https://unapproved.example.invalid; do
  curl -sS -D - -o /dev/null -X OPTIONS "${STAGING_ORIGIN}/api/auth/accept-admin-invite" \
    -H "Origin: $origin" -H "Access-Control-Request-Method: POST" \
    -H "Access-Control-Request-Headers: content-type"
done
```

## Decision

Mark a gate **PASS** only when there is evidence for the approved artifact and target. Use **FAIL** for an observed failure and **BLOCKED** when approval, infrastructure, or authorization is missing. Passing local preflight does not approve staging or production.
