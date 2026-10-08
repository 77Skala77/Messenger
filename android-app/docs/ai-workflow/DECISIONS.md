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
| DEC-026 | Phase 5: rejected envelopes deleted and rolled back, relay pages, ordered sending, strict invitations; paid audit before release | ACCEPTED |
| DEC-027 | Phase 6: AGPL-3.0 `LICENSE`; reuse of Telegram code ("GPL v2 or later" files, headerless ones at the owner's accepted risk); unread counters (Room v10); notifications without text; classic palette | ACCEPTED |
| DEC-028 | AI security audits of October 2026: triage, fix block TQ-110, safety-number check in 6f; not the independent audit | ACCEPTED |
| DEC-029 | Room v10: unread counters from a per-message "seen" mark (not a contact timestamp), set only for what is on screen; verified-date column for 6f | ACCEPTED (supersedes the column choice of DEC-027) |
| DEC-030 | Safety-number comparison step and local verified mark (audit H1): optional but explicit when accepting, offered to the scanner at once, bound to the exact number shown | ACCEPTED |
| DEC-031 | Notifications without message text: who and how many, never what; requests never by their claimed name; locked screen "New message" only; posted only by the background sync while the app is not on screen | ACCEPTED |
| DEC-033 | Telegram's look and motion taken over directly (owner, 2026-10-06): only the logo and the name stay out; code translated to Compose, not embedded; Source Sans 3; sync every 30 s and message order by sender time planned | ACCEPTED (widens DEC-027) |
| DEC-032 | TQ-108: a lost inbox secret blocks with its own explanation; screen security a switch (off), invitation screens not protected; used invitations withdrawn from the relay; envelope digests kept 30 days | ACCEPTED |
| DEC-034 | Message order by the sender's authenticated time (within one minute before arrival, never before the contact's previous text); background connection every 30 s through a foreground service (on by default, switch in settings, battery exemption asked) | ACCEPTED (owner, 2026-10-06) |
| DEC-035 | Chat management and replies: pin, mute (for a time or for good), mark read, clear history, delete chat with its contact and keys (owner's choice); swipe and selection like Telegram; replies as inner payload type 5; Room v11 (three nullable columns) | ACCEPTED (owner, 2026-10-06) |
| DEC-036 | Voice messages (no calls): recorded into memory as AAC, at most 15 minutes; the recording travels as AES-256-GCM pieces in a one-time relay queue, the note with key and secret as inner payload type 6; "Delivered" only once the recording itself arrived; pause and listen before sending; one-time recordings with a "played" message back (type 7, like Telegram); Room v12 | ACCEPTED (owner, 2026-10-06) |
| DEC-037 | Deleting single messages, on this phone or for both (inner payload type 8, like Telegram), with the bubble falling apart into dust; the chat screen in Telegram 12's glass look, edge to edge | ACCEPTED (owner, 2026-10-06) |
| DEC-038 | Telegram's own looks: the dark theme becomes Telegram's default "Dark Blue" (blue-to-violet outgoing bubbles), both themes get Telegram's default wallpaper (its gradient and doodle pattern), the settings take Telegram 12's layout, the recording controls Telegram's animations and tooltips; turning the phone keeps the open screen | ACCEPTED (owner, 2026-10-07) |
| DEC-039 | "Played" for every voice message (Telegram's dot until the recipient presses play; type 7 widened, Room v13), seek and trim of voice messages, finer dust with a snap, the glass rim and the "down" button fixed, Telegram 12's main screen with bottom tabs | ACCEPTED (owner, 2026-10-07) |

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

## DEC-026 — Phase 5: rejected envelopes are deleted, relay pages, ordered sending, strict invitations

**Status:** ACCEPTED
**Recorded:** 2026-10-01. Owner decisions in chat after the Phase 5 findings
(`../PHASE5_SECURITY_TEST_PLAN.md`): delete junk and page the relay (recommended option), fix all
findings in this phase, a paid external audit before any release, and a second AI review pass with
a fresh context now.

### Context

The Phase 5 fuzz and attack tests held for libsignal and every decoder, but found weaknesses around
them: anyone who knows a queue ID could stall or crash a receiver with junk (F1, F2, F5); a text
that overtook its HELLO burned the invitation and broke pairing (F3); the key-loss screen cut off
its only way out in landscape (F12); and smaller issues (F4, F6–F11).

### Decision

- **Every envelope that reaches a verdict is deleted from the relay**, accepted or rejected
  (malformed, unknown routing tag, failed authentication, not a valid HELLO). Only a local failure
  (storage, vault) reaches no verdict and keeps the envelope. Rejected envelopes commit nothing: the
  protocol transaction is rolled back. This replaces "unauthenticated envelopes stay on the relay".
- **Relay (still QA-level):** stores only canonical standard base64; a read returns at most the
  100 oldest messages; the reader deletes what it decided and fetches again (up to 10 pages per
  sync).
- **App transport:** a response may be at most 8 MB, 200 messages, and 30 s of reading; each item
  is parsed on its own and an unreadable item is skipped; an invalid answer is a relay error.
- **Ordered sending:** a contact's envelopes leave in the order they were made; one that failed
  holds back the later ones until it is sent (a text never overtakes its HELLO).
- **Invitations:** only a HELLO that uses exactly its invitation's pre-keys (EC one-time, signed,
  and Kyber) opens it; anything else is rejected and rolled back, so the invitation stays usable.
  Once used, all of its private pre-keys are deleted (the signed one too). Only the canonical QR
  text is accepted.
- Local damage gets local-fault types: `StoredRecordCorruptedException` for libsignal's
  `InvalidSessionException`, `VaultRecordRejectedException` for a damaged stored queue secret.
- The key-loss screen (DEC-023) and onboarding scroll, so their buttons stay reachable in
  landscape or with a large font (F12, found by the device run).
- The external human audit is required before v2 may be enabled in a release build (unchanged
  `AGENTS.md` rule); it is planned before release, not now.
- After the second (fresh-context AI) review, the same day: key IDs above 2^31 from a PreKey
  message are unknown keys, never storage errors (R1, High); an envelope that reaches no verdict no
  longer stops the rest of the queue, sending still runs, and the failure is reported afterwards;
  on a contact or request route only PreKey messages naming the pairing's own invitation pre-keys
  reach libsignal, and the scanner's side accepts none (R2); an expired invitation is unroutable
  even before cleanup (R3); send order is insertion order, not timestamps (R4); the copied
  invitation code is marked sensitive (R7); display names are checked per code point (I3); every
  database connection runs `synchronous` FULL and `secure_delete` ON (R5, I6: both phones ran WAL
  with NORMAL, one ran `secure_delete` FAST). Accepted for later with reasons: R6, `FLAG_SECURE`,
  I2, I5 (TQ-108) and I4.
- Invitation code and the clipboard (owner, after the hand check showed the Samsung keyboard keeping
  the copied code in its history despite the sensitive flag): add "Share invitation" (no
  clipboard) and keep "Copy invitation code" with an on-screen warning. This is a deliberate,
  narrow exception to the `AGENTS.md` clipboard rule, recorded there.

### Consequences

- No wire, invitation, or Room schema change; the relay API keeps its routes and JSON, with a page
  limit and a stricter upload check. Existing installs need nothing. Used invitations from before
  this change keep their signed pre-key until local data is deleted.
- A relay that reorders a text before its HELLO now causes that text to be rejected (lost), never
  the invitation; a relay can always drop messages anyway.
- Residual: anyone who knows a queue ID can still send junk faster than the receiver deletes it,
  within the relay's per-IP limits; a production relay needs real abuse control (Phase 7).

### Evidence

`V2DecoderFuzzTest`, `V2AttackMatrixTest`, `ConversationV2ServiceTest`, `LocalVaultCipherTest`,
`RelayHostileResponseInstrumentedTest`, `RelayHttpTransportInstrumentedTest`,
`ConversationV2StorageInstrumentedTest`, `LegacyResetInstrumentedTest`, relay `node --test`.
Results: `REWRITE_PLAN.md`, Phase 5.

---

## DEC-027 — Phase 6: license, reuse of Telegram for Android's code, unread counters, notifications

**Status:** ACCEPTED
**Recorded:** 2026-10-01. Owner decisions in chat (plan: `../PHASE6_UI_PLAN.md`).

### Decision

- The repository is licensed **AGPL-3.0** (`LICENSE` at the root, the official text; stated as
  AGPL-3.0-only like libsignal) with `THIRD_PARTY_NOTICES.md`. This formalises owner decision 2 of
  `REWRITE_PLAN.md` ("publishing the source under AGPL-3.0").
- **Telegram for Android's code may be reused**, as the owner wants a Telegram-like UI and accepts
  publishing everything: isolated, strong pieces are translated to Kotlin/Compose from files whose
  header says "GPL v. 2 or later" (416 of 1,836 checked files), used under GPL v3 and combined with
  this AGPL-3.0 work, keeping the original header and listed in the notices. The 54 files with an
  Apache-2.0 header keep that license. Files without a header (1,366, among them the bubble
  drawable `MessageDrawable.java`) may also be ported, **at the owner's accepted legal risk** (see
  below). Pieces tied to Telegram's data model are rebuilt from screenshots and screen recordings.
  Never the Telegram name, logo, icons, sounds, fonts or images. This replaces the earlier "do not
  copy Telegram's code" note in `CLAUDE.md` and `TASK_QUEUE.md`.
