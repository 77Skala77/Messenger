# Secure Messenger Roadmap

> **PARTLY HISTORICAL (2026-09-30):** statements here about the prototype session layer, `STANDARD_AEAD`, v1 invitations, reciprocal pairing, device approval states, or delivery diagnostics describe code removed by DEC-021. Relay, network, and process guidance may still apply. Current protocol: [Protocol v2](PROTOCOL_V2.md).

## Role And Control Boundaries

**Repository baseline reviewed:** `be98002f36bc5e442b748922a3a18401fe00d9c5`

This roadmap describes strategic directions and security gates. It is not a current-state
snapshot, implementation report, task queue, release approval, or proof that a test passed.

Use the project documents as follows:

- [Current Project State](ai-workflow/CURRENT_PROJECT_STATE.md) records recoverable facts.
- [Task Queue](ai-workflow/TASK_QUEUE.md) defines concrete order, prerequisites, and status.
- [Decisions](ai-workflow/DECISIONS.md) records accepted project-owner decisions.
- [MVP Delivery Baseline](MVP_DELIVERY_BASELINE.md) defines current delivery semantics.
- [Known Limitations](KNOWN_LIMITATIONS.md) and [Threat Model](THREAT_MODEL.md) define the
  current security-claim boundary.

A direction appearing here is not automatically approved, scheduled, implemented, enabled,
or suitable for a normal development task. Every implementation effort requires a scoped
queue item, explicit prerequisites, acceptance criteria, migration and rollback analysis,
and recorded verification.

Secure Messenger remains an audit-oriented MVP prototype. It must not be represented as
ready for real sensitive communications or as having production-grade security.

## Current Foundation — Android MVP Prototype

The repository contains an Android 1-to-1 messenger prototype whose active chat path uses:

- `SimplifiedSessionManager`;
- `STANDARD_AEAD`;
- local encrypted-message persistence and durable session/counter state;
- relay send, fetch, delete, retry, and best-effort WorkManager synchronization;
- authenticated replay suppression and encrypted delivery receipts;
- QR invitations, safety-number and verification-QR workflows;
- warning-only trust on first use;
- primary-device-only delivery for the current single-session ciphertext design;
- debug relay configuration, health checks, diagnostics, and QA controls.

Current delivery receipts distinguish relay acceptance from receiver-application delivery:

- `SENT` means the relay accepted the encrypted blob;
- `DELIVERED` means the sender authenticated a receipt confirming receiver acceptance,
  decryption, and durable storage;
- `DELIVERED` does not mean that a human opened or read the message.

Warning-only trust on first use permits communication after reciprocal invitation import
when the contact is `VerificationStatus.UNVERIFIED` and its matching primary device is
`DeviceApprovalStatus.PRIMARY_UNVERIFIED`. Encryption in that state does not prove the
identity of the intended human. The UI must preserve the unverified-identity warning until
the safety number or verification QR is compared through a trusted or independent channel.

The primary-only delivery planner permits `PRIMARY_UNVERIFIED` and `VERIFIED`, and blocks
`PENDING_APPROVAL`, device `KEY_CHANGED`, `REVOKED`, and a missing primary-device record.
The interactive-send path also blocks contact `VerificationStatus.KEY_CHANGED`.

Complete fail-closed handling of `VerificationStatus.UNKNOWN`, malformed, unsupported,
corrupt, migrated, restarted, and queued-retry trust state is not yet an established
runtime guarantee. That gap requires a separate approved hardening task and regression
matrix before it can be described as resolved.

The current foundation does not provide reviewed Double Ratchet forward secrecy,
post-compromise recovery, cryptographic multi-device fan-out, synchronized protocol
revocation, timely delivery guarantees, production relay privacy, secure backup, secure
wipe, or a production release process.

Signal-backed sessions remain disabled pending the crypto rewrite (see
`docs/ai-workflow/REWRITE_PLAN.md`). `PARANOIA_C4_AEAD` was removed from the codebase
entirely on 2026-09-27, not kept disabled. Experimental modules, storage boundaries,
DTOs, interfaces, and fixtures do not strengthen the active chat path.

## Near-Term Program — Documentation, Recovery, And Verification

The immediate program is repository recovery and fresh evidence, not protocol activation.
Concrete order and status remain authoritative in the [Task Queue](ai-workflow/TASK_QUEUE.md).

### 1. Complete documentation reconciliation

