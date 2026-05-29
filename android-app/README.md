# Secure Messenger MVP / Защищённый мессенджер MVP

Native Android audit-ready prototype of a private 1-to-1 messenger. Russian is the
default in-app language; English is available from Settings.

> **Security warning:** this is an MVP for architecture review and testing. It is
> not ready for real sensitive communications and makes no production-security claim.

## Implemented In This Version

- Kotlin and Jetpack Compose application split into `domain`, `crypto`, `delivery`,
  `storage`, `signalstore`, `transport`, and `ui` modules.
- Russian-first onboarding, contact, conversation, contact security, and settings screens.
- Persistent manual language selection (`Русский` / `English`); first launch selects Russian.
- Per-device EC key agreement key created in Android Keystore on API 31+.
- QR invitation generation, camera scanning, and manual invitation import.
- Contact verification state and key-change warning UI.
- Working `STANDARD_AEAD`: AES-256-GCM with authenticated associated data and
  persisted counter-based nonce allocation.
- Per-message HKDF-derived encryption keys and replay tracking in the prototype session layer.
- Real debug relay send/fetch/delete pipeline. Received authenticated envelopes are durably
  recorded with their replay marker before they are deleted from the relay while the
  app process is active.
- Encrypted delivery receipts for accepted incoming messages. `Sent to delivery
  server` means relay acceptance only; `Delivered` appears only after the receiver
  accepted, decrypted, stored the message and sent an authenticated receipt. Read
  receipts are not implemented.
- Durable pending/failed outgoing state with attempt count and exponential retry metadata.
  Foreground polling and WorkManager best-effort synchronization resend due ciphertext
  without logging its contents.
- Confirmed phone + emulator QA shows automatic retry after relay recovery:
  `Waiting for sync` messages can be resent by background sync / WorkManager without
  requiring manual sync, then move to `Delivered` after the receiver's delivery receipt.
- A two-peer integration test exercises reciprocal invitations, encrypted relay delivery,
  authenticated receive/storage/delete, duplicate replay suppression and tamper rejection.
- Room persistence with no plaintext message column; profile name and session state
  are encrypted with a Keystore-backed local vault key. Session send counters survive restart.
- Saved per-device development relay URL, `/health` availability check and manual
  synchronization action. The emulator default is `http://10.0.2.2:8787`; a
  physical device can use a laptop LAN address supplied in Settings. Phone +
  emulator QA can instead use a shared public HTTPS relay/tunnel URL.
- A delivery diagnostics screen with persisted health/sync observations, pending and
  retry counters, pairing/device/worker status, grouped technical events, human-readable
  event times and a last-error label. Copied reports show short queue fingerprints
  rather than full queue identifiers.
- Debug-only two-device QA controls: a readiness preflight, physical-phone warnings
  for loopback/bind-only relay addresses, and a confirmed test-data reset. The reset
  wipes local test/crypto/delivery/diagnostic state while retaining the selected
  Relay URL for repeated LAN checks.
- Abstract transport client and a minimal Node relay with opaque blobs, delivery
  deletion, TTL, `/health`, and LAN development binding.
- Explicit device states (`pending`, `verified`, `revoked`, `key_changed`). The current
  single-device delivery policy fails closed for pending, revoked, changed or missing
  primary device state and never silently fans out to a secondary device.
- Disabled `PARANOIA_C4_AEAD` module boundary and explicit experimental warning.
- Disabled official `libsignal` migration module pinned at `0.94.1`; it compiles as a
  dependency gate but is not used by the running MVP.
- Disabled `core:signalstore` spike: neutral Signal record interfaces, vault-encrypted
  Room record tables and experimental v2 PQXDH invitation DTOs; no working chat uses it.

## Placeholders And Boundaries

- The simplified static ECDH session is **not** Signal X3DH/PQXDH or Double Ratchet:
  it does not yet provide the required production-grade forward secrecy or post-compromise security.
- Official `libsignal` is not switched on: the new storage-only opaque record boundary
  is not wired to official library types or message processing. PQXDH semantics,
  Kotlin toolchain migration, licensing review and an independent integration audit
  remain prerequisites.
