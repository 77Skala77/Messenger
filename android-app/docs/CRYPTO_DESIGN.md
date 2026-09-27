# Cryptographic Design

## Non-Production Notice

Only the standard payload AEAD is executable. The current invitation/session scheme is
a deliberately limited prototype with an upgrade boundary; it is not a replacement for
Signal Protocol and is not appropriate for sensitive real-world communications.

Encryption of an unverified first exchange protects message content against parties
that do not possess the established session key. It does not prove that the imported
public key belongs to the intended human.

## Device Identity, Invitation, And Trust

The Android application generates a P-256 EC key-agreement key in Android Keystore.

The active v1 invitation payload contains:

- protocol version;
- receiving queue identifier;
- device ID;
- encoded public key.

It does not contain a private key, local profile display name, verification status, or
device-approval status. The application derives a SHA-256 fingerprint from the public
key and displays it as the safety number.

Each invitation is a receive address for its creator. A peer that must receive a reply
must publish its own invitation, so bidirectional communication requires reciprocal
invitation import.

### Separate persisted trust models

The project has two distinct trust models. They must not be described as one informal
“device state” enum.

Contact-level identity verification:

```text
VerificationStatus.VERIFIED
VerificationStatus.UNVERIFIED
VerificationStatus.KEY_CHANGED
VerificationStatus.UNKNOWN
```

Per-contact-device delivery approval:

```text
DeviceApprovalStatus.PRIMARY_UNVERIFIED
DeviceApprovalStatus.VERIFIED
DeviceApprovalStatus.PENDING_APPROVAL
DeviceApprovalStatus.KEY_CHANGED
DeviceApprovalStatus.REVOKED
```

A newly imported contact is stored as `VerificationStatus.UNVERIFIED`. Its matching
primary device is stored as `DeviceApprovalStatus.PRIMARY_UNVERIFIED`.

These two identifiers are related by the current onboarding flow, but they are not the
same type and must not be merged in code, persistence, tests, or technical documentation.

### Warning-only trust on first use

After reciprocal invitation import, the accepted MVP policy allows communication when:

- the contact is `VerificationStatus.UNVERIFIED`; and
- the matching primary device is
  `DeviceApprovalStatus.PRIMARY_UNVERIFIED`.

This is warning-only trust on first use. It supports remote onboarding when users cannot
meet in person and do not already share an independent secure channel.

The application must continue to show a clear warning that the remote identity has not
been independently verified. The state must not be called `VERIFIED` and must not be
presented as suitable for real sensitive communication.

Later safety-number or verification-QR comparison through a trusted or independent
channel may move both the contact and primary device to `VERIFIED`.

The first imported key can still be substituted if the invitation-delivery channel is
compromised. The warning-only policy is a usability decision, not protection against
first-contact impersonation.

See
[`ai-workflow/DEVICE_TRUST_POLICY.md`](ai-workflow/DEVICE_TRUST_POLICY.md)
for the accepted decision.

### Key changes, additional devices, and blocking

A different public-key fingerprint for an existing device ID produces key-changed trust
state in the normal import flow.

Current delivery remains blocked for:

- `VerificationStatus.KEY_CHANGED`;
- `DeviceApprovalStatus.PENDING_APPROVAL`;
- `DeviceApprovalStatus.KEY_CHANGED`;
- `DeviceApprovalStatus.REVOKED`;
- a missing primary-device record.

An additional device invitation is stored as
`DeviceApprovalStatus.PENDING_APPROVAL`. The current prototype retains its queue
endpoint but does not send to it automatically.

A `DeviceFanOutPlanner` boundary exists, but the current implementation intentionally
selects only the device matching the contact’s primary identity. It permits
`PRIMARY_UNVERIFIED` and `VERIFIED`, blocks pending/revoked/key-changed/missing
primary-device state, and never silently fans out to a secondary device.

Local revocation is local delivery blocking. It is not synchronized cryptographic
protocol revocation.

Policy requires `VerificationStatus.UNKNOWN`, malformed, unsupported, corrupt, and
otherwise invalid persisted trust state to fail closed. Current runtime and regression
evidence does not yet prove every unknown/invalid persistence, migration, restart, and
queued-retry case. This is an open hardening task, not an implemented guarantee.

### Trust-state persistence and compatibility

Room stores exact enum names as non-null text values:

