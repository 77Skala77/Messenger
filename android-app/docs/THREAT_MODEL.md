# Threat Model

> **PARTLY HISTORICAL (2026-09-30):** statements here about the prototype session layer, `STANDARD_AEAD`, v1 invitations, reciprocal pairing, device approval states, or delivery diagnostics describe code removed by DEC-021. Relay, network, and process guidance may still apply. Current protocol: [Protocol v2](PROTOCOL_V2.md).

## Status And Evidence Boundary

This repository is an audit-ready MVP prototype, not a production-secure messenger.

This document describes:

- the accepted security policy;
- security-relevant behavior visible in the current runtime code;
- narrowly scoped controls represented by current test source;
- known limitations and open hardening work.

It is not an assurance report or evidence of an independent cryptographic, Android,
protocol, server, or privacy audit.

Repository test and QA records must be interpreted carefully. A test in the source tree
shows intended regression coverage but does not prove that it passed for the current
commit unless a current test run is recorded. Historical phone, emulator, restart, retry,
receipt, and VPS QA results are useful context but are not verification of a later commit
or of current infrastructure.

Disabled Signal, Signal-store, and PQXDH invitation boundaries add no security
guarantee to the active messaging path. `PARANOIA_C4_AEAD` no longer exists in the
codebase (removed 2026-09-27) and never added one either.

## Adversaries And Capabilities

The MVP considers the following relevant threats:

- an untrusted or malicious relay that can observe metadata, withhold or delete blobs,
  replay previously submitted blobs, inject malformed blobs, or fill a queue;
- a network or relay operator that can observe IP addresses, timing, approximate message
  sizes, queue identifiers, and traffic frequency;
- an attacker that can replace or modify an invitation before the recipient independently
  verifies the remote safety number;
- malware, accessibility abuse, or a malicious operating system on an unlocked device;
- local access to database structure, identifiers, routing metadata, and access patterns;
- denial of service through relay unavailability, message withholding, queue flooding,
  or delayed Android background execution.

The current design does not claim protection against every capability of these
adversaries.

## Current Implemented Controls

The current implementation contains the following security-relevant controls:

- Message content is processed through the working authenticated-encryption suite
  `STANDARD_AEAD`.
- AEAD authenticates ciphertext and associated routing data before incoming plaintext is
  accepted.
- Room does not contain a plaintext message-body column. Persisted message payloads are
  encrypted ciphertext packages.
- The local profile display name, local contact display label, and prototype session state
  are wrapped using an Android Keystore-backed local vault key.
- Opaque disabled Signal-store record contents are also vault-wrapped when populated by
  tests. This adds no active Signal Protocol guarantee.
- Session send state is persisted before network delivery begins, and nonce allocation
  uses persisted counter state.
- Incoming encrypted messages and their accepted replay tuples are committed in one Room
  transaction before normal relay deletion.
- A later authenticated duplicate of an accepted replay tuple is suppressed.
- Delivery receipts are encrypted and authenticated control messages. A message is marked
  `DELIVERED` only after the receiving application accepts, decrypts, and durably stores
  it and the sender authenticates the resulting receipt.
- QR invitation import, safety-number display, verification QR comparison, manual
  verification, key-change warnings, and per-device approval state exist in the current
  application.
- An additional imported device is staged without automatically becoming a delivery
  target.
- Current delivery selects only the device matching the contact's primary identity and
  never silently fans out a single-device ciphertext to a secondary device.
- Delivery diagnostics omit plaintext, key material, nonce values, and ciphertext
  envelopes and use short queue fingerprints instead of full queue identifiers.

These controls protect specific data and operations under the assumptions below. They do
not make the prototype suitable for sensitive real-world communication.

## Identity, Invitation, And Device Trust

### Separate trust models

The project has two separate persisted trust models.

Contact-level identity verification:

```text
VerificationStatus.VERIFIED
VerificationStatus.UNVERIFIED
VerificationStatus.KEY_CHANGED
VerificationStatus.UNKNOWN
```

Per-contact-device delivery approval:

```text
DeviceApprovalStatus.PRIMARY_UNVERIFIED
DeviceApprovalStatus.VERIFIED
DeviceApprovalStatus.PENDING_APPROVAL
DeviceApprovalStatus.KEY_CHANGED
DeviceApprovalStatus.REVOKED
```

These values must not be collapsed into one informal device-state model.

A newly imported contact is normally stored as:

```text
VerificationStatus.UNVERIFIED
```