- A read-only reference copy of Telegram's Java sources lives outside the repository
  (`D:\Tools\reference\Telegram`, commit `f2908b1`).
- **Unread counters:** Room v10 adds a nullable `lastReadAtEpochMillis` to `v2_contacts`; a local
  time only, never sent (not a read receipt). *Superseded by DEC-029 (2026-10-04): a per-message
  "seen" mark instead, because the wall clock can move backwards.*
- **Notifications for new messages, without message text** ("New message from <local name>").

### Accepted legal risk (owner, 2026-10-01, later the same day)

The owner asked to use Telegram files **without a header** too (1,366 of 1,836 checked; the
repository states GPL v2, neither "or later" nor "only"; no file carries an explicit "v2 only"
marking). Under GPLv2 section 9 a program that does not specify a version may be used under any
published version, so such files could be used under GPLv3 and combined with AGPL-3.0; but whether
the repository's GPL v2 statement counts as "not specifying a version" is an **open legal
question**. No lawyer's opinion exists: two documents received that day are not one (the first says
it was prepared by ChatGPT and requires a specialist's check; the second carries no author, and both
state that a headerless file must be checked against the project's general terms). The owner
**knowingly accepts the risk**: a rights holder might object, and the affected code would then have
to be removed or rewritten. Consequences: headerless files may be ported one by one, each listed in
`THIRD_PARTY_NOTICES.md` as "no header; GPL v2 repository; used under GPLv3 at the owner's accepted
risk"; where a file is also easy to write anew, doing so is preferred; the 54 Apache-2.0 files keep
their own license and notices; a file with an explicit "GPL v2 only" marking is excluded.
Nobody may describe this as legally cleared or lawyer-approved. A real written opinion would
replace this paragraph.

### Style decisions (owner, 2026-10-01, after step 6b)

Telegram's classic palette (light green outgoing and white incoming bubbles); a light wallpaper
pattern of our own; a dark theme with Telegram's Night values that follows the system, plus an
in-app switch (Light, Dark, System) from the start. Details: `../PHASE6_THEME_NOTES.md` (2a) and
`../PHASE6_PORTING_NOTES.md`.

### Consequences

- Anyone who receives the app may ask for its source; all derived files must keep their notices.
- The unread marker is a Room schema change with a migration test; notifications need the Android
  13+ permission and must never carry message text (a test pins it).

---

## DEC-028 — AI security audits (October 2026): triage and fixes; not the independent audit

**Status:** ACCEPTED
**Recorded:** 2026-10-03. Owner decisions in chat. Triage of every finding: `../AI_AUDITS_2026-10.md`.

### Context

The owner had two AI tools audit the project: a source audit of commit `335ccca` with the TQ-109
working tree (2026-10-01), and an audit of a debug APK built from a copy of the same code, at the
bytecode level (2026-10-02..03: 0 confirmed, 9 conditional, 9 informational, 9 closed-safe
findings). Neither found a flaw in the cryptographic core. Every finding was checked against the
code on 2026-10-03; several were confirmed, one (V2-01) with a concrete path the audit had not
shown.

### Decision

- Both audits are internal checks, like the Phase 5 reviews. They are **not** the independent
  external audit (TQ-106); the release gate stays as it is (`enable_v2_protocol` is false in
  release, DEC-021, DEC-026).
- Fixed now, in one block before step 6c (owner: TQ-110):
  - **V2-01:** once an envelope got no verdict in a sync, a later rejected envelope with the same
    routing tag stays on the relay for the next sync instead of being deleted, at most 10 per
    sync. A text behind a HELLO that hit a local failure is no longer lost. A text that a relay
    moves before its HELLO is still rejected and deleted (F3).
  - **SESS-01:** the chat state changes only through atomic updates, and messages and the safety
    number are shown only for the contact they were read for.
  - **STORE-04:** one vault key and one profile even if "Create profile" is tapped twice:
    process-wide locks around key and profile creation, the profile row is inserted and never
    replaced, and the button is off while a profile is being created.
  - **L1:** the transport does not follow HTTP redirects.
  - **M1, SRV-01, SRV-02 (QA relay):** a full rate-limit table drops the least recent client
    instead of being cleared; `RELAY_TRUST_PROXY=1` counts the client address a proxy on the same
    machine adds last to `X-Forwarded-For`; whole-relay caps (100,000 messages, 256 MiB of
    ciphertext; then 503 `relay_full`); expired messages are swept at most once a second.
  - **M2:** the deployment examples use relay API v3.
  - **L3:** GitHub Actions pinned to commit hashes, Gradle wrapper validation in CI, and
    `distributionSha256Sum` in the wrapper (checked against gradle.org).
- Planned for step 6f (owner): **H1**, the main practical risk (whoever sees an invitation QR can
  pair first): a "compare the safety number" step and a local "verified" mark; **L2**: the
  invitation shows whether its upload worked and offers "try again".
- Accepted and documented, no code change: STORE-01 (putting back old records needs write access
  to the app's files, in practice root; out of scope), STORE-02 (the app never creates last-resort
  Kyber keys), INV-01 (the QR is a bearer capability, see H1), TRN-01 and I-2 (cleartext only in
  debug builds), I-3 (the fixed-key test vault sits in the main source set; unused there and
  removed from release builds by R8; to be moved once shared test fixtures exist), and the
  informational findings. L4, I-4 and I-7 stay with TQ-108 (step 6g); I-10 with TQ-008.
- The audit tools' working files were moved out of the project folder (owner, 2026-10-03).

### Consequences

- No wire, invitation or storage format change and no Room schema change (one new DAO insert
  method). DEC-026's deletion rule gains one exception: a same-tag rejection behind a failure
  waits one sync, bounded.
- A relay behind a reverse proxy needs `RELAY_TRUST_PROXY=1`; without it the relay behaves as
  before. A full relay answers 503, which the app treats like any relay failure and retries.

### Evidence

New tests: `ConversationV2ServiceTest` (a text behind a failed HELLO waits; deferral is bounded),
`ConversationV2StateTest` (SESS-01), `VaultKeyLossInstrumentedTest` (key and profile creation at
the same moment), `RelayHostileResponseInstrumentedTest` (redirects), relay tests (rate-limit
table, trusted proxy, whole-relay caps, expiry sweep). Results (2026-10-03): all suites green on
the JVM and on both phones, the V2-01 tests fail without the fix, the owner's hand check passed
(TQ-110 in `TASK_QUEUE.md`).

---

## DEC-029 — Room v10: unread counters from a per-message "seen" mark; verified-date column

