# Code Cleanup Handoff — Fully-Qualified Call-Site Artifacts

## The problem

Across both the Android app (Kotlin) and the backend (Java), many files called a class or
function using its **fully-qualified name inline**, instead of importing it at the top of the file
the normal way:

```kotlin
// Before
val PUSH_NOTIF = androidx.datastore.preferences.core.booleanPreferencesKey("push_notif")
```

```kotlin
// After
import androidx.datastore.preferences.core.booleanPreferencesKey
...
val PUSH_NOTIF = booleanPreferencesKey("push_notif")
```

This is a recognizable tell that code was generated incrementally (by a human or an AI) without a
final pass to consolidate imports — a class gets referenced once inline "to get it working," and
the import never gets added afterward. It doesn't change behavior, but it's inconsistent with the
rest of each file (which does use normal imports for everything else) and reads as unfinished.

## How it was found

A regex scan across every `.kt` and `.java` source file, looking for a dotted path of 3+ package
segments followed by a capitalized identifier, appearing outside of `import` statements, comments,
and Javadoc/KDoc `@link`/`@see`/`[...]` cross-references (those are legitimate uses of a
fully-qualified name and were left alone):

```bash
# Kotlin (run from android-app/app/src/main/java)
grep -rnE "[^.\"a-zA-Z](androidx|kotlinx|com\.mediwise|com\.google)\.[a-z][a-zA-Z0-9]*\.[a-z][a-zA-Z0-9.]*\.[A-Z][a-zA-Z0-9]*" --include="*.kt" . \
  | grep -v "^\S*:\s*import " | grep -v "^\S*:\s*//" | grep -v "\[com\.mediwise"

# Java (run from backend/src/main/java)
grep -rnE "[^.\"a-zA-Z](java|com\.mediwise|org\.springframework)\.[a-z][a-zA-Z0-9]*\.[a-z][a-zA-Z0-9.]*\.[A-Z][a-zA-Z0-9]*" --include="*.java" . \
  | grep -v "^\S*:\s*import " | grep -v "^\S*:\s*//" | grep -v "@link\|@see"
```

Every hit was checked individually before fixing — a few had a real reason for the full path (see
below), and those were left untouched rather than "fixed" incorrectly.

## What was fixed

**28 files total** — 17 Kotlin (Android), 11 Java (backend). Every fix followed the same pattern:
add the missing `import` line (or reuse one that already existed for that class but wasn't applied
consistently), then replace the inline fully-qualified reference with the short name.

### Android (`android-app/app/src/main/java/com/mediwise/`)
- `core/datastore/SessionDataStore.kt` — 6 occurrences of `booleanPreferencesKey`
- `data/remote/dto/Dtos.kt` — ~19 occurrences across every `toDomain()`/`toPatientProfile()`
  mapper function (`Doctor`, `Appointment`, `Consultation`, `Prescription`, etc.) — added one
  wildcard `import com.mediwise.domain.model.*`, matching the existing convention already used in
  the repository-layer files for the same DTO-mapping purpose
- `presentation/navigation/NavGraph.kt` — missing `ConsultationDetailScreen`/`MedicalRecordScreen`/
  `MedicalDocumentsScreen` imports (present for every other screen package, just missing these
  three), plus 4x `Role.DOCTOR`
- `core/fcm/FcmTokenRegistrationWorker.kt` — `Result.Success`/`Result.Error` (a `WorkResult` alias
  already exists for WorkManager's own `Result`, freeing the name for the app's own `Result` type)
- `data/repository/AuthRepositoryImpl.kt` — `PhoneAuthCredential`
- `presentation/screens/ai/AiTriageScreen.kt` — `ExperimentalMaterial3Api`
- `presentation/screens/appointment/AppointmentListScreen.kt` — `TextOverflow` x2
- `presentation/screens/appointment/AppointmentReviewScreen.kt` — `CircleShape`, `Color.White`
- `presentation/screens/call/CallScreen.kt` — `ImageVector`
- `presentation/screens/chat/ChatScreen.kt` — `TextAlign`
- `presentation/screens/consultation/ConsultationDetailScreen.kt` — `ExperimentalLayoutApi`,
  `FlowRow`, `Color.White` x3 (the first two were already covered by an existing wildcard import —
  pure redundancy, not even a missing-import case)
- `presentation/screens/doctor/DoctorScheduleScreen.kt` — `TextOverflow` x2
- `presentation/screens/doctors/DoctorDetailScreen.kt` — `ImageVector`
- `presentation/screens/notification/NotificationScreen.kt` — `Color` (used both as a type and as
  `Color.White`)
