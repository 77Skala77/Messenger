# Official libsignal Migration Spike

## Decision

The running MVP must continue using its explicitly labelled prototype session only
for development verification. It must not claim Signal-like security and must not
replace that layer with a handwritten Double Ratchet.

The official `libsignal` integration remains a disabled migration gate. Wiring it
into conversations is blocked until durable encrypted protocol stores, invitation
and prekey semantics, device transitions, licensing, toolchain compatibility and an
independent integration review are complete.

## Artifact Findings Verified On 2026-05-26

- The official Maven metadata publishes `org.signal:libsignal-client:0.94.1` and
  reports `lastUpdated` value `20260516203204`.
- The official `libsignal-client-0.94.1.pom` identifies the license as `AGPLv3`
  and declares `org.jetbrains.kotlin:kotlin-stdlib:2.2.20` plus
  `kotlinx-coroutines-core-jvm:1.10.2`.
- The resolved official `libsignal-android-0.94.1.aar` contains native libraries for
  `armeabi-v7a`, `arm64-v8a`, `x86` and `x86_64`, including `libsignal_jni.so`.
- The official repository README says Java packages are published for Signal's own
  use and that use outside Signal is unsupported.

The repository already pins both official artifacts at `0.94.1` in a separate
`core:signal-protocol` module. Its Java-only compile gate resolves the API, but
`MIGRATION_ENABLED` is `false` and the app runtime does not use it.

## Blockers

| Area | Current finding | Migration gate |
| --- | --- | --- |
| Protocol state | Current chats store a prototype ECDH root/counter, not Signal records. | Implement encrypted durable Identity/Session/PreKey/SignedPreKey/KyberPreKey stores and failure tests. |
| Invitation flow | Current QR exchanges a static public key and receive queue. | Define versioned prekey/PQXDH invitation envelopes and migration/interoperability behavior. |
| Device model | Local status blocks untrusted targets; there is no per-device ratchet fan-out. | Establish an independently verified session per approved device; specify revoke/key-change behavior. |
| Kotlin/Gradle | App is on Kotlin `1.9.24`; artifact POM depends on Kotlin `2.2.20`. Java gate compiles, but a Kotlin runtime adapter has not been compatibility-reviewed. | Perform controlled toolchain/dependency upgrade with app and instrumentation regression tests. |
| NDK/ABI | Official AAR supplies four Android ABIs; packaging and release size/stripping behavior is unreviewed. | Validate ABI packaging and device/emulator runs in release-like artifacts. |
| License/support | Official POM is AGPLv3 and upstream describes external use as unsupported. | Obtain legal review and accept maintenance/interoperability risk before distribution. |
| Assurance | No independent protocol integration or application security audit has occurred. | Complete review before any real sensitive-use claim. |

## Minimum Migration Plan

1. Keep `SessionManager` and delivery boundaries stable; add a new disabled Signal-backed
   implementation rather than changing existing ciphertext in place.
2. Define versioned conversation/session records and QR/prekey payloads so prototype and
   Signal messages cannot be confused or silently downgraded.
3. Implement all required `libsignal` stores encrypted at rest, with transactional replay
   and identity-change handling, and never use in-memory stores in the app path.
4. Model every approved device as a separate protocol recipient; fan out only to
   independently established verified sessions and block pending/revoked/key-changed devices.
5. Add two-device and multi-device interoperability tests across restart, failed relay
   delivery, duplicate receive, identity replacement and revocation.
6. Review AGPL obligations, upstream support position, JNI ABI packaging, toolchain upgrades,
   release hardening and diagnostic redaction.
7. Enable only for new test conversations behind a migration feature flag, then obtain
   independent cryptographic/application review before considering broader use.

## Out Of Scope

- Implementing Double Ratchet locally.
- Enabling `PARANOIA_C4_AEAD`; it remains a disabled experimental payload boundary.
- Claiming the prototype session provides forward secrecy or post-compromise recovery.

## Primary Sources

- Official repository and Android ABI/support notes: <https://github.com/signalapp/libsignal>
- Official Maven metadata: <https://build-artifacts.signal.org/libraries/maven/org/signal/libsignal-client/maven-metadata.xml>
- Official `0.94.1` POM: <https://build-artifacts.signal.org/libraries/maven/org/signal/libsignal-client/0.94.1/libsignal-client-0.94.1.pom>
