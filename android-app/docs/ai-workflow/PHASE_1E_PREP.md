# Phase 1e preparation brief — libsignal sessions in the delivery pipeline

Status: sub-step 1 (design, threat review, owner approvals) DONE 2026-09-29 — see
`DECISIONS.md` DEC-019 and DEC-020. No v2 protocol code exists yet. Written 2026-09-29 from the
repository state at commit `a60cf66`; the coupling map below is derived from current code.
Authoritative plan: `REWRITE_PLAN.md`. Scope of 1e there: "PQXDH prekey bundle, invitation v2,
session setup, Double Ratchet in the delivery pipeline". Step 1f (explicit legacy reset and
forward-secrecy / two-device tests) follows.

## What is already in place (verified 2026-09-29)

- `:core:signal-protocol` — `EncryptedSignalProtocolStore` (libsignal store interfaces over the
  encrypted Room records), `LocalSignalIdentityProvisioner`. Real PQXDH + Double Ratchet
  round trips pass in JVM tests and on device (SM-S928B). Not used by the app.
- Room v6 with encrypted Signal record tables; storage instrumented tests pass on device.
- libsignal JNI verified on device; desugaring enabled; `_testing` libs excluded.
- Flags: `OfficialSignalDoubleRatchetGate.MIGRATION_ENABLED = false`; `PARANOIA_C4_AEAD` gone.
- Not verified: minified release build containing libsignal (R8 keep rules).

## Coupling map — what 1e has to change

| Area | Current code | Why it is not a drop-in swap |
| --- | --- | --- |
| Session abstraction | `SessionManager` / `SimplifiedSessionManager` (`core/crypto`), `EstablishedSession` (static `rootKey`, `nextSendingNumber`) | libsignal sessions are stateful ratchet records in the store; there is no root key or counter to hold. |
| Wire message | `EncryptedSessionMessage` (message number, timestamp bucket, AAD, `CiphertextPackage`), `SessionMessageCodec` | libsignal ciphertext (`PreKeySignalMessage` / `SignalMessage`) has its own format. A new, versioned envelope is needed so v1 and v2 are never confused. |
| Outgoing | `OutgoingDeliveryProcessor` stores an `EncryptedSessionMessage`, retries the same blob | Encrypt once at enqueue, persist the ciphertext, retry the identical bytes. Still compatible with a ratchet, but the stored type and the encrypt-then-persist transaction change. |
| Incoming | `IncomingDeliveryProcessor.processFetchedDetailed` tries `decryptStoredMessage` against **every** binding until one authenticates | Trial decryption against several ratchet sessions is unsafe/unsound. Needs a sender hint in the envelope, then decrypt with exactly one session. |
| History display | `SecureMessengerViewModel.kt:540` re-decrypts stored ciphertext (`decryptStoredMessage(..., entity.outgoing)`) | A ratchet deletes message keys; stored ciphertext cannot be decrypted again, for either direction. Plaintext must be kept encrypted at rest under a vault key. |
| Persistence | `DeliveryPersistence` (`acceptIncoming`, `acceptDeliveryReceipt`, `queueOutgoingControlMessage`) typed on `EstablishedSession` / `EncryptedSessionMessage` | Signature and Room schema change (likely v7). Replay marker and receipt handling must be re-expressed. |
| Contact / identity | `DevicePublicIdentity` = X.509 EC P-256 key + fingerprint; `Contact`, `ContactDevice`, trust states | libsignal identity is a Curve25519 `IdentityKey`. Fingerprint / safety-number source changes (libsignal has a numeric fingerprint API). Fail-closed device states must be preserved. |
| Invitation | `InvitationCodec` `smp-invite-v1:` (queue id, device id, EC key); experimental v2 DTO in `:core:signalstore` uses placeholders | v2 needs a real bundle (see decision D7). v1 must never be silently reinterpreted. |
| UI | `SecureMessengerViewModel` (701 lines), `SecureMessengerApp` (1073 lines), `InvitationImportValidation`, `RelayBackgroundSync` | Only the minimum wiring in 1e; visual work is Phase 6. |

## Design decisions — RESOLVED 2026-09-29

