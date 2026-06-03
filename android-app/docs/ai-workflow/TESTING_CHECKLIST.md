# Testing Checklist — Secure Messenger MVP

Use this checklist for implementation tasks.

## Basic verification

- [ ] Project builds when build verification is relevant.
- [ ] Relevant unit tests pass.
- [ ] Relevant integration tests pass when available.
- [ ] Relevant Android/instrumented checks are run when device/emulator behavior is touched.
- [ ] No unrelated failing tests were introduced.
- [ ] No test was weakened just to pass.
- [ ] No test was deleted without a clear reason.

## Recommended commands

Use relevant commands from README when possible:

- ./gradlew :app:assembleDebug
- ./gradlew :app:lintDebug :core:signal-protocol:testDebugUnitTest :core:signalstore:test test

If a command cannot be run because of environment, time, SDK, emulator, or device limits, say so clearly.

## Security-sensitive test areas

When relevant, add or run tests for:

- device pairing success
- pairing rejection or malformed invitation
- pending approval blocks sending
- revoked device blocks sending
- key_changed device blocks sending
- missing primary device blocks sending
- incoming envelope authentication
- malformed envelope rejection
- duplicate/replayed message handling
- tamper rejection
- relay unavailable
- relay returns malformed payload
- retry persistence after app restart
- WorkManager best-effort sync behavior
- storage migration
- diagnostics sanitization
- copied report safety

## Delivery pipeline checks

When changing delivery code:

- [ ] Outgoing message persists before send attempt.
- [ ] attemptCount updates correctly.
- [ ] nextRetryAt updates correctly.
- [ ] Retry state survives app restart when expected.
- [ ] Due ciphertext can be resent without logging contents.
- [ ] Successful delivery clears or marks pending state correctly.
- [ ] Failed delivery does not lose message state.
- [ ] Duplicate accept does not create duplicate visible messages.
- [ ] Relay acceptance is not displayed as true delivery.
- [ ] Delivered state requires authenticated receiver acceptance and receipt.

## Incoming pipeline checks

When changing incoming receive code:

- [ ] Envelope is authenticated before acceptance.
- [ ] Replay marker is recorded durably before relay deletion where required.
- [ ] Duplicate replay is suppressed or handled safely.
- [ ] Tampered messages are rejected.
- [ ] Relay deletion failure is handled safely.
- [ ] Accepted incoming messages are stored without plaintext database columns.

## Android-specific checks

When relevant:

- [ ] App launches.
- [ ] Relevant screen opens.
- [ ] Background/foreground transition works.
- [ ] App restart preserves expected state.
- [ ] No crash appears in Logcat during tested flow.
- [ ] Permissions are handled explicitly.
- [ ] UI error states are understandable.
- [ ] Debug-only controls remain debug-only.

## Final testing report

Every task should report:

- Tests/checks run
- Tests/checks not run and why
- Observed result
- Remaining test gap
