# Current Project State — Secure Messenger MVP

Last repository verification: 2026-07-26

This file records the current recoverable state of Secure Messenger as verified against the GitHub default branch. It is a mutable factual snapshot, not a roadmap, decision log, task queue, release approval, or production-security claim.

## 1. Authoritative baseline

- Repository: `https://github.com/77Skala77/Messenger`
- Default branch: `main`
- Last verified GitHub HEAD: `c974e460b827022a841e374a95cb1ed736b2c25b`
- HEAD message: `Add Codex workflow guardrails`
- Previous verified commit: `4d5ee3dcafb51ef772b7c725ef2151b75b5f9a90` — `Document VPS SSH hardening`
- The current GitHub default branch is the only known recoverable repository baseline.
- The previous local clone and unpushed local commits are no longer available.
- Historical chat extracts and handoff notes provide context only. They do not prove that a file, commit, test, or implementation exists in the repository.

When sources disagree, use this order:

1. Current repository code, configuration, and feature flags.
2. Current tests and CI configuration.
3. Current repository documentation.
4. Historical handoff notes and chat extracts.

## 2. Status vocabulary

Use these terms consistently:

- **ACTIVE** — present and used by the current executable path.
- **IMPLEMENTED** — present in the repository, but not necessarily active in normal chats.
- **DISABLED** — present but explicitly blocked by a false flag, throwing adapter, build boundary, or documented gate.
- **EXPERIMENTAL** — spike, placeholder, DTO, interface, migration boundary, or test-only work that is not production functionality.
- **PLANNED** — described as future work but not implemented.
- **UNVERIFIED** — not confirmed from the current repository or current infrastructure.
- **HISTORICAL** — previously tested or reported, but not proof of current live state.
- **NOT IMPLEMENTED** — explicitly absent from the current project.

Do not infer implementation from documentation, an interface, DTO, test fixture, placeholder, disabled module, or historical chat report.

## 3. Project identity and security status

- The project is a native Android private 1-to-1 messenger MVP.
- Russian is the default application language; English is selectable in Settings.
- The project is intended for architecture review, development verification, and security review.
- It is not production-ready.
- It must not be represented as ready for real sensitive communications.
- No independent cryptographic or full application security audit has been completed.
- The relay, VPS QA results, disabled Signal work, and Paranoia C4 boundary do not justify a production-security claim.

## 4. Current active runtime

### Session and cryptography

- **ACTIVE:** normal chats use the prototype `SimplifiedSessionManager`.
- **ACTIVE:** `STANDARD_AEAD` is the only executable encryption suite.
- **ACTIVE:** `STANDARD_AEAD` uses JCA `AES/GCM/NoPadding`.
- **ACTIVE:** documented parameters include:
  - 256-bit keys;
  - 96-bit nonce composed from a random per-key prefix and durable monotonic counter;
  - 128-bit authentication tag;
  - authenticated associated data;
  - HKDF-derived per-message keys in the prototype session layer.
- **ACTIVE:** replay tracking exists.
- The prototype session is not Signal X3DH/PQXDH or Double Ratchet.
- The prototype does not provide production-grade forward secrecy or post-compromise recovery.

### Delivery semantics

- **ACTIVE:** outgoing messages are persisted locally before relay transmission.
- **ACTIVE:** pending and failed outgoing state includes attempt count and retry metadata.
- **ACTIVE:** foreground synchronization and WorkManager perform best-effort retry and incoming fetch.
- WorkManager is not a guarantee of timely delivery.
- **ACTIVE:** incoming envelopes are authenticated before acceptance.
- **ACTIVE:** accepted incoming messages and replay markers are durably recorded before relay deletion where required by the current pipeline.
- **ACTIVE:** duplicate/replayed messages are suppressed.
- **ACTIVE:** tampered messages are rejected.
- **ACTIVE:** encrypted delivery receipts are processed as control payloads rather than visible chat messages.
- `Sent to delivery server` means relay acceptance only.
- `Delivered` means that the sender received and validated an authenticated receipt confirming receiver acceptance, decryption, and durable storage.
- `Delivered` is not a read receipt.
- **NOT IMPLEMENTED:** user read receipts.

## 5. Android application state

The current repository documents these implemented Android capabilities:

- Kotlin and Jetpack Compose application.
- Russian-first onboarding and user interface.
- Persistent manual RU/EN language selection.
- QR invitation generation.
- Camera QR scanning and manual invitation import.
- Portrait-only QR scanner activity for invitation import and safety verification.
- Contact verification state and key-change warning UI.
- Conversation, contact security, Settings, relay configuration, and delivery diagnostics screens.
- Saved development relay URL.
- Relay `/health` check.
- Manual synchronization action.
- Debug-only readiness/preflight and disposable test-data reset controls.
- Physical-phone and emulator test flows.
- Release rejection of cleartext HTTP relay URLs.

