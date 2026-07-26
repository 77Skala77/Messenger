# Security Checklist — Secure Messenger MVP

Use this checklist before finishing any security-sensitive repository change.

This checklist supplements `../../AGENTS.md`. It does not replace the project threat model, design documents, tests, or independent review.

Mark every applicable item. For an item that is not applicable, record `N/A` and a short reason. Do not mark an item complete without evidence.

## 0. Scope and evidence

- [ ] The exact task and changed-file scope are stated.
- [ ] The current branch and HEAD are known.
- [ ] The complete diff was reviewed.
- [ ] Unrelated changes are absent or explicitly reported.
- [ ] Current code, feature flags, configuration, manifests, resources, tests, and CI were treated as stronger evidence than documentation or historical chat reports.
- [ ] Lost local commits, screenshots, handoff notes, and historical extracts were not treated as proof that implementation exists.
- [ ] Every affected capability is classified accurately as active, implemented, disabled, experimental, planned, historical, unverified, or not implemented.
- [ ] No feature is called implemented solely because an interface, DTO, placeholder, test fixture, dependency gate, source guard, or document exists.
- [ ] Security-sensitive assumptions and unresolved contradictions are listed explicitly.

## 1. Project status and security claims

- [ ] The MVP warning remains accurate.
- [ ] The change does not claim production-grade security.
- [ ] The change does not claim readiness for real sensitive communications.
- [ ] The change does not claim official Signal interoperability without verified evidence.
- [ ] Historical QA is not represented as current infrastructure verification.
- [ ] A historical VPS endpoint is not represented as currently reachable, secure, or production-ready without fresh verification.
- [ ] `Delivered` is not described as a read receipt.
- [ ] WorkManager is not described as a timely-delivery guarantee.
- [ ] Reset or deletion controls are not described as guaranteed secure erase.
- [ ] Metadata exposure and remaining limitations are not hidden or minimized.

## 2. Active cryptography and session path

- [ ] Normal chats still use the intended current session implementation unless a reviewed migration was explicitly approved.
- [ ] `STANDARD_AEAD` remains the only active payload encryption suite unless an approved design says otherwise.
- [ ] AES-GCM behavior was not weakened.
- [ ] Key length, authentication tag length, nonce format, and AAD binding were not weakened.
- [ ] Cryptographically secure randomness was not replaced with ordinary randomness.
- [ ] Nonce allocation cannot repeat under restart, retry, concurrency, restore, or failure conditions.
- [ ] Durable counters are updated atomically where required.
- [ ] HKDF inputs, salt, context, labels, and output use remain domain-separated and unambiguous.
- [ ] Per-message key derivation was not weakened or reused unsafely.
- [ ] Authentication is verified before plaintext is accepted or stored.
- [ ] Authentication failure rejects the payload and does not continue partially.
- [ ] No plaintext fallback, compatibility fallback, debug bypass, or downgrade path was added.
- [ ] The prototype session is not described as providing Signal, PQXDH, Double Ratchet, forward secrecy, or post-compromise recovery.

## 3. Keys, secrets, and secure storage boundaries

- [ ] Private keys are never logged, printed, copied, or exposed.
- [ ] Session keys are never logged, printed, copied, or exposed.
- [ ] Shared secrets are never logged, printed, copied, or exposed.
- [ ] Authentication and bearer tokens are never logged, printed, copied, or exposed.
- [ ] QR pairing secrets and raw pairing payloads are never logged, printed, copied, or exposed.
- [ ] Raw protocol records are never exposed in diagnostics, test output, or fixtures.
- [ ] Required secret persistence uses an existing reviewed boundary such as Android Keystore, the Keystore-backed vault, or an approved encrypted-record repository.
- [ ] Secrets were not duplicated into plaintext Room columns, ordinary preferences, unprotected DataStore values, temporary files, clipboard content, screenshots, crash reports, or committed fixtures.
- [ ] Key ownership and access scope remain correct.
- [ ] Key generation and import failure fail closed.
- [ ] Key invalidation, device lock changes, reinstall, restore, and unavailable-Keystore behavior were considered.
- [ ] Backup and restore do not silently export or resurrect protected key material.
- [ ] Reset and deletion behavior do not make unsupported erasure claims.
- [ ] New persisted cryptographic material has documented lifecycle, deletion, migration, and failure semantics.

