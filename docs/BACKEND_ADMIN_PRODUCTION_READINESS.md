# Backend & Admin Panel — Production Readiness Plan

Scope: `backend/` (Spring Boot) and `mediwise-admin/` (React/Vite). Android is explicitly out of
scope for this document. Every item below is additive or isolated — nothing here requires changing
an existing endpoint's contract, DB schema of existing tables, or UI behavior. Each item states
*why it matters*, *what's there today* (verified by reading the actual code, not assumed), and *how
to add it without breaking anything*.

Where relevant, items reference concrete files found during a full backend service audit this cycle.

---

## 0. What's already solid (don't rebuild this)

Worth stating plainly so effort isn't wasted re-deriving what already exists:

- **CI/CD**: `.github/workflows/ci.yml` already runs the full backend test suite against real
  Postgres/Mongo/Redis service containers, builds the admin bundle, builds+pushes a Docker image to
  GHCR, and deploys to AWS ECS on `main`. This is a real, working pipeline, not a stub.
- **Security fundamentals**: `SecurityConfig.java` correctly distinguishes 401 (unauthenticated) from
  403 (wrong role) — a detail most projects get wrong — has method-level `@PreAuthorize`, BCrypt at
  strength 12, stateless JWT sessions, and a locked-down CORS config driven by an env var.
  `AppointmentAuthorizationService` centralizes ownership checks so every new domain (Consultation,
  Prescription, MedicalRecord, FollowUp, Review) reuses one audited implementation instead of
  reimplementing it.
- **Migrations**: V7-V14 all have indexes on the FK columns that are actually queried
  (`idx_consultations_patient`, `idx_follow_ups_patient`, etc.) — this is already done correctly for
  every *new* table.
- **Backend test suite**: 118 tests, 0 failures (verified by actually running `mvn test` this
  session, not by inspection). Service-level tests exist for every new domain and include
  authorization-denied cases.
- **Actuator** (`spring-boot-starter-actuator`) is already a dependency — the plumbing for health
  checks and metrics is present, just not exposed/configured yet (see §2).

---

## 1. Testing & Quality Gates

### 1.1 Missing test coverage (concrete, verified gaps)
- **`AiService` has zero tests.** It's the most business-critical class in the app (drives triage,
  doctor routing, `careCategory`/`patientAge` enrichment) and has no coverage for prompt construction,
  the emergency-override path, or the Gemini-failure fallback.
- **`AppointmentEventListener` has zero tests.** This class now also owns the
  `appointment_status_history` audit trail (added this cycle) — a bug here (there was one, since
  fixed: old/new status were swapped for the reschedule case) has no regression test locking in the
  fix.
- **Controller-level tests exist for only 2 of ~15 controllers** (`AdminControllerTest`,
  `AuthControllerTest`). Every new domain (Consultation, Prescription, MedicalRecord, FollowUp,
  Review, MedicalDocument) has service tests but no controller/`@PreAuthorize`/validation tests —
  meaning a broken `@RequestMapping` path or a missing `@Valid` wouldn't be caught by the suite.
- **No migration/backfill tests.** V7/V8's backfill logic (migrating `Appointment.notes/diagnosis/
  prescription` into the new Consultation/Prescription tables) has never been tested against seeded
  data, despite §22 of the implementation plan explicitly calling this out as a Definition-of-Done
  item.

**How to add without breaking anything**: pure addition of new test classes. Use
`@WebMvcTest`/`MockMvc` for controllers (matching the existing pattern in `AdminControllerTest`), and
Mockito for `AiService`/`AppointmentEventListener` (matching `AppointmentServiceTest`'s style — mock
the repository/event-publisher dependencies). None of this touches production code paths.

### 1.2 No coverage measurement
There's no JaCoCo (or similar) plugin in `pom.xml`, so there's no visibility into what % of the
codebase the 118 passing tests actually exercise, and CI can't gate on a coverage threshold. Add the
`jacoco-maven-plugin` with a `report` goal bound to `test`; start by just publishing the report as a
CI artifact (no failing threshold yet) so the current baseline is visible before anyone commits to a
number.

### 1.3 Admin panel has no test infrastructure at all
No test script in `package.json`, no `.test.jsx` files, no test runner installed. For a panel that
can approve/reject doctors, view/modify patient data, and manage payments, this is a real gap. Add
Vitest (pairs naturally with Vite, near-zero config) + React Testing Library, and start with smoke
tests for the highest-risk pages: `Login.jsx`, `DoctorVerification.jsx`, `Appointments.jsx`. This is
purely additive — doesn't touch existing components.

---

## 2. Observability

### 2.1 Actuator is installed but not configured
`spring-boot-starter-actuator` is a dependency, and `/actuator/health` is already in
`SecurityConfig`'s public-endpoint allowlist — but there's no `management.endpoints.web.exposure`
config in `application.yml`, so only the default `health`/`info` endpoints are exposed, and `health`
itself isn't configured to show component detail (DB, Mongo, Redis connectivity) by default in
production profiles. Add:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized
```
This is additive config — doesn't change any existing endpoint's behavior, only adds new
`/actuator/*` ones (already reachable/allowlisted).

