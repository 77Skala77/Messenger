# External Audit Readiness

## Status And Claim Boundary

**Repository baseline reviewed:** `62f47a3b9498909df10bd3ac16bbd14ac20f1eec`

No independent cryptographic, protocol-integration, Android application, relay/privacy, or
release security audit has been conducted.

This file is an audit-planning checklist. It is not an audit report, certification,
penetration-test result, production-release approval, or proof that repository tests passed
for the reviewed commit.

TQ-106 remains **BLOCKED** by a stable scope, fresh executable evidence, resolution or
explicit acceptance of high-risk gaps, funding, and a qualified independent reviewer.

The project can support reviewer orientation and scope discussion. It is not ready to claim
completion of a formal production audit or suitability for real sensitive communications.

## Readiness Matrix

| Area | Status | Meaning |
| --- | --- | --- |
| Documentation orientation | **PARTIAL** | Core security documents exist and have been reconciled file by file; the final audit commit still needs a cross-document and link review. |
| Threat and architecture scope | **PARTIAL** | Threat model, crypto design, delivery baseline, limitations, roadmap, and migration gates exist; reviewer and owner must agree on exact scope. |
| Clean build and test evidence | **BLOCKED** | TQ-005 lacks a trusted desktop environment, so TQ-006 has no fresh clean-clone baseline. |
| Release evidence | **BLOCKED** | TQ-007 has no current release build, lint, signing, R8, manifest, permission, ABI, or artifact-inspection record. |
| Supply-chain evidence | **BLOCKED** | Verification/locking, full resolved inventory, SBOM, CVE workflow, license inventory, and reproducibility remain open under TQ-008. |
| Active-session assurance | **BLOCKED** | Normal chats use the prototype session without reviewed Double Ratchet forward secrecy or post-compromise recovery. |
| Signal migration assurance | **BLOCKED** | Official runtime integration, record mapping, PQXDH semantics, per-device sessions, interoperability, legal review, and rollback are disabled or absent. |
| Production relay and operations | **BLOCKED** | No current authorized production deployment or operational-security baseline exists. |
| Independent audit | **NOT PERFORMED** | No external reviewer has issued findings or a final report. |

## Evidence Vocabulary

Audit material must distinguish:

- **CURRENT REPOSITORY FACT** — visible at the selected commit;
- **TEST PRESENT IN SOURCE** — scenario exists but has no current recorded result;
- **CURRENT EXECUTED EVIDENCE** — exact command, environment, and pass/fail/skip result;
- **HISTORICAL EVIDENCE** — useful past context, not proof of current state;
- **DISABLED** — present but blocked by flag, gate, or isolated boundary;
- **EXPERIMENTAL** — spike, DTO, interface, fixture, or storage boundary;
- **PLANNED/DEFERRED** — future direction without implementation evidence;
- **UNVERIFIED** — not confirmed for the selected commit or infrastructure.

A test file, interface, DTO, feature flag, documentation statement, old screenshot, or VPS
report must not be presented as current executed evidence.

Documentation-only commits change descriptions, not runtime behavior or assurance evidence.

## Proposed Audit Tracks

### 1. Active MVP cryptography and delivery

Review the path used by normal chats:

- `STANDARD_AEAD`, AES-GCM parameters, key derivation, nonce/counter durability, and AAD;
- serialization, parsing, error handling, and plaintext lifetime;
- prototype session derivation and claim limits;
- persistence before relay submission;
- retry, cancellation, and WorkManager boundaries;
- authentication before acceptance;
- Room replay transaction and duplicate suppression;
- encrypted delivery receipts;
- `PENDING`, `FAILED`, `SENT`, `DELIVERED`, and `RECEIVED` meanings;
- malformed and unauthenticated relay blobs;
- diagnostics and logging.

The reviewer must not describe the prototype as Signal, PQXDH, Double Ratchet,
forward-secret, or post-compromise secure.

### 2. Identity, pairing, and device trust

Review both persisted trust models:

```text
VerificationStatus.VERIFIED
VerificationStatus.UNVERIFIED
VerificationStatus.KEY_CHANGED
VerificationStatus.UNKNOWN

DeviceApprovalStatus.PRIMARY_UNVERIFIED
DeviceApprovalStatus.VERIFIED
DeviceApprovalStatus.PENDING_APPROVAL
DeviceApprovalStatus.KEY_CHANGED
DeviceApprovalStatus.REVOKED
```

Scope includes reciprocal invitations, warning-only trust on first use, persistent
unverified warnings, safety verification, first-contact substitution, key changes,
primary-only targeting, persisted enum compatibility, Room migration 1 to 2, restart, and
queued retry.

