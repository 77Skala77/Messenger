# Task Queue — Secure Messenger MVP

This file contains the concrete, ordered work queue for the current repository.

It is not:

- a project-state snapshot — use [`CURRENT_PROJECT_STATE.md`](CURRENT_PROJECT_STATE.md);
- an architectural decision log — use [`DECISIONS.md`](DECISIONS.md);
- a strategic roadmap — use [`../ROADMAP.md`](../ROADMAP.md);
- a session diary — use [`HANDOFF.md`](HANDOFF.md);
- proof that work is complete — use the repository, tests, CI, and Git history.

## Queue baseline

- Repository: `https://github.com/77Skala77/Messenger`
- Authoritative recoverable branch: `main`
- Last verified GitHub HEAD before this queue update: `527c5ae95609b1601b6f6e8a0823c097a0ba4dd3`
- Queue created: `2026-07-26`; reconciled `2026-09-27` (Phase 0 of `REWRITE_PLAN.md`)
- Open GitHub issues found during queue creation: none
- Current limitation (as of 2026-07-26, now outdated): the previous laptop and local
  clone were unavailable; only the Android phone was available. A working development
  machine now exists (2026-09-27); see `CURRENT_PROJECT_STATE.md` section 14.
- **Primary current direction:** `docs/ai-workflow/REWRITE_PLAN.md` — a real
  Double Ratchet/PQXDH crypto rewrite followed by a UI pass. TQ-100 through TQ-107
  below describe overlapping Signal-migration groundwork; treat the rewrite plan as
  authoritative where the two disagree on sequencing.

Reverify branch, HEAD, issues, pull requests, and repository state before starting a task.

## Status definitions

- **READY** — can start from current repository evidence.
- **READY AFTER TQ-xxx** — starts only after the named task.
- **IN PROGRESS** — actively owned.
- **BLOCKED** — missing decision, environment, review, or prerequisite.
- **DEFERRED** — deliberately not scheduled.
- **DONE** — acceptance criteria are met and repository evidence is recorded.
- **CANCELLED** — no longer needed; reason recorded.

## Priority definitions

- **P0** — source-of-truth or documentation integrity work required first.
- **P1** — restores verification capability or closes a significant current gap.
- **P2** — security/release preparation that does not block the immediate workflow.
- **P3** — later product or research direction.

## Queue rules

1. Keep at most one implementation task **IN PROGRESS** unless tasks are independent.
2. Audit the current repository before editing.
3. Do not infer implementation from documentation, interfaces, DTOs, placeholders, disabled modules, fixtures, or historical chat reports.
4. Keep each task narrow enough for one focused review and commit.
5. Split unrelated runtime, build, storage, protocol, UI, and documentation changes.
6. Do not enable disabled Signal, PQXDH, libsignal, or SignalStore paths through an ordinary task. (`PARANOIA_C4_AEAD` no longer exists in the codebase — removed 2026-09-27.)
7. Do not commit or push unless explicitly requested.
8. Record exact checks run and skipped.
9. Link a GitHub issue or pull request when one is created.
10. On completion, record repository evidence and update affected state, decision, checklist, and handoff documents.
11. Do not keep an unlimited completed-work archive here; Git history is authoritative.

## Current order

1. ~~TQ-002 — Add the approved workflow package to GitHub~~ — DONE
2. ~~TQ-004 — Reconcile existing project documentation~~ — DONE
3. ~~TQ-003 — Audit canonical device-state identifiers~~ — CANCELLED (legacy states removed,
   DEC-021)
4. ~~TQ-005 — Rebuild a trusted development environment~~ — DONE
5. TQ-006 — Re-establish the executable verification baseline (clean-clone run open)
6. TQ-007 — Establish the release-variant baseline (signing, `lintRelease`, manifest audit open)
7. TQ-008 — Add dependency verification, locking, and SBOM workflow
8. ~~TQ-009 — Update outdated dependencies and AGP (51 lint warnings)~~ — DONE 2026-09-30

