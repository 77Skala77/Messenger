# Audit Scope

## Status

This repository is an Android MVP and audit-ready prototype. It is not
production-secure and must not be used for real sensitive conversations.

## In-Scope Components

- `core:crypto`: active `STANDARD_AEAD`, AAD, nonce persistence, prototype sessions and disabled C4 boundary.
- `core:delivery`: pending/retry flow, authenticate-before-store, replay suppression and delete-after-accept.
- `core:storage`: Room ciphertext persistence, vault wrapping and Keystore use.
- `core:transport` and `server/relay`: relay request handling, TTL and metadata exposure.
- `core:ui`: security status, safe diagnostics, localization and debug/release relay controls.
- `core:signalstore` and `core:signal-protocol`: disabled migration spike boundaries only.

## Explicit State Of The Protocol

- Active chats still use `SimplifiedSessionManager` based on prototype ECDH/HKDF behavior.
- Signal-backed sessions are disabled and are not reachable from active message delivery.
- `PARANOIA_C4_AEAD` was removed from the codebase entirely on 2026-09-27; there is no cascade implementation to scope in.
- LAN `http://` relay access is debug-only; release configuration blocks insecure HTTP URLs.

## Review Objectives

- Establish whether active AEAD/nonce/AAD and local storage boundaries match their stated MVP guarantees.
- Confirm relay delivery ordering, retry durability, replay suppression and fail-closed device policy.
- Verify diagnostics and logs do not emit plaintext, keys, nonces, ciphertext or full queue identifiers.
- Review the gate for a later official libsignal integration without treating the spike as an implemented protocol.

## Out Of Scope As A Completed Guarantee

Production protocol readiness, metadata anonymity, synchronized multi-device revocation,
secure deletion, full app compromise resistance, independent crypto assurance and
reproducible release artifacts remain unimplemented or unaudited.
