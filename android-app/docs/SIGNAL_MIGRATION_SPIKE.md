# Official libsignal Migration Spike

## Role And Status Boundary

**Repository baseline reviewed:** `771558f4cedef205707986aca04a7a85d049495c`

This document records a disabled migration investigation. It is not an implementation
report, protocol specification, dependency-upgrade approval, interoperability claim, or
permission to activate Signal-backed conversations.

The active Android chat path continues to use the explicitly labelled prototype
`SimplifiedSessionManager` with `STANDARD_AEAD` for development verification. It must not
be described as Signal, Signal-like, PQXDH, Double Ratchet, forward-secret, or suitable for
real sensitive communications.

The project must not implement a handwritten Double Ratchet or locally invented
Signal-compatible protocol as a shortcut.

Current repository status:

- **DISABLED:** official libsignal runtime migration gate;
- **DISABLED:** Signal-backed session feature flag;
- **EXPERIMENTAL:** neutral encrypted protocol-record storage boundaries;
- **EXPERIMENTAL:** version-2 PQXDH invitation DTO and codec;
- **NOT ACTIVE:** official protocol processing in normal chats;
- **NOT IMPLEMENTED:** verified interoperability, migration, or rollback.

Concrete sequencing, prerequisites, and status are controlled by
[Task Queue](ai-workflow/TASK_QUEUE.md), especially TQ-100 through TQ-105. Strategic
context is in [Roadmap](ROADMAP.md).

## Repository-Pinned Artifact Boundary

The repository pins these official artifacts at `0.94.1` in the isolated
`core:signal-protocol` compile gate:

```text
org.signal:libsignal-client:0.94.1
org.signal:libsignal-android:0.94.1
```

The pin is a repository fact, not a recommendation that `0.94.1` remain selected and not a
claim that it is the latest upstream release. Upstream release activity continued after the
original artifact review. No version change is authorized by this document.

Before any future version decision, reverify from official Signal sources:

- current available versions and release notes;
- POM and Gradle metadata;
- Kotlin and Coroutines requirements;
- Android ABI contents and JNI packaging;
- API and record-format changes;
- license and support position;
- compatibility with the project's current and proposed toolchain.

Do not refresh a dependency merely because a newer version exists. A controlled migration
requires its own approved task, exact source record, rollback plan, and full regression
matrix.

## Historical Artifact Inspection

The following is a dated historical snapshot verified on **2026-05-26**, not a statement
about the latest upstream release on a later date:

- official Signal Maven metadata published `org.signal:libsignal-client:0.94.1`;
- the inspected `libsignal-client-0.94.1.pom` identified the license as `AGPLv3` and
  declared Kotlin stdlib `2.2.20` plus Coroutines Core JVM `1.10.2`;
- the inspected `libsignal-android-0.94.1.aar` contained Android native libraries for
  `armeabi-v7a`, `arm64-v8a`, `x86`, and `x86_64`, including `libsignal_jni.so`;
- the official libsignal repository documented that Java packages are published for
  Signal's own use and that external use is unsupported;
- the official repository documented JDK 17 for Android builds and the need for both
  `libsignal-client` and `libsignal-android` when building for Android.

These observations must be rechecked before a migration decision. They do not prove that
the current app compiles, packages, runs, interoperates, or may legally be distributed with
an official Signal runtime adapter.

## Existing Disabled Repository Work

### Official compile gate

The isolated `core:signal-protocol` module resolves official artifacts and provides a
Java-oriented dependency/API gate. Its migration flag is disabled, and normal application
runtime does not route sessions, messages, invitations, receipts, or persistence through
it.

A successful dependency resolution or compile gate proves only that a narrow source and
artifact boundary compiled in the environment where it was run. It does not prove Kotlin
adapter compatibility, Android packaging, JNI loading, protocol correctness,
interoperability, or release readiness.

### Experimental encrypted store boundary

`core:signalstore` defines neutral asynchronous boundaries for:

- identity records;
- session records;
- prekey records;
- signed-prekey records;
- Kyber prekey records;
- a reserved future sender-key boundary.

The module does not map official libsignal record types. Current Room storage tables hold
opaque serialized bytes encrypted with the existing local vault boundary. Their presence is
an experimental persistence spike, not a working protocol store.

The disabled adapter cannot open active Signal-backed sessions. No normal chat message is
sent, received, acknowledged, retried, or migrated through this store boundary.

### Experimental invitation version 2

`INVITE_V2_SIGNAL_PQXDH_EXPERIMENTAL` is a neutral DTO and codec boundary. Its fields are
placeholders for investigation. It is not a verified or authenticated PQXDH prekey bundle.

The active version-1 invitation flow remains separate. Version 1 must not silently accept,
reinterpret, downgrade to, or be confused with the experimental version-2 format.

## Accepted Identity And Device-Trust Boundary

Current normal onboarding uses warning-only trust on first use after reciprocal invitation
import:

- `VerificationStatus.UNVERIFIED` may communicate;
- the matching primary `DeviceApprovalStatus.PRIMARY_UNVERIFIED` may be the current single
  delivery target;
- the UI must retain an identity-not-verified warning;
- the state is not `VERIFIED` and is not suitable for real sensitive communications.

The current primary-only planner also permits `DeviceApprovalStatus.VERIFIED`. It blocks
`PENDING_APPROVAL`, device `KEY_CHANGED`, `REVOKED`, and a missing primary-device record.
The interactive-send path separately blocks contact `VerificationStatus.KEY_CHANGED`.

