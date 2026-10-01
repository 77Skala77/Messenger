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
- 1e — **DONE** (2026-09-30): design approved (DEC-019); legacy relay message-ID fix
  (DEC-020); sub-steps 2–5 verified on JVM and SM-S928B (session wrapper, invitation v2,
  envelope/payload v2, Room v7 storage with atomic transactions); 6a pipeline with end-to-end
  JVM tests; 6b UI verified by hand on two physical phones; 7 release build with R8 succeeds.
  Status per sub-step: `PHASE_1E_PREP.md`.
- 1f — **DONE** (2026-09-30, owner decisions DEC-021). 1f-A proofs: forward secrecy,
  post-compromise security and the skipped-key boundary (`ForwardSecrecyTest`, JVM and device),
  Room-backed two-device and plaintext-at-rest tests (`ConversationV2RoomProofInstrumentedTest`),
  libsignal under R8 verified by hand with the `qaMinified` build. 1f-B: the legacy prototype,
  `:core:crypto`, `:core:delivery` and the spikes are deleted; `MIGRATION_7_8` drops the legacy
  tables and `LegacyReset` removes the legacy Keystore key and preferences once, with a one-time
  notice (tested on device); v2 is the only chat path in debug builds, with WorkManager
  background sync; release builds show a "not for messaging" screen
  (`enable_v2_protocol = false`). Upgrade from the 6b build, the one-time notice, messaging, and
  background delivery with the receiving app closed were verified by hand on both phones.
  Protocol summary: `../PROTOCOL_V2.md`.

**Phase 1 exit gate — status 2026-09-30: MET.** Every item below was proven by real builds and
test runs on two physical phones (evidence: `PHASE_1E_PREP.md`, "Step 1f evidence").

The original Phase 1 work list follows for reference; it is done, and the names it mentions
(`OfficialSignalDoubleRatchetGate`, `DeliveryPipeline.kt`) no longer exist.

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

Already done in Phase 1 (2026-09-30): real migrations 5→6 (libsignal records), 6→7 (v2
tables) and 7→8 (legacy tables dropped, DEC-021), each with an instrumented test; a
plaintext-at-rest test over the database files. Because legacy data is now reset on purpose,
the exit gate below means "v5 prototype data is dropped cleanly while profile, libsignal and v2
rows survive", not "kept intact".

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