## 4. Logging, diagnostics, and support output

- [ ] Logs are safe to share.
- [ ] Copied diagnostics are safe to share.
- [ ] Plaintext message content is absent.
- [ ] Keys, tokens, nonces, raw ciphertext, raw envelopes, raw protocol records, and QR payloads are absent.
- [ ] Full queue identifiers are replaced with short fingerprints where possible.
- [ ] Sensitive contact data is removed or explicitly sanitized.
- [ ] Event names and state labels do not leak secret material.
- [ ] Retry counters, timestamps, and fingerprints cannot be combined into an avoidable high-risk disclosure.
- [ ] Crash, worker, storage, camera, and network errors are redacted.
- [ ] Debug-only diagnostics remain unavailable or appropriately restricted in release builds.
- [ ] Test failures do not print sensitive fixtures or decrypted content.

## 5. Relay trust and transport

- [ ] The relay remains untrusted.
- [ ] Confidentiality does not depend on relay honesty.
- [ ] Authenticity does not depend on relay honesty.
- [ ] Replay safety does not depend on relay honesty.
- [ ] Device trust does not depend on relay honesty.
- [ ] Relay acceptance is not displayed as true delivery.
- [ ] The client safely handles relay drop, replay, reorder, delay, duplication, malformed payloads, stale blobs, and lies about availability.
- [ ] Send, fetch, and delete failures do not corrupt local state.
- [ ] A relay deletion failure does not cause unsafe acceptance or silent state loss.
- [ ] Queue identifiers, timing, payload size, and source-IP metadata exposure are not understated.
- [ ] Relay API route or identifier changes have compatibility and migration review.
- [ ] HTTP remains debug/QA-only.
- [ ] Release builds still reject cleartext relay URLs and disallow unintended cleartext networking.
- [ ] TLS, certificate, redirect, proxy, timeout, and network-security policy changes were reviewed when transport changed.
- [ ] Public relay or VPS configuration is not assumed current without fresh evidence.

## 6. Envelope, replay, receipt, and delivery safety

- [ ] Envelope version and required fields are validated before use.
- [ ] AAD binds the intended sender, recipient, conversation, message type, version, and identifiers where required by the design.
- [ ] Unknown or unsupported message types fail safely.
- [ ] Malformed envelopes are rejected without partial state changes.
- [ ] Duplicate and replayed payloads are suppressed or handled safely.
- [ ] Replay markers are persisted atomically before relay deletion where required.
- [ ] Crash or restart between acceptance, persistence, receipt creation, and relay deletion was considered.
- [ ] Tampered ciphertext, AAD, receipts, and identifiers are rejected.
- [ ] Delivery receipts are authenticated control payloads.
- [ ] Receipt processing cannot create visible chat messages.
- [ ] A forged or mismatched receipt cannot mark an unrelated message delivered.
- [ ] `Sent to delivery server` still means relay acceptance only.
- [ ] `Delivered` requires sender-side validation of an authenticated receiver receipt.
- [ ] Retry does not allocate unsafe nonce or key state.
- [ ] Retry does not lose, duplicate, or silently reorder durable outgoing state.
- [ ] attempt count and next-retry metadata remain durable and consistent.
- [ ] WorkManager and foreground synchronization cannot race into unsafe duplicate state.

## 7. Pairing, contacts, and device trust

- [ ] Pairing requires explicit user confirmation where required.
- [ ] QR and manual invitation input is strictly validated.
- [ ] Size, version, encoding, and field bounds are enforced before allocation or persistence.
- [ ] Invalid, truncated, oversized, trailing, or ambiguous invitation data is rejected.
- [ ] Unknown invitation versions do not silently downgrade.
- [ ] Experimental invitation formats are not accepted by the active flow.
- [ ] An unauthenticated or incomplete pairing does not silently create a trusted contact.
- [ ] Identity replacement triggers the intended warning or blocking behavior.
- [ ] Pending devices cannot receive messages.
- [ ] Revoked devices cannot receive messages.
- [ ] `key_changed` devices remain blocked until explicitly handled.
- [ ] Missing primary-device state blocks sending.
- [ ] Unverified or ambiguous device state fails closed.
- [ ] No silent fan-out to secondary devices was added.
- [ ] Per-device identifiers and trust transitions are not conflated.
- [ ] Local revocation is not represented as synchronized protocol revocation.
- [ ] Device-state persistence and migration preserve fail-closed behavior.

