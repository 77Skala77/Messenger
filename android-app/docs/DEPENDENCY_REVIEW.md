# Dependency Review

## Status And Scope

Review performed on 2026-05-26 for this MVP prototype. It verifies the resolved
dependency tree and checks official Maven metadata for high-impact libraries. It is
not a vulnerability assessment, SBOM, legal opinion or external security audit.

## Key Finding: Signal Migration Is Gated

The official Signal artifact repository reports `libsignal-client` and
`libsignal-android` version `0.94.1`. The Maven POM identifies AGPLv3 licensing and
pulls Kotlin stdlib `2.2.20`. The existing app is built with Kotlin `1.9.24`.

`core:signal-protocol` therefore contains a disabled Java compile-time boundary only.
The separate `core:signalstore` storage spike intentionally avoids official libsignal
types and remains disabled from chat execution.
Do not turn it into the runtime session provider until:

- the application toolchain is upgraded and retested as a controlled change;
- neutral encrypted record storage is mapped and tested with official protocol store
  types, and verified PQXDH/prekey invitations exist;
- AGPL/commercial licensing implications are reviewed;
- an independent protocol integration audit is completed.

Signal's own repository states that use outside Signal is unsupported and API changes
may occur without notice. Pinning and migration tests are mandatory.

## Version Snapshot

| Component | Pinned in MVP | Official metadata observed | Decision |
| --- | ---: | ---: | --- |
| Android Gradle Plugin | 8.5.2 | 9.2.1 stable | Defer coordinated Android/Kotlin upgrade. |
| Kotlin | 1.9.24 | 2.3.21 stable | Must migrate before runtime libsignal use. |
| Compose BOM | 2024.06.00 | 2026.05.01 | Defer together with Kotlin/AGP migration. |
| Activity Compose | 1.9.0 | 1.13.0 | Defer controlled UI regression run. |
| Core KTX | 1.13.1 | 1.18.0 | Defer controlled API/toolchain upgrade. |
| AppCompat | 1.7.0 | 1.7.1 | Low-risk candidate after this security change is isolated. |
| Lifecycle | 2.8.2 | 2.10.0 | Defer controlled lifecycle/background-sync review. |
| Room | 2.6.1 | 2.8.4 | Defer until migration/instrumentation matrix is expanded. |
| Coroutines | 1.8.1 | 1.11.0 | Defer together with Kotlin migration. |
| WorkManager | 2.9.1 | 2.11.1 | Pin compatible stable baseline for best-effort delivery worker; review during toolchain upgrade. |
| ZXing Embedded | 4.3.0 | 4.3.0 | No newer release observed. |
| Official libsignal | gated only: 0.94.1 | 0.94.1 | Compiles in disabled boundary; not shipped in app path. |

For AndroidX streams whose metadata currently points to alpha/beta/RC artifacts,
only stable versions were considered for the snapshot.

## Hardening Actions Implemented

- Release network security policy rejects cleartext traffic; a user-selected HTTP
  LAN relay is permitted only for debug two-device development verification.
- WorkManager schedules best-effort encrypted delivery synchronization with network
  constraints and exponential retry; it provides no timely-delivery assurance.
- Release build enables R8 minification and resource shrinking.
- GitHub Actions performs app build/lint/unit tests, relay tests and Android emulator
  language/Room/Keystore tests.

## Remaining Gates

- Add dependency locking/verification and generate a reviewed SBOM.
- Perform CVE/license analysis for all transitive dependencies and native artifacts.
- Define signed release/reproducible build procedure.
- Audit network transport authentication and server deployment before non-local use.

## Sources Checked

- Signal official repository and integration notice: <https://github.com/signalapp/libsignal>
- Signal official Maven repository: <https://build-artifacts.signal.org/libraries/maven/org/signal/libsignal-client/maven-metadata.xml>
- Spike-specific toolchain findings: [LIBSIGNAL_TOOLCHAIN_REVIEW.md](LIBSIGNAL_TOOLCHAIN_REVIEW.md)
- Android/AndroidX official Maven metadata: <https://dl.google.com/dl/android/maven2/master-index.xml>
- WorkManager release notes and scheduling constraints: <https://developer.android.com/jetpack/androidx/releases/work>
- Maven Central metadata for Kotlin, Coroutines and ZXing: <https://repo1.maven.org/maven2/>
