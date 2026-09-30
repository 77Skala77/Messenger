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
| DEC-003 | Normal chats remain on the prototype session path until reviewed migration gates are met | SUPERSEDED by DEC-021 |
| DEC-004 | `STANDARD_AEAD` is the only active payload-encryption suite | SUPERSEDED by DEC-021 |
| DEC-005 | The relay is an untrusted opaque transport | ACCEPTED |
| DEC-006 | Message acceptance and delivery state require durable authenticated client evidence | ACCEPTED |
| DEC-007 | Device delivery fails closed and targets one usable primary device | ACCEPTED |
| DEC-008 | Local storage protects selected content, not all database metadata | ACCEPTED |
| DEC-009 | HTTP relay access is debug-only; release cleartext is denied | ACCEPTED |
| DEC-010 | Signal migration must use a reviewed official integration, not a handwritten ratchet | IMPLEMENTED (debug); release GATED |
| DEC-011 | SignalStore and invitation v2 remain disabled experimental boundaries | SUPERSEDED by DEC-019, DEC-021 |
| DEC-012 | `PARANOIA_C4_AEAD` remains disabled and cannot replace a session protocol | SUPERSEDED by DEC-016 |
| DEC-013 | Diagnostics use minimum necessary, sanitized technical data | ACCEPTED |
| DEC-014 | Reset and deletion must not claim forensic secure erase | ACCEPTED |
| DEC-015 | Workflow documents have separate, non-overlapping roles | ACCEPTED |
| DEC-016 | `PARANOIA_C4_AEAD` removed entirely rather than kept disabled | ACCEPTED |
| DEC-017 | Keep and finish `:core:signal-protocol`/`:core:signalstore` rather than rebuild clean | ACCEPTED |
| DEC-018 | libsignal store adapter semantics: blocking, strict identity trust, fail-closed Kyber handling | ACCEPTED |
| DEC-019 | Step 1e design: relayed committed invitation, one-sided pairing with explicit request, flag-gated enablement | ACCEPTED |
| DEC-020 | Relay-supplied message IDs are never trusted as local keys or cross-contact receipt targets | ACCEPTED |
| DEC-021 | Step 1f: automatic explicit legacy reset, legacy code and modules removed, v2 only in debug, release without chat until Phase 5 | ACCEPTED |
| DEC-022 | TQ-009: stay on AGP 8.13.2 and compileSdk 36; Kotlin and libsignal pinned | ACCEPTED |
| DEC-023 | Phase 2: vault key never replaced silently; a lost key fails closed; the user decides | ACCEPTED |
| DEC-024 | Phase 3: self-certifying relay queues, server message IDs, invitation v3, reset of v2 test chats | ACCEPTED |
| DEC-025 | Phase 4 (multi-device) deferred; version 1 is one device per person | ACCEPTED |

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

**Status:** SUPERSEDED by DEC-021 (2026-09-30) — the prototype path was removed; debug builds use v2 only, release has no chat until Phase 5.
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

**Status:** SUPERSEDED by DEC-021 (2026-09-30) — `STANDARD_AEAD` was removed with the prototype; message encryption is libsignal's.
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

**Status:** ACCEPTED; the legacy primary-device planner was removed with the prototype (DEC-021). v2 applies the same fail-closed rule to a single device per contact (see `../PROTOCOL_V2.md`).
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

**Status:** IMPLEMENTED for debug builds (DEC-019, DEC-021, 2026-09-30) — official libsignal, no handwritten ratchet; release remains GATED on the Phase 5 independent review.
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

**Status:** SUPERSEDED by DEC-019 and DEC-021 (2026-09-30) — the store is the active v2 store in debug builds; the experimental invitation DTO was deleted and replaced by invitation v2.
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

## DEC-019 — Step 1e design: relayed committed invitation, one-sided pairing, flag-gated enablement

**Status:** ACCEPTED
**Recorded:** 2026-09-29. Owner approvals given in chat on 2026-09-29 (items O1–O4). Items
T1–T8 were proposed by the assistant with the approvals and not objected to. Nothing here is
implemented yet; implementation status is tracked in `REWRITE_PLAN.md` and `PHASE_1E_PREP.md`.