## 8. Room, persistence, and migration safety

- [ ] Room does not gain plaintext message columns.
- [ ] Encrypted fields remain encrypted at rest through the intended boundary.
- [ ] The database is not falsely described as fully encrypted when metadata and schema remain visible.
- [ ] Schema version and exported schema files are updated consistently when required.
- [ ] Every schema change has an explicit migration or an approved destructive-development-only rationale.
- [ ] Migration preserves security-critical fields, counters, replay markers, trust state, and encrypted records.
- [ ] Migration failure fails safely.
- [ ] Partial migration or crash recovery cannot expose plaintext or corrupt trust state.
- [ ] Downgrade to an older schema or app version was considered.
- [ ] Identifier uniqueness and foreign-key assumptions remain safe.
- [ ] Transaction boundaries prevent inconsistent accept/store/delete/receipt state.
- [ ] Session counters survive restart as required.
- [ ] Retry state survives restart as required.
- [ ] Local metadata exposure was not increased without documentation.
- [ ] Test reset and cleanup do not silently alter production semantics.
- [ ] Deletion behavior is not represented as guaranteed flash-block erasure.

## 9. Disabled and experimental boundaries

- [ ] `SignalStoreFeatureFlags.SIGNAL_BACKED_SESSIONS_ENABLED` remains `false` unless reviewed activation was explicitly approved.
- [ ] `OfficialSignalDoubleRatchetGate.MIGRATION_ENABLED` remains `false` unless every required gate was explicitly satisfied and approved.
- [ ] The active chat path still uses the intended prototype session manager.
- [ ] Official libsignal classes are not silently wired into normal chats.
- [ ] In-memory Signal stores are not used in an application runtime path.
- [ ] Experimental SignalStore boundaries are not described as official protocol stores.
- [ ] Experimental PQXDH DTOs are not described as verified bundles.
- [ ] The active invitation flow cannot confuse v1 and experimental v2 formats.
- [ ] Dependency, Kotlin, Gradle, JNI, ABI, manifest, resource, shrinker, and packaging changes do not accidentally activate experimental code.
- [ ] AGPL/licensing and unsupported-external-use concerns were reviewed when libsignal-related distribution changed.
- [ ] Migration, rollback, downgrade, interoperability, and identity-change behavior were reviewed before any Signal activation.
- [ ] `PARANOIA_C4_AEAD` remains disabled unless explicit reviewed activation work was approved.
- [ ] Paranoia C4 is not described as a replacement for a reviewed session protocol.
- [ ] No home-grown cascade, fallback, unauthenticated composition, ambiguous KDF reuse, or incomplete primitive placeholder entered an active path.
- [ ] Historical lost local Signal work was not recreated or ported wholesale without current-repository review and explicit approval.

## 10. Dependencies, build, Android, and release boundaries

- [ ] New or upgraded dependencies are necessary and scoped.
- [ ] Dependency origin, version, license, maintenance status, and security impact were reviewed.
- [ ] Lockfiles, version catalogs, repositories, and checksums remain consistent where applicable.
- [ ] Kotlin, Gradle, AGP, JDK, Android SDK, NDK, or Node changes were not introduced casually.
- [ ] JNI and ABI packaging were reviewed when native libraries changed.
- [ ] Release shrinking, obfuscation, keep rules, and resource shrinking do not break security behavior.
- [ ] Manifest components, exported flags, permissions, intent filters, deep links, providers, services, receivers, and activities were reviewed when touched.
- [ ] Debug-only components and controls remain debug-only.
- [ ] Release signing, network policy, and build-type separation were not weakened.
- [ ] No local SDK paths, `local.properties`, generated private keys, signing secrets, APKs, build output, temporary logs, or machine-specific files were added.
- [ ] Relay tests and Android checks remain aligned with the changed scope.
- [ ] CI was not weakened, bypassed, or made to ignore failures.

