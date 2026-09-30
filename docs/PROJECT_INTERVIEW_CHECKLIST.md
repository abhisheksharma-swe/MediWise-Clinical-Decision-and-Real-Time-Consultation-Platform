# MediWise — Project Interview Checklist

A guide to the parts of this codebase you should understand well enough to explain out loud,
without notes, in an interview. Written from a full pass over the backend and admin panel —
every claim here is backed by an actual file/line in the repo, not a guess.

---

## 1. Architecture

MediWise is a three-part system:

- **`android-app/`** — Kotlin + Jetpack Compose client (patients and doctors).
- **`backend/`** — Spring Boot REST + WebSocket API, the single source of truth for all business logic.
- **`mediwise-admin/`** — React (Vite) admin dashboard for platform operators.

Three datastores, each used for what it's actually good at:
- **PostgreSQL** — all transactional, relational data: users, appointments, consultations,
  prescriptions, medical records, payments.
- **MongoDB** — chat messages, AI symptom reports, audit logs — higher-volume, less-structured,
  append-heavy data.
- **Redis** — JWT refresh-token/blacklist state, OTP codes for password reset, distributed locks.

**What to be able to say in one sentence:** "Relational data that needs joins and transactions goes
in Postgres; high-volume append-only data goes in Mongo; anything short-lived or needing atomic
counters/locks goes in Redis."

## 2. Backend Flow

Every new domain (Consultation, Prescription, MedicalRecord, FollowUp, Review) follows the same
layering: `Controller` (routing, `@PreAuthorize` role check) → `Service` (business logic +
ownership check via `AppointmentAuthorizationService`) → `Repository` (Spring Data JPA) → `Entity`.
DTOs have hand-written `from()` static factories (no MapStruct) so the entity-to-response mapping
is explicit and easy to read in a diff.

`GlobalExceptionHandler` is a single `@RestControllerAdvice` mapping every domain exception
(`ResourceNotFoundException` → 404, `BusinessException` → 400, `SlotConflictException` → 409,
`RateLimitExceededException` → 429, etc.) to a consistent `ApiResponse` JSON shape. No controller
has its own try/catch for these — that's the whole point of centralizing it.

## 3. Frontend Flow (Admin Panel)

Plain React + Vite, no Redux — `AuthContext` (React Context) holds the JWT and user, `axios`
handles API calls, `react-router-dom` handles routing. Pages under `src/pages/` are one per admin
feature (Doctors, Appointments, AuditLogs, etc.), each independently fetching its own data via
`services/*.js` wrapper functions around axios.

## 4. Database Design

Look at `backend/src/main/resources/db/migration/` — it's the actual source of truth for the
schema, in order (V1 through V15). Key relationships to know:
- `appointments` is the hub: it references `patient_profiles`, `doctors`, and `time_slots`.
- `consultations` (V7) has a unique FK to `appointments` — one consultation per appointment,
  formalizing what used to be three free-text columns directly on `Appointment`
  (`notes`/`diagnosis`/`prescription`, kept temporarily for backward compatibility).
- `prescriptions` → `prescription_items` is a one-to-many, each item structured
  (medicine/dosage/frequency/timing), not a free-text blob.
- `patient_conditions`/`patient_allergies`/`patient_medications` are patient-owned (not
  appointment-scoped), with a `self_reported` flag distinguishing patient-entered vs.
  doctor-confirmed data, and an optional `source_consultation_id` linking back to where a
  doctor-authored entry came from.
- `doctor_ratings` and `appointment_status_history` are append-only audit-style tables.

**Be ready to explain:** why `Appointment.notes/diagnosis/prescription` still exist alongside the
new `Consultation` table (answer: rollout safety — old Android clients reading those columns don't
break while the migration to the new endpoints happens gradually; removing them is a planned final
cleanup step once Android is confirmed migrated).

## 5. Authentication / Authorization

Two layers, and the difference matters:
- **Authentication** (who are you): `JwtAuthFilter` — a `OncePerRequestFilter` that accepts either
  a MediWise-issued JWT or a raw Firebase ID token (auto-provisioning a new patient account on
  first Firebase login). Tokens carry a `jti` (JWT ID) checked against a Redis blacklist on logout,
  since JWTs can't be "revoked" server-side otherwise.