**Status:** ACCEPTED (2026-10-04, step 6d). Supersedes the column named in DEC-027 for unread
counters; everything else in DEC-027 stands.

### Context

DEC-027 planned a nullable `lastReadAtEpochMillis` on `v2_contacts`, unread meaning "newer than
that time". Message times are the phone's wall clock at receipt, and that clock can move backwards
(manual change, network time correction): a text received after such a jump would count as read
without ever being shown.

### Decision

- **Room v10** (`MIGRATION_9_10`, purely additive): `v2_messages.isRead INTEGER NOT NULL DEFAULT 1`
  and `v2_contacts.verifiedAtEpochMillis INTEGER` (null). Existing rows count as seen, so an update
  brings no flood of old badges; no row is deleted or reinterpreted.
- An incoming text is stored unseen; the user's own messages are stored seen. The chat list counts
  unseen incoming texts per contact (and per request; they stay unseen when a request becomes a
  contact, which keeps its ID).
- **Seen means shown:** a conversation's texts are marked in the same locked step that reads them
  for the screen (`textsMarkingRead`), and only while the conversation is on screen with the app
  in front (`LifecycleResumeEffect`). A text that arrives a moment later stays unseen until it is
  shown.
- **Local only:** nothing about reading is ever sent; there are no read receipts (`AGENTS.md`).
- `verifiedAtEpochMillis` is the date the user confirmed matching safety numbers; set and shown
  from step 6f (audit H1, DEC-028). Unused until then.

### Consequences

- Compatibility: additive schema change only; no protocol, wire, invitation, vault or AAD change.
  Room has no down-migration: installing an older build over a v10 database fails to open it, as
  for every earlier schema step (QA builds only).
- The unread query scans `v2_messages` once per refresh (a few thousand rows at most in QA); an
  index can follow if it ever matters.

### Evidence

`SecureDatabaseMigrationInstrumentedTest` (9 to 10), `ConversationV2StorageInstrumentedTest`
(counts, last text, marking), `ConversationV2ServiceTest` (counts across sync, marking, held texts
after acceptance, nothing sent when seeing). Results: HANDOFF and TASK_QUEUE (step 6d).

---

## DEC-030 — Safety-number comparison step and local verified mark (audit H1)