### Context

Step 1e wires libsignal PQXDH + Double Ratchet into invitations and delivery. `AGENTS.md`
requires explicit owner approval, feature-flag isolation, tests, migration and threat review,
and independent review before experimental sessions serve normal chats.

Measured with libsignal 0.94.1 on 2026-09-29 (scratch program, real library): `KEMKeyType`
offers only `KYBER_1024`; its serialized public key is 1569 bytes; a complete bundle encoded as
invitation text is about 2574 characters (v1 is about 250), which needs a QR code near the
format's maximum size at the lowest error-correction level; a first (PreKey) message carrying
two bytes of text is 1759 bytes. Also observed: `SessionBuilder` takes `(remote, local)`
addresses but `SessionCipher` takes `(local, remote)` — the wrapper must hide this.

### Owner decisions

- **O1 Invitation transport.** The QR code is small and carries a commitment; the full bundle
  travels through the untrusted relay and is accepted only if it matches the commitment. The
  relay API is unchanged.
- **O2 Pairing.** One side scans. The scanned side receives a contact **request** that a person
  must explicitly accept. This is trust on first use for the accepting side (weaker than mutual
  scanning, which was offered and not chosen). Unaccepted requests never become contacts,
  never get receipts, and cannot receive messages.
- **O3 Enablement.** New sessions sit behind a feature flag that is OFF by default. It may be
  turned ON only in debug builds, only after the step 1f gate passes (these are isolated test
  conversations in the sense of TQ-105). Release builds keep it OFF until the independent
  review of Phase 5. Legacy prototype code coexists temporarily; it is deleted in 1f together
  with the explicit legacy reset, and a conversation never runs on both protocols.
  Accepted consequence: the libsignal identity private key is a software key, encrypted at rest
  with a Keystore key and present in app memory while in use (as in Signal-Android). The legacy
  P-256 identity key was hardware-backed and non-exportable.
- **O4 Legacy vulnerability.** The relay-supplied message ID issue is patched in the legacy path
  now (DEC-020) and closed by design in v2 (T4).
- **O3 amendment (owner, 2026-09-30).** To build and verify the UI (sub-step 6b) and to pass the
  step 1f gate, v2 may be enabled **before** 1f only through a hidden QA switch in the existing
  debug QA tools: off by default, present only in debug builds (release cannot enable it), and
  turned on explicitly by the owner on test phones. Manual two-device tests use the S24 Ultra
  and a second Android phone. Default enablement in debug still waits for the 1f gate.

### Technical decisions

- **T1 Message bodies at rest** are encrypted with the Keystore-backed vault (AES-GCM), with the
  message ID, contact ID, and direction bound into the AAD. A ratchet deletes message keys, so
  stored protocol ciphertext cannot be decrypted again. This is not weaker than the legacy
  design, whose stored session root key already decrypts every stored message.
- **T2 Atomicity.** Decrypting advances the ratchet. Session-record updates, the stored message,
  and an envelope-digest marker commit in one Room transaction, under one process-wide protocol
  lock shared by the UI and the WorkManager worker. The relay copy is deleted only after the
  commit. A byte-identical re-delivery is recognised by its digest and deleted without
  decryption. Encrypt-and-enqueue is atomic in the same way, so a sending chain position is
  never reused.
- **T3 Envelope v2.** A distinct magic and version; v1 and v2 envelopes are never accepted in
  place of each other. Each envelope carries a random 16-byte routing tag assigned by the
  recipient (per contact), instead of any sender identifier, so no trial decryption is needed.
- **T4 Message IDs.** The sender's message ID travels inside the encrypted payload. Local
  storage IDs are generated locally. A receipt names an inner ID and only updates a message
  that was sent to the contact whose session authenticated the receipt.
- **T5 Pre-keys.** Every invitation has its own signed pre-key, one-time EC pre-key, and
  one-time Kyber pre-key, and no last-resort key, so one QR code yields at most one request and
  invitations share no key state (no rotation logic). The one-time keys are consumed by the
  first session; the whole set is deleted when the invitation expires unused, and the signed
  pre-key of a used invitation after a grace period. (Refined during sub-step 2; the first
  draft of this decision shared one rotated signed pre-key.)
