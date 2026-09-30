# Security-Critical Files

These files deserve focused review before extending the protocol or using test results as
evidence. Paths are relative to `android-app/`. Updated 2026-09-30 (protocol v2, DEC-021).

| Area | File or directory | Review focus |
| --- | --- | --- |
| libsignal store adapter | `core/signal-protocol/.../EncryptedSignalProtocolStore.kt`, `LocalSignalIdentityProvisioner.kt` | Strict identity trust (both directions), one-time Kyber deletion, last-resort base-key reuse rejection, buffer wiping |
| Sessions | `core/signal-protocol/.../SignalSessions.kt` | Address order, failure mapping (never mistake storage failures for bad messages), safety numbers |
| Invitation | `core/signal-protocol/.../InvitationPreKeys.kt`, `InvitationV2.kt` | Per-invitation one-time keys, sealed bundle, digest commitment, strict parsing, expiry |
| Wire formats | `core/signal-protocol/.../EnvelopeV2.kt`, `V2Binary.kt` | Version separation, bounds, padding, safe display names, envelope digest |
| Pipeline | `core/signal-protocol/.../ConversationV2Service.kt` | Lock + transaction per step, delete-after-commit, request flow, contact-scoped receipts, untrusted identity on receive never changes state |
| Encrypted storage | `core/storage/src/main/kotlin/app/securemessenger/storage/` | Vault AAD binding, `RoomProtocolTransactionRunner`, `MIGRATION_7_8`, wipe completeness |
| Legacy reset | `core/ui/.../LegacyReset.kt`, `SecureDatabase.MIGRATION_7_8` | Runs once, removes legacy key/preferences/tables, never migrates legacy state |
| Release gating | `core/ui/src/*/res/values/transport_policy.xml` (`enable_v2_protocol`), `V2Availability.kt`, `SecureMessengerApp.kt`, `V2BackgroundSync.kt` | v2 unreachable in release; cleartext only in debug |
| Background sync | `core/ui/.../V2BackgroundSync.kt` | No sensitive logging, availability gate, process-wide lock shared with the UI |
| UI | `core/ui/.../V2Screens.kt`, `ConversationV2ViewModel.kt` | Request acceptance, safety number display, delivery status wording |
| Android transport policy | `app/src/main/res/xml/network_security_config.xml`, `app/src/debug/res/xml/network_security_config.xml` | Cleartext allowed only in debug paths |
| Build and packaging | `app/build.gradle.kts`, `core/signal-protocol/consumer-rules.pro` | R8 keep rules, desugaring, excluded test/desktop natives, `qaMinified` |
| Relay URL policy | `core/transport/.../RelayServerSettings.kt` | Release rejection of `http://`, URL validation |
| Relay service | `server/relay/src/` | Opaque storage, TTL, health endpoint, log policy (no auth yet: Phase 3) |

Related review documents: `PROTOCOL_V2.md`, `ai-workflow/DECISIONS.md` (DEC-018 to DEC-021),
`THREAT_MODEL.md`, `SECURITY_WARNINGS.md`, `LIBSIGNAL_TOOLCHAIN_REVIEW.md`, `AUDIT_CHECKLIST.md`.
