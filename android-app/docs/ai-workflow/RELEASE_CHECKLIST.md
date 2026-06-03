# Release Checklist — Secure Messenger MVP

Use this before tagging, publishing, sharing a build, or handing the app to another tester.

## Code readiness

- [ ] Working tree reviewed.
- [ ] No unrelated files changed.
- [ ] No debug-only shortcuts left in production paths.
- [ ] No hardcoded local-only relay URL in release config unless intentionally documented.
- [ ] No secrets committed.
- [ ] No generated private keys committed.
- [ ] No temporary logs committed.
- [ ] local.properties was not committed.
- [ ] Machine-specific files were not committed.

## Build readiness

- [ ] Clean build completed or skipped with documented reason.
- [ ] Relevant tests completed or skipped with documented reason.
- [ ] App launches.
- [ ] Critical flows tested manually when possible.
- [ ] README build instructions still match reality.

## Security readiness

- [ ] SECURITY_CHECKLIST.md reviewed.
- [ ] MVP warning remains accurate.
- [ ] No production-security claim was added.
- [ ] Relay remains untrusted.
- [ ] Device trust checks remain fail-closed.
- [ ] Replay protection remains enabled.
- [ ] Pairing requires explicit confirmation.
- [ ] Diagnostics are safe to share.
- [ ] Copied reports do not expose secrets or full sensitive identifiers.
- [ ] Release builds reject HTTP relay URLs and disallow cleartext networking.
- [ ] Disabled experimental crypto/session modules remain disabled unless explicitly approved and reviewed.

## Documentation readiness

- [ ] README updated if behavior changed.
- [ ] Known limitations documented.
- [ ] Test instructions updated if needed.
- [ ] HANDOFF.md updated if work will continue later.
- [ ] Any new security tradeoff is documented.

## Final release note format

Every release/build handoff should include:

- Release/build summary
- Security-sensitive changes
- Verification performed
- Verification not performed and why
- Known limitations
- Next recommended step