- `contacts.verificationStatus` is written with `VerificationStatus.name` and read with
  `VerificationStatus.valueOf`;
- `contact_devices.approvalStatus` is written with `DeviceApprovalStatus.name` and read
  with `DeviceApprovalStatus.valueOf`.

The uppercase enum identifiers are therefore part of the local database compatibility
surface.

Do not rename, lowercase, merge, or reinterpret values such as:

```text
PRIMARY_UNVERIFIED
PENDING_APPROVAL
KEY_CHANGED
```

without:

- a reviewed Room migration;
- compatibility analysis;
- rollback planning;
- regression tests for existing rows;
- restart and queued-delivery tests.

The existing migration from schema v1 to v2 maps every contact verification value other
than `VERIFIED` to the new device value `PRIMARY_UNVERIFIED`. A targeted migration and
retry test remains required for old `KEY_CHANGED`, `UNKNOWN`, malformed, or unsupported
state before the migration path can be described as fully fail closed.

## Prototype Sessions

Phase 1 uses ECDH followed by HKDF-SHA-256 domain separation to form a session root.

Each message derives a 256-bit message key from:

- the session root;
- message number;
- ordered sender/recipient direction.

The two directions therefore do not use the same derived message key at equal counters.
The derived key byte array is overwritten after encryption or decryption.

This scheme is intentionally limited. Static ECDH plus a per-message KDF does **not**
provide the forward secrecy or break-in recovery of a reviewed Double Ratchet.

The `SessionManager` boundary is intended to be replaced with reviewed
X3DH/PQXDH-style establishment and Double Ratchet processing after protocol-library,
licensing, toolchain, storage, migration, interoperability, and security review.

### Session and message persistence

`SessionStateCodec` serializes the send counter together with the session root inside
the Keystore-wrapped session blob.

A counter increment is persisted before network delivery begins. Sends per contact are
serialized so older counter state cannot overwrite newer state.

`SessionMessageCodec` serializes encrypted wire material and authenticated routing
data. Incoming AEAD authentication occurs before an atomic Room transaction records the
encrypted message and accepted replay tuple.

The relay envelope is deleted only after that transaction succeeds, or after a later
delivery is recognized as an already durably accepted authenticated replay.

Outgoing ciphertext is stored with `MessageStatus.PENDING` before a relay attempt.
Network failures retain it as `MessageStatus.FAILED` with durable attempt count and
next-retry timestamp.

Foreground polling and WorkManager best-effort sync call the same delivery boundary.
This is transport reliability behavior, not a cryptographic ratchet property or a
timely-delivery guarantee.

### Delivery receipts

After a normal incoming message is authenticated, decrypted, and durably accepted, the
receiver queues an encrypted delivery-receipt control payload back to the sender.

The receipt contains:

- original message ID;
- conversation ID;
- original sender device ID;
- original recipient device ID;
- timestamp;
- receipt type `DELIVERED`.

It does not contain message plaintext, keys, nonces, ciphertext, or queue IDs.

The sender marks the outgoing message as `MessageStatus.DELIVERED` only after
authenticating the receipt and durably recording its replay marker.

Duplicate receipts are suppressed by the replay table and are not displayed as chat
messages.

`DELIVERED` means the receiving application accepted, decrypted, and durably stored the
message. It does not mean the human user opened or read it. Read receipts are not
implemented.

### Diagnostics boundary

The delivery-diagnostics boundary receives only:

- counts;
- enum status codes;
- timestamps;
- short SHA-256 fingerprints of queue identifiers.

It has no input fields for plaintext, key bytes, nonce values, or ciphertext envelopes.
It is QA support rather than cryptographic evidence or metadata protection.

## Signal Protocol Migration Gate

The isolated `core:signal-protocol` module resolves official Signal artifacts:

```text
org.signal:libsignal-client:0.94.1
org.signal:libsignal-android:0.94.1
```

The library provides Signal Protocol/Double Ratchet functionality, but the adapter is
deliberately disabled and is not part of the application runtime path.

Activation requires, at minimum:

- encrypted durable implementation of every required official `SignalProtocolStore`
  mapping;
- verified prekey/PQXDH invitation design;
- identity-change and downgrade semantics;
- reviewed per-device session fan-out and revocation;
- migration and rollback design;
- Kotlin/Compose toolchain migration;
- JNI/ABI packaging review;
- AGPL and external-use review;
- interoperability tests;
- independent cryptographic integration review.

