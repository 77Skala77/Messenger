# Phase 5 — Security test pass: inputs, properties, attack matrix, findings

Status: **DONE** (2026-10-01): exit gate met. All suites green; every finding fixed or accepted with
its reasons; the owner checked the build by hand on both phones. The paid external audit is
planned before any release (TQ-106). Plan section: `ai-workflow/REWRITE_PLAN.md`, Phase 5.
Exit gate: the fuzz / replay / tamper suite is green, and every finding below is fixed or explicitly
accepted with its reasoning recorded.

This is an internal test pass written by the same AI assistant that wrote most of the code. It is
**not** the independent review that `AGENTS.md` requires before v2 may be enabled in release.

## 1. Untrusted inputs and what each test must prove

Common property for every decoder: an input is either **rejected with the decoder's own exception
type**, or **accepted as a value that encodes back to exactly that input**. Nothing is half-parsed,
nothing is trusted in a second spelling, and no input causes a different exception, an error, or
unbounded memory use.

| # | Input | Who controls it | Code | Property to prove |
| --- | --- | --- | --- | --- |
| 1 | Invitation text (QR scan, paste) | whoever shows the QR code | `InvitationV2.decodeCode` | Rejected with `InvitationRejectedException`, or exactly the canonical text of one code |
| 2 | Mailbox blobs | the relay; anyone with the QR code (knows the mailbox ID) | `InvitationV2.open` | Only a blob matching the committed digest is opened; a damaged seal is `SEAL_MISMATCH`; nothing else escapes |
| 3 | Sealed bundle plaintext | the inviter (or a QR holder, but the digest binds it) | `InvitationBundleCodec.decode` | Rejected with `InvitationRejectedException`, or re-encodes exactly; keys parse and both signatures verify |
| 4 | Relay HTTP responses | the relay; sender-chosen strings inside items | `RelayHttpTransportClient` | Bounded size; one bad item never blocks the others; nothing crashes the app |
| 5 | Envelope bytes | the relay; anyone who knows the queue ID | `EnvelopeV2Codec.decode` | `MalformedV2Exception`, or exact re-encoding |
| 6 | libsignal ciphertext | the relay; anyone; authenticated only by libsignal | `SignalSessions.readPreKeyHeader`, `.decrypt` | Only `SignalSessionException`; a changed message decrypts to the original plaintext or not at all; failures change no state |
| 7 | Inner payload | an authenticated peer | `InnerPayloadV2Codec.decode` | `MalformedV2Exception`, or exact re-encoding up to padding; names and queue IDs valid |
| 8 | Payload meaning per state | an authenticated peer; the relay (replay, reorder) | `ConversationV2Service` | Wrong-state messages change nothing; receipts only from the right contact; reorders lose nothing |
| 9 | HTTP requests to the relay | anyone on the network | `server/relay` | Only 4xx for bad input; the relay keeps serving |
| 10 | Vault blobs (local) | storage damage | `AndroidKeystoreVaultCipher.decode` / `decrypt` | Only `VaultRecordRejectedException` (DEC-023) |
| 11 | libsignal records (local) | storage damage that passed the vault | `EncryptedSignalProtocolStore.parseThenWipe` | Only `StoredRecordCorruptedException` (a local fault, never a verdict) |
| 12 | Stored queue read secret (local) | damaged preferences | `V2QueueCredentialStore.getOrCreate` | A vault failure type, never a generic exception |

Not fuzzed, with reasons: Room enum and hex columns (written only by the app; a damaged value fails
loudly as a local fault); the relay URL setting (typed by the owner; release rejects `http://`);
outgoing text and names (checked by the encoders, covered by `EnvelopeV2Test`).

## 2. Replay / tamper / reorder / downgrade matrix (v2 pipeline)

The relay double in the JVM tests is the attacker (`FakeRelay`); so is a third party who knows a QR
code or a routing tag. All cases run against real libsignal sessions.

