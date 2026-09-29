# Decisions — Secure Messenger MVP

This file records durable project decisions that continue to constrain implementation, documentation, testing, and release work.

It is not:

- a current-state snapshot — use [`CURRENT_PROJECT_STATE.md`](CURRENT_PROJECT_STATE.md);
- a task list — use [`TASK_QUEUE.md`](TASK_QUEUE.md);
- a temporary session handoff — use [`HANDOFF.md`](HANDOFF.md);
- a replacement for detailed design documents, source code, tests, or threat analysis.

## Authority and maintenance

- Repository: `https://github.com/77Skala77/Messenger`
- Authoritative recoverable branch: `main`
- Repository evidence reviewed for this initial decision log: `c974e460b827022a841e374a95cb1ed736b2c25b`
- Initial decision-log recording date: `2026-07-26`

Most technical decisions below predate this file. Their exact original decision dates are not known from the current repository, so this file does not invent them.

Use these statuses:

- **ACCEPTED** — the decision currently governs the project.
- **GATED** — accepted direction, but activation is blocked by explicit prerequisites.
- **DEFERRED** — deliberately postponed; not current implementation.
- **REJECTED** — explicitly not accepted.
- **SUPERSEDED** — replaced by a later decision.

When changing an accepted decision:

1. Do not silently rewrite its meaning.
2. Add a new decision that supersedes it.
3. Link both records.
4. Update affected code, tests, `CURRENT_PROJECT_STATE.md`, checklists, and design documents together.
5. Record migration, compatibility, rollback, security, and release consequences.

## Decision index

| ID | Decision | Status |
| --- | --- | --- |
| DEC-001 | GitHub default branch is the recoverable project baseline | ACCEPTED |
| DEC-002 | The application remains an explicitly non-production MVP | ACCEPTED |
| DEC-003 | Normal chats remain on the prototype session path until reviewed migration gates are met | ACCEPTED |
| DEC-004 | `STANDARD_AEAD` is the only active payload-encryption suite | ACCEPTED |
| DEC-005 | The relay is an untrusted opaque transport | ACCEPTED |
| DEC-006 | Message acceptance and delivery state require durable authenticated client evidence | ACCEPTED |
| DEC-007 | Device delivery fails closed and targets one usable primary device | ACCEPTED |
| DEC-008 | Local storage protects selected content, not all database metadata | ACCEPTED |
| DEC-009 | HTTP relay access is debug-only; release cleartext is denied | ACCEPTED |
| DEC-010 | Signal migration must use a reviewed official integration, not a handwritten ratchet | GATED |
| DEC-011 | SignalStore and invitation v2 remain disabled experimental boundaries | ACCEPTED |
| DEC-012 | `PARANOIA_C4_AEAD` remains disabled and cannot replace a session protocol | SUPERSEDED by DEC-016 |
| DEC-013 | Diagnostics use minimum necessary, sanitized technical data | ACCEPTED |
| DEC-014 | Reset and deletion must not claim forensic secure erase | ACCEPTED |
| DEC-015 | Workflow documents have separate, non-overlapping roles | ACCEPTED |
| DEC-016 | `PARANOIA_C4_AEAD` removed entirely rather than kept disabled | ACCEPTED |
| DEC-017 | Keep and finish `:core:signal-protocol`/`:core:signalstore` rather than rebuild clean | ACCEPTED |
| DEC-018 | libsignal store adapter semantics: blocking, strict identity trust, fail-closed Kyber handling | ACCEPTED |

---

## DEC-001 — GitHub default branch is the recoverable project baseline

**Status:** ACCEPTED
**Recorded:** 2026-07-26

### Context

The previous laptop was formatted and sold. Its local repository clone and unpushed commits are unavailable. Historical chat extracts describe later local work, but that work cannot be recovered or verified as current repository content.

### Decision

The current GitHub default branch is the authoritative recoverable baseline.

Use this evidence order when claims conflict:

1. current repository code, configuration, and feature flags;
2. current tests and CI;
3. current repository documentation;
4. historical handoff notes and chat extracts.

### Consequences

- A historical commit message, file description, screenshot, DTO, or test report does not prove that current implementation exists.
- Lost local work may be reconsidered as an idea, but it must not be recreated wholesale without reviewing the current repository.
- Repository changes, commit status, push status, branch, HEAD, and working-tree state must be reported accurately.
- Documentation must distinguish current GitHub evidence from historical context.

