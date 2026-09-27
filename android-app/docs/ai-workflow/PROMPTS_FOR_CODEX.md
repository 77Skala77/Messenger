# Prompts for Codex — Secure Messenger MVP

Use these templates for Codex work in `77Skala77/Messenger`.

The templates reduce repeated prompt text. They do not override:

- `android-app/AGENTS.md`;
- current repository evidence;
- current feature flags and tests;
- explicit user instructions;
- the checklist relevant to the task.

Examples below assume Codex starts from the repository root. When already inside `android-app`, remove the `android-app/` path prefix.

## Common rules

For every task:

- Treat the current checked-out repository as the working state.
- Treat the GitHub default branch as the authoritative recoverable baseline.
- Do not treat historical chat extracts or reports of lost local commits as current implementation evidence.
- Do not call a feature implemented merely because an interface, DTO, placeholder, test fixture, disabled module, or document exists.
- Keep changes minimal and reviewable.
- Preserve fail-closed behavior.
- Do not expose plaintext, keys, tokens, QR payloads, raw protocol records, or unsafe diagnostics.
- Do not enable Signal, PQXDH, libsignal, or SignalStore paths unless the task explicitly requests reviewed activation work. `PARANOIA_C4_AEAD` no longer exists in the codebase (removed 2026-09-27) and must not be reintroduced.
- Do not commit or push unless the prompt explicitly requests it.
- Report what was verified and what could not be verified.

## 1. Start a session without editing

Use this to establish context before assigning a concrete task.

```text
Read:

- android-app/AGENTS.md
- android-app/docs/ai-workflow/README.md
- android-app/docs/ai-workflow/CURRENT_PROJECT_STATE.md
- android-app/docs/ai-workflow/HANDOFF.md

Do not edit files.

Verify mutable claims against the current repository.

Return:

1. Current repository branch and HEAD.
2. Active runtime and cryptographic path.
3. Disabled or experimental boundaries.
4. Current operational constraints.
5. Any contradiction between code, tests, configuration, and documentation.
6. The exact next task recorded in HANDOFF.md, if still valid.

Do not infer that lost local commits or historical chat-reported files exist.
Then wait for the concrete task.
```

## 2. Audit one documentation file without editing

Use this before approving documentation changes.

```text
Read:

- android-app/AGENTS.md
- android-app/docs/ai-workflow/README.md
- android-app/docs/ai-workflow/CURRENT_PROJECT_STATE.md
- the target file
- only the code, tests, configuration, and related documents needed to verify its claims

Task:
Audit this file without editing it:

[TARGET FILE]

Check for:

- factual errors;
- stale paths, commits, dates, endpoints, versions, and infrastructure claims;
- confusion between active, implemented, disabled, experimental, planned, historical, and unverified work;
- claims unsupported by the current repository;
- duplication with other workflow documents;
- missing security, compatibility, migration, testing, Git, or source-of-truth boundaries;
- broken or misleading links and commands;
- content that should move to CURRENT_PROJECT_STATE.md, DECISIONS.md, TASK_QUEUE.md, or HANDOFF.md.

Preserve useful current content.

Return:

1. Overall assessment.
2. Content to keep.
3. Proposed changes with a reason for each.
4. Content to remove or relocate with a reason for each.
5. Claims requiring repository verification.
6. Recommended final structure.

Do not modify files.
Do not invent missing project facts.
```

## 3. Apply approved documentation changes

Use only after the audit or when the user has explicitly approved immediate editing.

```text
Read:

- android-app/AGENTS.md
- android-app/docs/ai-workflow/README.md
- android-app/docs/ai-workflow/CURRENT_PROJECT_STATE.md
- the target file
- the approved audit findings

Task:
Apply the approved changes to:

[TARGET FILE]

Requirements:

- Keep useful and current content.
- Remove only content shown to be obsolete, incorrect, misleading, or unnecessarily duplicated.
- Do not rewrite correct text merely for style.
- Do not add claims unsupported by the current repository.
- Mark infrastructure or historical claims as unverified or historical when current evidence is absent.
- Keep the file within its assigned role.
- Update related links or indexes only when necessary.
- Do not change runtime code, build configuration, feature flags, or unrelated files.
- Do not commit or push.

Run relevant documentation checks, link/path checks, and git diff checks when available.

Return the final report required by AGENTS.md.
```

