# HRMS code audit

## Architecture and coverage

The application is a modular Spring Boot and Hibernate monolith with a Vue single-page frontend. The platform and company security chains use distinct JWTs backed by revocable database sessions. Company data uses Hibernate tenant discrimination and PostgreSQL composite foreign keys. Flyway versions 1 through 7 establish the schema, indexes, tenant ownership, company lifecycle, invitations, and local owner authentication.

The source inventory covers platform onboarding, dashboards, organization, employees, attendance, leave, payroll, recruitment, expenses and advances, performance, employee self-service, users and roles, notifications, and audit records. The Vue router combines feature-specific screens and schema-driven CRUD views. Most lists share the data table, action menu, and pagination components. Company pages use a shared Axios client. Platform requests use their own token.

## Findings and fixes

| Priority | Finding | Change |
|---|---|---|
| High | Cascaded child updates accept client-supplied IDs without confirming that each existing line belongs to the parent being edited. | Validate nested IDs on holiday lists, salary structures, and expense claims. |
| High | Employee relationships can serialize a reporting manager's private fields. | Keep the manager's display identity and suppress private fields on nested manager references. |
| High | Advance actions need explicit checks for valid status transitions. | Align API rules with the actions offered in the frontend. |
| Medium | Attendance check-out accepts a record without a check-in time, and invalid month input can surface a server error. | Add validation and check the API responses. |
| Medium | Shared CRUD pagination can show older responses after a newer request completes. | Apply only the latest result and reset the page when the result set becomes empty. |
| Medium | The Maven build uses custom source paths and unqualified package roots. | Move source, resources, and tests to standard Maven directories and update discovery and documentation. |

## Further verification

Existing indexes cover many foreign keys and filters. Actual query plans and N+1 behavior require representative data and query measurements. PostgreSQL row-level security could add a second read boundary after separating database roles and designing transaction-scoped tenant context for pooled connections and the platform JDBC paths. This audit does not enable RLS or rewrite applied migrations.

## Changed and added files

- **Backend:** Created `ChildRecordIds.java`. Updated the leave and payroll controllers, expense controller and service, employee model, attendance service, and payroll service. Nested updates now accept only child IDs already attached to the parent, at most once per request. Pending expense edits preserve decision metadata.
- **Build layout:** Moved backend application and tests under `backend/src/main/java/com/hrms` and `backend/src/test/java/com/hrms`. Moved resources to Maven's standard resource directories and removed source-directory overrides from `pom.xml`. Spring Boot now discovers the modules and repositories from its root package. A clean build avoids stale classes from the former package paths.
- **Frontend:** Updated `CrudPage.vue`, the company router, and the shared Axios client. Only the latest page result is applied, an empty final page falls back to a valid page, and a stale unauthorized response cannot clear a newer company session. Table searching is labeled as page-local.
- **Tests and docs:** Expanded `PlatformOnboardingTest.java` and updated `README.md` and `backend/TENANT_MIGRATION.md` for the new layout.
- **Database:** All seven migration files retained their SHA-256 hashes after the move. There is no new schema migration. The integration tests used newly initialized PostgreSQL databases on loopback.

## Initial verification

| Check | Result | Evidence |
|---|---|---|
| Maven compile and tests in clean standard layout | PASS | `mvn -B -o compile test` completed with two tests, zero failures, zero errors, zero skips. |
| Flyway clean install on PostgreSQL 18 | PASS | Spring Boot applied V1 through V7 and validated Hibernate mappings. |
| Existing-data upgrade rehearsal | BLOCKED | This run had no approved copy of the existing data for verification. The migration files were verified byte-for-byte. |
| Owner sign-in, invitations, company lifecycle | PASS | Integration tests exercised login, company creation, first-admin acceptance, invite replacement and revocation, simulated email failure and retry, suspension, and reactivation. |
| Tenant isolation and session boundaries | PASS | Two-company tests covered scoped department pagination, forbidden cross-company department reads/writes/deletes, employee and attendance reads, advance reads, payroll listing and submission, platform/company JWT separation, and session revocation. |
| Backend workflow regressions | PASS | Tests covered manager field redaction, attendance duration recalculation and invalid periods, advance transitions, holiday child ownership, and draft payroll creation. Representative read endpoints also passed for dashboard, organization, recruitment, leave, performance, administration, notifications, and auditing. |
| Frontend dependency resolution and build | PASS | `npm ls --depth=0` resolved the five direct packages and `npm run build` compiled 152 modules. |
| Working-tree whitespace check | PASS | Read-only `git diff --check` exited successfully with Windows line-ending warnings. |
| Frontend lint | BLOCKED | No lint command is configured in `package.json`. |
| Browser console, network, and end-to-end UI | BLOCKED | The browser tool could not determine the active Windows browser URL and stopped browser access. Authenticated screens and the new pagination behavior still need UI testing. |
| Exhaustive API, workflow, and load verification | BLOCKED | The current suite does not establish complete coverage. Browser coverage, representative load data, and module-specific scenarios remain necessary. |

