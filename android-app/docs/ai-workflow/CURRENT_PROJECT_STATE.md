# Current Project State — Secure Messenger MVP

Last repository verification: 2026-10-03 (TQ-109 and TQ-110, DEC-028); later work up to DEC-039 is described below as written, uncommitted

This file records the current recoverable state of Secure Messenger as verified against the
repository. It is a mutable factual snapshot, not a roadmap, decision log, task queue, release
approval, or production-security claim.

## 1. Authoritative baseline

- Repository: `https://github.com/77Skala77/Messenger`, default branch `main`.
- The owner pushes; the working tree of the development machine (`D:\Messenger`) is the working
  state. Report whether changes are committed and pushed.
- Historical chat extracts and handoff notes are context only.

Evidence order: current code/configuration/flags, then tests and CI, then repository
documentation, then historical notes.

## 2. Status vocabulary

ACTIVE, IMPLEMENTED, DISABLED, EXPERIMENTAL, PLANNED, UNVERIFIED, HISTORICAL, NOT IMPLEMENTED — as
defined in `AGENTS.md`. Do not infer implementation from documentation, interfaces, or tests alone.

## 3. Project identity and security status

- Native Android private 1-to-1 messenger MVP; Russian default, English selectable.
- Not production-ready; must not be represented as ready for real sensitive communications.
- **No independent cryptographic or application security audit has been completed.**

## 4. Current runtime

### Protocol (DEC-019, DEC-021)

- **ACTIVE (debug builds only):** protocol v2 — official libsignal 0.94.1 PQXDH (Kyber-1024) +
  Double Ratchet, through the project's encrypted store adapter. Design: `../PROTOCOL_V2.md`.
- **DISABLED in release builds:** `enable_v2_protocol` is `false` in main resources; release shows a
  "not for messaging" screen and never constructs the pipeline. Enabling it requires an independent
  external audit (TQ-106, planned before release, DEC-026) and owner approval; the Phase 5 test pass
  is internal and does not replace it.
- **REMOVED (2026-09-30):** the legacy prototype (`SimplifiedSessionManager`, `STANDARD_AEAD`, v1
  invitations, legacy delivery pipeline, device-approval states, delivery diagnostics screen) and
  the modules `:core:crypto` and `:core:delivery`. Legacy data is deleted once on upgrade
  (`MIGRATION_7_8`, `LegacyReset`). `PARANOIA_C4_AEAD` was removed on 2026-09-27.

### Pairing and delivery semantics

- One-sided pairing: the scanner fetches a QR-committed sealed bundle and sends HELLO; the inviter
  sees a contact request (claimed name unverified, 60-digit safety number) and must accept it.
  Trust on first use on the accepting side.
- Outgoing envelopes are encrypted once, stored, and resent as is; foreground polling while the
  chat is open; best-effort WorkManager sync (`V2SyncWorker`, about every 15 minutes or later;
  delivery with the receiving app closed verified by hand on 2026-09-30).
- Incoming: decrypt + store + envelope digest commit in one Room transaction under a process-wide
  lock; relay deletion after commit; byte-identical re-deliveries recognised without decryption.
  A rejected envelope commits nothing (rolled back) and is deleted as well; only a local failure
  keeps an envelope on the relay (DEC-026). Exception: behind an envelope without a verdict, a
  rejected one with the same routing tag waits for the next sync, at most 10 per sync (DEC-028).
  Outgoing envelopes leave in order per contact.
- `Sent to delivery server` = relay acceptance. `Delivered` = authenticated receipt from the same
  contact naming the inner message ID. Not a read receipt. **NOT IMPLEMENTED:** read receipts.
  Voice messages only: "played" (type 7) when the recipient presses play (DEC-036, DEC-039).

## 5. Android application

