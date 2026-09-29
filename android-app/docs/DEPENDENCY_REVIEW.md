# Dependency And Supply-Chain Review

## Role And Evidence Boundary

**Repository baseline reviewed:** `04fb122d316ffcdd82a9c2a2ea16667bf498221d`

This document records repository-declared dependency and supply-chain boundaries. It is not:

- proof that dependencies resolved in a clean current environment;
- a current vulnerability scan;
- an SBOM;
- a license opinion;
- a reproducible-build result;
- approval to upgrade dependencies;
- proof that CI or release builds passed for the reviewed commit;
- an external security audit.

A version declared in Gradle, a test present in source, and a historical successful build
are different forms of evidence. Keep them separate.

Concrete supply-chain work is controlled by TQ-008 in
[Task Queue](ai-workflow/TASK_QUEUE.md). Signal-specific gates are documented in
[Signal Migration Spike](SIGNAL_MIGRATION_SPIKE.md) and
[libsignal Toolchain Review](LIBSIGNAL_TOOLCHAIN_REVIEW.md).

## Current Repository Dependency Sources

Gradle currently resolves from:

- Google's Maven repository;
- Maven Central;
- Gradle Plugin Portal for plugins;
- Signal's official build-artifact Maven repository, restricted to group `org.signal`.

The repository restriction for Signal artifacts reduces accidental dependency resolution
from that source. It does not verify artifact integrity, signatures, provenance, licenses,
transitive dependencies, or native payloads.

The Gradle wrapper is pinned to:

```text
Gradle 8.7 binary distribution
```

The wrapper configuration validates the distribution URL. The repository must still record
and review wrapper changes, expected distribution checksum controls, and clean-clone
behavior before treating the wrapper as a complete supply-chain control.

## Current Direct Version Inventory

The version catalog currently declares:

| Component | Repository pin |
| --- | ---: |
| Gradle wrapper | `8.7` |
| Android Gradle Plugin | `8.5.2` |
| Kotlin | `2.2.20` |
| Compose BOM | `2024.06.00` |
| Activity Compose | `1.9.0` |
| AppCompat | `1.7.0` |
| AndroidX Core KTX | `1.13.1` |
| Lifecycle | `2.8.2` |
| Navigation Compose | `2.7.7` |
| Room | `2.7.2` |
| Kotlin Coroutines | `1.10.2` |
| WorkManager | `2.9.1` |
| ZXing Embedded | `4.3.0` |
| JUnit | `4.13.2` |
| AndroidX JUnit | `1.2.1` |
| AndroidX Test Runner | `1.6.2` |
| Espresso | `3.6.1` |
| Official libsignal gate | `0.94.1` |

Application configuration additionally declares the Kotlin Compose compiler plugin
(`org.jetbrains.kotlin.plugin.compose`, same version as Kotlin; replaced the old Compose
compiler extension `1.5.14` on 2026-09-27), compile and target SDK 34, minimum SDK 31,
and Java/Kotlin JVM target 17. The Kotlin, Room, and coroutines pins were raised on
2026-09-27 for libsignal compatibility — see `LIBSIGNAL_TOOLCHAIN_REVIEW.md`,
"Toolchain upgrade (step 1b)". The debug build, unit tests, and storage instrumented tests
passed on 2026-09-29; the minified release build has not been verified. `:core:signal-protocol`
also declares `com.android.tools:desugar_jdk_libs` `2.1.5` (core library desugaring, required
by `libsignal-android`).

This table is an inventory of direct declarations. It is not a complete resolved graph and
must not be read as a recommendation that every pin is current, safe, mutually compatible,
or suitable for production.

## Version-Freshness Boundary

The earlier review included a dated table of versions described as current stable releases.
That presentation is unsafe in a long-lived repository document because upstream versions
change independently of this project.

This review therefore does not declare a latest version for Android, Kotlin, AndroidX,
Coroutines, ZXing, Gradle, or libsignal.

Before any upgrade task:

1. query official upstream metadata and release notes on the day of the decision;
2. record the exact source and date;
3. distinguish stable, preview, alpha, beta, and release-candidate streams;
4. inspect minimum Java, Gradle, AGP, Kotlin, Android SDK, and NDK requirements;
5. inspect breaking changes, migrations, deprecations, native artifacts, and licenses;
6. choose a target for project reasons, not merely because it is newer;
7. define rollback and regression coverage;
8. perform the upgrade in isolated commits.