Phase 1 of `REWRITE_PLAN.md` (steps 1a–1f) replaced TQ-100 to TQ-103 and TQ-105. Multi-device
(TQ-104), the independent audit (TQ-106), and the production relay (TQ-107) remain blocked on
later phases of that plan, which is the primary active plan.

---

# Now

## Recently completed — TQ-001 Cross-document consistency review

**Priority:** P0
**Status:** DONE
**Completed:** 2026-07-26
**Repository changes:** None
**Commit:** None
**Push:** None

The ten-file workflow package was reviewed as one system outside GitHub. The review aligned the README index, current-state audit status, handoff, status vocabulary, device-state caveat, task status, file links, and whitespace checks. Unsupported entries not grounded in current GitHub were removed.

Completion evidence is the final reviewed package and the separate workflow consistency audit report. Retain this compact record until TQ-002 is integrated.

---

## TQ-002 — Add the approved workflow package to GitHub

**Priority:** P0
**Status:** DONE (corrected 2026-09-27 — was stale as READY; integrated via commits
`abfa147` through `527c5ae`, see `CURRENT_PROJECT_STATE.md` section 13)
**Type:** Documentation-only repository change
**Security-sensitive:** Yes
**Explicit repository-write approval required:** Yes
**Explicit commit/push approval required:** Yes

### Objective

Replace or add the approved workflow files without changing executable behavior.

### Expected actions

Update:

- `android-app/AGENTS.md`
- `android-app/docs/ai-workflow/README.md`
- `android-app/docs/ai-workflow/HANDOFF.md`
- `android-app/docs/ai-workflow/PROMPTS_FOR_CODEX.md`
- `android-app/docs/ai-workflow/SECURITY_CHECKLIST.md`
- `android-app/docs/ai-workflow/TESTING_CHECKLIST.md`
- `android-app/docs/ai-workflow/RELEASE_CHECKLIST.md`

Create:

- `android-app/docs/ai-workflow/CURRENT_PROJECT_STATE.md`
- `android-app/docs/ai-workflow/DECISIONS.md`
- `android-app/docs/ai-workflow/TASK_QUEUE.md`

### Procedure

1. Re-fetch every target file and current blob SHA.
2. Reverify `main` HEAD.
3. Compare current GitHub contents with approved drafts.
4. Confirm no target changed unexpectedly.
5. Apply only approved documentation changes.
6. Re-fetch written files.
7. Verify links, filenames, UTF-8, and final text.
8. Review the complete diff.
9. Commit only when requested.
10. Push only when requested.

### Acceptance criteria

- [ ] All ten files exist at intended paths.
- [ ] Useful current content was not accidentally lost.
- [ ] No executable code, configuration, test, schema, or source changed.
- [ ] Workflow README lists every existing workflow file and no absent file.
- [ ] Relative links resolve.
- [ ] No secret, private key, token, plaintext, machine path, or unsafe log was added.
- [ ] `git diff --check` passes.
- [ ] Commit/push state is exact.
- [ ] Current state and handoff reflect the integration.
- [ ] Resulting commit is recorded if created.

A full Android build may be skipped as documentation-only, but skipped checks must not be called passes.

---

## TQ-003 — Audit canonical device-state identifiers and persistence

**Priority:** P0
**Status:** CANCELLED (2026-09-30) — the legacy device-approval and verification states it would audit were removed with the prototype (DEC-021). v2 contact states are defined in `../PROTOCOL_V2.md`.
**Type:** Audit first; no code change in the first pass
**Security-sensitive:** Yes

### Context

Current documentation uses:

- `UNVERIFIED`
- `KEY_CHANGED`
- `PENDING_APPROVAL`
- `pending`
- `verified`
- `revoked`
- `key_changed`

The intended fail-closed behavior is clear, but exact runtime, database, serialization, and UI identifiers are not normalized.

### Objective

