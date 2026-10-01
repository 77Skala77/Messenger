# Security Warnings

Updated 2026-09-30 for protocol v2 (DEC-021).

## Required User Warning

This is an MVP and audit-ready prototype only. Do not use it for real sensitive
conversations. It has not undergone independent cryptographic, Android, protocol,
server or privacy audit.

## Explicitly Unsafe Or Incomplete For Production

- Chats use official libsignal PQXDH + Double Ratchet (protocol v2, `PROTOCOL_V2.md`), but only
  in debug builds. Release builds have no chat until an independent external audit, planned before
  any release (`enable_v2_protocol = false`, DEC-026). The project's store adapter, invitation
  format, and pipeline around libsignal are unaudited; Phase 5 tested them internally (fuzzing,
  attack matrix, a second AI review), which is not an audit.
- Trust on first use: the inviter accepts a contact request after seeing an unverified claimed
  name and a safety number. Comparing safety numbers in person is the only identity check.
- Single device per contact; no multi-device fan-out and no synchronized revocation.
- The relay is untrusted and exposes metadata: IP addresses, queue IDs, routing tags, timing,
  sizes, and the initiator's identity key in the first (PreKey) messages. Per-queue read secrets
  and QA-level limits exist since Phase 3 (DEC-024); they are not production abuse control.
- Background sync uses WorkManager and is best effort: Android may delay it, and it is not a
  guarantee of timely offline delivery.
- `Delivered` means the contact's device authenticated, decrypted, and stored the message; it is
  not a read receipt. Receipts are encrypted but reveal delivery metadata to the peers.
- Debug builds permit a user-selected cleartext `http://` relay URL for testing. It is not a
  secure deployment transport.
- A VPS HTTPS relay is not trusted either: HTTPS protects the network hop, but the relay still
  observes the metadata above. It must not store messages beyond TTL and must use secret-free
  logs. The old `http://194.87.226.46` endpoint is historical and not acceptable for release.
- Local data is encrypted field by field with a Keystore-backed vault key; the database structure
  and metadata are visible (not SQLCipher). The libsignal identity private key is a software key
  encrypted at rest.
- If the vault key is lost or replaced, the conversations stored on the phone are unrecoverable.
  The app never creates a replacement key on its own; it blocks, explains, and lets the user
  delete the local data and start over (DEC-023).
- "Delete local data" and the debug test reset remove records and keys, but flash storage gives
  no forensic secure-deletion guarantee.
- Legacy prototype data is deleted once on upgrade (`MIGRATION_7_8`, `LegacyReset`); legacy
  conversations cannot be continued or converted.
- Release builds reject `http://` relay configuration and Android cleartext traffic; this does
  not make an unreviewed HTTPS relay production-safe. The release APK is unsigned and has no
  reproducible-build review.
- App lock, encrypted backup, and attachments are not implemented.

## Paranoia C4 (removed)

`PARANOIA_C4_AEAD` was a permanently-disabled cascade-cipher placeholder. It was
removed from the codebase entirely in Phase 0 of the crypto rewrite (see
`docs/ai-workflow/REWRITE_PLAN.md`) rather than kept disabled — it never had reviewed
primitives, a specified composition, or an audit, and a home-grown cascade would not
have added provable security over a single reviewed AEAD suite in any case.

## Logging And Diagnostics Policy

Never log or export plaintext, decrypted attachments, passwords, private keys, session
keys, message keys, nonce values, ciphertext, or full queue IDs. The app has no diagnostics
export screen (the legacy one was removed in step 1f).
The optional QA logcat scripts sanitize labelled sensitive fields before saving or
displaying output, but QA must use disposable messages: scripts cannot recognize
arbitrary sensitive text introduced by future accidental logging.

Public relay deployment adds operational obligations that are not solved by the MVP:
abuse controls, rate limiting, monitoring, patching, incident response, retention
review and independent server/protocol audit.

## Forbidden Claims

Do not market this project using claims such as "unbreakable", "military-grade",
"absolute security", or key-size-based security guarantees.
