# Testing Checklist — Secure Messenger MVP

> **PARTLY HISTORICAL (2026-09-30):** statements here about the prototype session layer, `STANDARD_AEAD`, v1 invitations, reciprocal pairing, device approval states, or delivery diagnostics describe code removed by DEC-021. Relay, network, and process guidance may still apply. Current protocol: [Protocol v2](../PROTOCOL_V2.md).

Use this checklist to select, run, and report verification for repository changes.

This checklist supplements `../../AGENTS.md`. It does not require every command for every task. Verification must match the actual changed-file scope and risk.

For an applicable item, record the result. For an inapplicable item, record `N/A` and a short reason. Never mark a check complete without evidence.

## 0. Establish the verification scope

Before running checks:

- [ ] Record the current branch and HEAD.
- [ ] Review the complete diff.
- [ ] List every changed file.
- [ ] Confirm that unrelated changes are absent or explicitly reported.
- [ ] Classify the task as one or more of:
  - documentation-only;
  - tests-only;
  - runtime code;
  - cryptography/session;
  - delivery/transport/relay;
  - storage/migration;
  - Android UI/lifecycle/permissions;
  - build/dependency/manifest/resource/JNI;
  - release/infrastructure;
  - security-sensitive.
- [ ] Identify affected modules and source sets.
- [ ] Identify persistent formats, wire formats, feature flags, and security boundaries that could be affected.
- [ ] Select checks based on the changed scope rather than convenience.
- [ ] Treat historical QA and old passing tests as context, not proof that the current change passes.

## 1. Test-selection rules

### Documentation-only changes

A full Android build may be skipped when all of the following are true:

- [ ] Only Markdown, text, or documentation assets changed.
- [ ] No source code, tests, Gradle files, manifests, resources, CI, scripts, relay code, schemas, deployment files, or feature flags changed.
- [ ] The documentation does not introduce or alter executable commands without verifying them against the repository.
- [ ] The documentation does not make a new runtime, security, compatibility, release, or infrastructure claim without repository evidence.
- [ ] Paths, links, filenames, module names, flags, commands, versions, and referenced commits were checked.
- [ ] Formatting and diff checks were performed.
- [ ] The reason for skipping build, lint, unit, relay, and instrumented checks is reported.

Documentation-only does **not** mean verification-free.

Minimum documentation checks:

```bash
git diff --check
git diff --stat
git diff -- <changed-document-paths>
```

Also verify manually or with available tooling:

- [ ] Relative links resolve.
- [ ] Referenced files exist.
- [ ] Module names match `settings.gradle.kts`.
- [ ] Build/test commands match README and CI.
- [ ] Feature-flag values match current source.
- [ ] Status language distinguishes active, implemented, disabled, experimental, planned, historical, and unverified work.
- [ ] Historical or lost local work is not represented as current repository content.
- [ ] No secrets, tokens, plaintext messages, private paths, or unsafe logs entered documentation.

Run broader checks when documentation changes:

- build commands;
- Gradle module names;
- CI behavior;
- Room schema or migration instructions;
- manifest or release instructions;
- relay API;
- feature flags;
- active cryptographic/session claims;
- security guarantees;
- compatibility or wire-format claims.

### Tests-only changes

- [ ] Run the changed tests.
- [ ] Compile the affected test source set.
- [ ] Run the nearest related production-code tests.
- [ ] Confirm the test fails for the intended defect when practical and passes after the fix.
- [ ] Confirm no production code was changed indirectly through fixtures, generated sources, build configuration, or shared test helpers.
- [ ] Run broader build/lint only when test wiring, Gradle, resources, manifests, dependencies, or shared runtime code changed.
- [ ] Report why unrelated checks were skipped.

### Runtime or build-related changes

Runtime, build, dependency, resource, manifest, JNI, Room, transport, relay, UI, delivery, or security-sensitive changes require focused tests plus the broader checks appropriate to the affected area.

Do not classify ambiguous scope as documentation-only.

## 2. Current verification baselines

Run commands from the repository paths shown below.

### Local Android build, lint, and unit baseline

```bash
cd android-app
./gradlew :app:assembleDebug
./gradlew :app:lintDebug :core:signal-protocol:testDebugUnitTest :core:signalstore:test test
```

The second command is the broader local repository baseline documented by the Android README.

### Current GitHub Actions build-and-unit baseline