Finish the file-by-file TQ-004 audit so non-workflow documentation agrees with current
code, accepted decisions, exact persisted identifiers, disabled-feature gates, delivery
semantics, and historical-evidence boundaries.

Documentation changes must not be treated as proof that an underlying runtime, test,
release, infrastructure, or security gap was fixed.

### 2. Rebuild a trusted development environment

TQ-005 requires a fresh trusted desktop or laptop environment and clean clone from GitHub.
Do not rely on unavailable local history, unverified caches, old signing material, or
historical build outputs.

The recovered environment must record exact tool versions, repository HEAD, remote and
branch state, ignored local files, and absence of committed secrets or machine-specific
paths.

### 3. Re-establish the executable verification baseline

TQ-006 must produce fresh pass, fail, or skip evidence for the current commit, including:

- Android debug assembly and lint;
- project and Signal-boundary unit tests;
- relay tests;
- compatible instrumented UI, storage, Keystore, transport, and delivery checks;
- explicit confirmation that relay-dependent instrumented tests executed rather than being
  skipped.

Historical phone, emulator, restart, retry, receipt, LAN, and VPS results remain context,
not current verification.

### 4. Establish the release-variant baseline

TQ-007 must inspect the real release artifact and configuration without implying production
readiness. Required areas include:

- release build and lint results;
- signing state;
- R8 and resource shrinking output;
- cleartext denial and relay URL policy;
- manifest, permissions, exported components, package identity, APK contents, and ABIs;
- absence of debug controls, secrets, local paths, fixtures, and unintended endpoints;
- upgrade and rollback limitations.

Release cleartext denial and shrinking configuration exist in source, but configuration is
not proof that the current release artifact builds correctly or is safe to distribute.

### 5. Add supply-chain and release controls

TQ-008 must split dependency and release hardening into reviewable steps:

- dependency verification or locking;
- deterministic, reviewable SBOM generation;
- transitive and native-artifact inventory;
- CVE analysis workflow;
- license inventory and review;
- reproducible-build assessment;
- signing-key custody and release procedure;
- CI enforcement for the selected controls.

No broad dependency upgrade should be mixed into these controls without a separate reviewed
migration task.

## Current-Protocol Hardening Before Expansion

After fresh executable evidence exists, high-risk gaps in the active prototype must be
scoped independently before adding protocol or product complexity. Candidate work includes:

- fail closed for `VerificationStatus.UNKNOWN` and invalid persisted trust values;
- targeted migration 1 to 2 behavior for old `KEY_CHANGED`, unknown, malformed, and corrupt
  values;
- restart, queued-retry, Room round-trip, migration, and deserialization regression tests
  across every contact and device state;
- verification that direct UI send, background retry, and migration paths enforce the same
  accepted trust policy;
- precise failure reporting rather than silently skipping malformed retry records;
- review of delivery-receipt failure, replay, and restart behavior;
- denial-of-service limits for malformed or unauthenticated relay queues.

These are roadmap gaps, not approved implementation tasks until entered into the Task Queue
with explicit scope and authorization.

## Gated Security Program — Reviewed Session Protocol

The prototype static-ECDH session must not be replaced with a handwritten Signal-like
protocol or a locally invented Double Ratchet.

Any future session migration must follow a reviewed, interoperable path through the gated
official libsignal investigation described in
[Signal Migration Spike](SIGNAL_MIGRATION_SPIKE.md) and
[libsignal Toolchain Review](LIBSIGNAL_TOOLCHAIN_REVIEW.md).

Current status is:

- **DISABLED:** official libsignal runtime gate;
- **DISABLED:** Signal-backed session feature flag;
- **EXPERIMENTAL:** neutral encrypted Signal-record storage boundaries;
- **EXPERIMENTAL:** version-2 PQXDH invitation DTO and codec;
- **NOT ACTIVE:** official protocol processing in normal chats;
- **NOT IMPLEMENTED:** verified protocol interoperability or migration.

The blocked TQ-100 through TQ-105 program must resolve, at minimum:

1. legal and licensing acceptability, including unsupported external-use risk;
2. controlled Kotlin, Android Gradle Plugin, Compose, Room, Coroutines, WorkManager, lint,
   and CI migration planning;
3. official record mapping, encrypted persistence, transaction, restart, failure, deletion,
   and identity-change semantics;