### 2.2 No metrics export
Add `micrometer-registry-prometheus` (one dependency) so the now-exposed `/actuator/prometheus`
endpoint emits real metrics (request latency, JVM heap, DB pool utilization, custom counters). Zero
risk — it's a new dependency and a new read-only endpoint, doesn't touch existing code.

### 2.3 No structured/correlated logging
Current logging is plain `slf4j`/Logback console output. There's already an `X-Correlation-Id`
header exposed in CORS config (`SecurityConfig.java:116`), suggesting correlation IDs were intended
but the logging config doesn't propagate them into every log line via MDC. Add a
`Filter`/`HandlerInterceptor` that reads/generates `X-Correlation-Id` and puts it in the SLF4J MDC,
then update `logback-spring.xml`'s pattern to include `%X{correlationId}`. This makes every log line
traceable to a single request across services without changing any business logic.

### 2.4 No alerting
Once metrics exist (§2.2), wire a basic alert (e.g. error-rate or payment-failure-rate threshold) into
whatever's already monitoring the ECS deployment. This is an infra/ops task, not a code change.

---

## 3. Security Hardening

### 3.1 No global rate limiting
`ChatRateLimiter` exists but is scoped to chat only (`ChatController`, `CallSignalingController`).
There's no rate limiting on `/api/v1/auth/login`, `/api/v1/auth/register`, or the password-reset OTP
endpoint — all prime brute-force/enumeration targets. Add a `Bucket4j`-backed filter (or reuse the
existing `ChatRateLimiter`'s pattern, generalized) keyed by IP+endpoint for the auth endpoints
specifically. This is additive — a new filter that only *adds* a 429 response under abuse, doesn't
change normal-traffic behavior.

### 3.2 No dependency vulnerability scanning
No Dependabot config, no OWASP dependency-check, no Trivy/Snyk step in CI. Add a
`.github/dependabot.yml` for Maven + npm (zero code change, just a config file) and/or an
`owasp-dependency-check-maven` plugin run as a separate, non-blocking CI job initially (report-only,
so it can't break existing builds while the team triages the initial findings).

### 3.3 No explicit security headers beyond Spring's defaults
Spring Security's default `headers()` already sets `X-Content-Type-Options`, `X-Frame-Options`, and
cache-control headers, so this isn't a hole — but HSTS (`Strict-Transport-Security`) is only added
when Spring sees the request as HTTPS, which depends on the load balancer forwarding
`X-Forwarded-Proto` correctly. Verify (not necessarily change) that the ECS/ALB config forwards this
header, and add an explicit `.headers(headers -> headers.httpStrictTransportSecurity(...))` block as
defense-in-depth if not already relying on the LB for TLS termination guarantees.

### 3.4 AuthService OTP delivery is a documented no-op
`AuthService.java:276` — the password-reset OTP is generated, stored in Redis, and only logged
server-side (`log.info`), never actually emailed/texted to the user. This is a known, commented TODO,
not a hidden bug, but it blocks the password-reset flow from working for real users in production.
Wire a real provider (SES/SNS, Twilio, etc.) behind a small `OtpDeliveryService` interface — additive,
and testable by injecting a fake implementation in tests without touching `AuthServiceTest`'s
existing assertions.

### 3.5 Audit log retention has no policy
`AuditLogRepository`/`AuditAspect` write every `@Service` method call to Mongo indefinitely — there's
no TTL index or archival job, so this collection will grow unbounded. Add a Mongo TTL index (a
one-line migration/index creation, not a schema change to the document shape) once a retention period
is decided (e.g. 1 year for compliance).

---

## 4. Performance & Database

### 4.1 N+1 query patterns (found by reading the actual service code, not assumed)
- **`ReviewService.getForDoctor`** (`review/service/ReviewService.java:67-72`): for each review in a
  page (up to 50), it calls `patientProfileRepository.findById(...)` individually inside the `.map()`.
  Fix: batch-fetch all needed `PatientProfile`s in one `findAllById` call before mapping, keyed by a
  `Map<UUID, String>` lookup — same output, one query instead of up to 51.
- **`PrescriptionService.getForPatient`/`getMyPrescriptions`** (`prescription/service/
  PrescriptionService.java:83-90`): same pattern — one `prescriptionItemRepository.findByPrescriptionId...`
  call per prescription in the page. Fix: batch-fetch all items for the page's prescription IDs in one
  `findByPrescriptionIdIn(...)` query, then group in memory.

Both fixes are internal to the service method — the public method signature, return type, and
response shape are unchanged, so nothing calling these methods (controllers, Android, tests) needs to
change. Existing tests should still pass unmodified; new tests should assert the query count if a
tool like `@DataJpaTest` + a query-counting assertion is added.

### 4.2 `doctor_ratings` and `appointment_status_history` have no index on the columns they're queried by
- `doctor_ratings.doctor_id` (V1 migration) has no index, despite `ReviewService.getForDoctor` and
  `recomputeDoctorRating`'s `averageRatingForDoctor`/`countByDoctorId` both filtering on it — every
  doctor-detail-page view and every new review submission does a full table scan as this grows.
- `appointment_status_history.appointment_id` (added this cycle) has the same gap.

Fix: a new migration (`V15__add_missing_indexes.sql`) adding
`CREATE INDEX idx_doctor_ratings_doctor ON doctor_ratings(doctor_id);` and
`CREATE INDEX idx_appointment_status_history_appointment ON appointment_status_history(appointment_id);`.
Purely additive — a new migration file, no changes to existing data or existing migrations (never
edit an already-applied Flyway migration).

### 4.3 `ddl-auto: update` is still active
Already called out in the implementation plan (§18 step 22) as a deliberately deferred item — flip to
`validate` only once all migrations are confirmed stable in staging. Not a "do it now" item, just
confirming it's tracked and not forgotten.

---

## 5. Resilience & Fault Tolerance

### 5.1 No circuit breaker / retry policy on external calls
`AiService` (Gemini API), `RazorpayOrderGateway` (payment gateway), and `S3StorageService` (AWS S3)
all make outbound HTTP calls with no explicit timeout/retry/circuit-breaker configuration beyond
whatever the underlying SDK defaults to. `AiService` already has a *manual* fallback path
(`fallbackReport`) for Gemini failures, which is good, but it's ad-hoc rather than using a real
library. Add `resilience4j-spring-boot3` and wrap the three external clients with
`@CircuitBreaker`/`@Retry` annotations — this *adds* resilience around existing calls without
changing their success-path behavior at all.

### 5.2 Payment webhook idempotency
Worth explicitly verifying (not assumed broken): confirm `RazorpayOrderGateway`'s webhook handler is
idempotent against duplicate delivery (a standard webhook-provider behavior). If it isn't already,
add an idempotency check keyed on the gateway's event/payment ID before applying state changes — an
additive guard, not a rewrite of the payment flow.

---

## 6. API Design & Documentation

### 6.1 OpenAPI/Swagger coverage
`springdoc-openapi` annotations (`@Operation`, `@Tag`) are already used consistently across the new
controllers (Consultation, Prescription, MedicalRecord, FollowUp, Review) — this is in good shape.
Verify the older controllers (Appointment, Doctor, Auth) have the same level of annotation for
consistency; if not, it's a documentation-only addition with zero functional risk.

### 6.2 API versioning beyond the `/v1` prefix
Every endpoint is already under `/api/v1/...`, which is the right foundation. No action needed until
a breaking v2 change is actually required — just confirms the convention is already correctly in
place project-wide.

---

## 7. Admin Panel (`mediwise-admin`)

### 7.1 No test tooling, no linting, no type checking
`package.json` has zero devDependencies for testing, ESLint, or Prettier, and no TypeScript — so
there's nothing in CI or pre-commit that catches an undefined variable, an unused import, or a typo'd
prop name before it ships. Add:
- ESLint + `eslint-plugin-react` (config-only, doesn't change any `.jsx` file unless you also fix
  what it flags)
- Vitest + React Testing Library (§1.3)
- Optionally, incremental TypeScript adoption via `.tsx` for *new* files only (doesn't require
  converting the existing 100% JS codebase at once)

### 7.2 No frontend error tracking
No Sentry (or equivalent) and no React error boundary found anywhere in `src/`. A single uncaught
render error currently white-screens the whole admin panel with no visibility into what happened in
production. Add one top-level `<ErrorBoundary>` wrapping the router, and wire Sentry's React SDK —
both are additive, wrap-around changes that don't touch existing component logic.

### 7.3 No `.env.example`
There's no committed template showing what env vars (API base URL, etc.) the admin panel expects at
build time — new developers have to reverse-engineer it from `vite.config.js`/source. Add a
`.env.example` with placeholder values; zero risk since it's documentation, not consumed by the app
unless copied to `.env`.

### 7.4 CI builds but doesn't lint or test the admin panel
`ci.yml`'s `admin-ci` job runs `npm ci` + `npm run build` only. Once §7.1's tooling exists, add
`npm run lint` and `npm run test` steps to the same job — this only *adds* new steps to the existing
job, doesn't change the existing build/artifact-upload steps.

---

## 8. Compliance & Data Governance

Given this is a clinical/medical-record system, worth flagging even though none of this is a "bug":

- **Data retention**: no documented policy for how long `medical_documents`, `consultations`, or
  `audit_logs` are retained. This is a product/legal decision, not an engineering one, but the
  infrastructure to enforce whatever is decided (TTL indexes, S3 lifecycle policies) doesn't exist
  yet.
- **Patient data export/deletion**: no endpoint for a patient to export or request deletion of their
  own data (a GDPR-style right, and increasingly expected even outside the EU for health data). Would
  be a new, isolated endpoint per domain (Consultation, MedicalRecord, MedicalDocument) — additive,
  no change to existing read/write paths.
- **IP hashing in audit logs is already done correctly** (`AuditAspect.resolveIpHash` — SHA-256, never
  stores raw IPs) — this is a good sign the team already thought about PII exposure once; the gaps
  above are places that same care hasn't been extended to yet.

---

## 9. Suggested Priority Order

Not everything needs to happen at once. A realistic phased approach:

**Phase 1 (safety net, do first — all pure additions, no risk) — DONE**
1. ✅ Added missing indexes (§4.2) — `V15__add_missing_indexes.sql` (`doctor_ratings.doctor_id`,
   `appointment_status_history.appointment_id`).
2. ✅ Fixed both N+1 queries (§4.1) — `ReviewService.getForDoctor` and
   `PrescriptionService.getForPatient` now batch-fetch instead of querying per row; existing tests
   updated to match, full suite still green.
3. ✅ Added `AiServiceTest` (9 tests) and `AppointmentEventListenerTest` (5 tests, including a
   regression test locking in the reschedule old/new-status bug fix from earlier this cycle).
4. ✅ Actuator health/metrics config already existed in `application.yml`; added the missing
   `micrometer-registry-prometheus` dependency it needed to actually work.

Verified: 132 → 135 backend tests, all passing, via a real `mvn test` run (not inspection).

**Phase 2 (hardening) — DONE**
5. ✅ Global auth-endpoint rate limiting (§3.1) — reused `ChatRateLimiter` (already proven for chat)
   on `/auth/login`, `/register`, `/forgot-password`, `/reset-password`, keyed by client IP; new
   `RateLimitExceededException` → 429 via `GlobalExceptionHandler`; test coverage added.
6. ✅ Dependency scanning (§3.2) — `.github/dependabot.yml` covering Maven, npm, Gradle, and GitHub
   Actions, weekly.
7. ✅ Admin panel tooling (§7.1, §7.2) — ESLint (0 errors after two trivial pre-existing issues
   fixed), Vitest + React Testing Library, a real `ErrorBoundary` wrapping the app (with its own
   tests), and a `Login.jsx` smoke-test suite. `npm run lint` and `npm test` now run in CI
   (`admin-ci` job) alongside the existing build step.
8. ✅ OTP delivery (§3.4) — extracted an `OtpDeliveryService` interface; `LoggingOtpDeliveryService`
   preserves the exact previous behavior (log-only) so nothing changes functionally yet, but a real
   provider (SES/SNS/Twilio) can now be dropped in as one new `@Service` bean without touching
   `AuthService`. Test coverage added for both the delivery call and the unknown-identifier no-op.

**Phase 3 (maturity) — DONE, one item scoped down deliberately**
9. ⚠️ Resilience around external calls (§5.1) — **done for `S3StorageService` only**, scoped down
   from the original "wrap all three external clients with resilience4j annotations" plan.
   Annotation-driven `@Retry`/`@CircuitBreaker` AOP can't be proven to actually fire without a full
   Spring context test (none of this codebase's existing tests use one), so implementing it as
   annotations and calling it done would have been exactly the kind of "looks right but unverified"
   change this cycle was correcting for. Instead, `S3StorageService.putObject` now retries
   programmatically via `resilience4j-retry`'s core API (3 attempts, 300ms backoff, retrying on
   `AmazonClientException`) — plain Java, so a Mockito unit test can prove it actually retries and
   actually gives up correctly (see `S3StorageServiceTest`, 4 tests including a real
   fail-twice-then-succeed case). The file's bytes are now read into memory once and re-streamed
   per attempt, fixing a latent bug the naive version would have had (retrying against an
   already-consumed `InputStream` silently uploads a truncated file).
   `RazorpayOrderGateway` (a lambda-based `@Bean`, not a class) and `AiService`'s Gemini HTTP call
   were **not** touched: the gateway would need a real refactor (lambda → class) to safely host
   retry logic, and payment-order creation is exactly the kind of call where a wrong retry policy
   risks duplicate orders — that deserves a dedicated pass, not a rushed addition here. `AiService`
   already has an equivalent hand-rolled fallback (`fallbackReport`) covering the same need.
10. ✅ Structured/correlated logging (§2.3) — new `CorrelationIdFilter` puts a correlation ID (the
    client's `X-Correlation-Id` if sent, else a generated one) into SLF4J MDC for every request,
    echoes it back on the response, and the console log pattern now includes it. `GlobalExceptionHandler`
    and the two JWT security handlers were updated to read the same MDC value instead of each
    generating/reading their own inconsistent one — a real inconsistency this closed, not just a
    logging nicety. 4 new tests.
11. ✅ Controller-level tests for every new domain (§1.1) — added for all 6 (Consultation,
    Prescription, MedicalRecord, MedicalDocument, FollowUp, Review): 22 new tests covering routing,
    request/response mapping, and status codes, using `AuthenticationPrincipalArgumentResolver` so
    `@AuthenticationPrincipal User` params resolve correctly in standalone `MockMvc`.
12. ✅ Coverage reporting via JaCoCo (§1.2) — plugin added (report-only, no enforced threshold yet),
    wired into CI as an uploaded artifact. **Real baseline measured: 30% instruction coverage**
    (24,708/35,754 missed, 231 classes analyzed) — not a guess, read directly off the generated
    report.

Verified: 165 backend tests passing (up from 118 at the start of this session), via real `mvn test`
runs throughout, not inspection.

**Phase 4 (governance, needs product/legal input first)**
13. Data retention policy + enforcement (§8).
14. Patient data export/deletion endpoints (§8).
15. Revisit `RazorpayOrderGateway` and `AiService` resilience properly (deferred from Phase 3 §9
    above) — likely needs the gateway refactored from a lambda `@Bean` into a real class first.

None of these phases require touching the plan's already-completed 22 steps — they're all
orthogonal hardening on top of a backend that, per this session's full test runs, already works
correctly.