The proposals D1–D9 below were the input to the owner decision. **The accepted design is
DEC-019** in `DECISIONS.md`; where they differ, DEC-019 wins. Main differences: D5 uses a
recipient-assigned routing tag, not a sender device ID (DEC-019 T3); D7 chose option (c) with an
encrypted, committed bundle (O1, T8); D8 uses one-time keys only, no last-resort key for
invitations (T5); D9 became one-sided scanning plus an explicit contact request (O2), not
reciprocal scanning. D2 is refined by O3: legacy code coexists temporarily behind the flag and
is deleted in 1f; no conversation ever uses both protocols.

Original proposals, kept for history:

- **D1 Feature flag and approval.** `AGENTS.md` forbids enabling Signal sessions for normal chats
  without explicit approval, flag isolation, tests, migration review, threat review, and
  independent review. Proposal: new sessions behind a default-OFF flag; owner approval recorded
  in `DECISIONS.md` (DEC-019) before any wiring; flag stays off until the 1f gate passes.
- **D2 No coexistence.** Per owner decision 1 in `REWRITE_PLAN.md`, no dual-protocol code: the
  pipeline moves to libsignal, legacy conversations are reset explicitly in 1f. Until then the
  legacy path stays as it is (frozen).
- **D3 Plaintext at rest.** Store message plaintext as vault-encrypted records (Keystore-backed
  AEAD, same pattern as the profile vault), never in plaintext columns. Room schema change,
  migration, and doc updates (the docs currently say Room stores ciphertext).
- **D4 Atomic persistence.** Decrypting advances the ratchet. Session update, plaintext record,
  and replay marker must commit in one Room transaction **before** the relay message is
  deleted; a crash mid-way must be safe. The current libsignal store adapter writes per record
  (DEC-018), so a transaction wrapper is required. Needs a test that kills the process between
  steps (or a fault-injection test).
- **D5 Envelope v2.** Version prefix distinct from v1, carries a sender hint (sender device id)
  outside the authenticated payload, rejects v1 blobs on v2 sessions and vice versa. Relay sees
  only opaque bytes plus the existing routing fields.
- **D6 Identity mapping.** Keep `deviceId` as the routing/trust key; add the libsignal identity
  key to the contact device record; keep `PENDING_APPROVAL` / `REVOKED` / `KEY_CHANGED` /
  missing-primary fail-closed; map libsignal identity change to `KEY_CHANGED` and block sending.
- **D7 Invitation size (open risk, needs measurement).** A PQXDH bundle contains a Kyber-1024
  public key (1568 bytes) plus signature and EC keys, roughly 1.9 KB raw and about 2.5 KB after
  base64 — close to the limit of a single scannable QR code, which would be very dense. These
  sizes are estimates, to be measured. Options: (a) dense single QR, (b) multi-frame QR,
  (c) QR carries identity + queue + a hash, bundle fetched via the untrusted relay and verified
  against the hash, (d) manual/text import only for v2. Owner decision needed after measuring.
- **D8 Prekeys.** One-time prekey per invitation; when consumed, fall back to the last-resort
  Kyber prekey (already supported, DEC-018). Replenishment policy to be defined.
- **D9 Reciprocal pairing.** The current flow exchanges two invitations and requires
  reciprocal confirmation before sending. Define who initiates the PQXDH session and keep the
  "not ready until confirmed" rule.

## Sub-steps inside 1e (each verified by a real build before the next)

1. **DONE 2026-09-29** — threat review, DEC-019, owner approvals. Legacy relay-message-ID patch
   (DEC-020) as a separate commit.
2. **DONE 2026-09-30** — `InvitationPreKeyFactory` (per-invitation signed, one-time EC and
   one-time Kyber pre-keys) and `SignalSessions` (start/encrypt/decrypt by local contact ID,
   typed failures, PreKey header reading). 10 tests, JVM and SM-S928B.
3. **DONE 2026-09-30** — `InvitationV2`: 122-character QR text (mailbox ID, seal key, digest),
   sealed bundle (AES-256-GCM), strict bundle codec with early signature checks. 6 tests.
4. **DONE 2026-09-30** — `EnvelopeV2Codec` (routing tag, libsignal type, digest) and
   `InnerPayloadV2Codec` (HELLO / ACCEPT / TEXT / RECEIPT, inner message IDs, 160-byte padding,
   safe display names). 6 tests.
