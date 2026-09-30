# Protocol v2 — current design (libsignal PQXDH + Double Ratchet)

Status: **ACTIVE in debug builds only** (since 2026-09-30, DEC-021). Release builds contain the
code but never construct it and show a "not for messaging" screen until the independent review of
Phase 5. **Not independently reviewed. Not for real sensitive communications.**

This file describes what the repository implements now. Design rationale and the threat review:
`ai-workflow/DECISIONS.md` DEC-018 (store adapter), DEC-019 (design), DEC-020 (relay message IDs),
DEC-021 (legacy removal). Implementation history: `ai-workflow/PHASE_1E_PREP.md`.

## Building blocks

| Part | Where | What it does |
| --- | --- | --- |
| libsignal 0.94.1 | `libs.versions.toml` | PQXDH (Kyber-1024) session setup, Double Ratchet, identity keys, safety numbers. No home-grown cryptography for messages. |
| Store adapter | `:core:signal-protocol` `EncryptedSignalProtocolStore` | libsignal store interfaces over encrypted records; strict identity trust in both directions; one-time Kyber keys deleted on use. |
| Encrypted records | `:core:storage` `EncryptedSignalStoreRepository`, `EncryptedConversationV2Repository` | Room rows encrypted with the Android Keystore AES-GCM vault key, AAD bound to the row. |
| Session wrapper | `SignalSessions` | Start/encrypt/decrypt by local contact ID; typed failures; safety number. |
| Invitation | `InvitationPreKeyFactory`, `InvitationV2` | Per-invitation pre-keys; small QR code committing to a sealed bundle on the relay. |
| Wire formats | `EnvelopeV2Codec`, `InnerPayloadV2Codec` | Relay envelope and encrypted inner payload. |
| Pipeline | `ConversationV2Service` | Pairing, requests, messages, receipts, retries, invitation expiry. |
| App | `:core:ui` | Screens, `ConversationV2ViewModel`, `V2SyncWorker` (WorkManager), `LegacyReset`. |

## Invitation and pairing (one-sided scan, explicit request)

1. The inviter creates a fresh invitation: its own signed pre-key, one-time EC pre-key and
   one-time Kyber-1024 pre-key (no last-resort key), a random routing tag, and a random 32-byte
   invitation secret. HKDF-SHA256 derives from the secret the AES-256-GCM seal key and the read
   secret of the relay mailbox (DEC-024). The bundle is sealed and uploaded to that mailbox.
   Lifetime: 24 hours.
2. The QR code is `smp-invite-v3:` + base64url(version, invitation secret, SHA-256 digest of the
   exact sealed bytes), about 100 characters. `smp-invite-v2:` codes are refused.
3. The scanner derives the mailbox read secret, fetches the mailbox, keeps only a blob matching the digest, opens it, validates it
   strictly (exact key sizes, signatures), starts a PQXDH session, stores the inviter as a contact
   "waiting for acceptance", and sends a HELLO carrying its own receive queue and routing tag.
4. The inviter's device turns the HELLO into a **contact request** (never a contact) showing the
   claimed name as unverified and the 60-digit safety number. A person accepts (with a local name)
   or declines. Accepting sends ACCEPT; declining deletes the request, its session and identity.

A photographed QR code can be used by a third party, but only once (one-time pre-keys): the
inviter then sees a request from an unknown identity and must not accept it without comparing the
safety number. The relay cannot substitute the bundle (digest) or read it (seal key).

## Messages and receipts

- Envelope (relay-visible): `SME2`, version, recipient-assigned routing tag, libsignal message
  type, ciphertext. No sender identifier; no trial decryption.
- Inner payload (encrypted): `SMI2`, type (HELLO / ACCEPT / TEXT / RECEIPT), sender message ID,
  sent-at time, body; padded to 160-byte blocks.
- "Delivered" is set only by an authenticated RECEIPT naming the inner message ID, from the same
  contact the message was sent to. Relay message IDs are never trusted (DEC-020).