## 4. Implement one narrow repository task

```text
Read:

- android-app/AGENTS.md
- android-app/docs/ai-workflow/CURRENT_PROJECT_STATE.md
- android-app/docs/ai-workflow/HANDOFF.md
- the checklist relevant to the task

Task:
[PASTE ONE NARROW TASK]

Before editing:

1. Inspect the relevant code, tests, configuration, and documentation.
2. Restate the scope.
3. Classify the task as documentation-only, tests-only, runtime, build/release, or security-sensitive.
4. Identify security, compatibility, migration, and data-persistence impact.
5. List the files expected to change.
6. Propose a minimal plan.

Then implement only the approved scope.

Hard boundaries:

- Do not weaken cryptography, replay protection, authentication, device trust, storage, diagnostics redaction, or release network policy.
- Do not add plaintext fallback.
- Do not trust the relay.
- Do not silently change wire formats, QR payloads, identifiers, Room schema, nonce/counter formats, or persisted session records.
- Do not activate disabled experimental paths.
- Do not recreate lost local work wholesale.
- Do not touch unrelated files.
- Do not weaken tests.
- Do not commit or push.

Run focused checks first, then broader relevant checks when possible.

Return the final report required by AGENTS.md.
```

## 5. Perform a security review

```text
Read:

- android-app/AGENTS.md
- android-app/docs/ai-workflow/SECURITY_CHECKLIST.md
- android-app/docs/ai-workflow/CURRENT_PROJECT_STATE.md
- the current diff and all directly affected files

Task:
Perform a security review of the current changes.

Review at minimum:

- cryptographic primitive or parameter weakening;
- randomness and nonce/counter safety;
- KDF and AAD changes;
- plaintext fallback;
- envelope and receipt authentication;
- replay-marker ordering and duplicate handling;
- fail-open behavior;
- relay-trust assumptions;
- device-state bypasses;
- unsafe pairing or invitation acceptance;
- Room migration and persistence safety;
- Keystore and vault behavior;
- secret logging or copied-diagnostics exposure;
- debug/release boundary drift;
- accidental Signal/libsignal/PQXDH/SignalStore activation;
- reintroduction of `PARANOIA_C4_AEAD` or any home-grown cipher cascade (it was removed entirely, not gated);
- dependency, manifest, resource, JNI/ABI, and packaging changes;
- unsupported production-security or interoperability claims.

Return findings ordered by:

1. Critical
2. High
3. Medium
4. Low
5. Informational

For every finding include:

- affected file and location;
- evidence;
- security property affected;
- exploit or failure scenario;
- minimal recommended fix;
- missing test.

If no finding exists in a category, say so.
Do not edit files.
```

## 6. Verify build and tests

```text
Read:

- android-app/AGENTS.md
- android-app/docs/ai-workflow/TESTING_CHECKLIST.md
- android-app/docs/ai-workflow/CURRENT_PROJECT_STATE.md
- current GitHub Actions configuration
- the current diff

Task:
Select and run the checks relevant to the current changed-file scope.

Do not assume every task requires every command.
Explain why each selected check is relevant and why any standard check is skipped.

Possible baseline checks include:

cd android-app
./gradlew :app:assembleDebug
./gradlew :app:lintDebug :core:signal-protocol:testDebugUnitTest :core:signalstore:test test

cd android-app/server/relay
node --test

When Android device behavior is affected and a compatible environment exists:

cd android-app
./gradlew :core:ui:connectedDebugAndroidTest \
  :core:storage:connectedDebugAndroidTest \
  :core:transport:connectedDebugAndroidTest

Also run relevant diff, path, flag, secret, and documentation checks.

Return:

1. Changed-file scope.
2. Commands run.
3. Pass/fail result for each command.
4. Exact failures and likely cause.
5. Checks not run and why.
6. Security-sensitive verification gaps.
7. Recommended next action.

Do not edit files.
Do not commit or push.
```

## 7. Fix a failing build or test safely

