# MVP Delivery Baseline

## Status And Evidence Boundary

**Repository baseline reviewed:** `640e3bd0652cad9395aee8d7424b1c1ed38f59b6`

This document describes the current delivery contract visible in repository code at the
reviewed baseline. It separates:

- current runtime behavior;
- test coverage present in source;
- historical implementation and QA evidence;
- checks still required for the current HEAD.

A test file or historical QA report is not proof that the same check passed for the
reviewed commit. Exact pass, fail, or skip results must come from a current recorded run.

The active Android chat path uses the prototype `SimplifiedSessionManager` with
`STANDARD_AEAD`. Signal-backed sessions and `PARANOIA_C4_AEAD` remain disabled and do
not replace, migrate, or strengthen the active delivery path.

## Current Runtime Contract

### Outgoing persistence and relay submission

For a normal outgoing text message, the current interactive-send path:

1. serializes the text as an encrypted message payload;
2. encrypts it through the current prototype session;
3. persists the incremented session state before any relay attempt;
4. stores the encrypted message locally as `MessageStatus.PENDING`;
5. attempts delivery through `OutgoingDeliveryProcessor`.

Persisting the updated session state before network delivery is security-critical. It
reduces the risk of reusing old counter and nonce state after restart.

The relay receives an opaque encoded encrypted message and a queue identifier. A
successful relay submission changes local status to `MessageStatus.SENT`. It does not
prove that the receiving application fetched, authenticated, decrypted, or stored the
message.

On relay failure, the message is retained as `MessageStatus.FAILED` with:

- durable attempt count;
- durable next-retry timestamp;
- exponential retry delay capped by the current retry policy.

Manual synchronization may force retry of retryable `PENDING` or `FAILED` messages before
their scheduled retry time. Normal synchronization retries only messages that are due.

### Message status meanings

The delivery statuses have distinct meanings:

- `MessageStatus.PENDING` — encrypted ciphertext is stored locally and awaits a delivery
  attempt.
- `MessageStatus.FAILED` — a relay attempt failed and durable retry metadata was recorded.
- `MessageStatus.SENT` — the relay accepted the encrypted blob.
- `MessageStatus.DELIVERED` — the sender authenticated a delivery receipt confirming that
  the receiving application accepted, decrypted, and durably stored the original message.
- `MessageStatus.RECEIVED` — an incoming encrypted message was accepted and durably stored
  by the local receiving application.

`DELIVERED` is not a read receipt. It does not mean that the human opened, viewed, or read
the message. User read receipts are not implemented.

### Device targeting and identity trust

The delivery planner selects only the device whose identity matches the contact's primary
identity. It never silently fans out a single-device ciphertext to a secondary device.

The current primary-only planner permits:

- `DeviceApprovalStatus.PRIMARY_UNVERIFIED`;
- `DeviceApprovalStatus.VERIFIED`.

It blocks delivery for:

- `DeviceApprovalStatus.PENDING_APPROVAL`;
- `DeviceApprovalStatus.KEY_CHANGED`;
- `DeviceApprovalStatus.REVOKED`;
- a missing primary-device record.

The interactive-send path also explicitly blocks `VerificationStatus.KEY_CHANGED`.

After reciprocal invitation import, accepted warning-only trust-on-first-use policy allows
communication when:

- the contact is `VerificationStatus.UNVERIFIED`; and
- its matching primary device is
  `DeviceApprovalStatus.PRIMARY_UNVERIFIED`.

This supports remote onboarding but does not prove that the imported public key belongs to
the intended human. The UI must continue to show the identity-not-verified warning until
the safety number or verification QR is compared through a trusted or independent channel.

Policy requires `VerificationStatus.UNKNOWN`, malformed enum strings, unsupported values,
corrupt records, and otherwise invalid persisted trust state to fail closed. Current
runtime and regression evidence does not yet establish that guarantee for every direct
send, queued retry, restart, and migration path. That remains open hardening work.

### Incoming authentication, persistence, replay, and deletion

Incoming relay blobs are decoded and authenticated before they are accepted as messages.

For an accepted text message:

- the encrypted message record and accepted replay tuple are committed through the Room
  acceptance transaction;
- the relay blob is deleted only after durable acceptance;
- a later authenticated duplicate is suppressed using durable replay state and is also
  deleted from the relay;
- no second displayed plaintext message is created.

Malformed or unauthenticated blobs are not stored as chat messages and are not deleted by
the normal acceptance path. They may remain until explicit deletion, relay expiry, or
other relay behavior. This avoids acknowledging invalid content but permits queue-filling
denial of service.

### Encrypted delivery receipts

After a new incoming text message is authenticated and durably accepted, the receiver
attempts to queue an encrypted delivery-receipt control message.

The receipt identifies the original message and binds the relevant conversation and device
identities. It does not contain the original message plaintext, cryptographic keys, nonce
values, ciphertext body, or queue identifier.

The receipt is:

- encrypted through the current session;
- stored locally as an outgoing `PENDING` control message;
- sent through the same primary-only delivery processor;
- authenticated by the original sender before the original message becomes `DELIVERED`.

Receipt replay state is durable before receipt-relay deletion. Duplicate receipts are
suppressed. A receipt for an unknown or mismatched original message does not mark any chat
message delivered. Receipt control payloads are not displayed as chat messages.