- Each protocol step (decrypt + store, encrypt + enqueue) runs under one process-wide lock and one
  Room transaction together with the libsignal store writes; the relay copy is deleted only after
  commit; byte-identical re-deliveries are recognised by digest without decryption. "Delete local
  data" and the profile check before a pipeline is built take the same lock, so no step commits
  during a wipe and a wipe never leaves a new identity behind (`V2PipelineWipeInstrumentedTest`).
- libsignal checks a PreKey message's *claimed* identity before authenticating it, so an
  untrusted-identity result on receive is treated as unauthenticated and never changes contact
  state (otherwise the relay could block contacts with forged envelopes).

## Storage and keys

- Message bodies, names, and the QR text are stored vault-encrypted; protocol ciphertext is not
  kept after sending. Stored history is **not** protected by forward secrecy (a device compromise
  that includes the vault key reveals stored history, as in Signal).
- The libsignal identity private key is a software key, encrypted at rest with the Keystore key,
  present in app memory while in use.
- Room schema v8. The legacy prototype tables were dropped by `MIGRATION_7_8`; its Keystore key and
  preferences are removed once by `LegacyReset` (DEC-021).
- Key loss (DEC-023): the Keystore vault key is created only together with a new profile, never
  by encrypt or decrypt. The profile row is the canary: if it no longer decrypts (key missing,
  invalidated or replaced, or the row damaged), the app runs nothing, explains that the stored
  data cannot be read, and offers only "Check again" and a confirmed "Delete local data and start
  over". A storage failure is never treated as a protocol verdict.

## Verified properties (tests, 2026-09-30)

| Property | Evidence |
| --- | --- |
| Forward secrecy: a full state theft after reading cannot decrypt what was read | `ForwardSecrecyTest` (JVM, both phones), `ConversationV2RoomProofInstrumentedTest` (stolen Room image incl. vault key) |
| Post-compromise security: one round trip after a theft locks a passive thief out | `ForwardSecrecyTest` |
| Honest boundary: a message not yet received at theft time is readable by the thief | `ForwardSecrecyTest` |
| Relay cannot substitute bundles, relabel IDs, forge receipts, or block contacts via identity claims | `InvitationV2Test`, `ConversationV2ServiceTest`, `DeliveryPipeline` history (DEC-020) |
| Crash between decrypt and store loses nothing | `ConversationV2ServiceTest`, `ConversationV2StorageInstrumentedTest` |
| Two devices over real Room, restart, plaintext never in the database file | `ConversationV2RoomProofInstrumentedTest` |
| Legacy envelopes and v1 invitations are never accepted; legacy data is dropped | `ConversationV2ServiceTest`, `SecureDatabaseMigrationInstrumentedTest`, `LegacyResetInstrumentedTest` |
| libsignal works under R8 | manual run of the `qaMinified` build on two phones |
| Private keys, pre-keys (EC, Kyber) and ratchet keys never reach the database files in clear | `ConversationV2RoomProofInstrumentedTest` (Phase 2) |
| A lost, replaced or damaged vault key fails closed: no new key, no state change, envelope kept on the relay, user told | `VaultKeyLossInstrumentedTest`, `LocalKeyLossInstrumentedTest`, `ConversationV2ServiceTest` (DEC-023) |
| No production source logs or prints | `SecurityLoggingPolicyTest` |

## Not done / limits

- No independent review (Phase 5). Release builds have no chat.
- Trust on first use on the accepting side; safety-number comparison is manual.
- Single device per contact (Phase 4). Revocation is not a protocol message.
- Relay queues are self-certifying (DEC-024): only the holder of a queue's read secret can read or
  delete it; contacts learn only the queue ID; the relay assigns message IDs and applies QA-level
  limits. It still sees metadata: queue IDs,
  routing tags, sizes, timing, the initiator's identity key in PreKey headers.
- Background sync is best effort (WorkManager, about 15 minutes or later).
