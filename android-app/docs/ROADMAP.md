# Roadmap

## Phase 1: Android MVP

- Compose screens, Russian-first localization, invitations, verification indicators.
- Working standard payload AEAD, encrypted-at-rest records and opaque relay prototype.
- Security tests, threat model and explicit placeholder boundaries.
- Completed in prototype: debug relay send/fetch/delete pipeline, durable session
  counters/replay state, durable outgoing retry metadata with foreground retries,
  fail-closed local device delivery policy, and emulator CI coverage.
- Explicitly deferred: WorkManager/offline synchronization and cryptographic
  multi-device fan-out require reviewed session semantics first.

## Phase 2: Reviewed Session Protocol

- Replace prototype ECDH state with an established Signal-like implementation:
  X3DH or PQXDH and Double Ratchet.
- Started: an official `libsignal 0.94.1` adapter module is dependency-checked but disabled.
- Started: a disabled `core:signalstore` spike defines neutral store boundaries,
  vault-encrypted opaque Room records and an experimental invitation v2 DTO. It is
  not active in chats and is not an official protocol-store adapter.
- Remaining: official libsignal record mapping, verified PQXDH invitations, signed
  device semantics, delivery fan-out/revocation, Kotlin toolchain and licensing review.
- Investigation record: see `SIGNAL_MIGRATION_SPIKE.md`; no homemade ratchet is accepted
  as a shortcut around the migration gate.

## Phase 3: Post-Quantum Hybrid Handshake

- Assess a reviewed ML-KEM hybrid integration after ecosystem and interoperability review.

## Phase 4: Groups

- Evaluate MLS for group sessions and multi-device membership transitions.

## Phase 5: Metadata Reduction

- Sealed-sender design, padding, opaque queues, batching, relay chaining and
  Tor/P2P/local transports behind the transport abstraction.

## Phase 6: External Audit

- Protocol, cryptography, Android keystore/storage, server privacy, dependency and UI
  security review. Paranoia C4 cannot graduate before this gate.
- Status: audit scope/checklist prepared; no external audit has been performed.

## Phase 7: Release Discipline

- Reproducible builds, signed artifacts, dependency pinning/SBOM, hardened release
  configuration and incident-response process.
- Started: release cleartext denial and R8/resource shrinking are enabled; complete
  signing/reproducibility/SBOM work remains.