| Attack | Expected result | Test |
| --- | --- | --- |
| Byte-identical re-delivery, also under a new relay ID | `DUPLICATE`, deleted, stored once | `ConversationV2ServiceTest.duplicateAndRelabelledEnvelopesAreRecognisedWithoutDecryption` |
| Changed copies of real envelopes in every routing state (invitation, request, contact, ACCEPT, RECEIPT) | rejected with no change at all, or the original's exact effect | `V2AttackMatrixTest.noChangedEnvelopeIsTrustedOrChangesAnything` |
| Old envelopes (as they were and changed) after the state moved on | `DUPLICATE` / `UNAUTHENTICATED` / `UNROUTABLE`, no change | same test |
| HELLO fails to send once, then a text is written | the text waits; both arrive in order; the text is held for the request | `V2AttackMatrixTest.aFailedHelloHoldsBackLaterMessagesSoNothingOvertakesIt` |
| The relay moves a text before its HELLO | the text is rejected without spending anything; the HELLO pairs | `V2AttackMatrixTest.aTextTheRelayMovesBeforeItsHelloBurnsNothing` |
| Junk flood (garbage, unknown tags, forgeries under a real tag) in front of a real message | all decided and deleted page by page; the real message arrives | `V2AttackMatrixTest.aFloodOfJunkIsDeletedAndTheRealMessageBehindItArrives` |
| A used invitation's private pre-keys | all deleted; later PreKey messages of the session still decrypt | `V2AttackMatrixTest.aUsedInvitationKeepsNoPreKeysAndLaterPreKeyMessagesStillDecrypt` |
| Receipt from another contact, or for an incoming message | ignored, status unchanged | `V2AttackMatrixTest.receiptsCountOnlyFromTheContactTheMessageWentTo` |
| HELLO / ACCEPT / RECEIPT in the wrong state; unparseable authenticated payload | ignored, no state change, session intact | `V2AttackMatrixTest.controlMessagesOutOfPlaceChangeNothing` |
| Other envelope, libsignal, or inner payload versions | rejected or ignored, no change | `V2AttackMatrixTest.otherVersionsAreNeverAccepted` |
| Envelope moved to another contact's routing tag | rejected, no change for anyone | `V2AttackMatrixTest.relabellingAnEnvelopeForAnotherContactIsNeverTrusted` |
| Forged identity claim under a known tag | rejected, contact state unchanged | `ConversationV2ServiceTest.aForgedIdentityClaimUnderAKnownRoutingTagChangesNothing` |
| Second scanner of one invitation | never routed | `ConversationV2ServiceTest.aSecondScannerOfTheSameInvitationIsNeverRouted` |
| HELLO mixing two invitations' pre-keys | rejected, nothing consumed | `V2AttackMatrixTest.aHelloMustUseItsOwnInvitationsKyberKey` |
| Substituted or damaged sealed bundle | `NOT_FOUND` / `SEAL_MISMATCH` | `InvitationV2Test`, `V2DecoderFuzzTest.relayedBlobsAndSealedPlaintextsAreRejectedCleanly` |
| Legacy envelopes, v1/v2 invitation codes, pre-v3 queue IDs | refused | `ConversationV2ServiceTest`, `InvitationV2Test` |
| Late HELLO after expiry | `UNROUTABLE` | `ConversationV2ServiceTest.expiredInvitationsLoseTheirPreKeysAndALateHelloIsUnroutable` |

## 3. Results of the first run (2026-10-01, JVM, before any fix)

16 new tests: 12 green, 4 red. Everything that libsignal and the decoders do with hostile bytes held:
no unexpected exception, every accepted input re-encodes exactly, no changed envelope was trusted
with another effect, and rejected envelopes changed no record at all (libsignal leaves nothing
behind, not even an identity). The four red tests are findings F3, F4, F6, F7 below. Results after
the fixes: section 7.

## 4. Findings

Severity is for this QA-stage app (no real users, debug builds only).

