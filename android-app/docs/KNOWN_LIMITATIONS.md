# Known Limitations

## Status And Claim Boundary

The application is an audit-ready MVP prototype, not a production-secure messenger.
It has not completed an independent cryptographic, Android, protocol, server, privacy,
or release-security audit and must not be represented as suitable for real sensitive
communications.

Current chats use `SimplifiedSessionManager` and the executable `STANDARD_AEAD` suite.
There is no active PQXDH or Double Ratchet session yet — real Double Ratchet/PQXDH
via `libsignal` is the committed direction of `docs/ai-workflow/REWRITE_PLAN.md`.
Signal-backed sessions remain disabled pending that rewrite. `PARANOIA_C4_AEAD` was
removed from the codebase entirely (2026-09-27) rather than kept as a disabled
placeholder. Disabled modules, storage spikes, DTOs, interfaces, and test fixtures add no
security guarantee to the active messaging path.

## Session And Protocol Security

- The prototype static-ECDH session design does not provide the forward secrecy or
  post-compromise recovery of a reviewed Double Ratchet.
- The project has not demonstrated reviewed Signal Protocol interoperability.
- Secure protocol migration, downgrade handling, rollback, and production key-management
  behavior are not implemented.
- The active invitation flow is a limited project-specific protocol and is not a reviewed
  Signal prekey or PQXDH establishment flow.

## Identity And Device Trust

The accepted MVP policy uses warning-only trust on first use after reciprocal invitation
import. Communication is allowed when the contact is
`VerificationStatus.UNVERIFIED` and its matching primary device is
`DeviceApprovalStatus.PRIMARY_UNVERIFIED`.

Encryption in this state does not prove that the imported key belongs to the intended
human. An attacker controlling the initial invitation-delivery channel can substitute the
first imported key. The interface must continue to show that the identity is unverified,
and the state must not be described as `VERIFIED` or as appropriate for real sensitive
communication. Identity assurance requires later safety-number or verification-QR
comparison through a trusted or independent channel.

Contact verification and device delivery approval are separate persisted models. Current
delivery permits primary-device states `PRIMARY_UNVERIFIED` and `VERIFIED`, and blocks:

- `VerificationStatus.KEY_CHANGED`;
- `DeviceApprovalStatus.PENDING_APPROVAL`;
- `DeviceApprovalStatus.KEY_CHANGED`;
- `DeviceApprovalStatus.REVOKED`;
- a missing primary-device record.

Multi-device cryptographic fan-out is not implemented. Delivery selects only the matching
primary device and does not silently target a secondary device. Local revocation is local
delivery policy state, not synchronized cryptographic revocation across devices.

Policy also requires `VerificationStatus.UNKNOWN`, malformed enum strings, unsupported
persisted states, corrupt trust records, and otherwise invalid trust state to fail closed.
Complete runtime and regression evidence for every UI, direct-send, restart, migration,
background-retry, and queued-delivery path does not yet exist.

Persisted trust values use exact uppercase enum names. Migration 1 to 2 maps every contact
verification value other than `VERIFIED` to `PRIMARY_UNVERIFIED`. Targeted runtime
hardening and migration/retry tests remain required for old `KEY_CHANGED`, `UNKNOWN`,
malformed, unsupported, or corrupt values before this boundary can be described as fully
fail closed.

See [Device Trust Policy](ai-workflow/DEVICE_TRUST_POLICY.md) and
[Cryptographic Design](CRYPTO_DESIGN.md) for the accepted policy and persistence details.

## Delivery Semantics And Availability

- Foreground synchronization and WorkManager retries are best effort. Android may delay
  background work, so prompt or timely offline delivery is not guaranteed.
- The relay can delete, delay, reorder, replay, or withhold encrypted blobs. Authenticated
  encryption does not prevent denial of service.
- Malformed or unauthenticated blobs are not accepted as messages, but unusable blobs may
  remain until later processing or expiry and can contribute to queue-filling denial of
  service.