GitHub Actions currently runs from `android-app`:

```bash
./gradlew :app:assembleDebug :app:lintDebug :core:signal-protocol:testDebugUnitTest test
node --test server/relay/test/*.test.mjs
```

Do not assume CI runs `:core:signalstore:test` merely because the local README recommends it. If SignalStore-related files change, run that task explicitly when possible.

### Relay tests

From the Android project:

```bash
cd android-app
node --test server/relay/test/*.test.mjs
```

Or from the relay directory:

```bash
cd android-app/server/relay
node --test
```

### Android instrumented checks

When a compatible device or emulator is available:

```bash
cd android-app
./gradlew :core:ui:connectedDebugAndroidTest \
  :core:storage:connectedDebugAndroidTest \
  :core:transport:connectedDebugAndroidTest
```

Current GitHub Actions emulator coverage runs:

```bash
cd android-app
./gradlew :core:ui:connectedDebugAndroidTest \
  :core:storage:connectedDebugAndroidTest
```

The transport instrumented task is documented locally but is not part of the current emulator CI command.

## 3. General test integrity

- [ ] Relevant focused tests pass before relying on broader aggregate tasks.
- [ ] Relevant unit tests pass.
- [ ] Relevant integration tests pass.
- [ ] Relevant instrumented tests pass when the environment supports them.
- [ ] Build and lint pass when relevant.
- [ ] Relay tests pass when relay or transport behavior changed.
- [ ] No unrelated failing test was introduced.
- [ ] No existing failing test was ignored without reporting it.
- [ ] No test was weakened, deleted, disabled, skipped, filtered out, or made less strict merely to pass.
- [ ] No production security check was replaced by a test-only assumption.
- [ ] Assertions verify behavior rather than only absence of crashes.
- [ ] Negative and failure cases are tested where relevant.
- [ ] Tests are deterministic or document unavoidable timing dependence.
- [ ] Tests do not depend unnecessarily on public network services.
- [ ] Tests do not contain real secrets, tokens, keys, QR payloads, plaintext user messages, private endpoints, or sensitive contact data.
- [ ] Test logs and failure output are safe to share.
- [ ] Generated test artifacts and local reports are not committed unless intentionally required.

## 4. Cryptography and session tests

When cryptographic or session behavior changes:

- [ ] Correct encryption/decryption succeeds.
- [ ] Wrong key fails.
- [ ] Modified ciphertext fails.
- [ ] Modified authentication tag fails.
- [ ] Modified AAD fails.
- [ ] Truncated and oversized payloads fail safely.
- [ ] Unsupported versions fail closed.
- [ ] Nonce format and length are verified.
- [ ] Nonce/counter allocation remains unique across repeated sends.
- [ ] Counter state survives restart where required.
- [ ] Concurrent sends cannot reuse counters or nonces.
- [ ] Retry does not accidentally allocate or reuse unsafe cryptographic state.
- [ ] HKDF domain labels and context are verified where changed.
- [ ] Authentication occurs before plaintext acceptance.
- [ ] No plaintext fallback or downgrade path is exercised.
- [ ] The active prototype session remains distinguishable from disabled Signal-backed formats.
- [ ] Tests do not claim Signal, Double Ratchet, forward secrecy, or post-compromise recovery for the prototype path.

When test vectors exist:

- [ ] Known-answer encryption/decryption vectors pass.
- [ ] Serialization is stable and versioned.
- [ ] Cross-version compatibility expectations are explicit.

## 5. Signal, PQXDH, and experimental-boundary tests

When `core:signal-protocol`, `core:signalstore`, invitation v2, or related build wiring changes:

- [ ] `SignalStoreFeatureFlags.SIGNAL_BACKED_SESSIONS_ENABLED` remains `false` unless reviewed activation was explicitly approved.
- [ ] `OfficialSignalDoubleRatchetGate.MIGRATION_ENABLED` remains `false` unless reviewed activation was explicitly approved.
- [ ] Disabled adapters still fail closed.
- [ ] Normal chats still use the prototype session implementation.
- [ ] Experimental invitation v2 cannot enter the active v1 flow.
- [ ] Placeholder PQXDH fields are not accepted as authenticated protocol material.
- [ ] Official library types are not silently wired into normal runtime code.
- [ ] In-memory protocol stores cannot be reached from the application path.
- [ ] Opaque protocol records remain encrypted at rest through the intended boundary.
- [ ] Record mapping, transaction, restart, identity-change, rollback, and downgrade behavior is not claimed complete unless tested.
- [ ] `:core:signal-protocol:testDebugUnitTest` passes.
- [ ] `:core:signalstore:test` passes when SignalStore files change.
- [ ] Source guards, when present, test the actual current repository rather than files reported only in historical local work.
- [ ] Dependency, Kotlin, Gradle, JNI/ABI, packaging, shrinker, and licensing-sensitive changes are reported.

