# Security-Critical Files

These files deserve focused review before extending the prototype or using its test
results as evidence.

| Area | File or directory | Review focus |
| --- | --- | --- |
| Active crypto | `core/crypto/src/main/kotlin/app/securemessenger/crypto/` | AEAD, nonce allocation, AAD, prototype session limitations, disabled C4 |
| Session activation | `core/ui/src/main/kotlin/app/securemessenger/ui/SecureMessengerViewModel.kt` | Confirms active path stays on `SimplifiedSessionManager`, key erasure and delivery wiring |
| Delivery | `core/delivery/src/main/kotlin/app/securemessenger/delivery/DeliveryPipeline.kt` | Authenticate-before-store, delete-after-accept, replay and retry behavior |
| Storage | `core/storage/src/main/kotlin/app/securemessenger/storage/` | Ciphertext-only records, vault use, atomic replay persistence |
| Relay URL policy | `core/transport/src/main/kotlin/app/securemessenger/transport/RelayServerSettings.kt` | Release rejection of `http://`, URL validation |
| Worker | `core/ui/src/main/kotlin/app/securemessenger/ui/RelayBackgroundSync.kt` | Retry/fetch execution, no sensitive logging, release relay policy |
| Diagnostics | `core/ui/src/main/kotlin/app/securemessenger/ui/SafeDeliveryDiagnostics.kt` | Redaction, capped events and absence of sensitive inputs |
| UI notices | `core/ui/src/main/kotlin/app/securemessenger/ui/SecureMessengerApp.kt` | Security warnings, pairing claims and copied report content |
| Android transport policy | `app/src/main/res/xml/network_security_config.xml`, `app/src/debug/res/xml/network_security_config.xml`, `core/ui/src/*/res/values/transport_policy.xml` | Cleartext allowed only in debug paths |
| Relay service | `server/relay/src/` | Opaque storage, TTL, health endpoint and log policy |
| Disabled migrations | `core/signalstore/`, `core/signal-protocol/` | Must remain disabled until separately reviewed |

Related review documents: `CRYPTO_DESIGN.md`, `THREAT_MODEL.md`,
`SECURITY_WARNINGS.md`, `LIBSIGNAL_TOOLCHAIN_REVIEW.md` and `AUDIT_CHECKLIST.md`.
