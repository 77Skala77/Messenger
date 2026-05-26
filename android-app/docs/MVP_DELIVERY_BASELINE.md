# MVP Delivery Baseline

## Baseline

This document records the messaging baseline established by commit
`ad9193f Verify relay delivery and persist retry state`, preserved through the
disabled Signal store spike, and extended in the LAN two-device verification phase.

The active Android chat path still uses the prototype `SimplifiedSessionManager`
with `STANDARD_AEAD`. The Signal store spike does not replace or silently migrate
existing conversations.

## Working Development Behavior

- An outgoing encrypted message is persisted locally as `PENDING` before relay send.
- Relay failures preserve `FAILED` state with durable attempt count and next retry time.
- Foreground polling and WorkManager best-effort processing retry due outgoing
  ciphertext and fetch incoming envelopes using the saved relay endpoint.
- Incoming envelopes are authenticated before encrypted local storage is committed.
- The replay marker and encrypted received record are committed atomically.
- Authenticated duplicates do not create a second displayed/plaintext message.
- A relay envelope is deleted only after successful durable acceptance, or after an
  authenticated duplicate is recognized from durable replay state.
- Delivery fails closed for a missing, pending, revoked or key-changed primary device.
- A real Android instrumented scenario sends, fetches and deletes through the local
  Node HTTP relay over `adb reverse`.
- The debug UI saves a per-device relay URL, checks `/health`, and supports a
  two-physical-device LAN relay scenario with reciprocal QR invitations.

## Not Production-Secure

- Sessions are prototype static ECDH/HKDF, not official PQXDH/Double Ratchet.
- Forward secrecy and post-compromise recovery expected of a reviewed ratchet are absent.
- WorkManager is subject to Android scheduling and power constraints; it does not
  guarantee immediate background delivery.
- Device revocation is local state only, not a synchronized cryptographic revocation system.
- The development relay observes queue IDs, message timing, sizes and network metadata.
- `PARANOIA_C4_AEAD` is disabled and contains no operational secure implementation.
- No independent cryptographic or application security audit has been completed.

## Regression Commands

```bash
./gradlew :app:assembleDebug test lintDebug
cd server/relay && node --test
```

With a connected device, a running local relay and `adb reverse tcp:8787 tcp:8787`:

```bash
./gradlew :core:ui:connectedDebugAndroidTest \
  :core:storage:connectedDebugAndroidTest \
  :core:transport:connectedDebugAndroidTest
```
