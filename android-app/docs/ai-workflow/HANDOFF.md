# Handoff — Secure Messenger MVP

Use this file to preserve project context between Codex sessions.

## Current project identity

Repository path:

- ~/Documents/Codex/2026-05-26/senior-android-engineer-rust-engineer-security/android-app

Project:

- Secure Messenger MVP / Защищённый мессенджер MVP

Gradle root:

- SecureMessengerMvp

Main modules include:

- :app
- :core:domain
- :core:crypto
- :core:storage
- :core:transport
- :core:delivery
- :core:ui
- :core:signal-protocol
- :core:signalstore

## Stable context that must not be lost

- This is an MVP for architecture review and testing.
- It is not production-ready for real sensitive communications.
- Russian is the default in-app language; English is available in Settings.
- The executable chat path currently uses the prototype session layer and STANDARD_AEAD.
- STANDARD_AEAD uses AES-256-GCM with authenticated associated data and durable nonce/counter handling.
- Per-message keys are HKDF-derived in the prototype session layer.
- Replay tracking exists and must not be weakened.
- Received authenticated envelopes are durably recorded with replay marker before relay deletion while the app process is active.
- Delivery receipts are encrypted/authenticated.
- "Sent to delivery server" means relay acceptance only.
- "Delivered" must mean receiver accepted, decrypted, stored the message, and sent an authenticated receipt.
- Outgoing state is durable with attempt count and exponential retry metadata.
- WorkManager synchronization is best effort, not a guarantee of timely delivery.
- Relay is untrusted.
- Debug HTTP relay is allowed only by debug development policy.
- Release builds reject HTTP relay URLs and disallow cleartext networking.
- Room stores ciphertext rather than plaintext message columns.
- Profile name and session state are encrypted with a Keystore-backed local vault key.
- Current device states include pending, verified, revoked, key_changed.
- Current single-device delivery policy fails closed for pending, revoked, changed, or missing primary device state.
- Revocation is local delivery blocking, not synchronized protocol revocation.
- PARANOIA_C4_AEAD is disabled and must remain disabled unless explicitly approved and reviewed.
- Official libsignal migration is disabled.
- core:signalstore is a disabled spike.
- Experimental v2 PQXDH invitation DTOs are not verified PQXDH bundles.
- Signal-backed sessions are not wired into normal chats.

## Current safe strategic direction

The next safe security step is not to enable experimental Signal code in normal chats.

The next safe step is an isolated Signal backend/session spike behind a feature flag, with tests, without activating it in ordinary conversations until reviewed.

## Safe continuation prompt for Codex

Use this at the start of a new Codex session:

Read AGENTS.md and docs/ai-workflow/*.md first.

Do not edit files yet.

Summarize:

1. The active project identity.
2. The security-critical rules.
3. The disabled experimental boundaries.
4. The next safest step.

Then wait for the concrete task.

## End-of-session update template

At the end of a long task, update this file with:

Date:
Branch:
Task:
Summary:
Files changed:
Tests/checks run:
Tests/checks not run:
Security impact:
Remaining risks:
Next safest step:

## Last manual checkpoint

Date: 2026-06-03

Branch before guardrails: main

Working tree before guardrails: clean

Confirmed project evidence:

- README title: Secure Messenger MVP / Защищённый мессенджер MVP
- Gradle rootProject.name: SecureMessengerMvp
- Modules include app, crypto, delivery, storage, transport, signal-protocol, signalstore
- README documents relay, QR invitation, replay tracking, device states, disabled SignalStore/PQXDH boundary, disabled PARANOIA_C4_AEAD, disabled libsignal migration