### Revisit when

- Another verified repository or backup is recovered.
- The authoritative branch changes.
- Repository governance moves to a different source of truth.

---

## DEC-002 — The application remains an explicitly non-production MVP

**Status:** ACCEPTED
**Recorded from existing repository evidence:** 2026-07-26

### Context

The project contains working encrypted messaging and QA infrastructure, but the active session design is still a prototype. Production transport, complete release discipline, multi-device protocol semantics, secure backup, application lock, independent audit, and other production requirements are incomplete.

### Decision

The project must be described as an MVP for architecture review, development verification, QA, and security review.

It must not be represented as:

- ready for real sensitive communications;
- production-secure;
- independently audited;
- officially Signal-interoperable;
- providing production-grade forward secrecy or post-compromise recovery.

### Consequences

- Internal debug QA builds and technical-evaluation release variants are permitted with explicit warnings.
- Public production distribution is blocked.
- Release notes, UI copy, README files, and handoffs must preserve the MVP warning.
- Historical successful QA does not upgrade the project's assurance level.

### Evidence

- [`../../README.md`](../../README.md)
- [`../THREAT_MODEL.md`](../THREAT_MODEL.md)
- [`../KNOWN_LIMITATIONS.md`](../KNOWN_LIMITATIONS.md)
- [`../ROADMAP.md`](../ROADMAP.md)

### Revisit when

Only after production session security, transport operations, release discipline, compatibility, independent review, and documented residual risks are complete.

---

## DEC-003 — Normal chats remain on the prototype session path until reviewed migration gates are met

**Status:** ACCEPTED
**Recorded from existing repository evidence:** 2026-07-26

### Context

The executable application uses `SimplifiedSessionManager` with static ECDH and HKDF-derived per-message keys. This supports MVP testing but does not provide the security properties of PQXDH plus Double Ratchet.

Disabled Signal-related modules and storage spikes exist, but they are not part of normal messaging.

### Decision

Normal chats remain on the prototype session path until a separately reviewed migration satisfies all protocol, storage, device, compatibility, licensing, testing, and rollback gates.

The prototype must remain clearly labelled as non-production.

### Consequences

- No documentation may call the active path Signal, PQXDH, X3DH, or Double Ratchet.
- No disabled adapter or experimental invitation format may be wired into ordinary conversations as a shortcut.
- Migration must preserve clear version separation so prototype and future Signal-backed records cannot be confused or silently downgraded.
- Existing conversations must not be silently reinterpreted or migrated without an explicit design.

### Evidence

- [`../CRYPTO_DESIGN.md`](../CRYPTO_DESIGN.md)
- [`../MVP_DELIVERY_BASELINE.md`](../MVP_DELIVERY_BASELINE.md)
- [`../SIGNAL_MIGRATION_SPIKE.md`](../SIGNAL_MIGRATION_SPIKE.md)

### Revisit when

A reviewed Signal-backed implementation is ready behind an explicit feature flag with migration, interoperability, failure, restart, device, licensing, and independent-review evidence.

---

## DEC-004 — `STANDARD_AEAD` is the only active payload-encryption suite

**Status:** ACCEPTED
**Recorded from existing repository evidence:** 2026-07-26

### Context

The repository has one executable payload-encryption suite. The former disabled experimental Paranoia C4 boundary was removed 2026-09-27 (see DEC-016).

### Decision

`STANDARD_AEAD` remains the only active suite.

Its documented MVP design uses:

- JCA `AES/GCM/NoPadding`;
- 256-bit message keys;
- 96-bit nonces formed from a random per-key prefix and durable monotonic counter;
- 128-bit authentication tags;
- authenticated associated data;
- HKDF-SHA-256 domain separation for per-message keys;
- persistence of counter state before ciphertext is emitted.

### Consequences

- Nonce/counter persistence is a security invariant.
- Backup or restore changes must re-evaluate nonce rollback risk.
- AAD and wire-format changes require explicit versioning and compatibility review.
- Authentication must complete before plaintext is accepted.
- Plaintext fallback and downgrade behavior are forbidden.
- Retry and concurrency must not cause key or nonce reuse.

### Evidence

- [`../CRYPTO_DESIGN.md`](../CRYPTO_DESIGN.md)
- [`../THREAT_MODEL.md`](../THREAT_MODEL.md)

### Revisit when