A future Signal design must not accidentally change this accepted onboarding decision,
auto-verify a new device, silently fan out to secondary devices, or reinterpret persisted
enum values. It must separately specify:

- contact identity verification;
- device approval;
- protocol-session establishment;
- key change;
- revocation;
- receipt targeting;
- synchronization and retry behavior.

Policy requires `VerificationStatus.UNKNOWN`, malformed, unsupported, corrupt, and invalid
persisted trust state to fail closed. Current runtime and tests do not yet prove this for
every migration, restart, direct-send, and queued-retry path. Signal migration must not
hide or inherit that unresolved gap.

## Blocking Program

### TQ-100 — Licensing and unsupported external use

Before distribution or runtime activation:

- obtain qualified legal review of AGPL obligations;
- evaluate Signal's unsupported-external-use position;
- record maintenance, API-change, interoperability, and support risk;
- obtain explicit project-owner acceptance or rejection.

This document is not legal advice and does not resolve that gate.

### TQ-101 — Controlled Android and Kotlin toolchain plan

The repository pin and the inspected artifact metadata have different Kotlin-era
requirements. A future adapter must not trigger an incidental broad upgrade.

Plan and verify Kotlin, Android Gradle Plugin, Compose, Room and KAPT/KSP, Coroutines,
WorkManager, lint, CI, instrumented tests, release packaging, and rollback as separate
reviewable steps.

### TQ-102 — Official record mapping and lifecycle

Map official types only after the record lifecycle is designed. Required behavior includes:

- identity, session, prekey, signed-prekey, and Kyber-prekey records;
- serialization and versioning;
- encrypted-at-rest persistence;
- transactional updates;
- restart and crash recovery;
- failure and partial-write behavior;
- deletion and local reset;
- identity replacement and trust-state interaction;
- migration and rollback.

Do not use an in-memory protocol store in the application path as a substitute for durable
semantics.

### TQ-103 — Authenticated PQXDH and prekey invitations

Define a versioned protocol payload with reviewed semantics for:

- identity and device binding;
- signed-prekey verification;
- one-time prekey selection and consumption;
- Kyber/PQ key lifecycle;
- queue binding;
- replay and duplicate import;
- malformed, oversized, unsupported, and downgraded input;
- version-1 and version-2 separation;
- migration and rollback.

The existing experimental DTO must not be promoted by filling fields without a reviewed
protocol design and interoperability evidence.

### TQ-104 — Per-device session and revocation semantics

A future design requires one independently established protocol session per approved
device. Define:

- fan-out selection;
- approval and verification requirements;
- primary and secondary device behavior;
- key-change handling;
- local and synchronized revocation semantics;
- receipt routing;
- retry and partial fan-out failure;
- device addition and removal;
- restart and state-recovery behavior.

Do not silently fan out the current single-device ciphertext.

### TQ-105 — Isolated Signal-backed test conversations

Initial activation, if all earlier gates clear, may apply only to new isolated test
conversations behind an explicit disabled-by-default feature flag.

It must not:

- migrate existing normal chats silently;
- reinterpret prototype ciphertext or session records;
- weaken downgrade separation;
- create a production-security claim;
- justify real sensitive use.

## Interoperability And Verification Gates

Before any runtime activation, require recorded evidence for:

- official API and record mapping;
- Java/Kotlin adapter behavior;
- JNI loading on every supported ABI;
- debug and release packaging;
- two-device official interoperability;
- restart and crash recovery;
- failed relay submission and retry;
- duplicate and replay handling;
- identity replacement and key change;
- device approval and revocation;
- receipt processing;
- version separation and downgrade rejection;
- migration and rollback;
- malformed and oversized input;
- encrypted-at-rest persistence;
- diagnostic and log redaction.

Every check must identify the exact commit, artifact versions, build tools, device or
emulator, ABI, API level, and pass/fail/skip result. A test present in source is not evidence
that it passed for the current commit.

## Independent Review Gate

No Signal-backed path may be called secure, production-ready, interoperable, or suitable
for sensitive communications until qualified independent reviewers evaluate the selected
scope, including:

- protocol integration;
- identity and device trust;
- persistence and migrations;
- Android Keystore and local-vault boundaries;
- parsing, replay, receipts, and delivery state;
- JNI, native packaging, dependencies, and release artifacts;
- downgrade and rollback behavior;
- relay and metadata assumptions;
- diagnostics and user-facing security claims.

Independent review is necessary but does not replace legal approval, fresh test evidence,
operational-security design, or resolution of release-blocking findings.

## Out Of Scope

This spike does not authorize:

- a handwritten Double Ratchet;
- activation of official libsignal in normal chats;
- dependency or toolchain upgrades;
- reinterpretation of existing conversations;
- multi-device fan-out;
- synchronized protocol revocation;
- enabling `PARANOIA_C4_AEAD`;
- a production relay or release;
- any Signal, post-quantum, or production-security marketing claim.

## Official Sources

Reverify these sources at the time of any dependency or migration decision:

- official repository, build instructions, packaging notes, license, and support boundary:
  <https://github.com/signalapp/libsignal>
- official Signal Maven repository:
  <https://build-artifacts.signal.org/libraries/maven/>
- detailed repository toolchain record:
  [libsignal Toolchain Review](LIBSIGNAL_TOOLCHAIN_REVIEW.md)

The repository pin, current upstream release, and selected migration target are three
different facts. Keep them separate.