Determine canonical identifiers and semantics from current code and persisted formats before editing terminology.

### Inspect

- domain enums and data classes;
- Room entities, converters, schemas, and migrations;
- repositories and delivery policy;
- pairing and invitation flows;
- UI strings and rendering;
- tests;
- README, crypto design, threat model, limitations, current state, decisions, and checklists;
- any serialized or wire-visible state.

### Questions

1. Which exact identifiers exist in source?
2. Which are persisted?
3. Which are UI labels only?
4. Which are historical documentation terms?
5. Is `pending` equivalent to `PENDING_APPROVAL`?
6. How does `UNVERIFIED` relate to `verified`?
7. Is any name part of a wire, QR, database, or migration format?
8. Would renaming require migration or compatibility handling?
9. Which vocabulary should documentation use without changing persisted values?

### Acceptance criteria

- [ ] Exact code locations are recorded.
- [ ] Runtime, persisted, wire, and display names are separated.
- [ ] Pending, verified, revoked, changed, missing, and ambiguous semantics are documented.
- [ ] No persisted value is renamed casually.
- [ ] Canonical documentation vocabulary is proposed.
- [ ] Compatibility and migration impact are stated.
- [ ] Documentation corrections are listed.
- [ ] Any runtime rename becomes a separate approved task.

---

# Next

## TQ-004 — Reconcile existing project documentation

**Priority:** P1
**Status:** DONE
**Completed:** 2026-07-26
**Type:** File-by-file documentation audit
**Security-sensitive:** Yes

### Objective

Bring non-workflow documentation into agreement with code, the workflow package, and canonical device-state findings.

### Completed scope

Core documents:

- `android-app/README.md`
- `android-app/docs/CRYPTO_DESIGN.md`
- `android-app/docs/THREAT_MODEL.md`
- `android-app/docs/KNOWN_LIMITATIONS.md`
- `android-app/docs/MVP_DELIVERY_BASELINE.md`
- `android-app/docs/ROADMAP.md`
- `android-app/docs/SIGNAL_MIGRATION_SPIKE.md`
- `android-app/docs/LIBSIGNAL_TOOLCHAIN_REVIEW.md`
- `android-app/docs/DEPENDENCY_REVIEW.md`
- `android-app/docs/AUDIT_READINESS.md`

Later relay, QA, VPS, localization, and deployment passes:

- `android-app/docs/LOCALIZATION.md`
- `android-app/docs/TWO_DEVICE_TEST.md`
- `android-app/docs/MANUAL_TWO_DEVICE_QA.md`
- `android-app/docs/PHONE_EMULATOR_PUBLIC_RELAY_QA.md`
- `android-app/docs/RELAY_PUBLIC_TESTING.md`
- `android-app/docs/VPS_RELAY_DEPLOYMENT.md`
- `android-app/deploy/README.md`
- `android-app/docs/LOGCAT_QA.md`

### Rules applied

- Audit and propose before editing each file.
- Preserve verified historical QA, labelled historical.
- Do not convert roadmap items into implementation claims.
- Do not update dependency versions from memory.
- Use official current sources when refreshing public dependency facts.
- Do not combine all documents into one broad rewrite.

### Acceptance criteria

- [x] Device-state terminology is consistent or explicitly mapped.
- [x] Commands align with README and CI where the documents prescribe repository checks.
- [x] Active/disabled/experimental status aligns with code and flags.
- [x] Historical VPS claims are not presented as current.
- [x] Delivery and `Delivered` semantics are consistent.
- [x] Storage and erase limitations remain accurate.
- [x] Signal and C4 gates remain explicit.
- [x] Links and referenced paths were reviewed during the file passes.
- [x] Each document retains a distinct role.

### Completion evidence

