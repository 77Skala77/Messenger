# Handoff — Secure Messenger MVP

This file is a short mutable transfer of unfinished session work. It is not a source of truth for implementation status or architecture.

## Read first

1. [`../../AGENTS.md`](../../AGENTS.md)
2. [`README.md`](README.md)
3. [`CURRENT_PROJECT_STATE.md`](CURRENT_PROJECT_STATE.md)
4. [`../PROTOCOL_V2.md`](../PROTOCOL_V2.md)
5. [`REWRITE_PLAN.md`](REWRITE_PLAN.md)
6. [`TASK_QUEUE.md`](TASK_QUEUE.md)
7. [`DECISIONS.md`](DECISIONS.md) (DEC-018 to DEC-027)
8. The checklist relevant to the task

## Source-of-truth warning

- Repository: `https://github.com/77Skala77/Messenger`, branch `main`.
- The owner pushes. Check `git log origin/main..main` for local commits not yet pushed.
- Historical chat reports are context only, not repository evidence.

## Current handoff

**Date:** 2026-10-01

**Workstream:** `REWRITE_PLAN.md` — Phases 1–3 and 5 done, Phase 4 deferred, Phase 6 (Telegram-style UI) in progress

**Status:** Phase 1 (steps 1a–1f) done; the Phase 1 exit gate is met. Evidence:
`PHASE_1E_PREP.md` ("Step 1f evidence") and `REWRITE_PLAN.md` (progress list).

**Runtime:** protocol v2 is the only chat path, in debug builds only. Release builds show a
"not for messaging" screen (`enable_v2_protocol = false`, DEC-021). The legacy prototype and
the modules `:core:crypto` and `:core:delivery` are deleted.

**Room:** schema v9 (`MIGRATION_7_8` dropped the legacy tables; `MIGRATION_8_9` reset the v2 test
conversations for relay API v3). Legacy Keystore key and preferences are removed once on upgrade by
`LegacyReset`, with a one-time notice.

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

Phase 4 (multi-device) was **deferred** by the owner (2026-10-01, DEC-025): version 1 is one device
per person; draft design with the owner's answers in `../PHASE4_MULTIDEVICE_DESIGN.md`.

**Phase 5 (security test pass) is done** (2026-10-01, DEC-026): inputs, attack matrix, findings
F1–F12, the second AI review (R1–R7, I1–I6) and results are in `../PHASE5_SECURITY_TEST_PLAN.md`.
Owner decisions: delete junk and page the relay, fix all findings, paid external audit before any
release (TQ-106), a second AI review (done), "Share invitation" plus "Copy" with a warning.

### Next step

**Model check first (owner rule, `CLAUDE.md`):** on the owner's "go", compare the active model
with the recommendation below; if it differs, warn and ask before starting.

**Phase 6 — Telegram-style UI** is in progress: step 6a (plan, the owner's decisions, `LICENSE`,
`THIRD_PARTY_NOTICES.md`) is done; see `../PHASE6_UI_PLAN.md` and DEC-027. Telegram code may be
reused per DEC-027 ("GPL v2 or later" files; headerless files at the owner's accepted legal risk);
the read-only reference copy is at `D:\Tools\reference\Telegram`. **TQ-109** (system Back button) is
done (2026-10-03, committed with TQ-110). **Step 6b is done**
(`../PHASE6_PORTING_NOTES.md`: metrics, rules and colors per piece; the reworked steps 6c–6h; almost
nothing needs Telegram code copied, our Compose code is written from the notes). The owner chose the
classic palette (green outgoing bubbles), an own light wallpaper pattern, and a dark theme with an
in-app switch (notes, section 4; theme notes, 2a).

**AI audits (2026-10-03, DEC-028):** the owner had two AI tools audit the code (not the independent
audit TQ-106). Every finding was checked against the code (`../AI_AUDITS_2026-10.md`). **TQ-110**
(the fix block: V2-01, SESS-01, STORE-04, L1, relay limits, deployment examples, CI pinning) is
**done**: all suites green on the JVM and on both phones, the owner's hand check passed (results in
`TASK_QUEUE.md`). The audits' safety-number item (H1) and the invitation upload state (L2) belong to
step 6f.

Next: **6c** (theme with the switch, type scale, avatar, badge, ticks, bubble shape,
date chip, wallpaper pattern, a preview screen). Recommended model: **Sonnet 5.5 · High** for 6c,
6e, 6f, 6h; **Opus 5.5 · High** for the Room v10 migration (6d), the verified-mark rules (6f), the
notification privacy rules (6f') and TQ-108 (6g). Commit policy: one commit round at the end of each
phase, step or task (owner, 2026-09-30).

## Operational constraints

- Development machine: Windows, ~3.9 GB RAM, JDK 17 and Android SDK on drive D (`README.md` has
  the low-memory Gradle flags). With AGP 8.13 a release build takes 5–15 minutes. Close games and
  other heavy programs before building: with under ~1.3 GB free RAM the JVM cannot start.
- Test phones: Samsung S24 Ultra (SM-S928B) and Redmi A3x (24048RN6CG), Android 16, over USB.
  The relay runs on the laptop at `127.0.0.1:8787` and the phones reach it through
  `adb reverse tcp:8787 tcp:8787`.
- Start the relay from `server/relay` with `node src/server.mjs` (fixed for Windows in Phase 3);
  set `HOST=127.0.0.1` to bind it to the laptop only.
- Instrumented Compose tests fail with `ComposeTimeoutException` if a phone's screen turns off
  during a long run (the S24 turns it off after 10 minutes): wake the screens before running
  `:core:ui:connectedDebugAndroidTest`.
- WorkManager does not run periodic work early when the job is forced with
  `cmd jobscheduler run -f`; to test background sync, wait for the scheduled run.
- Skipped verification must be reported as skipped, never passed.
- Do not commit or push without explicit owner approval.

## Replacement rule

Replace this handoff when the workstream changes. Stable facts belong in `CURRENT_PROJECT_STATE.md`, durable rationale in `DECISIONS.md`, and planned work in `TASK_QUEUE.md`.
