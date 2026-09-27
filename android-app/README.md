# Secure Messenger MVP / Защищённый мессенджер MVP

Native Android security-oriented prototype of a private 1-to-1 messenger. Russian is
the default in-app language; English is available from Settings.

> **Security warning:** this is an MVP for architecture review and testing. It is not
> ready for real sensitive communications and makes no production-security claim.
> Encryption of an unverified first exchange does not prove who owns the remote key.

## Implemented In This Version

- Kotlin and Jetpack Compose application split into `domain`, `crypto`, `delivery`,
  `storage`, `signalstore`, `transport`, and `ui` modules.
- Russian-first onboarding, contact, conversation, contact-security, and settings screens.
- Persistent manual language selection (`Русский` / `English`); first launch selects Russian.
- Per-device EC key-agreement key created in Android Keystore on API 31+.
- QR invitation generation, camera scanning, and manual invitation import.
- Contact identity verification status, safety-number display, key-change warnings, and
  per-device approval state.
- Working `STANDARD_AEAD`: AES-256-GCM with authenticated associated data and persisted
  counter-based nonce allocation.
- Per-message HKDF-derived encryption keys and replay tracking in the prototype session layer.
- Development relay send/fetch/delete pipeline. Authenticated incoming envelopes are
  durably recorded with their replay marker before relay deletion where required by
  the current pipeline.
- Encrypted delivery receipts for accepted incoming messages. `Sent to delivery server`
  means relay acceptance only; `Delivered` appears only after the receiver accepted,
  decrypted, stored the message and sent an authenticated receipt. Read receipts are
  not implemented.
- Durable pending/failed outgoing state with attempt count and exponential retry metadata.
  Foreground polling and WorkManager best-effort synchronization resend due ciphertext
  without logging its contents.
- A two-peer integration test exercises reciprocal invitations, encrypted relay delivery,
  authenticated receive/storage/delete, duplicate replay suppression and tamper rejection.
- Room persistence with no plaintext message column; profile name and session state are
  encrypted with a Keystore-backed local vault key. Session send counters survive restart.
- Saved per-device development relay URL, `/health` availability check and manual
  synchronization action. The emulator default is `http://10.0.2.2:8787`; a physical
  device can use a laptop LAN address supplied in Settings. Phone + emulator QA can
  instead use a shared public HTTPS relay/tunnel URL.
- A delivery diagnostics screen with persisted health/sync observations, pending and retry
  counters, pairing/device/worker status, grouped technical events, human-readable event
  times and a last-error label. Copied reports show short queue fingerprints rather than
  full queue identifiers.
- Debug-only two-device QA controls: a readiness preflight, physical-phone warnings for
  loopback/bind-only relay addresses, and a confirmed test-data reset. The reset removes
  current local test/crypto/delivery/diagnostic state while retaining the selected Relay
  URL for repeated LAN checks. It is not a forensic secure-erasure guarantee.
- Abstract transport client and a minimal Node relay with opaque blobs, delivery deletion,
  TTL, `/health`, and LAN development binding.
- Warning-only trust on first use for a newly imported remote contact after reciprocal
  invitation import. The app continues to display an identity-not-verified warning until
  the safety number or verification QR is checked through a trusted or independent channel.
- Primary-only delivery policy. The current planner never silently selects a secondary device.
- `PARANOIA_C4_AEAD` was removed entirely (2026-09-27) — it is not present in any form,
  not merely disabled. See `docs/ai-workflow/DECISIONS.md` DEC-016.
- Disabled official `libsignal` migration module pinned at `0.94.1`; it compiles as a
  dependency gate but is not used by the running MVP.
- Disabled `core:signalstore` spike: neutral Signal record interfaces, vault-encrypted
  Room record tables and experimental v2 PQXDH invitation DTOs; no working chat uses it.

Historical repository QA records describe successful phone + emulator delivery,
automatic retry after relay recovery, delivery receipts, duplicate suppression, Room
restart persistence, and VPS relay checks. These results are useful context, but they
are not current verification for a later commit or current infrastructure state.