- Eighteen documents were audited and updated through separate documentation-only commits.
- Every write was followed by a one-commit, one-file diff check.
- The final documentation-pass HEAD before this queue update was `d2a38a25f24e6ea2f7c674d9f51c9daa6a77e8e0`.
- No runtime source, test source, Gradle configuration, relay implementation, deployment example, or QA script was changed by TQ-004.
- Android, relay, instrumented, and release tests were not run for the documentation-only passes and are not claimed as passed.

### Remaining boundary

TQ-004 reconciled documentation claims; it did not establish a fresh executable, release, infrastructure, dependency, or security-assurance baseline. Those remain assigned to TQ-005 through TQ-008 and the blocked security program.

---

## TQ-005 — Rebuild a trusted development environment

**Priority:** P1
**Status:** DONE (2026-09-29) — JDK 17, Android SDK 34 and Node.js installed on drive D; two physical test phones available. See `CURRENT_PROJECT_STATE.md` section 9.
**Type:** Environment/provisioning
**Security-sensitive:** Yes

### Blocker

No desktop development machine is currently available.

### Objective

Create a fresh environment from GitHub without relying on lost local state.

### Minimum environment

- trusted desktop/laptop;
- Git;
- JDK 17;
- Android SDK Platform 34 and required tools;
- Android emulator API 34;
- ADB and optional physical-device connection;
- Node.js compatible with project and CI;
- private GitHub access.

### Procedure

1. Provision and update the OS.
2. Enable full-disk encryption and user authentication.
3. Install tools from trusted sources.
4. Clone `77Skala77/Messenger`.
5. Verify remote, branch, and HEAD.
6. Create `local.properties` locally only.
7. Confirm ignored files and no secret leakage.
8. Record exact tool versions.
9. Run TQ-006.
10. Do not restore unverified old caches, keys, signing files, or build outputs.

### Acceptance criteria

- [ ] Fresh clone matches GitHub.
- [ ] Working tree is clean.
- [ ] Tool versions are recorded.
- [ ] SDK/emulator and Node are usable.
- [ ] No secrets or machine paths are committed.
- [ ] Setup instructions are updated if necessary.

---

## TQ-006 — Re-establish the executable verification baseline

**Priority:** P1
**Status:** PARTLY DONE (2026-09-30) — the full baseline passes on the development machine:
build, lint, all unit tests, relay tests, and instrumented `:core:signal-protocol`,
`:core:storage`, `:core:ui`, `:core:transport` on two physical phones (evidence:
`PHASE_1E_PREP.md`, "Step 1f evidence"). Not yet done: a run from a clean clone, and a CI run
(the workflow result is unverified from this machine).
**Type:** Build/test/QA
**Security-sensitive:** Yes

### Objective

Produce fresh evidence from a clean clone instead of historical reports.

### Required automated baseline

```bash
cd android-app
./gradlew :app:assembleDebug
./gradlew :app:lintDebug :core:signal-protocol:testDebugUnitTest :core:signalstore:test test
node --test server/relay/test/*.test.mjs
```

With a compatible emulator/device:

```bash
./gradlew :core:ui:connectedDebugAndroidTest   :core:storage:connectedDebugAndroidTest   :core:transport:connectedDebugAndroidTest
```

### Acceptance criteria

- [ ] Every command has an exact pass/fail result.
- [ ] Failures have exact output and a separate fix task.
- [ ] Instrumented checks identify device/emulator and API level.
- [ ] No skipped check is called passed.
- [ ] Current state reflects new evidence.
- [ ] Handoff records the next step.
- [ ] Deterministic baseline tests do not require a public VPS.

---

## TQ-007 — Establish the release-variant baseline

**Priority:** P1
**Status:** PARTLY DONE (2026-09-30) — `assembleRelease` (R8) succeeds; unsigned APK 357.5 MB, 4 ABIs; libsignal keep rules verified at runtime with `qaMinified`. Open: signing, `lintRelease`, manifest/permission audit.
**Type:** Release/build audit
**Security-sensitive:** Yes

### Objective

Determine actual release-build state without claiming production readiness.

### Required checks