Its matching primary device is normally stored as:

```text
DeviceApprovalStatus.PRIMARY_UNVERIFIED
```

### Warning-only trust on first use

After the reciprocal invitation flow, the accepted MVP policy permits communication when:

- the contact is `VerificationStatus.UNVERIFIED`; and
- the matching primary device is
  `DeviceApprovalStatus.PRIMARY_UNVERIFIED`.

This is warning-only trust on first use.

Encryption of this first unverified exchange can protect message content from parties
that do not possess the established session key. It does not prove that the imported key
belongs to the intended human.

The conversation interface must continue to display a clear warning that the contact's
identity has not been independently verified. The state must not be called `VERIFIED` or
presented as appropriate for real sensitive communication.

Later comparison of the safety number or verification QR through a trusted or independent
channel may move both the contact and its primary device to `VERIFIED`.

An attacker controlling the initial invitation-delivery channel can still substitute the
first imported key. Warning-only trust on first use is a usability decision, not
protection against first-contact impersonation.

Bidirectional communication operationally requires reciprocal invitation import because
each invitation advertises its creator's receiving queue and public identity.

### Blocking states and current enforcement boundary

The accepted policy requires delivery to remain blocked for:

- `VerificationStatus.KEY_CHANGED`;
- `DeviceApprovalStatus.PENDING_APPROVAL`;
- `DeviceApprovalStatus.KEY_CHANGED`;
- `DeviceApprovalStatus.REVOKED`;
- a missing primary-device record.

The current interactive-send path explicitly blocks
`VerificationStatus.KEY_CHANGED`. The current primary-only delivery planner permits
`PRIMARY_UNVERIFIED` and `VERIFIED`, and blocks pending, revoked, key-changed, or missing
primary-device state.

A changed primary-contact key is persisted as both contact-level and primary-device
key-changed state in the normal import flow.

Local device revocation is a local delivery block. It is not synchronized cryptographic
revocation across several devices.

### Open trust-state hardening

Policy requires the following states to fail closed:

- `VerificationStatus.UNKNOWN`;
- malformed enum strings;
- unsupported persisted states;
- corrupt trust records;
- otherwise invalid contact or device trust state.

This is not yet a complete runtime guarantee.

The normal onboarding flow does not use `UNKNOWN` as an approved first-contact state, but
current UI and interactive-send checks explicitly block `KEY_CHANGED` rather than
comprehensively rejecting every non-approved verification value.

The lower-level outgoing retry and delivery planner bases its target decision on the
primary device approval state. Current runtime and regression evidence does not yet prove
that every queued-retry, restart, migration, and invalid contact-verification case fails
closed.

Persisted enum values are read using exact enum-name conversion. Malformed values may
therefore cause deserialization failures, and handling is not uniform across UI
observation, direct delivery, background retry, restart, and migration paths.

Migration 1 to 2 maps every contact verification value other than `VERIFIED` to
`PRIMARY_UNVERIFIED`. Targeted runtime changes and regression tests remain required for
old `KEY_CHANGED`, `UNKNOWN`, malformed, unsupported, and corrupt values before the
persistence boundary can be described as fully fail closed.

The uppercase enum names stored in Room are part of the local database compatibility
surface. They must not be renamed, lowercased, merged, or reinterpreted without a reviewed
migration, compatibility analysis, rollback planning, and regression tests.

See
[Device Trust Policy](ai-workflow/DEVICE_TRUST_POLICY.md)
for the accepted decision and
[Cryptographic Design](CRYPTO_DESIGN.md)
for the broader protocol and persistence boundary.

## MVP Does Not Fully Protect Yet

The current MVP does not fully protect:

- the authenticity of the intended human during an unverified first contact;
- timing metadata, IP addresses, message size, or traffic frequency;
- stable queue linkability, server-side queue metadata, or the complete social graph;
- local database structure, routing AAD, lookup identifiers, table relationships, or
  access patterns;
- Signal-store lookup metadata such as record and address identifiers;
- a device compromised while unlocked or a malicious operating system;
- plaintext shown on screen or present in Compose, keyboard, clipboard, notification, or
  application memory;
- screenshots, keyboard collection, accessibility capture, notification leakage, or
  memory inspection;
- cryptographic multi-device fan-out, synchronized revocation, or cross-device state
  synchronization;
