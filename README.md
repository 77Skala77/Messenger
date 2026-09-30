# Secure Messenger MVP / Защищённый мессенджер MVP

Native Android prototype of a private 1-to-1 messenger, with a Kotlin Compose app and a minimal
development relay. Russian is the default app language; English is available in Settings.

> **Security warning:** this repository is an MVP for development verification and security
> review. It is not production-secure and must not be used for real sensitive conversations. The
> protocol integration has not had an independent security review.

Since 2026-09-30 chats use official **libsignal PQXDH + Double Ratchet** (protocol v2), in
**debug builds only**; release builds have no chat until the independent review of Phase 5. The
earlier prototype session layer (`SimplifiedSessionManager`, `STANDARD_AEAD`) was removed, and old
test chats are deleted once on upgrade. `PARANOIA_C4_AEAD` was removed on 2026-09-27. See
[`android-app/docs/PROTOCOL_V2.md`](android-app/docs/PROTOCOL_V2.md) and
[`android-app/docs/ai-workflow/REWRITE_PLAN.md`](android-app/docs/ai-workflow/REWRITE_PLAN.md).

Verified on 2026-09-30: automated tests on the JVM and on two physical phones (Samsung S24 Ultra,
Redmi A3x) including forward secrecy and post-compromise security proofs, plus manual two-phone
pairing, messaging, `Delivered` receipts and relay-outage recovery. `Delivered` is not a read
receipt; read receipts are not implemented.

## Build And Test

```bash
cd android-app
./gradlew :app:assembleDebug test :app:lintDebug
cd server/relay
node --test
```

A local Node relay is enough for LAN/USB debug QA. A public relay is still untrusted QA
infrastructure, not production. The historical VPS test endpoint (`http://194.87.226.46`, HTTP by
IP, legacy protocol) is unverified and must not be used as current infrastructure.

## Documentation

- [Android app and relay overview](android-app/README.md)
- [Protocol v2 (current design)](android-app/docs/PROTOCOL_V2.md)
- [Rewrite plan](android-app/docs/ai-workflow/REWRITE_PLAN.md)
- [Known limitations](android-app/docs/KNOWN_LIMITATIONS.md)
- [Security warnings](android-app/docs/SECURITY_WARNINGS.md)
- [VPS HTTPS relay deployment](android-app/docs/VPS_RELAY_DEPLOYMENT.md)
- [Relay deployment examples](android-app/deploy/README.md)