## Identity Trust And Device Delivery

The project has two separate persisted trust models. They must not be collapsed into
one informal “device state” list.

Contact-level identity verification:

```text
VerificationStatus.VERIFIED
VerificationStatus.UNVERIFIED
VerificationStatus.KEY_CHANGED
VerificationStatus.UNKNOWN
```

Per-contact-device delivery approval:

```text
DeviceApprovalStatus.PRIMARY_UNVERIFIED
DeviceApprovalStatus.VERIFIED
DeviceApprovalStatus.PENDING_APPROVAL
DeviceApprovalStatus.KEY_CHANGED
DeviceApprovalStatus.REVOKED
```

After reciprocal invitation import, current warning-only trust-on-first-use policy
allows communication when:

- the contact is `VerificationStatus.UNVERIFIED`; and
- the matching primary device is `DeviceApprovalStatus.PRIMARY_UNVERIFIED`.

This permits remote first contact when an in-person meeting or pre-existing independent
secure channel is unavailable. It does **not** prove that the public key belongs to the
intended human. The UI must continue to show an unverified-identity warning, and the
state must not be described as `VERIFIED` or suitable for real sensitive communication.

Later safety-number or verification-QR comparison through a trusted or independent
channel may move both the contact and primary device to `VERIFIED`.

Current delivery remains blocked for:

- `VerificationStatus.KEY_CHANGED`;
- `DeviceApprovalStatus.PENDING_APPROVAL`;
- `DeviceApprovalStatus.KEY_CHANGED`;
- `DeviceApprovalStatus.REVOKED`;
- a missing primary-device record.

Policy also requires malformed, unsupported, corrupt, and
`VerificationStatus.UNKNOWN` trust state to fail closed. Current runtime and regression
evidence does not yet prove every unknown/invalid persistence and migration case; this
is an open hardening and test task, not an implemented guarantee.

Room stores exact uppercase enum names in `contacts.verificationStatus` and
`contact_devices.approvalStatus`. Do not rename, lowercase, merge, or reinterpret those
values without a Room migration, compatibility analysis, rollback plan, and regression
tests.

The current primary-only planner permits `PRIMARY_UNVERIFIED` and `VERIFIED`, blocks
pending/revoked/key-changed/missing primary state, and does not silently fan out to a
secondary device. Local revocation is local delivery blocking, not synchronized
cryptographic protocol revocation.

See
[Device Trust Policy](docs/ai-workflow/DEVICE_TRUST_POLICY.md)
for the accepted decision and persistence boundary.

## Placeholders And Boundaries

- The simplified static ECDH session is **not** Signal X3DH/PQXDH or Double Ratchet. It
  does not provide production-grade forward secrecy or post-compromise security.
- Official `libsignal` is not switched on. The storage-only opaque record boundary is
  not wired to official library types or message processing. PQXDH semantics, Kotlin
  toolchain migration, licensing review, and an independent integration audit remain
  prerequisites.
- Proper per-device session fan-out, synchronized revocation, app lock, biometric unlock,
  encrypted backup, attachments, and secure wipe are not implemented.
- WorkManager sync is implemented for due outgoing retry and incoming fetch, but it is
  best effort and may be delayed by Android power management. It is not an assurance of
  timely delivery. Manual sync remains useful for QA and immediate checks.
- The user-selected HTTP relay URL is allowed only by the debug development policy. The
  Settings screen warns about HTTP in debug builds. Release builds reject HTTP relay
  URLs and disallow cleartext networking; they do not yet have a production transport.
- The debug-only reset control is for disposable QA state; it keeps the configured Relay
  URL by design and is not a secure-erasure guarantee.
- Room stores ciphertext rather than using SQLCipher; routing AAD and database shape still
  expose local metadata.
- Android deletion cannot guarantee erasure of historical flash-storage blocks.
- Rust/UniFFI crypto integration is deliberately deferred; the crypto boundary is Kotlin-first.

## Crypto Mode