**Status 2026-09-30: DONE, exit gate met** (DEC-023; owner chose "block and explain, the user
decides" for key loss).

- Key loss: the vault key is created only with a new profile; encrypt/decrypt never create one.
  The profile row is the canary: a missing, invalidated, or replaced key, or a damaged canary,
  makes the data `UNREADABLE`; the app then runs nothing and shows a screen with "Check again"
  and a confirmed "Delete local data and start over" (no automatic deletion). The worker returns
  failure instead of retrying. The wipe deletes the key first and needs no key.
- Found and fixed on the way: (1) the old vault silently created a new key when its key was
  missing, splitting data across two keys; (2) libsignal 0.94.1 wraps a *checked* exception from a
  store callback in an `AssertionError`, which `catch (Exception)` misses, so a vault failure
  inside a libsignal callback would have crashed the chat's polling loop — every vault failure is
  now unchecked (`VaultException`) and a JVM test pins the libsignal behavior; (3) a stored record
  that authenticates but does not parse could be taken for a bad message — now
  `StoredRecordCorruptedException`, never a verdict; (4) a truncated vault blob surfaced as
  `EOFException` (a "temporary" failure) — now a rejected record.
- Secrets at rest: a device test takes the identity private key, signed/one-time EC pre-keys, the
  Kyber secret key, and every 32-byte key inside the session records (root, chain, message, and
  private ratchet keys) from the store and proves none of them appears in the database files.
  Logs: `SecurityLoggingPolicyTest` scans every production source for logging or printing (the
  check that lived in the removed `:core:crypto`). There is no diagnostics export.
- Exit gate: migration v5 with prototype data → v8 (`SecureDatabaseMigrationInstrumentedTest`);
  missing, replaced, and damaged key and rows on the real Keystore (`VaultKeyLossInstrumentedTest`);
  the pipeline, the worker, and the screen on a device (`LocalKeyLossInstrumentedTest`); key loss
  inside libsignal callbacks (`ConversationV2ServiceTest`).
- Runs on 2026-09-30 (AGP 8.13.2): unit tests all green (signal-protocol 48, storage 4, ui 13,
  signalstore 3); on SM-S928B and 24048RN6CG each: storage 26/26, ui 19/19, signal-protocol
  48/48, transport 5/5 (live relay over `adb reverse`); `lintDebug` 0 errors; release build with
  R8. The first device run found two test-side problems, both fixed: an old vault test relied on
  encrypt creating the key (now forbidden), and the source-scanning logging test cannot run on a
  device (moved to the ui JVM tests).

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

**Status 2026-10-01: DONE, exit gate met** (DEC-024; design `../PHASE3_RELAY_DESIGN.md`; owner
chose: reset of v2 test chats with a notice, one QR secret with HKDF, relay TTL stays 7 days).

- Relay API v3: self-certifying queue IDs (`q3-` + hash of a 32-byte read secret); anyone sends,
  only the read secret reads or deletes (Bearer); the relay assigns message IDs and refuses a
  client-supplied one; deletion is bound to the queue; limits: 64 KB body, per-IP token buckets,
  1,000 pending per queue. The relay also starts directly on Windows now (`node src/server.mjs`).
- App: `RelayQueueCredentials` (shared test vector with the relay), transport API with queue
  credentials, invitation `smp-invite-v3:` (one secret, HKDF-derived seal key and mailbox secret,
  about 100 characters, v2 codes refused), the own queue's read secret stored vault-encrypted,
  Room v9 (`MIGRATION_8_9` resets the v2 test conversations; profile and local identity kept),
  one-time notice.
- Evidence (2026-10-01): relay tests 8/8; unit tests green (domain 3, signal-protocol 49, storage 4,
  ui 13); on SM-S928B and 24048RN6CG each: storage 27/27 (incl. migration 8→9), ui 21/21 (reset,
  encrypted secret), signal-protocol 49/49, transport 7/7 against the live relay (incl. 403 for a
  foreign or missing secret and cross-queue deletion); lint 0 errors; release with R8 (v2 closed);
  the owner checked the upgrade, the new QR, pairing, messages, and restart by hand on both phones.

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

**Status 2026-10-01: DEFERRED by the owner (DEC-025).** Version 1 is one device per person. The
draft design with the owner's answers (own key per device + primary-signed list, confirmation for
a contact's new device, "Delivered" from any device) is in `../PHASE4_MULTIDEVICE_DESIGN.md`. The
plan continues with Phase 5. Note: the text above was written for the removed prototype; the
draft design describes the v2 starting point.

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

**Status 2026-10-01: DONE, exit gate met** (DEC-026; inputs, matrix, findings and results:
`../PHASE5_SECURITY_TEST_PLAN.md`). The paid external audit was moved by the owner to before any
release (TQ-106); until then release builds keep the chat closed. Owner chose: delete junk and page the relay, fix all findings
now, a paid external audit before any release (not now), and a second AI review with a fresh
context.

- Fuzz and attack suites written first; their first run held for libsignal and every decoder and
  turned four findings red. Twelve findings in total (F1–F12), all fixed or documented: junk could
  stall or crash a receiver (relay pages and canonical base64, bounded transport, rejected envelopes
  deleted and rolled back); a text could overtake its HELLO and burn the invitation (ordered
  sending, only a HELLO opens an invitation); a HELLO could borrow another invitation's Kyber key;
  used invitations kept their signed pre-key; four spellings of one QR text; two local damage cases
  had the wrong failure type; the key-loss screen cut off its only way out in landscape (found by
  the device run).
- Second review (fresh-context AI): 7 findings and 6 notes, triaged in the plan's section 8. The
  top one (R1, High) was real and confirmed by a red test: a PreKey message naming a key ID above
  2^31 threw out of the store and could stall the whole receive queue; fixed together with R2–R5,
  R7 (partly), I3 and I6; R6, I2, I5 and `FLAG_SECURE` are queued (TQ-108).
- Evidence (2026-10-01): JVM signal-protocol 73 (49 before), storage 5, ui 13, domain 3; relay 11
  (8 before); on SM-S928B and 24048RN6CG each: storage 29/29, transport 12/12, signal-protocol
  73/73, ui 24/24; `lintDebug` 0 errors (16 warnings, unchanged since TQ-009); release build with
  R8 (0 warnings, 357.6 MB, `enable_v2_protocol` false in the APK).
- The owner's hand check on both phones (2026-10-01): upgrade, messages, restart, and the relay
  switched off and on (messages queued during the outage arrived in order). It showed the Samsung
  keyboard keeping a copied invitation code in its history despite the sensitive flag; owner
  decision: add "Share invitation" and keep "Copy" with a warning (DEC-026).

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