| ID | Finding | Severity | Evidence | Proposed fix |
| --- | --- | --- | --- | --- |
| F1 | The app reads a relay response of any size into memory. The relay returns every pending message at once (up to 1,000 × 64 KB). A full queue can exhaust memory and crash the app, on the low-RAM phone first. | Medium | Code reading (`fetchMessages`, `RelayStore.list`) | Relay returns at most 100 messages per request, oldest first; the app caps the response size and item count. |
| F2 | The relay stores any string as ciphertext. A non-base64 item makes the app's whole fetch throw, so it silently receives nothing until that item expires (up to 7 days). Anyone who knows the queue ID can do it. | Medium | Code reading; test to add | The relay accepts only canonical base64; the app skips (does not trust) any item it cannot parse. |
| F3 | A text that reaches the relay before its HELLO (one failed HELLO send is enough) burns the invitation and is lost; the HELLO then routes nowhere and the pairing never completes, silently. | Medium | Red test | Sender: a contact's envelopes leave in order, a failed one holds back the rest. Receiver: only a HELLO opens an invitation; anything else is rejected and rolled back. (Keeping such texts aside on the relay was considered and dropped: with paged reads, someone holding the QR code could fill the head of the queue with them and block it for a day.) |
| F4 | One invitation has four accepted texts (the base64 tail bits are not checked), although the code claims one canonical text. | Low | Red test | Accept only the canonical text. |
| F5 | Envelopes that fail checks stay on the relay until their TTL (up to 7 days): every sync processes them again, and they fill the 1,000-per-queue limit so real senders get "queue full". Anyone who knows the queue ID can do it. | Medium | Code reading | Delete what can never become valid (malformed, unknown tag, failed authentication, not a valid HELLO). |
| F6 | A HELLO is checked against its invitation's EC pre-key IDs but not its Kyber pre-key ID: someone with two of your QR codes can spend the second invitation's Kyber key through the first one, so the second invitation dies silently. | Low | Red test | After decryption, require that the invitation's own Kyber key was consumed; otherwise roll back. |
| F7 | A damaged stored session record surfaces as libsignal's `InvalidSessionException` instead of `StoredRecordCorruptedException`. Local fault either way. | Low (local) | Red test | Map it in `parseThenWipe`. |
| F8 | The signed pre-key of a used invitation is never deleted. The first message stays forward secret (its one-time keys are deleted), but private key material lingers. | Low | Code reading | Delete all of an invitation's pre-keys when it is used (and clean up leftovers). |
| F9 | A rejected envelope commits whatever the step wrote. Today libsignal writes nothing on failure (proved by the tests), but the guarantee rests on libsignal's internals. | Low (hardening) | Code reading | Roll back the transaction for every rejected envelope. |
| F10 | A damaged stored queue secret (bad base64, wrong length) throws a generic exception instead of a vault failure. Local only. | Low (local) | Code reading | Map to `VaultRecordRejectedException`. |
| F11 | `KEY_CHANGED` is never set by any code path (strict identity trust rejects a changed key before it could be recorded). Not a vulnerability; the docs should say so. | Info | Code reading | Document. |
| F12 | The "stored data cannot be read" screen (DEC-023) and the onboarding screen did not scroll. On a phone in landscape (or with a large font on a small screen) their buttons were cut off, so a user whose vault key was lost could not reach "Delete local data and start over", the only way out. | Medium (availability of a security flow) | Found by the device run: `LocalKeyLossInstrumentedTest` failed on the Redmi lying sideways; screenshot confirmed | Both screens scroll and stay centred while the content fits; the test now runs in a 320 dp window on every phone and fails without the fix. |

## 5. Compatibility (AGENTS.md checklist) for the proposed fixes

- Wire formats (envelope, payload, invitation, sealed bundle): **unchanged**. F4 only refuses texts
  the app never produces.
- Relay API: same routes and JSON; `GET` returns at most 100 messages (oldest first); `POST` refuses
  non-base64 ciphertext with 400. The app never sent such ciphertext, so nothing it does breaks.
- Room schema: unchanged (v9). No migration.
- Behaviour: the app deletes rejected envelopes from its own queue (it already holds the read
  secret); deferred envelopes stay. Rollback of rejected steps changes nothing that a test can
  observe today (F9).
- Key lifecycle (F8): deletes private pre-keys that libsignal never needs again (a later PreKey
  message with the same base key uses the existing session, not the pre-keys).
- Downgrade or cross-protocol risk: none added; the fixes only reject more.

## 6. Owner decisions (2026-10-01, DEC-026)

1. Junk on the relay (F1, F2, F5): **delete what can never become valid, and page the relay**
   (recommended option).
2. **Fix all findings in this phase** (F3–F10; F11 is documentation).
3. Paid external audit by a human expert: **before any release**, not now. Until then release
   builds keep the chat closed (`AGENTS.md`).
