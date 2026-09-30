# Audit Scope

## Status

This repository is an Android MVP and audit-ready prototype. It is not production-secure and
must not be used for real sensitive conversations. Updated 2026-09-30 for protocol v2 (DEC-021).

## In-Scope Components

- `core:signal-protocol`: libsignal store adapter, sessions, invitation v2, envelope and payload
  codecs, the v2 pipeline (`ConversationV2Service`).
- `core:storage`: Room v8, Keystore vault, encrypted records, protocol transactions, legacy reset
  migration.
- `core:signalstore`: neutral storage boundaries.
- `core:transport` and `server/relay`: relay requests, TTL, metadata exposure.
- `core:ui`: release gating, request acceptance and safety numbers, background sync, legacy reset,
  debug/release relay controls.

## Explicit State Of The Protocol

- Chats use official libsignal PQXDH + Double Ratchet (protocol v2), in debug builds only;
  release builds have no chat until independent review.
- The legacy prototype (`SimplifiedSessionManager`, `STANDARD_AEAD`) and `PARANOIA_C4_AEAD` are
  removed; there is nothing of them to scope in.
- LAN `http://` relay access is debug-only; release configuration blocks insecure HTTP URLs.

## Review Objectives

- Confirm the store adapter and pipeline preserve libsignal's guarantees (forward secrecy,
  post-compromise security, replay rejection) and fail closed on identity changes.
- Review invitation v2 (commitment, sealing, one-time keys) and the request/TOFU flow.
- Review transaction atomicity, relay deletion ordering, receipts, and background sync.
- Verify logs never contain plaintext, keys, ciphertext, or full queue IDs.
- Confirm v2 is unreachable in release builds.

## Out Of Scope As A Completed Guarantee

Production protocol readiness, metadata anonymity, multi-device and synchronized revocation,
relay authentication, secure deletion, full device-compromise resistance, independent crypto
assurance, and reproducible release artifacts remain unimplemented or unaudited.
