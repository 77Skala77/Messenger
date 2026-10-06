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
  been independently reviewed. Phase 5 added fuzz and attack tests and a second AI review, which
  are internal checks, not an audit; a paid external audit is required before release (DEC-026).
  Interoperability with Signal's own apps is neither a goal nor tested.
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
- "Copy invitation code" puts the invitation secret on the clipboard. The clip is marked
  sensitive, but some keyboards keep their own clipboard history anyway (the Samsung keyboard does);
  the screen says so and offers "Share invitation", which bypasses the clipboard (DEC-026).
- Whoever has the QR code can also delete the sealed bundle from the relay mailbox (the code holds
  its read secret); the intended scanner then gets "no longer works" and needs a new invitation.
- An invitation works once. Once used, the inviter's app also withdraws its sealed bundle from the
  relay (DEC-032), so a later scan is told at once that the invitation no longer works. Only a scan
  made before the inviter's app processed the first answer still waits without an answer; the
  scanner's contact page then says that someone else may have used the invitation first.
- The invitation and scanner screens can be screenshotted (owner's choice, DEC-032); "Screen
  security" in settings (off by default) blocks screenshots and recording of the whole app.

## Delivery Semantics And Availability

- Foreground polling (chat open) and WorkManager sync (about every 15 minutes or later) are best
  effort. Android may delay background work; timely offline delivery is not guaranteed.
- The background connection (DEC-034, debug builds, on by default) fetches every 30 seconds while
  the app is closed, through a foreground service with a permanent notification and a wake lock. It
  costs battery (more on mobile data). Phone makers' battery managers (Xiaomi, Redmi and others) may
  still stop it unless the app is allowed to run in the background and to autostart; the periodic
  work stays the fallback. It relies on targetSdk 34: Android 15 rules for apps targeting 35 allow
  such a service six hours a day, so raising targetSdk needs another design first.
- Message order (DEC-034): both phones agree only for texts that arrived within a minute of being
  written; a text that came later shows at its arrival. Clocks that differ by more than a few
  seconds between the phones can still swap two texts written at nearly the same moment.
- Replies (DEC-035) are not shown by an app that predates them (inner payload type 5): such an app
  drops the reply. A deleted contact is not told; its later texts stay at "sent to the delivery
  server" on its phone.
- The relay can delete, delay, reorder, replay, or withhold encrypted blobs. Authenticated
  encryption does not prevent denial of service. Unauthenticated or unroutable blobs are never
  accepted; the app deletes them once decided (DEC-026), but anyone who knows a queue ID can still
  send junk faster than it is deleted, within the QA relay's per-IP limits. If the relay moves a
  text before its HELLO, that text is rejected (lost); the invitation is not affected.
- `Delivered` means the receiving application authenticated, decrypted, and durably stored the
  message and the sender authenticated the resulting receipt from the same contact. It does not
  mean the person read the message. Read receipts are not implemented.
- Invitations expire after 24 hours and can be used once. If the relay withholds the sealed bundle,
  the inviter must show a new invitation.

## Relay And Network Metadata

- The debug relay may use HTTP (LAN or `adb reverse`); release builds reject cleartext.
- Relay queues are self-certifying (DEC-024): the queue ID is the hash of a read secret that only
  the owner holds, so contacts can send but not read or delete. The relay assigns message IDs and
  applies QA-level limits (64 KB bodies of canonical base64, per-IP rate, 1,000 pending per queue,
  at most 100 messages per read); this is not production abuse control. Over plain HTTP (debug) the
  read secret travels in clear; release requires HTTPS. Pending envelopes live up to 7 days on the
  relay.
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
- The database overwrites deleted rows (`secure_delete` ON) and syncs every commit (`synchronous`
  FULL) on every connection (DEC-026). Old page versions can still sit in the write-ahead log
  until a checkpoint, and flash storage can keep old blocks: deleted ratchet states are not
  guaranteed gone from the hardware.
- Plaintext exists in input, Compose, and process memory while messages are typed or displayed.
  Screenshots, keyboards, notifications, accessibility services, and malware are out of scope.
- App lock, biometric unlock, encrypted backup, device transfer, and attachments are not implemented.
- If the vault key in Android Keystore is lost, invalidated or replaced (DEC-023), everything
  stored on that phone is unrecoverable. The app detects it, runs nothing, says so, and lets the
  user delete the local data and start over; contacts must be paired again and see a new safety
  number. The key is an AES-256 key in Android Keystore without StrongBox or unlocked-device
  binding (background sync runs while the phone is locked).

## Local Deletion And Legacy Reset

- The receive queue's read secret lives in the app's preferences (vault-encrypted). If only that
  file were lost while the database survived, the app would silently create a new queue and
  contacts would keep sending to the old one (rare; fix planned, TQ-108).
- "Delete local data" removes the vault key first, then the profile, conversations and libsignal
  records (one transaction, under the protocol lock), then the v2 queue ID; the relay URL is kept.
  It needs no key, so it also works when the data is unreadable. Blobs already on the relay
  expire by TTL.
- The one-time legacy reset drops the legacy tables and removes the legacy Keystore key and
  preferences. Neither action is a forensic secure erase: flash storage can keep old blocks.

## Build And Tooling

- AGP 8.13.2 (DEC-022) builds Kotlin 2.2 code; lint and R8 read its metadata without errors or
  warnings since TQ-009. Libraries that need compileSdk 37 (Compose 1.12, core 1.19, lifecycle
  2.11), Kotlin above 2.2.20 and AGP 9 are deliberately not adopted yet.
- Release APK is unsigned; reproducible builds, signing, dependency verification and SBOM are
  not in place (TQ-007, TQ-008).

## Additional Missing Production Controls

No complete release, signing, dependency-verification, SBOM, backup and recovery,
incident-response, production relay, or external security-audit process exists. These require
separate implementation, verification, and review before any production-security claim.
