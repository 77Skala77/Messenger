# Prompts for Codex — Secure Messenger MVP

Use these prompts when working with Codex in this repository.

## 1. Start a safe implementation task

Read AGENTS.md and docs/ai-workflow/*.md first.

Task:
[PASTE TASK HERE]

Work mode:
1. Inspect relevant files.
2. Restate the task.
3. Identify security-sensitive areas.
4. Produce a small plan before editing.
5. List files you intend to modify.
6. Implement minimal changes.
7. Run relevant tests/checks.
8. Report summary, files changed, tests run, security impact, remaining risks, and next step.

Hard rules:
- Do not weaken crypto.
- Do not trust the relay.
- Do not add plaintext fallback.
- Do not bypass device trust checks.
- Do not log secrets.
- Preserve fail-closed behavior.
- Do not enable disabled experimental crypto/session boundaries unless explicitly requested.

## 2. Security review

Read AGENTS.md and docs/ai-workflow/SECURITY_CHECKLIST.md.

Perform a security review of the current changes.

Focus on:
- crypto weakening
- plaintext fallback
- relay trust assumptions
- device approval bypass
- revoked-device bypass
- key_changed bypass
- replay protection
- envelope authentication
- unsafe logging
- secret exposure
- fail-open behavior
- unsafe storage migration
- accidental activation of disabled Signal/libsignal/PQXDH/SignalStore code
- accidental activation of PARANOIA_C4_AEAD

Return:
1. Critical findings
2. High-risk findings
3. Medium-risk findings
4. Low-risk findings
5. Missing tests
6. Exact files/areas to inspect
7. Recommended minimal fixes

## 3. Build/test verification

Read AGENTS.md and docs/ai-workflow/TESTING_CHECKLIST.md.

Verify the current branch.

Run relevant build and tests for this project.

Report:
- commands run
- pass/fail result
- failures with exact error
- likely cause
- minimal fix plan
- security-sensitive impact, if any

## 4. Fix build errors safely

Read AGENTS.md first.

The build/test is failing.

Fix only the minimal cause of the failure.

Rules:
- Do not weaken tests to pass.
- Do not remove security checks.
- Do not make broad refactors.
- Do not touch unrelated files.
- Preserve fail-closed behavior.
- Do not enable disabled experimental modules.

After fixing:
- rerun the failing command
- report files changed
- report test result
- report remaining risks

## 5. End-of-session handoff

Update docs/ai-workflow/HANDOFF.md.

Include:
- date
- branch
- task
- summary
- files changed
- tests/checks run
- tests/checks not run
- security impact
- remaining risks
- next safest step

Do not include secrets, tokens, keys, plaintext messages, QR payloads, or sensitive logs.