## 11. Compatibility and protocol migration

- [ ] No envelope, AAD, receipt, QR, relay API, identifier, nonce, counter, replay-marker, vault, session, or protocol-record format changed silently.
- [ ] New formats are versioned and cannot be confused with existing formats.
- [ ] Unsupported versions fail closed.
- [ ] Downgrade behavior is explicit and safe.
- [ ] Existing stored data and active conversations have a migration or compatibility plan.
- [ ] Rollback behavior is documented.
- [ ] Cross-version sender/receiver behavior was considered.
- [ ] Retried or queued ciphertext remains interpretable after upgrade where required.
- [ ] Old data is not silently re-encrypted with unsafe key or nonce reuse.
- [ ] Any destructive migration is explicit, narrowly scoped, and approved.
- [ ] Required documentation was updated with the actual compatibility effect.

## 12. Tests and verification

- [ ] Focused unit tests were added or run for the changed behavior.
- [ ] Relevant integration tests were added or run.
- [ ] Relevant Android instrumented tests were run when device, Room, Keystore, permission, lifecycle, or UI behavior changed.
- [ ] Relay API tests were run when relay or transport behavior changed.
- [ ] Build and lint were run when relevant.
- [ ] Negative tests cover malformed, tampered, replayed, oversized, unsupported, unavailable, and failure cases.
- [ ] Restart, retry, concurrency, crash, and partial-failure behavior were tested where relevant.
- [ ] Tests assert fail-closed behavior.
- [ ] Tests were not weakened, deleted, skipped, or bypassed merely to pass.
- [ ] Test fixtures contain no real secrets or sensitive user data.
- [ ] Every skipped check is listed with a reason and resulting risk.
- [ ] Historical passing tests are not used as proof that current changes pass.
- [ ] Manual QA is distinguished from automated verification.
- [ ] Current infrastructure state is not claimed verified without current evidence.

## 13. Documentation and status consistency

- [ ] Runtime behavior, feature flags, tests, and documentation agree.
- [ ] `CURRENT_PROJECT_STATE.md` is updated when factual project state changed.
- [ ] `DECISIONS.md` is updated when a durable architectural decision changed or was added.
- [ ] `TASK_QUEUE.md` is updated when actionable future work changed.
- [ ] `HANDOFF.md` contains only current unfinished-session context.
- [ ] Workflow `README.md` is updated when files are added, removed, renamed, or repurposed.
- [ ] Known limitations and security tradeoffs remain documented.
- [ ] Disabled, experimental, historical, and unverified work is labelled accurately.
- [ ] No document claims that a lost local commit or absent file exists.
- [ ] Commands, paths, module names, links, versions, and flags were checked against the repository.
- [ ] Documentation-only changes did not silently alter runtime code or configuration.

## 14. Git and delivery of the change

- [ ] The full working diff was reviewed before completion.
- [ ] Staged changes contain only the approved scope.
- [ ] Secret and machine-file checks were performed before any commit.
- [ ] Commit creation occurred only when explicitly requested.
- [ ] Push occurred only when explicitly requested.
- [ ] No amend, rebase, force-push, history rewrite, branch deletion, or tag deletion occurred without explicit approval.
- [ ] Commit hash, branch, working-tree state, and push status are reported when Git operations occurred.
- [ ] Uncommitted drafts are clearly identified as not part of the authoritative GitHub baseline.

## 15. Final security review

Before finishing, answer:

1. What exact security property could this change affect?
2. What is the strongest repository evidence for the claimed behavior?
3. What failure, attack, downgrade, replay, migration, or data-loss scenario was considered?
4. Which checks passed?
5. Which checks were not run and why?
6. What remains unverified?
7. Did any active/disabled/experimental status change?
8. Did any persistent or wire format change?
9. Did documentation remain consistent with the repository?
10. What should a human reviewer inspect first?
11. Was a commit created?
12. Was anything pushed?
