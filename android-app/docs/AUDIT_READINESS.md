# External Audit Readiness

## Status

No external cryptographic audit has been conducted. This file is an audit intake
checklist prepared for a qualified independent reviewer; it is not an audit report
and must not be used to make security claims.

## Proposed Audit Scope

- `STANDARD_AEAD`, nonce durability, AAD binding, envelope parsing and failure behavior.
- Prototype session limitations and the planned official Signal/PQXDH/Double Ratchet migration.
- Android Keystore usage, Room/vault encryption, replay transaction and local wipe limits.
- QR invitations, safety verification, key-change and new-device approval UX.
- Relay storage, TTL/deletion behavior and exposed metadata.
- Paranoia C4 disabled boundary and criteria for any future review.
- Dependency, licensing, build/release and diagnostic logging controls.

## Evidence Available

- Crypto round-trip, wrong-AAD/tag, nonce, replay and serialization tests.
- Android instrumented language persistence, Keystore and Room replay/retry/device-state tests.
- Relay API TTL/delete tests.
- Delivery integration tests for two reciprocal peers, relay deletion, replay
  suppression, AAD/tag rejection, retry timing and fail-closed recipient policy;
  an instrumented scenario also runs through the real HTTP client and local Node relay.
- Disabled Signal store spike tests: feature flag/adapter refusal, experimental v2
  DTO round-trip and vault-encrypted opaque record persistence/delete boundaries.
- Threat model, crypto design, dependency snapshot and localization policy.

## Blocking Findings Before Any Real Use

- Active sessions still use prototype static ECDH rather than an audited ratchet.
- Official `libsignal` integration is disabled: storage-only neutral records exist,
  but official record mapping, PQXDH semantics, toolchain and legal review are pending.
- Local debug relay does not protect transport metadata and is not a deployment target.
- Paranoia C4 is unimplemented and disabled.
- Independent audit, release signing/reproducibility review and operational review are absent.