Policy requires `VerificationStatus.UNKNOWN` and malformed, unsupported, corrupt, or
invalid persisted state to fail closed. Current runtime and tests do not prove this for
every direct-send, migration, restart, and retry path. This is a known high-risk item.

### 3. Android local security and privacy

Review Keystore and vault use, key invalidation, Room schemas and migrations, local metadata,
process memory, UI rendering, keyboard, clipboard, notifications, screenshots,
accessibility, debug controls, reset behavior, retained relay URL, flash-erasure limits,
and the absence of secure backup, device transfer, and app lock.

Keystore use reduces some extraction risk but does not protect an unlocked compromised
device or every plaintext surface.

### 4. Relay, metadata, and availability

Review relay parsing, TTL, deletion, queue identifiers, replay/reorder/delay/withholding,
unauthenticated queue flooding, source IP, timing, size and frequency metadata, debug HTTP,
release HTTP rejection, HTTPS limits, logging, retention, abuse controls, rate/resource
limits, monitoring, and incident response.

Historical VPS success is not current production evidence. TQ-107 remains blocked.

### 5. Build, dependency, and release security

Review the Gradle wrapper and repositories, resolved dependency graphs, plugins, verification
and locking, SBOM, CVEs, licenses, native libraries and ABIs, disabled libsignal artifacts,
variant separation, cleartext policy, manifest, permissions, exported components, R8,
signing, reproducibility, CI trust, secrets, update, rollback, and incident process.

This track requires TQ-007 and TQ-008 evidence. Source configuration alone is insufficient.

### 6. Disabled and experimental boundaries

Review whether these can accidentally become reachable:

- official libsignal runtime gate;
- Signal-backed session flag;
- neutral Signal-record stores;
- experimental PQXDH invitation version 2;
- active version-1 invitation separation;
- confirmation that `PARANOIA_C4_AEAD` is absent (removed 2026-09-27, not a placeholder to inspect);
- debug-only QA features;
- release flags and resources.

### 7. Future official libsignal migration

Do not mix a hypothetical Signal implementation into the active-MVP audit. A later dedicated
review must cover TQ-100 through TQ-105: legal/support decision, controlled toolchain
migration, official record mapping, authenticated PQXDH/prekey semantics, per-device
sessions, JNI/ABI packaging, interoperability, migration, downgrade, rollback, and isolated
test activation.

Experimental stores and DTOs are not implementation evidence.

## Repository Evidence Available For Intake

The repository contains reviewer-orientation material:

- README, threat model, crypto design, limitations, delivery baseline, and roadmap;
- accepted device-trust policy;
- Signal migration and toolchain reviews;
- dependency and supply-chain review;
- relay, QA, localization, and deployment guides;
- source for domain, crypto, storage, delivery, transport, UI, and feature flags;
- unit, instrumented, and relay test source;
- GitHub workflow configuration.

Test source includes scenarios for AEAD failure, nonce/counter behavior, serialization,
replay, two-peer delivery, retry, authenticated duplicates, delivery receipts, device
policy, Room persistence/reset, Keystore, relay health/TTL/delete, HTTP transport, and
disabled Signal boundaries.

This describes source coverage only. It does not claim those tests passed at the selected
commit.

## Historical Evidence Boundary

Repository documents record historical phone, emulator, retry, receipt, duplicate,
restart, LAN, and VPS QA. This may help reviewers understand intended flows, but must not be
used as current proof of the selected commit, devices, infrastructure, ownership, DNS, TLS,
firewall, service, SSH, logging, retention, or production readiness.

## Missing Evidence Before Formal Execution

### Trusted environment and current baseline

TQ-005 is blocked because no trusted desktop environment is available. TQ-006 must later
record exact pass/fail/skip results for:

```bash
cd android-app
./gradlew :app:assembleDebug
./gradlew :app:lintDebug :core:signal-protocol:testDebugUnitTest :core:signalstore:test test
node --test server/relay/test/*.test.mjs
```

With a compatible emulator or device:

```bash
./gradlew :core:ui:connectedDebugAndroidTest \
  :core:storage:connectedDebugAndroidTest \
  :core:transport:connectedDebugAndroidTest
```

Record commit, JDK, Gradle, Android SDK, device/emulator, API level, ABI, and whether
relay-dependent tests executed rather than being skipped.

### Release baseline

TQ-007 must record:

```bash
cd android-app
./gradlew :app:assembleRelease :app:lintRelease
```

Inspect signing, R8/resource shrinking, cleartext policy, debug-feature exclusion, manifest,
permissions, exported components, APK contents, ABIs, package identity, secrets, paths,
fixtures, endpoints, and upgrade limitations.

### Supply-chain baseline