The debug reset is for disposable QA state and is not a secure-erasure guarantee.

## 6. Storage and device trust

### Storage

- **ACTIVE:** Room stores message ciphertext rather than plaintext message columns.
- Room is not described as a fully SQLCipher-encrypted database.
- Database shape, routing data, and other metadata may remain visible locally.
- **ACTIVE:** profile name and prototype session state are documented as encrypted with a Keystore-backed local vault key.
- **ACTIVE:** session send counters survive restart.
- **IMPLEMENTED/DISABLED:** encrypted opaque Signal-like record tables exist as part of the SignalStore migration spike.
- Current documentation identifies Room schema version 5 for that storage boundary.
- Exact entities, migrations, transaction behavior, and current schema must be reverified before any database change.
- Android flash storage does not provide a reliable secure-deletion guarantee for historical blocks.

### Device trust

Current newer documentation uses these device-state labels:

- `pending`
- `verified`
- `revoked`
- `key_changed`

Other current design documentation also uses `UNVERIFIED`, `PENDING_APPROVAL`, and `KEY_CHANGED`. The exact runtime and persisted identifier vocabulary remains **UNVERIFIED** pending the device-state audit in `TASK_QUEUE.md`; do not rename persisted values based only on documentation wording.

Current policy semantics:

- sending fails closed for pending, revoked, changed, or missing primary-device state;
- the app does not silently fan out to secondary devices;
- revocation is local delivery blocking;
- revocation is not synchronized cryptographic protocol revocation;
- proper reviewed per-device session fan-out is not implemented.

## 7. Relay and infrastructure

### Relay model

- **ACTIVE for development/QA:** minimal Node relay under `android-app/server/relay`.
- The relay transports opaque encrypted envelopes and expiration metadata.
- Relay API documented in the repository:
  - `POST /v1/messages`
  - `GET /v1/messages/{queueId}`
  - `DELETE /v1/messages/{messageId}`
  - `GET /health`
- The relay is untrusted.
- The relay can observe metadata such as source IP, timing, queue identifiers, approximate payload size, and traffic frequency.
- Client confidentiality, authenticity, replay safety, device trust, and message acceptance must not depend on relay honesty.
- Local Node relay is appropriate for debug/LAN QA.
- A public relay remains QA infrastructure, not production infrastructure.

### Historical VPS evidence

The repository documents successful historical QA using:

- Ubuntu 24.04 LTS;
- Node relay bound to `127.0.0.1:8787`;
- Caddy reverse proxy;
- systemd service;
- UFW blocking direct public access to `8787/tcp`;
- SSH public-key login with password login disabled;
- physical Android phone plus Android Emulator;
- two-way delivery, retry recovery, delivery receipts, and reboot recovery.

Historical tested endpoint:

- `http://194.87.226.46`

This endpoint was debug-only HTTP by IP and was not suitable for release or production use.

**UNVERIFIED:** current VPS availability, ownership, DNS, TLS, Caddy, systemd, firewall, logs, retention, abuse controls, SSH state, and endpoint reachability. Do not assume the historical server is still live or correctly configured.

## 8. Disabled and experimental boundaries

### Signal-backed sessions

- **DISABLED:** `SignalStoreFeatureFlags.SIGNAL_BACKED_SESSIONS_ENABLED = false`.
- **DISABLED:** `OfficialSignalDoubleRatchetGate.MIGRATION_ENABLED = false`.
- **IMPLEMENTED/DISABLED:** official artifacts are pinned at `0.94.1` in a separate migration module.
- **DISABLED:** the official gate throws instead of creating a development store while migration is disabled.
- **EXPERIMENTAL:** `core:signalstore` contains neutral boundaries for:
  - identity records;
  - session records;
  - prekey records;
  - signed-prekey records;
  - Kyber prekey records;
  - a reserved future sender-key boundary.
- **EXPERIMENTAL:** `INVITE_V2_SIGNAL_PQXDH_EXPERIMENTAL` is a neutral placeholder DTO/codec.
- The experimental v2 invitation is not a verified PQXDH bundle.
- Normal chats remain on the prototype session path.
- The active v1 invitation flow must not silently accept the experimental v2 format.
- No working message is sent, received, or migrated through the SignalStore spike.

Current documented migration blockers include:

- official record mapping and serialization;
- durable encrypted protocol stores and transaction semantics;
- real prekey/PQXDH invitation semantics;
- identity-change and downgrade handling;
- per-device session fan-out and revocation behavior;
- Kotlin/toolchain compatibility;
- JNI/ABI packaging review;
- AGPL/licensing and unsupported-external-use review;
- interoperability tests;
- migration and rollback design;
- independent protocol and application security review.

### Paranoia C4