- `presentation/screens/profile/ConsultationHistoryScreen.kt` — `Color.White`
- `presentation/screens/profile/EditProfileScreen.kt` — `LazyListScope` (receiver type), `KeyboardOptions`
- `presentation/screens/profile/ProfileScreen.kt` — `LazyListScope` (receiver type)
- `presentation/screens/profile/SettingsScreen.kt` — `ImageVector` x2
- `presentation/screens/schedule/ScheduleScreen.kt` — `SlotModel`
- `presentation/components/Components.kt` — `VisualTransformation`, `KeyboardOptions`,
  `TextOverflow`, `FollowUp`, `Role`, `Screen` (6 distinct missing imports in one file)

### Backend (`backend/src/main/java/com/mediwise/`)
- `analytics/service/AnalyticsService.java` — `Appointment.AppointmentStatus` x6
- `admin/service/AdminService.java` — `PatientProfile`
- `ai/service/AiService.java` — `PatientProfile` (redundant, already imported), `Objects`,
  `Period`, `LocalDate`, `UUID` (redundant, already imported), `ArrayList`
- `admin/controller/AdminController.java` — `UserRoleUpdateRequest` (present for every other admin
  DTO, just missing this one)
- `appointment/repository/AppointmentRepository.java` — `JpaSpecificationExecutor`
- `chat/controller/ChatController.java` — `MultipartFile`
- `common/handler/GlobalExceptionHandler.java` — `HttpRequestMethodNotSupportedException`,
  `HttpMessageNotReadableException`
- `common/response/PagedResponse.java` — `Page`
- `common/util/JwtSecretProvider.java` — `ObjectProvider`
- `common/util/JwtUtil.java` — `StandardCharsets`
- `payment/repository/PaymentRepository.java` — `Query`, `Param`, `BigDecimal`, `Instant` (5
  occurrences across 3 query methods)

## One real bug found underneath a "cosmetic" fix

`config/AuditConfig.java`'s `auditorProvider()` bean checked `principal instanceof UserDetails`,
with a comment claiming "Principal is CustomUserDetails." That class doesn't exist in this
codebase — `JwtAuthFilter` actually sets the authenticated principal to the raw `User` entity
(`new UsernamePasswordAuthenticationToken(user, ...)`), which never implements `UserDetails`. That
`instanceof` check could **never match**, so this bean always fell through to `Optional.empty()`.

**Current impact: none** — no entity in the project uses `@CreatedBy`/`@LastModifiedBy`, so nothing
currently consumes this `AuditorAware<UUID>` bean's value. But it was silently broken, and if
anyone adds those annotations later expecting JPA auditing to work, it wouldn't have. Fixed to
check for the real `User` type and read `user.getId()` directly — simpler than the original's
UUID-parsed-from-username approach, and actually correct.

## Legitimate exceptions — left alone on purpose

- KDoc/Javadoc cross-references like `[com.mediwise.core.fcm.ClinicalFcmService]` or
  `@link com.mediwise.Foo` — these are meant to reference a class that isn't (and shouldn't be)
  imported into the file just for a doc comment. Standard practice, not an artifact.
- `───` section-divider comments (e.g. `// ── Patient: Book appointment ──`) seen throughout the
  backend — these are informative navigation aids for large service classes (each carries a real
  section name, not decoration), consistent with the codebase's established style. Not touched.

## Verification performed

1. Re-ran both scan regexes after all fixes — zero remaining hits in either codebase (aside from
   the legitimate KDoc references above).
2. `cd android-app && ./gradlew :app:compileDebugKotlin` — **BUILD SUCCESSFUL**, no new warnings or
   errors introduced (same pre-existing deprecation warnings as before, unrelated to this change).
3. `cd backend && mvn clean compile` — **BUILD SUCCESS**.
4. `cd backend && mvn test` — **165/165 tests passing** (same count as before these changes; nothing
   broke).

## If you're continuing this cleanup

- Re-run the two scan commands above periodically (e.g. as a pre-commit or CI check) to catch new
  instances before they accumulate — they're cheap and have zero false positives once the KDoc/
  comment exclusions are applied.
- When adding a fix, always check whether the target name is already imported (possibly under a
  different local convention, like a wildcard import) before adding a new explicit import — several
  of the fixes above ended up being "just remove the redundant prefix" once the existing import was
  found, not "add a new import."
- Watch for the reverse mistake this pattern makes easy to introduce with `sed`: a global
  find-replace on the qualified name can accidentally rewrite the `import` line itself down to a
  bare `import ClassName` with no package — always grep the import block after any bulk replace to
  catch this before compiling.
