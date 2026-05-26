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

## Prototype Sessions

Phase 1 uses ECDH followed by HKDF-SHA-256 domain separation to form a session root.
Each message derives a 256-bit message key from that root, message number and ordered
sender/recipient direction, so two directions do not use the same derived key at equal
counters. The derived byte array is overwritten immediately after encryption/decryption.

This is intentionally limited: static ECDH plus per-message KDF does **not** deliver
the forward secrecy or break-in recovery of a real Double Ratchet. The `SessionManager`
boundary is meant to be replaced with X3DH/PQXDH plus Double Ratchet after protocol
library selection and review.

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
