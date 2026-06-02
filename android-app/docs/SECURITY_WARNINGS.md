# Security Warnings

## Required User Warning

This is an MVP and audit-ready prototype only. Do not use it for real sensitive
conversations. It has not undergone independent cryptographic, Android, protocol,
server or privacy audit.

## Explicitly Unsafe Or Incomplete For Production

- Simplified ECDH session setup is not X3DH/PQXDH plus Double Ratchet.
- Debug relay synchronization is wired, but it exposes queue/timing/size/IP metadata
  and is not a production transport.
- Failed delivery is persisted and can be retried by a WorkManager worker, but
  background execution is best effort, may be delayed by Android, and is not a
  guarantee of timely offline delivery.
- Delivery receipts are encrypted control messages and contain no plaintext, but they
  still reveal minimal delivery metadata to the peers. `Delivered` means the receiving
  device accepted, decrypted and stored the message; it is not a read receipt.
- Debug builds permit a user-selected cleartext LAN relay URL for two-device testing.
  It is not a secure deployment transport and exposes network metadata.
- Public HTTPS tunnel/VPS relay testing is QA-only. It helps a phone and emulator
  reach the same relay, but it still exposes relay metadata and is not production
  infrastructure.
- A VPS HTTPS relay is not trusted. HTTPS protects the network hop to the server,
  but the relay can still observe IP addresses, request timing, queue metadata,
  approximate message sizes and traffic frequency. It must not store messages beyond
  TTL and must use secret-free logs.
- The confirmed public `http://194.87.226.46` endpoint is a temporary debug/QA
  exception only. Public HTTP by IP is not acceptable for release or production.
  Configure a domain name and HTTPS before broader testing.
- VPS SSH access was hardened for QA with public-key login, disabled password login
  and disabled keyboard-interactive login. This reduces one operational risk, but it
  does not make the relay trusted or production-ready.
- The debug-only test-data reset intentionally preserves the configured relay URL for
  repeated LAN QA while removing local profiles, cryptographic/delivery state and
  diagnostics. It is not a forensic secure-erasure claim.
- Release builds reject `http://` relay configuration and Android cleartext traffic;
  this hardening does not make an unreviewed HTTPS relay production-safe.
- New devices can be staged, approved and locally revoked; true per-device sessions,
  cryptographic multi-device fan-out, synchronized revocation, metadata protection,
  app lock, secure backup and attachment handling are incomplete.
- The official `libsignal` dependency is only a disabled migration gate. It does not
  make current chats Double Ratchet sessions.
- `core:signalstore` stores encrypted opaque spike records and an experimental v2
  invitation DTO only. `SIGNAL_BACKED_SESSIONS_ENABLED` is false; none of this
  creates a Signal-backed chat or verifies PQXDH data.
- No forensic secure-delete assurance is possible in this version.
- Basic release defaults now disable cleartext networking and enable shrinking, but
  no reproducible-build review or external release-hardening audit has been completed.

## Paranoia C4

`PARANOIA_C4_AEAD` is disabled. The module is only an interface and design note until
all necessary primitives are sourced from reliable reviewed implementations, its full
composition is specified and tested, and an independent audit accepts the result.
It must not be enabled, advertised, or treated as secure based on its cascade name.

## Logging And Diagnostics Policy

Never log or export plaintext, decrypted attachments, passwords, private keys, session
keys, message keys, nonce values, or raw encrypted envelopes tied to identifiable users.
The delivery diagnostics screen exports only technical statuses, counters, formatted
timestamps, grouped event names, last-error labels and short SHA-256 queue fingerprints,
with a 100-event cap. This redaction boundary still requires audit review and is not
a metadata-anonymity guarantee.
The optional QA logcat scripts sanitize labelled sensitive fields before saving or
displaying output, but QA must use disposable messages: scripts cannot recognize
arbitrary sensitive text introduced by future accidental logging.

Public relay deployment adds operational obligations that are not solved by the MVP:
abuse controls, rate limiting, monitoring, patching, incident response, retention
review and independent server/protocol audit.

## Forbidden Claims

Do not market this project using claims such as "unbreakable", "military-grade",
"absolute security", or key-size-based security guarantees.
