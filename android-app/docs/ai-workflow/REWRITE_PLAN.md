# Rewrite Plan — Secure Messenger: Real Crypto Core, Then Telegram-Style UI

Status: APPROVED. Phase 0 DONE (2026-09-27). Phase 1 next.
Created: 2026-09-27
Owner decisions: see "Decisions" at the bottom.

## Why this file exists

This is a standalone execution plan, independent of the existing `TASK_QUEUE.md` /
`CURRENT_PROJECT_STATE.md` pair (those two are already stale relative to git history —
see the audit note at the end of this file). This plan is the source of truth for the
crypto-core rewrite and the later UI rewrite. Hand this file back in a future session
and say "follow REWRITE_PLAN.md" — that's enough context to resume.

## Guiding principles

1. **No home-grown cryptography.** Real Double Ratchet + PQXDH via `libsignal`
   (already pinned at `0.94.1` in `gradle/libs.versions.toml`, currently gated off).
   No cascade ciphers, no extra "paranoia" layers on top of a reviewed protocol.
   `PARANOIA_C4_AEAD` gets deleted, not finished — it adds attack surface without
   provable benefit.
2. **Security before polish.** UI/UX is the last phase, not the first. A pretty chat
   screen over a weak protocol is worse than an ugly screen over a strong one.
3. **Each phase has an exit gate.** No phase is "done" without the tests/checks
   listed for it passing. No phase starts before the previous one's gate passes.
4. **Reuse what's already good:** module boundaries, Room encrypted-at-rest pattern,
   Keystore usage, delivery-pipeline retry/receipt logic, existing test suites.
   Replace only what's actually weak: the session/crypto core, the relay's lack of
   auth, and eventually the UI.
5. **Fail closed, always.** Every new trust/approval state follows the same rule
   already used for device approval: unknown or ambiguous state blocks sending,
   never allows it by default.

## Phase 0 — Baseline and cleanup (before touching crypto)

Goal: stop building on a moving, self-contradictory foundation.

- Reconcile `CURRENT_PROJECT_STATE.md` and `TASK_QUEUE.md` against actual git HEAD
  (they currently describe a HEAD several commits behind reality).
