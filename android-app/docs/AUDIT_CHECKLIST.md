# Audit Checklist

## Protocol And Feature Gates

- [ ] Confirm active chats instantiate `SimplifiedSessionManager`, with its non-production limitation visible in documentation.
- [ ] Confirm Signal-backed session feature flags remain disabled and no active delivery path calls the adapter.
- [ ] Confirm `PARANOIA_C4_AEAD` remains disabled and contains no fake or fallback implementation.

## Payload And Local Data

- [ ] Review `STANDARD_AEAD` nonce uniqueness and authenticated AAD enforcement.
- [ ] Confirm wrong AAD/tag fails before local plaintext acceptance.
- [ ] Confirm persisted messages and session records do not store plaintext payloads.
- [ ] Confirm private device keys remain under Android Keystore boundaries where designed.

## Relay Delivery

- [ ] Verify pending-before-send, retry metadata and WorkManager execution.
- [ ] Verify authenticate-before-store, atomic replay marker persistence and delete-after-accept.
- [ ] Verify duplicate/replay input cannot create duplicate visible plaintext.
- [ ] Verify pending, revoked, changed or missing primary devices fail closed.

## Diagnostics And Privacy

- [ ] Inspect copied diagnostic report: no plaintext, keys, nonce values, ciphertext or full queue IDs.
- [ ] Confirm queue identifiers appear only as short hashes and technical event log is capped at 100 entries.
- [ ] Confirm logs do not contain sensitive fields.
- [ ] Record relay metadata limitations and the visible configured relay URL.
- [ ] Inspect the preflight summary boundary and QA logcat redaction: no payload, key, nonce, ciphertext or full queue identifier.

## Debug And Release

- [ ] Confirm debug build visibly warns when LAN `http://` relay is configured.
- [ ] Confirm release build rejects insecure HTTP relay URLs and Android cleartext networking.
- [ ] Confirm LAN HTTP testing is described as debug-only, never production transport.
- [ ] Confirm debug-only test reset requires confirmation, clears local test/delivery/diagnostic state and intentionally preserves only Relay URL configuration.
- [ ] Confirm physical-phone warnings reject `127.0.0.1`, `localhost` and `0.0.0.0` as LAN client test addresses while emulator `10.0.2.2` is not misclassified.

## Evidence

- [ ] Run Gradle build, unit tests and lint.
- [ ] Run connected UI/storage/transport checks on a test Android device.
- [ ] Run relay Node tests.
- [ ] Record the two-device QA result using `MANUAL_TWO_DEVICE_QA.md` without exporting sensitive content.
- [ ] When troubleshooting, capture only sanitized evidence according to `LOGCAT_QA.md`.
