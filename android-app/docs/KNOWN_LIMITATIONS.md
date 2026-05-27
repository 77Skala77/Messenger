# Known Limitations

## Security Boundary

The application is an MVP prototype, not a production-secure messenger. Active chats
use `SimplifiedSessionManager`; there is no active PQXDH/Double Ratchet session.
Signal-backed sessions remain disabled, and `PARANOIA_C4_AEAD` remains a disabled
placeholder rather than a secure mode.

## Delivery And Metadata

- The debug LAN relay uses HTTP for test convenience and exposes network traffic to the local network.
- The relay can observe client IP addresses, queue identifiers, timestamps, message sizes and frequency.
- The relay can delete, delay or withhold encrypted blobs; authenticated encryption does not prevent denial of service.
- WorkManager retries are best effort and may be delayed by Android scheduling.

## Devices And Storage

- Multi-device fan-out is not implemented; delivery targets a single usable primary device.
- Revocation is local policy state, not a synchronized cryptographic revocation mechanism.
- Plaintext necessarily exists in memory while entering or displaying a message.
- Keystore-backed encryption does not protect an unlocked compromised device.
- Local wipe does not guarantee forensic erasure from flash storage.

## Diagnostic Boundary

Delivery diagnostics intentionally show technical statuses, counts, timestamps and
short SHA-256 fingerprints of queue identifiers. They do not include plaintext,
cryptographic key material, nonce values, ciphertext blobs or full queue identifiers.
The configured relay URL is visible to the local user because it is needed to diagnose
the LAN test connection.