- **DISABLED:** `PARANOIA_C4_AEAD` is an isolated placeholder.
- It is not an enabled secure mode.
- It contains no accepted home-grown cascade or fallback implementation in the active path.
- It must not be enabled or described as secure without reviewed implementations, domain-separated KDF/MAC design, test vectors, integration tests, and independent audit.
- Paranoia C4 does not replace a reviewed session protocol.

## 9. Current modules

The Gradle project name is `SecureMessengerMvp`.

Current included modules:

- `:app`
- `:core:domain`
- `:core:crypto`
- `:core:storage`
- `:core:transport`
- `:core:delivery`
- `:core:ui`
- `:core:signal-protocol`
- `:core:signalstore`

The relay is under:

- `android-app/server/relay`

## 10. Current verification baseline

Repository build requirements documented in README:

- JDK 17
- Android SDK Platform 34
- Node.js 20+ for the relay

Primary Gradle checks:

```bash
cd android-app
./gradlew :app:assembleDebug
./gradlew :app:lintDebug :core:signal-protocol:testDebugUnitTest :core:signalstore:test test
```

Relay checks:

```bash
cd android-app/server/relay
node --test
```

Instrumented checks, when a compatible device or emulator is available:

```bash
cd android-app
./gradlew :core:ui:connectedDebugAndroidTest \
  :core:storage:connectedDebugAndroidTest \
  :core:transport:connectedDebugAndroidTest
```

Current GitHub Actions verifies:

- app debug assembly;
- app lint;
- Signal protocol unit tests;
- general unit tests;
- Node relay API tests;
- emulator UI and storage security flows.

A command listed here is a verification baseline, not proof that it has been run after future edits.

## 11. Explicitly not implemented or not complete

- Production-ready session security.
- Active Signal/PQXDH/Double Ratchet conversations.
- Verified official Signal interoperability.
- Production multi-device cryptographic fan-out.
- Synchronized protocol revocation.
- Read receipts.
- Application lock and biometric unlock.
- Encrypted backup.
- Attachments and media transfer.
- Secure wipe guarantee.
- Production relay transport.
- Domain-backed production HTTPS endpoint.
- Complete reproducible build, signing, SBOM, incident-response, and release process.
- External cryptographic/application audit.

## 12. Historical work absent from GitHub

A later historical chat extract reports local commits after `c974e46`, including disabled Signal skeletons, test-only adapters, expanded source guards, workflow task packs, and many `Document ... gaps` commits.

Those commits are not present in the current GitHub repository and the old local clone is unavailable.

Therefore:

- do not cite them as current implementation;
- do not assume their files or tests exist;
- do not recreate them blindly;
- use their ideas only after reviewing the current repository and deciding that the work is still appropriate;
- a historical `Document ... gaps` commit would prove documentation work only, not resolution of the underlying gap.

## 13. Documentation audit state

A complete ten-file workflow package has been prepared outside GitHub:

- revised `android-app/AGENTS.md`;
- revised `android-app/docs/ai-workflow/README.md`;
- new `CURRENT_PROJECT_STATE.md`;
- revised `HANDOFF.md`;
- revised `PROMPTS_FOR_CODEX.md`;
- revised `SECURITY_CHECKLIST.md`;
- revised `TESTING_CHECKLIST.md`;
- revised `RELEASE_CHECKLIST.md`;
- new `DECISIONS.md`;
- new `TASK_QUEUE.md`.

The cross-document consistency review was completed outside the repository on 2026-07-26. The package is internally aligned for the next repository-integration step.

These drafts are not part of the authoritative GitHub baseline until the user explicitly approves the repository write and the resulting GitHub commit is verified.

The next queued task is TQ-002 in `TASK_QUEUE.md`: add the approved workflow package to GitHub without changing executable behavior.

`SESSION_NOTES.md` is intentionally not planned unless a concrete need for a separate experiment journal appears.

## 14. Operational constraint

User-confirmed current constraint:

- the previous laptop was formatted and sold;
- the previous local clone is unavailable;
- only the Android phone is currently available;
- GitHub is the sole recoverable project baseline known to the user;
- local Android/Gradle/emulator/VPS verification cannot currently be assumed available.

Any future task must report clearly when verification requires a newly provisioned desktop, SDK, emulator, signing environment, or server access.

## 15. Update rules

Update this file when any of the following changes:

- GitHub HEAD or authoritative branch;
- active session or cryptographic path;
- feature flags;
- module list;
- Room schema or migrations;
- relay API or trust assumptions;
- device states or delivery semantics;
- implemented/disabled/experimental status;
- build or CI commands;
- production-readiness claims;
- current operational constraints.

Keep this file factual and compact.

Do not place detailed task planning here; use `TASK_QUEUE.md`.

Do not place architectural rationale here; use `DECISIONS.md`.

Do not place temporary session continuation notes here; use `HANDOFF.md`.