**Status:** ACCEPTED (2026-10-05, step 6f; owner's answers the same day).

### Context

Pairing is trust on first use (DEC-019): whoever sees an invitation QR code, even in a photo, can
pair first and appear to the inviter as a contact request under any name (audit H1, DEC-028).
Comparing the 60-digit safety number over a trusted channel is the only protection. Until now the
number was shown, but nothing asked for the comparison and nothing recorded it.

### Decision (owner, 2026-10-05)

- **Accepting stays possible without comparing, but only as an explicit choice.** "Compare and
  accept" on a request opens a step with the request's number, how to compare it, and three answers:
  "The numbers match — accept" (accepted and marked verified), "Accept without comparing" (accepted,
  the chat header keeps saying "Safety number not checked"), "The numbers differ — decline". A
  mandatory comparison was rejected: it blocks pairing at a distance and trains people to tap
  "match" without looking.
- **The scanner gets the step at once:** after "Add contact" succeeds, the new contact's page opens
  with its number and "The numbers match", while both people are still together. Back leads to the
  new chat; comparing later stays possible (tap the name in the chat header).
- **The mark is bound to the exact number the person saw.** The screen passes the number it showed;
  the service recomputes the number of the stored identity inside the same locked transaction and
  marks only if the digits are identical, otherwise it throws and changes nothing (accepting
  included). A stale screen or any mix-up can therefore never mark a contact whose number was not
  shown.
- **Local only:** `v2_contacts.verifiedAtEpochMillis` (Room v10, DEC-029), never sent; each side
  marks its own contact. The mark can be removed on the contact page.
- **Never "checked" with a changed key:** a contact's identity key never changes; setting
  `KEY_CHANGED` also clears the mark (Room and the test double), a blocked contact cannot be marked,
  and `V2Contact.safetyNumberChecked` is false for a blocked contact whatever is stored.
- Wording: "Safety number checked / not checked", never "verified" or "secure": the threat model
  forbids presenting any state as fit for real sensitive communication before the independent audit.
  No mark in the chat list (Telegram's blue tick means something else).

### Consequences

- Compatibility: no protocol, wire, invitation, vault or AAD change; no schema change (the column
  exists since Room v10). `acceptRequest` gains an optional compared number; `markVerified` and
  `clearVerified` are new service methods.
- The card of a request in the chat list no longer accepts by itself; it leads to the step.

### Evidence

`ConversationV2ServiceTest` (the mark after "match"; nothing else sets it; a request from whoever
photographed the code is not accepted with the real contact's number; a wrong, partial or empty
number accepts nothing; marking needs the shown number and can be undone; the scanner marks while
waiting and keeps the mark; a blocked contact loses it and cannot get it),
`ConversationV2StorageInstrumentedTest` (Room), `SafetyNumberCheckInstrumentedTest` and
`ConversationScreenInstrumentedTest` (screens). Device suites not yet run (no phone, 2026-10-05).

---

## DEC-031 — Notifications without message text (step 6f')

**Status:** ACCEPTED (2026-10-05). The owner's decisions are those of DEC-027 (notifications for new
messages, "New message from <local name>", never the text, driven by the background sync, the
Android 13+ permission asked in context); this entry records the privacy rules that implement them.

### Decision

- **Who and how many, never what.** A notification for a contact says the name the person gave
  that contact and "New message" (or "N new messages", all unseen texts of that chat). The code that
  builds notifications never reads a message text: its input are unseen counts and contacts only
  (`messageNotices`), and a source check pins that it uses no text, no messaging or big-text style,
  no ticker and no inline reply.
- **A request is never announced by its claimed name:** that name is chosen by whoever sent the
  request (anyone who saw the QR code, audit H1), so the notification says only "Contact request" and
  asks to compare the safety number.
- **Locked screen: "New message" only, unless the person chose otherwise.** Every notification is
  private with a public version that has neither name nor count. *Corrected 2026-10-06 after the first
  device run:* an app cannot set its channel's lock-screen visibility (both phones answered "no
  override"), so the first plan, "the channel is private and Android shows the public version even
  where the phone shows all content", was wrong. What a locked phone shows depends on the person's own
  lock-screen setting: with "hide sensitive content" (the default on a phone with a lock) only "New
  message"; with "show all content" the name and the count. The text of a message exists in no
  version of any notification, so it is never shown in either case. Android's notification history
  and apps with notification access see the name and the count, never the text (accepted with
  DEC-027).
- **Posted only by the background sync, only while no screen of the app is visible**, for the
  chats whose unseen count grew during that sync (counts read before and after; also after a sync
  that failed midway, since those texts would never be announced later). The chat screen fetches
  every few seconds only while the app is on screen (started); before, it kept fetching in the
  background until Android froze the app, and texts fetched that way would have arrived unannounced.
- **Removed** when the chat is shown, when the request is accepted or declined, when tapped, and all
  of them after "Delete local data" (they name contacts). Tapping opens the chat: the intent carries
  only the local contact ID, accepted only in the form of a random local ID.
- **Permission in context:** a card on the chat list once a chat or a request exists, "Turn on" or
  "Not now" (not asked again); settings show whether notifications are on and open Android's
  notification settings. Release builds have no chats and post nothing.

### Consequences

- In the background, new messages arrive with the periodic sync (about every 15 minutes or later,
  best effort), not every few seconds; the chat screen's notice already said so.
- No protocol, wire, invitation, storage or schema change.

### Evidence

`NotificationRulesTest` (what is announced, the words, intent IDs, the source check),
`NotificationPrivacyInstrumentedTest` (two devices over Room and libsignal: the posted notification
and its public version carry neither text nor claimed name; the public version says "New message"; the
channel is never public; nothing while the app is on screen). Run on both phones 2026-10-06: it caught
the wrong channel claim above (fixed); the rest passed.

---

## DEC-039 — "Played" for every voice message; trim, seek, the main screen's tabs

**Status:** ACCEPTED (2026-10-07, the owner's requests the same day after the hand check of DEC-038:
a voice message should show on both phones whether it was played, "like Telegram"; asked to choose,
the owner took Telegram's way: a small dot next to the length until the recipient presses play, two
ticks still mean "delivered". Also: seek in a voice message, trim a paused recording, finer dust with
a finger snap, the rim of the glass less visible, the round "down" button out of the way while
recording, and Telegram 12's main screen with its bottom tabs). Written on Opus.

### Decision

- **"Played" for every voice message.** Pressing play on the contact's ordinary voice message marks
  it listened on this phone and queues "played" (inner payload type 7, unchanged since DEC-036) for
  the contact, once, in one transaction. The contact's app marks its copy listened (an ordinary one is
  kept, a one-time one is deleted as before). Until then both phones show Telegram's dot (3 dp, in the
  bars' played color, 6 dp after the length). Our own messages are never marked by playing them;
  one-time recordings keep their own path (`playOnce`). A pending request gets no "played" (no
  receipts for requests), a blocked contact nothing; the dot goes here all the same. Fetching a
  recording to play it tells nothing by itself: only the screen's play does (`markVoiceListened`).
- **Room v13**: one column, `mediaListened`, on `v2_messages`. Voice messages stored before it count
  as listened.
- **Seek like Telegram's** (`SeekBarWaveform`): a tap on the bars plays or pauses; a finger moved
  0.2 cm sideways drags the place along (from where it was, not to where the finger is), shown as it
  goes and taken on release; on a message not playing the place stays and is where it starts. The
  contact's one-time recording cannot be dragged.
- **Trim while paused** (`RecordedAudioPlayerView`): the paused recording has two white handles; the
  parts cut off are darker with faint bars, a label in the middle plays the kept part and gives its
  length. Sending sends only the kept part; recording on asks "Trim the recording?" the first time
  (Telegram's "trimvoicehint") and then cuts it and goes on. The cut is made on whole AAC frames, in
  memory: the frames kept move to the front, the rest of the buffer is zeroed; nothing is written to
  a file (DEC-036 rule).
- **Dust and snap.** The grains of a deleted message are as fine as Telegram's (0.4 dp, at most
  24,000 per bubble, drawn in batches); a short finger snap plays as it falls apart (the owner's joke
  on the effect's name). The snap is our own sound, made in code from noise and a tone; no recording
  of a film or anything else is used. It follows the chat sounds' switch and silent mode.
- **Glass rim.** The light along a pane's top and bottom is Telegram's sliver (the rounded edge
  minus itself moved in by the stroke width), full width only in the middle and fading down the round
  sides; ours was an even line all round, which the owner saw as a gray rim.
- **The round "down" button** goes while a recording runs (it sat between the recording's buttons).
- **Main screen with tabs** (Telegram 12, `MainTabsActivity`, `GlassTabView`): a pill of glass at the
  bottom (56 dp, 8 dp from the edges, at most 344 dp wide) with Chats (counting unread chats that are
  not muted, and requests), Contacts, Settings and Profile; the selected tab gets a pill of its color
  at 9 percent and its icon filled; pages slide sideways. Contacts lists the contacts by name with
  "My invitation" and "Add contact"; Settings is the settings page without a way back; Profile shows
  this phone's profile and the invitation. The bar goes while chats are searched or selected; Back on
  another tab returns to the chats. Folders and calls are not part of this messenger. The tab icons
  are Telegram's (`tab_chats`, `tab_contacts`, `tab_settings` Lottie files, their outline and filled
  frames turned into vectors).
- **Second round, after the owner's check the same evening** (screenshots and three recordings, one
  of Telegram filmed with another phone): the glass panes lose Telegram's light lines along top and
  bottom altogether and read the picture behind with a margin (an empty margin showed as a band); the
  tab icons are 24 dp as Telegram draws its Lottie (they were 44 dp); a screen hidden under another
  is not drawn at all instead of drawn clear, so it draws afresh when it shows (the main screen
  flickered black during a swipe back, an old copy of the chat showed when a page over it closed);
  the swipe back leaves alone a drag along a voice message's bars or a trim handle and is off while a
  recording runs (both took the person out of the chat); messages are selected as in Telegram (a long
  press starts it, a round tick at the start of each line, a tap anywhere on a line ticks it, "N
  selected" with reply and delete in the glass header; a tap on a bubble now opens its menu); a
  message the contact deleted for both falls apart here too, with the snap; a one-time voice message
  plays in Telegram's own player (the chat blurred behind it, the bubble moved toward the middle, a
  note that it can be heard once, "Delete and close"; at the end the bubble falls apart into dust).
  Then (the owner's recording): opening a settings page from the Settings tab flashed the chats and
  Back led to the chats, not the settings. The screen stack built a screen anew whenever its part
  changed (top to below and back), losing its state (the tab, a chat's scroll); its screens are now
  emitted in one keyed loop from one call, so each keeps its composition. Also by the owner's
  decision (2026-10-07, after the consequences were explained): the chat list no longer shows the
  "Secure chats (test)" title, the red "test build, not independently reviewed" banner or the gray
  note about fetching; the title is "Chats". The warning stays only on the onboarding screen and on
  the release build's "not for messaging" screen; the lack of an independent audit is still true
  (TQ-106) and nothing may claim otherwise. After the next check: a voice message could not be
  selected by a long press (its bars and its bubble both answered and the second took the tick away
  again); a long press now only adds, and works on the empty part of a message's line too; the tabs
  lost the ripple square Telegram does not have; at start the onboarding screen and "No chats yet"
  no longer flash before the profile and the chats are read. A message's line now spans the whole
  width (the list lost its side padding, the bubble keeps 8 dp inside its line), so the long press and
  the selection tint reach the edges. Checked 2026-10-07: ui JVM tests green; on both phones the voice,
  conversation, conversation motion and chat list classes 38 of 38, and the home tabs, system Back and
  settings classes earlier the same evening; installed on both.
  No format or storage change in this round.

### Compatibility (AGENTS.md checklist)

1. Wire format: unchanged. Type 7 keeps its layout (the 16-byte inner ID); its meaning widens from
   "our one-time recording was played" to "our voice message was played". Room: v12 to v13 adds one
   column (`INTEGER NOT NULL DEFAULT 0`) and sets it to 1 for every stored voice message.
2. An older app that gets "played" for an ordinary recording ignores it after authenticating it (its
   rule accepts one-time recordings only): the dot stays there. An older app never sends it for
   ordinary recordings: the dot stays here until the contact updates. Nothing fails.
3. Migration and rollback: additive; a downgrade below v13 is not supported by Room (as before,
   debug builds only).
4. Confusion: "played" for a one-time recording still deletes it as before; for an ordinary one it
   only sets the mark, once (a second one is ignored). Texts and unknown IDs are ignored as before.
5. Existing data: kept; old voice messages count as listened, so none sends "played" late.
6. Tests: `ConversationV2ServiceTest` (told once and shown on both phones, kept playable; not for our
   own or one-time messages; not told to a request or a blocked contact; a forged or repeated
   "played" sets the mark once and deletes nothing), the Room migration and repository tests, JVM
   tests of the trim, the seek and the tab counter, device tests of the dot, the drag, the trim with
   its question and the tabs.

### Security notes

- New signal about listening, chosen by the owner: the contact learns when a voice message was first
  played on this phone (roughly, by when "played" arrives), as Telegram shows it. It travels inside
  the end-to-end session like a receipt; the relay sees one more small envelope. There is still no
  read receipt for texts, and no switch to turn "played" off (Telegram has none either).
- Trimming wipes the cut-off sound from memory; the sent recording is the kept part only.
- The snap and the tabs change no rule; the tab bar shows only what the list already shows.

---

## DEC-038 — Telegram's dark theme, wallpaper, settings and recording animations; turning the phone

**Status:** ACCEPTED (2026-10-07, the owner's requests with screenshots and screen recordings the same
day: the dark theme and the settings "laid out the same way as in Telegram", the chat background
"like the original, more detailed, not cartoonish", the recording's animations "exactly as shown
there", the hint of "1" next to it each time, and the open chat surviving a turn of the phone).
Written on Opus. No protocol, storage or security rule changes.

### Decision

- **Dark theme = Telegram's default night theme "Dark Blue"** (the owner's Telegram uses it; its
  colors were measured on the screenshots: ground #151E27, sections and lists #1D2733): the values of
  `darkblue.attheme` with the default accent of `Theme.java`, among them outgoing bubbles filled
  from one gradient fixed to the screen (blue below, violet above: #258DE5, #4272DF, #8146D7,
  #9F3EAA; `MessageDrawable`). As before (DEC-027), a Telegram value that fails WCAG AA is darkened
  or lightened just enough (the first two bubble colors, the grays, the badge and button blues;
  `MessengerColorsTest` checks every stop of the gradient). The light theme stays the classic one.
- **Text color by default.** Text without its own color now takes the theme's text color everywhere
  (on dark grounds it was black: the owner's dark-settings screenshot).
- **Wallpaper = Telegram's default.** Telegram's four-color gradient (`MotionBackgroundDrawable`; the
  native `generateGradient`, rewritten in Kotlin: a 60 by 80 grid, the colors at four of eight points
  on a ring, a slight twist, (0.9 - distance)⁴ weights) with its doodle pattern (`default_pattern.svg`,
  copied unchanged, 507 KB, 745 shapes) drawn once into an alpha mask as wide as the phone's shorter
  side and laid over it with "soft light": light #DBDDBB, #6BA587, #D5D88D, #88B884 with the pattern
  in black at 34 percent (`Theme.createDefaultWallpaper`); dark #0E141A, #172431, #0F171E, #141C2B
  with it in white at 38 percent. Each message of ours turns the colors one step on in 500 ms, as
  Telegram does. Our own drawn doodles are gone.
- **Settings in Telegram 12's layout** (`SettingsActivity`): the person's avatar and name on top,
  rounded sections on the gray ground, entries with Telegram's colored tiles and white icons (its
  `settings_*` vectors, copied unchanged), each opening its own page: chat settings (a preview chat
  on the wallpaper, the theme as three cards, the swipe action), privacy (screen security, deleting
  the local data), notifications and sounds, the background connection, the delivery server, the
  language. Telegram's switch, radio button, headings and notes are drawn from its numbers. What
  each setting does is unchanged; release builds still show no chat settings that do not exist there.
- **Recording like Telegram's** (`ChatActivityEnterView`, `ControlsView`, `RecordCircle`, `RecordDot`,
  `HintView2`): the circle follows the finger to the left and shrinks to 70 percent while it slides;
  the lock climbs with the finger (only the lock, slightly tilted and open at rest) and goes away once
  the slide is past 30 percent; locked, the lock becomes the pause button and "1" snaps in above it;
  a cancel by the finger takes the circle back into the microphone; "CANCEL" or the bin plays
  Telegram's bin (the red dot turns into a bin whose lid jumps; drawn after the frames of its
  animation) while the timer slides away and the text field fades back in; paused, the circle goes
  into the bar, which shows the recording as Telegram does (a blue strip, white bars, a white label
  with play and the length) and a send button. Tooltips stand next to their button as Telegram's do:
  "1" switched on says what it means each time (and nothing when switched off); after locking, the
  pause button is pointed at the first four times; paused, "1" is explained the first three times.
  The send sign stays an arrow: a white paper plane in a blue circle is Telegram's logo (DEC-033).
- **Turning the phone keeps the open screen.** The activity handles orientation, size and the
  system's dark mode itself (like Telegram's), so nothing is rebuilt: the open chat, a draft and a
  recording stay, and onboarding no longer flashes on a slow phone. The app's screen stack is saved,
  so a language change (which still rebuilds the activity) comes back to the same page.

### Compatibility

No wire, storage, invitation or vault format changes; no Room change. The theme choice and all
settings keep their stored values. The wallpaper pattern lives only in memory (about 4 MB on a
1440-pixel-wide phone, 1 MB on the Redmi).

### Security notes

- Screen security (`FLAG_SECURE`), notifications, the relay and the protocol are untouched. The
  person's own name, shown at the top of the settings, is read from the vault like before and kept
  only in memory.
- The saved screen stack holds only the names of screens, nothing of the chats.

---

## DEC-037 — Deleting single messages, for this phone or for both; the glass chat screen

**Status:** ACCEPTED (2026-10-06, the owner's requests the same day: "delete just that message, like
Telegram, where it shatters into pieces", with the choice to delete it for the other person too; and
the chat screen "as sleek as liquid glass", the wallpaper under the status bar and the navigation
bar, taken from Telegram's code). Written on Opus.

### Decision

- **Delete a message.** A long press on a bubble (or the accessibility menu) offers "Delete" next to
  "Reply". Telegram's dialog asks "Delete message" / "Are you sure you want to delete this message?"
  with a tick "Also delete for <name>" (not offered for a blocked contact). The message goes from
  this phone: its row, its vault-encrypted body or note, its stored pieces. An outgoing one still
  waiting to be sent is never sent (its ratchet step is skipped; libsignal tolerates the gap); a
  receipt already queued for it still goes. Quotes of it then say "Message not available".
- **For both: inner payload type 8**, inside the session: a count (1 to 100) and that many distinct
  inner message IDs of this chat, never its own. The contact's app deletes its copies of those
  texts and voice messages, in either direction (Telegram allows that in private chats), when the
  request arrives. Only this chat's messages can be named; control messages, other chats and
  unknown IDs are ignored, and so is a request from a pending contact request.
- **Dust.** The deleted bubble's picture falls apart into small squares that drift, float up and
  fade from left to right in about a second and a half (Telegram's `ThanosEffect`, its particle rules
  rewritten for the CPU), while the list closes the gap.
- **The glass chat screen (Telegram 12).** The app draws edge to edge: the chat's wallpaper and
  messages run under the status bar and the navigation bar, and every other screen keeps its content
  clear of the bars (its top bar's color goes under the status bar) and of the keyboard. In the chat,
  the header is three glass pills (back, the contact, the menu; no call button: there are no calls)
  and the input bar a glass island with the microphone or send button in a blue circle. Glass is
  what lies behind it, recorded every frame into a render node, blurred 6 dp with three times the
  saturation, under the panel color at 85 percent, with Telegram's liquid-glass shader bending it at
  the rim on Android 13 and later (`Glass.kt`; the shader is Telegram's, copied unchanged). Nothing
  of the chat leaves the GPU for it; no bitmap is kept.

### Compatibility (AGENTS.md checklist)

1. Payload type 8 is new; the envelope and types 1 to 7 are unchanged. No Room change: deleting rows
   uses a new query; nothing is added to the schema.
2. An app that predates type 8 drops the request after authenticating it: the other copy stays.
3. Migration and rollback: none needed.
4. Confusion: type 8 is read only by its own strict rule (count 1 to 100, distinct IDs, not itself).
5. Existing data: unchanged.
6. Tests: `EnvelopeV2Test` and `V2DecoderFuzzTest` (types 7 and 8), `ConversationV2ServiceTest`
   (here only sends nothing; for both deletes on the other phone in both directions; gone, unknown
   or another chat's IDs change nothing; a blocked contact cannot be asked), the Room test of the
   single delete, device tests of the menu, the dialog and the glass screen.

### Security notes

- A contact can delete our copy of any message of our chat with it, ours included (owner: as in
  Telegram). It cannot reach another chat; the relay cannot forge the request (authenticated).
- "Delete for both" is not a guarantee: the other phone may have shown, copied or screenshotted the
  message already, may never get the request (an old app, a deleted contact), and deletion is not a
  secure erase of the flash (DEC-014).
- Edge to edge and the glass change no rule: screen security (`FLAG_SECURE`) and the notification
  rules are untouched.

---

## DEC-036 — Voice messages (inner payload type 6, one-time relay queues, Room v12)

**Status:** ACCEPTED (2026-10-06, the owner's answers the same day: voice messages only, no calls;
at most 15 minutes; "Delivered" once the recording itself has arrived). Written on Opus.

### Context

After the chat management block the owner asked for voice. Calls are not wanted ("only messages,
calls are not needed"); files and photos come later, video not at all. A recording (about 3.7 MB
for 15 minutes) does not fit one relay message (64 KiB of base64, 80 KB per request), should not
pass through the session as one huge payload, must never be stored unencrypted, and should tell the
relay as little as possible.

### Decision

- **Recording, in memory only.** The microphone's sound (16 kHz mono; 44.1 kHz where a phone cannot
  record at 16 kHz) goes into the phone's own AAC-LC encoder at 32 kbit/s, and the frames, each
  behind its ADTS header, into one buffer reserved at the largest allowed size. No file is written at
  any point, so no unencrypted copy is ever on the flash; the buffers are zeroed when the recording
  is handed over or thrown away. The bars of the bubble are at most 100 values of 0 to 31
  (Telegram's rule: each slice's peak against 1.8 times their average, at least 2,500). Telegram's
  gestures: hold to record and let go to send, slide left to cancel, slide up to lock; a tap only
  explains; at most 15 minutes (sent at the limit), at least 0.7 seconds. Leaving the chat or the app
  stops the microphone: nothing records in the background. The microphone permission is asked at
  the first use and declared in the debug manifest only (chats exist only in debug builds).
- **The recording travels beside the session.** It is cut into pieces of 47 KiB, the last filled up
  with zeros, so the relay learns only how many pieces there are (at most 96). Each piece is
  encrypted with AES-256-GCM under a fresh random 32-byte key used for this one recording; the nonce
  is four zero bytes and the 64-bit piece index; the associated data is `"smp-voice-piece-v1\0"`,
  the number of pieces and the index (16 bits each), so a piece cannot be moved, repeated or left
  out unnoticed. A piece is `"SMM1"`, version 1, its index and the ciphertext with its tag: 48,151
  bytes, 64,204 characters of base64, inside the relay's 64 KiB (a test reads the relay's and the
  transport's limits). The digest is SHA-256 over `"smp-voice-recording-v1\0"` and the padded
  recording. A standard AEAD with a single-use key; no construction of our own.
- **One relay queue per recording.** The sender makes a fresh read secret (queue ID as in DEC-024)
  and sends the pieces there. The read secret and the key travel only inside the note.
- **The note is inner payload type 6**, inside the session like a text (authenticated, padded): the
  queue's read secret, the key, the digest, the size, the number of pieces, the length in
  milliseconds, the bars, the codec (1 = AAC in ADTS), then 0, or 1 and the 16-byte inner ID of the
  message it answers (replies work as in DEC-035, in both directions). Decoding is strict: the
  number of pieces must match the size, the length lies between 1 ms and 15 minutes and 2 seconds,
  at most 100 bars of 0 to 31, codec 1, the reply marker 0 or 1, a message cannot answer itself.
- **Sending order.** The note's envelope is held until every piece is up, and the contact's later
  envelopes wait behind it (ordered sending, DEC-026), so a contact never gets a note before its
  recording. Pieces go up across syncs (the relay's rate limit); progress only moves forward; one
  upload and one download run at a time in the process. A recording whose stored piece is gone (a
  damaged store) is given up so it cannot hold the chat back: its note goes, the contact never gets
  the recording, and it never shows "Delivered".
- **Receiving.** The note is stored once authenticated, like a text (order by DEC-034, it completes
  a pairing waiting for acceptance, a request's is held). The app then fetches the one-time queue
  (at most 3 pages per sync), keeps only pieces that decrypt under the note's key at their own
  place, stores them as they came (still encrypted under that key; the note itself vault-encrypted),
  and deletes everything it fetched from that queue after the commit, junk included. Once all pieces
  are here and the whole matches the digest (and the padding is zeros), the message is ready, and
  only then is its receipt queued: **"Delivered" means the recording itself is on the contact's
  phone (owner).** A mismatch makes it unavailable (pieces deleted, no receipt); so does a recording
  still incomplete 25 hours after its note (the relay keeps a piece a day). A request's voice
  message is fetched, and receipted only when the request is accepted.
- **Playing** decrypts the pieces into memory and hands them to Android's player through a
  `MediaDataSource` (no file); the bytes are zeroed when playing ends. Our own plays at once.
- **Pause (owner, like Telegram's newest screen):** once locked, a recording can be paused; the
  microphone is let go, what was recorded so far can be listened to (from memory), and then it goes
  on, is deleted, or is sent. The timer shows tenths ("0:04,3").
- **One-time recordings (owner, like Telegram):** while locked or paused, the round "1" button above
  the send button makes the recording one-time (a hint with a flame: "The recipient will be able to
  play the message only once."; pressed again, it is an ordinary one). The note's options byte says
  so (bit 0; any other bit is refused). The receiving phone gives such a recording only through a
  one-time path that, in one transaction, decrypts it into memory, deletes its pieces and marks it
  played, so it is never given again even if the app stops while it plays; it plays from the start
  and cannot be jumped in. Bubbles carry Telegram's "1" until played; then they say "Played".
- **"Played" (inner payload type 7, owner: "the 1 disappears and it says listened"):** the receiving
  phone then tells the sender, inside the session, that the one-time recording was played; the
  sender's "1" goes, its bubble says "Played" and its own copy is deleted too, as in Telegram. This
  is the only thing about listening the app ever sends, and only for one-time recordings: a "played"
  naming anything else (an ordinary recording, a text, an unknown ID, a recording not all uploaded)
  changes nothing.
- **Room v12**: `v2_messages.mediaState` (nullable text: uploading, downloading, ready, unavailable)
  and `v2_messages.mediaProgress` (pieces uploaded, default 0); a new table `v2_media_chunks`
  (message ID, index, piece) holds the pieces as they travel, one row each (a row stays far below
  Android's 2 MB cursor window). A voice message's `encryptedBody` is its note, vault-encrypted with
  its own record type (`voice-note`) bound to the row. Clearing history, deleting a contact and the
  wipe delete the pieces too.

### Compatibility (AGENTS.md checklist)

1. Old and new: payload type 6 is new; types 1 to 5 and the envelope are unchanged. The relay API
   is unchanged (v3: send, fetch, delete), now also used on one-time queues. Room v11 to v12 adds
   two columns and one table.
2. Backward compatibility: an app that predates type 6 decrypts the note, finds the type unknown,
   keeps the ratchet step and drops it without a receipt; the sender sees "sent to the delivery
   server" only, and the pieces expire on the relay within a day. Both test phones get one build.
3. Migration and rollback: `MIGRATION_11_12` only adds; every row survives byte for byte
   (instrumented test). A downgrade to a v11 build is not supported (Room refuses a newer schema).
4. Confusion: pieces carry their own magic and domain-separated labels and use a key that exists for
   one recording only; an envelope dropped into a one-time queue fails the piece check and is
   deleted; a piece dropped into an inbox fails envelope parsing and is deleted (DEC-026).
5. Existing data: no voice messages; other rows get no media state and progress 0.
6. Tests: `VoiceMediaTest` (round trips, every tampering refused, the relay's size limit),
   `EnvelopeV2Test` and `V2DecoderFuzzTest` (type 6 with and without a reply),
   `ConversationV2ServiceTest` (delivered only with the recording, missing piece, junk deleted, note
   held and texts behind it, given up after 25 hours, from a request, a reply, clearing and deleting,
   a damaged store), `ConversationV2StorageInstrumentedTest` and `SecureDatabaseMigrationInstrumentedTest`
   (Room v12), `VoiceModelTest` (lengths, bars, ADTS header), `VoiceMessagesInstrumentedTest` (the real
   microphone and player on the phones; the recording is zeroed once played),
   `LocalizationResourcesTest` (microphone in the debug manifest only; no file in recorder or player).

### Security notes

- The relay learns that a fresh queue receives N equal pieces and when, from which address they are
  uploaded and from which they are fetched, so it can link the two phones (as it can for any envelope
  sent to an inbox), and roughly how long the recording is (one piece is about 12 seconds). It cannot
  read, change, reorder, swap or cut short a recording unnoticed.
- "Played" tells the sender when a one-time recording was played: metadata about listening, sent by
  design for one-time recordings only (owner). It is a courtesy, not a proof: a modified app could
  stay silent or say it without playing, and nothing can stop a recipient from recording the sound
  with another device.
- The note's read secret gives our phone no power its sender lacks: a contact can only name a queue
  whose secret it already holds, and could empty that queue itself. A hostile contact can make our
  phone fetch and delete at most 3 pages a sync per voice message until it completes or expires: a
  bounded cost, never a verdict.
- Stored recordings are protected like stored texts: their key is in the vault-encrypted note, so
  they are not forward secret against a thief who can also use the Keystore key (CLAUDE.md boundary).
- While recording or playing, the sound exists in the app's memory and in Android's audio stack.

---

## DEC-035 — Chat management and replies (Room v11, inner payload type 5)

**Status:** ACCEPTED (2026-10-06, the owner's answers the same day: replies "yes"; "Delete chat"
deletes the contact too).

### Context

The owner asked to finish the Telegram look with what its chat list and chats do: swipe a chat,
select chats, pin, mute, delete, and answer a message by swiping it. Pins and mutes need a place to
live; a reply needs to say which text it answers, which changes what a message carries.

### Decision

- **Pin, mute, mark read, clear history: on this phone only.** Nothing is sent, the contact is never
  told. Pinned chats stay on top, the latest pin first. A mute lasts an hour, eight hours, two days
  or for good (the chat's menu; the list and its swipe mute for good, as in Telegram); a muted chat
  is not announced (DEC-031 rules otherwise unchanged) but its texts are still counted, with a gray
  counter. "Mark as read" from the list sets the local "seen" mark of DEC-029 for the whole chat;
  nothing about reading is ever sent.
- **Clear history** deletes the chat's texts on this phone: every incoming one, and every outgoing
  one already handed to the relay. A text still waiting to be sent keeps its envelope (a committed
  ratchet step the contact's phone needs) and stays until it is sent. Receipts and the session stay.
- **Delete chat deletes the contact (owner's choice)**: its texts, everything still queued for it,
  the invitation it answered (whose pre-keys were already gone), its libsignal session and stored
  identity, in one transaction under the protocol lock. Nothing is sent. Whatever it sends later
  finds no route and is deleted from the relay unread (as any unknown tag, DEC-026); it sees "sent
  to the delivery server", never "delivered". Writing to each other again takes a new invitation,
  and the identity is then trusted afresh (TOFU), like any new contact; the person who still has
  the contact must delete it too before a new pairing (one identity, one contact).
- **Replies.** A text may answer another text of the same chat, in either direction. Inner payload
  **type 5** = a text (type 3 fields) followed by the 16-byte inner message ID it answers. Only the
  reference travels: each phone shows the quote from its own copy, or "Message not available" if it
  does not have that text. A plain text keeps type 3, byte for byte. The sender refuses a reply to a
  text it does not have; a receiver stores the reference unchecked (it only selects what is shown).
- **Room v11** adds three nullable columns: `v2_contacts.pinnedAtEpochMillis`,
  `v2_contacts.mutedUntilEpochMillis`, `v2_messages.replyToInnerIdHex`. Plain columns like the other
  local marks (state, verified date); message bodies stay vault-encrypted.
- **The look is Telegram's** (DEC-033): swipe a row to the left past 45 percent (or fling at
  3500 px/s) for the action chosen in the settings (pin by default; read, mute or delete), with its
  band, icon and reveal; a long press selects, and the top bar then pins, mutes, deletes, marks read
  or clears the selection; a bubble swiped left 50 dp (start 0.4 cm, at most 80 dp) or pressed long
  is answered; a quote jumps to the quoted text and lights it up.

### Compatibility (AGENTS.md checklist)

1. Old and new: payload type 5 is new; types 1 to 4 and the envelope are unchanged; Room v10 to v11
   adds nullable columns only.
2. Backward compatibility: an app that predates type 5 decrypts a reply, finds the type unknown,
   keeps the ratchet step and drops the content (authenticated but unparseable), sends no receipt:
   the reply is lost on that phone, safely. Both test phones get the same build.
3. Migration and rollback: `MIGRATION_10_11` only adds columns; every row survives byte for byte
   (instrumented test). A downgrade to a v10 build is not supported (Room refuses a newer schema),
   as before.
4. Downgrade and cross-protocol confusion: none; type 5 is read only by its own rule (strict, the
   16-byte ID must be there, nothing may follow, a text cannot answer itself).
5. Existing data: chats start unpinned and unmuted; stored texts answer nothing.
6. Tests: `EnvelopeV2Test` (type 5 bytes, a plain text unchanged, damaged replies rejected),
   `V2DecoderFuzzTest` (replies in the corpus), `ConversationV2ServiceTest` (reply on both phones,
   unknown reply refused, pin and mute local, mark read sends nothing, clear keeps what is still to
   be sent, deleted contact unroutable and re-paired afresh), `ConversationV2StorageInstrumentedTest`
   and `SecureDatabaseMigrationInstrumentedTest` (Room v11), UI tests on the phones.

### Security notes

- Deletion is user-initiated and confirmed; `secure_delete` is on, the deleted rows were vault-
  encrypted anyway; flash still gives no secure-erase guarantee.
- After a deletion, a replay of an old envelope of that contact finds no route; libsignal's spent
  keys stay the protection for contacts that remain (clearing history removes inner-ID duplicates
  only for deleted texts; the envelope digests and libsignal refuse replays).
- A contact can only confuse its own chat's quotes (a reply naming an ID it chose); no other chat,
  no state and nothing outside the display depends on the reference.

---

## DEC-034 — Message order by the sender's time; background connection every 30 seconds

**Status:** ACCEPTED (2026-10-06, the owner's decisions of the same day; written on Opus).

### Context

The owner saw the same conversation in a different order on the two phones: each phone sorted
incoming texts by the time they arrived, which depends on when each phone happened to fetch. And
with the screen locked, new messages were announced only when Android ran the periodic work (15
minutes or more); the owner asked for at least once a minute, every 30 seconds being fine.

### Decision

- **Order.** An incoming text is shown, and sorted, at the time its sender's app put in it (inside
  the encrypted, authenticated payload) if that time lies at most 60 seconds before its arrival
  here; otherwise at its arrival. It is never shown before the contact's previous text here: one
  that came too late (and so shows at its arrival) must not end up below a later one that came in
  time. Our own texts are stored with exactly the time their payload carries, so both phones show
  the same times. Texts with the same time keep the order they were stored in (Room `rowid`), never
  that of their random IDs. The relay cannot touch the time; a contact can move its own text at
  most a minute back and never ahead of its arrival, so it can neither push its chat up the list
  nor place a text in the future. No wire or storage format changes.
- **Background connection.** A foreground service (`V2ConnectionService`, type `dataSync`) runs the
  same sync as the periodic work every 30 seconds while no screen of the app is visible, under the
  same notification rules (DEC-031) and the same protocol lock. Android requires a permanent
  notification for it ("Checking for new messages", no message content, silent). It is **on by
  default** where chats exist and a switch in the settings turns it off; the app asks Android to
  stop optimizing its battery (a button, Android's own question), and explains that some phones
  (Xiaomi, Redmi) also need "Autostart". A partial wake lock keeps the processor awake between
  rounds (renewed each round with a timeout, so it lapses if the loop dies); it starts when the
  chats are shown, after the phone starts, and after an update; it stops for good on a lost key,
  unreadable data or a lost inbox secret, like the periodic work, and when the local data is
  deleted. The service, its receiver and their permissions exist only in debug builds (the debug
  manifest), where v2 exists; release builds have none of them.

### Consequences and limits

- Battery: the processor stays awake while the connection runs, and the radio wakes every 30
  seconds; on mobile data that costs noticeably more than Wi-Fi. The owner accepted this.
- Android limits: targetSdk 34 keeps `dataSync` services without a time limit; with targetSdk 35
  Android allows them six hours a day, so a later SDK update needs another approach (TASK_QUEUE).
  Phone makers' battery managers may still stop the service; the periodic work stays the fallback.
- Still not a timely-delivery guarantee (AGENTS.md): best effort, now usually within half a minute.

### Evidence

`ConversationV2ServiceTest` (both phones show the same order and times; a late first text keeps
its place; the window and the future rule), `NotificationRulesTest` (a muted chat is not announced),
`LocalizationResourcesTest` (the service and its permissions are in the debug manifest only, the
code refuses to run without v2), device runs on both phones. Results: HANDOFF.

---

## DEC-033 — Telegram's look and motion taken over directly (widens DEC-027)

**Status:** ACCEPTED (2026-10-06, the owner's decision in chat).

### Context

DEC-027 allowed reusing Telegram for Android's code under the licensing rules and, as a precaution,
excluded their "name, logo, icons, sounds, fonts or images". Step 6b then judged that almost nothing
needed copying: their screens are custom Java views tied to a huge data model, so our Compose code
was written from measured numbers. The owner now wants the interface to be practically a fork of
Telegram's: the same look, and above all the same effects, swipes and animations, "taken from there,
adapted for our system". They read Telegram's terms as forbidding only the paper-plane logo and the
name; the same was found on Telegram's API terms page on 2026-10-06 (no "Telegram" in an app's name
unless "Unofficial", no logo; nothing about copying the interface, assets or code) and the Android
repository states GPL-2.0 and asks to publish the code. Stickers and features we do not have are not
wanted; "if needed, we will add them ourselves".

### Decision

- **Only Telegram's logo and name stay out.** Icons, sounds, layouts, animations and code may be
  taken. Every file taken from is kept in `THIRD_PARTY_NOTICES.md` with its header status; original
  headers stay; the source stays published (AGPL-3.0). The old rule against copying icons, sounds
  and images is lifted. The mismatch of GPL-2.0 (some files "or later", most without a header) with
  our AGPL-3.0 remains the owner's accepted risk, not a lawyer's opinion.
- **Translate, do not embed.** Their views cannot run in Compose without pulling in their framework
  (`AndroidUtilities`, `Theme`, `LocaleController`, hundreds of classes, native libraries): the app
  would grow by hundreds of thousands of lines of code that nobody here audits, and the paid audit
  (TQ-106) would cost more. So each piece is translated line by line into Compose: the same numbers,
  curves, thresholds and decisions, in a small file of our own (`TelegramMotion.kt` holds them, with
  unit tests that pin them).
- **Work in large blocks, checked by tests and by the owner at the end.** The first blocks: the
  screen stack (open and close 150 ms, 48 dp slide, the swipe back that follows the finger with scrim
  and shadow, `TelegramScreenStack.kt`), the lists (chat rows and messages move in 250 ms, new
  messages grow from 90 percent), the unread counter (220, 150 and 430 ms), the scroll-down button
  with its count, the floating date. Written in the second batch (same day): the font (Source Sans 3
  variable, SIL OFL 1.1, one `.ttf` in `res/font`), the top bar (`TelegramTopBar`), the popup menu
  (`TelegramPopupMenu`: the chat list's round button and the home menu with Settings), Telegram-style
  alert dialogs (`TelegramAlertDialog`), settings as gray ground with white sections, the animated
  light/dark palette (300 ms), Telegram's press effect (black or white ripple), the empty-chat note and the send
  button's 10 to 100 percent scale. Not done: Telegram's icons and sounds (no download was approved;
  our drawn icons stay), a long-press "Copy" on a message (it would put message text on the
  clipboard, which AGENTS.md allows only for the invitation code: needs the owner's decision).
- Planned with this decision (owner agreed): the background sync every 30 seconds through a
  foreground service, and the message order by the sender's authenticated time within one minute.

### Consequences

- The conversation and the chat list now animate; touches are ignored while a screen moves; the
  screen beneath the top one stays composed (hidden, invisible to accessibility) so a swipe shows it
  at once; screens keep their saved state while buried.
- No protocol, wire, storage or security change. Screen security (`FLAG_SECURE`) and the rules of
  DEC-029 to DEC-032 are untouched; `LocalScreenVisible` makes the "seen" mark follow the screen that
  is really on show.

### Evidence

`TelegramMotionTest` (numbers, curves and decisions), `TelegramScreenStackInstrumentedTest` (push,
pop, swipe far, short, vertical, root, switched off), `ConversationMotionInstrumentedTest` (button,
count, floating date), `SystemBackInstrumentedTest` through the new stack. Results: HANDOFF.

---

## DEC-032 — TQ-108: lost inbox secret, screen security, single-use invitations told, digest pruning

**Status:** ACCEPTED (2026-10-06, step 6g; the owner's answers the same day). Closes the second-review
items R6, R7, I2, I5 (`../PHASE5_SECURITY_TEST_PLAN.md`) and the AI-audit items L4, I-4, I-7.

### Decision

- **R6, lost inbox secret.** The read secret of this phone's relay inbox lives in preferences,
  vault-encrypted; the database lives elsewhere. If the secret is missing or damaged while a
  contact, request or invitation exists (they know the inbox), nothing creates a new one: the pipeline
  throws `ReceiveQueueLostException`, the worker stops for good, and the app shows "Your contacts can
  no longer reach this phone" with "Check again" and a confirmed "Delete local data and start over",
  like a lost vault key (DEC-023). With nothing that knows the inbox, a first one is simply created.
  This replaces the plan to create the secret together with the profile: the rule protects exactly
  when a replacement would cost something, and needs no change for existing profiles.
- **R7, screenshots (owner).** The invitation and scanner screens are **not** protected: people may
  screenshot a QR code. In return the owner asked that a used code clearly no longer works (below).
  For the whole app there is a **"Screen security" switch in settings, off by default** (like Signal):
  on, the app's windows carry `FLAG_SECURE` (no screenshots or recording, hidden in recent apps).
- **I2, single use made visible (owner).** An invitation was always single use (DEC-026: one-time
  pre-keys, any other HELLO rejected). Now, once a HELLO used it, the inviter's app also deletes its
  sealed bundle from the relay mailbox (best effort; the bundle holds public keys only), so whoever
  scans the code later is told at once "This invitation no longer works". The inviter's invitation
  screen says when the code shown was used or expired; a scanner still waiting is told on the contact
  page that someone else may have used the invitation first (the only case left: a scan made before
  the inviter's app processed the first answer).
- **I5, digest pruning.** Envelope digests are forgotten after 30 days (the relay keeps envelopes one
  day). A replay of a forgotten envelope is still rejected: libsignal refuses spent message keys and
  pre-keys, and inner message IDs are unique per contact.

### Consequences

- No protocol, wire, invitation or schema change; two new DAO queries; the relay's existing delete
  call is used on the invitation's own mailbox. In the background, digests are pruned with each sync.

### Evidence

`ConversationV2ServiceTest` (a used invitation is withdrawn and a later scan gets "not found"; a relay
failure while withdrawing changes no verdict; forgotten digests let no replayed HELLO, held text or
text back in, nothing changes), `ConversationV2StorageInstrumentedTest` (Room queries),
`ReceiveQueueLossInstrumentedTest` (pipeline, worker, explanation screen, fresh start),
`LegacyResetInstrumentedTest` (a damaged secret is a rejected record, and a lost queue while known),
`ScreenSecurityInstrumentedTest`, `SafetyNumberCheckInstrumentedTest` (waiting hint). Device suites
not yet run (no phone, 2026-10-06).

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
- attachments and media other than voice messages (those are DEC-036; files and photos are wanted
  next, video is not);
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