## Follow-up verification

| Check | Result | Evidence |
|---|---|---|
| Maven compile and integration suite | PASS | Four tests passed on disposable PostgreSQL 18.7. |
| Executable JAR and Vue production build | PASS | Maven repackaged the Spring Boot JAR and Vite compiled 152 modules. |
| Workflows and security | PASS | Exercised owner login, onboarding, company suspension and session revocation, organization and employees, attendance punches, leave allocation and decisions, expense approval and payment, advances, payroll recalculation and submission, recruitment hiring, appraisals, roles, employee self-service, notifications, audit logs, and password changes. Covered negative cases for cross-company and same-company access. |
| Concurrent payroll submission and applicant hiring | PASS | Two concurrent requests produced one successful action and one rejected duplicate in each scenario. |
| Clean install and legacy upgrade | PASS | Flyway applied V1–V7 on a clean schema and upgraded a populated V2 schema to V7. Checked historical IDs, payroll amounts, attendance hours, roles, tenant constraints, and a repeat migration with zero pending changes. |
| Local backup and restore | PASS | Restored a read-only dump of the current local HRMS into a password-protected temporary PostgreSQL instance. Employee, attendance, payslip, user, company, and migration counts matched. The temporary copy was removed. |
| Local SMTP and browser onboarding | PASS | A loopback SMTP server captured the invitation for an `example.invalid` fixture inbox. Automated Chrome verified owner login, invitation acceptance, first-admin login, department creation, ten module pages and their API calls, and logout. The same checks passed against the packaged JAR running the production profile in isolation. |
| Staging SMTP delivery | BLOCKED | The approved staging SMTP host, secret-store credentials, and controlled inbox are unavailable. No external email was sent. |
| Production deployment verification | BLOCKED | A deployed HTTPS staging application and a production-version database restore were unavailable. The installed Flyway release warned that PostgreSQL 18 exceeds its declared verified range even though both local migration checks passed. |

The follow-up fixed a hardcoded browser origin that caused invitation acceptance to return HTTP 403. Configure `app.cors.allowed-origins` for the actual deployed frontend. Attendance edits now update the record identified by the URL, employee attendance reports reject invalid periods, and leave approval preserves existing attendance. Payroll actions lock existing runs during transitions. Hiring locks the applicant and commits employee creation and the applicant status together. Shared CRUD fields now have accessible labels.

## Production readiness

The primary workflows passed in isolation. Production readiness remains unverified until staging HTTPS and real invitation delivery pass with approved settings, and a deployment-specific backup and rollback rehearsal is complete. The tests cover the specified workflow paths and selected failure cases. They do not cover every edge case, all browsers, or production load. Company sign-in throttling, workload measurements, and any future RLS policy remain separate hardening work.

## Final release candidate audit · 18 November 2026

**Current release decision — BLOCKED.** Local credential remediation passed. Historical credential exposure remains documented, and reuse outside the local environment is unverified. Stage an approved, immutable candidate with independent staging secrets, then complete staging verification before production sign-off. The release tests below apply to the working-tree candidate based on `60a7f66`, which includes untracked source files and migrations. A clean checkout of that commit alone does not contain the tested candidate.