- Proper per-device session fan-out, app lock, biometric unlock, encrypted backup,
  attachments, and secure wipe are not implemented. Revocation is local delivery blocking,
  not synchronized protocol revocation.
- WorkManager sync is implemented for due outgoing retry and incoming fetch, but it
  is best effort and may be delayed by Android power management. It is not an
  assurance of timely delivery. Manual sync remains useful for QA and immediate
  checks, even though confirmed testing showed automatic retry after relay recovery.
- The user-selected HTTP relay URL is allowed only by the debug development policy.
  The Settings screen warns about HTTP in debug builds. Release builds reject HTTP
  relay URLs and disallow cleartext networking; they do not yet have a production transport.
- The debug-only reset control is for disposable QA state; it keeps the configured
  Relay URL by design and is not a secure-erasure guarantee.
- Room stores ciphertext rather than using SQLCipher; routing AAD and database shape still
  expose local metadata.
- Android secure deletion cannot guarantee erasure of historical flash-storage blocks.
- Rust/UniFFI crypto integration is deliberately deferred; the crypto boundary is Kotlin-first.

## Crypto Mode

`STANDARD_AEAD` is the only executable encryption suite. It uses JCA
`AES/GCM/NoPadding`, 256-bit keys, a 96-bit nonce assembled from a random per-key
prefix and durable monotonic counter, a 128-bit authentication tag, and bound AAD.

`PARANOIA_C4_AEAD` is an isolated, disabled placeholder. It contains no home-grown
cascade or fallback encryption. It cannot be enabled as secure until independently
reviewed implementations of Threefish, Serpent, AES and SHACAL-2, domain-separated
KDF/MAC design, test vectors, and an audit are available.

## Build Android App

Requirements: JDK 17 and Android SDK Platform 34.

```bash
cd android-app
./gradlew :app:assembleDebug
./gradlew :app:lintDebug :core:signal-protocol:testDebugUnitTest :core:signalstore:test test
```

Install on an API 31+ emulator or device:

```bash
./gradlew :app:installDebug
```

The generated debug APK is under `app/build/outputs/apk/debug/`.

With a USB-debugging device connected, run the instrumented checks for saved
language selection, Room delivery/replay persistence, Android Keystore behavior and
the real HTTP relay delivery scenario. For the transport test, start the Node relay
and apply the `adb reverse` command shown below first:

```bash
./gradlew :core:ui:connectedDebugAndroidTest :core:storage:connectedDebugAndroidTest :core:transport:connectedDebugAndroidTest
```

## Run Relay Server

The relay has no dependencies beyond Node.js 20+:

```bash
cd android-app/server/relay
node --test
node src/server.mjs
```

The development server listens on `0.0.0.0:8787` so physical devices on the same
test Wi-Fi network can enter the laptop LAN URL in `Настройки` -> `Сервер доставки`,
for example `http://192.168.1.45:8787`. The emulator default is
`http://10.0.2.2:8787`. For a single attached Android device, an optional USB route is:

```bash
adb reverse tcp:8787 tcp:8787
```

With that USB route, set the debug app URL to `http://127.0.0.1:8787`. The release
network policy does not allow cleartext endpoints. For phone + emulator tests
through different networks, use one HTTPS relay URL on both clients, for example
`https://temporary-relay.example`. The relay implements:

```text
POST   /v1/messages
GET    /v1/messages/{queueId}
DELETE /v1/messages/{messageId}
GET    /health
```

The service stores opaque ciphertext envelopes and expiration timestamps only. In
this MVP it can still observe queue identifiers, timing, message size and source IP.

An invitation advertises its creator's receive queue. Consequently, bidirectional
testing requires reciprocal invitation import: B imports A's invitation to send to A,
and A imports B's invitation to send to B. The delivery integration test uses this
two-way setup rather than treating one invitation as a global contact address.
See [docs/TWO_DEVICE_TEST.md](docs/TWO_DEVICE_TEST.md) for the physical two-device
LAN verification procedure and its security limitations. For a test-day checklist
including safe diagnostic capture, use
[docs/MANUAL_TWO_DEVICE_QA.md](docs/MANUAL_TWO_DEVICE_QA.md).
If a second phone is not available, use
[docs/PHONE_EMULATOR_PUBLIC_RELAY_QA.md](docs/PHONE_EMULATOR_PUBLIC_RELAY_QA.md)
and [docs/RELAY_PUBLIC_TESTING.md](docs/RELAY_PUBLIC_TESTING.md).