- **T6 Addressing.** libsignal address = (local contact ID, device 1) until Phase 4; address
  names are local-only (verified with libsignal 0.94.1: each side may name the other
  differently). v2 state lives in separate `v2_*` tables (Room v7), so legacy rows are never
  reinterpreted as v2 rows. (Refined during sub-step 5; the first draft added a protocol-version
  column to the legacy contact table instead.)
- **T7 Identity change** stays strict (DEC-018) and fails closed. Refined during sub-step 6: on
  **receive**, libsignal checks the identity a PreKey message *claims* before authenticating
  anything, so an untrusted-identity result is attacker-controlled (the relay sees every routing
  tag). It is treated as an unauthenticated message and **never** changes contact state;
  otherwise a relay could block any contact with one forged envelope. A genuinely new identity
  cannot reach an existing conversation anyway (it lacks the routing tags) and must re-pair.
- **T9 Placement (no new module).** Neutral v2 persistence and transaction boundaries live in
  `:core:signalstore`; their Room implementation in `:core:storage` (which stays libsignal-free);
  the pipeline (`ConversationV2Service`) in `:core:signal-protocol`, which now also depends on
  `:core:domain` for `TransportClient`; UI wiring in `:core:ui` behind the flag.
- **T8 Bundle confidentiality.** The relayed bundle is encrypted with a random single-use
  AES-256-GCM key taken from the QR code, and the QR commits to the exact relayed bytes with a
  domain-separated SHA-256 digest. The relay learns nothing from the bundle; someone who sees
  the QR can read the (public) bundle but cannot substitute it.

### Pairing flow

1. Alice shows an invitation: a fresh one-time EC and Kyber pre-key, a routing tag, a random
   mailbox ID `M` and key `K`. She uploads `E = AES-GCM_K(bundle)` to relay queue `M`; the QR
   holds `smp-invite-v2:` + (version, `M`, `K`, digest of `E`).
2. Bob scans, fetches `M`, keeps only a blob matching the digest, decrypts it, validates the
   bundle strictly, and builds a session (libsignal verifies the pre-key signatures). Alice
   becomes Bob's contact with the identity key from the QR-committed bundle. Bob sends a HELLO
   PreKey message with his receive queue and his routing tag for Alice. Bob waits for acceptance.
3. Alice decrypts the HELLO atomically into a pending request (not a contact). No receipts.
4. Alice accepts: the contact is created with the existing warning-only first-use states
   (`UNVERIFIED` / `PRIMARY_UNVERIFIED`) and an ACCEPT message is sent. Declining deletes the
   request, its session, and its identity record.
5. Bob receives ACCEPT; both sides are ready. Unused invitations expire (relay TTL and local
   deletion of their pre-keys).

### Threat review (relay untrusted; summary)

| Attack | Outcome |
| --- | --- |
| Substitute the bundle | Digest mismatch; rejected. |
| Withhold bundle, HELLO, or ACCEPT | Pairing stalls (denial of service only); show a new QR. |
| Replay HELLO or any message | One-time pre-key already consumed / libsignal duplicate check / envelope digest; no second acceptance. |
| QR photographed by a third party | They can read the bundle and send one HELLO first. Alice sees a request she must not accept blindly (safety number); Bob's HELLO then fails and he is told the invitation was used. At most one request per QR. |
| Relabel or reuse message IDs | No effect: inner authenticated IDs, local storage IDs, contact-scoped receipts. |
| Downgrade v2 → v1, cross-protocol confusion | Distinct envelope magic and per-contact protocol version; mismatches are rejected. |
| Crash mid-decrypt, UI and worker concurrency | Transaction rollback, retained relay copy, process-wide lock. |
| Metadata | Relay still sees queue IDs, routing tags (pseudonymous, per conversation), sizes (PreKey messages are ~1.7 KB, revealing a new session), timing, and the initiator's identity key in the PreKey header. Accepted; comparable to Signal's server. |

### Residual risks

