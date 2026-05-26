# libsignal Toolchain Review

## Status

Reviewed on 2026-05-26 for an isolated, disabled integration spike. This document
does not authorize runtime Signal-backed chats and is not a legal or security audit.

## Official Artifacts Observed

| Item | Finding |
| --- | --- |
| Published artifact version | `org.signal:libsignal-client:0.94.1` and `org.signal:libsignal-android:0.94.1` |
| Maven metadata update marker | `20260516203204` |
| License declared by official POM | `AGPLv3` |
| Kotlin dependency declared by POM | `org.jetbrains.kotlin:kotlin-stdlib:2.2.20` |
| Coroutine dependency declared by POM | `kotlinx-coroutines-core-jvm:1.10.2` |
| Native Android ABIs in resolved AAR | `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64` |

The official repository also states that published Java packages are for Signal's
own use and that external use is unsupported. API stability and maintenance must be
treated as integration risks.

## Toolchain Mismatch

This project currently builds Kotlin code with Kotlin `1.9.24`. The official
`libsignal 0.94.1` artifacts carry Kotlin `2.2.20` dependencies and Kotlin metadata
newer than the current Kotlin compiler expects during some analysis paths.

The existing `core:signal-protocol` gate is Java-only and disabled. The new
`core:signalstore` boundary deliberately has no dependency on official libsignal
types. It can therefore compile without upgrading the Android application toolchain.

Do not upgrade the complete project to Kotlin `2.2.20` as an incidental spike
change. A controlled decision must consider Compose compiler compatibility, AGP,
Room/KAPT or KSP migration, lint, instrumentation coverage and release artifacts.

## Current Safe Scope

- Persist neutral opaque serialized record bytes encrypted by the existing local vault.
- Define interfaces for identity, session, prekey, signed prekey and Kyber prekey records.
- Encode/decode `INVITE_V2_SIGNAL_PQXDH_EXPERIMENTAL` as a neutral DTO only.
- Keep `SIGNAL_BACKED_SESSIONS_ENABLED=false`.
- Leave all working chats on the existing explicitly non-production prototype path.

## Blocks Before Runtime Integration

- Legal review of AGPLv3 obligations and upstream unsupported-external-use position.
- Approved Kotlin/AGP/Compose/Room toolchain migration with full regression testing.
- Reviewed mapping from official libsignal record types into encrypted durable stores.
- Versioned PQXDH/prekey exchange, identity-change and per-device session semantics.
- JNI packaging validation across required device/emulator ABI targets.
- Independent protocol integration and application security review.

## Primary Sources

- Official repository: <https://github.com/signalapp/libsignal>
- Official Maven metadata: <https://build-artifacts.signal.org/libraries/maven/org/signal/libsignal-client/maven-metadata.xml>
- Official `0.94.1` POM: <https://build-artifacts.signal.org/libraries/maven/org/signal/libsignal-client/0.94.1/libsignal-client-0.94.1.pom>