```bash
cd android-app
./gradlew :app:assembleRelease :app:lintRelease
```

Inspect:

- signing state;
- R8/resource shrinking;
- cleartext policy and HTTP rejection;
- absence of debug-only controls;
- manifest merge;
- permissions and exported components;
- APK contents and ABIs;
- accidental secrets, paths, fixtures, or endpoints;
- package/version identity;
- upgrade limitations.

### Acceptance criteria

- [ ] Release build and lint results are recorded.
- [ ] Signing state is explicit.
- [ ] Unsigned/test-signed artifacts are not called production releases.
- [ ] Cleartext remains denied.
- [ ] Disabled flags remain disabled.
- [ ] Production release remains blocked.
- [ ] Gaps become separate tasks.

---

## TQ-008 — Add dependency verification, locking, and SBOM workflow

**Priority:** P2
**Status:** READY AFTER TQ-006
**Type:** Build/supply-chain hardening
**Security-sensitive:** Yes

### Objective

Implement remaining dependency controls as separate reviewed steps, not a broad upgrade.

### Required split

1. Gradle dependency verification or locking.
2. SBOM format and generation.
3. Transitive dependency and native-artifact inventory.
4. CVE analysis workflow.
5. License inventory and review workflow.
6. Reproducible-build assessment.
7. Signing-key custody and release procedure.

### Acceptance criteria

- [ ] No incidental dependency upgrade is mixed in.
- [ ] Generated metadata is reviewable and deterministic where possible.
- [ ] CI verifies chosen controls.
- [ ] Signing material remains outside Git.
- [ ] Automated and human/legal responsibilities are explicit.
- [ ] Signal licensing remains a separate gate.

---

## TQ-009 — Update outdated dependencies and AGP (lint warnings)

**Priority:** P2
**Status:** DONE (2026-09-30) — see "Result" below. Remaining library updates (compileSdk 37, AGP 9,
Kotlin above 2.2.20, targetSdk) are deliberately not part of this task and need their own decision.
**Type:** Build/dependency maintenance
**Security-sensitive:** Yes (build toolchain and packaging; do not mix with protocol work)

### Context

`./gradlew :app:lintDebug` on 2026-09-29 reported 0 errors and 51 warnings:

| Count | Lint id | Meaning |
| --- | --- | --- |
| 43 | `GradleDependency` | Newer library versions exist (e.g. Compose BOM `2024.06.00` → `2026.09.00`, activity-compose `1.9.0` → `1.13.0`, appcompat `1.7.0` → `1.8.0`, core-ktx `1.13.1` → `1.19.1`). The same library is counted once per build configuration. |
| 6 | `AndroidGradlePluginVersion` | AGP `8.5.2` → `9.4.1` available. |
| 1 | `InsecureBaseConfiguration` | Debug-only cleartext HTTP in `app/src/debug/res/xml/network_security_config.xml`. **Intentional (debug/QA relay); do not "fix".** Release and `main` deny cleartext. |
| 1 | `UnusedAttribute` | `localeConfig` applies only on API 33+; minSdk is 31. Harmless. |

Version numbers are a 2026-09-29 snapshot; re-check before acting.

Also found 2026-09-30: R8 bundled with AGP 8.5.2 prints "An error occurred when parsing kotlin
metadata" warnings during `assembleRelease` because the app modules use Kotlin 2.2 (R8 predates
it). The minified release build still succeeds and the libsignal keep rules apply, but R8 cannot
use Kotlin metadata for those classes; the AGP upgrade in this task should remove the warnings,
so re-inspect the release APK and mapping afterwards.