No dependency version should be refreshed from memory or copied from a generic update
report without official-source verification.

## Current Implemented Boundaries

### Centralized pins

Direct versions are centralized in `gradle/libs.versions.toml`. This improves reviewability
but does not lock the complete transitive graph.

### Restricted Signal repository

The Signal Maven source is limited to `org.signal`. Official artifacts are pinned at
`0.94.1` in the isolated `core:signal-protocol` module.

The runtime migration gate remains disabled. The presence of official artifacts in the
build graph does not activate Signal-backed chats and does not prove legal, protocol,
toolchain, JNI, or release suitability.

### Release network and shrinking configuration

Repository source configures:

- release rejection of cleartext HTTP relay URLs and cleartext networking;
- R8 minification for release;
- release resource shrinking.

These are useful configuration boundaries, not evidence that the current release artifact
builds, contains only intended dependencies, or is ready for distribution. TQ-007 must
inspect the real release variant.

### CI source coverage

Repository workflow and test source cover several build, lint, unit, relay, UI, storage,
and emulator paths. Their presence is not proof of a pass on the current commit. Fresh
pass, fail, and skip evidence belongs to TQ-006 and TQ-007.

## Missing Or Unverified Supply-Chain Controls

### Dependency verification

No project verification metadata was found at:

```text
android-app/gradle/verification-metadata.xml
```

Repository code search also did not identify that file. Therefore Gradle dependency
verification must not be claimed as implemented.

TQ-008 should decide:

- checksum verification scope;
- treatment of plugins and buildscript dependencies;
- official Signal repository artifacts;
- trusted-key or signature policy where applicable;
- review procedure for metadata changes;
- CI enforcement;
- emergency update procedure.

Generated verification metadata must be reviewed rather than accepted blindly.

### Dependency locking

No project root lockfile was found at:

```text
android-app/gradle.lockfile
```

Repository code search did not identify a Gradle lockfile. This does not prove that no
configuration-specific lock file could ever exist, but the repository currently has no
recorded locking baseline that should be described as complete.

TQ-008 must choose whether to use dependency locking, verification, or both, and document
which configurations and modules are covered.

### Complete resolved inventory

The version catalog does not show:

- transitive dependency versions;
- dependency substitutions or conflict resolution;
- platform/BOM-expanded versions;
- plugin implementation dependencies;
- test-only transitive artifacts;
- native libraries inside AARs or JARs;
- duplicate resources;
- release-only shrinker inputs.

A clean-environment report should capture resolved graphs for relevant debug, test,
androidTest, lint, and release configurations.

### SBOM

No reviewed SBOM generation or validation workflow is established. TQ-008 must select and
document:

- format, such as CycloneDX or SPDX;
- scope and variants;
- treatment of Gradle plugins, build tools, test dependencies, and native artifacts;
- deterministic output expectations;
- storage and review process;
- CI generation and comparison;
- release association.

An automatically generated SBOM is an inventory aid, not proof of safety or license
compliance.

### Vulnerability analysis

This review does not establish a current CVE result. A useful workflow must define:

- trusted advisory sources;
- direct, transitive, plugin, and native scope;
- severity and exploitability triage;
- Android-specific reachability considerations;
- false-positive handling;
- exception ownership and expiry;
- remediation and backport policy;
- evidence retained with each release.

“No vulnerabilities reported” must never be inferred from an empty automated result alone.

### License analysis

The repository does not yet have a complete reviewed license inventory and distribution
procedure.

Required work includes:

- direct and transitive dependency licenses;
- Gradle plugins and build-time tools;
- native libraries and Rust-backed artifacts;
- notice and attribution files;
- source-offer or corresponding-source obligations where applicable;
- app-store and distribution constraints;
- legal review for official libsignal and unsupported external use.

This document is not legal advice.

### Provenance and artifact trust

Pinning a coordinate does not prove artifact provenance. Review should consider:

- repository ownership and TLS endpoint;
- expected checksums;
- Gradle verification metadata;
- artifact signing where supported;
- repository content filters;
- immutable version policy;
- cache poisoning and compromised repository scenarios;
- dependency confusion;
- plugin portal and buildscript trust;
- wrapper distribution verification.

### Reproducible builds

No current reproducible-build result is established. A future assessment must control or
record:

- JDK and operating system;
- Gradle and Android SDK/NDK versions;
- environment variables and locale;
- timestamps and archive ordering;
- signing inputs;
- R8 behavior;
- generated sources and resources;
- dependency graph and caches;
- APK/AAB comparison method.

Two successful builds are not reproducible-build evidence unless their expected outputs
are compared under a documented procedure.

## Signal-Specific Dependency Gate

The repository pins official libsignal `0.94.1`, but that pin is not a selected production
migration target and is not necessarily the latest upstream release.

The dated 2026-05-26 artifact inspection recorded, for `0.94.1`:

- AGPLv3 in the inspected official POM;
- Kotlin stdlib `2.2.20`;
- Coroutines Core JVM `1.10.2`;
- Android native libraries for four ABIs;
- upstream's unsupported-external-use warning.

These findings must be refreshed before a version decision. Runtime integration remains
blocked by TQ-100 through TQ-105, including:

- legal and support-risk review;
- controlled toolchain migration;
- official record mapping and lifecycle design;
- authenticated PQXDH/prekey invitation semantics;
- per-device sessions and revocation behavior;
- JNI/ABI and release packaging;
- interoperability, migration, rollback, and independent review.

Do not combine a libsignal version update with unrelated dependency modernization.

## Native Artifact Review

Android dependencies may carry native `.so` files even when the project contains no local
C/C++ source. The inventory must include:

- ABI and path;
- originating dependency and version;
- debug/testing native libraries;
- symbols and stripping behavior;
- duplicate or conflicting libraries;
- licensing and notices;
- release artifact inclusion;
- device/emulator loading results;
- CVE and advisory coverage.

Official libsignal is a known high-impact native boundary because its Android artifact
contains Rust-backed JNI libraries. Other AARs must also be inspected rather than assumed
Java-only.

## Upgrade Discipline

Every upgrade task should be narrow and record:

- reason for change;
- official source and release notes;
- old and target versions;
- resolved dependency diff;
- API and behavior changes;
- Android SDK, Java, Kotlin, Gradle, and AGP requirements;
- schema or persisted-format effects;
- native and ABI effects;
- license changes;
- security advisories;
- migration and rollback;
- exact verification results.

Avoid labels such as “low risk” based only on semantic version size. Even patch releases may
change transitive dependencies, generated code, resources, native artifacts, or behavior.

## TQ-008 Implementation Split

Supply-chain hardening should remain divided into reviewable changes:

1. capture clean-clone resolved dependency graphs;
2. add and review dependency verification and/or locking;
3. inventory transitive and native artifacts;
4. select and generate an SBOM;
5. establish CVE triage;
6. establish license and notice review;
7. assess reproducibility;
8. define signing-key custody and release procedure;
9. enforce selected controls in CI;
10. update audit and release documentation with actual evidence.

Do not mix incidental version upgrades into these steps.

## Required Verification Evidence

A completed dependency review should identify:

- exact repository commit;
- clean-clone environment;
- JDK, Gradle, AGP, Kotlin, Android SDK, and NDK if applicable;
- repository URLs used;
- complete relevant resolved graphs;
- verification or lock metadata;
- direct, transitive, plugin, and native inventory;
- SBOM output and validation;
- CVE and license findings with owners;
- debug, test, instrumented, lint, and release results;
- signed or unsigned artifact state;
- remaining exceptions and expiry dates.

Skipped checks must be recorded as skipped, not passed.

## Official Sources For Future Refresh

Use official primary sources at the time of each decision:

- Android Gradle Plugin and Android build documentation:
  <https://developer.android.com/build>
- AndroidX release notes:
  <https://developer.android.com/jetpack/androidx/versions>
- Kotlin releases and compatibility information:
  <https://kotlinlang.org/docs/releases.html>
- Gradle dependency verification and locking documentation:
  <https://docs.gradle.org/current/userguide/dependency_verification.html>
  and <https://docs.gradle.org/current/userguide/dependency_locking.html>
- official libsignal repository and Maven source:
  <https://github.com/signalapp/libsignal>
  and <https://build-artifacts.signal.org/libraries/maven/>
- Maven Central for artifacts published there:
  <https://repo.maven.apache.org/maven2/>

A source being official does not by itself make an upgrade appropriate. Record the exact
page, version, date, and decision rationale.