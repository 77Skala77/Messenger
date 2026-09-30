# libsignal Toolchain Review

## Role And Evidence Boundary

**Repository baseline reviewed:** `e7e14e0de77c8308ff29eb773bdddc75710b8a28`

This document records toolchain and packaging risks for a disabled libsignal migration
investigation. It is not:

- approval to upgrade any dependency;
- approval to activate Signal-backed chats;
- proof that official protocol integration works;
- a legal conclusion;
- a security audit;
- a substitute for current build and release evidence.

The repository pin, the version most recently published upstream, and a future selected
migration target are separate facts. Do not merge them into one statement.

Concrete work remains controlled by
[Task Queue](ai-workflow/TASK_QUEUE.md), especially TQ-100 through TQ-105. The protocol and
storage gates are summarized in
[Signal Migration Spike](SIGNAL_MIGRATION_SPIKE.md).

## Current Repository Toolchain

The current repository version catalog and Android configuration specify:

| Area | Repository value |
| --- | --- |
| Android Gradle Plugin | `8.5.2` |
| Kotlin Gradle plugin | `2.2.20` |
| Compose compiler | Kotlin Compose compiler plugin `2.2.20` (was extension `1.5.14`) |
| Compose BOM | `2024.06.00` |
| Room | `2.7.2` (KAPT) |
| Kotlin Coroutines | `1.10.2` |
| WorkManager | `2.9.1` |
| Application compile SDK | `34` |
| Application target SDK | `34` |
| Application minimum SDK | `31` |
| Java source and target | `17` |
| Kotlin JVM target | `17` |
| Repository-pinned libsignal | `0.94.1` |

These are repository declarations, not proof that every build variant currently passes.
Fresh results belong to TQ-006 and TQ-007.

## Toolchain upgrade (step 1b, 2026-09-27) — DEBUG BUILD VERIFIED 2026-09-29

Performed as step 1b of `ai-workflow/REWRITE_PLAN.md` Phase 1. It was written on
2026-09-27 without a local toolchain (JDK/Android SDK install was deferred), so it
remained unverified until step 1a was completed.

**Verified 2026-09-29** (JDK 17 Temurin 17.0.20, Android SDK platform 34 / build-tools
34.0.0, Gradle 8.7 wrapper, physical device SM-S928B, Android 16, arm64-v8a):

