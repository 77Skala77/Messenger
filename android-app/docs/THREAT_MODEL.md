# Threat Model

## Status

This repository is an audit-ready MVP prototype, not a production-secure messenger.
The document describes intended boundaries and the current implementation honestly;
it is not an assurance report.

## MVP Protects

- Message content processed through working authenticated encryption (`STANDARD_AEAD`).
- Persisted local message payloads from being stored as plaintext.
- Local profile display name and prototype session state using an Android
  Keystore-backed vault key.
- Opaque disabled Signal-store spike record contents, when populated in tests, using
  the same local vault; this adds no active messaging protocol guarantee.
- Basic QR invitation import, displayed safety fingerprint, manual verification status,
  and warnings when an observed contact key changes.
- Durable rejection of a replayed authenticated message number across app restarts.
- Staging an explicitly imported additional device without automatically trusting it
  or enabling delivery to it.
- Preventing the current delivery path from sending to a primary device locally marked
  pending, revoked or key-changed.

## MVP Does Not Fully Protect Yet

- Timing metadata, IP addresses, message size, or traffic frequency.
- Server-side queue metadata and complete social-graph resistance.
- Local Signal-store lookup metadata such as record/address identifiers; only opaque
  record payload bytes are wrapped by the vault.
- A device compromised while unlocked or a malicious operating system.
- Screenshots, keyboard collection, notification leakage, or memory inspection.
- Cryptographic multi-device delivery fan-out, synchronized revocation and cross-device state synchronization.
- Prompt delivery while the application is not active; WorkManager synchronization
  is best effort and Android may postpone it.
- Forward secrecy and post-compromise recovery equivalent to Double Ratchet.
- Authenticity of an invitation acquired over an untrusted channel before the user
  compares its safety number.

## Assets And Trust Boundaries

- Private device agreement key: intended to remain inside Android Keystore.
- Session root key: derived using prototype EC ECDH and stored only as an encrypted
  local blob; it is necessarily present in memory while an active session is used.
- Per-message keys: derived using HKDF for an operation and explicitly overwritten afterward.
- Plaintext: exists in Compose/input/application memory while displayed or encrypted;
  it must not enter Room, relay payload logs, or diagnostics.
- Relay: untrusted for confidentiality and integrity; AEAD authenticates content and AAD.

## Relay Limitations

The relay stores opaque encrypted blobs with TTL and supports deletion after receipt.
It does not receive plaintext or private keys. It still observes a queue identifier,
arrival/retrieval times, approximate message sizes and network-layer source data.
Relay denial of service, deletion, withholding and traffic analysis remain possible.
Malformed unauthenticated blobs are retained rather than acknowledged, so an abusive
relay or sender can also deny delivery by filling a queue.
The runnable endpoint is a debug development transport that may be exposed on a
local Wi-Fi network for physical-device testing. Cleartext LAN transport and a
user-configured endpoint are not a deployable anonymity or production relay design.

## Device Compromise

Android Keystore reduces key extraction risk but does not make a compromised device
safe. Malware with UI/accessibility privileges may read plaintext displayed on screen
or capture it before encryption. Local wipe removes current app entries and Keystore
aliases but cannot promise forensic secure erase on flash storage.

## Security Assumptions

- Android API 31+ Keystore correctly protects generated EC agreement and AES vault keys.
- The JCA AES-GCM and HMAC-SHA-256 implementations on the device are correct.
- Counter state writes complete before AEAD encryption; backup is disabled for local
  database and preferences to avoid restoring old nonce state.
- Users verify safety fingerprints using a trusted out-of-band comparison.

## Future Metadata Reductions

Sealed sender, opaque single-use queues, padding, batching, relay chaining, Tor/P2P
transport, and removal of stable queue linkability belong to later reviewed phases.
