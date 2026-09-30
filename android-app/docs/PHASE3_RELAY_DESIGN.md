# Phase 3 design — relay authorization

Status: **ACCEPTED 2026-10-01 (DEC-024)**, implementation in progress. Written before any code, as
`AGENTS.md` requires for relay API, invitation and storage-format changes. Owner answers: Q1 = (a)
reset of v2 test conversations with a notice; Q2 = one QR secret with HKDF-derived keys; Q3 = relay
maximum TTL stays 7 days.

## 1. Current state (read from the code)

Relay (`server/relay/src`): `POST /v1/messages {messageId?, queueId, expiresAtEpochMillis,
opaqueCiphertext}`, `GET /v1/messages/{queueId}`, `DELETE /v1/messages/{messageId}`, in-memory
store, max TTL 7 days, max body 2 MB, no authentication, no limits.

Problems:

| # | Problem | Who can exploit it | Effect |
| --- | --- | --- | --- |
| P1 | The queue ID is both the address and the read capability | Every contact (they receive my queue ID in the invitation bundle and HELLO), anyone who saw my QR, the relay operator, anyone on the path of plain HTTP | Read my pending envelopes (ciphertext only, plus metadata) and **delete** them: silent loss of delivery |
| P2 | `DELETE` takes only a message ID, not bound to a queue | Anyone who learned a message ID (they are listed by `GET`) | Delete any message of any queue |
| P3 | The client chooses `messageId`; `put` overwrites | Any client | Overwrite someone else's pending envelope, in any queue |
| P4 | No rate or size limits | Anyone | Flood a queue or the relay |

Confidentiality and authenticity of message content are not affected: envelopes are end-to-end
encrypted and authenticated (v2); the receiver deduplicates by envelope digest (DEC-019/020). The
damage is **availability and metadata**.

## 2. Proposed design

### 2.1 Self-certifying queues (fixes P1)

- The owner of a queue picks a random 32-byte **read secret** `R`.
- Queue ID `Q = "q3-" + base64url(SHA-256("smp-relay-queue-v1\0" || R))` (46 characters).
- Anyone who knows `Q` can **send** to it; only who knows `R` can **read or delete**: requests
  carry `Authorization: Bearer base64url(R)`, the relay recomputes the hash and compares in
  constant time.
- The relay keeps **no per-queue state or token table**: nothing to register, nothing lost on a
  relay restart, no enumeration of queues. A wrong secret and an empty queue are not
  distinguishable beyond "forbidden", and the relay learns only `Q` (as today).
- Contacts only ever learn `Q`, never `R`.

### 2.2 Queue-bound deletion and server message IDs (fixes P2, P3)

- `POST /v1/queues/{Q}/messages {expiresAtEpochMillis, opaqueCiphertext}` → `201 {messageId}`. The
  relay assigns a random 128-bit `messageId`; a client-supplied one is rejected.
- `GET /v1/queues/{Q}/messages` (Bearer `R`) → pending envelopes with their `messageId`.
- `DELETE /v1/queues/{Q}/messages/{messageId}` (Bearer `R`) deletes only a message of that queue.
- A resend after a lost response may create a duplicate on the relay; the receiver already
  recognizes byte-identical envelopes by digest without decrypting (unchanged).

### 2.3 Limits (P4; QA-level, not production)

- Body ≤ 64 KB (envelopes and sealed invitation bundles are a few KB).
- Per IP: token bucket for `POST` and for `GET`/`DELETE` (e.g. 60 per minute, bursts of 20);
  `429` on excess.
- Per queue: at most 1,000 pending envelopes; `POST` beyond that gets `429`.
- Relay-side maximum TTL stays 7 days (owner, Q3). The client sends 24 hours.
- The client treats `429` like any temporary relay failure (existing retry with backoff).

### 2.4 Invitation v3 (the QR changes)

The scanner must read the inviter's invitation mailbox, so the QR must carry that mailbox's read
secret instead of its ID.

- `smp-invite-v2:` → **`smp-invite-v3:`**; v2 codes are refused with "unsupported version", never
  reinterpreted.
- Proposed body (question Q2): version byte + 32-byte **invitation secret** `S` + 32-byte digest
  of the sealed bundle. From `S`, HKDF-SHA256 derives the seal key (`info
  "smp-invite-v3/seal-key"`) and the mailbox read secret (`info "smp-invite-v3/mailbox-read"`);
  the mailbox queue ID follows from the read secret as in 2.1. QR text ≈ 102 characters (today
  122). The sealed bundle, its digest commitment, one-time pre-keys, expiry and the request flow
  are unchanged.
- Whoever sees the QR can read (and delete) that one mailbox, which holds only the sealed bundle:
  the same exposure as today, bounded by the one-time use and 24-hour expiry.
- The bundle's `receiveQueueId` and HELLO's queue field carry `Q` of the new form.

### 2.5 Client storage of the read secret

The own receive queue's `R` is a bearer credential: it must not sit in plain preferences
(`AGENTS.md`). It is stored vault-encrypted (AAD `relay-queue-read-secret-v1`) in the existing
preferences file; the wipe removes it; with a lost vault key it is unreadable like everything else
(DEC-023). The invitation mailbox secret needs no new storage: it is derived from the QR text,
which is already vault-encrypted in `v2_invitations`. No Room schema change.

## 3. Compatibility, migration, rollback (`AGENTS.md` checklist)

1. **Old/new formats:** relay routes `/v1/messages...` → `/v1/queues/{Q}/messages...` with Bearer
   read secrets; queue IDs `v2-<uuid>` / `inv2-...` → `q3-<hash>`; invitation `smp-invite-v2:` →
   `smp-invite-v3:`. Envelope (`SME2`), inner payload (`SMI2`), libsignal sessions and Room tables
   are unchanged.
2. **Backward compatibility:** none on the wire. An old app cannot talk to the new relay and vice
   versa; v2 invitations are refused explicitly. Only test data exists (debug builds; release has
   no chat).
3. **Migration (question Q1):** existing v2 contacts know each other's *old* queue IDs, which have
   no read secret. Either (a) one explicit reset of v2 conversations with a one-time notice, then
   re-pair (the DEC-021 pattern), or (b) an in-band "my new queue" message over the existing
   sessions, with the relay serving old queues unauthenticated during a transition (keeps P1 open
   meanwhile, more protocol surface).
4. **Downgrade / cross-protocol risks:** the version prefix and the `q3-` queue form are checked
   strictly; a v2 code is never read as v3; the relay does not accept the old routes at all, so
   nothing silently falls back to unauthenticated reads.
5. **Effect on local data:** with (a), v2 contacts, sessions, messages and invitations are deleted
   once; profile and vault key stay. With (b), contacts survive.
6. **Tests:** relay tests for missing/wrong/other-queue bearer (403), cross-queue delete refused,
   client-supplied `messageId` refused, limits (413/429); invitation v3 codec and v2 refusal; client
   transport against the live relay on both phones; end-to-end pairing and messaging on two phones.

## 4. Threat review (what changes for the relay's view)

- The relay still learns: IPs, `Q` values, timing, sizes, and the initiator's identity key in
  PreKey headers (unchanged, documented). It additionally sees `R` on reads: a relay operator can
  already read and delete everything it stores, so this adds no power over what it has.
- Plain HTTP (debug, `adb reverse`) exposes `R` on the path; release requires HTTPS (unchanged
  policy). A leaked `R` allows reading ciphertext and deleting pending envelopes of that queue,
  never decrypting them.
- Contacts can no longer read or delete my queue; a malicious contact can still flood it, bounded
  by the limits.