- **Authorization** (are you allowed to do this): two levels —
  - Role-based: `@PreAuthorize("hasRole('DOCTOR')")` at the controller.
  - Ownership-based: `AppointmentAuthorizationService` — a shared service (not duplicated per
    domain) answering "is this patient me, or a doctor who has actually treated this patient, or
    an admin?" Every new domain calls the same three methods
    (`assertCanViewPatientHistory`/`assertCanAccessAppointment`/`assertDoctorOwnsAppointment`)
    instead of reimplementing the check.

**A subtle but important detail**: `SecurityConfig` explicitly disables Spring Security's default
`anonymous()` authentication and wires separate `AuthenticationEntryPoint` (401) vs.
`AccessDeniedHandler` (403) beans. Why this matters: without it, a missing/expired token gets
reported as 403 instead of 401, and the Android app's OkHttp `Authenticator` only triggers
token-refresh-and-retry on a 401 — so this one config choice is what makes silent token refresh
work at all.

## 6. API Flow

Every endpoint is under `/api/v1/...`. Every response is wrapped in `ApiResponse<T>`
(`success`, `data`, `message`, `code`, `correlationId`, `timestamp`) — this consistency is what
lets `GlobalExceptionHandler` and every Android/admin API client treat success and error paths the
same way. Pagination uses a consistent `PagedResponse<T>` (`content`, `page`, `size`,
`totalElements`, `totalPages`, `first`, `last`) built from a Spring Data `Page<T>`.

Every request now carries a correlation ID (`X-Correlation-Id`, client-supplied or generated) that
flows through SLF4J MDC into every log line for that request, and back out in error responses — so
a support ticket with one ID can be traced through the whole request's logs.

## 7. Important Classes/Components

- **`AppointmentService`** — the largest, most important service. Owns the entire appointment
  lifecycle state machine (`PENDING → CONFIRMED → IN_PROGRESS → COMPLETED`, plus `CANCELLED`,
  `NO_SHOW`, `RESCHEDULED`), publishes a domain event on every transition
  (`AppointmentBookedEvent`, `AppointmentCancelledEvent`, etc.) consumed by
  `AppointmentEventListener` for notifications and audit-trail writes.
- **`TimeSlotRepository.tryConsumeLockedSlot`** — see §9, the most interesting concurrency code
  in the project.
- **`AiService`** — Gemini-based symptom triage with a hard-coded safety floor: an
  emergency-keyword match (`"chest pain"`, `"can't breathe"`, etc.) forces `urgencyScore=100` and
  `Emergency Medicine` regardless of what the model returns, and every recommendation gets a
  server-appended "not a substitute for professional medical advice" disclaimer that the model
  itself never controls.
- **`S3StorageService`** — the shared upload/presign logic, extracted from what used to be
  duplicated in `ProfileService` and `ChatService`. Uploads retry up to 3 times on transient AWS
  failures, buffering the file's bytes once so each retry attempt gets a fresh stream (retrying
  against an already-read stream would silently upload a truncated file).
- **`RazorpayOrderGateway`** — a one-method adapter interface wrapping the Razorpay SDK, which
  exposes its API as public fields rather than injectable beans (so it can't be `@Mock`ed
  directly). The interface is the seam that makes `RazorpayService` unit-testable.

## 8. Important Design Decisions

- **Shared authorization/upload services instead of per-domain duplication** — flagged during a
  code audit as a real duplication problem and fixed by extracting `AppointmentAuthorizationService`
  and `S3StorageService` once, used everywhere.
- **Event-driven notifications** — `AppointmentService` doesn't call `NotificationService`
  directly; it publishes a domain event, and `AppointmentEventListener` consumes it
  `@TransactionalEventListener(phase = AFTER_COMMIT)` — meaning a notification is only ever sent
  after the DB transaction actually commits, so a booking that later rolls back never sends a
  false notification.
- **Presigned URLs only for genuinely sensitive content** — profile images and chat attachments
  stay on permanently public S3 URLs (a known, documented, deliberately-deferred gap), but medical
  documents use time-limited presigned URLs generated fresh on every read, never persisted.
