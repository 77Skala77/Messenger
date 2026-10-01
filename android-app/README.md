# Secure Messenger MVP / Защищённый мессенджер MVP

Native Android security-oriented prototype of a private 1-to-1 messenger. Russian is the default
in-app language; English is available from Settings.

> **Security warning:** this is an MVP for architecture review and testing. It is not ready for
> real sensitive communications and makes no production-security claim. The protocol
> integration has **not** had an independent security review.

## Current state (2026-09-30)

- **Protocol v2 is the only chat path:** official libsignal 0.94.1 sessions — PQXDH (with a
  Kyber-1024 post-quantum key) and the Double Ratchet. Design, verified properties and limits:
  [docs/PROTOCOL_V2.md](docs/PROTOCOL_V2.md).
- **Debug builds only.** The `enable_v2_protocol` resource is `true` only in debug resources.
  Release builds show a "not for messaging" screen until an independent external audit, planned
  before any release ([DEC-021, DEC-026](docs/ai-workflow/DECISIONS.md)). Phase 5 added an internal
  security test pass ([docs/PHASE5_SECURITY_TEST_PLAN.md](docs/PHASE5_SECURITY_TEST_PLAN.md)).
- **The legacy prototype was removed** (static-ECDH session layer, `STANDARD_AEAD`, v1
  invitations, the `:core:crypto` and `:core:delivery` modules). Old test chats are deleted once,
  explicitly, on upgrade (`MIGRATION_7_8` and `LegacyReset`), with a one-time notice.
  `PARANOIA_C4_AEAD` was removed earlier (DEC-016).
- Verified on 2026-09-30 by automated tests (JVM and two physical phones) and by hand on a
  Samsung S24 Ultra and a Redmi A3x: pairing, safety numbers, messages, `Delivered` receipts,
  restart, relay outage and recovery, forward secrecy and post-compromise security tests,
  libsignal under R8.

## Features

- Compose UI: onboarding, chats, contact requests, invitation QR/scan, settings.
- One-sided pairing: one person scans a small QR invitation; the other person gets a contact
  request with a 60-digit safety number and must accept it explicitly. Trust on first use; compare
  the safety number through a trusted channel.
- `Sent to delivery server` means relay acceptance only. `Delivered` appears only after an
  authenticated receipt from the contact. It is not a read receipt; read receipts are not
  implemented.
- Messages, names and the invitation text are stored encrypted with an Android Keystore-backed
  key (AES-GCM). Stored history is not protected by forward secrecy.
- Durable outgoing queue with retries; foreground polling while the chat is open and best-effort
  WorkManager background sync (about every 15 minutes or later, as Android allows).
- Minimal Node relay: opaque blobs, deletion, TTL, `/health`. The relay is untrusted.

## Not implemented / limits

Single device per contact; no synchronized revocation; relay authorization and limits are QA-level
(Phase 3); no app lock, backup, attachments, or secure erase; Android deletion cannot guarantee
erasure of flash-storage blocks. See [Known Limitations](docs/KNOWN_LIMITATIONS.md).

## Modules

`domain` (relay transport interface), `transport` (HTTP relay client, relay settings),
`signalstore` (neutral storage boundaries), `storage` (Room v8, Keystore vault, encrypted
records), `signal-protocol` (libsignal adapter, invitations, envelopes, the v2 pipeline), `ui`
(Compose, view models, background sync, legacy reset), `app`.

## Build Android App

Requirements: JDK 17 and Android SDK Platform 34. On low-memory machines (about 4 GB RAM) add
`"-Dorg.gradle.jvmargs=-Xmx1280m" "-Pkotlin.compiler.execution.strategy=in-process" --max-workers=1`.

```bash
cd android-app
./gradlew :app:assembleDebug
./gradlew :app:lintDebug :core:signal-protocol:testDebugUnitTest :core:signalstore:test test
```

Install on an API 31+ device: `./gradlew :app:installDebug`. The `qaMinified` build type
(`./gradlew :app:assembleQaMinified`) applies the release R8 rules with debug signing, to check
libsignal under minification; it installs next to the debug app as `app.securemessenger.qa`.