5. **DONE 2026-09-30** — `ConversationV2Store` / `ProtocolTransactionRunner` boundaries, Room v7
   (`v2_*` tables, `MIGRATION_6_7`), `EncryptedConversationV2Repository`,
   `RoomProtocolTransactionRunner`. 9 new instrumented tests (22 storage tests in total) on
   SM-S928B, including rollback of libsignal and v2 rows together and migration 6→7.
6. Pipeline and UI:
   - 6a **written 2026-09-30** — `ConversationV2Service` (invitation, scan, request,
     accept/decline, text, receipts, retries, expiry) under a process-wide lock and one
     transaction per step. 12 end-to-end JVM tests with real libsignal and an attacking relay
     double (duplicates, relabelling, forged identity claims, second scanner, crash rollback).
   - 6b **DONE 2026-09-30** — minimal UI behind the hidden debug QA switch (off by default,
     impossible in release): invitation QR/text, scan or paste, contact requests with claimed
     name and safety number, explicit accept/decline, chat with delivery status. Background sync
     for v2 is **not implemented** (messages are fetched only while the v2 screen is open).
     **Manually verified on two physical phones** (Samsung S24 Ultra SM-S928B and Redmi A3x
     24048RN6CG, both Android 16) through a local relay over `adb reverse`: pairing with
     matching safety numbers, request/accept, messages in both directions, `Delivered`
     receipts, app restart (state kept), repeated and own invitation refused, switch off/on,
     local data deletion clears v2 contacts, and a relay outage: messages stayed
     `Waiting for sync`, all were delivered after the relay returned, exactly once each.
     Not covered by this manual run: two-device forward-secrecy proof and legacy reset (1f),
     WorkManager background sync for v2, and a public relay.
7. **DONE 2026-09-30 (build level; runtime of the minified build UNVERIFIED)** — release-variant
   check. `:app:assembleRelease` (minified, R8) succeeds in ~34 min on the 3.9 GB machine; the
   APK is **unsigned** (357.5 MB, 4 ABIs). The app module now enables core library desugaring
   and excludes `libsignal_jni_testing.so`; the first release build also showed that
   libsignal-client's jar carries **desktop natives as Java resources** (`*.dll`, `*.dylib`,
   including `*_testing`, ~153 MB) — excluded via `packaging.resources.excludes`, none remain.
   In `mapping.txt` the libsignal keep rules are applied (`-keep class org.signal.libsignal.**`
   from `consumer-rules.pro`, plus libsignal's own JAR rules) and only D8/R8 synthetic classes
   (lambdas, `-IA`) are renamed or removed. R8 prints "error occurred when parsing kotlin
   metadata" warnings (AGP 8.5.2 predates Kotlin 2.2; tracked in TQ-009). **Runtime under R8
   verified in step 1f (2026-09-30):** the `qaMinified` build type (same R8 rules as release,
   not debuggable, debug-signed, `.qa` suffix, arm64 only, 96 MB) was installed on both
   phones; pairing, safety numbers, messages in both directions, and `Delivered` receipts
   worked by hand. `mapping.txt`: no non-synthetic libsignal class renamed; app classes are
   obfuscated, so R8 really ran.

## Constraints that apply (from `AGENTS.md`)

- Never weaken fail-closed behavior, replay tracking, or receipt semantics ("Delivered" only
  after an authenticated receipt).
- Never log keys, sessions, raw payloads, or full queue ids.
- Describe old/new formats, compatibility, migration/rollback, downgrade and cross-protocol
  risks before changing any wire or storage format.
- Final report must state: active chat path changed; `SIGNAL_BACKED_SESSIONS_ENABLED` value;
  `OfficialSignalDoubleRatchetGate.MIGRATION_ENABLED` value; official libsignal active in normal
  chats; normal v1 invitation flow changed; `PARANOIA_C4_AEAD` reintroduced (must be no).

## Recommended working mode

Model: Opus 5.5, effort Extra for sub-steps 1–5 (protocol, atomicity, and wire-format
reasoning); Sonnet 5.5 · High is enough for the mechanical part of sub-step 6 and for docs.
Machine note: 3.9 GB RAM — build with `-Dorg.gradle.jvmargs=-Xmx1280m
-Pkotlin.compiler.execution.strategy=in-process --max-workers=1` if the Gradle daemon crashes.
