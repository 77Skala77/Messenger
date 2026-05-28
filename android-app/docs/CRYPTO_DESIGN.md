# Cryptographic Design

## Non-Production Notice

Only the standard payload AEAD is executable. The current invitation/session scheme
is deliberately a small prototype with an upgrade boundary; it is not a replacement
for Signal Protocol and is not appropriate for sensitive real-world communications.

## Device Identity And Invitation

The Android application generates a P-256 EC key agreement key in Android Keystore.
An invitation QR payload contains version, receiving queue identifier, device ID and
public encoded key. It never contains the private key or the local profile display name.
The public-key SHA-256 fingerprint is displayed as the safety number.

Accepting an invitation records the public identity locally as `UNVERIFIED`.
The user must compare fingerprints or QR data over a trusted path to mark it verified.
A different public-key fingerprint for an existing device produces `KEY_CHANGED`.
An additional device invitation is persisted as `PENDING_APPROVAL`; the current
prototype retains its queue endpoint but does not send to it automatically. A locally
revoked or key-changed primary device blocks sending. A `DeviceFanOutPlanner`
boundary exists, but the implementation intentionally emits only the established
primary-session target; it does not simulate multi-device cryptography.

Each invitation is a receive address for its creator. A peer that must receive a
reply must publish its own invitation, so the authenticated A-to-B delivery test first
performs reciprocal imports.

## Prototype Sessions

Phase 1 uses ECDH followed by HKDF-SHA-256 domain separation to form a session root.
Each message derives a 256-bit message key from that root, message number and ordered
sender/recipient direction, so two directions do not use the same derived key at equal
counters. The derived byte array is overwritten immediately after encryption/decryption.

This is intentionally limited: static ECDH plus per-message KDF does **not** deliver
the forward secrecy or break-in recovery of a real Double Ratchet. The `SessionManager`
boundary is meant to be replaced with X3DH/PQXDH plus Double Ratchet after protocol
library selection and review.

`SessionStateCodec` serializes the send counter together with the session root inside
the Keystore-wrapped session blob. A counter increment is persisted before network
delivery begins, with sends per contact serialized so older counter state cannot
overwrite newer state. `SessionMessageCodec` serializes only encrypted wire material and
authenticated routing data. Incoming AEAD authentication occurs before an atomic Room
transaction records both the encrypted message and accepted replay tuple. The relay
envelope is deleted only after that transaction succeeds, or after a later delivery is
recognized as an already durably accepted authenticated replay.

Outgoing ciphertext is stored as `PENDING` before a relay attempt. Network failures
retain it as `FAILED` with durable attempt count and next-retry timestamp. Foreground
polling and WorkManager best-effort sync call the same delivery boundary. This is a
transport reliability mechanism, not a cryptographic ratchet property or a timely
delivery guarantee.

After a normal incoming message is authenticated, decrypted and durably accepted,
the receiver queues an encrypted delivery receipt control payload back to the sender.
The receipt contains the original message ID, conversation ID, original sender device
ID, original recipient device ID, timestamp and receipt type `DELIVERED`. It does
not contain message plaintext, keys, nonces, ciphertext or queue IDs. The sender only
marks an outgoing message as `DELIVERED` after authenticating this receipt and
durably recording the receipt replay marker. Duplicate receipts are suppressed by the
same replay table and are not displayed as chat messages.

`DELIVERED` means the receiving device accepted, decrypted and saved the message. It
does not mean the human user opened or read it; read receipts are not implemented.

The delivery diagnostics boundary receives only counts, enum status codes, timestamps
and short SHA-256 fingerprints of queue identifiers. It has no input for plaintext,
key bytes, nonce values or ciphertext envelopes. It is QA support rather than
cryptographic evidence or metadata protection.

## Signal Protocol Migration Gate

The isolated `core:signal-protocol` module resolves official Signal artifacts
`org.signal:libsignal-client:0.94.1` and `org.signal:libsignal-android:0.94.1`.
The library provides Signal Protocol/Double Ratchet, but the adapter is deliberately
disabled and is not part of the app runtime path.