Failure to queue or send a receipt does not reverse the receiver's durable acceptance of
the original message. The sender may therefore remain at `SENT` until an authenticated
receipt is successfully returned and processed.

### Foreground and WorkManager synchronization

Foreground synchronization and WorkManager invoke the same delivery boundaries for:

- due outgoing retry;
- incoming fetch and processing;
- immediate attempts for newly queued delivery receipts.

WorkManager is best effort. Android scheduling, battery controls, network state, process
lifetime, and device policy may delay execution. This baseline makes no timely-delivery or
always-online guarantee.

Manual synchronization remains useful for QA and immediate retry requests.

### Relay and endpoint behavior

The development relay supports:

```text
POST   /v1/messages
GET    /v1/messages/{queueId}
DELETE /v1/messages/{messageId}
GET    /health
```

The debug application can save a development relay URL and check `/health`. Debug policy
allows configured HTTP endpoints for local QA. Release policy rejects insecure HTTP relay
URLs and Android cleartext networking, but no production relay deployment has been
reviewed.

An invitation advertises its creator's receiving queue. Bidirectional communication and
QA therefore require reciprocal invitation import.

The relay remains untrusted. It can observe queue identifiers, request timing, source
network information, approximate message size, traffic frequency, and shared-relay usage.
It can delete, delay, withhold, reorder, replay, or flood blobs. Payload authentication
rejects unauthorized modification but does not prevent denial of service or traffic
analysis.

## Current Test Coverage Present In Source

Current unit-test source includes scenarios for:

- two-peer encrypted relay exchange;
- authenticated receive and deletion;
- authenticated duplicate suppression;
- tampered AAD and authentication-tag rejection;
- durable retry metadata and successful due retry;
- forced manual retry;
- retry after relay recovery without duplicate plaintext acceptance;
- primary-only targeting and device-policy block states;
- reciprocal-pairing readiness;
- delivery block reasons;
- encrypted delivery-receipt processing;
- duplicate receipt suppression;
- safe handling of an unknown receipt.

Current Room instrumented-test source includes scenarios for:

- encrypted message persistence without a plaintext message column;
- durable session counter and replay state;
- durable retry state;
- delivered status persistence;
- durable local revocation;
- deletion of current local delivery and disabled Signal-spike state.

Current transport instrumented-test source uses a real local Node HTTP relay and the
current crypto and delivery processors. It uses in-memory delivery persistence rather than
Room, does not exercise Compose UI, does not prove real WorkManager execution, and does not
complete the full delivery-receipt round trip.

When the local relay is unavailable, that transport scenario uses a JUnit assumption and
may be skipped rather than fail. Verification records must therefore confirm that it
actually executed.

Source coverage does not currently establish complete fail-closed behavior for every
`VerificationStatus.UNKNOWN`, malformed persisted enum, migration 1 to 2, restart, and
queued-retry trust-state case.

## Historical Evidence

Commit `ad9193fbe212187538c0121e8182f942de069838` introduced the original relay delivery
and durable retry-state baseline. Later repository work added LAN relay configuration,
WorkManager synchronization, diagnostics, receipts, Room persistence coverage, and
additional device-trust behavior.

Repository documents also record historical phone, emulator, retry-recovery, receipt,
restart, two-device LAN, and VPS QA. These records are useful historical context. They do
not prove that the same checks passed for the reviewed HEAD or that any historical relay
or VPS remains available, authorized, or securely configured.

## Not Production-Secure

- Sessions use prototype static ECDH/HKDF rather than reviewed PQXDH and Double Ratchet.
- Production-grade forward secrecy and post-compromise recovery are absent.
- Warning-only first contact does not protect against substitution of the initial imported
  key.
- Full fail-closed handling of invalid persisted trust state is incomplete.
- Multi-device cryptographic fan-out and synchronized protocol revocation are not
  implemented.
- WorkManager does not guarantee prompt or reliable offline delivery.
- The relay exposes metadata and can deny service.
- Debug HTTP transport is for development and QA only.
- `PARANOIA_C4_AEAD` is disabled and contains no operational secure implementation.
- Signal-backed sessions remain disabled.
- No independent cryptographic, Android, protocol, relay, privacy, or application-security
  audit has been completed.

## Regression Commands

Automated Android baseline:

```bash
cd android-app
./gradlew :app:assembleDebug
./gradlew :app:lintDebug :core:signal-protocol:testDebugUnitTest :core:signalstore:test test
```

Relay baseline:

```bash
cd android-app/server/relay
node --test
```

Instrumented baseline with a compatible emulator or device:

```bash
cd android-app
./gradlew :core:ui:connectedDebugAndroidTest \
  :core:storage:connectedDebugAndroidTest \
  :core:transport:connectedDebugAndroidTest
```

For the real local HTTP relay transport scenario, start the Node relay first and provide
the route expected by the selected device, such as:

```bash
adb reverse tcp:8787 tcp:8787
```

A verification report must record:

- exact commit SHA;
- exact commands;
- pass, fail, or skip for every command and relevant test;
- device or emulator model and API level for instrumented checks;
- confirmation that relay-dependent tests executed rather than being skipped;
- exact failure output for any unsuccessful check.

No command listed here is evidence of a pass until its result is recorded for the current
commit.
