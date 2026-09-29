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
2. Session service in `:core:signal-protocol`: per-invitation bundle creation (one-time EC and
   Kyber pre-keys, signed pre-key reuse), session bootstrap from a bundle, encrypt/decrypt
   wrappers that hide libsignal's address-order difference, HELLO sender lookup by the
   PreKey header identity. JVM + device tests; still unused by the app.
3. Invitation v2: QR codec (`smp-invite-v2:`, mailbox ID, key, digest), sealed-bundle codec
   (AES-256-GCM, strict bundle format), version separation; malformed and oversized input tests.
4. Envelope v2 and inner payload codec (HELLO / TEXT / RECEIPT / ACCEPT, inner message IDs,
   routing tag); tamper, downgrade, and cross-protocol tests.
5. Storage: Room v7 (contact protocol version, pending requests, invitations, vault-encrypted
   message bodies, envelope digests), migration, instrumented tests; atomic transaction plus
   process-wide protocol lock (DEC-019 T2) with a fault-injection test.
6. Pipeline integration behind the flag; minimal UI (show v2 invitation, scan, accept/decline
   request); tests mirroring `DeliveryPipelineTest` for the new path.
7. Release-variant check: app depends on `:core:signal-protocol`, desugaring and `_testing`
   exclusion applied in the app module, minified release build verified.

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