## 6. Pairing and invitation tests

When pairing, QR scanning, manual import, contacts, or invitation formats change:

- [ ] Valid invitation generation and import succeeds.
- [ ] Reciprocal invitations work as required for bidirectional messaging.
- [ ] Blank input is rejected.
- [ ] Invalid encoding is rejected.
- [ ] Wrong prefix or version is rejected.
- [ ] Truncated data is rejected.
- [ ] Trailing data is rejected when forbidden.
- [ ] Oversized fields are rejected before unsafe allocation or persistence.
- [ ] Negative or out-of-range identifiers are rejected.
- [ ] Unknown versions do not silently downgrade.
- [ ] Experimental invitation formats are rejected by the active flow.
- [ ] Pairing requires intended confirmation.
- [ ] Malformed invitation does not create a trusted contact or partial session.
- [ ] Re-import and duplicate-import behavior is explicit.
- [ ] Identity/key replacement produces intended warning or blocking behavior.
- [ ] QR scanner permission denial and camera unavailability fail safely.
- [ ] Manual import and camera import enforce equivalent validation rules.

## 7. Device-trust tests

- [ ] `pending` device state blocks sending.
- [ ] `revoked` device state blocks sending.
- [ ] `key_changed` device state blocks sending.
- [ ] Missing primary-device state blocks sending.
- [ ] Unverified or ambiguous state fails closed.
- [ ] `verified` state permits only intended delivery.
- [ ] No silent fan-out to secondary devices occurs.
- [ ] Device-state persistence survives restart.
- [ ] Migration preserves device trust state.
- [ ] Local revocation is not tested or described as synchronized protocol revocation.
- [ ] Identity-change handling cannot silently restore trust.
- [ ] Failure paths do not fall back to an older trusted key.

## 8. Outgoing delivery tests

When outgoing delivery code changes:

- [ ] Outgoing message state persists before the first send attempt.
- [ ] Ciphertext and required metadata persist without plaintext database columns.
- [ ] `attemptCount` updates correctly.
- [ ] `nextRetryAt` updates correctly.
- [ ] Retry delay and backoff behavior are bounded and intentional.
- [ ] Retry state survives process death and app restart.
- [ ] Relay unavailability does not lose message state.
- [ ] Timeout and malformed relay response do not mark true delivery.
- [ ] Relay acceptance maps only to `Sent to delivery server`.
- [ ] Due ciphertext can be resent without logging content.
- [ ] Retry does not create duplicate visible messages.
- [ ] Concurrent foreground and WorkManager synchronization do not corrupt state.
- [ ] Successful send clears or advances pending state correctly.
- [ ] Failed send remains recoverable.
- [ ] Cancellation and worker replacement behavior are safe.
- [ ] Power-management delay is not interpreted as delivery failure or guarantee.

## 9. Incoming delivery tests

When incoming receive code changes:

- [ ] Envelope is validated and authenticated before acceptance.
- [ ] Unknown queue or recipient identifiers fail safely.
- [ ] Malformed envelope is rejected.
- [ ] Tampered envelope is rejected.
- [ ] Modified AAD is rejected.
- [ ] Duplicate/replayed message is suppressed or safely handled.
- [ ] Replay marker is durably recorded before relay deletion where required.
- [ ] Accepted message is stored atomically with required replay state.
- [ ] Crash between authentication, persistence, receipt creation, and deletion was considered.
- [ ] Relay deletion failure is recoverable and does not duplicate visible content.
- [ ] Accepted messages are stored without plaintext Room columns.
- [ ] Invalid input does not create partial contact, session, or visible message state.
- [ ] Message ordering is not trusted blindly.
- [ ] Old or delayed blobs do not bypass replay protection.

## 10. Delivery-receipt tests

