# Known Limitations

## Status And Claim Boundary

The application is an audit-ready MVP prototype, not a production-secure messenger. It has not
completed an independent cryptographic, Android, protocol, server, privacy, or release-security
audit and must not be represented as suitable for real sensitive communications.

Since 2026-09-30 the only chat path is protocol v2: official libsignal PQXDH + Double Ratchet
([PROTOCOL_V2.md](PROTOCOL_V2.md)). It runs in **debug builds only**; release builds have no chat
until the independent review of Phase 5 (DEC-021). The legacy prototype (`SimplifiedSessionManager`,
`STANDARD_AEAD`, v1 invitations) was removed, and its data is deleted once on upgrade.
`PARANOIA_C4_AEAD` was removed on 2026-09-27.

## Session And Protocol Security

- libsignal provides the Double Ratchet's forward secrecy and post-compromise security; project
  tests prove both through this app's store adapter, including their honest boundary: a message
  not yet received when a device is stolen keeps its (skipped) key and is readable by the thief,
  and healing after a compromise needs one full round trip.
- The integration (store adapter, invitations, envelopes, pipeline) is project code and has not
  been independently reviewed. Interoperability with Signal's own apps is neither a goal nor tested.
- The libsignal identity private key is a software key: encrypted at rest with an Android Keystore
  key, but present in app memory while in use (as in Signal-Android). The removed legacy identity
  was a hardware-backed, non-exportable P-256 key.

## Identity And Pairing Trust

- One-sided pairing: the scanner gets the inviter's identity from the QR-committed bundle; the
  inviter gets the scanner's identity from the HELLO and must accept a contact request. This is
  **trust on first use** on the accepting side. A person who photographs the QR code can send one
  request first; the only protection is comparing the 60-digit safety number through a trusted or
  independent channel, which is manual.
- Unaccepted requests are never contacts and get no receipts. A contact whose identity changes is
  never trusted again (strict trust in both directions); re-pairing with a new invitation is the
  only way forward. An identity claimed by an unauthenticated message never changes contact state.
- Single device per contact. Multi-device fan-out and synchronized revocation are not implemented
  (Phase 4).

## Delivery Semantics And Availability

- Foreground polling (chat open) and WorkManager sync (about every 15 minutes or later) are best
  effort. Android may delay background work; timely offline delivery is not guaranteed.
- The relay can delete, delay, reorder, replay, or withhold encrypted blobs. Authenticated
  encryption does not prevent denial of service. Unauthenticated or unroutable blobs are not
  accepted and stay on the relay until expiry, which can contribute to queue filling.
- `Delivered` means the receiving application authenticated, decrypted, and durably stored the
  message and the sender authenticated the resulting receipt from the same contact. It does not
  mean the person read the message. Read receipts are not implemented.
- Invitations expire after 24 hours and can be used once. If the relay withholds the sealed bundle,
  the inviter must show a new invitation.

## Relay And Network Metadata

- The debug relay may use HTTP (LAN or `adb reverse`); release builds reject cleartext.
- The relay has no per-queue authentication and no rate limiting (Phase 3). Anyone who learns a
  queue ID can read or delete its blobs (still ciphertext).
- The relay operator can observe client IP addresses, queue IDs, per-conversation routing tags,
  request timing, message sizes (padded to 160-byte blocks), traffic frequency, and the
  initiator's identity key in PreKey message headers.
- Historical VPS or endpoint QA records do not prove current server availability, ownership,
  configuration, or security.

## Devices, Local Storage, And Plaintext

- Message bodies, contact names, claimed names, and invitation text are vault-encrypted
  (AES-GCM, Keystore key) with the row bound into the AAD; the database is not fully encrypted
  (no SQLCipher). Table structure, identifiers, routing tags, statuses and timestamps are visible.
- Stored history is not protected by forward secrecy: a compromise that includes the usable
  vault key reveals stored messages.
- Plaintext exists in input, Compose, and process memory while messages are typed or displayed.
  Screenshots, keyboards, notifications, accessibility services, and malware are out of scope.
- App lock, biometric unlock, encrypted backup, device transfer, and attachments are not implemented.

## Local Deletion And Legacy Reset

- "Delete local data" removes the profile, conversations, libsignal records (one transaction,
  under the protocol lock), the vault key and the v2 queue ID; the relay URL is kept. Blobs
  already on the relay expire by TTL.
- The one-time legacy reset drops the legacy tables and removes the legacy Keystore key and
  preferences. Neither action is a forensic secure erase: flash storage can keep old blocks.

## Build And Tooling

- AGP 8.5.2 predates Kotlin 2.2: lint's Kotlin analysis is degraded and R8 cannot parse Kotlin
  metadata (warnings only). Tracked in TQ-009.
- Release APK is unsigned; reproducible builds, signing, dependency verification and SBOM are
  not in place (TQ-007, TQ-008).

## Additional Missing Production Controls

No complete release, signing, dependency-verification, SBOM, backup and recovery,
incident-response, production relay, or external security-audit process exists. These require
separate implementation, verification, and review before any production-security claim.
