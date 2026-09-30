# Device Trust Policy — Warning-Only Trust on First Use

> **HISTORICAL (2026-09-30):** this document describes the removed legacy prototype (static-ECDH session layer, `STANDARD_AEAD`, v1 invitations, reciprocal pairing, device approval states), deleted by DEC-021. It is kept for history and audit context only and does not describe current code. Current design: [Protocol v2](../PROTOCOL_V2.md).

**Status:** ACCEPTED  
**Decision date:** 2026-07-26  
**Repository baseline reviewed:** `abfa1475b24fefd0b39860d6e0a42005f0fa2910`

This file records the project-owner decision made after the TQ-003 device-state audit.
It is a focused decision record and takes precedence over older workflow wording that
says every unverified primary device must be blocked. It does not replace current code,
tests, Room schemas, `CURRENT_PROJECT_STATE.md`, or the broader decision log.

## Accepted policy

Secure Messenger MVP uses warning-only trust on first use for a newly imported remote
contact.

After the required reciprocal invitation/pairing flow:

- `VerificationStatus.UNVERIFIED` is allowed to communicate;
- the corresponding primary `DeviceApprovalStatus.PRIMARY_UNVERIFIED` is allowed as
  the single current delivery target;
- the UI must continue to show a clear, persistent warning that the remote identity has
  not been independently verified;
- the state must not be described as `VERIFIED` or as suitable for real sensitive
  communications;
- later comparison of the safety number or verification QR through a trusted or
  independent channel may move both contact and primary device to `VERIFIED`.

The rationale is practical remote onboarding: users in different countries may have no
in-person meeting or pre-existing independent secure channel. Blocking all communication
until manual verification would make first contact impossible for the intended MVP use.
The tradeoff is explicit: encryption may be active, but identity authenticity is not yet
independently established.

## States that continue to block delivery

Delivery must remain blocked for:

- `VerificationStatus.KEY_CHANGED`;
- `DeviceApprovalStatus.PENDING_APPROVAL`;
- `DeviceApprovalStatus.KEY_CHANGED`;
- `DeviceApprovalStatus.REVOKED`;
- a missing primary-device record;
- malformed, unsupported, corrupt, or otherwise invalid persisted trust state.

`VerificationStatus.UNKNOWN` is not an approved warning-only onboarding state. It is
required to fail closed. The TQ-003 audit found that current runtime and regression-test
evidence does not yet prove fail-closed handling for every `UNKNOWN` or invalid persisted
value. That is an open hardening task, not an implemented guarantee.

## Exact state models

Contact-level verification:

```text
VerificationStatus.VERIFIED
VerificationStatus.UNVERIFIED
VerificationStatus.KEY_CHANGED
VerificationStatus.UNKNOWN
```

Per-device approval:

```text
DeviceApprovalStatus.PRIMARY_UNVERIFIED
DeviceApprovalStatus.VERIFIED
DeviceApprovalStatus.PENDING_APPROVAL
DeviceApprovalStatus.KEY_CHANGED
DeviceApprovalStatus.REVOKED
```

Derived readiness/blocking types:

```text
PairingStatus.WAITING_FOR_RETURN_INVITATION
PairingStatus.READY_FOR_CONVERSATION
PairingStatus.DEVICE_BLOCKED

DeliveryBlockReason.MISSING_PRIMARY_DEVICE
DeliveryBlockReason.PENDING_APPROVAL
DeliveryBlockReason.REVOKED
DeliveryBlockReason.KEY_CHANGED
```

Technical documentation must distinguish contact verification from device approval.
Localized labels such as “Unverified,” “Pending approval,” or their Russian equivalents
may be used when describing UI text, but they are not persisted identifiers.

## Persistence and compatibility

Room stores the uppercase enum names as non-null `TEXT`:

- `contacts.verificationStatus` is written with `VerificationStatus.name` and read with
  `VerificationStatus.valueOf`;
- `contact_devices.approvalStatus` is written with `DeviceApprovalStatus.name` and read
  with `DeviceApprovalStatus.valueOf`.

Do not rename, lowercase, merge, or reinterpret these values without a reviewed Room
migration, compatibility analysis, rollback plan, and regression tests.

Migration 1 → 2 maps every contact verification value other than `VERIFIED` to the
new device value `PRIMARY_UNVERIFIED`. A targeted migration/retry regression test is
required for old `KEY_CHANGED` or invalid state before the migration path can be called
fully fail closed.

## Current runtime boundary

The current primary-only delivery planner permits `PRIMARY_UNVERIFIED` and `VERIFIED`,
blocks pending/revoked/key-changed/missing primary states, and never silently selects a
secondary device. Existing tests confirm that `PRIMARY_UNVERIFIED` is a usable QA state.

This policy therefore preserves the normal current onboarding behavior. It does not
activate Signal, PQXDH, multi-device fan-out, synchronized revocation, or any disabled
experimental path.

## Required follow-up

1. Reconcile `AGENTS.md`, `CURRENT_PROJECT_STATE.md`, `DECISIONS.md`, the project README,
   design documents, and checklists with this accepted policy during TQ-004.
2. Add a focused runtime task to fail closed for `VerificationStatus.UNKNOWN`, invalid
   persisted enum strings, and migration/retry edge cases.
3. Add tests for every contact/device state, Room round trips, migration 1 → 2, restart,
   queued retry, and invalid persisted values.
4. Preserve the visible identity-not-verified warning for `UNVERIFIED` /
   `PRIMARY_UNVERIFIED`.

## Security claim boundary

Warning-only trust on first use is a usability decision, not proof of identity. It must
never be presented as protection against invitation substitution on the first unverified
exchange. The MVP remains unsuitable for real sensitive communications and has no
production-security claim.