Also found 2026-09-30: lint bundled with AGP 8.5.2 prints "Module was compiled with an
incompatible version of Kotlin" for the Kotlin 2.2 modules, i.e. lint's Kotlin analysis cannot
read their metadata and is degraded (it still reports 0 errors). The AGP upgrade in this task is
expected to fix that; verify lint output is clean afterwards. Count on 2026-09-30: 53 warnings
(45 `GradleDependency`, 6 AGP, 1 intentional debug cleartext, 1 `localeConfig`). After step 1f:
54 — plus 1 `ChromeOsAbiSupport` from the debug-only `abiFilters += "arm64-v8a"` in
`app/build.gradle.kts` (intentional: smaller debug APK for the phones; release keeps 4 ABIs).

### Result (2026-09-30, DEC-022)

- AGP 8.5.2 -> 8.13.2, Gradle 8.7 -> 8.14.3, compileSdk 34 -> 36 (targetSdk 34 kept), Compose BOM
  2025.10.01, activity-compose 1.13.0, appcompat 1.8.0, core-ktx 1.17.0, lifecycle 2.10.0,
  navigation-compose 2.10.2, WorkManager 2.12.0, Room 2.8.5 (schema 8.json unchanged), AndroidX test
  libraries. AGP 9.x not adopted; Kotlin 2.2.20, coroutines 1.10.2 and libsignal stay pinned.
- Lint: 0 errors; warnings 54 -> 25 in total: GradleDependency 4 (Compose BOM 2026.09, core-ktx 1.19,
  lifecycle 2.11 need compileSdk 37), NewerVersionAvailable 6 (Kotlin, coroutines: pinned),
  AGP 2 (9.x), OldTargetApi 1 (targetSdk 34: a behavior change, not part of this task), plus the
  intentional debug cleartext, ChromeOsAbiSupport (debug arm64 only) and localeConfig. No
  Kotlin-metadata problems in lint; R8 Kotlin-metadata warnings 14 -> 0.
- Also recorded: AGP 8.11.1 crashes lint on Kotlin 2.2 (rejected). AGP 8.x needs about 2 GB free RAM
  on this machine; close games and other heavy programs before building.
- Verified on the new toolchain: `:app:assembleDebug`, `:app:assembleRelease` (R8), all unit tests
  (120 runs), `:app:lintDebug`, and on SM-S928B and 24048RN6CG: storage 20/20 (includes
  migrations), ui 16/16, signal-protocol 46/46, transport 5/5. The owner checked the debug build
  by hand on both phones (screens, language switch, existing contacts, messages to "Delivered",
  QR scanner).
- Not run: the `qaMinified` build on the new toolchain (the release R8 build was checked instead).

### Objective

Reduce the 50 actionable warnings (the intentional debug cleartext one stays) through small,
separately reviewable upgrade steps, each verified by a real build and tests.

### Constraints

- Upgrade in separate commits: (1) AndroidX libraries that do not raise `compileSdk`,
  (2) Compose BOM (newer Compose may require `compileSdk` 35), (3) AGP + Gradle + KGP
  compatibility (`docs/LIBSIGNAL_TOOLCHAIN_REVIEW.md` records the KGP 2.2.20 support range of
  AGP 7.3.1–8.11.1; AGP 9.x is outside it), (4) optionally KAPT→KSP for Room.
- Do not mix with cryptographic or protocol changes. libsignal stays pinned at `0.94.1`.
- After every step run the baseline: `:app:assembleDebug`, `:app:lintDebug`, all unit tests,
  storage and signal-protocol instrumented tests on a device, relay tests, and — once an app
  module depends on libsignal — the minified release build.
- Explicitly decide, and record in `DECISIONS.md`, whether to adopt AGP 9.x or stay on 8.x.
- Keep the intentional debug-only cleartext setting; add a lint baseline/suppression with an
  explanatory comment only if it is agreed to silence that one warning.
- Mind the 3.9 GB RAM development machine (see `LIBSIGNAL_TOOLCHAIN_REVIEW.md`).

### Acceptance criteria

- [x] Recorded build/test evidence for the update. One commit at the end of the task, by the
  owner's 2026-09-30 rule, instead of one commit per step.
