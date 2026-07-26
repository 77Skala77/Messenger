# Handoff — Secure Messenger MVP

This file is a short mutable transfer of unfinished session work. It is not a source of truth for implementation status or architecture.

## Read first

1. [`../../AGENTS.md`](../../AGENTS.md)
2. [`README.md`](README.md)
3. [`CURRENT_PROJECT_STATE.md`](CURRENT_PROJECT_STATE.md)
4. [`DEVICE_TRUST_POLICY.md`](DEVICE_TRUST_POLICY.md)
5. [`TASK_QUEUE.md`](TASK_QUEUE.md)
6. [`DECISIONS.md`](DECISIONS.md)
7. The checklist relevant to the task

## Source-of-truth warning

- Repository: `https://github.com/77Skala77/Messenger`
- Authoritative branch: `main`
- Last verified parent HEAD before this documentation update: `abfa1475b24fefd0b39860d6e0a42005f0fa2910`
- Last executable/configuration baseline: `c974e460b827022a841e374a95cb1ed736b2c25b`
- Read the current HEAD from GitHub; this file does not self-reference its own commit.
- The previous local clone and unpushed commits are unavailable.
- Historical chat reports are context only, not repository evidence.

## Current handoff

**Date:** 2026-07-26

**Workstream:** Device-state audit and accepted trust-policy recording

**Status:** TQ-003 audit completed; warning-only trust-on-first-use policy approved and recorded in a focused decision file; broader documentation reconciliation remains pending

**Runtime changes:** None

**Room/schema changes:** None

**Build/tests:** Not run; this change is documentation-only and no desktop Android environment is available

### Completed

- Integrated the original ten-file workflow package into `main` at `abfa1475b24fefd0b39860d6e0a42005f0fa2910`.
- Audited contact/device state types, Room persistence, migration 1 → 2, planner behavior, UI labels, and existing tests.
- Verified separate persisted models:
  - `VerificationStatus`: `VERIFIED`, `UNVERIFIED`, `KEY_CHANGED`, `UNKNOWN`;
  - `DeviceApprovalStatus`: `PRIMARY_UNVERIFIED`, `VERIFIED`, `PENDING_APPROVAL`, `KEY_CHANGED`, `REVOKED`.
- Project owner approved warning-only trust on first use for reciprocal-paired `UNVERIFIED` / `PRIMARY_UNVERIFIED`.
- Recorded that decision in [`DEVICE_TRUST_POLICY.md`](DEVICE_TRUST_POLICY.md) and indexed it in the workflow README.

### Important current boundary

- `UNVERIFIED` / `PRIMARY_UNVERIFIED` is allowed for initial remote communication only with a clear identity-not-verified warning.
- It must not be described as verified or safe for real sensitive communication.
- `KEY_CHANGED`, `PENDING_APPROVAL`, `REVOKED`, and missing primary state block delivery.
- `UNKNOWN`, malformed, unsupported, or invalid persisted trust state is required to fail closed.
- Current runtime/test evidence for every unknown/invalid case is incomplete; a focused code and regression-test task remains required.
- Room stores exact uppercase enum names; do not rename without migration and tests.
- Older conflicting wording in `AGENTS.md`, `DECISIONS.md`, `CURRENT_PROJECT_STATE.md`, and checklists has not yet been fully reconciled; the focused accepted policy file takes precedence for this issue until TQ-004 updates those documents.

## Exact next step

TQ-004 — audit and reconcile non-workflow and workflow project documentation one file at a time, beginning with `android-app/README.md`, then update the central workflow documents without overstating runtime hardening.

That pass must replace shorthand such as `pending`, `verified`, `revoked`, and `key_changed` with precise type-qualified terminology where technical identifiers are intended, while preserving localized UI labels where screen text is being described.

## Operational constraints

- Only the Android phone is currently available.
- Local Gradle, SDK, emulator, ADB, release-build, and VPS verification cannot be assumed.
- Skipped verification must be reported as skipped, never passed.
- Do not commit or push future changes without explicit user approval.

## Replacement rule

Replace this handoff when the workstream changes. Stable facts belong in `CURRENT_PROJECT_STATE.md`, durable rationale in `DECISIONS.md`, and planned work in `TASK_QUEUE.md`.