A reviewed replacement or additional suite has complete specification, test vectors, migration rules, failure behavior, and independent cryptographic review.

---

## DEC-005 — The relay is an untrusted opaque transport

**Status:** ACCEPTED
**Recorded from existing repository evidence:** 2026-07-26

### Context

The Node relay transports opaque encrypted envelopes and expiration metadata. It can still observe network and routing metadata and can drop, delay, replay, reorder, withhold, or delete traffic.

### Decision

The relay is never a confidentiality, authenticity, replay-safety, device-trust, or message-acceptance authority.

Client cryptography and durable client state must remain authoritative.

### Consequences

- The relay must not receive plaintext or private keys.
- Relay acceptance means only that the server accepted an envelope.
- The client must authenticate envelopes and receipts independently.
- Denial of service and metadata observation remain known limitations.
- Queue identifiers, timing, size, IP address, and frequency exposure must not be understated.
- A public relay remains QA infrastructure until separately hardened and reviewed.
- Historical VPS success does not prove current endpoint availability or security.

### Evidence

- [`../THREAT_MODEL.md`](../THREAT_MODEL.md)
- [`../MVP_DELIVERY_BASELINE.md`](../MVP_DELIVERY_BASELINE.md)
- [`../../README.md`](../../README.md)

### Revisit when

A new transport or metadata-hiding design is reviewed. Even then, transport trust must be documented explicitly rather than assumed.

---

## DEC-006 — Message acceptance and delivery state require durable authenticated client evidence

**Status:** ACCEPTED
**Recorded from existing repository evidence:** 2026-07-26

### Context

The relay cannot truthfully prove that the receiving application accepted a message. Crashes, replay, duplicate fetch, retry, and relay-deletion failure can otherwise create false or inconsistent state.

### Decision

For incoming messages:

1. authenticate before acceptance;
2. atomically persist the encrypted received record and replay marker;
3. delete the relay envelope only after durable acceptance, or after a durably recognized authenticated duplicate.

For outgoing messages:

1. persist encrypted pending state before relay transmission;
2. retain durable retry metadata on failure;
3. mark `Delivered` only after the sender validates an authenticated receiver receipt and records receipt replay state.

### Consequences

- Relay acceptance is displayed only as sent-to-server state.
- `Delivered` means receiver-side application acceptance, decryption, durable storage, and authenticated receipt.
- `Delivered` is not a read receipt.
- Duplicate messages and receipts must be idempotent.
- Receipt control payloads must not appear as visible chat messages.
- WorkManager remains best effort and is not a timely-delivery guarantee.
- Transaction ordering and restart behavior are security-sensitive.

### Evidence

- [`../CRYPTO_DESIGN.md`](../CRYPTO_DESIGN.md)
- [`../MVP_DELIVERY_BASELINE.md`](../MVP_DELIVERY_BASELINE.md)
- [`../../README.md`](../../README.md)

### Revisit when

Delivery or receipt protocol semantics change, a read-receipt feature is designed, or transport/session migration requires a new durable state machine.

---

## DEC-007 — Device delivery fails closed and targets one usable primary device

**Status:** ACCEPTED
**Recorded from existing repository evidence:** 2026-07-26

### Context

The MVP records device trust state and has a future fan-out boundary, but it does not implement reviewed per-device ratchets, synchronized revocation, or multi-device state synchronization.

### Decision

The current delivery path targets only the established usable primary device.

Sending must fail closed when the relevant primary device is:

- pending or awaiting approval;
- revoked;
- key-changed;
- missing;
- unverified or otherwise ambiguous.

No silent fan-out to secondary devices is permitted.

### Consequences

- Additional devices may be staged but are not automatically trusted or enabled for delivery.
- Identity/key changes require explicit handling.
- Local revocation blocks local delivery only.
- Documentation must not call local revocation synchronized cryptographic revocation.
- Future multi-device work requires a separate reviewed session per approved device.

### Evidence

- [`../THREAT_MODEL.md`](../THREAT_MODEL.md)
- [`../CRYPTO_DESIGN.md`](../CRYPTO_DESIGN.md)
- [`../MVP_DELIVERY_BASELINE.md`](../MVP_DELIVERY_BASELINE.md)
- [`../SIGNAL_MIGRATION_SPIKE.md`](../SIGNAL_MIGRATION_SPIKE.md)

### Documentation note

