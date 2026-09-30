# Handoff — Secure Messenger MVP

This file is a short mutable transfer of unfinished session work. It is not a source of truth for implementation status or architecture.

## Read first

1. [`../../AGENTS.md`](../../AGENTS.md)
2. [`README.md`](README.md)
3. [`CURRENT_PROJECT_STATE.md`](CURRENT_PROJECT_STATE.md)
4. [`../PROTOCOL_V2.md`](../PROTOCOL_V2.md)
5. [`REWRITE_PLAN.md`](REWRITE_PLAN.md)
6. [`TASK_QUEUE.md`](TASK_QUEUE.md)
7. [`DECISIONS.md`](DECISIONS.md) (DEC-018 to DEC-021)
8. The checklist relevant to the task

## Source-of-truth warning

- Repository: `https://github.com/77Skala77/Messenger`, branch `main`.
- The owner pushes. Check `git log origin/main..main` for local commits not yet pushed.
- Historical chat reports are context only, not repository evidence.

## Current handoff

**Date:** 2026-09-30

**Workstream:** `REWRITE_PLAN.md` — Phase 1 (official libsignal), TQ-009, Phase 2 (storage)

**Status:** Phase 1 (steps 1a–1f) done; the Phase 1 exit gate is met. Evidence:
`PHASE_1E_PREP.md` ("Step 1f evidence") and `REWRITE_PLAN.md` (progress list).

**Runtime:** protocol v2 is the only chat path, in debug builds only. Release builds show a
"not for messaging" screen (`enable_v2_protocol = false`, DEC-021). The legacy prototype and
the modules `:core:crypto` and `:core:delivery` are deleted.

**Room:** schema v8 (`MIGRATION_7_8` drops the legacy tables). Legacy Keystore key and
preferences are removed once on upgrade by `LegacyReset`, with a one-time notice.

**Toolchain:** TQ-009 is done (2026-09-30, DEC-022): AGP 8.13.2, Gradle 8.14.3, compileSdk 36,
AndroidX, Compose BOM 2025.10.01, Room 2.8.5. Lint and R8 now read Kotlin 2.2 metadata cleanly.

**Phase 2 (storage hardening) is done** (2026-09-30, DEC-023): the vault key is never replaced
silently; a lost, invalidated or replaced key blocks the app with an explanation, "Check again",
and a confirmed "Delete local data and start over"; vault failures are unchecked and never become
protocol verdicts; secrets-at-rest and logging tests cover every module. Results:
`REWRITE_PLAN.md`, Phase 2.

**Phase 3 (relay hardening) is done** (2026-10-01, DEC-024): self-certifying queues with read
secrets, relay-assigned message IDs, QA-level limits, invitation v3, Room v9 (v2 test chats reset
once with a notice). Design: `../PHASE3_RELAY_DESIGN.md`; results: `REWRITE_PLAN.md`, Phase 3.

### Next step

**Model check first (owner rule, `CLAUDE.md`):** on the owner's "go", compare the active model
with the recommendation below; if it differs, warn and ask before starting.

**Phase 4 — multi-device and revocation** (`REWRITE_PLAN.md`; TQ-104): real fan-out with one
Double Ratchet session per contact device, revocation as an authenticated message, replacing the
single-device-per-contact model. This is the largest protocol change left (new identity and device
model, storage, UI for device approval, migration of contacts), so begin with a written design,
threat review and owner decisions before any code. Recommended model: **Opus 5.5 · High** (Extra is
reasonable for the design document itself). Commit policy: one commit at the end of each phase,
step or task (owner, 2026-09-30).

## Operational constraints

- Development machine: Windows, ~3.9 GB RAM, JDK 17 and Android SDK on drive D (`README.md` has
  the low-memory Gradle flags). With AGP 8.13 a release build takes 5–15 minutes. Close games and
  other heavy programs before building: with under ~1.3 GB free RAM the JVM cannot start.
- Test phones: Samsung S24 Ultra (SM-S928B) and Redmi A3x (24048RN6CG), Android 16, over USB.
  The relay runs on the laptop at `127.0.0.1:8787` and the phones reach it through
  `adb reverse tcp:8787 tcp:8787`.
- Known Windows quirk: `node src/server.mjs` exits without starting, because its main-module
  check compares `import.meta.url` with `file://` + the argv path, which never matches on
  Windows. Start the relay with a one-line wrapper that imports `createRelayServer` from
  `src/server.mjs` and calls `.listen(8787, "127.0.0.1")`. Not fixed yet.
- Instrumented Compose tests fail with `ComposeTimeoutException` if a phone's screen turns off
  during a long run (the S24 turns it off after 10 minutes): wake the screens before running
  `:core:ui:connectedDebugAndroidTest`.
- WorkManager does not run periodic work early when the job is forced with
  `cmd jobscheduler run -f`; to test background sync, wait for the scheduled run.
- Skipped verification must be reported as skipped, never passed.
- Do not commit or push without explicit owner approval.

## Replacement rule

Replace this handoff when the workstream changes. Stable facts belong in `CURRENT_PROJECT_STATE.md`, durable rationale in `DECISIONS.md`, and planned work in `TASK_QUEUE.md`.