TQ-008 must produce reviewed resolved graphs, dependency verification and/or locking,
direct/transitive/plugin/native inventory, SBOM, CVE analysis, license review,
reproducibility assessment, signing-key procedure, and CI enforcement.

### Stable audit commit

Before reviewer work begins:

- select and freeze the audit commit or controlled audit branch;
- refresh current-state, handoff, decisions, and task records to that commit;
- verify documentation links and terminology;
- record all known findings and accepted risks;
- define post-freeze fixes and retests.

## Known Release-Blocking Findings

Disclose these before audit intake:

- prototype static ECDH/HKDF instead of reviewed PQXDH and Double Ratchet;
- no production-grade forward secrecy or post-compromise recovery;
- warning-only first contact without independent identity authentication;
- incomplete fail-closed evidence for unknown and invalid persisted trust state;
- no cryptographic multi-device fan-out or synchronized protocol revocation;
- no timely-delivery guarantee and no read receipts;
- local metadata and plaintext-surface exposure;
- reset is not forensic erasure and retains the relay URL;
- local deletion cannot guarantee deletion of every relay copy;
- relay metadata exposure and denial of service;
- no production relay or current verified infrastructure;
- official libsignal integration disabled and blocked (rewrite in progress, see `docs/ai-workflow/REWRITE_PLAN.md`);
- `PARANOIA_C4_AEAD` removed from the codebase (2026-09-27) — no longer a boundary to audit;
- dependency verification, SBOM, reproducibility, and signing process incomplete;
- no independent audit completed.

This list is not guaranteed complete; an audit may find additional issues.

## Audit Packet Requirements

Provide a versioned packet with:

- exact commit and access method;
- in-scope modules, variants, infrastructure, and flows;
- explicit exclusions and security-claim boundaries;
- threat model, accepted decisions, and known risks;
- architecture, data flow, key lifecycle, trust boundaries, formats, schemas, and APIs;
- feature flags and disabled boundaries;
- clean-clone environment and raw build/test results;
- device/emulator and release-artifact evidence;
- resolved dependencies, native inventory, SBOM, CVE, and license findings;
- known issue register with owners;
- rules of engagement, test-data policy, contacts, and secure communication method.

Do not place production secrets, user plaintext, private keys, live tokens, or unredacted
sensitive logs in the ordinary repository audit packet.

## Reviewer Deliverables

Agree on:

- reviewed commit, artifacts, variants, and exclusions;
- executive summary with strict claim boundaries;
- findings with severity, evidence, affected code, and reproduction;
- threat-model corrections;
- release-blocking versus defense-in-depth classification;
- remediation guidance;
- unresolved questions and limitations;
- retest plan and final finding status.

The report should describe scope, evidence, limitations, and residual risk rather than
unqualifiedly declaring the product secure.

## Remediation And Retest

For every finding:

1. assign owner and severity;
2. reproduce against the audit commit;
3. create a scoped task;
4. record security, compatibility, migration, and rollback impact;
5. implement an isolated change;
6. run the relevant regression set;
7. update documentation;
8. obtain reviewer retest or evidence validation;
9. record residual risk and disposition.

A runtime finding is not closed merely because documentation changed.

## Entry And Exit Gates

A formal audit engagement can begin after owner and reviewer accept a stable scope and the
packet has, at minimum:

- selected commit or controlled branch;
- fresh clean-clone debug/test evidence;
- instrumented evidence where required;
- release evidence if release is in scope;
- dependency and native inventory;
- known-risk register;
- reconciled design, threat, limitation, and decision documents;
- exact disabled and experimental boundaries;
- qualified reviewer, funding, and secure communication process.

Audit start does not authorize production release. Release still requires remediation and
retest of blockers plus production relay, supply-chain, signing, privacy, update, rollback,
and incident-response gates.

## Related Documents

- [Threat Model](THREAT_MODEL.md)
- [Cryptographic Design](CRYPTO_DESIGN.md)
- [Known Limitations](KNOWN_LIMITATIONS.md)
- [MVP Delivery Baseline](MVP_DELIVERY_BASELINE.md)
- [Roadmap](ROADMAP.md)
- [Signal Migration Spike](SIGNAL_MIGRATION_SPIKE.md)
- [libsignal Toolchain Review](LIBSIGNAL_TOOLCHAIN_REVIEW.md)
- [Dependency And Supply-Chain Review](DEPENDENCY_REVIEW.md)
- [Device Trust Policy](ai-workflow/DEVICE_TRUST_POLICY.md)
- [Task Queue](ai-workflow/TASK_QUEUE.md)

The selected audit commit, not an unrecorded later branch state, is the authoritative review
target.