`STANDARD_AEAD` is the only executable encryption suite. It uses JCA
`AES/GCM/NoPadding`, 256-bit keys, a 96-bit nonce assembled from a random per-key
prefix and durable monotonic counter, a 128-bit authentication tag, and bound AAD.

`PARANOIA_C4_AEAD` — a proposed Threefish/Serpent/AES/SHACAL-2 cascade — was removed
from the codebase entirely on 2026-09-27 rather than kept as a disabled placeholder.
It never had reviewed primitive implementations, domain-separated KDF/MAC design, or
test vectors, and a home-grown cascade would not have added provable security over a
single reviewed AEAD suite. See `docs/ai-workflow/DECISIONS.md` DEC-016.

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

With a USB-debugging device connected, run the instrumented checks for saved language
selection, Room delivery/replay persistence, Android Keystore behavior, and the real
HTTP relay delivery scenario. For the transport test, start the Node relay and apply
the `adb reverse` command shown below first:

```bash
./gradlew :core:ui:connectedDebugAndroidTest   :core:storage:connectedDebugAndroidTest   :core:transport:connectedDebugAndroidTest
```

## Run Relay Server

The relay has no dependencies beyond Node.js 20+:

```bash
cd android-app/server/relay
node --test
node src/server.mjs
```

The development server listens on `0.0.0.0:8787` so physical devices on the same test
Wi-Fi network can enter the laptop LAN URL in `Настройки` → `Сервер доставки`, for
example `http://192.168.1.45:8787`. The emulator default is
`http://10.0.2.2:8787`.

For one attached Android device, an optional USB route is:

```bash
adb reverse tcp:8787 tcp:8787
```

With that route, set the debug app URL to `http://127.0.0.1:8787`. The release network
policy does not allow cleartext endpoints.

For phone + emulator tests through different networks, use one HTTPS relay URL on both
clients, for example `https://temporary-relay.example`. For deployment examples, see
[docs/VPS_RELAY_DEPLOYMENT.md](docs/VPS_RELAY_DEPLOYMENT.md) and
[deploy/README.md](deploy/README.md).

The relay implements:

```text
POST   /v1/messages
GET    /v1/messages/{queueId}
DELETE /v1/messages/{messageId}
GET    /health
```

The service stores opaque ciphertext envelopes and expiration timestamps only. It can
still observe queue identifiers, timing, message size, source IP, and traffic frequency.
Local relay is appropriate for debug QA. A public relay remains untrusted QA transport,
not production infrastructure.

### Historical VPS evidence

The repository records historical QA using Ubuntu 24.04 LTS, the Node relay bound to
`127.0.0.1:8787`, Caddy, UFW blocking direct external access to `8787/tcp`, and SSH
public-key login with password and keyboard-interactive login disabled.

The historical temporary endpoint was:

```text
http://194.87.226.46
```

Historical phone + Android Emulator tests reported two-way delivery, automatic retry,
delivery receipts, duplicate suppression, and reboot recovery. This HTTP-by-IP endpoint
was not suitable for release or production use.

Current VPS availability, ownership, DNS, TLS, Caddy, systemd, firewall, logs, retention,
abuse controls, SSH configuration, and endpoint reachability are unverified. Do not use
the historical endpoint as current infrastructure without fresh authorization and
verification.

An invitation advertises its creator's receive queue. Bidirectional testing therefore
requires reciprocal invitation import:

- B imports A's invitation to send to A.
- A imports B's invitation to send to B.

The delivery integration test uses this two-way setup rather than treating one
invitation as a global contact address.

See [docs/TWO_DEVICE_TEST.md](docs/TWO_DEVICE_TEST.md) for the physical two-device LAN
procedure and security limitations. For a test-day checklist including safe diagnostic
capture, use [docs/MANUAL_TWO_DEVICE_QA.md](docs/MANUAL_TWO_DEVICE_QA.md). When a second
phone is unavailable, see
[docs/PHONE_EMULATOR_PUBLIC_RELAY_QA.md](docs/PHONE_EMULATOR_PUBLIC_RELAY_QA.md)
and [docs/RELAY_PUBLIC_TESTING.md](docs/RELAY_PUBLIC_TESTING.md).