Current documents use more than one naming style for device states. `CRYPTO_DESIGN.md` contains names such as `UNVERIFIED`, `KEY_CHANGED`, and `PENDING_APPROVAL`, while newer project documentation describes `pending`, `verified`, `revoked`, and `key_changed`.

This decision records the required semantics, not an unverified exact enum spelling. Verify the current domain model before changing code, tests, migrations, or status terminology, then normalize the documentation.

### Revisit when

Reviewed per-device session fan-out, synchronized device transitions, and revocation semantics are implemented and tested.

---

## DEC-008 — Local storage protects selected content, not all database metadata

**Status:** ACCEPTED
**Recorded from existing repository evidence:** 2026-07-26

### Context

Room stores encrypted message packages rather than plaintext message bodies. Selected profile, contact-label, session, and experimental opaque record content is wrapped with a Keystore-backed local vault. Room itself is not described as a fully encrypted SQLCipher database.

### Decision

Protect sensitive persisted content through existing reviewed encryption boundaries while documenting remaining local metadata exposure honestly.

Backup and device transfer must not restore protected database/preference state in a way that can roll back nonce counters or resurrect invalid security state.

### Consequences

- Plaintext message bodies must not be added to Room.
- Schema shape, identifiers, routing AAD, lookup keys, and access patterns may remain visible locally.
- Keystore-backed encryption does not protect an unlocked compromised device.
- Room migrations involving counters, replay state, trust state, encrypted records, or identifiers are security-sensitive.
- Backup and restore changes require nonce, key, migration, and rollback review.
- Full-database-encryption claims are forbidden unless implementation changes and evidence support them.

### Evidence

- [`../CRYPTO_DESIGN.md`](../CRYPTO_DESIGN.md)
- [`../THREAT_MODEL.md`](../THREAT_MODEL.md)
- [`../KNOWN_LIMITATIONS.md`](../KNOWN_LIMITATIONS.md)
- [`../../README.md`](../../README.md)

### Revisit when

A reviewed full-database-encryption design, new backup model, or different persistent-security architecture is adopted.

---

## DEC-009 — HTTP relay access is debug-only; release cleartext is denied

**Status:** ACCEPTED
**Recorded from current repository configuration:** 2026-07-26

### Context

LAN HTTP is useful for controlled phone/emulator QA. It is not suitable for release or production use.

### Decision

Debug builds may permit an explicitly configured HTTP relay for development and QA with visible warnings.

Release configuration must:

- reject HTTP relay URLs;
- deny cleartext traffic;
- avoid depending on a local or historical debug endpoint.

### Consequences

- Debug and release network-security resources must remain separate.
- Public testing of a release variant requires a currently verified HTTPS endpoint.
- TLS improves transport protection to the relay but does not make the relay trusted or hide metadata from its operator.
- A historical HTTP-by-IP VPS endpoint must not be used as a release dependency without fresh verification and redesign.

### Evidence

- [`../../README.md`](../../README.md)
- [`../THREAT_MODEL.md`](../THREAT_MODEL.md)
- current Android manifest and network-security resources

### Revisit when

A production transport architecture is reviewed, deployed, monitored, and included in an operational-security model.

---

## DEC-010 — Signal migration must use a reviewed official integration, not a handwritten ratchet

**Status:** GATED
**Recorded from existing repository evidence:** 2026-07-26

### Context

The project needs stronger session security than the prototype. The repository contains a disabled official libsignal dependency/compile gate. A locally invented Double Ratchet would create unacceptable protocol and cryptographic risk.

### Decision

The intended migration direction is an established, reviewed Signal-style implementation using official library behavior where legally and technically acceptable.

A handwritten Double Ratchet is rejected as a shortcut.

Activation is blocked until the documented gates are complete:

- durable encrypted official protocol-store mappings;
- real prekey/PQXDH invitation semantics;
- identity-change and downgrade handling;
- reviewed per-device session and revocation behavior;
- Kotlin/Gradle/toolchain compatibility;
- JNI/ABI and release packaging;
- licensing and unsupported-external-use review;
- interoperability, restart, failure, migration, and rollback tests;
- independent protocol/application security review.

### Consequences

- `MIGRATION_ENABLED` remains false.
- In-memory protocol stores cannot enter the app runtime path.
- Prototype and future Signal formats require explicit version separation.
- Activation begins only with new test conversations behind a feature flag.
- Existing conversations must not be silently migrated.
- Distribution cannot proceed on unresolved licensing assumptions.