Activation requires an encrypted durable implementation of every required
official `SignalProtocolStore` mapping, verified prekey/PQXDH invitation design,
identity-change semantics, device fan-out/revocation tests, Kotlin/Compose toolchain
migration, AGPL licensing review and independent cryptographic integration review.
Until then the prototype session remains explicitly non-production.

See `SIGNAL_MIGRATION_SPIKE.md` for artifact/ABI findings and the minimum gated
migration sequence.

## Disabled Signal Store Spike

`core:signalstore` is a compile-time boundary with
`SIGNAL_BACKED_SESSIONS_ENABLED=false`. It provides neutral interfaces for identity,
session, prekey, signed prekey and Kyber prekey records, plus a reserved sender-key
interface for future group assessment. Its disabled `SignalSessionManagerAdapter`
cannot create a session.

Room schema v5 contains storage-only tables for the five one-to-one record types.
`EncryptedSignalStoreRepository` wraps opaque serialized bytes with the existing
local vault before persistence. Lookup identifiers remain local metadata; record
contents, including any future private record material, are not stored plaintext.
This boundary has not been mapped to official libsignal records or made reachable
from chat UI/delivery.

`INVITE_V2_SIGNAL_PQXDH_EXPERIMENTAL` is a neutral serialized DTO for future design
tests. Its signature and prekey fields are placeholders, are not verified by a
protocol implementation, and are never accepted by active v1 chat invitations.

## Associated Data

The authenticated but not confidential AAD frame includes:

- Protocol version.
- Conversation ID.
- Sender device ID and recipient device ID.
- Message number.
- Timestamp bucket.
- Content type.
- Encryption suite ID.

Changing any of these fields causes authenticated decryption failure. AAD exposes
routing metadata wherever its encoded form is stored or transmitted.

## STANDARD_AEAD

The working suite is `AES/GCM/NoPadding` through the platform JCA provider:

- AES-256 message key.
- 96-bit nonce: random four-byte per-key prefix plus persisted 64-bit incrementing counter.
- 128-bit authentication tag.
- SHA-256 AAD digest retained in the package for early mismatch detection; GCM AAD
  authentication remains the authority.

The nonce counter is synchronously persisted before ciphertext is emitted. Database
and preferences backup is disabled so old nonce counters are not restored alongside keys.
Any redesign of persistence or backup must re-evaluate the nonce invariant.

Debug builds permit an explicit LAN HTTP relay address for physical-device testing and
display a warning. Release configuration rejects HTTP relay URLs and retains Android
cleartext-network blocking. Transport hardening does not change the current prototype
session protocol or establish a production deployment.

## Local Storage Encryption

Room does not store plaintext message bodies. End-to-end ciphertext packages are kept
as blobs. Local profile names, local contact labels and session-state blobs are wrapped
with an AES-GCM key created by Android Keystore. The Keystore chooses randomized vault
IVs. This protects at-rest content under the stated device assumptions, not against a
running compromised device.

## PARANOIA_C4_AEAD Design Boundary

The target proposal is payload encryption only:

```text
message key
  -> domain-separated KDF subkeys for Threefish, Serpent, AES, SHACAL-2, MAC, nonce
plaintext -> Threefish -> Serpent -> AES -> SHACAL-2 -> ciphertext
tag = MAC(macKey, associatedData || ciphertext)
```

It must authenticate before any plaintext is released, never fall back to plaintext,
never share direct keys across ciphers and never bypass verification.

In this MVP `ParanoiaC4Aead` always fails with
`ExperimentalSuiteDisabledException`. There is no XOR, toy cascade or unsupported
claim. Safe implementation requires selected audited primitive libraries, precisely
specified modes/framing/nonce construction, independent vectors, differential testing,
side-channel review and external cryptographic audit.

Paranoia C4 cannot replace identity verification, authenticated key agreement,
ratcheting, device management or metadata protection.
