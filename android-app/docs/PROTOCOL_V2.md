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
   exact sealed bytes), about 100 characters. Only that one canonical text is accepted;
   `smp-invite-v2:` codes are refused.
3. The scanner derives the mailbox read secret, fetches the mailbox, keeps only a blob matching the digest, opens it, validates it
   strictly (exact key sizes, signatures), starts a PQXDH session, stores the inviter as a contact
   "waiting for acceptance", and sends a HELLO carrying its own receive queue and routing tag.
4. The inviter's device turns the HELLO into a **contact request** (never a contact) showing the
   claimed name as unverified and the 60-digit safety number. A person accepts (with a local name)
   or declines. Accepting sends ACCEPT; declining deletes the request, its session and identity.

Only a HELLO that uses exactly the invitation's pre-keys (one-time EC, signed, and one-time Kyber)
opens it; any other message under the invitation's routing tag is rejected without spending
anything (DEC-026). Once used, all of the invitation's private pre-keys are deleted, and the
inviter's app withdraws the sealed bundle from the mailbox (best effort, DEC-032), so a later scan
is told at once that the invitation no longer works.

A photographed QR code can be used by a third party, but only once (one-time pre-keys): the
inviter then sees a request from an unknown identity and must not accept it without comparing the
safety number. The relay cannot substitute the bundle (digest) or read it (seal key).

## Messages and receipts

- Envelope (relay-visible): `SME2`, version, recipient-assigned routing tag, libsignal message
  type, ciphertext. No sender identifier; no trial decryption.
- Inner payload (encrypted): `SMI2`, type (HELLO / ACCEPT / TEXT / RECEIPT / TEXT_REPLY / VOICE / PLAYED / DELETE), sender
  message ID, sent-at time, body; padded to 160-byte blocks. TEXT_REPLY (type 5, DEC-035) is a TEXT
  followed by the 16-byte inner message ID of the text it answers (either direction, same chat);
  only the reference travels, each phone shows the quote from its own copy. A plain TEXT is type 3
  exactly as before; an app that predates type 5 drops a reply after authenticating it.