- Decide fate of `:core:signal-protocol` and `:core:signalstore` modules: keep and
  finish (recommended — they're the intended landing spot for this work) vs. delete
  and rebuild clean. Recommendation: keep, they're already structured correctly.
- Remove `PARANOIA_C4_AEAD` and its module boundary entirely (dead weight, no path
  to "secure" without primitives nobody is going to review).
- Freeze the current `STANDARD_AEAD` prototype path as read-only/legacy: existing
  local test conversations must not silently break, but no new work goes into
  `SimplifiedSessionManager`.

Exit gate: one clean, current status doc; explicit decision recorded on module fate;
old prototype path frozen and marked legacy in code comments.

## Phase 1 — Real protocol core (X3DH/PQXDH + Double Ratchet)

Goal: replace `SimplifiedSessionManager` with actual `libsignal` sessions.

**Blocker found at Phase 1 start (2026-09-27):** `libsignal-client` 0.94.1 declares
Kotlin stdlib 2.2.20, while the project compiles with Kotlin 1.9.24. Kotlin 1.9 cannot
read Kotlin 2.2 class metadata, so no Kotlin code can call libsignal types until the
toolchain is upgraded (see `docs/LIBSIGNAL_TOOLCHAIN_REVIEW.md`). Phase 1 therefore
starts with a toolchain upgrade, in this order:

| Step | Work | Recommended model / effort |
| --- | --- | --- |
| 1a | Install JDK 17 + Android SDK; record a passing baseline build of the current code | Sonnet 5 · Medium |
| 1b | Toolchain upgrade: Kotlin 2.2, Compose compiler plugin, AGP/Gradle, coroutines, Room KAPT→KSP — separate commits | Opus 5.5 · High while blind; Sonnet 5 · High once a build loop exists |
| 1c | libsignal packaging: native `.so` for 4 ABIs, resource exclusions, R8 keep rules | Sonnet 5 · High |
| 1d | Signal stores backed by real libsignal record types, encrypted in Room | Opus 5.5 · High |
| 1e | PQXDH prekey bundle, invitation v2, session setup, Double Ratchet in the delivery pipeline | Opus 5.5 · Extra |
| 1f | Explicit legacy-conversation reset + tests (forward secrecy, two-device) | Opus 5.5 · High |

Owner initially deferred 1a (toolchain install), so 1b and 1d were written "blind" and
marked unverified. 1a was completed on 2026-09-29 and 1b/1d were then verified (below).
The Phase 1 exit gate still requires the remaining steps to pass on a real build and test
run.

Note: model recommendations in the table above were written for "Sonnet 5"; the owner now
uses Sonnet 5.5 for the "Sonnet" rows. Re-evaluate the "Opus" rows before each step.

Progress:
- 1a — **DONE** (2026-09-29): JDK 17 (Temurin 17.0.20), Android SDK (platform 34,
  build-tools 34.0.0), Node.js v24.19.0 installed; physical device SM-S928B (Android 16,
  arm64-v8a) available for instrumented tests. Baseline is the 1b build below.
- 1b — **DONE, debug build verified** (2026-09-29): Kotlin 2.2.20, Compose compiler plugin,
  coroutines 1.10.2, Room 2.7.2 (KAPT kept), `kotlinOptions`→`compilerOptions`. AGP,
  Gradle, compileSdk, and Compose BOM unchanged. `:app:assembleDebug`, lint (0 errors),
  and 105 unit tests pass. The minified release (R8) build is **not** verified. Details:
  `docs/LIBSIGNAL_TOOLCHAIN_REVIEW.md`, "Toolchain upgrade (step 1b)".
- 1c — **PARTLY DONE** (2026-09-29): libsignal's JNI library verified on a physical device
  (7 instrumented tests, real PQXDH/Double Ratchet); 4 ABIs packaged, `_testing` libraries
  excluded, core library desugaring enabled (required by the AAR). **Open:** R8 keep rules in
  `core/signal-protocol/consumer-rules.pro` are unverified — needs a minified release build
  that actually contains libsignal, which requires an app module to depend on
  `:core:signal-protocol` (not done; falls under 1e when the pipeline is wired) plus the same
  desugaring and `_testing` exclusion in that app module. Details:
  `docs/LIBSIGNAL_TOOLCHAIN_REVIEW.md`.
- 1d — **DONE, verified** (2026-09-29): `EncryptedSignalProtocolStore` (libsignal store
  interfaces over the encrypted repository), `LocalSignalIdentityProvisioner`, blocking store
  boundaries, Room v6 + `MIGRATION_5_6`. JVM tests with real PQXDH sessions (10 tests
  across signal-protocol and signalstore) and 12 storage instrumented tests on a physical
  device (including the 5→6 migration) pass. Semantics recorded in `DECISIONS.md` DEC-018.
  Still disabled: nothing in the app uses the adapter.
- 1e — **preparation done, not started** (2026-09-29): coupling map, open design decisions
  D1–D9 (notably invitation QR size with a Kyber-1024 key, plaintext-at-rest, atomic ratchet
  persistence), and proposed sub-steps are in `PHASE_1E_PREP.md`.

- Turn on `OfficialSignalDoubleRatchetGate.MIGRATION_ENABLED` behind a feature flag.
  Legacy prototype conversations are reset explicitly when the new protocol ships
  (owner decision, see "Decisions") — never silently reinterpreted as new sessions,
  per the project's own compatibility rules.
- Implement the four `Signal*StoreBoundary` interfaces in `:core:signalstore` against
  real `libsignal` record types (identity, session, prekey, signed prekey, Kyber
  prekey for PQXDH). Records stay encrypted at rest via the existing Keystore vault
  pattern — that part of the current design is correct, keep it.
- Real invitation/prekey bundle exchange to replace the current single-static-key
  QR invite: initial bundle needs identity key, signed prekey, one-time prekeys,
  and a Kyber prekey for PQXDH. QR payload format changes — version it explicitly
  (`smp-invite-v2:`), never silently reinterpret v1.
- Wire `SessionCipher` encrypt/decrypt through the existing delivery pipeline
  (`DeliveryPipeline.kt` logic mostly stays — it already does
  authenticate-before-store, replay-marker-before-delete correctly; only the
  session/cipher underneath changes).
- Identity-change / safety-number handling: real Double Ratchet still needs the
  same "warn on key change" UX the project already has — port it, don't lose it.

Exit gate: two-device integration test exchanges messages through real
`SessionCipher`, forward secrecy verified (compromising a later message key does
not decrypt earlier messages — write a test that proves this), safety-number
verification still works, the explicit legacy-conversation reset is tested (old
prototype session state is cleared on purpose and can never be decrypted or accepted
as a new-protocol session), and all of this is proven by a real build and test run on
an installed toolchain — not by code review alone.

## Phase 2 — Storage hardening

Goal: make sure the new protocol state is stored at least as safely as before.

- Room migration for the new signal record tables (schema is already v5 with
  placeholder tables for this — write the real migration, not just `CREATE TABLE`).
- Confirm no protocol secret (root key, chain key, message key, one-time prekey
  private material) ever lands in a non-encrypted column, log line, or diagnostics
  report — same rule the project already enforces, just re-verify against the new
  code paths specifically.
- Decide and document key-loss behavior: what happens if the Keystore key is
  invalidated (biometric enrollment change, factory state reset) — currently
  undocumented for the new store, needs an explicit "fails closed, conversation
  unrecoverable, user is told plainly" answer.

Exit gate: migration test from v5 with existing prototype data intact; a
deliberately-corrupted/missing Keystore key test that proves the app fails closed
instead of crashing into an inconsistent state or silently dropping to plaintext.

## Phase 3 — Transport / relay hardening

Goal: the relay stops being a trivially abusable open queue.

- Per-queue auth: a bearer token or keyed capability issued at queue creation,
  required for GET/DELETE on that queue. Today anyone who learns a `queueId` can
  read and delete it — cheap to fix now, expensive to fix once a real deployment
  exists.
- Server-assigned `messageId` (stop trusting a client-supplied id — today a client
  can overwrite another record by reusing an id).
- Basic rate limiting per queue/IP on the relay to blunt trivial flooding (this is
  still explicitly *not* a production relay — just enough to stop the cheapest
  abuse during testing).
- Re-confirm the "relay is untrusted" invariant still holds after Phase 1: the
  relay must not be able to learn anything from the new prekey-bundle exchange
  beyond what it already can't learn today (ciphertext, timing, size, queue id).

Exit gate: relay tests cover rejecting a request with a missing/wrong queue token
and rejecting a client-supplied duplicate `messageId`.

## Phase 4 — Multi-device and revocation, done properly

Goal: replace "primary-device-only, block everything else" with real fan-out.

- Multiple devices per contact each get their own Double Ratchet session (this is
  how Signal actually does it — no shared session across devices).
- Revocation becomes a signed, distributable statement other devices/contacts can
  verify, not just a local `REVOKED` row that only blocks local sending.
- Keep the existing fail-closed defaults for pending/unknown states — this part of
  the current design doesn't need to change, only the fan-out mechanism around it.

Exit gate: a contact with two approved devices receives on both; revoking one
device stops delivery to it without needing the other side to also revoke first
(still local-trust-model correct, but multi-device correct too).

## Phase 5 — Security test pass

Goal: earn the right to call this "reviewed", not just "written".

- Wire-format fuzz tests for invitation/prekey-bundle decode (malformed input must
  reject cleanly, never crash, never partially trust).
- Replay/tamper/downgrade test matrix specifically for the new protocol path
  (mirror what already exists for the old path in `DeliveryPipelineTest.kt`).
- If budget allows: one paid external review of the `libsignal` integration layer
  specifically (not the whole app) before calling Phase 1–4 "done" rather than
  "implemented".

Exit gate: fuzz/replay/tamper suite green; review findings triaged (fixed or
explicitly accepted with reasoning recorded).

## Phase 6 — UI/UX rewrite (Telegram-style)

Goal: only after Phases 0–5 have a passing exit gate.

- Chat list screen: avatars, last message preview, unread count, timestamp —
  standard Telegram-style list.
- Conversation screen: message bubbles (sent/received visually distinct), grouped
  by sender/time, delivery ticks (sent/delivered — read receipts only if actually
  implemented, never fake the tick).
- Contact/security screen simplified: safety number check surfaced clearly but
  without needing a manual to understand it.
- Light/dark theme, standard Material 3 but Telegram-familiar layout conventions.
- Onboarding/QR flows get a visual pass, but the underlying v2 invite/bundle format
  from Phase 1 stays unchanged — this phase is visual only, not protocol-touching.

Exit gate: usability pass — someone who's never seen the app can send a first
message within a minute of opening it, no explanation needed.

## Phase 7 — Release readiness (only if you want to actually ship it)

- Dependency locking / SBOM, reproducible release build, signing key management.
- Production relay design (TLS, real hosting, no more "temporary HTTP by IP").
- Full independent security audit of the whole app, not just the protocol layer.

This phase is explicitly out of scope until you decide you want this to be a real
shipped product rather than a serious personal/portfolio project. No need to plan
it in detail now.

## Tracked side tasks (do not lose)

- **TQ-009 — dependency / AGP updates (51 lint warnings)** — owner request 2026-09-29.
  Schedule: after the Phase 1 exit gate, before Phase 6 (newer Compose is what the UI
  rewrite will build on). Full description and constraints: `TASK_QUEUE.md` TQ-009. Do not
  mix it into protocol work.
- Minified release build with libsignal (R8 keep rules, desugaring, `_testing` exclusion in
  the app module) — verify as part of 1e, see step 1c above.

## Decisions (answered by the owner 2026-09-27)

1. **Legacy prototype conversations: RESET.** When Phase 1 ships, existing
   `STANDARD_AEAD`/`SimplifiedSessionManager` conversations are not migrated — the app
   performs a clean, explicit reset to the new protocol (pre-release, test data only,
   no real users). No dual-protocol coexistence code is built. The reset must be
   explicit and fail-closed: never silently reinterpret an old session as a new one.
2. **Library: official `libsignal`, AGPL-3.0 accepted.** The owner accepts that
   distributing the app means publishing its source under AGPL-3.0. `vodozemac`
   (Apache-2.0, no PQXDH) was considered and not chosen. This clears the licensing
   blocker listed in `OfficialSignalDoubleRatchetGate.BLOCKERS`; the other blockers
   (durable encrypted store, prekey/PQXDH exchange, independent audit) remain.
3. **PQXDH from day one** (not objected to; `libsignal` 0.94.1 supports Kyber prekeys).
4. **JDK/Android SDK installation: LATER** (superseded 2026-09-29: installed, step 1a
   done). Phase 1 code was written before a local toolchain existed, so nothing was
   reported as compiled or tested until then. The Phase 1 exit gate still requires a real
   build and test run for every remaining step.
5. Timeline expectation: this is genuinely months of focused work for a solo
   effort done carefully, not weeks — no date estimate is given.

---

## Audit note on existing workflow docs (found during planning, not yet fixed)

`CURRENT_PROJECT_STATE.md` currently records GitHub HEAD as `c974e460b8...` and
lists TQ-002 as the next task. Actual `main` HEAD as of this plan's creation is
`527c5ae` ("Close TQ-004 documentation reconciliation"), and `TASK_QUEUE.md` marks
TQ-004 as DONE while `CURRENT_PROJECT_STATE.md` doesn't reflect that integration
happened at all. Recommend folding this reconciliation into Phase 0 rather than
fixing it separately — no sense doing it twice.