## Delivery Diagnostics

Open `Настройки` → `Диагностика доставки` in the debug app. The copied report contains
relay configuration, last health/sync status, delivery counts, retry time,
pairing/device/worker status, last-error text, and the latest grouped technical events
with local timestamps. It deliberately excludes message text, keys, nonce values,
ciphertext, and full queue identifiers.

Clearing diagnostics removes technical history but does not silently change pairing or
trust state.

Before a physical two-device send, open `Настройки` → `Готовность к тесту переписки`
and run preflight after saving the LAN Relay URL and selecting the conversation.

On a real phone:

- `127.0.0.1` and `localhost` point to that phone, not to the laptop;
- `0.0.0.0` is a server bind address, not a client URL;
- use the laptop Wi-Fi IP for LAN testing;
- use a shared public HTTPS relay/tunnel URL for clients on different networks.

Debug-only `Сбросить тестовые данные` requires confirmation and returns the app to
onboarding while keeping the selected Relay URL.

For sanitized crash/worker/storage/camera/network capture during manual testing, use
[docs/LOGCAT_QA.md](docs/LOGCAT_QA.md). Its local `qa-logcat.txt` output is ignored by
Git and must not contain real message content or secrets.

## Localization

The UI defaults to Russian on first start, even on a non-Russian system locale. Open
`Настройки` and select `English` to switch. The choice is stored locally and applied
immediately and after restart. Each language option row is clickable across its full
width, including its label and radio indicator.

UI text is kept in:

- `core/ui/src/main/res/values-ru/strings.xml` — Russian.
- `core/ui/src/main/res/values/strings.xml` — English.

See [docs/LOCALIZATION.md](docs/LOCALIZATION.md) for the checklist.

QR invitation import and QR safety verification launch a dedicated portrait-only
scanner activity. The rest of the messenger is not orientation-locked.

## Documentation

### Architecture, security, and project state

- [Threat Model](docs/THREAT_MODEL.md)
- [Crypto Design](docs/CRYPTO_DESIGN.md)
- [Security Warnings](docs/SECURITY_WARNINGS.md)
- [Known Limitations](docs/KNOWN_LIMITATIONS.md)
- [MVP Delivery Baseline](docs/MVP_DELIVERY_BASELINE.md)
- [Audit Scope](docs/AUDIT_SCOPE.md)
- [Audit Readiness](docs/AUDIT_READINESS.md)
- [Audit Checklist](docs/AUDIT_CHECKLIST.md)
- [Security-Critical Files](docs/SECURITY_CRITICAL_FILES.md)

### AI workflow and accepted decisions

- [AI Workflow Index](docs/ai-workflow/README.md)
- [Current Project State](docs/ai-workflow/CURRENT_PROJECT_STATE.md)
- [Decisions](docs/ai-workflow/DECISIONS.md)
- [Device Trust Policy](docs/ai-workflow/DEVICE_TRUST_POLICY.md)
- [Task Queue](docs/ai-workflow/TASK_QUEUE.md)

### Signal and dependency review

- [Roadmap](docs/ROADMAP.md)
- [Dependency Review](docs/DEPENDENCY_REVIEW.md)
- [Signal Migration Spike](docs/SIGNAL_MIGRATION_SPIKE.md)
- [libsignal Toolchain Review](docs/LIBSIGNAL_TOOLCHAIN_REVIEW.md)

### QA, relay, deployment, and localization

- [Localization](docs/LOCALIZATION.md)
- [Two Device Test](docs/TWO_DEVICE_TEST.md)
- [Manual Two Device QA](docs/MANUAL_TWO_DEVICE_QA.md)
- [Phone + Emulator Public Relay QA](docs/PHONE_EMULATOR_PUBLIC_RELAY_QA.md)
- [Relay Public Testing Notes](docs/RELAY_PUBLIC_TESTING.md)
- [VPS Relay Deployment](docs/VPS_RELAY_DEPLOYMENT.md)
- [Relay Deployment Examples](deploy/README.md)
- [Logcat QA](docs/LOGCAT_QA.md)