4. A second AI review with a fresh context, after the fixes: **yes**. It is a second opinion, not an
   independent audit; its findings are triaged in section 8.

## 7. Results after the fixes (2026-10-01)

Final, after the second review's fixes too: JVM signal-protocol 73 tests (49 before Phase 5),
storage 5, ui 13, domain 3, all green; relay `node --test` 11 (8 before). On SM-S928B and
24048RN6CG each: storage 29/29 (27 before), transport 12/12 (7 before; the hostile-relay tests need
no live relay), signal-protocol 73/73 with the device fuzz budget, ui 24/24 (21 before). The first
device run found F12 on the Redmi, which was lying sideways; the ui suite then passed on both
phones with the Redmi still in landscape. The R2 and R3 tests were also run with their fixes
switched off and failed, as they should; the F12 test likewise without its fix.

New and changed tests:

- `V2DecoderFuzzTest` (signal-protocol, JVM and both phones): envelope, inner payload, invitation
  text, bundle, sealed blobs, PreKey header, session decrypt, damaged stored records, queue and
  random IDs.
- `V2AttackMatrixTest` (signal-protocol): tamper in every routing state, replay after the state
  moved on, ordered sending, relay reorder, receipts, wrong-state control messages, versions,
  relabelling, mixed-invitation HELLO, used-invitation pre-keys, junk flood with paging.
- `LocalVaultCipherTest` (storage, JVM): fuzzed vault blobs.
- `RelayHostileResponseInstrumentedTest` (transport, device): unreadable items, non-list answers,
  oversized, huge and slow answers from an in-test hostile server.
- `RelayHttpTransportInstrumentedTest`: the live relay refuses non-canonical base64.
- `ConversationV2StorageInstrumentedTest`: unsent envelopes keep insertion order.
- `LegacyResetInstrumentedTest`: a damaged stored queue secret is a rejected record.
- `LocalKeyLossInstrumentedTest`: the way out of the key-loss screen is reachable in a short
  window (F12).
- After the second review: `V2AttackMatrixTest` (key IDs above 2^31 on every route, a contact
  spending another invitation, a late HELLO before cleanup, the clock moving back, one failing
  envelope among others), `SignalStoreHostileKeyIdInstrumentedTest` (ui, device: the production
  adapter over Room), `DatabaseDurabilityInstrumentedTest` (storage, device), name checks in
  `EnvelopeV2Test`; the test double now enforces the repository's ID rule.
- Relay: canonical base64 only, oldest page only, hostile requests get a 4xx.
- `ConversationV2ServiceTest`: rejected envelopes are now deleted (DEC-026).

The device budget of the sampled fuzz inputs is a third of the JVM one (the phones run the same
source set); `-Dsmp.fuzz.scale=N` deepens a JVM run.

## 8. Second review (fresh-context AI) — triage

A separate AI agent with a fresh context reviewed the libsignal integration and the pipeline after
the fixes above (read only, 2026-10-01). Not an independent audit, but it found what the first pass
missed: its top finding was confirmed by a test before the fix.

