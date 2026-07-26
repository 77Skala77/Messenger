# Handoff — Secure Messenger MVP

This file transfers current unfinished work between Codex or ChatGPT sessions.

It is intentionally short and replaceable. It is not the source of truth for architecture, implementation status, security guarantees, roadmap, or completed-work history.

## Read first

1. [`../../AGENTS.md`](../../AGENTS.md)
2. [`README.md`](README.md)
3. [`CURRENT_PROJECT_STATE.md`](CURRENT_PROJECT_STATE.md)
4. [`TASK_QUEUE.md`](TASK_QUEUE.md)
5. The checklist relevant to the task
6. [`DECISIONS.md`](DECISIONS.md) when architecture or security rationale matters

Verify mutable claims against the current GitHub repository.

## Source-of-truth warning

- Repository: `https://github.com/77Skala77/Messenger`
- Authoritative recoverable branch: `main`
- Last verified GitHub HEAD: `c974e460b827022a841e374a95cb1ed736b2c25b`
- HEAD message: `Add Codex workflow guardrails`
- The previous local clone and unpushed local commits are unavailable.
- Historical chat extracts and reports about lost local commits are context only.
- A file, commit, test, or implementation absent from current GitHub must not be treated as part of the project.

## Current handoff

**Date:** 2026-07-26

**Workstream:** Workflow documentation reconstruction

**Status:** Cross-document audit completed outside GitHub; repository integration not started

**Repository changes:** None

**Commit created:** No

**Push performed:** No

### Completed outside the repository

A consistent ten-file workflow package has been prepared:

- revised `android-app/AGENTS.md`;
- revised `android-app/docs/ai-workflow/README.md`;
- new `CURRENT_PROJECT_STATE.md`;
- revised `HANDOFF.md`;
- revised `PROMPTS_FOR_CODEX.md`;
- revised `SECURITY_CHECKLIST.md`;
- revised `TESTING_CHECKLIST.md`;
- revised `RELEASE_CHECKLIST.md`;
- new `DECISIONS.md`;
- new `TASK_QUEUE.md`.

TQ-001, the cross-document consistency review, is complete outside GitHub.

The review corrected:

- the workflow README so it lists all ten files and their distinct roles;
- stale handoff steps that referred to already completed file audits;
- stale current-state text that treated `DECISIONS.md` and `TASK_QUEUE.md` as not yet prepared;
- inconsistent workflow status vocabulary;
- device-state wording that could be mistaken for verified persisted identifiers;
- unsupported future-feature entries that were not grounded in current GitHub;
- obsolete conditional references to files that are now part of the package;
- trailing whitespace that would fail `git diff --check`.

These files remain drafts until explicitly written to GitHub and the resulting commit is verified.

## Current repository boundaries

- The project is an MVP and is not production-ready.
- Normal chats use the prototype `SimplifiedSessionManager` and `STANDARD_AEAD`.
- `SignalStoreFeatureFlags.SIGNAL_BACKED_SESSIONS_ENABLED` is `false`.
- `OfficialSignalDoubleRatchetGate.MIGRATION_ENABLED` is `false`.
- Signal/PQXDH/libsignal/SignalStore work is disabled or experimental.
- `PARANOIA_C4_AEAD` is disabled.
- The relay is untrusted.
- Historical VPS QA does not prove current endpoint availability or configuration.
- Lost local commits after GitHub HEAD `c974e46` are not current implementation evidence.
- Exact runtime and persisted device-state identifiers still require TQ-003 after workflow integration.

For fuller status, use `CURRENT_PROJECT_STATE.md` and verify it against current GitHub.

## Current operational constraints

- The previous laptop was formatted and sold.
- The previous local repository clone is unavailable.
- Only the Android phone is currently available.
- Local Gradle, Android SDK, emulator, ADB, release-build, and VPS verification cannot currently be assumed.
- GitHub is the sole known recoverable project baseline.

Skipped local verification must be reported as skipped, never as passed.

## Exact next step

TQ-002 — add the approved workflow package to GitHub.

This step requires explicit user approval because writing through GitHub creates repository commits.

Before any write:

1. Re-fetch `main` HEAD.
2. Re-fetch every existing target file and blob SHA.
3. Confirm no target changed since the audit.
4. Confirm the exact ten-file package.
5. State the intended commit scope and message.
6. Obtain explicit approval for the repository write.

During integration:

- change documentation files only;
- do not alter code, configuration, tests, schemas, CI, relay, manifests, resources, or feature flags;
- verify every written file by re-fetching it;
- report the resulting commit hash and final repository state;
- do not perform any additional unrelated commit or push action.

## How to update this file

Replace this handoff after meaningful unfinished work. Do not append an unlimited session diary.

Use:

```text
Date:
Workstream:
Status:
Repository branch and verified HEAD:
Task:
Completed:
Files changed or drafted:
Checks run:
Checks not run and why:
Security impact:
Remaining risks or blockers:
Commit created:
Push performed:
Exact next step:
```

Move stable facts to `CURRENT_PROJECT_STATE.md`, durable rationale to `DECISIONS.md`, and future work to `TASK_QUEUE.md`.
