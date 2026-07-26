# AI Workflow — Secure Messenger MVP

This directory contains repository-local workflow rules, factual state, decisions, task planning, checklists, prompts, and handoff material for Codex work on Secure Messenger MVP.

This README is an index and reading guide. It is not the source of truth for implementation status, architecture, security guarantees, or release readiness.

## Authoritative project baseline

The current GitHub default branch is the authoritative recoverable project baseline.

During active work, the checked-out branch and its uncommitted diff are the working state. Always report whether changes are local, committed, and pushed.

Historical chat summaries, handoff notes, screenshots, and reports of lost local commits provide context only. They do not prove that a file, commit, test, configuration, or implementation exists in the current repository.

When sources disagree, use this order:

1. Current executable code, configuration, manifests, resources, dependency wiring, and feature flags.
2. Current tests, CI configuration, and reproducible command output.
3. Current repository documentation.
4. Historical handoff notes and chat extracts.

Report contradictions explicitly. Do not silently select a convenient version.

## Required starting point

Before changing repository files, read:

- [`../../AGENTS.md`](../../AGENTS.md) — mandatory repository rules, security boundaries, verification expectations, Git rules, and reporting requirements.

For audit or documentation-review tasks, report proposed changes before editing unless the user explicitly requests immediate modification.

## Recommended reading order

1. Read [`../../AGENTS.md`](../../AGENTS.md).
2. Read this index.
3. Read [`CURRENT_PROJECT_STATE.md`](CURRENT_PROJECT_STATE.md) when the task depends on current implementation, feature flags, modules, infrastructure, or known limitations.
4. Read [`HANDOFF.md`](HANDOFF.md) when continuing unfinished work; verify its mutable claims.
5. Read [`DECISIONS.md`](DECISIONS.md) when architecture, security boundaries, compatibility, or durable rationale matters.
6. Read [`TASK_QUEUE.md`](TASK_QUEUE.md) when selecting or updating concrete future work.
7. Read the checklist relevant to the task.
8. Inspect the relevant source code, tests, build configuration, CI, README files, and design documents.
9. Use [`PROMPTS_FOR_CODEX.md`](PROMPTS_FOR_CODEX.md) only when a reusable task template is useful.

Do not load every workflow document for every small task. Read only the material needed to verify the requested scope safely.

## Files

- [`../../AGENTS.md`](../../AGENTS.md) — canonical repository workflow and security rules.
- [`README.md`](README.md) — this index and reading guide.
- [`CURRENT_PROJECT_STATE.md`](CURRENT_PROJECT_STATE.md) — mutable factual snapshot verified against the current repository.
- [`DECISIONS.md`](DECISIONS.md) — durable accepted decisions, rationale, consequences, and review conditions.
- [`TASK_QUEUE.md`](TASK_QUEUE.md) — ordered concrete work, prerequisites, blockers, and acceptance criteria.
- [`HANDOFF.md`](HANDOFF.md) — short replaceable record of current unfinished work.
- [`PROMPTS_FOR_CODEX.md`](PROMPTS_FOR_CODEX.md) — reusable task templates that do not override rules, evidence, or explicit instructions.
- [`SECURITY_CHECKLIST.md`](SECURITY_CHECKLIST.md) — security review controls for cryptography, trust, storage, relay, diagnostics, compatibility, and security claims.
- [`TESTING_CHECKLIST.md`](TESTING_CHECKLIST.md) — test selection and verification guidance for documentation, code, Android, storage, delivery, relay, and release changes.
- [`RELEASE_CHECKLIST.md`](RELEASE_CHECKLIST.md) — readiness and stop conditions for tags, shared builds, tester handoffs, release variants, and distribution claims.

## Document roles

- Stable mandatory rules belong in `AGENTS.md`.
- Mutable verified facts belong in `CURRENT_PROJECT_STATE.md`.
- Durable rationale belongs in `DECISIONS.md`.
- Concrete future work belongs in `TASK_QUEUE.md`.
- Temporary unfinished-session context belongs in `HANDOFF.md`.
- Reusable wording belongs in `PROMPTS_FOR_CODEX.md`.
- Task-specific controls belong in the checklists.
- Completed-work history belongs primarily in Git commits and pull requests.

Do not duplicate the same mutable fact across several files unless a short reference is necessary for safe use.

## Scope

These rules apply only to this repository.

Do not generalize or reuse Messenger-specific security assumptions, commands, paths, feature flags, or workflow rules in unrelated repositories.

Do not represent disabled, experimental, planned, test-only, historical, or unverified work as active implementation unless current repository evidence proves it.

Do not store secrets, tokens, keys, plaintext messages, QR payloads, raw protocol records, or sensitive logs in workflow documentation.

## Maintenance

When a workflow file is added, removed, renamed, or changes purpose, update this index in the same change.

When project behavior, feature flags, security boundaries, compatibility, or release status changes, update the appropriate workflow and project documentation or report clearly why no documentation update was needed.