- prompt delivery while the application is inactive;
- guaranteed WorkManager execution or timely offline delivery;
- forward secrecy and post-compromise recovery equivalent to a reviewed Double Ratchet;
- secure backup, device transfer, attachment handling, or app-lock protection;
- forensic secure deletion from flash storage;
- complete fail-closed handling of unknown, malformed, unsupported, or migrated trust
  state;
- deletion of already queued relay ciphertext as a consequence of local app-data
  deletion;
- relay availability, resistance to message withholding, or resistance to queue-filling
  denial of service;
- production relay deployment, metadata anonymity, or independently reviewed release
  hardening.

## Assets And Trust Boundaries

### Device agreement key

The private EC device-agreement key is intended to remain inside Android Keystore.

A compromised Android runtime, operating system, accessibility service, or unlocked
application can still abuse the key or obtain plaintext without directly extracting its
private key bytes.

### Session root

The prototype session root is derived using EC ECDH and HKDF and stored as a
Keystore-wrapped local blob.

It is necessarily present in application memory while an active session is used. The
static-ECDH session design does not provide Double Ratchet forward secrecy or
post-compromise recovery.

### Per-message keys and nonce state

Per-message keys are derived using HKDF for an operation and explicitly overwritten
afterward.

Nonce and session-counter persistence are security-critical. Backup, restore, migration,
rollback, device transfer, or concurrent-write changes can invalidate the current nonce
and counter assumptions and require a new security review.

### Plaintext

Plaintext exists in Compose, input, application, and display memory while a message is
typed, encrypted, decrypted, or displayed.

Plaintext must not enter Room, relay payload logs, delivery diagnostics, or exported QA
reports.

### Persisted trust state

Contact verification and per-device approval values directly affect delivery decisions.

Room trust values, their enum conversion, migration behavior, and retry behavior are
therefore part of the security boundary rather than ordinary presentation state.

### Invitation and verification channels

The initial invitation-delivery channel is not automatically trusted.

A safety number or verification QR provides identity assurance only when compared through
a trusted or independent channel. Merely displaying a fingerprint does not prove who owns
the key.