### Evidence

- [`../SIGNAL_MIGRATION_SPIKE.md`](../SIGNAL_MIGRATION_SPIKE.md)
- [`../CRYPTO_DESIGN.md`](../CRYPTO_DESIGN.md)
- [`../ROADMAP.md`](../ROADMAP.md)

### Revisit when

Every migration gate has current repository evidence and explicit approval.

---

## DEC-011 — SignalStore and invitation v2 remain disabled experimental boundaries

**Status:** ACCEPTED
**Recorded from existing repository evidence:** 2026-07-26

### Context

`core:signalstore` contains neutral interfaces and encrypted opaque-record storage intended to support future migration experiments. An experimental v2 invitation DTO exists with placeholder prekey/signature fields.

### Decision

The SignalStore spike remains a disabled storage and API boundary.

The experimental invitation v2 remains a test/design artifact and is not a valid active PQXDH invitation.

### Consequences

- `SIGNAL_BACKED_SESSIONS_ENABLED` remains false.
- Disabled adapters must fail closed.
- Normal messages are not sent, received, or migrated through the spike.
- Official record mapping and protocol transaction semantics are not claimed complete.
- Active v1 invitation handling must reject or remain isolated from experimental v2.
- Interfaces, DTOs, tables, and tests do not by themselves count as active Signal implementation.

### Evidence

- [`../SIGNAL_MIGRATION_SPIKE.md`](../SIGNAL_MIGRATION_SPIKE.md)
- [`../CRYPTO_DESIGN.md`](../CRYPTO_DESIGN.md)
- current SignalStore feature-flag source

### Revisit when

The gated migration work has a reviewed implementation plan and the user explicitly approves the next isolated activation stage.

---

## DEC-012 — `PARANOIA_C4_AEAD` remains disabled and cannot replace a session protocol

**Status:** SUPERSEDED by DEC-016 (2026-09-27) — `PARANOIA_C4_AEAD` was removed from
the codebase entirely, not merely kept disabled. This record is retained for history.
**Recorded from existing repository evidence:** 2026-07-26

### Context

Paranoia C4 is a proposed payload-encryption boundary involving multiple primitives. It is not an implemented secure suite and cannot provide identity verification, authenticated key agreement, ratcheting, device management, or metadata protection.

### Decision

`PARANOIA_C4_AEAD` remains disabled.

It must not become active or be described as secure without:

- precisely specified primitive modes and framing;
- domain-separated KDF and MAC design;
- reviewed nonce construction;
- selected maintained implementations;
- independent test vectors;
- differential and integration testing;
- side-channel review;
- external cryptographic audit.

It cannot replace a reviewed session protocol.

### Consequences

- Current `ParanoiaC4Aead` behavior must fail closed.
- No XOR, toy cascade, unauthenticated composition, shared direct cipher keys, fallback encryption, or partial implementation may enter runtime.
- C4 work, if resumed, must remain isolated from normal chats until independently approved.
- Release builds keep the C4 flag false.

### Evidence

- [`../CRYPTO_DESIGN.md`](../CRYPTO_DESIGN.md)
- [`../KNOWN_LIMITATIONS.md`](../KNOWN_LIMITATIONS.md)
- [`../ROADMAP.md`](../ROADMAP.md)

### Revisit when

All design, implementation, testing, and independent-review prerequisites are met.

---

## DEC-013 — Diagnostics use minimum necessary, sanitized technical data

**Status:** ACCEPTED
**Recorded from existing repository evidence:** 2026-07-26

### Context

Delivery and worker failures need enough observability for QA, but diagnostics can expose messages, keys, queue identifiers, timing, contacts, and protocol material.

### Decision

Diagnostics and copied reports contain only the minimum technical information needed for debugging.

Permitted categories include:

- high-level event names;
- enum/status values;
- timestamps;
- counters;
- retry metadata;
- short fingerprints where a full identifier is unnecessary.

They must exclude:

- plaintext;
- private, session, or shared keys;
- authentication tokens;
- QR secrets and raw pairing payloads;
- nonce values;
- ciphertext and raw envelopes;
- raw protocol records;
- full queue identifiers when a short fingerprint is enough.

### Consequences

- Diagnostic APIs should not accept secret material they do not need.
- Logs and copied reports must be safe to share.
- Debug-only diagnostic controls remain scoped to debug as intended.
- Diagnostic output is QA evidence, not cryptographic assurance or metadata protection.
- Clearing diagnostic history must not silently alter pairing or delivery state.

