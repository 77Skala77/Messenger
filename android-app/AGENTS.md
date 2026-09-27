# AGENTS.md — Codex workflow rules for Secure Messenger MVP

This is a security-sensitive encrypted messenger MVP.

Treat this repository as a high-risk project where shortcuts can break confidentiality, authentication, replay safety, device trust, delivery integrity, or user privacy.

## Repository authority and evidence

The current GitHub repository is the authoritative recoverable baseline for the restored project.

Historical chat extracts, handoff notes, screenshots, and reports of lost local commits are context only. A file, commit, test, feature, or implementation that is absent from the current repository must not be treated as part of the project.

During new work, the checked-out branch and its uncommitted diff are the working state. Always report whether changes are committed, pushed, or still local.

Use this evidence order when resolving claims:

1. Current executable code, feature flags, build configuration, manifests, resources, and dependency wiring.
2. Current tests, CI configuration, and reproducible command output.
3. Current repository documentation.
4. Historical chat extracts, handoffs, and reported local work.

When sources conflict, do not choose silently. Report the conflict, identify the stronger evidence, and mark anything not verified as unverified.

## Project status

This is an MVP for architecture review and testing.

It is not production-ready and must not be represented as ready for real sensitive communications.

The current executable chat path uses the prototype session layer and `STANDARD_AEAD`.

Signal-backed sessions, PQXDH semantics, official libsignal processing, and SignalStore integration are experimental or disabled boundaries unless current code, dependency wiring, feature flags, and tests explicitly prove otherwise. `PARANOIA_C4_AEAD` was removed from the codebase entirely on 2026-09-27 and no longer exists in any form (see `docs/ai-workflow/DECISIONS.md` DEC-016).

Distinguish these statuses in code reviews and documentation:

- active
- implemented
- disabled
- experimental or test-only
- planned
- historical
- unverified
- not implemented
- obsolete or superseded

Do not call a capability implemented merely because a DTO, interface, placeholder, design document, test fake, dependency gate, or source guard exists.

## Core working mode

Before editing repository files:

1. Read this file.
2. Read `docs/ai-workflow/README.md` for the workflow map.
3. Read `CURRENT_PROJECT_STATE.md`, `HANDOFF.md`, `DECISIONS.md`, `TASK_QUEUE.md`, and the relevant checklist only as needed for the task; verify every mutable claim against the repository.
4. Inspect the relevant code, tests, configuration, documentation, and recent diff or history.
5. Restate the task briefly and classify its scope: audit, documentation-only, tests-only, runtime, build/release, or security-sensitive.
6. Identify security-sensitive areas and possible compatibility or migration impact.
7. Produce a small implementation plan.
8. List the files expected to change.
9. Prefer minimal, reviewable diffs.

Do not load every workflow document for every small task.

For audit or documentation-review tasks, report proposed changes before modifying files unless the user explicitly requests immediate editing.

After editing repository files:

1. Run relevant checks when possible.
2. Report exactly what changed.
3. Report what was verified.
4. Report what could not be verified and why.
5. Report whether related documentation was updated or why it was not needed.
6. Report security impact.
7. Report compatibility or migration impact when relevant.
8. Report remaining risks.
9. Report Git state when applicable.
10. Suggest the next safest step.

## Hard security rules

Never weaken cryptography.

Never replace cryptographically secure randomness with ordinary randomness.

Never add plaintext fallback.

Never trust the relay server.

Never turn fail-closed behavior into fail-open behavior.

Never silently continue after a security-critical failure.

Never remove security checks to make implementation easier.

Never weaken, delete, or bypass tests merely to make them pass.

Never claim production-grade security, official Signal interoperability, or readiness for real sensitive communications without independent evidence and review.

Never enable disabled experimental crypto or session modules for normal chats without explicit user approval, feature-flag isolation, tests, migration review, threat review, and independent security review.

## Secrets, storage, logging, and diagnostics

Never log, print, expose, copy into diagnostics, screenshots, crash reports, support reports, or test output:

- plaintext messages
- private keys
- session keys
- shared secrets
- authentication or bearer tokens
- QR pairing secrets
- raw pairing payloads
- raw protocol records
- raw encrypted payloads when they can aid correlation, replay analysis, or queue identification
- full queue identifiers when a short fingerprint is enough
- sensitive contact data unless explicitly sanitized

Persist security-sensitive material only when required by the design and only through an existing reviewed storage boundary such as Android Keystore, the Keystore-backed vault, or an approved encrypted-record repository.