- Trust on first use on the accepting side (owner-accepted, O2).
- The identity private key is in app memory while in use (O3).
- No independent review yet; enabling outside debug builds stays blocked (Phase 5).

### Revisit when

Implementation shows a libsignal contract differs from the above, QR scanning of the v2 code
fails in practice, or Phase 3 relay authentication or Phase 4 multi-device changes routing.

---

## DEC-021 — Step 1f: explicit legacy reset and removal; v2 only in debug until Phase 5

**Status:** ACCEPTED
**Recorded:** 2026-09-30. Owner decisions given in chat on 2026-09-30 (all as recommended).

### Decision

- **Release builds have no chat until the independent review of Phase 5.** The legacy
  prototype is removed entirely; v2 may not serve release builds before independent review
  (`AGENTS.md`), so a release build shows a "not for messaging" screen. Release is not
  distributed (Phase 7 is out of scope).
- **Debug builds use v2 only, on by default,** once the step 1f proofs pass (DEC-019 O3). They
  remain isolated test conversations in the sense of TQ-105.
- **Automatic explicit reset with a one-time notice.** On the first start of the new version,
  legacy contacts, devices, sessions, messages, replay markers, the legacy Keystore P-256
  identity, nonce counters, and the legacy queue ID are deleted on purpose, once. A legacy
  session can never be read, migrated, or accepted as a v2 session. No real users exist; all
  data is test data.
- **v2 background sync (WorkManager) is part of 1f,** replacing the legacy worker.
- **Legacy code is deleted, including the then-empty `:core:crypto` and `:core:delivery`
  modules and the spikes** (`OfficialSignalDoubleRatchetGate`, the experimental v2 invitation
  DTO, `DisabledSignalSessionManagerAdapter`). `MIGRATION_ENABLED` and
  `SIGNAL_BACKED_SESSIONS_ENABLED` are replaced by one explicit build-type switch. Git history
  keeps the removed code.

### Order

1f-A proofs first (no behavior change): forward secrecy, post-compromise security and their
honest boundary (skipped message keys), on in-memory and Room-backed stores, JVM and device;
a Room-backed two-device integration test; a minified debug-signed build checked by hand on two
phones. Then 1f-B: v2 background sync, the reset (Room v8), legacy removal, flags, docs.

---

## DEC-022 — TQ-009: stay on AGP 8.x (8.13.2) and compileSdk 36; Kotlin and libsignal stay pinned

**Status:** ACCEPTED
**Recorded:** 2026-09-30 (TQ-009, made by the implementing session within the task's
"explicitly decide whether to adopt AGP 9.x or stay on 8.x" requirement; the owner can reverse it).

### Decision

- **AGP 8.13.2 with Gradle 8.14.3; compileSdk 36; targetSdk stays 34; minSdk 31.** AGP 9.x is not
  adopted: Kotlin 2.2.20 (pinned by libsignal, `LIBSIGNAL_TOOLCHAIN_REVIEW.md`) documents support
  only up to AGP 8.11.1, and AGP 9 also changes the Kotlin/KAPT plugin model. Revisit together
  with a Kotlin upgrade, separately from protocol work.
- **Library versions are bounded by compileSdk 36.** Compose 1.12 (BOM 2026.09.00),
  `androidx.core` 1.19 and lifecycle 2.11 require compileSdk 37, which AGP 8.13 does not support.
  Used instead: Compose BOM 2025.10.01, core-ktx 1.17.0, lifecycle 2.10.0. Newer ones wait for an
  AGP that supports compileSdk 37.
- **Why not AGP 8.11.1:** its lint crashes (`Provided Metadata instance has version 2.1.0, while
  maximum supported version is 2.0.0`) in the Compose lint checks on Kotlin 2.2 classes. Compose
  BOM 2025.10 ships lint checks that read the newer metadata, which removed the crash without
  disabling any check.