```text
Read:

- android-app/AGENTS.md
- android-app/docs/ai-workflow/CURRENT_PROJECT_STATE.md
- the relevant testing checklist
- the failing output
- the affected code and tests

Failure:
[PASTE EXACT COMMAND AND ERROR]

Task:
Fix only the minimal verified cause of the failure.

Rules:

- Reproduce the failure when possible.
- Do not weaken, delete, skip, or bypass tests merely to pass.
- Do not remove security checks.
- Do not make broad refactors.
- Do not change dependencies, Gradle, manifests, resources, Room schema, protocol formats, or feature flags unless they are the verified cause and the user approves that scope.
- Preserve fail-closed behavior.
- Do not activate disabled experimental modules.
- Do not touch unrelated files.
- Do not commit or push.

After the fix:

1. Rerun the exact failing command.
2. Run the nearest relevant regression checks.
3. Report the root cause.
4. Report files changed.
5. Report security and compatibility impact.
6. Report remaining unverified areas.
```

## 8. Reverify current project state

Use this before updating `CURRENT_PROJECT_STATE.md`.

```text
Read:

- android-app/AGENTS.md
- android-app/docs/ai-workflow/CURRENT_PROJECT_STATE.md
- current code, feature flags, settings.gradle.kts, README files, CI, and relevant design documents

Task:
Reverify the factual current project state against the checked-out repository.

Check:

- branch and HEAD;
- module list;
- active session and crypto path;
- Signal and migration flags (`PARANOIA_C4_AEAD` no longer exists to flag-check);
- relay API and build policy;
- Room schema and migration version;
- device states and delivery semantics;
- build, test, and CI commands;
- implemented, disabled, experimental, planned, historical, and unverified capabilities;
- infrastructure claims that may no longer be current;
- files or commits mentioned in documentation but absent from the repository.

Return:

1. Confirmed current facts.
2. Facts that changed.
3. Contradictions.
4. Historical or unverified claims.
5. Exact proposed edits to CURRENT_PROJECT_STATE.md.

Do not edit files.
```

## 9. Evaluate historical or lost local work

```text
Read:

- android-app/AGENTS.md
- android-app/docs/ai-workflow/CURRENT_PROJECT_STATE.md
- the current repository
- the supplied historical extract or lost-work description

Task:
Evaluate this historical work without assuming it exists:

[PASTE HISTORICAL DESCRIPTION]

For each reported file, commit, test, or feature:

1. State whether it exists in the current repository.
2. Classify it as current, absent, superseded, still useful as an idea, or unsafe to recreate.
3. Identify prerequisites and conflicts with the current code.
4. Separate documentation work from underlying feature implementation.
5. Recommend whether to discard, redesign, reimplement narrowly, or defer it.

Do not recreate or port the work.
Do not edit files.
Do not treat historical commit messages as proof of implementation.
```

## 10. Prepare a commit without pushing

Use only when the user explicitly requests a commit.

```text
Read android-app/AGENTS.md and inspect the complete working diff.

Task:
Prepare and create one focused commit for the approved changes.

Before committing:

1. Verify the changed-file scope.
2. Confirm there are no unrelated changes.
3. Run the relevant checks or document why they cannot run.
4. Review the staged diff.
5. Check for secrets, private keys, tokens, local.properties, machine paths, generated keys, APKs, build output, temporary logs, node_modules, and other machine-specific files.
6. Confirm disabled experimental flags remain unchanged unless activation was explicitly approved.

Create a concise commit message that describes the actual change.

Do not amend, rebase, force-push, rewrite history, delete branches/tags, or push.

Return:

- commit hash;
- commit message;
- files committed;
- checks run;
- checks not run and why;
- security impact;
- branch;
- working-tree status;
- push status: not pushed.
```

## 11. Update the session handoff

```text
Read:

- android-app/AGENTS.md
- android-app/docs/ai-workflow/HANDOFF.md
- android-app/docs/ai-workflow/CURRENT_PROJECT_STATE.md
- the current branch, HEAD, diff, and task results

Task:
Replace the current handoff with a concise record of unfinished work.

Include:

- date;
- workstream;
- status;
- repository branch and verified HEAD;
- task;
- completed work;
- files changed or drafted;
- checks run;
- checks not run and why;
- security impact;
- remaining risks or blockers;
- commit created;
- push performed;
- exact next step.

Do not copy the full architecture or permanent project status into HANDOFF.md.
Move stable facts to CURRENT_PROJECT_STATE.md.
Do not append an unlimited session diary.
Do not include secrets, tokens, keys, plaintext, QR payloads, raw protocol records, or sensitive logs.
Do not commit or push.
```