- **Programmatic retry over annotation-based AOP** — `S3StorageService`'s retry logic is written as
  plain Java calling Resilience4j's core API directly, not `@Retry` annotations, specifically so a
  plain unit test (no Spring context) can prove the retry actually happens.

## 9. The Hardest Piece of Code Here: Slot Locking

`TimeSlotRepository.tryLockSlot` / `tryConsumeLockedSlot` implement optimistic concurrency control
as conditional SQL `UPDATE` statements instead of a `SELECT` followed by a `save()`. Booking a slot
is a two-phase process:
1. `tryLockSlot` — `UPDATE ... WHERE id = :slotId AND status = 'AVAILABLE'`. If two patients hit
   this simultaneously for the same slot, only one `UPDATE` affects a row (returns 1); the other
   affects zero rows (the `WHERE` no longer matches once the first request already flipped the
   status) — that's the signal to reject the second request with a conflict, not a re-read.
2. `tryConsumeLockedSlot` — same pattern, but conditioned on `status = 'LOCKED' AND lockedBy = :userId`,
   turning a temporary lock into a permanent booking only if the caller is the one who holds it.

**Why this matters**: a naive "read the slot, check if available, then save" has a race window
between the read and the write where two requests can both pass the check. This pattern closes
that window entirely at the database level — no `synchronized`, no distributed lock needed for this
specific operation, because the atomicity comes from the single conditional `UPDATE` statement
itself.

## 10. Error Handling

Every custom exception (`BusinessException`, `ResourceNotFoundException`, `SlotConflictException`,
`UnauthorizedException`, `PaymentException`, `RateLimitExceededException`) is a plain unchecked
`RuntimeException` with a stable error `code` string, mapped once in `GlobalExceptionHandler`. No
controller has bespoke error handling — that's deliberate, so every endpoint's error shape is
predictable to any client.

## 11. Security Considerations

- BCrypt at strength 12 for passwords.
- JWT with a separate access/refresh token pair (short-lived access token, longer refresh token),
  blacklist-on-logout via Redis keyed by JWT ID.
- IP addresses in audit logs are SHA-256 hashed, never stored raw (`AuditAspect.resolveIpHash`).
- Rate limiting on `/auth/login`, `/register`, `/forgot-password`, `/reset-password` (added this
  cycle) — keyed by client IP via a reused sliding-window limiter (`ChatRateLimiter`), since these
  are the classic brute-force/enumeration targets and previously had no protection.
- Password-reset flow never reveals whether an identifier corresponds to a real account (same
  response either way) — a deliberate anti-enumeration measure.
- `AuditAspect` is a Spring AOP `@Around` advice wrapping every `@Service` method call, writing a
  structured audit entry (actor, action, outcome, hashed IP) to MongoDB — this is how the platform
  gets a tamper-evident record of who did what, without every service method manually logging it.

## 12. Deployment

`.github/workflows/ci.yml` runs on every push/PR: backend tests against real Postgres/Mongo/Redis
containers (not mocks, an actual integration-style test run), admin lint+test+build, then on `main`
a Docker image build/push to GHCR and a rolling deploy to AWS ECS. `spring.jpa.hibernate.ddl-auto`
is currently `update` (auto-migrates on top of Flyway-managed schema) — flipping this to `validate`
is a deliberately deferred step, done only once all migrations are confirmed stable in staging.

## 13. Third-Party Services

- **Google Gemini** — symptom triage (`AiService`), with a manual fallback response if the API is
  unreachable/misconfigured rather than failing the request.
