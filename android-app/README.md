# Secure Messenger MVP / Защищённый мессенджер MVP

Native Android audit-ready prototype of a private 1-to-1 messenger. Russian is the
default in-app language; English is available from Settings.

> **Security warning:** this is an MVP for architecture review and testing. It is
> not ready for real sensitive communications and makes no production-security claim.

## Implemented In This Version

- Kotlin and Jetpack Compose application split into `domain`, `crypto`, `storage`,
  `transport`, and `ui` modules.
- Russian-first onboarding, contact, conversation, contact security, and settings screens.
- Persistent manual language selection (`Русский` / `English`); first launch selects Russian.
- Per-device EC key agreement key created in Android Keystore on API 31+.
- QR invitation generation, camera scanning, and manual invitation import.
- Contact verification state and key-change warning UI.
- Working `STANDARD_AEAD`: AES-256-GCM with authenticated associated data and
  persisted counter-based nonce allocation.
- Per-message HKDF-derived encryption keys and replay tracking in the prototype session layer.
- Room persistence with no plaintext message column; profile name and session state
  are encrypted with a Keystore-backed local vault key.
- Abstract transport client and a minimal Node relay with opaque blobs, delivery deletion, and TTL.
- Disabled `PARANOIA_C4_AEAD` module boundary and explicit experimental warning.

## Placeholders And Boundaries

- The simplified static ECDH session is **not** Signal X3DH/PQXDH or Double Ratchet:
  it does not yet provide the required production-grade forward secrecy or post-compromise security.
- Relay transport classes and the pending-message pipeline exist, but the UI does not yet
  run background sync/delivery; sent demo messages remain locally pending.
- Restored conversation history decryption, device-link approval workflow, app lock,
  biometric unlock, encrypted backup, attachments, and secure wipe are not implemented.
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
./gradlew testDebugUnitTest
```

Install on an API 31+ emulator or device:

```bash
./gradlew :app:installDebug
```

The generated debug APK is under `app/build/outputs/apk/debug/`.

## Run Relay Server

The relay has no dependencies beyond Node.js 20+:

```bash
cd android-app/server/relay
node --test
node src/server.mjs
```

It listens on `http://127.0.0.1:8787` by default and implements:

```text
POST   /v1/messages
GET    /v1/messages/{queueId}
DELETE /v1/messages/{messageId}
```

The service stores opaque ciphertext envelopes and expiration timestamps only. In
this MVP it can still observe queue identifiers, timing, message size and source IP.

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