| Check | Result | Evidence |
|---|---|---|
| Git diff and file inventory | PASS | Reviewed tracked and staged diffs and inspected untracked release inputs. All 100 apparent deletions map to the standard Maven layout, including 69 files identical after package normalization. The remaining 31 moved files contain content changes. No files were staged. |
| Historical credential exposure | FAIL | A redacted scan of eight reachable commits found `backend/application-local.properties` in `8dfbca0`. Its `DB_PASSWORD`, company `JWT_SECRET`, and `ADMIN_INITIAL_PASSWORD` values matched local configuration at the time of the scan. Git history was retained and the current local configuration file is ignored. No configured SMTP credential or Platform Owner signing key was confirmed in the scanned history. |
| Local credential remediation | PASS | The local database password and company JWT key were rotated, all 13 affected local accounts received unique passwords, and company sessions were revoked. All 13 accounts passed sign-in verification. |
| Credential reuse outside the local environment | BLOCKED | Staging and production secret sources have not been verified. Treat the historical values as compromised. Use independent values in staging and verify or rotate any affected credentials before production. |
| Flyway migration hashes | PASS | The SHA-256 hashes of V1–V7 match the prior packaged release's source snapshot byte for byte. V1 and V2 also match the Git HEAD blobs at their pre-move paths. The clean install and populated V2 upgrade succeeded without applying changes to production. |
| Backend integration and package | PASS | `mvn -B -o verify` on disposable PostgreSQL 18.7 ran 6 tests with zero failures, errors, or skips and built the executable JAR. Platform controller tests used the `prod` Spring profile and isolated fixtures. |
| Frontend production build | PASS | `npm ls --depth=0` resolved the direct dependencies. `npm run build` compiled 152 modules. |
| Tenant isolation and Platform Owner security | PASS | Two-company tests verified scoped reads and mutations, the separate platform and company authentication boundaries, owner logout, tenant suspension and session revocation. Code review confirmed separate keys, issuers and audiences and a server-side platform session check. |
| Payroll concurrency and duplicate hiring | PASS | Each pair of concurrent submission or hiring requests returned one success and one rejected duplicate. Final payroll and hiring state checks passed. |
| Invitation expiry, revocation, and one-time acceptance | PASS | An expired invitation was shown as expired, rejected by the API, and left the company pending with no admin account. Resend invalidated the prior token, revocation prevented acceptance, and replay after successful acceptance was rejected. Mail in these tests was mocked. |
| Secure cookie configuration | PASS | Production profile checks verified `Secure=true`, `HttpOnly=true`, and `SameSite=Lax` for servlet session cookies. Platform and company login responses issued no session cookie. Both currently use bearer tokens. |
| Production HTTPS and live response headers | BLOCKED | No approved staging URL or ingress configuration was supplied. Certificate trust, HTTP redirects, HSTS, proxy forwarding, and actual cookie behavior for any gateway cookies remain unverified. |
| CORS and origin configuration | PASS | The local test accepted the configured HTTPS origin on company API preflight and rejected an unrelated origin with 403. Browser clients use relative API URLs. Staging must serve or proxy `/api` under the same public origin and supply the exact frontend origin in the company API allowlist. |
| Deployed frontend and API origin | BLOCKED | No staging deployment was available to verify SPA route fallback, frontend and API proxy routing, browser headers, or deployment-specific origins. |
| Database backup and restore instructions | PASS | The previous local recovery rehearsal restored a password-protected disposable PostgreSQL copy with matching counts for employees, attendance, payslips, users, companies, and Flyway history. The procedure below includes checks for archive, roles, and restore access. |
| Deployment rollback and restore | BLOCKED | A restore from a protected deployment-specific backup, the matching previous application artifact, and a measured rollback window are still required. The bundled Flyway release warned that PostgreSQL 18 exceeds its declared tested range despite the successful local run. Validate the planned version combination in staging. |
| Real staging SMTP | BLOCKED | No approved staging SMTP credentials or controlled inbox were available. No external email was sent. The earlier loopback SMTP and Chrome test remains the local evidence. |
| Company login throttling at deployment | BLOCKED | The application has owner lockout but no equivalent company login throttle. An ingress rule or application control must be configured and verified before the production sign-off. |
| Staging deployment checklist | PASS | Prepared below. Its environment-dependent steps remain open. |

### Staging deployment checklist