### Evidence

- [`../CRYPTO_DESIGN.md`](../CRYPTO_DESIGN.md)
- [`../THREAT_MODEL.md`](../THREAT_MODEL.md)
- [`../KNOWN_LIMITATIONS.md`](../KNOWN_LIMITATIONS.md)
- [`../../README.md`](../../README.md)

### Revisit when

Support, telemetry, crash reporting, remote diagnostics, or production observability is introduced.

---

## DEC-014 — Reset and deletion must not claim forensic secure erase

**Status:** ACCEPTED
**Recorded from existing repository evidence:** 2026-07-26

### Context

The debug reset can remove current application records and Keystore aliases, but Android flash storage and platform behavior cannot guarantee that historical blocks are forensically unrecoverable.

### Decision

Reset, local wipe, uninstall, Room deletion, and relay deletion must be described according to their actual scope.

No current control may claim guaranteed secure erase.

### Consequences

- Debug reset remains a disposable QA-state control.
- Retaining the configured relay URL during reset must be documented.
- Removal of current entries is distinct from forensic erasure.
- Tests must verify state transitions without asserting unsupported physical-media guarantees.
- Tester guidance must discourage real sensitive data.

### Evidence

- [`../THREAT_MODEL.md`](../THREAT_MODEL.md)
- [`../KNOWN_LIMITATIONS.md`](../KNOWN_LIMITATIONS.md)
- [`../../README.md`](../../README.md)

### Revisit when

A separately reviewed data-destruction and key-erasure model is implemented, with claims limited to what Android can actually guarantee.

---

## DEC-015 — Workflow documents have separate, non-overlapping roles

**Status:** ACCEPTED
**Recorded:** 2026-07-26

### Context

The original workflow documents duplicated project state, session history, next steps, and permanent rules. This made stale paths and outdated strategic instructions look authoritative.

### Decision

Use the workflow files as follows:

- `AGENTS.md` — durable repository rules and safety boundaries;
- `README.md` — index and reading guide;
- `CURRENT_PROJECT_STATE.md` — verified mutable factual snapshot;
- `DECISIONS.md` — durable accepted decisions and rationale;
- `TASK_QUEUE.md` — actionable future work;
- `HANDOFF.md` — short replaceable record of unfinished session work;
- `PROMPTS_FOR_CODEX.md` — reusable templates that do not override rules or evidence;
- checklists — task-specific verification controls.

Do not create `SESSION_NOTES.md` unless a concrete need for a separate experiment journal appears.

### Consequences

- Stable facts move out of `HANDOFF.md`.
- Task planning does not live in `CURRENT_PROJECT_STATE.md`.
- Decisions are not silently inferred from roadmap items.
- Git history remains the primary completed-work history.
- The workflow index must be updated when files are added, removed, renamed, or repurposed.

### Revisit when

The workflow becomes too large, conflicting, or insufficient for actual repository work.

---

## DEC-016 — `PARANOIA_C4_AEAD` removed entirely rather than kept disabled

**Status:** ACCEPTED
**Recorded:** 2026-09-27

### Context

DEC-012 kept `PARANOIA_C4_AEAD` as a disabled placeholder pending a hypothetical future
independent review of a home-grown Threefish/Serpent/AES/SHACAL-2 cascade. Starting the
crypto-core rewrite in `docs/ai-workflow/REWRITE_PLAN.md` (real Double Ratchet + PQXDH
via `libsignal`) forced the question of what, if anything, Paranoia C4 was still for.

### Decision

Remove `PARANOIA_C4_AEAD` from the codebase entirely: the `ParanoiaC4Aead` class, the
`ExperimentalSuiteDisabledException` it alone used, the `EncryptionMode.PARANOIA_C4_AEAD`
enum entry, the dead `ENABLE_PARANOIA_C4` `BuildConfig` field, the disabled option row
and warning text in the Settings screen, and the associated string resources. Do not
keep it as a disabled boundary "for later" — a home-grown cipher cascade does not add
provable security over a single reviewed AEAD suite, and this project's own committed
direction is a reviewed protocol (`libsignal` Double Ratchet/PQXDH), not additional
home-grown layers on top of it.

### Consequences

- No code path anywhere in the repository references `PARANOIA_C4_AEAD` after this
  decision. `EncryptionMode` now has exactly one value, `STANDARD_AEAD`.
