# Secure Messenger MVP / Защищённый мессенджер MVP

Native Android audit-ready prototype of a private 1-to-1 messenger. Russian is the
default in-app language; English is available from Settings.

> **Security warning:** this is an MVP for architecture review and testing. It is
> not ready for real sensitive communications and makes no production-security claim.

## Implemented In This Version

- Kotlin and Jetpack Compose application split into `domain`, `crypto`, `delivery`,
  `storage`, `transport`, and `ui` modules.
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
- Durable pending/failed outgoing state with attempt count and exponential retry metadata.
  A foreground-only retry coordinator resends due ciphertext without logging its contents.
- A two-peer integration test exercises reciprocal invitations, encrypted relay delivery,
  authenticated receive/storage/delete, duplicate replay suppression and tamper rejection.
- Room persistence with no plaintext message column; profile name and session state
  are encrypted with a Keystore-backed local vault key. Session send counters survive restart.
- Abstract transport client and a minimal Node relay with opaque blobs, delivery deletion, and TTL.
- Explicit device states (`pending`, `verified`, `revoked`, `key_changed`). The current
  single-device delivery policy fails closed for pending, revoked, changed or missing
  primary device state and never silently fans out to a secondary device.
- Disabled `PARANOIA_C4_AEAD` module boundary and explicit experimental warning.
- Disabled official `libsignal` migration module pinned at `0.94.1`; it compiles as a
  dependency gate but is not used by the running MVP.

## Placeholders And Boundaries

- The simplified static ECDH session is **not** Signal X3DH/PQXDH or Double Ratchet:
  it does not yet provide the required production-grade forward secrecy or post-compromise security.
- Official `libsignal` is not switched on: encrypted durable Signal protocol stores,
  PQXDH/prekey invitations, Kotlin toolchain migration, licensing review and an
  independent integration audit are prerequisites.
- Proper per-device session fan-out, app lock, biometric unlock, encrypted backup,
  attachments, and secure wipe are not implemented. Revocation is local delivery blocking,
  not synchronized protocol revocation.
- Retry metadata and foreground retries are implemented, but `WorkManager`/offline
  background delivery is still a deliberately explicit placeholder.
- The active relay URL is a debug-only localhost development endpoint. Release builds
  disallow cleartext networking and do not yet have a production transport configuration.
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
./gradlew :app:lintDebug :core:signal-protocol:testDebugUnitTest test
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

For an attached Android device, expose only the local development relay through ADB:

```bash
adb reverse tcp:8787 tcp:8787
```

The debug app then reaches `http://127.0.0.1:8787`; the release network policy
does not allow this cleartext endpoint. The relay implements:

```text
POST   /v1/messages
GET    /v1/messages/{queueId}
DELETE /v1/messages/{messageId}
```

The service stores opaque ciphertext envelopes and expiration timestamps only. In
this MVP it can still observe queue identifiers, timing, message size and source IP.

An invitation advertises its creator's receive queue. Consequently, bidirectional
testing requires reciprocal invitation import: B imports A's invitation to send to A,
and A imports B's invitation to send to B. The delivery integration test uses this
two-way setup rather than treating one invitation as a global contact address.

## Localization

The UI defaults to Russian on first start, even on a non-Russian system locale.
Open `Настройки` and select `English` to switch; the choice is stored locally and
applied after restart. UI text is kept in Android resource files:

- `core/ui/src/main/res/values-ru/strings.xml` - Russian.
- `core/ui/src/main/res/values/strings.xml` - English.

See [docs/LOCALIZATION.md](docs/LOCALIZATION.md) for the checklist.

## Documentation

- [Threat Model](docs/THREAT_MODEL.md)
- [Crypto Design](docs/CRYPTO_DESIGN.md)
- [Security Warnings](docs/SECURITY_WARNINGS.md)
- [Roadmap](docs/ROADMAP.md)
- [Localization](docs/LOCALIZATION.md)
- [Dependency Review](docs/DEPENDENCY_REVIEW.md)
- [Signal Migration Spike](docs/SIGNAL_MIGRATION_SPIKE.md)
- [Audit Readiness](docs/AUDIT_READINESS.md)