4. versioned authenticated PQXDH/prekey invitation design, lifecycle, replay, downgrade,
   malformed-input, migration, and rollback handling;
5. JNI and ABI packaging, release size, stripping, and device compatibility;
6. one independently established session per approved device;
7. explicit fan-out, approval, key-change, revocation, receipt, synchronization, and failure
   behavior;
8. interoperability and migration tests;
9. independent protocol-integration and application-security review.

Only new isolated test conversations may be considered for initial activation behind an
explicit feature flag. Existing normal chats must not be silently migrated, reinterpreted,
or downgraded. No Signal-backed path may justify real sensitive use before its complete
review gate is satisfied.

## Gated Production Program

### Independent security review

Independent review is a cross-cutting gate rather than a late product feature. TQ-106
requires a stable scope, fresh verification baseline, resolved high-risk gaps, qualified
reviewers, and funding.

Candidate review areas include:

- active AEAD, nonce and counter durability;
- session design or reviewed migration;
- invitation parsing, trust-state handling, replay, receipts, and delivery state;
- Keystore, vault, Room, migrations, backup, reset, and deletion boundaries;
- Android UI, memory, notification, keyboard, clipboard, and accessibility exposure;
- relay confidentiality, availability, retention, abuse, and metadata limits;
- dependencies, native artifacts, build, signing, release, diagnostics, and supply chain;
- disabled and experimental boundary enforcement.

A pre-integration protocol review, release/application review, infrastructure/privacy
review, and final review of the selected production scope may occur at different gates.

### Production relay and operations

TQ-107 remains blocked. Historical VPS success is not production evidence.

A production relay decision requires reviewed design and operational ownership for:

- HTTPS and certificate lifecycle;
- authentication and abuse controls;
- rate, queue, storage, and resource limits;
- minimal logging, payload-log prohibition, queue-identifier protection, and retention;
- monitoring, alerting, patching, incident response, backup, and recovery;
- secrets and access-control custody;
- deployment authorization and infrastructure ownership;
- privacy and metadata limitations.

TLS protects the network hop but does not hide IP addresses, timing, queue metadata,
approximate message size, or traffic frequency from the relay operator.

### Production release exit gate

A production release remains blocked until the selected scope has, at minimum:

- fresh debug, instrumented, release, and relay verification;
- reviewed session security and migration behavior;
- complete trust-state fail-closed enforcement for supported persistence paths;
- dependency verification, SBOM, license and native-artifact review;
- controlled signing-key custody and reproducible-build assessment;
- production relay and operational-security approval;
- privacy, incident-response, update, rollback, and vulnerability-handling procedures;
- independent security review with resolved release-blocking findings.

## Deferred Research Directions

The following directions are not active implementation phases and are not queued merely
because they appear in this roadmap:

- additional reviewed ML-KEM hybrid designs beyond the gated Signal-migration direction;
- MLS or another reviewed design for groups and multi-device membership transitions;
- sealed-sender-style delivery;
- message padding and batching;
- opaque or single-use queues;
- relay chaining;
- Tor, reviewed P2P, or local transports;
- attachments and media;
- app lock and biometric unlock;
- encrypted backup and device transfer;
- secure data-destruction design;
- synchronized cryptographic revocation.

Each direction requires a separate product decision, threat analysis, protocol and storage
design, migration and rollback plan, abuse analysis, tests, and security review before it
can enter the active queue.

### Paranoia C4 (removed)

`PARANOIA_C4_AEAD` was never a required roadmap destination. It was removed from the
codebase entirely on 2026-09-27 (see `docs/ai-workflow/DECISIONS.md` DEC-016) rather
than kept as a disabled placeholder, because a home-grown cipher cascade does not add
provable security over a single reviewed AEAD suite, and this project's committed
crypto direction (`docs/ai-workflow/REWRITE_PLAN.md`) is a reviewed protocol —
`libsignal` Double Ratchet + PQXDH — not additional home-grown layers on top of it.

Any future proposal to revive a similar idea would require a separate accepted
specification, independently reviewed primitive implementations, explicit
domain-separated KDF and MAC design, nonce and key lifecycle analysis, test vectors,
interoperability and integration tests, migration and rollback planning, release
review, and independent cryptographic audit — the same bar DEC-016 already sets.

A cipher cascade of this kind cannot replace a reviewed session protocol, identity
system, device-trust model, or production-security program regardless of primitives.
