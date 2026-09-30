# Audit Checklist

Updated 2026-09-30 for protocol v2 (DEC-021).

## Protocol And Release Gates

- [ ] Confirm `enable_v2_protocol` is `false` in main (release) resources and that release builds never construct the pipeline, create a libsignal identity, or schedule v2 work.
- [ ] Confirm the legacy prototype (`SimplifiedSessionManager`, `STANDARD_AEAD`, v1 invitations) and `PARANOIA_C4_AEAD` are absent and not reintroduced in any form.
- [ ] Confirm legacy envelopes and v1 invitations are rejected by v2, and the legacy reset runs once.

## Protocol v2

- [ ] Review the store adapter: strict identity trust, one-time Kyber deletion, last-resort reuse rejection.
- [ ] Review invitation v2: one-time pre-keys, digest commitment, seal, strict parsing, expiry.
- [ ] Review the request flow: unaccepted requests never become contacts or get receipts.
- [ ] Confirm untrusted-identity results on receive never change contact state.
- [ ] Confirm each decrypt/encrypt step commits atomically with its stored result, and relay deletion follows the commit.
- [ ] Re-run `ForwardSecrecyTest` and `ConversationV2RoomProofInstrumentedTest`.

## Local Data

- [ ] Confirm message bodies, names, and invitation text are vault-encrypted with row-bound AAD.
- [ ] Confirm protocol ciphertext is not kept after sending and plaintext never reaches the database file.
- [ ] Confirm "delete local data" removes every conversation and libsignal record, the vault key, and the v2 queue ID.
- [ ] Confirm no code path creates a vault key except profile creation, and that a lost, invalidated or replaced key blocks the app without state changes (DEC-023); re-run `VaultKeyLossInstrumentedTest` and `LocalKeyLossInstrumentedTest`.
- [ ] Confirm private keys, pre-keys and ratchet keys never reach the database files in clear, and damaged rows fail closed instead of reading as "not stored".

## Relay Delivery

- [ ] Verify retries, backoff, and WorkManager execution (best effort only).
- [ ] Verify byte-identical re-deliveries are deleted without decryption and no duplicate plaintext appears.
- [ ] Verify receipts only mark TEXT messages sent to the same contact.

## Privacy

- [ ] Confirm logs contain no plaintext, keys, ciphertext, or full queue IDs.
- [ ] Record relay metadata limitations (queue IDs, routing tags, sizes, timing, PreKey identity).

## Debug And Release

- [ ] Confirm the debug build visibly warns when an `http://` relay is configured.
- [ ] Confirm release rejects insecure HTTP relay URLs and cleartext networking.
- [ ] Confirm the debug test reset requires confirmation and keeps only the relay URL.

## Evidence

- [ ] Run Gradle build, unit tests, and lint.
- [ ] Run connected signal-protocol, storage, ui, and transport tests on a device.
- [ ] Run relay Node tests.
- [ ] Record a two-phone manual run (pairing, safety numbers, messages, receipts, relay outage).
- [ ] When troubleshooting, capture only sanitized evidence according to `LOGCAT_QA.md`.