- [x] Rotate the exposed values on the local HRMS instance, reset all affected local accounts, and verify sign-in and session revocation.
- [ ] Verify that staging uses fresh, independent credentials through the approved secret store. Rescan the exact candidate and confirm no active credentials are present. Track historical exposure separately and rotate any confirmed reuse outside the local instance.
- [ ] Build an approved, immutable candidate containing the full working-tree source, including the new Maven paths and V3–V7. Record the source revision, JAR and frontend bundle hashes, and migration checksums. Keep the previous matched artifacts available for rollback.
- [ ] Provision isolated staging database credentials and configure `DB_URL`, `DB_USER`, `DB_PASSWORD`, independent company and owner signing keys, `app.platform.public-origin`, and the exact `app.cors.allowed-origins`. Use a private bootstrap file only if staging has no owner, then unmount it after provisioning.
- [ ] Serve the frontend and route `/api` from one approved HTTPS origin. Check certificate chain and hostname, HTTP-to-HTTPS redirects, HSTS, SPA fallback for login and invite URLs, and the protection of any ingress-set cookies. Keep the backend private behind the ingress and trust forwarding headers only from that ingress. Confirm the expected origin succeeds and an unapproved origin is denied. [Spring Security HTTP headers](https://docs.spring.io/spring-security/reference/features/exploits/headers.html)
- [ ] Configure company login throttling at the staging ingress or in the application and verify that it rejects excessive requests without affecting normal sign-in. Recheck owner lockout, tenant separation, session revocation, and the concurrency tests against the staged artifact.
- [ ] Configure only approved staging SMTP from the secret store. Obtain explicit authorization for one invitation to the controlled staging inbox, then verify arrival, the HTTPS invitation origin, acceptance, revocation, and expiry. Keep production addresses and credentials out of the rehearsal.
- [ ] Record the backup, restore, migration and rollback evidence below. Document go/no-go criteria, the sign-off owners, and the rollback decision deadline before opening production traffic.

### Release gates

| Gate | Result | What is required |
|---|---|---|
| Start staging | BLOCKED | Approve the exact source archive recorded below and the staging environment. Provide an isolated staging database, independent secrets, and an approved HTTPS hostname and ingress. No real SMTP send is needed to start this deployment. |
| Sign off staging | BLOCKED | Verify deployed TLS, redirects, HSTS, cookies where applicable, frontend and API routing, CORS, and company login throttling. Recheck login, tenant isolation, session revocation, concurrency, and invitations on the staged artifact. Real SMTP requires approved staging credentials, a controlled test inbox, and explicit authorization for the send. |
| Promote to production | BLOCKED | Complete staging sign-off, verify or rotate any production reuse of the exposed credentials, and validate the chosen PostgreSQL and Flyway versions. Rehearse upgrade, restore, and rollback against a protected deployment-specific backup with the matching previous artifact. Record rollback timing and obtain release approval. |

### Backup, migration, and rollback rehearsal

1. Capture the pre-upgrade schema version, applied checksums, PostgreSQL version, backup timestamp, and representative counts. Take a protected, encrypted database snapshot or a custom-format `pg_dump`. For an approved logical backup, account separately for cluster roles and tablespaces, and verify restore ownership and privileges. Keep database credentials out of shell arguments and logs. [PostgreSQL backup documentation](https://www.postgresql.org/docs/18/backup.html)
2. Verify the logical archive with `pg_restore --list`. Restore into an empty isolated database using `pg_restore --exit-on-error --dbname=<isolated-database> <backup-file>` and the approved ownership and privilege settings. Verify that the runtime account has the intended rights. Compare counts, representative historical payroll and attendance values, tenant ownership and foreign keys, and Flyway history. [PostgreSQL restore documentation](https://www.postgresql.org/docs/18/app-pgrestore.html)
3. Deploy the candidate to that restored staging database. Run Flyway validation and the browser, tenant isolation, payroll, and invitation checks. Capture migration duration and verify a second startup has no pending migrations. Resolve checksum or unsupported-version warnings before sign-off.
4. Rehearse rollback on another isolated target by restoring the pre-upgrade backup and starting the matching previous application artifact. Verify login and representative records. Time the restore and compare with the approved recovery objective. For an actual rollback, stop writes, preserve the migrated database and post-cutover changes for reconciliation, and redirect traffic only after validating the restored copy. An older JAR alone does not reverse a database migration.

### Local credential remediation · 18 November 2026

**PASS — Local rotation.** Rotated the PostgreSQL password for the local HRMS instance and its company JWT signing key. Reset the initial admin and the twelve other authorized local accounts reusing the exposed password, each with a unique password, and revoked existing company sessions. Removed the obsolete initial-admin setting from local configuration.

Verified that PostgreSQL accepts the replacement password and rejects the previous one. All 13 accounts authenticated through the local frontend proxy with their replacement passwords and signed out. The prior admin password and pre-rotation JWT were both rejected.

**BLOCKED — Deployment sign-off.** The historical Git exposure is still recorded above. Any reuse outside the local instance requires separate verification and rotation. Production data and Git history were untouched.

## Isolated staging package · 18 November 2026

**Decision — BLOCKED for deployment.** The source archive is prepared for review. Its digest and the isolated staging environment still require explicit approval. Production deployment is outside the authorized scope.

| Item | Record |
|---|---|
| Exact Git base | `60a7f66003762803d4dd9803d587ee94b06a0ca0` |
| Source identity | Working-tree snapshot, identified by the archive SHA-256 and per-file hashes. A clean checkout of the base commit would omit the reviewed changes and untracked Maven-layout sources. |
| Source archive | [hrms-source.zip](../hrms-staging-rc-60a7f66-20261118/hrms-source.zip) |
| Archive SHA-256 | `256812123a371ce9d7354d83cb1f3208f8e4f11af3784317482260dd518d08c3` |
| Manifest and checksum list | [SOURCE_MANIFEST.json](../hrms-staging-rc-60a7f66-20261118/SOURCE_MANIFEST.json), [SOURCE_SHA256SUMS.txt](../hrms-staging-rc-60a7f66-20261118/SOURCE_SHA256SUMS.txt), [ARCHIVE_SHA256.txt](../hrms-staging-rc-60a7f66-20261118/ARCHIVE_SHA256.txt) |
| Contents | 194 files. Includes 47 unchanged tracked files, 22 modified tracked files, and 125 inspected untracked release files. The manifest lists 100 retired tracked paths from the source relocation. Includes backend and frontend source, build files and npm lockfile, tests, V1–V7 migrations, assets, and the staging runbook. |
| Exclusions | Local configuration containing credentials, JWT keys, bootstrap passwords, DPAPI receipts, database dumps and local data, dependency caches, generated builds, temporary scripts and logs, and Git metadata and history. This audit report is delivered separately so it can record the archive digest. |

The archive uses an explicit source-file allowlist. The review checked candidate files for literal credential patterns and known local or historical values without printing them. The integration test now generates its passwords and signing keys at runtime rather than embedding fixed fixtures. All 194 extracted files matched their manifest hashes, and all seven Flyway files matched the previous tested source snapshot byte for byte.

Use the [staging runbook](STAGING_RUNBOOK.md) for isolated database and secret provisioning, HTTPS and API ingress, the CORS allowlist, cookie settings, build order, and repeatable release verification.

| Gate | Result | Evidence or next requirement |
|---|---|---|
| Source inventory, untracked review, and archive integrity | PASS | All expected source paths were included. No unknown untracked file was accepted. Every extracted file matched its SHA-256. |
| Credential exclusion | PASS | The release ZIP excludes local credentials and Git history. Historical exposure in the repository remains recorded above. |
| Flyway migration integrity | PASS | V1–V7 matched the prior tested source snapshot. |
| Backend from extracted ZIP | PASS | `mvn -B -o verify` ran six tests on disposable loopback PostgreSQL with zero failures, errors, or skips. Clean install and populated V2-to-V7 upgrade both passed, and Maven packaged the Spring Boot JAR. |
| Frontend from extracted ZIP | PASS | With a clean extraction and no bundled dependencies, `npm ci` installed the locked dependencies and `npm run build` compiled 152 modules. The initial offline attempt lacked a cached package. The registry-backed retry passed. |
| Exact source package approval | BLOCKED | Await explicit approval of the archive digest above. |
| Isolated staging infrastructure approval | BLOCKED | Await an approved staging database, fresh independent credentials and signing keys, secret-store references, HTTPS hostname, and ingress configuration. |
| Staging deployment | BLOCKED | Await both package and environment approvals. No deployment has been attempted. |
| Deployed verification | BLOCKED | Run the runbook against the approved deployment. It covers HTTPS and routing, CORS, login throttling, owner boundaries, tenant isolation, session revocation, HR workflows, concurrency, invitation lifecycle, and recovery. |
| Real SMTP delivery | BLOCKED | An approved staging SMTP configuration, controlled inbox, and explicit authorization to send are all required. No external email was sent. |
| Production promotion | BLOCKED | Production access and deployment are outside the authorized scope. |
