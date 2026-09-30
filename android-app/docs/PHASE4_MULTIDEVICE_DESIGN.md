# Phase 4 design — several devices per person, and revocation (DEFERRED)

Status: **DEFERRED by the owner on 2026-10-01 (DEC-025).** Version 1 of the app is one device per
person (like Telegram's secret chats). This draft stays as the starting point when the phase is
resumed; the owner's answers already given are recorded here and in DEC-025:

- D2 identity model: **A** — own key per device, device list signed by the primary device.
- D3 a contact's new or removed device: **ask for confirmation**; nothing is sent to a contact's
  new device until the user confirms it (stricter than the drafted recommendation of a notice).
- D4 "Delivered": **at least one** of the contact's devices confirmed.

Resuming the phase means another protocol change and another security review (Phase 5 will not
cover multi-device).

## 1. Current state (from the code)

- One device per contact. `SignalSessions.DEVICE_ID = 1`; a `v2_contacts` row holds one identity
  key, one peer queue ID and one routing-tag pair; HELLO/ACCEPT set them up.
- Each install has one libsignal identity (created with the pipeline). The safety number is
  computed from the two identity keys.
- Storage addresses already carry a device ID (`SignalProtocolAddress(recipientId, 1..127)`).
- No linking, no device list, no revocation.

## 2. Goal and exit gate (`REWRITE_PLAN.md`)

- A contact with two approved devices receives on both, each over its own Double Ratchet session.
- Revoking one device stops delivery to it without the other side having to revoke first.
- Fail-closed defaults stay: unknown or unverified devices are never sent to or accepted from.

Proposed non-goals for this phase (v1): moving old history to a new device; more than 5 devices
per person; recovery after losing the primary device; groups.

## 3. Identity model (decision D2)

| | **A. Own key per device, device list signed by the primary (recommended)** | B. One shared key on all devices (Signal-like) |
| --- | --- | --- |
| How | Each device has its own libsignal identity. The primary device's identity key is the *account key*; it signs a versioned list of the account's devices (their identity keys and queue IDs). | Linking copies the identity private key to the new device. |
| Safety number | Unchanged: computed over account keys, as today. | Unchanged. |
| Revocation | The primary publishes a new signed list without the device. The removed device has no account key, so it cannot re-add itself. Real revocation. | The removed device still holds the shared key and could keep announcing itself. Signal relies on its account server to stop that; our relay is untrusted and has no accounts, so true revocation would need a new key for everyone (new safety number). |
| Stolen linked device | Loses access once contacts apply the new list. | Keeps the account key: a full account compromise. |
| Cost | A signed device list, its verification, and per-device identities. | Simpler linking; no working revocation. |
| Primary lost | Linked devices keep working but cannot add/remove devices; recovery = new account and re-pairing (v1). | Any device can continue. |

## 4. Linking a new device (recommended flow)

1. On the new phone, instead of "create profile", the user picks "link to my existing phone". The
   new device creates its own identity, relay queue and one-time pre-keys and shows a link QR
   (`smp-link-v1:`, same sealed-bundle and HKDF construction as invitation v3).
2. The primary scans it. Both screens show the same short device code; the user compares and
   confirms. Warning text: only link your own device.
3. The primary opens a session with the new device and sends, inside it: the new signed device
   list, the profile name, and a snapshot of the contacts (for each: account key, device list,
   queue IDs). Nothing of the old history.
4. The new device prepares one one-time bundle per contact device; the primary forwards each over
   its existing session with that contact device, together with the new signed device list.
5. Each contact device checks the list (signature by the account key it already trusts, version
   higher than the last one seen, the bundle's key is in it), opens a session with the new device
   automatically and says hello. The chat shows a notice (decision D3).

## 5. Messages across devices

- Sending a text: one envelope per contact device (own session, queue, routing tag) plus one
  "sent from my other device" copy per own other device, so all my devices show the conversation.
  All copies share the inner message ID.
- Receiving: each of my devices gets its own copy and sends its own receipt.
- "Delivered" (decision D4): recommended "at least one of the contact's devices confirmed".
- A new device starts with an empty history.

## 6. Revocation

- Only the primary adds or removes devices (it alone holds the account key). Removing publishes a
  new list version to every contact device and every own device; they delete the removed device's
  sessions and refuse it from then on. The removed device is asked to wipe itself (best effort; it
  cannot be forced). A linked device can unlink itself; the primary then publishes the new list.
- A contact that is offline applies the new list at its next sync; until then it may still send to
  the removed device. Documented limit.
- Lists are versioned; an older or equal version (a replay by the relay) is ignored.

## 7. Losing the primary device

v1: linked devices keep working with existing contacts but cannot add or remove devices; a new
primary means a new account key (contacts see a new safety number and pair again). A recovery key
or "promote a linked device" can come later.

## 8. Storage, wire formats, migration

- New tables (Room v10): own devices, contact devices (per contact: device number, identity key,
  queue ID, routing tags, state), the latest verified device list per account, and an outbox row
  per destination device.
- Wire: inner payload version 3 with new types (device list, link messages, device announcement,
  device hello, sent-copy sync). Invitation v4: the bundle also carries the inviter's signed device
  list, so a new contact learns all devices at pairing. Older versions refused, never reinterpreted.
- Existing contacts are migrated as single-device accounts (device 1, list version 0) — no reset
  needed. Their app will publish a signed list at the first change.

## 9. Threat review (summary)

- **Compromised primary** adds an attacker's device and receives future messages. The safety number
  does not change (same account key), so the chat notice (D3) and the visible device list with
  device codes are the countermeasures.
- **Relay** cannot forge lists (signed) and cannot roll them back (monotonic versions). It learns
  the number of devices and their queue IDs (metadata).
- **Revoked or stolen linked device** keeps what it already stored; it gets no new messages once
  contacts apply the list and cannot send (refused), nor re-add itself.
- **Link QR** shown by an attacker to trick the user into linking a foreign device: the device-code
  comparison and the warning; linking only ever happens on the primary after explicit confirmation.

## 10. Sub-steps (one commit at the end of the phase, owner rule)

- 4b: device list format, signing and verification, storage v10 and migration (JVM + device tests).
- 4c: fan-out send/receive for contacts with several devices (JVM tests with 2 + 2 devices).
- 4d: linking flow (link QR, session, contact snapshot, announcements).
- 4e: sent-copy sync between own devices, receipts across devices.
- 4f: revocation.
- 4g: UI (link, device list with codes, remove, notices).
- 4h: manual QA with four app instances on two phones: the debug (`app.securemessenger`) and QA
  (`app.securemessenger.qa`) builds install side by side, so each phone can be two devices.

This is the largest phase so far (comparable to or bigger than step 1e).