Never duplicate secrets into plaintext Room columns, ordinary preferences, unprotected DataStore values, temporary files, copied reports, clipboard content, or committed fixtures.

Changes to key ownership, vault behavior, key invalidation, backup/restore, reset, or deletion semantics require explicit security review and tests.

Logs and copied diagnostics must be safe to share.

Prefer high-level event names, timestamps, sanitized state names, short fingerprints, counters, and retry metadata.

## Security-critical areas

Treat these as security-critical:

- cryptographic session setup
- `STANDARD_AEAD` implementation
- nonce allocation and counter persistence
- HKDF-derived per-message keys
- replay tracking
- envelope and receipt authentication
- incoming message pipeline
- outgoing delivery pipeline
- delivery receipts and status transitions
- retry state and WorkManager synchronization
- local persistence of ciphertext, encrypted records, metadata, and security-sensitive state
- Android Keystore usage
- profile and session vault keys
- Room migrations for security-sensitive tables
- QR invitation generation, scanning, validation, and manual import
- device trust states and transitions, including pending/approval, verified, revoked, key-change, missing, and ambiguous states; verify exact runtime and persisted identifiers before editing them
- fail-closed delivery policy
- relay transport, queue lifecycle, retention, and relay deletion behavior
- diagnostics and copied reports
- reset, wipe, and secure-erasure claims
- disabled Signal, libsignal, PQXDH, and SignalStore boundaries
- removed `PARANOIA_C4_AEAD` boundary (do not reintroduce a home-grown cipher cascade in any form)
- dependency, Kotlin/Gradle, JNI/ABI, manifest, resource, and packaging changes that could activate experimental code
- release cleartext-network policy and debug/release separation

Changes in these areas require tests or a clear explanation of why tests could not be run.

## Relay trust model

The relay is untrusted.

Assume the relay can:

- drop messages
- replay messages
- reorder messages
- delay messages
- return malformed payloads
- attempt downgrade behavior
- correlate metadata
- expose queue identifiers
- lie about availability
- fail during send, fetch, or delete
- serve old blobs
- attempt to trick clients into invalid state

The app must not depend on relay honesty for confidentiality, authenticity, device trust, replay safety, or message acceptance.

`Sent to delivery server` means relay acceptance only.

`Delivered` must only mean that the sender received and validated an authenticated receipt confirming that the receiver accepted, decrypted, and durably stored the message.

`Delivered` is not a read receipt.

WorkManager synchronization is best effort and must not be documented as a timely-delivery guarantee.

HTTP relay access is debug/QA-only. Do not weaken release cleartext rejection or represent a historical public endpoint as currently operational without re-verification.

## Device trust rules

Never bypass checks for:

- pending devices
- revoked devices
- key-changed devices
- missing primary device state
- unverified or ambiguous device trust state

The current single-device delivery policy must fail closed for pending, revoked, changed, or missing primary-device state.

Do not silently fan out to secondary devices.

Do not treat revocation as synchronized protocol revocation unless that protocol is actually implemented and verified.

Do not silently create or trust unknown contacts from unauthenticated or incomplete pairing state.

## Experimental crypto and session boundaries

Do not wire disabled Signal-backed sessions into the running chat path unless explicitly requested and all required gates are satisfied.

Do not treat experimental PQXDH invitation DTOs as verified PQXDH bundles.

Do not treat the disabled SignalStore spike as production Signal storage or as an official libsignal store adapter.

Do not enable libsignal-backed processing without explicit approval, tests, threat review, licensing review, toolchain review, JNI/ABI and packaging review, migration and rollback review, interoperability testing, and independent integration review.

`PARANOIA_C4_AEAD` was removed from the codebase entirely (2026-09-27, DEC-016) rather than kept disabled. Do not reintroduce it, or any other home-grown cipher cascade, without independently reviewed primitives, test vectors, domain-separated KDF/MAC design, failure semantics, and audit.

Determine any Signal migration task from the current repository, `docs/ai-workflow/TASK_QUEUE.md`, `docs/ai-workflow/DECISIONS.md`, `docs/ROADMAP.md`, and `docs/SIGNAL_MIGRATION_SPIKE.md`. The concrete task queue and current code take precedence over roadmap wording.

Do not assume that chat-reported or lost local Signal work exists. Do not recreate or port such work wholesale without inspecting the current repository and receiving explicit approval.

## Compatibility and migration rules

Do not silently change or reinterpret:

- envelope versions or authenticated associated data
- message or receipt wire formats
- QR invitation and pairing payloads
- relay API routes or identifiers
- Room schema or security-sensitive columns
- message, queue, contact, device, or session identifiers
- nonce, counter, replay-marker, or key-derivation formats
- vault, session, or protocol-record serialization
- debug/release network policy

Before changing any of these, describe:

1. the old and new formats or behavior;
2. backward-compatibility expectations;
3. migration and rollback behavior;
4. downgrade and cross-protocol confusion risks;
5. effects on existing local data and conversations;
6. tests that prove safe failure and restart behavior.

Migration failure must fail safely. Never silently discard or reinterpret security-critical state.

## Documentation integrity

Do not rewrite accurate documentation merely for style.

Preserve useful technical history, rationale, limitations, and warnings unless they are proven obsolete, incorrect, misleading, or needlessly duplicated.

When behavior changes, update the related documentation in the same change or explicitly explain why no documentation update is needed.

Do not mark a feature implemented because it was discussed, planned, documented, represented by a placeholder, or reported in a historical chat.

Use explicit labels such as enabled, disabled, experimental, planned, historical, unverified, and obsolete where ambiguity is possible.

Verify file paths, commands, links, module names, feature flags, and status claims against the current repository.

Treat dates, dependency versions, public endpoints, VPS state, CI results, and operational evidence as time-sensitive. Mark them historical or re-verify them before presenting them as current.

When documentation and implementation conflict, report the discrepancy instead of silently editing one side to match an assumption.

## Git safety

Do not create a commit unless the user explicitly requests it.

Do not push unless the user explicitly requests it.

Never force-push, rewrite published history, delete branches or tags, or reset away work without explicit approval and a clear recovery plan.

Before committing:

1. inspect the complete diff;
2. inspect the staged diff;
3. confirm only intended files are staged;
4. run `git diff --check` and, when staged, `git diff --cached --check`;
5. check that no secrets or machine-specific files are included.

Do not commit:

- `local.properties`
- local SDK paths
- `.env` files or credentials
- private keys, SSH keys, keystores, signing material, generated keys, or tokens
- APK, AAB, build outputs, `.gradle`, `node_modules`, IDE caches, or machine-specific files
- diagnostics or QA captures containing real sensitive data

If Git operations were performed, report the branch, commit hash, push status, and final working-tree state.

## Android and build rules

Prefer the existing Gradle structure.

Do not change module boundaries broadly unless explicitly requested.

Do not rewrite large UI or architecture areas when a focused fix is enough.

Avoid unrelated formatting-only changes.

Commands below assume the working directory is `android-app` unless stated otherwise.

## Verification rules

Use the current README, Gradle module list, and GitHub Actions workflow to select commands.

Common checks include:

```bash
./gradlew :app:assembleDebug
./gradlew :app:lintDebug :core:signal-protocol:testDebugUnitTest :core:signalstore:test test
node --test server/relay/test/*.test.mjs
```

When Android device or emulator behavior is affected and the environment is available, run relevant instrumented checks, including:

```bash
./gradlew :core:ui:connectedDebugAndroidTest \
  :core:storage:connectedDebugAndroidTest \
  :core:transport:connectedDebugAndroidTest
```

For a clearly documentation-only change, a full Android build may be skipped when the scope is unambiguous. Still verify paths, links, commands, status claims, contradictions, and formatting, and run `git diff --check` when working in a clone.

If documentation changes a build instruction, module name, feature status, security claim, or runtime description, verify the claim against code, configuration, tests, or CI rather than treating it as prose-only.

When relay code or relay operational documentation changes, run the relay tests when possible.

If a command cannot be run because of environment, time, SDK, emulator, device, network, credentials, or server limits, say so clearly. Never convert an unrun check into a passing claim.

## Required final report format

Every task that changes repository files must end with:

- Summary
- Files changed
- Changed-file scope
- Tests/checks run
- Tests/checks not run and why
- Documentation impact
- Security impact
- Compatibility or migration impact, when relevant
- Remaining risks
- Git status: branch, commit, push, and working tree, when applicable
- Suggested next step

For Signal, crypto, session, or invitation work, also report explicitly:

- Active normal chat path changed: yes/no
- `SIGNAL_BACKED_SESSIONS_ENABLED` value
- `OfficialSignalDoubleRatchetGate.MIGRATION_ENABLED` value
- Official libsignal active in normal chats: yes/no
- Normal v1 invitation flow changed: yes/no
- `PARANOIA_C4_AEAD` reintroduced in any form: yes/no (must always be no; it was removed entirely, not gated)