- [x] `lintDebug` reports 0 errors; the remaining warnings are listed above.
- [x] compileSdk 36 (targetSdk unchanged) is explained in DEC-022 and tested on two devices.
- [x] No behavior change to crypto, storage schema (`8.json` unchanged), wire formats, or the relay.
- [x] Documentation (`DEPENDENCY_REVIEW.md`, `LIBSIGNAL_TOOLCHAIN_REVIEW.md`,
  `KNOWN_LIMITATIONS.md`) is updated.

---

# Blocked security program

These tasks require explicit decisions and remain isolated from normal chats.

## TQ-100 — Decide whether official libsignal external use is acceptable

**Priority:** P1
**Status:** DONE (2026-09-27) — owner accepted official libsignal and AGPL-3.0 (REWRITE_PLAN decision 2). AGPL notice obligations remain open before any distribution.
**Blocker:** Qualified legal/licensing review and project-owner acceptance of unsupported-external-use risk.

### Acceptance criteria

- [ ] Legal conclusion documented.
- [ ] Distribution obligations understood.
- [ ] Maintenance/support risk accepted or rejected.
- [ ] Decision added to `DECISIONS.md`.
- [ ] No runtime activation occurs.

---

## TQ-101 — Plan a controlled Android/Kotlin toolchain migration

**Priority:** P1
**Status:** DONE (2026-09-29) — Kotlin 2.2.20 and related upgrades (REWRITE_PLAN step 1b), verified by real builds. AGP upgrade remains in TQ-009.
**Blockers:** TQ-005, TQ-006, and direction from TQ-100.

### Objective

Plan Kotlin, AGP, Compose, Room/KAPT or KSP, Coroutines, WorkManager, lint, CI, instrumented tests, and release artifacts as a controlled migration.

### Acceptance criteria

- [ ] Current and target versions use official sources.
- [ ] Migration is split into reviewable steps.
- [ ] Rollback and compatibility plan exists.
- [ ] Regression matrix is defined.
- [ ] Signal runtime activation is excluded.

---

## TQ-102 — Map official protocol records into disabled encrypted stores

**Priority:** P1
**Status:** DONE (2026-09-29) — `EncryptedSignalProtocolStore` over the encrypted Room records (step 1d, DEC-018).
**Blockers:** TQ-100, TQ-101, approved record-lifecycle design, and development environment.

### Scope

- identity;
- session;
- prekey;
- signed prekey;
- Kyber prekey;
- transaction, restart, failure, deletion, and identity-change behavior;
- encrypted-at-rest verification;
- no in-memory runtime store;
- no normal-chat activation.

A separate implementation plan is required after blockers clear.

---

## TQ-103 — Design versioned PQXDH/prekey invitation semantics

**Priority:** P1
**Status:** DONE (2026-09-30) — invitation v2 (DEC-019, `../PROTOCOL_V2.md`).
**Blockers:** TQ-100, reviewed protocol design, identity/device decisions.

### Required decisions

- prekey ownership and rotation;
- signature verification;
- one-time prekey consumption;
- Kyber/PQ key lifecycle;
- identity, queue, and device binding;
- replay/duplicate import;
- v1/v2 separation;
- migration/rollback;
- malformed and oversized input.

Do not reinterpret `INVITE_V2_SIGNAL_PQXDH_EXPERIMENTAL` as a verified bundle.

---

## TQ-104 — Define per-device session and revocation semantics

**Priority:** P1
**Status:** BLOCKED — Phase 4 of REWRITE_PLAN (multi-device and revocation). v2 is single-device per contact until then.
**Blockers:** TQ-003 and reviewed Signal design.

### Objective

Specify one independently verified session per approved device, fan-out, key change, approval, revocation, receipts, synchronization, and failure behavior.

Do not silently fan out from the current primary-device path.

---

## TQ-105 — Isolated Signal-backed test conversations

**Priority:** P1
**Status:** DONE (2026-09-30) — v2 is the only chat path in debug builds (isolated test conversations); release has no chat until TQ-106 (DEC-021).
**Blockers:** TQ-100 through TQ-104, interoperability tests, migration/rollback design, independent-review plan.