- Voice messages (DEC-036): the recording (AAC-LC in ADTS frames, made in memory, at most 15
  minutes) never travels in the session. It is cut into 47 KiB pieces (the last padded with zeros,
  at most 96), each encrypted with AES-256-GCM under a fresh 32-byte key for this recording only
  (nonce: four zero bytes and the 64-bit index; associated data: `"smp-voice-piece-v1\0"`, the
  number of pieces and the index), sent as `"SMM1"`, version 1, 16-bit index, ciphertext and tag to
  a fresh one-time relay queue. The note, inner payload type 6 (VOICE), carries that queue's read
  secret, the key, the SHA-256 digest of the padded recording (`"smp-voice-recording-v1\0"`), the
  size, the number of pieces, the length, at most 100 loudness values of 0 to 31, the codec (1), and
  0 or 1 and the inner ID of the message it answers. The note's envelope waits until every piece is
  up (and the contact's later envelopes behind it). The receiver keeps only pieces that decrypt
  under the note's key at their own place, deletes everything it fetched from that queue after its
  commit, and receipts the note only once the whole recording is here and matches the digest:
  "Delivered" means the recording arrived. A recording still incomplete 25 hours after its note is
  marked unavailable without a receipt. An app that predates type 6 drops the note after
  authenticating it. The note's options byte marks a one-time recording (bit 0): the receiving phone
  gives it once, deleting its pieces in the same transaction, and answers with PLAYED (type 7: the
  16-byte inner ID, a receipt's layout), on which the sender deletes its own copy and shows "Played";
  a PLAYED naming anything but our own fully uploaded one-time recording changes nothing (DEC-036).
- Delete for both (type 8, DEC-037): a count (1 to 100) and that many distinct inner message IDs of
  the chat, either direction, never the request's own; the receiving phone deletes those texts and
  voice messages of that chat only, and ignores anything else (and any request from a pending
  contact request).
- An incoming text is shown and sorted at its sender's time if that lies at most 60 seconds before
  its arrival, else at its arrival, and never before the contact's previous text; ties keep the
  stored order (DEC-034). The relay cannot change that time; a contact can move its own text at most
  a minute back, never ahead of its arrival.
- Local only, never sent (DEC-035): pin, mute, "mark as read", clearing a chat's history (texts still
  waiting to be sent stay), and deleting a contact with its session and identity; afterwards its
  envelopes find no route and are deleted unread.
- "Delivered" is set only by an authenticated RECEIPT naming the inner message ID, from the same
  contact the message was sent to. Relay message IDs are never trusted (DEC-020).
- Each protocol step (decrypt + store, encrypt + enqueue) runs under one process-wide lock and one
  Room transaction together with the libsignal store writes; byte-identical re-deliveries are
  recognised by digest without decryption. Digests are forgotten after 30 days (DEC-032); a replay of
  an older envelope is still rejected by libsignal and the inner message IDs (tested). "Delete local data" and the profile check before a
  pipeline is built take the same lock, so no step commits during a wipe and a wipe never leaves a
  new identity behind (`V2PipelineWipeInstrumentedTest`).
- Relay deletion (DEC-026): an accepted envelope is deleted after its transaction committed; a
  rejected one (malformed, unknown routing tag, failed authentication, not a valid HELLO) commits
  nothing (rolled back) and is deleted too, since it can never become valid. Only a local failure
  keeps an envelope on the relay. The relay returns at most the 100 oldest messages per fetch; a sync
  reads up to 10 pages. The transport bounds every answer (8 MB, 200 messages, 30 s) and skips any
  item it cannot parse.
- Sending is ordered per contact (insertion order, not timestamps): an envelope that failed holds
  back the contact's later ones, so a text never overtakes its HELLO.
- Once a session exists, the only PreKey messages accepted are the peer's first ones, naming the
  pre-keys of the invitation this pairing used; the scanner's side accepts none. Key IDs above
  2^31 (which libsignal hands over as negative numbers) are unknown keys. An expired invitation is
  unroutable even before its cleanup ran.
- An envelope that reaches no verdict (a storage failure, an unexpected error) stays on the relay
  without stopping the rest of the queue; sending still runs and the failure is reported after.
  Behind it, a rejection of an envelope with the same routing tag is not final in that sync: such
  an envelope (for example a text behind its HELLO) stays on the relay for the next sync instead
  of being deleted, at most 10 per sync, so a local failure cannot lose the next message (audit
  V2-01, DEC-028).
- libsignal checks a PreKey message's *claimed* identity before authenticating it, so an
  untrusted-identity result on receive is treated as unauthenticated and never changes contact
  state (otherwise the relay could block contacts with forged envelopes).

## Storage and keys

- Message bodies, names, and the QR text are stored vault-encrypted; protocol ciphertext is not
  kept after sending. Stored history is **not** protected by forward secrecy (a device compromise
  that includes the vault key reveals stored history, as in Signal).
- The libsignal identity private key is a software key, encrypted at rest with the Keystore key,
  present in app memory while in use.
- Room schema v10. The legacy prototype tables were dropped by `MIGRATION_7_8`; its Keystore key and
  preferences are removed once by `LegacyReset` (DEC-021). `MIGRATION_8_9` reset the v2 test
  conversations for relay API v3 (DEC-024). `MIGRATION_9_10` only added two local columns (a "seen"
  mark per message and a verified date per contact, DEC-029); the protocol does not use them.
- Inbox loss (DEC-032): if the inbox read secret is missing or damaged while contacts, requests or
  invitations exist, no new inbox is created; the app stops and explains it like a lost key.
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
| Every decoder of untrusted bytes rejects cleanly or accepts only the exact canonical encoding; libsignal raises only its protocol exceptions on hostile ciphertext (Phase 5 fuzzing) | `V2DecoderFuzzTest`, `LocalVaultCipherTest` |
| A changed envelope is never trusted with another effect; a rejected one changes no record at all; replays after the state moved on change nothing | `V2AttackMatrixTest` |
| Receipts only from the right contact; wrong-state control messages, other versions, relabelling, and mixed-invitation HELLOs change nothing | `V2AttackMatrixTest` |
| Junk cannot stall or crash the receiver; a hostile relay answer is bounded and cannot block the other items | `V2AttackMatrixTest`, `RelayHostileResponseInstrumentedTest`, relay tests |

## Not done / limits

- No independent review: the Phase 5 test pass, a second AI review and two AI audits (2026-10,
  DEC-028) are internal; a paid external audit is required before release (DEC-026). Release
  builds have no chat.
- Trust on first use on the accepting side; safety-number comparison is manual. This is the main
  practical risk: whoever sees an invitation QR (a photo, a screenshot) can pair first, or delete
  the invitation from the relay. Since step 6f (DEC-030) accepting goes through a comparison step
  (optional but explicit) and a local mark records a confirmed match for exactly the number shown;
  the mark is never sent and the protocol does not use it.
- Single device per contact (Phase 4). Revocation is not a protocol message.
- `KEY_CHANGED` exists as a contact state but nothing sets it today: strict identity trust rejects a
  changed key before it could be recorded, and an untrusted-identity result never changes state.
- Anyone who knows a queue ID can still send junk faster than the receiver deletes it, within the
  QA relay's per-IP limits; a relay that reorders a text before its HELLO causes that text to be
  rejected (the invitation survives).
- Relay queues are self-certifying (DEC-024): only the holder of a queue's read secret can read or
  delete it; contacts learn only the queue ID; the relay assigns message IDs and applies QA-level
  limits (per address, per queue, and for the whole relay, DEC-028). It still sees metadata: queue
  IDs, routing tags, sizes, timing, the initiator's identity key in PreKey headers.
- The app does not follow HTTP redirects from the relay (DEC-028).
- Background sync is best effort (WorkManager, about 15 minutes or later).
