# Relay Public Testing Notes

## Scope

Public relay testing exists so a physical phone and Android Emulator can reach the
same relay when LAN discovery is inconvenient. It is for QA only. It is not a
production deployment model and does not change the MVP security status.

## Minimum Dev Rules

- Use HTTPS for any relay URL reachable from the public internet.
- Keep debug HTTP only for local LAN or emulator tests.
- Do not log plaintext, private keys, session keys, message keys, nonce values,
  ciphertext contents, encrypted blob bodies or full queue IDs.
- Log only minimal technical events: health checks, accepted/deleted message IDs in
  redacted form if needed, TTL cleanup counts and error classes without payloads.
- Keep relay TTL short for undelivered messages.
- Do not store messages longer than the configured TTL.
- Do not publish tunnel credentials, provider tokens or local configuration files.

## Production Gap

A production relay would require HTTPS, rate limits, abuse controls, monitoring,
secret-free logs, operational key management, deployment hardening, backup/restore
policy, privacy review and an external security audit. Even then, this MVP client
still lacks Signal/PQXDH/Double Ratchet and metadata protection, so no production
security claim is allowed.