- [ ] Receiver creates a receipt only after intended acceptance, decryption, and durable storage.
- [ ] Receipt is encrypted and authenticated.
- [ ] Receipt binds to the intended sender, receiver, and message identifier as required.
- [ ] Tampered receipt is rejected.
- [ ] Receipt for an unknown message is rejected or safely ignored.
- [ ] Receipt cannot mark a different message delivered.
- [ ] Duplicate receipt is idempotent.
- [ ] Receipt control payload does not appear as a visible chat message.
- [ ] Sender marks `Delivered` only after validating the authenticated receipt.
- [ ] `Delivered` is not treated as a read receipt.
- [ ] Relay acceptance alone never marks `Delivered`.
- [ ] Receipt retry and replay handling cannot corrupt outgoing state.

## 11. Room, vault, and migration tests

When storage entities, DAOs, repositories, schema, vault, or migrations change:

- [ ] Current Room schema version is confirmed.
- [ ] Exported schema files are updated when required.
- [ ] A migration exists for every supported schema transition.
- [ ] Migration tests use the actual exported schemas.
- [ ] Migration preserves encrypted fields.
- [ ] Migration preserves counters, replay markers, retry state, device trust, and identifiers.
- [ ] Migration does not introduce plaintext message columns.
- [ ] Migration failure fails safely.
- [ ] Partial migration or crash does not leave ambiguous trust or session state.
- [ ] Restart after migration succeeds.
- [ ] Downgrade or older-app behavior is understood and documented.
- [ ] Profile and session vault encryption still works.
- [ ] Wrong, invalidated, or unavailable Keystore key fails safely.
- [ ] Record encryption at rest is verified for affected tables.
- [ ] Test reset behavior remains debug-only and does not claim secure erase.
- [ ] Database metadata exposure is not confused with full database encryption.

Run storage instrumented tests when a compatible device/emulator is available:

```bash
cd android-app
./gradlew :core:storage:connectedDebugAndroidTest
```

## 12. Relay and transport tests

When relay or transport changes:

- [ ] `/health` returns the intended status.
- [ ] `POST /v1/messages` validates request shape and limits.
- [ ] `GET /v1/messages/{queueId}` handles empty, malformed, and unknown queues safely.
- [ ] `DELETE /v1/messages/{messageId}` is idempotent or has documented behavior.
- [ ] TTL expiration works.
- [ ] Oversized requests are rejected.
- [ ] Malformed JSON or payloads fail safely.
- [ ] Duplicate submission behavior is explicit.
- [ ] Relay restart behavior is tested.
- [ ] Relay unavailability and recovery are tested.
- [ ] Client timeout, cancellation, malformed response, and connection failure are tested.
- [ ] Queue identifiers and opaque blobs are not logged unsafely.
- [ ] Relay cannot forge accepted client messages.
- [ ] HTTP remains debug/QA-only.
- [ ] Release cleartext rejection remains intact.
- [ ] Public VPS or historical endpoint availability is not required for deterministic automated tests.
- [ ] Tests use local relay or controlled fixtures where possible.

Run:

```bash
cd android-app
node --test server/relay/test/*.test.mjs
```

When a device/emulator and relay are available:

```bash
cd android-app
./gradlew :core:transport:connectedDebugAndroidTest
```

## 13. Diagnostics and report tests

- [ ] Copied reports omit plaintext messages.
- [ ] Copied reports omit private/session keys and shared secrets.
- [ ] Copied reports omit tokens, QR secrets, raw pairing payloads, nonce values, ciphertext, and raw protocol records.
- [ ] Full queue identifiers are replaced with short fingerprints where intended.
- [ ] Contact data is sanitized.
- [ ] Error messages are useful without exposing secrets.
- [ ] Event grouping and timestamps remain understandable.
- [ ] Clearing diagnostics does not alter pairing or delivery state unexpectedly.
- [ ] Debug-only diagnostics remain unavailable or restricted in release builds.
- [ ] Failure assertions do not print sensitive test data.

## 14. Android UI, lifecycle, and localization tests

When relevant:

- [ ] App launches.
- [ ] Changed screen opens.
- [ ] Russian remains the first-launch default.
- [ ] Manual RU/EN selection persists across restart.
- [ ] Changed strings exist in both language resource sets where required.
- [ ] No untranslated or hard-coded user-facing text was introduced unintentionally.
- [ ] Full-row selection and accessibility behavior remain usable where changed.
- [ ] Portrait-only scanner behavior remains scoped to the scanner activity.
- [ ] Background/foreground transition preserves expected state.
- [ ] Process death and app restart preserve required state.
- [ ] Configuration change does not corrupt state.
- [ ] Permission granted, denied, denied permanently, and unavailable-device cases are handled.
- [ ] UI error states are understandable and do not expose sensitive details.
- [ ] Debug-only controls remain debug-only.
- [ ] No unexpected crash appears in Logcat during the tested flow.
- [ ] Logcat output is sanitized.

Run UI instrumented tests when possible:

```bash
cd android-app
./gradlew :core:ui:connectedDebugAndroidTest
```

## 15. Build, dependency, manifest, and release checks

When build logic, dependencies, plugins, manifests, resources, JNI/ABI, shrinker rules, or release configuration changes:

- [ ] `:app:assembleDebug` passes.
- [ ] `:app:lintDebug` passes.
- [ ] General unit tests pass.
- [ ] Affected module tests pass.
- [ ] Dependency resolution succeeds from a clean environment when practical.
- [ ] New dependency versions and repositories are intentional.
- [ ] Manifest merge result is reviewed when components or permissions change.
- [ ] Exported activities, services, receivers, and providers are reviewed.
- [ ] Debug and release source-set separation remains correct.
- [ ] HTTP/cleartext release rejection remains intact.
- [ ] R8/resource shrinking does not remove required security behavior.
- [ ] JNI/ABI packaging is checked when native artifacts change.
- [ ] APK output contains only intended ABIs and resources when relevant.
- [ ] No signing secret, local SDK path, generated key, APK, build directory, or machine-specific file is committed.
- [ ] CI configuration was not weakened or made to ignore failures.

## 16. Manual two-device QA

Manual QA is required when a change affects a real interaction that automated tests do not fully cover.

When possible, verify with controlled test data:

- [ ] Reciprocal invitation exchange.
- [ ] Message delivery in both directions.
- [ ] Relay acceptance status.
- [ ] Authenticated delivery receipt and `Delivered` transition.
- [ ] No read-receipt claim.
- [ ] Relay outage.
- [ ] Automatic retry after relay recovery.
- [ ] App restart with pending retry.
- [ ] Duplicate/replay suppression.
- [ ] Tampered payload rejection when a safe test harness exists.
- [ ] Pending/revoked/key-changed/missing-primary blocking.
- [ ] Diagnostics capture remains sanitized.
- [ ] No duplicate visible chat messages.
- [ ] Debug HTTP warning and release policy remain distinct.

Record:

- devices/emulators used;
- Android versions;
- app build or commit;
- relay type and URL class, without exposing secrets;
- exact steps;
- observed result;
- screenshots/logs captured;
- sanitization performed;
- untested gaps.

Historical phone/emulator or VPS QA must be labelled historical unless rerun.

## 17. Environment and skipped verification

When a check cannot run:

- [ ] State the exact command not run.
- [ ] State why it could not run.
- [ ] Distinguish missing environment from timeout, known failure, permissions, absent device, unavailable SDK, absent server access, or task irrelevance.
- [ ] Do not report `pass` for a skipped command.
- [ ] Do not claim CI will pass merely because local focused tests passed.
- [ ] Do not claim local verification merely because historical CI passed.
- [ ] State the risk created by the missing check.
- [ ] State what environment is needed to close the gap.
- [ ] Recommend the smallest next verification step.

Current known operational constraint:

- the previous laptop and local clone are unavailable;
- only the Android phone is currently available;
- local Gradle, Android SDK, emulator, ADB, and VPS verification cannot be assumed available.

This constraint explains skipped checks; it does not convert them into passes.

## 18. Final testing report

Every task must report:

### Scope

- Task classification
- Branch and HEAD
- Changed files
- Affected modules and security boundaries

### Checks run

For each check:

- exact command or procedure;
- environment;
- result;
- relevant output summary.

### Checks not run

For each skipped check:

- exact check;
- reason;
- resulting risk;
- environment needed.

### Assessment

- Observed behavior
- Failures and likely root cause
- Regressions found
- Security-sensitive impact
- Compatibility or migration impact
- Remaining test gaps
- Recommended next verification step
- Commit created: yes/no
- Push performed: yes/no