| ID | Finding | Severity | Verdict | Resolution |
| --- | --- | --- | --- | --- |
| R1 | Key IDs in a PreKey message are u32; libsignal passes those above 2^31 to the store as negative ints, the Room repository threw `IllegalArgumentException`, which escaped every layer. One crafted envelope (from anyone with a QR code, a contact, or the relay) stopped the whole receive queue for up to 7 days, and the attacker could repeat it. The first pass missed it: the test double had no ID check, and byte mutations cannot produce 5-byte varints. | High | Confirmed by a red test | Fixed: the store adapter treats negative IDs as unknown keys (a clean rejection); an envelope that reaches no verdict no longer stops the others (it stays on the relay, sending still runs, the failure is reported after); the test double enforces the repository's ID rule; tests with IDs 0x80000000 and 0xFFFFFFFF on every route (JVM) and on the production adapter over Room (device); PreKey forgeries in the flood test. |
| R2 | On contact and request routes any PreKey message reached libsignal, which starts a new session from whatever pre-keys of ours it names: a contact who saw another QR code could spend that invitation silently. | Low/Medium | Confirmed by code; test | Fixed: after a session started, only PreKey messages naming the pre-keys of the pairing's own invitation pass; the scanner's side accepts none. |
| R3 | Expiry was not enforced when routing: a late HELLO was accepted until the cleanup ran. | Low | Confirmed | Fixed: an expired invitation (plus the 10-minute skew) is unroutable; the cleanup now runs before each sync. |
| R4 | Send order followed the wall clock, which can move backwards. | Low | Confirmed | Fixed: insertion order (`rowid`); device test with a backwards timestamp. |
| R5 | A commit might not survive a power loss (WAL with `synchronous` NORMAL), yet the relay copy is deleted after it. | Low | Confirmed on both phones (`journal_mode=wal`, `synchronous=1`) | Fixed: every connection runs `synchronous` FULL (`execPerConnectionSQL`); `DatabaseDurabilityInstrumentedTest` pins it. |
| R6 | If the preferences file holding the receive-queue secret is lost while the database survives, a new queue is created silently and contacts keep sending to the old one. | Low | Confirmed by code | Accepted for now (rare; a proper fix needs its own explanation screen): planned with Phase 6, see `TASK_QUEUE.md`. **Fixed in step 6g (2026-10-06, DEC-032; not yet run on a phone):** a lost or damaged secret while anyone knows the inbox blocks with its own explanation. |
| R7 | The invitation code goes to the clipboard unmarked; no `FLAG_SECURE`. | Low | Confirmed | The clip is marked sensitive, but the owner's hand check showed the Samsung keyboard keeping it in its history anyway. Owner decision: a "Share invitation" button (no clipboard) and "Copy" kept with an on-screen warning, a recorded exception to the `AGENTS.md` clipboard rule (DEC-026); `InvitationSharingInstrumentedTest`. `FLAG_SECURE` on the QR screen stays an owner decision for Phase 6 (it also blocks screenshots people use to share the QR). **Owner, 2026-10-06 (DEC-032):** the invitation screens stay unprotected; a "Screen security" switch for the whole app, off by default; a used invitation clearly no longer works. |
| I1 | A QR holder can also delete the sealed bundle from the mailbox (the code holds its read secret), so the real scanner gets "not found". | Info | Confirmed | Documented (`KNOWN_LIMITATIONS.md`); same power as using the invitation first. |
| I2 | DEC-019 says the second scanner "is told the invitation was used"; not implemented (they wait forever). | Info | Confirmed | Documented as not implemented; `TASK_QUEUE.md`. **Step 6g (DEC-032):** the used invitation is withdrawn from the relay, so a later scan is told at once; a scan made before that gets a hint on the contact page. |
| I3 | The display-name filter let zero-width characters, separators, BOM and tag characters through. | Info | Confirmed | Fixed: checked per code point; format characters (except the zero-width joiner of emoji), separators and unpaired surrogates are refused; tests. |
| I4 | Decoders leave plaintext copies in memory. | Info | Confirmed | Accepted: memory wiping on the JVM is best effort (strings, GC copies); `KNOWN_LIMITATIONS.md` already says plaintext lives in process memory. |
| I5 | `v2_accepted_envelopes` is never pruned. | Info | Confirmed | Accepted for now (about 100 bytes per message); replays would still be rejected by libsignal and inner IDs if pruned: `TASK_QUEUE.md`. **Step 6g (DEC-032):** pruned after 30 days; a JVM test replays every forgotten envelope and nothing changes. |
| I6 | SQLite `secure_delete` was assumed off, so deleted (vault-encrypted) ratchet states could linger in free pages. | Info | Partly confirmed: ON on the Redmi, FAST on the Samsung (FAST leaves traces in free pages) | Fixed: every connection runs `secure_delete` ON; pinned by the same test. Old pages can still sit in the WAL until a checkpoint, and flash keeps old blocks (`KNOWN_LIMITATIONS.md`). |

The review's notes on tests that proved less than they claimed were all addressed: the R1 checks run
on the production adapter too, the late-HELLO test no longer runs the cleanup first, the flood
contains PreKey forgeries, forgeries claim the right identity, mixed pre-keys are tested on contact
routes, and the clock moves backwards in the ordering tests.
