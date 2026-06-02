# Secure Messenger MVP / Защищённый мессенджер MVP

Native Android audit-ready prototype for a private 1-to-1 messenger, with a Kotlin
Compose app and a minimal development relay. Russian is the default app language;
English is available in Settings.

> **Security warning:** this repository is an MVP for development verification and
> security review. It is not production-secure and must not be used for real
> sensitive conversations.

Current chats use the prototype `SimplifiedSessionManager`, not Signal
PQXDH/Double Ratchet. The Signal-backed store is a disabled migration spike only.
`PARANOIA_C4_AEAD` is also a disabled placeholder and is not an enabled secure mode.

Confirmed QA: a physical Android phone and Android Emulator exchanged messages via
the local Node relay, delivery receipts updated outgoing messages to `Delivered`,
and WorkManager/background sync resent pending retry messages after relay recovery.
`Delivered` is not a read receipt; user read receipts are not implemented.

## Build And Test

```bash
cd android-app
./gradlew :app:assembleDebug test lintDebug
cd server/relay
node --test
```

Local Node relay is enough for LAN/debug QA. For phone + emulator tests outside one
local network, use a public HTTPS relay URL and the VPS deployment examples under
`android-app/deploy`; this is still QA infrastructure, not a production-security
claim.

Confirmed VPS QA: an Ubuntu 24.04 LTS VPS served the relay through Caddy and
systemd, survived reboot, and delivered messages plus delivery receipts between a
physical phone and Android Emulator. The tested public endpoint was temporary
debug-only HTTP by IP: `http://194.87.226.46`. It is not suitable for release or
production use. SSH public-key login was verified and password login was disabled.
A domain name and HTTPS are required before broader testing.

## Documentation

- [Android app and relay overview](android-app/README.md)
- [Two-device LAN test](android-app/docs/TWO_DEVICE_TEST.md)
- [Manual two-device QA checklist](android-app/docs/MANUAL_TWO_DEVICE_QA.md)
- [Crypto design](android-app/docs/CRYPTO_DESIGN.md)
- [Security warnings](android-app/docs/SECURITY_WARNINGS.md)
- [VPS HTTPS relay deployment](android-app/docs/VPS_RELAY_DEPLOYMENT.md)
- [Relay deployment examples](android-app/deploy/README.md)
- [Audit scope](android-app/docs/AUDIT_SCOPE.md)
- [Known limitations](android-app/docs/KNOWN_LIMITATIONS.md)
- [Audit checklist](android-app/docs/AUDIT_CHECKLIST.md)