Compose UI: onboarding, chats, contact requests, invitation QR/scan/paste, conversation with
delivery status and safety number, settings (language, appearance Light/Dark/System, relay URL and
health check, sync, delete local data, debug test reset and theme preview), a one-time legacy-reset
notice. Portrait-only QR scanner activity. Release rejects cleartext relay URLs. Phase 6 (DEC-027):
the messenger theme and its components exist (step 6c, Telegram's classic and Night palettes; since
DEC-038, written 2026-10-07 and not yet run on a phone: the dark theme is Telegram's "Dark Blue",
both themes use Telegram's default wallpaper, the settings have Telegram 12's layout with a page per
entry, the recording has Telegram's animations, and turning the phone keeps the open screen); the
chat list has its new look (step 6d: rows, requests on top, round button with a menu, pull to
refresh) and so does the conversation (step 6e: bubbles, date pills, header, input bar; the safety
number is one tap away on the header). Both were written 2026-10-05 and have not yet been run on a
phone. Notifications without message text (step 6f', DEC-031, written 2026-10-05, not yet run on a
phone): posted only by the background sync while no screen of the app is visible; the chat screen
fetches every few seconds only while the app is on screen.

## 6. Storage and trust

- Room schema **v13** (2026-10-07, uncommitted until the owner's check): `local_profile`, the
  libsignal record tables (`signal_*`), and the v2 tables (`v2_invitations`, `v2_contact_requests`,
  `v2_contacts`, `v2_messages`, `v2_accepted_envelopes`, and `v2_media_chunks` for the encrypted
  pieces of voice messages). Exported schemas 1–11 are committed; 12 and 13 are generated by the build and
  go with the DEC-036 to DEC-039 commit. `MIGRATION_11_12` adds a voice message's media state and upload
  progress and the pieces table; `MIGRATION_12_13` (DEC-039) its "listened" mark. Legacy tables were dropped by `MIGRATION_7_8`;
  `MIGRATION_8_9` reset the v2 test conversations for relay API v3 (DEC-024); `MIGRATION_9_10`
  added a local per-message "seen" mark for unread counters and a contact's verified date for step
  6f (DEC-029); `MIGRATION_10_11` (written 2026-10-06, DEC-035) adds a chat's pin time and mute end
  and the inner ID of the text a reply answers. Nothing about reading is sent: there are no read
  receipts.
- Incoming texts sort by the sender's authenticated time within one minute of arrival, never before
  the contact's previous text (DEC-034). Pin, mute, "mark as read", clearing history and deleting a
  chat (with its contact, session and identity) are local and send nothing (DEC-035). Replies are
  inner payload type 5 (DEC-035).
- Voice messages (DEC-036, debug builds; no calls): recorded into memory as AAC, at most 15 minutes,
  sent as AES-256-GCM pieces in a one-time relay queue; the note (type 6) carries key and read secret
  inside the session; "Delivered" only once the whole recording arrived; pause and listen before
  sending; one-time recordings answered by "played" (type 7), and since DEC-039 every voice message:
  pressing play on the contact's tells it once, and both phones show Telegram's dot until then (the only
  signal about listening; there are still no read receipts for texts). Paused recordings can be
  trimmed (in memory) and played messages dragged to a place. The main screen has Telegram 12's
  bottom tabs (Chats, Contacts, Settings, Profile). Single messages can be deleted on this
  phone or for both (type 8, DEC-037). The chat screen has Telegram 12's glass look and the app draws
  edge to edge. Written 2026-10-06/07; JVM suites and the debug build green; device runs see HANDOFF.
- Background connection (DEC-034, debug builds only, on by default): a foreground service fetches
  every 30 s while the app is closed; best effort, not a delivery guarantee.
- Message bodies, names, and invitation text are vault-encrypted (Keystore AES-GCM, row-bound AAD);
  libsignal records likewise. Not SQLCipher: structure and metadata are visible.
- The libsignal identity private key is a software key encrypted at rest (DEC-019 O3).
- Key loss (DEC-023, Phase 2): the vault key is created only with a new profile; the profile row
  is the canary. If it no longer decrypts, the app runs nothing and offers "Check again" and a
  confirmed "Delete local data and start over". Vault failures are unchecked and never become
  protocol verdicts. Key and profile creation run under process-wide locks and the profile row is
  never replaced, so a double tap makes one key and one profile (DEC-028).
- Contact states: `AWAITING_ACCEPTANCE`, `ACTIVE`, `KEY_CHANGED` (blocked; no code path sets it
  today, see `../PROTOCOL_V2.md`). A local "safety number checked" mark (DEC-030, written
  2026-10-05, not yet run on a phone) is set only by the person's confirmation for exactly the number
  shown, never sent, and cleared with `KEY_CHANGED`.
- Inbox loss (DEC-032, written 2026-10-06, not yet run on a phone): a missing or damaged inbox read
  secret while contacts, requests or invitations exist blocks the app with its own explanation; no
  new inbox is created silently. Envelope digests are kept 30 days. A "Screen security" switch
  (off by default) sets `FLAG_SECURE` for the app; used invitations are withdrawn from the relay. Pending requests are not contacts. Strict identity trust in both directions (DEC-018). Single device per contact; no
  synchronized revocation.
- Flash storage gives no secure-deletion guarantee.

## 7. Relay and infrastructure

- **ACTIVE for development/QA:** Node relay in `android-app/server/relay`, API v3 (DEC-024):
  `POST /v1/queues/{queueId}/messages`, `GET` and `DELETE` on the same path with a Bearer read
  secret, `GET /health`. Queue IDs are self-certifying; the relay assigns message IDs; it stores
  only canonical base64 and returns at most the 100 oldest messages per read (DEC-026).
- The relay is untrusted, has QA-level limits only (not production abuse control), and observes
  IPs, queue IDs, routing tags, timing, sizes, and the initiator's identity key in PreKey headers.
  Limits (DEC-026, DEC-028): per-address rate limits (least-recent eviction; behind a local reverse
  proxy `RELAY_TRUST_PROXY=1` counts the forwarded client address), 1,000 messages per queue,
  100,000 messages and 256 MiB of ciphertext for the whole relay. The app does not follow HTTP
  redirects from the relay.
- Two-phone QA on 2026-09-30 used a relay on the laptop reached over USB (`adb reverse`).
- **HISTORICAL/UNVERIFIED:** the VPS relay (`http://194.87.226.46`, HTTP by IP, legacy protocol).

## 8. Modules

`:app`, `:core:domain`, `:core:storage`, `:core:transport`, `:core:ui`, `:core:signal-protocol`,
`:core:signalstore`. Relay: `android-app/server/relay`.

## 9. Verification baseline (last full run 2026-10-03)

Last run (TQ-110, 2026-10-03): `:app:assembleDebug :app:lintDebug test` green (lint 0 errors, 16
warnings), relay tests 15/15, device suites on both phones (ui 27, storage 31, transport 13 with
the live relay, signal-protocol 75). The release and `qaMinified` builds were last built at the end
of Phase 5 (2026-10-01).

Toolchain on the development machine: JDK 17 (`D:\Tools\jdk-17`), Android SDK 34 and 36, AGP 8.13.2,
Gradle 8.14.3, Kotlin 2.2.20 (TQ-009, DEC-022), Node.js 24;
~3.9 GB RAM (use the low-memory Gradle flags in `README.md`). Devices: Samsung S24 Ultra
(SM-S928B) and Redmi A3x (24048RN6CG), both Android 16.

```bash
cd android-app
./gradlew :app:assembleDebug
./gradlew :app:lintDebug :core:signal-protocol:testDebugUnitTest :core:signalstore:test test
./gradlew :core:signal-protocol:connectedDebugAndroidTest :core:storage:connectedDebugAndroidTest \
  :core:ui:connectedDebugAndroidTest :core:transport:connectedDebugAndroidTest
./gradlew :app:assembleRelease :app:assembleQaMinified
cd server/relay && node --test
```

Evidence of the last run is recorded in `TASK_QUEUE.md` (TQ-110 results); older runs in
`REWRITE_PLAN.md` and `PHASE5_SECURITY_TEST_PLAN.md`. CI (`.github/workflows/android.yml`, actions
pinned to commit hashes since DEC-028) runs wrapper validation, assemble, lint, unit tests, relay
tests, and emulator UI/storage tests; its current result is **UNVERIFIED** from this machine.

## 10. Explicitly not implemented or not complete

Independent review; v2 in release builds; multi-device fan-out and synchronized revocation; relay
production relay abuse controls; read receipts; app lock; encrypted backup; attachments; secure
wipe; production relay and HTTPS endpoint; reproducible build, signing, SBOM; external audit.

## 11. Update rules

Update this file when the active protocol path, release gating, module list, Room schema,
relay API or trust assumptions, delivery semantics, build or CI commands, or production-readiness
claims change. Keep it factual and compact; plans go to `TASK_QUEUE.md`, rationale to
`DECISIONS.md`, session notes to `HANDOFF.md`.