Instrumented tests (device or emulator; the transport test needs the relay and `adb reverse`):

```bash
./gradlew :core:signal-protocol:connectedDebugAndroidTest :core:storage:connectedDebugAndroidTest \
  :core:ui:connectedDebugAndroidTest :core:transport:connectedDebugAndroidTest
```

## Run Relay Server

The relay needs only Node.js 20+:

```bash
cd android-app/server/relay
node --test
node src/server.mjs
```

It listens on `0.0.0.0:8787`. A physical phone on the same Wi-Fi uses the laptop LAN address in
`Настройки` → `Сервер доставки`, for example `http://192.168.1.45:8787`; the emulator default is
`http://10.0.2.2:8787`. Over a USB cable, `adb reverse tcp:8787 tcp:8787` lets the phone use
`http://127.0.0.1:8787` (this is how the two-phone tests of 2026-09-30 were run). Release builds
reject cleartext relay URLs.

The relay (API v3, DEC-024) implements `POST /v1/queues/{queueId}/messages` (anyone),
`GET /v1/queues/{queueId}/messages` and `DELETE /v1/queues/{queueId}/messages/{messageId}` (both need
`Authorization: Bearer <read secret>`; the queue ID is a hash of that secret) and `GET /health`. Start
it with `node server/relay/src/server.mjs` (also on Windows). It stores opaque envelopes and expiry
times but can observe queue IDs, routing tags, timing, sizes, source IPs and traffic frequency.

### Historical VPS evidence

Historical QA (legacy protocol, before 2026-09-27) used Ubuntu 24.04, the Node relay behind Caddy
with UFW, and a temporary HTTP-by-IP endpoint (`http://194.87.226.46`) that was never suitable
for release or production. Current VPS state is unverified; see
[docs/VPS_RELAY_DEPLOYMENT.md](docs/VPS_RELAY_DEPLOYMENT.md) and [deploy/README.md](deploy/README.md).

## Localization

The UI defaults to Russian on first start. Select `English` in `Настройки` to switch; the choice
is stored and applied immediately. Strings: `core/ui/src/main/res/values-ru/strings.xml` and
`core/ui/src/main/res/values/strings.xml`. See [docs/LOCALIZATION.md](docs/LOCALIZATION.md).
QR scanning uses a dedicated portrait-only activity; the rest of the app is not orientation-locked.

## Documentation

- [Protocol v2](docs/PROTOCOL_V2.md) — current design
- [Known Limitations](docs/KNOWN_LIMITATIONS.md), [Security Warnings](docs/SECURITY_WARNINGS.md),
  [Threat Model](docs/THREAT_MODEL.md)
- [AI workflow index](docs/ai-workflow/README.md), [Current Project State](docs/ai-workflow/CURRENT_PROJECT_STATE.md),
  [Decisions](docs/ai-workflow/DECISIONS.md), [Rewrite Plan](docs/ai-workflow/REWRITE_PLAN.md),
  [Task Queue](docs/ai-workflow/TASK_QUEUE.md)
- [libsignal Toolchain Review](docs/LIBSIGNAL_TOOLCHAIN_REVIEW.md), [Dependency Review](docs/DEPENDENCY_REVIEW.md)
- Relay and QA: [Relay Public Testing](docs/RELAY_PUBLIC_TESTING.md), [VPS Relay Deployment](docs/VPS_RELAY_DEPLOYMENT.md),
  [Logcat QA](docs/LOGCAT_QA.md)
- Historical (removed legacy prototype): [Crypto Design](docs/CRYPTO_DESIGN.md),
  [MVP Delivery Baseline](docs/MVP_DELIVERY_BASELINE.md), [Two Device Test](docs/TWO_DEVICE_TEST.md),
  [Manual Two Device QA](docs/MANUAL_TWO_DEVICE_QA.md), [Device Trust Policy](docs/ai-workflow/DEVICE_TRUST_POLICY.md),
  [Signal Migration Spike](docs/SIGNAL_MIGRATION_SPIKE.md)
