# Security Checklist — Secure Messenger MVP

Use this checklist before finishing any security-sensitive change.

## 1. Project status and claims

- [ ] The change does not claim production-grade security.
- [ ] The change preserves the MVP warning.
- [ ] Disabled experimental modules remain disabled unless explicitly approved.
- [ ] The running chat path is not silently switched to experimental Signal/libsignal/PQXDH/SignalStore code.
- [ ] PARANOIA_C4_AEAD remains disabled unless explicitly approved and reviewed.

## 2. Cryptography

- [ ] No cryptographic primitive was weakened.
- [ ] Secure randomness was not replaced with ordinary randomness.
- [ ] STANDARD_AEAD behavior was not weakened.
- [ ] AES-GCM nonce allocation remains durable and safe.
- [ ] Counter persistence survives restart where required.
- [ ] HKDF-derived per-message key behavior was not weakened.
- [ ] No plaintext fallback was added.
- [ ] No debug bypass was added to crypto/session code.

## 3. Keys and secrets

- [ ] Private keys are never logged.
- [ ] Session keys are never logged.
- [ ] Shared secrets are never logged.
- [ ] Authentication tokens are never logged.
- [ ] QR pairing secrets are never logged.
- [ ] Sensitive payloads are not exposed in copied diagnostics.
- [ ] Local vault / Keystore behavior was not weakened.

## 4. Relay trust model

- [ ] Relay remains untrusted.
- [ ] Relay cannot read plaintext.
- [ ] Relay cannot forge valid accepted messages.
- [ ] Relay cannot bypass device trust checks.
- [ ] Relay cannot bypass replay protection.
- [ ] Relay unavailability does not corrupt local state.
- [ ] Relay send/fetch/delete failures are handled safely.
- [ ] Relay acceptance is not displayed as true delivery.
- [ ] Delivery only means authenticated receiver acceptance and receipt.

## 5. Replay and envelope safety

- [ ] Incoming envelopes are authenticated before acceptance.
- [ ] Malformed envelopes are rejected safely.
- [ ] Duplicate/replayed messages are suppressed or handled safely.
- [ ] Replay markers are durably recorded before relay deletion where required.
- [ ] Tampered messages are rejected.
- [ ] Message ordering assumptions are not trusted blindly.

## 6. Device trust

- [ ] Pending devices cannot receive messages.
- [ ] Revoked devices cannot receive messages.
- [ ] key_changed devices are blocked until explicitly handled.
- [ ] Missing primary device state blocks sending.
- [ ] No silent fan-out to secondary devices was added.
- [ ] Pairing requires explicit confirmation.
- [ ] Contact verification and key-change warnings were not weakened.

## 7. Storage and persistence

- [ ] Room does not gain plaintext message columns.
- [ ] Profile/session state encryption was not weakened.
- [ ] Migrations preserve security-critical fields.
- [ ] Migration failure fails safely.
- [ ] Retry state survives restart where expected.
- [ ] Session counters survive restart where expected.
- [ ] Local metadata exposure was not increased without documentation.

## 8. Diagnostics and reports

- [ ] Logs are safe to share.
- [ ] Copied reports use short queue fingerprints rather than full identifiers when possible.
- [ ] Diagnostics do not expose plaintext, keys, tokens, QR payloads, or sensitive contact data.
- [ ] Debug-only controls remain clearly debug-only.
- [ ] Reset controls are not represented as secure erase.

## 9. Tests and verification

- [ ] Relevant unit tests were run.
- [ ] Relevant integration tests were run where possible.
- [ ] Build was run where possible.
- [ ] Failing tests were not ignored.
- [ ] Tests were not weakened to pass.
- [ ] Any skipped verification is documented with a reason.

## 10. Final security questions

Before finishing, answer:

1. Did this change touch security-sensitive code?
2. What security property could be affected?
3. What tests/checks support the change?
4. What remains unverified?
5. What should a human review?