The v2 invitation QR is a bearer capability (AI audits 2026-10, H1 and INV-01, DEC-028):
whoever sees it, for example in a photo or a screenshot, can pair first and be shown to the
inviter as a contact request, or delete the invitation from the relay. Comparing the safety
number is the only protection against the first; the second only costs a new invitation. The
invitation is single use; once used, the inviter's app withdraws it from the relay so a later scan is
told it no longer works (DEC-032). The invitation screens can be screenshotted (owner's choice); a
"Screen security" switch, off by default, blocks screenshots and recording of the whole app.

### Relay

The relay is untrusted for confidentiality, integrity, availability, and metadata
privacy.

Payload AEAD protects message content and authenticated associated data against relay
modification, but it does not hide routing and traffic metadata or prevent relay denial
of service.

### Security-relevant UI

The UI is responsible for preserving the distinction between encrypted and verified.

An `UNVERIFIED` contact may communicate under the accepted warning-only policy, but the
interface must continue to show that identity authenticity has not been established.

v2 implementation (DEC-030, 2026-10-05): accepting a request goes through a comparison step with
three explicit answers (the numbers match, accept without comparing, the numbers differ), and the
scanner is shown the same comparison right after scanning. The chat header says "Safety number not
checked" until the person confirms a match; the local mark is set only for the exact number shown,
is never sent, is removed with a changed key, and is worded "checked", never "secure".

Notifications (DEC-031, written 2026-10-05, not yet run on a phone) say who wrote, by the person's own
name for the contact, and how many unseen texts there are; never the text, and never the name a
contact request claims. A locked phone shows only "New message" (the notification's public version)
unless the person chose to show all notification content on the lock screen, which is Android's
setting and not the app's to override. Android's notification history and apps the user granted
notification access still learn the contact's local name and the count. A chat the person muted
(DEC-035) is not announced while the mute lasts.

Message order (DEC-034): an incoming text sorts by its sender's own time, taken from the
authenticated payload, but only within the minute before it arrived and never before the contact's
previous text. The relay cannot change that time; the contact can move its own text at most a
minute back, never into the future and never ahead of its arrival, so it cannot raise its chat in
the list. The shown time of a late text is its arrival.

Background connection (DEC-034, debug builds only): a foreground service fetches every 30 seconds
while the app is closed and keeps the processor awake. It runs the same pipeline under the same
protocol lock and notification rules as the periodic work, holds no key of its own, logs nothing,
and its permanent notification carries no message content. It widens nothing the relay could not
already see, except a steadier fetch rhythm (the relay learns that this inbox is polled every
30 seconds, as it already learned the 3-second rhythm while the app was open).

Chat management (DEC-035) is local: pinning, muting, "mark as read" and clearing history send
nothing. Deleting a chat deletes the contact with its session and stored identity (owner's
choice); the contact is not told and sees its later texts stay at "sent to the delivery server".
A new pairing afterwards trusts the identity afresh, as any first pairing does; the safety-number
comparison is the defence, as before. A reply names the text it answers inside the encrypted
payload; a contact can only make its own quotes point at the wrong text in its own chat.

## Relay Limitations

The relay stores opaque encrypted blobs with TTL and supports deletion after receipt.

It does not receive message plaintext or private keys through the intended delivery
interface. It still observes:

- queue identifiers;
- arrival and retrieval times;
- approximate message sizes;
- source network information;
- traffic frequency.

Relay deletion, withholding, reordering, replay, traffic analysis, and denial of service
remain possible.

Malformed or unauthenticated blobs are not acknowledged as accepted messages. Because
they remain available for later processing or expiry, an abusive sender or relay can deny
delivery by filling a queue with unusable blobs.

The runnable endpoint is a debug development transport that may be exposed on a local
Wi-Fi network for physical-device testing. A cleartext LAN connection and a
user-configured endpoint are not a deployable anonymity or production-relay design.

Release builds reject insecure HTTP relay configuration and Android cleartext traffic.
This transport restriction does not make an unreviewed HTTPS relay production-secure.

A public HTTPS relay or tunnel can improve reachability for QA outside one LAN, but it is
not a trusted privacy boundary. TLS protects the network hop to the endpoint; it does not
hide IP addresses, request timing, queue metadata, approximate message sizes, or traffic
frequency from the relay operator.

Before any broader test, a public relay requires short retention, payload-log
prohibition, full-queue-ID log prohibition, access control where appropriate, abuse
controls, monitoring, patching, rate limiting, and incident-response planning.

Historical VPS and endpoint records do not establish current availability, ownership,
configuration, security, or authorization.

## Device Compromise And Local Deletion

Android Keystore reduces direct key-extraction risk but does not make a compromised
device safe.

Malware with UI, accessibility, input, or operating-system privileges may read plaintext
displayed on screen, capture it before encryption, or act through the application while
keys are usable.

The current local deletion path removes Room application data, prototype sessions,
replay and retry state, disabled Signal-spike records, local queue state, nonce
preferences, and current Keystore aliases.

The saved relay URL is retained for repeated development and QA use.

Local deletion does not request deletion of every blob already present on a relay.
Remaining relay ciphertext is subject to normal receipt deletion, relay behavior, and
TTL.

Neither application deletion nor Android Keystore alias deletion can promise forensic
secure erase of historical flash-storage blocks.

Out of scope (AI audits 2026-10, STORE-01, DEC-028): an attacker who can write the app's
private files while the vault key stays the same, in practice with root access, could put
back older encrypted records of the same row, which still authenticate. The records carry no
freshness counter. Backup and device transfer of the database are disabled, so the app itself
never restores old state.

## Security Assumptions

The current implementation depends on the following assumptions:

- Android API 31+ Keystore correctly protects generated EC agreement and AES vault keys
  against direct extraction under the documented device conditions.
- The platform JCA AES-GCM and HMAC-SHA-256 implementations behave correctly.
- Nonce counter state is durably advanced before ciphertext is emitted.
- Session send state is persisted before network delivery begins.
- Database and preference backup remains disabled so old nonce and session state is not
  restored alongside existing keys.
- Room transactions provide the documented atomicity for accepted replay tuples and
  incoming encrypted messages.
- Identity authenticity is claimed only after the user compares the safety number or
  verification QR through a trusted or independent channel.
- Communication in `UNVERIFIED` / `PRIMARY_UNVERIFIED` state makes no identity-authenticity
  assumption and continues to display the required warning.

Failure of these assumptions can invalidate the documented controls.

## Future Metadata Reductions

The following are future proposals, not current protections:

- sealed-sender-style delivery;
- opaque or single-use queues;
- message padding;
- batching;
- relay chaining;
- Tor or reviewed P2P transport;
- removal of stable queue linkability.

Each requires separate protocol design, implementation, testing, migration analysis, and
security review before it can be described as implemented.