Confirmed QA: phone + Android Emulator through the local Node relay delivered
messages both ways, returned encrypted delivery receipts, updated outgoing status
from `Sent to delivery server` to `Delivered`, recovered retryable messages after
relay restart through background sync / WorkManager, and did not show duplicate
chat messages. `Delivered` is a delivery receipt from the receiving device, not a
read receipt.

## Delivery Diagnostics

Open `Настройки` -> `Диагностика доставки` in the debug app. The copied report
contains relay configuration, last health/sync status, delivery counts, retry time,
pairing/device/worker status, last-error text and the latest 100 grouped technical
events with local `HH:mm:ss` timestamps. It deliberately excludes message text, keys,
nonce values, ciphertext and full queue identifiers.
Clearing diagnostics removes technical history but does not silently change pairing state.

Before a physical two-device send, open `Настройки` -> `Готовность к тесту
переписки` and run preflight after saving the LAN Relay URL and selecting the
conversation. On a real phone, `127.0.0.1` and `localhost` point to that phone,
not to the laptop; `0.0.0.0` is a server bind address, not a client URL. Use the
laptop Wi-Fi IP for LAN, or a public HTTPS relay/tunnel URL for phone + emulator
tests across different networks. Debug-only `Сбросить тестовые данные` requires
confirmation and returns the app to onboarding while keeping that Relay URL.

For sanitized crash/worker/storage/camera/network capture during manual testing,
use [docs/LOGCAT_QA.md](docs/LOGCAT_QA.md). Its local `qa-logcat.txt` output is
ignored by git and must not contain real message content or secrets.

## Localization

The UI defaults to Russian on first start, even on a non-Russian system locale.
Open `Настройки` and select `English` to switch; the choice is stored locally and
applied immediately and after restart. Each language option row is clickable across
its full width, including its label and radio indicator. UI text is kept in Android
resource files:

- `core/ui/src/main/res/values-ru/strings.xml` - Russian.
- `core/ui/src/main/res/values/strings.xml` - English.

See [docs/LOCALIZATION.md](docs/LOCALIZATION.md) for the checklist.

QR invitation import and QR safety verification launch a dedicated portrait-only
scanner activity. The rest of the messenger is not orientation-locked.

## Documentation

- [Threat Model](docs/THREAT_MODEL.md)
- [Crypto Design](docs/CRYPTO_DESIGN.md)
- [Security Warnings](docs/SECURITY_WARNINGS.md)
- [Roadmap](docs/ROADMAP.md)
- [Localization](docs/LOCALIZATION.md)
- [Dependency Review](docs/DEPENDENCY_REVIEW.md)
- [Signal Migration Spike](docs/SIGNAL_MIGRATION_SPIKE.md)
- [libsignal Toolchain Review](docs/LIBSIGNAL_TOOLCHAIN_REVIEW.md)
- [MVP Delivery Baseline](docs/MVP_DELIVERY_BASELINE.md)
- [Two Device Test](docs/TWO_DEVICE_TEST.md)
- [Audit Readiness](docs/AUDIT_READINESS.md)
- [Manual Two Device QA](docs/MANUAL_TWO_DEVICE_QA.md)
- [Phone + Emulator Public Relay QA](docs/PHONE_EMULATOR_PUBLIC_RELAY_QA.md)
- [Relay Public Testing Notes](docs/RELAY_PUBLIC_TESTING.md)
- [Logcat QA](docs/LOGCAT_QA.md)
- [Audit Scope](docs/AUDIT_SCOPE.md)
- [Known Limitations](docs/KNOWN_LIMITATIONS.md)
- [Security-Critical Files](docs/SECURITY_CRITICAL_FILES.md)
- [Audit Checklist](docs/AUDIT_CHECKLIST.md)
