# AGENTS.md — Codex workflow rules for Secure Messenger MVP

This is a security-sensitive encrypted messenger MVP.

Treat this repository as a high-risk project where shortcuts can break confidentiality, authentication, replay safety, device trust, or user privacy.

## Project status

This is an MVP for architecture review and testing.

It is not production-ready and must not be represented as ready for real sensitive communications.

The current executable chat path uses the prototype session layer and STANDARD_AEAD.

Signal-backed sessions, PQXDH semantics, libsignal migration, PARANOIA_C4_AEAD, and SignalStore integration are experimental or disabled boundaries unless the code and feature flags explicitly prove otherwise.

## Core working mode

Before editing code:

1. Read this file.
2. Inspect relevant files.
3. Restate the task briefly.
4. Identify security-sensitive areas.
5. Produce a small implementation plan.
6. List files expected to change.
7. Prefer minimal, reviewable diffs.

After editing code:

1. Run relevant build/tests when possible.
2. Report exactly what changed.
3. Report what was verified.
4. Report what could not be verified and why.
5. Report security impact.
6. Report remaining risks.
7. Suggest the next safest step.

## Hard security rules

Never weaken cryptography.

Never replace cryptographically secure randomness with ordinary randomness.

Never add plaintext fallback.

Never trust the relay server.

Never turn fail-closed behavior into fail-open behavior.

Never silently continue after a security-critical failure.

Never remove security checks to make implementation easier.

Never weaken tests to make them pass.

Never claim production-grade security.

Never enable disabled experimental crypto/session modules for normal chats without explicit user approval, feature flag isolation, tests, and security review.

## Forbidden logging and diagnostics

Never log, print, persist, expose, or include in copied diagnostics:

- plaintext messages
- private keys
- session keys
- shared secrets
- authentication tokens
- QR pairing secrets
- raw pairing payloads
- bearer tokens
- raw encrypted payloads when they can help correlation, replay analysis, or queue identification
- full queue identifiers if a short fingerprint is enough
- sensitive contact data unless explicitly sanitized

Logs and copied diagnostics must be safe to share.

Prefer high-level event names, timestamps, sanitized state names, short fingerprints, counters, and retry metadata.

## Security-critical areas

Treat these as security-critical:

- cryptographic session setup
- STANDARD_AEAD implementation
- nonce allocation and counter persistence
- HKDF-derived per-message keys
- replay tracking
- envelope authentication
- incoming message pipeline
- outgoing delivery pipeline
- delivery receipts
- retry state and WorkManager synchronization
- local encrypted storage
- Android Keystore usage
- profile/session vault keys
- Room migrations for security-sensitive tables
- QR invitation generation, scanning, and manual import
- device states: pending, verified, revoked, key_changed
- fail-closed delivery policy
- relay transport and relay deletion behavior
- diagnostics and copied reports
- disabled Signal/libsignal/PQXDH/SignalStore boundaries
- disabled PARANOIA_C4_AEAD boundary

Changes in these areas require tests or a clear explanation of why tests could not be run.

## Relay trust model

The relay is untrusted.

Assume the relay can:

- drop messages
- replay messages
- reorder messages
- delay messages
- return malformed payloads
- attempt downgrade behavior
- correlate metadata
- expose queue identifiers
- lie about availability
- fail during send/fetch/delete
- serve old blobs
- attempt to trick clients into invalid state

The app must not depend on relay honesty for confidentiality, authenticity, device trust, replay safety, or message acceptance.

"Sent to delivery server" means relay acceptance only.

"Delivered" must only mean the receiver accepted, decrypted, stored the message and returned an authenticated receipt.

## Device trust rules

Never bypass checks for:

- pending devices
- revoked devices
- key_changed devices
- missing primary device state
- unverified or ambiguous device trust state

The current single-device delivery policy must fail closed for pending, revoked, changed, or missing primary device state.

Do not silently fan out to secondary devices.

Do not treat revocation as synchronized protocol revocation unless that protocol is actually implemented and verified.

## Experimental crypto/session boundaries

Do not wire disabled Signal-backed sessions into the running chat path unless explicitly requested.

Do not treat experimental PQXDH invitation DTOs as verified PQXDH bundles.

Do not treat the disabled SignalStore spike as production Signal storage.

Do not enable libsignal-backed processing without explicit approval, tests, threat review, licensing review, and migration review.

Do not enable PARANOIA_C4_AEAD as secure without independent review, test vectors, KDF/MAC design, and audit.

The next safe step for Signal work is an isolated backend/session spike behind a feature flag, not normal chat activation.

## Android and build rules

Prefer using the existing Gradle structure.

Do not change module boundaries broadly unless explicitly requested.

Do not rewrite large UI or architecture areas when a focused fix is enough.

Do not commit local.properties changes, local SDK paths, secrets, generated keys, or machine-specific files.

Avoid unrelated formatting-only changes.

## Expected verification commands

Use relevant commands from README when possible:

- ./gradlew :app:assembleDebug
- ./gradlew :app:lintDebug :core:signal-protocol:testDebugUnitTest :core:signalstore:test test

If a command cannot be run because of environment, time, SDK, emulator, or device limits, say so clearly.

## Required final report format

Every implementation task must end with:

- Summary
- Files changed
- Tests/checks run
- Tests/checks not run and why
- Security impact
- Remaining risks
- Suggested next step