- `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL (Kotlin 2.2.20, Compose compiler plugin,
  Room 2.7.2 with KAPT).
- `./gradlew :app:lintDebug :core:signal-protocol:testDebugUnitTest :core:signalstore:test test`
  — BUILD SUCCESSFUL; 105 unit tests, 0 failures, 0 skipped; lint 0 errors, 51 warnings
  (dependency/AGP update notices, the debug-only cleartext network config, and one
  `localeConfig` API-level notice).
- `./gradlew :core:storage:connectedDebugAndroidTest` on the device above — 12 tests passed.
- `node --test server/relay/test/*.test.mjs` — 3 tests passed (Node.js v24.19.0).

**Step 1c (2026-09-29, partly verified):**

- `:core:signal-protocol:connectedDebugAndroidTest` on SM-S928B (Android 16, arm64-v8a) —
  7 tests passed: the same libsignal PQXDH/Double Ratchet tests as the JVM suite (the
  `src/test` sources are shared with `androidTest`), so the Android JNI library
  `libsignal_jni.so` loads and works on a physical device.
- `libsignal-android:0.94.1` **requires core library desugaring**; without it
  `checkDebugAndroidTestAarMetadata` fails even at minSdk 31. Enabled with
  `desugar_jdk_libs` 2.1.5 in `:core:signal-protocol`. Any app module that depends on it
  must enable desugaring too. (This resolves the earlier "likely irrelevant" assumption.)
- The AAR ships `libsignal_jni.so` and `libsignal_jni_testing.so` for arm64-v8a,
  armeabi-v7a, x86, x86_64 (each 84–104 MB). The `_testing` library is excluded via
  `packaging.jniLibs.excludes`; the built test APK (408 MB, debug) contains only the four
  `libsignal_jni.so` files. Owner decision: keep all 4 ABIs.
- Build note: the development machine has ~3.9 GB RAM; the Gradle daemon crashed with an
  out-of-memory error on the default `-Xmx2048m`. Building succeeded with
  `-Dorg.gradle.jvmargs=-Xmx1280m -Pkotlin.compiler.execution.strategy=in-process
  --max-workers=1`.
- `core/signal-protocol/consumer-rules.pro` adds conservative R8 keep rules for
  `org.signal.libsignal.**` and native methods. **Update 2026-09-30:** the app now depends on
  `:core:signal-protocol` (through `:core:ui`, debug QA switch only). `:app:assembleRelease`
  succeeds; `mapping.txt` shows the rules applied and no real libsignal class renamed. The
  first release APK also contained ~153 MB of desktop natives (`*.dll`, `*.dylib`, including
  `*_testing`) from libsignal-client's jar resources, now excluded in `:app`
  (`packaging.resources.excludes`). R8 warns it cannot parse Kotlin 2.2 metadata under AGP
  8.5.2 (TQ-009). Release APK: unsigned, 357.5 MB, 4 ABIs. **libsignal under R8 verified at
  runtime (2026-09-30):** the `qaMinified` build type uses the release R8 configuration with
  debug signing and debug QA resources; installed on SM-S928B and Redmi A3x, v2 pairing,
  messaging, and receipts worked by hand.

**Still NOT verified:** a minified release build (`assembleRelease`, R8) that actually
contains libsignal, resulting release APK size/ABI splits, and instrumented tests for
`:core:transport` and `:core:ui`.

The risk analysis sections below were written against the pre-upgrade versions; the
checklist below records which items the debug build has now answered.

| Area | Before | After | Evidence (checked 2026-09-27) |
| --- | --- | --- | --- |
| Kotlin (KGP, stdlib) | `1.9.24` | `2.2.20` | `libsignal-client-0.94.1.pom` declares `kotlin-stdlib 2.2.20`; libsignal `java/build.gradle` at tag `v0.94.1` uses KGP `2.2.20` |
| Compose compiler | extension `1.5.14` | plugin `org.jetbrains.kotlin.plugin.compose` `2.2.20` | Kotlin 2.x ships the Compose compiler as a Kotlin plugin |
| Kotlin Coroutines | `1.8.1` | `1.10.2` | both libsignal POMs declare `1.10.2` |
| Room | `2.6.1` (KAPT) | `2.7.2` (KAPT kept) | Room 2.7.0 release notes: targets Kotlin 2.0 and requires projects to compile with Kotlin 2.0+; KAPT still supported. `room-runtime-android-2.7.2.aar` and `sqlite(-framework)-android-2.5.1.aar` declare `minCompileSdk=34` |
| `kotlinOptions {}` DSL | used in app, ui, storage, transport | `kotlin { compilerOptions { jvmTarget = JVM_17 } }` | Kotlin 2.2.0 raises `kotlinOptions{}` deprecation to error |
| AGP / Gradle | `8.5.2` / `8.7` | unchanged | KGP 2.2.20 supports AGP 7.3.1–8.11.1 and Gradle 7.6.3–8.14 (kotlinlang.org compatibility table) |
| compileSdk / minSdk | 34 / 31 | unchanged | libsignal-android `v0.94.1` builds with `compileSdk 34`, `minSdkVersion 23` |
| Compose BOM | `2024.06.00` | unchanged | kept to avoid a compileSdk 35 requirement from newer Compose |

Deliberately not done in this step (separate, later changes): KAPT→KSP, AGP/Gradle
upgrade, Compose BOM upgrade.

Known risks and their status after the 2026-09-29 debug build:

- Room 2.7.2's KAPT processor reading Kotlin 2.2 class metadata — **passed** (debug build,
  `kaptDebugKotlin` succeeded; schema v6 was exported).
- Compose compiler plugin 2.2.20 against the older Compose runtime from BOM `2024.06.00` —
  **passed at compile time** (debug build). Runtime behavior of the UI was not exercised.
- R8 bundled with AGP 8.5.2 processing Kotlin 2.2 metadata in the minified release build —
  **UNVERIFIED** (release build not run).
- K2 compiler stricter diagnostics in existing Kotlin source — **passed** (debug build and
  all unit-test compilation succeeded).
- libsignal's `coreLibraryDesugaringEnabled = true`: assumed likely irrelevant at minSdk 31 —
  **WRONG**: the AAR metadata check fails the build without desugaring; now enabled
  (see step 1c above).

## Current Official Artifact Gate

The isolated `core:signal-protocol` Android library:

- applies only the Android library plugin;
- contains Java source compatibility set to 17;
- does not apply the Kotlin plugin;
- depends on both `org.signal:libsignal-client` and `org.signal:libsignal-android`;
- resolves those artifacts from Signal's official Maven repository;
- is not connected to the active chat runtime.

The Signal Maven repository is restricted in Gradle to the `org.signal` group. This reduces
accidental resolution from that repository but does not provide dependency verification,
artifact locking, signature verification, SBOM coverage, or a legal/security approval.

The Java-only gate deliberately avoids forcing an application-wide Kotlin migration merely
to inspect official APIs. A passing compile gate would establish only narrow source and
dependency compatibility for that module and environment.

It would not establish:

- Kotlin adapter compatibility;
- official record-store correctness;
- JNI loading;
- supported Android ABI packaging;
- debug or release APK correctness;
- protocol interoperability;
- migration or rollback behavior;
- legal distribution permission;
- production security.

## Historical Official Artifact Snapshot

The following observations were recorded on **2026-05-26** for repository-pinned version
`0.94.1`. They are a dated artifact snapshot, not a claim that `0.94.1` is still the latest
upstream version:

| Item | Historical finding for `0.94.1` |
| --- | --- |
| Official artifacts inspected | `org.signal:libsignal-client:0.94.1`, `org.signal:libsignal-android:0.94.1` |
| Maven metadata marker observed | `20260516203204` |
| License declared by inspected POM | `AGPLv3` |
| Kotlin stdlib declared by inspected client POM | `2.2.20` |
| Coroutines Core JVM declared by inspected client POM | `1.10.2` |
| Android native ABIs observed in inspected AAR | `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64` |
| Native library observed | `libsignal_jni.so` |

The official libsignal repository also documented:

- JDK 17 as the supported JDK for Android builds;
- the need for both `libsignal-client` and `libsignal-android` on Android;
- Android native libraries for four ABIs;
- packaging exclusions for non-Android native resources carried by `libsignal-client`;
- that published Java packages are for Signal's own use and external use is unsupported;
- AGPLv3 licensing.

Upstream release activity continued after this historical snapshot. Before any migration or
upgrade decision, refresh every artifact fact from official sources and record the date,
exact version, hashes or verification metadata where available, and relevant release notes.

## Why The Current Boundary Is Safe To Keep Disabled

The project currently avoids a broad toolchain migration because:

- the official gate is Java-only;
- `core:signalstore` uses neutral interfaces rather than official libsignal types;
- normal chats stay on `SimplifiedSessionManager`;
- Signal-backed session flags remain disabled;
- experimental invitation version 2 remains a DTO/codec boundary only.

This separation prevents a compile spike from silently changing the active protocol,
conversation format, storage records, device fan-out, or user security claims.

It does not mean the existing spike is complete. It means the risk is contained while the
gates remain disabled.

## Toolchain Compatibility Risks

### Kotlin metadata and runtime alignment

The project's Kotlin plugin is `1.9.24`, while the historically inspected `0.94.1` client
POM declared Kotlin stdlib `2.2.20`. A Kotlin adapter compiled directly against official
APIs may encounter metadata, compiler, binary, or dependency-alignment issues not exercised
by the current Java-only gate.

Do not solve this by forcing Kotlin stdlib versions or suppressing metadata errors without a
reviewed compatibility analysis. Such work can create compile success while leaving runtime
or transitive incompatibility unresolved.

### Compose compiler and Kotlin coupling

The current application explicitly uses Compose compiler extension `1.5.14`. A Kotlin
migration must verify the supported Compose compiler or plugin model for the selected Kotlin
version. Updating Kotlin alone may break compilation, Compose tooling, lint, or generated
code.

### Android Gradle Plugin and Gradle

A selected Kotlin or Compose migration may require an AGP or Gradle change. That affects:

- Android variant APIs;
- manifest processing;
- lint behavior;
- resource shrinking and R8;
- packaging;
- test infrastructure;
- CI images and caches;
- release artifact reproducibility.

No broad AGP or Gradle upgrade belongs inside a protocol adapter commit.

### Room, KAPT, and possible KSP migration

The current repository uses Room `2.6.1` and the Kotlin KAPT plugin. A toolchain change must
check:

- generated DAO and database code;
- schema export and migration tests;
- KAPT compatibility;
- whether KSP is required or merely optional;
- incremental build behavior;
- Room transaction and nullability behavior;
- instrumented persistence tests.

Changing annotation processing while mapping official protocol records would combine two
high-risk migrations and should be avoided.

### Coroutines alignment

The project declares Coroutines `1.8.1`; the historical `0.94.1` client POM declared
Coroutines Core JVM `1.10.2`. A future adapter must inspect the full resolved graph and
verify:

- version conflict resolution;
- Android and JVM coroutine artifacts;
- cancellation behavior;
- dispatcher assumptions;
- binary compatibility;
- WorkManager interaction;
- tests under process death and retry.

Dependency resolution alone is not evidence that coroutine semantics are correct.

### Minimum SDK and Android API behavior

The application minimum SDK is 31. The selected official artifacts may support a broader
range, but the project must test the actual supported project range rather than relying on
artifact metadata. Verify device and emulator behavior for every ABI and API level the
project intends to support.

## Native And Packaging Risks

Official Android integration brings Rust-backed JNI libraries into the Android artifact.
Required review includes:

- `armeabi-v7a`, `arm64-v8a`, `x86`, and `x86_64` presence for the selected version;
- duplicate native libraries and resource merge conflicts;
- non-Android `.dll` and `.dylib` resources carried by `libsignal-client`;
- official recommended packaging exclusions;
- optional testing JNI libraries;
- release stripping and symbol behavior;
- APK/AAB size impact;
- ABI splits;
- Play or other distribution requirements;
- native crash reporting and redaction;
- loading on physical devices and emulators;
- R8/ProGuard keep requirements;
- license and acknowledgment files in distributed artifacts.

A debug Java compile does not exercise these risks. TQ-007 must inspect the actual release
artifact before any distribution claim.

## Supply-Chain Risks

Current repository configuration does not by itself prove:

- Gradle dependency verification;
- dependency locking;
- trusted checksums for official artifacts;
- deterministic dependency resolution;
- complete transitive inventory;
- native-library inventory;
- CVE analysis;
- SBOM generation;
- reproducible builds;
- license-notice completeness.

These controls belong to TQ-008 and must not be assumed from version pinning alone.

A future libsignal task must record:

- exact artifact coordinates;
- source repository;
- hashes or Gradle verification metadata;
- transitive dependencies;
- native files and ABIs;
- license information;
- release notes and API changes;
- chosen rollback version.

## Legal And Support Gate

The inspected official POM and repository identify AGPLv3 licensing, while upstream states
that external use of published packages is unsupported.

Before runtime activation or distribution, TQ-100 requires qualified review of:

- source and distribution obligations;
- modifications and corresponding-source requirements;
- notices and acknowledgments;
- combined-work implications;
- app-store and distribution constraints;
- unsupported API and maintenance risk;
- interoperability and update cadence;
- project-owner risk acceptance.

This document is technical context only and is not legal advice.

## Controlled Migration Plan

TQ-101 should create a staged plan rather than one large upgrade.

### Stage A — Reproduce the current baseline

Record current results for:

```bash
cd android-app
./gradlew :app:assembleDebug
./gradlew :app:lintDebug :core:signal-protocol:testDebugUnitTest :core:signalstore:test test
```

Also record relay tests and compatible instrumented tests. A skipped check is not a pass.

### Stage B — Resolve and inventory the selected official version

Without activating runtime Signal chats:

- refresh official metadata;
- select and justify a target version;
- inspect POM and Gradle metadata;
- inventory transitive and native artifacts;
- add dependency verification or locking as separately approved;
- record licenses and upstream support warnings.

### Stage C — Establish the target toolchain in isolated commits

Separate changes for:

- Gradle wrapper if required;
- AGP;
- Kotlin;
- Compose compiler/plugin and BOM;
- Room and annotation processing;
- Coroutines and WorkManager;
- lint and test infrastructure.

Each step needs rollback and the full relevant regression subset.

### Stage D — Validate packaging before protocol mapping

Build and inspect debug and release-like artifacts across intended ABIs. Confirm JNI loading,
resource exclusions, R8 behavior, artifact size, native contents, and device execution.

### Stage E — Map official records in the disabled store

Only after toolchain and packaging baselines are stable, implement reviewed official record
mapping and lifecycle semantics under disabled flags. Do not activate normal conversations.

### Stage F — Isolated test conversations

Only after TQ-100 through TQ-104 and interoperability gates clear, consider new isolated
test conversations behind an explicit disabled-by-default feature flag.

## Required Verification Matrix

A migration proposal must specify exact pass, fail, or skip evidence for:

- clean-clone dependency resolution;
- debug assembly and lint;
- release assembly and lint;
- all JVM/unit tests;
- Room migration and persistence tests;
- UI and storage instrumented tests;
- JNI loading on every intended ABI;
- physical-device and emulator runs;
- R8/resource-shrunk artifact behavior;
- official protocol interoperability;
- restart, crash, retry, replay, identity-change, and revocation behavior;
- downgrade and rollback;
- dependency verification and SBOM output;
- absence of secrets, local paths, debug controls, and unintended native artifacts.

Record the exact commit, JDK, Gradle, AGP, Kotlin, Android SDK, NDK if applicable, artifact
versions, device/emulator, ABI, and API level.

## Current Safe Scope

Until all gates clear, allowed work remains limited to reviewed disabled investigation:

- inspect official metadata and APIs;
- maintain the isolated Java compile gate;
- define neutral encrypted store interfaces;
- test storage-only encrypted opaque records;
- maintain an explicitly experimental invitation DTO;
- design migrations, rollback, and interoperability tests;
- keep all feature flags disabled;
- keep normal chats on the prototype path.

Do not introduce official types into the active ViewModel, delivery processor, current
conversation records, or version-1 invitation flow.

## Official Sources

Reverify official information when a migration task begins:

- libsignal repository, license, Android build instructions, packaging notes, and support
  boundary: <https://github.com/signalapp/libsignal>
- official Signal Maven repository:
  <https://build-artifacts.signal.org/libraries/maven/>
- repository-pinned client POM snapshot:
  <https://build-artifacts.signal.org/libraries/maven/org/signal/libsignal-client/0.94.1/libsignal-client-0.94.1.pom>
- repository-pinned Android POM snapshot:
  <https://build-artifacts.signal.org/libraries/maven/org/signal/libsignal-android/0.94.1/libsignal-android-0.94.1.pom>

Do not rely on this dated snapshot to choose a future version.