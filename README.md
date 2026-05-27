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

## Build And Test

```bash
cd android-app
./gradlew :app:assembleDebug test lintDebug
cd server/relay
node --test
```

## Documentation

- [Android app and relay overview](android-app/README.md)
- [Two-device LAN test](android-app/docs/TWO_DEVICE_TEST.md)
- [Manual two-device QA checklist](android-app/docs/MANUAL_TWO_DEVICE_QA.md)
- [Crypto design](android-app/docs/CRYPTO_DESIGN.md)
- [Security warnings](android-app/docs/SECURITY_WARNINGS.md)
- [Audit scope](android-app/docs/AUDIT_SCOPE.md)
- [Known limitations](android-app/docs/KNOWN_LIMITATIONS.md)
- [Audit checklist](android-app/docs/AUDIT_CHECKLIST.md)
