# Roadmap

## Phase 1: Android MVP

- Compose screens, Russian-first localization, invitations, verification indicators.
- Working standard payload AEAD, encrypted-at-rest records and opaque relay prototype.
- Security tests, threat model and explicit placeholder boundaries.

## Phase 2: Reviewed Session Protocol

- Replace prototype ECDH state with an established Signal-like implementation:
  X3DH or PQXDH and Double Ratchet.
- Add prekey management, signed device records, delivery integration, durable replay
  windows and explicit new-device approval.

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

## Phase 7: Release Discipline

- Reproducible builds, signed artifacts, dependency pinning/SBOM, hardened release
  configuration and incident-response process.