- `EncryptionMode` was never persisted in Room and is not part of any wire format, QR
  invitation, or migration — removal required no schema migration.
- DEC-012 is superseded by this decision and retained only for history.
- Any future desire for a second/alternate payload cipher must go through the same
  independent-review bar as `STANDARD_AEAD` did — no revival of the cascade design.

### Evidence

- [`../CRYPTO_DESIGN.md`](../CRYPTO_DESIGN.md)
- [`../SECURITY_WARNINGS.md`](../SECURITY_WARNINGS.md)
- [`REWRITE_PLAN.md`](REWRITE_PLAN.md)

### Revisit when

Never, unless someone proposes a specific reviewed additional cipher with a concrete
threat-model justification independent of "more layers feels safer."

---

## DEC-017 — Keep and finish `:core:signal-protocol` and `:core:signalstore` rather than rebuild clean

**Status:** ACCEPTED
**Recorded:** 2026-09-27

### Context

Starting the crypto-core rewrite (`docs/ai-workflow/REWRITE_PLAN.md`) required deciding
whether `:core:signal-protocol` (the `OfficialSignalDoubleRatchetGate` boundary pinned to
`libsignal` `0.94.1`) and `:core:signalstore` (the neutral `Signal*StoreBoundary`
interfaces and `EncryptedSignal*RecordEntity` Room tables already at schema v5) should be
finished as the landing spot for real Double Ratchet/PQXDH sessions, or deleted and
rebuilt from a blank module.

### Decision

Keep both modules and finish them in place. They are already structured correctly for
this purpose: the dependency is pinned, the store interfaces match the shape `libsignal`
needs (identity/session/prekey/signed-prekey/Kyber-prekey records), and the Room schema
already reserves encrypted-at-rest tables for them. Phase 1 of `REWRITE_PLAN.md` turns
`OfficialSignalDoubleRatchetGate.MIGRATION_ENABLED` on behind a feature flag and
implements the `Signal*StoreBoundary` interfaces against real `libsignal` record types,
rather than starting a new module pair from scratch.

### Consequences

- No new Gradle modules are created for the Double Ratchet/PQXDH work; `:core:signal-protocol`
  and `:core:signalstore` stop being disabled placeholders and become live, in-progress code
  once Phase 1 starts.
- The existing Room v5 schema for `signal_*` tables is the basis for Phase 2's real migration,
  not a throwaway placeholder to delete.
- `DisabledSignalSessionManagerAdapter` and `OfficialSignalDoubleRatchetGate`'s current
  "throws while disabled" behavior stays in place until Phase 1 explicitly replaces it
  behind the feature flag described there — this decision does not itself enable anything.

### Evidence

- [`REWRITE_PLAN.md`](REWRITE_PLAN.md) Phase 1
- `core/signalstore/src/main/kotlin/app/securemessenger/signalstore/SignalStoreBoundaries.kt`
- `core/signal-protocol/src/main/java/app/securemessenger/signalprotocol/OfficialSignalDoubleRatchetGate.java`
- `core/storage/src/main/kotlin/app/securemessenger/storage/SecureDatabase.kt` (schema v5 `signal_*` tables)

### Revisit when

Phase 1 implementation reveals the existing interfaces don't actually fit real
`libsignal` store contracts and need a structural redesign rather than filling in.

---

## DEC-018 — libsignal store adapter semantics

**Status:** ACCEPTED
**Recorded:** 2026-09-27 (REWRITE_PLAN.md step 1d). Implementation verified 2026-09-29:
JVM tests (`EncryptedSignalProtocolStoreTest`, real PQXDH sessions) and storage instrumented
tests passed on a real build.

### Context

libsignal 0.94.1 calls its store interfaces synchronously, including from native code, and
leaves several policies to the store owner: identity trust, which Kyber pre-keys are
one-time versus last-resort, and how local identity is created.

### Decision

- **Blocking storage.** The neutral `Signal*StoreBoundary` interfaces and their per-record DAO
  methods are blocking, not `suspend`. Room's main-thread check is the guard against misuse.
  One protocol operation per remote address must run at a time; the caller (step 1e) owns
  that serialization.
- **Strict identity trust.** A remote identity is trusted only if none is stored or it matches
  the stored key, in **both** directions. libsignal's reference store accepts changed keys when
  receiving; this project rejects them, consistent with the fail-closed `KEY_CHANGED` policy.