- **Not changed:** Kotlin 2.2.20, coroutines 1.10.2 (both libsignal's versions), libsignal
  0.94.1, KAPT (KSP later), targetSdk.
- No crypto, protocol, wire-format, storage-schema (`8.json` unchanged) or relay change.

---

## DEC-023 — Phase 2: the vault key is never replaced silently; a lost key fails closed and the user decides

**Status:** ACCEPTED
**Recorded:** 2026-09-30. Owner decision in chat: block and explain, offer "check again" and a
confirmed "delete local data and start over"; no automatic deletion.

### Context

Before Phase 2, `AndroidKeystoreVaultCipher` created a new Keystore key on any encrypt or decrypt
when its alias was missing. After a key loss (Keystore reset, OEM fault, app data restored without
the non-exportable key) old rows would fail with an opaque error while new rows were written under
the new key: data split across two keys, and the user never told.

### Decision

- **One place creates the vault key:** a new profile on an empty vault
  (`LocalProfileRepository.createProfile` → `createKeyIfAbsent`). Encrypt and decrypt never create
  a key; a missing, unrecoverable, or permanently invalidated key raises
  `VaultKeyUnavailableException`. `createProfile` refuses to replace an existing profile.
- **The profile row is the vault's canary:** written first with the key, deleted last; nothing
  else is stored without it. `localDataState()` decrypts it: `EMPTY`, `READABLE`, or `UNREADABLE`
  (key missing or invalidated, a key that is not the one the data was written with, or a damaged
  canary). Any other failure (Keystore not answering) is treated as temporary, never as loss.
- **Unreadable data blocks the app:** only a screen that explains the loss, with "Check again" and
  a confirmed "Delete local data and start over". No chat, no background sync (the worker returns
  failure, not retry), no key creation, no automatic deletion: a failure that only looked final
  must not cost the user their data. For a temporary failure: "Check again" only.
- **The wipe deletes the key first,** then every row in one transaction under the protocol lock; it
  needs no key. If deleting the key fails, nothing is deleted; once the key is gone, leftover rows
  and flash remnants can no longer be decrypted.
- **A storage failure is never a protocol verdict.** A failure inside libsignal's store callbacks
  propagates as an error: the transaction rolls back, the envelope stays on the relay, no contact
  state changes. Damaged rows fail closed (bad tag or malformed blob) and are never read as "not
  stored", which for identity records would mean trusting a new identity.
- **Every vault failure is unchecked** (`VaultException`: `VaultKeyUnavailableException`,
  `VaultRecordRejectedException`, `VaultTemporarilyUnavailableException`). Found while testing:
  libsignal 0.94.1 passes unchecked exceptions from store callbacks back unchanged, but wraps a
  checked one in an `AssertionError` (and logs it through its own logger), which `catch
  (Exception)` blocks miss and which would have crashed the chat's polling loop. A stored libsignal
  record that authenticates but does not parse raises the unchecked
  `StoredRecordCorruptedException` for the same reason. A JVM test pins the libsignal behavior.

### Consequences

- A lost key ends the conversations stored on that phone; the user re-pairs and contacts see a
  new safety number. The screen says so.
- Key parameters are unchanged (AES-256-GCM, no user-authentication binding, so biometric and
  lock-screen changes do not invalidate it). StrongBox and unlocked-device-only keys are not
  adopted: background sync runs while the phone is locked, and not every test phone has
  StrongBox. Revisit in Phase 5.
- Cloud backup and device transfer stay excluded in the manifest, so a database restored without
  its key is not an expected path; if it happens, it is detected and handled as above.

### Evidence (2026-09-30)

`VaultKeyLossInstrumentedTest` (real Keystore), `LocalKeyLossInstrumentedTest` (pipeline, worker,
and the screen on a device), `ConversationV2ServiceTest` (key loss inside libsignal callbacks),
`LocalVaultCipherTest` (damaged blobs), the 5→8 migration test, the secrets-at-rest test in
`ConversationV2RoomProofInstrumentedTest`, and `SecurityLoggingPolicyTest`. Results:
`REWRITE_PLAN.md`, Phase 2.

---

## DEC-024 — Phase 3: self-certifying relay queues, server message IDs, invitation v3

**Status:** ACCEPTED
**Recorded:** 2026-10-01. Owner decisions in chat: explicit reset of v2 test conversations with a
notice; one QR secret with HKDF-derived keys; relay maximum TTL stays 7 days.

### Decision

Design: `../PHASE3_RELAY_DESIGN.md`.

- Queue ID = `q3-` + base64url(SHA-256("smp-relay-queue-v1\0" || R)) for a 32-byte read secret R.
  Anyone may send to a queue; reading and deleting need `Authorization: Bearer R`. The relay keeps
  no token table. Contacts learn only the queue ID.
- The relay assigns message IDs; deletion is bound to the queue; a client-supplied message ID is
  refused.
- QA-level limits: body ≤ 64 KB, per-IP rate limits, ≤ 1,000 pending envelopes per queue. Relay
  maximum TTL stays 7 days (owner choice; the app sends 24 hours).
- Invitation `smp-invite-v3:` = version + 32-byte secret S + 32-byte sealed-bundle digest. The seal
  key and the mailbox read secret are derived from S with HKDF-SHA256 (distinct `info` labels).
  `smp-invite-v2:` codes are refused as an unsupported version.
- The own queue's read secret is stored vault-encrypted, never in plain preferences.
- Migration: v2 test conversations (contacts, requests, messages, invitations, their libsignal
  sessions, identities and pre-keys) are deleted once, with a one-time notice; the profile, the
  local libsignal identity and the vault key stay. Users pair again.

### Consequences

Closes: contacts reading or deleting my queue (P1), cross-queue deletion (P2), message-ID
overwrite (P3), trivial flooding (P4). No backward compatibility on the wire: old apps and the new
relay do not interoperate (test data only). A leaked read secret exposes pending ciphertext and
lets it be deleted, never decrypted.

---

## DEC-025 — Phase 4 (multi-device) deferred; version 1 is one device per person

**Status:** ACCEPTED
**Recorded:** 2026-10-01. Owner decision in chat after the draft design
(`../PHASE4_MULTIDEVICE_DESIGN.md`) and a plain-language explanation of the trade-off.

### Decision

- Phase 4 is **deferred**. The first version supports one device per person (as today; comparable
  to Telegram's secret chats). The plan continues with Phase 5 (security test pass), then Phase 6
  (Telegram-style UI).
- Answers already given, to be used when the phase resumes: own identity key per device with a
  device list signed by the primary device (option A); a contact's new device gets nothing until
  the user confirms it; "Delivered" once at least one of the contact's devices confirmed.

### Consequences

- Single device per contact stays a documented limitation (`KNOWN_LIMITATIONS.md`,
  `PROTOCOL_V2.md`). No revocation protocol is needed while only one device exists.
- Adding multi-device later changes the protocol again and needs its own security review; the
  Phase 5 review does not cover it.

---

## DEC-020 — Relay-supplied message IDs are never trusted as local keys or cross-contact receipt targets

**Status:** ACCEPTED (legacy patch). Moot since 2026-09-30: the legacy path it patched was removed (DEC-021); v2 closes the issue by design (DEC-019 T4).
**Recorded:** 2026-09-29 (found while designing step 1e; owner chose to patch the legacy path)

### Context

The relay returns each blob with a `messageId` that it controls. The legacy pipeline stored an
incoming message under that ID with `REPLACE` semantics and echoed it in the delivery receipt,
and the sender updated any outgoing message with that ID regardless of contact. A malicious
relay could therefore overwrite an existing local message on the receiver, or make a dropped
message to one contact show `Delivered` by relabelling a message delivered to another contact.
Both violate DEC-005 and DEC-006.

### Decision

- Incoming messages are stored under a locally generated ID, never the relay-supplied one.
- A delivery receipt only updates an outgoing message sent to the same contact whose session
  authenticated the receipt.

### Consequences

- Residual legacy risk: the legacy wire format has no authenticated sender message ID, so the
  receipt still echoes the relay-visible ID. A relay can still swap IDs among messages to the
  **same** contact (mark one dropped message delivered using another message's receipt). v2
  closes this (DEC-019 T4); the legacy path is removed in 1f.
- No schema or wire-format change; existing rows keep their IDs.

### Evidence

- `DeliveryPipelineTest` — incoming message stored under a local ID.
- `SecureRoomStateInstrumentedTest.deliveryReceiptOnlyMarksMessagesSentToTheSameContact`.

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