- `DELIVERED` means the receiving application authenticated, decrypted, and durably stored
  the message and the sender authenticated the resulting receipt. It does not mean the
  human user opened or read the message.
- User read receipts are not implemented.
- Legacy path only (DEC-020): the legacy wire format has no authenticated sender message ID,
  so a delivery receipt echoes the relay-visible ID. Since 2026-09-29 incoming messages are
  stored under local IDs and receipts only update messages sent to the same contact, but a
  malicious relay can still swap IDs among messages to the **same** contact and make one
  dropped message show `DELIVERED`. The v2 protocol (DEC-019 T4) removes this; the legacy
  path is deleted in step 1f.

## Relay And Network Metadata

- The debug LAN relay may use HTTP for test convenience and exposes traffic to the local
  network. It is not a deployable production or anonymity design.
- Release builds reject insecure HTTP relay configuration and Android cleartext traffic.
  This restriction does not make an unreviewed HTTPS relay production-secure.
- An HTTPS relay protects the network hop to the endpoint but the relay operator can still
  observe client IP addresses, queue identifiers, request timing, approximate message
  sizes, and traffic frequency.
- Stable queue identifiers and traffic patterns may expose linkability and social-graph
  metadata.
- A public relay can still withhold, delete, replay, reorder, or flood traffic and requires
  separate abuse controls, monitoring, retention limits, logging restrictions, patching,
  rate limiting, and incident-response planning.
- Historical VPS or endpoint QA records do not prove current server availability,
  ownership, configuration, authorization, security, or retention behavior.

## Devices, Local Storage, And Plaintext

- Room stores message ciphertext rather than a plaintext message-body column, but the
  database is not claimed to be fully encrypted with SQLCipher.
- Local database structure, identifiers, table relationships, routing associated data,
  statuses, timestamps, lookup identifiers, and access patterns may remain visible.
- Opaque disabled Signal-store record payloads may be vault-wrapped, but their lookup and
  address identifiers remain local metadata and the store is not active messaging
  protection.
- Plaintext necessarily exists in input, Compose, application, display, and process memory
  while messages are typed, encrypted, decrypted, or displayed.
- Screenshots, screen recording, keyboard or clipboard collection, notification leakage,
  accessibility capture, UI automation, and memory inspection are not prevented by the
  messaging cryptography.
- Android Keystore reduces direct key-extraction risk but does not make an unlocked or
  compromised device safe. Malware or a malicious operating system may capture plaintext
  or act through the application while keys are usable.
- App lock and biometric unlock, encrypted backup, secure device transfer, and attachment
  or media encryption workflows are not implemented.

## Local Deletion

The current local deletion path removes current Room application data, prototype session
state, replay and retry state, disabled Signal-spike records, local queue state, nonce
preferences, and current Keystore aliases.

It has important limitations:

- the configured relay URL is retained for repeated development and QA use;
- local deletion does not request deletion of every ciphertext blob already queued on a
  relay;
- remaining relay ciphertext is subject to normal receipt deletion, relay behavior, and
  TTL;
- application deletion and Keystore alias deletion cannot guarantee forensic erasure of
  historical flash-storage blocks.

The debug reset and local deletion controls must not be described as cryptographic remote
revocation or secure physical erasure.

## Diagnostic Boundary

Delivery diagnostics intentionally show technical statuses, counts, timestamps, the
configured relay URL, and short SHA-256 fingerprints of queue identifiers. They do not
include plaintext, cryptographic key material, nonce values, ciphertext envelopes, or
full queue identifiers.

Diagnostics are QA support. They are not cryptographic evidence, identity verification,
metadata anonymity, or proof of current relay security.

## Additional Missing Production Controls

The current project does not provide a complete reproducible release, signing, dependency
verification, SBOM, backup and recovery, incident-response, production relay, or external
security-audit process. These require separate implementation, verification, and review
before any production-security claim.