### Objective

Enable only new isolated test conversations behind an explicit feature flag.

This cannot activate existing normal chats or justify real sensitive use.

---

## TQ-106 — Independent cryptographic and application security audit

**Priority:** P1
**Status:** BLOCKED — this is Phase 5 of `REWRITE_PLAN.md`. Until it passes, release builds
have no chat (`enable_v2_protocol = false`, DEC-021).
**Blockers:** Phases 2–4, funding, and a qualified reviewer.

### Candidate scope (updated 2026-09-30 for protocol v2)

- the libsignal store adapter (identity trust, one-time pre-key and Kyber handling);
- invitation v2 (commitment, sealing, expiry), the request/TOFU flow, safety numbers;
- the v2 pipeline: parsing, replay and dedupe, receipts, transaction atomicity, relay deletion
  ordering;
- Keystore, vault, Room, migrations (including `MIGRATION_7_8`), backup, reset;
- relay and metadata exposure;
- dependencies (libsignal, AGPL obligations), build, release gating, R8 keep rules.
- Scope document: `../AUDIT_SCOPE.md`; checklist: `../AUDIT_CHECKLIST.md`.

---

## TQ-107 — Production relay and operational-security design

**Priority:** P2
**Status:** BLOCKED
**Blockers:** Deployment decision, threat review, operations ownership, current infrastructure access.

### Required areas

- HTTPS and certificate lifecycle;
- authentication and abuse controls;
- rate/resource limits;
- minimal logging and retention;
- monitoring and incident response;
- deployment, updates, secrets, backup, and recovery;
- privacy and metadata limits;
- infrastructure ownership and access control.

Historical VPS success is not production evidence.

---

# Deferred and not queued

These roadmap directions require a new decision and scoped design before entering the active queue:

- post-quantum ML-KEM hybrid handshake beyond gated Signal migration;
- MLS groups;
- sealed sender;
- padding and batching;
- opaque/single-use queues;
- relay chaining;
- Tor, P2P, or local transports;
- attachments and media;
- app lock and biometric unlock;
- encrypted backup;
- secure data-destruction design;
- synchronized cryptographic revocation;
- Paranoia C4 implementation — moot: `PARANOIA_C4_AEAD` was removed entirely from the
  codebase 2026-09-27 (Phase 0 of `REWRITE_PLAN.md`), not merely left disabled.

Do not open these as implementation tasks merely because they appear in the roadmap.

# Done retention

TQ-001 is complete outside GitHub; TQ-002 integrated the reviewed package (DONE, see above). This record is retained for history.

The workflow drafts are still not repository changes and do not complete TQ-002.

TQ-004 is complete. Its compact evidence is retained in this queue while Git history remains authoritative for the eighteen individual document commits.

For later completed tasks, retain a compact entry only until its commit or pull request is recorded, affected documentation is updated, and the next dependency is unblocked. Then rely on Git history.

# Task template

```markdown
## TQ-xxx — Title

**Priority:** P0/P1/P2/P3
**Status:** READY / READY AFTER / IN PROGRESS / BLOCKED / DEFERRED
**Type:** Documentation / audit / runtime / storage / transport / build / release / security
**Security-sensitive:** Yes/No
**Issue/PR:** link when available

### Objective

One concrete outcome.

### Evidence and context

Current repository facts supporting the task.

### Scope

Files or subsystems that may change.

### Out of scope

Explicit exclusions.

### Prerequisites and blockers

What must be true before work starts.

### Acceptance criteria

- [ ] Observable repository result.
- [ ] Required checks.
- [ ] Documentation consistency.
- [ ] Security, compatibility, migration, and rollback impact.
- [ ] Commit/push state reported.

### Verification

Exact commands or procedures.

### Completion evidence

Commit:
Checks:
Remaining risks:
```