Until those gates are satisfied, the prototype session remains explicitly
non-production.

See [`SIGNAL_MIGRATION_SPIKE.md`](SIGNAL_MIGRATION_SPIKE.md) for artifact/ABI findings
and the minimum gated migration sequence.

## Disabled Signal Store Spike

`core:signalstore` is a compile-time boundary with:

```text
SIGNAL_BACKED_SESSIONS_ENABLED=false
```

It provides neutral interfaces for:

- identity records;
- session records;
- prekey records;
- signed-prekey records;
- Kyber prekey records;
- a reserved sender-key interface for future group assessment.

Its disabled `SignalSessionManagerAdapter` cannot create a session.

Room schema v5 contains storage-only tables for the five one-to-one record types.
`EncryptedSignalStoreRepository` wraps opaque serialized bytes with the existing local
vault before persistence.

Lookup identifiers remain local metadata. Record contents, including any future private
record material, are not stored as plaintext.

This boundary has not been mapped to official libsignal records and is not reachable
from current chat UI or delivery.

`INVITE_V2_SIGNAL_PQXDH_EXPERIMENTAL` is a neutral serialized DTO for future design
tests. Its signature and prekey fields are placeholders, are not verified by a protocol
implementation, and are never accepted by active v1 chat invitations.

## Associated Data

The authenticated but not confidential AAD frame includes:

- protocol version;
- conversation ID;
- sender device ID;
- recipient device ID;
- message number;
- timestamp bucket;
- content type;
- encryption-suite ID.

Changing any authenticated field causes authenticated decryption failure.

AAD is authenticated but not encrypted. It exposes routing metadata wherever its
encoded form is stored or transmitted.

## STANDARD_AEAD

The only working suite is `AES/GCM/NoPadding` through the platform JCA provider:

- AES-256 message key;
- 96-bit nonce formed from a random four-byte per-key prefix and persisted 64-bit
  incrementing counter;
- 128-bit authentication tag;
- SHA-256 AAD digest retained in the package for early mismatch detection, while GCM AAD
  authentication remains authoritative.

The nonce counter is synchronously persisted before ciphertext is emitted.

Database and preference backup is disabled so old nonce counters are not restored
alongside keys. Any redesign of persistence, backup, restore, or device transfer must
re-evaluate the nonce invariant.

Debug builds permit an explicitly configured LAN HTTP relay address for physical-device
testing and display a warning.

Release configuration rejects HTTP relay URLs and retains Android cleartext-network
blocking. Transport hardening does not change the prototype session protocol or
establish a production deployment.

## Local Storage Encryption

Room does not store plaintext message bodies. End-to-end ciphertext packages are stored
as blobs.

The following selected values are wrapped with an AES-GCM key created by Android
Keystore:

- local profile display name;
- local contact display label;
- prototype session-state blob;
- opaque disabled Signal-store record contents when used by tests.

Android Keystore selects randomized vault IVs.

This protects selected at-rest content under the documented device assumptions. It does
not encrypt all database metadata and does not protect plaintext or keys from a running
compromised device.

Room is not a fully SQLCipher-encrypted database. Table shape, identifiers, routing AAD,
lookup keys, and access patterns may remain visible locally.

## PARANOIA_C4_AEAD — Removed (Phase 0, 2026-09-27)

`PARANOIA_C4_AEAD` was a design-only, permanently-disabled cascade-cipher proposal
(Threefish -> Serpent -> AES -> SHACAL-2). It was never an implemented secure mode:
the code always threw an exception, and no reviewed primitive implementation, KDF/MAC
design, or test vectors ever existed for it.

It was removed entirely from the codebase in Phase 0 of the crypto rewrite (see
`docs/ai-workflow/REWRITE_PLAN.md`), rather than kept as a disabled placeholder,
because a home-grown cipher cascade adds attack surface without any provable security
benefit over a single reviewed AEAD construction — the same reasoning that governs
this project's choice to migrate to real `libsignal` Double Ratchet + PQXDH instead of
inventing additional home-grown ratchet layers.

`STANDARD_AEAD` (AES-256-GCM) remains the only executable payload-encryption suite in
the legacy prototype session path. It is being replaced by real Double Ratchet sessions
per the rewrite plan; see that document for the current protocol direction.
