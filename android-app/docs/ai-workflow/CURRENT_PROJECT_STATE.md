# Current Project State — Secure Messenger MVP

Last repository verification: 2026-09-30

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
  "not for messaging" screen and never constructs the pipeline. Enabling it requires the Phase 5
  independent review and owner approval.
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
- `Sent to delivery server` = relay acceptance. `Delivered` = authenticated receipt from the same
  contact naming the inner message ID. Not a read receipt. **NOT IMPLEMENTED:** read receipts.

## 5. Android application

Compose UI: onboarding, chats, contact requests, invitation QR/scan/paste, conversation with
delivery status and safety number, settings (language, relay URL and health check, sync, delete
local data, debug test reset), a one-time legacy-reset notice. Portrait-only QR scanner activity.
Release rejects cleartext relay URLs.

## 6. Storage and trust

- Room schema **v8**: `local_profile`, the libsignal record tables (`signal_*`), and the v2 tables
  (`v2_invitations`, `v2_contact_requests`, `v2_contacts`, `v2_messages`, `v2_accepted_envelopes`).
  Exported schemas 1–8 are committed. Legacy tables were dropped by `MIGRATION_7_8`.
- Message bodies, names, and invitation text are vault-encrypted (Keystore AES-GCM, row-bound AAD);
  libsignal records likewise. Not SQLCipher: structure and metadata are visible.
- The libsignal identity private key is a software key encrypted at rest (DEC-019 O3).
- Key loss (DEC-023, Phase 2): the vault key is created only with a new profile; the profile row
  is the canary. If it no longer decrypts, the app runs nothing and offers "Check again" and a
  confirmed "Delete local data and start over". Vault failures are unchecked and never become
  protocol verdicts.
- Contact states: `AWAITING_ACCEPTANCE`, `ACTIVE`, `KEY_CHANGED` (blocked). Pending requests are
  not contacts. Strict identity trust in both directions (DEC-018). Single device per contact; no
  synchronized revocation.
- Flash storage gives no secure-deletion guarantee.

## 7. Relay and infrastructure

- **ACTIVE for development/QA:** Node relay in `android-app/server/relay`, API v3 (DEC-024):
  `POST /v1/queues/{queueId}/messages`, `GET` and `DELETE` on the same path with a Bearer read
  secret, `GET /health`. Queue IDs are self-certifying; the relay assigns message IDs.
- The relay is untrusted, has QA-level limits only (not production abuse control), and observes
  IPs, queue IDs, routing tags, timing, sizes, and the initiator's identity key in PreKey headers.
- Two-phone QA on 2026-09-30 used a relay on the laptop reached over USB (`adb reverse`).
- **HISTORICAL/UNVERIFIED:** the VPS relay (`http://194.87.226.46`, HTTP by IP, legacy protocol).

## 8. Modules

`:app`, `:core:domain`, `:core:storage`, `:core:transport`, `:core:ui`, `:core:signal-protocol`,
`:core:signalstore`. Relay: `android-app/server/relay`.

## 9. Verification baseline (last run 2026-09-30)

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

Evidence of the last run is recorded in `REWRITE_PLAN.md` (step 1f) and `PHASE_1E_PREP.md`. CI
(`.github/workflows/android.yml`) runs assemble, lint, unit tests, relay tests, and emulator
UI/storage tests; its current result is **UNVERIFIED** from this machine.

## 10. Explicitly not implemented or not complete

Independent review; v2 in release builds; multi-device fan-out and synchronized revocation; relay
production relay abuse controls; read receipts; app lock; encrypted backup; attachments; secure
wipe; production relay and HTTPS endpoint; reproducible build, signing, SBOM; external audit.

## 11. Update rules

Update this file when the active protocol path, release gating, module list, Room schema,
relay API or trust assumptions, delivery semantics, build or CI commands, or production-readiness
claims change. Keep it factual and compact; plans go to `TASK_QUEUE.md`, rationale to
`DECISIONS.md`, session notes to `HANDOFF.md`.