- **Razorpay** — payment gateway, order creation, and signature-verified payment confirmation
  (`RazorpayService` verifies the HMAC signature server-side before trusting a payment claim from
  the client — never trusts the client's "payment succeeded" assertion alone).
- **Firebase** — authentication (ID token verification) and FCM push notifications.
- **AWS S3** (or local MinIO in dev) — file storage for profile images, chat attachments, and
  medical documents.

## 14. Difficult Technical Problems Actually Solved Here

1. **The slot-booking race condition** (§9) — two patients can't double-book the same slot.
2. **401 vs. 403 semantics for token refresh** (§5) — a one-line-sounding config choice with a real
   client-side consequence if gotten wrong.
3. **Backward-compatible schema migration** — adding `Consultation` as a new table while keeping
   the old `Appointment.notes/diagnosis/prescription` columns alive during rollout, with a V7
   migration that backfills existing data into the new structure.
4. **AI safety floor that the model can't override** — the emergency-keyword check runs after
   Gemini's response comes back and can force-overwrite its urgency/specialty/recommendation,
   because a medical triage feature can't rely on a language model alone to catch "this is an
   emergency."
5. **Retry logic that's actually provably correct** — see §8's note on programmatic vs.
   annotation-based resilience.

---

## Top 20 Interview Questions Based Specifically on This Project

1. Walk me through what happens, end to end, when a patient books an appointment.
2. How do you prevent two patients from booking the same time slot at the same time?
3. Why is there a separate `AuthenticationEntryPoint` and `AccessDeniedHandler` instead of one handler?
4. What's the difference between authentication and authorization in your system, concretely?
5. Why did you extract `AppointmentAuthorizationService` instead of leaving the checks in each service?
6. Walk me through your JWT refresh flow. What happens when an access token expires mid-request?
7. How do you revoke a JWT before it naturally expires?
8. Why does `Consultation` exist as a separate table instead of just adding columns to `Appointment`?
9. How did you migrate existing data into the new `Consultation`/`Prescription` tables without breaking old clients?
10. Explain your `ApiResponse` wrapper — why does every endpoint return the same shape?
11. How does your AI symptom-triage feature avoid giving a dangerous "you're fine, stay home" response to an actual emergency?
12. What happens if the Gemini API is down when a patient submits symptoms?
13. Why do medical documents use presigned S3 URLs but chat attachments and profile images don't?
14. Walk me through your payment flow — how do you know a payment actually succeeded and wasn't spoofed by the client?
15. What's the difference between your rate limiter and a production-grade one (e.g., what would break if you scaled to multiple instances)?
16. Why is the S3 upload retry implemented as plain Java instead of a `@Retry` annotation?
17. What would happen if two retries of an S3 upload used the same input stream instead of a byte buffer?
18. How does a request's correlation ID get from an incoming HTTP header into every log line for that request?
19. Explain the event-driven notification design — why publish an event instead of calling `NotificationService` directly from `AppointmentService`?
20. If you had to add a "cancel and refund" feature tomorrow, which existing pieces would you reuse and where would new code go?

---

## Code You Should Specifically Study Before an Interview

| File | What it does | Why it exists | Concepts to know | Likely question |
|---|---|---|---|---|
| `schedule/repository/TimeSlotRepository.java` | Conditional `UPDATE` statements for slot lock/consume | Prevents double-booking without a SELECT-then-UPDATE race | Optimistic concurrency control, atomic compare-and-swap via SQL `WHERE` clause | "How do two conditional UPDATE queries prevent a race condition here?" |
| `config/SecurityConfig.java` | Spring Security filter chain, 401/403 split | Makes Android's silent token-refresh actually work | Spring Security filter chain ordering, `AuthenticationEntryPoint` vs `AccessDeniedHandler` | "Why does a missing token need to be 401, not 403?" |
| `ai/service/AiService.java` | Gemini triage + hard-coded emergency override | Safety-critical: a model can't be the only safeguard | Prompt engineering constraints, defense-in-depth, fallback design | "How do you stop the AI model from making a dangerous call on its own?" |
| `notification/listener/AppointmentEventListener.java` | Consumes appointment lifecycle events | Decouples notification/audit logic from the state-machine logic | `@TransactionalEventListener(phase=AFTER_COMMIT)`, event-driven architecture | "Why AFTER_COMMIT and not a plain `@EventListener`?" |
| `common/storage/S3StorageService.java` | Shared S3 upload w/ retry | Consolidates 3 duplicated upload blocks; safe retry | Resilience patterns, stream re-read hazards | "What breaks if you retry with the same InputStream?" |
| `payment/service/RazorpayOrderGateway.java` + `RazorpayService` | Payment order creation/verification | SDK exposes public fields, not mockable directly | Adapter pattern, HMAC signature verification, why you never trust client-reported payment success | "How do you know a client isn't lying about a successful payment?" |