- **Local identity is created once, explicitly.** `LocalSignalIdentityProvisioner` creates it
  through an atomic insert that never overwrites. Reading a missing identity throws rather than
  generating one.
- **Kyber pre-keys.** libsignal's `storeKyberPreKey` stores a *one-time* key (deleted on use);
  a last-resort key must be stored explicitly. The last-resort flag is bound into the record's
  vault AAD, so changing it in the database makes the record undecryptable. One-time use is a
  single atomic `DELETE`; last-resort reuse detection is a single atomic `INSERT ... IGNORE` of
  `(kyberPreKeyId, signedPreKeyId, SHA-256(domain || baseKey))`. Unknown keys count as reuse.
- **Buffers holding private material** are wiped after libsignal parses them; libsignal copies
  them into native state (verified in the v0.94.1 constructors).

### Consequences

- Room schema v6 (`MIGRATION_5_6`) adds the local identity and base-key tables and the Kyber
  flag. It deletes pre-existing Kyber spike rows, whose AAD format changed.
- `wipeLocalData` also clears the local Signal identity and base-key tracking.
- A contact whose key changed cannot even deliver an incoming message until the app-level
  re-verification flow (later step) explicitly replaces the stored identity.

### Evidence

- `core/signal-protocol/src/main/java/app/securemessenger/signalprotocol/EncryptedSignalProtocolStore.kt`
- `core/storage/src/main/kotlin/app/securemessenger/storage/EncryptedSignalStoreRepository.kt`
- Tests: `EncryptedSignalProtocolStoreTest` (JVM, real PQXDH sessions), `SecureRoomStateInstrumentedTest`,
  `SecureDatabaseMigrationInstrumentedTest` — **none have been run yet** (no toolchain).

### Revisit when

The step 1e pipeline integration or an independent review shows a store contract was
misread, or when multi-device (Phase 4) needs per-device identity handling.

---

## Explicitly rejected shortcuts

The following are not accepted project decisions:

- enabling disabled Signal-backed sessions because interfaces or tests exist;
- treating experimental invitation v2 as real PQXDH;
- using in-memory Signal stores in the application runtime;
- implementing a handwritten Double Ratchet as a shortcut;
- reviving Paranoia C4 or any home-grown cipher cascade in any form (removed entirely, see DEC-016 — not merely "disabled without audit");
- adding plaintext fallback for compatibility or availability;
- trusting relay acceptance as receiver delivery;
- treating `Delivered` as a read receipt;
- silently sending to pending, revoked, key-changed, missing, or ambiguous device state;
- silently fanning out to secondary devices;
- claiming synchronized revocation when only local blocking exists;
- claiming full database encryption because message bodies are ciphertext;
- claiming secure erase from reset or deletion;
- treating historical VPS or test results as current verification;
- treating lost local commits as current repository implementation;
- publishing the current project as production-ready.

## Deferred directions that are not current decisions to implement

**Updated 2026-09-27:** reviewed Signal/PQXDH plus Double Ratchet activation and
cryptographic multi-device fan-out are **no longer merely deferred** — they are the
committed direction of `docs/ai-workflow/REWRITE_PLAN.md` Phases 1 and 4. They are
kept in this list below only to record that they were once undecided; treat the
rewrite plan as authoritative on this point. Paranoia C4 is removed below (it was
deleted from the codebase, not deferred — see DEC-016).

Remaining roadmap topics that are still genuinely deferred, not active commitments:

- post-quantum ML-KEM hybrid handshake beyond what PQXDH itself provides;
- MLS-based groups;
- sealed sender;
- padding and batching;
- opaque or single-use queues;
- relay chaining;
- Tor, P2P, or local transports;
- attachments and media;
- app lock and biometric unlock;
- encrypted backup;
- secure data-destruction design;
- reproducible builds, SBOM, and full incident-response process.

Each requires a separate scoped decision before implementation begins.

## Open documentation consistency issue

The exact device-state identifier vocabulary is inconsistent across current documents.

Before editing device-state code, tests, Room migrations, or public documentation:

1. inspect the current domain model and persisted values;
2. identify the actual runtime and storage identifiers;
3. choose one canonical documentation vocabulary;
4. document compatibility and migration impact before renaming any persisted value;
5. update all affected documents and tests together.

This issue does not change DEC-007's fail-closed semantics.
