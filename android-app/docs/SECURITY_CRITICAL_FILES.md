# Security-Critical Files

These files deserve focused review before extending the protocol or using test results as
evidence. Paths are relative to `android-app/`. Updated 2026-10-01 (protocol v2, DEC-021;
Phase 5, DEC-026).

| Area | File or directory | Review focus |
| --- | --- | --- |
| libsignal store adapter | `core/signal-protocol/.../EncryptedSignalProtocolStore.kt`, `LocalSignalIdentityProvisioner.kt` | Strict identity trust (both directions), one-time Kyber deletion, last-resort base-key reuse rejection, buffer wiping |
| Sessions | `core/signal-protocol/.../SignalSessions.kt` | Address order, failure mapping (never mistake storage failures for bad messages), safety numbers |
| Invitation | `core/signal-protocol/.../InvitationPreKeys.kt`, `InvitationV2.kt` | Per-invitation one-time keys, sealed bundle, digest commitment, strict and canonical parsing, expiry, only a HELLO with exactly its pre-keys opens it, all pre-keys deleted once used |
| Wire formats | `core/signal-protocol/.../EnvelopeV2.kt`, `V2Binary.kt` | Version separation, bounds, padding, safe display names, envelope digest |
| Pipeline | `core/signal-protocol/.../ConversationV2Service.kt` | Lock + transaction per step, delete-after-commit, rejected envelopes rolled back and deleted (DEC-026), paged sync, ordered sending, request flow, contact-scoped receipts, untrusted identity on receive never changes state |
| Relay client | `core/transport/.../RelayHttpTransportClient.kt` | Bearer only in the header, bounded answers (size, count, time), one unreadable item never blocks the rest |
| Security test suites | `core/signal-protocol/src/test/.../V2DecoderFuzzTest.kt`, `V2AttackMatrixTest.kt`, `Fuzz.kt`; `core/transport/src/androidTest/.../RelayHostileResponseInstrumentedTest.kt` | What each test proves (`PHASE5_SECURITY_TEST_PLAN.md`); a weakened test weakens the evidence |
| Encrypted storage | `core/storage/src/main/kotlin/app/securemessenger/storage/` | Vault AAD binding, `RoomProtocolTransactionRunner`, `MIGRATION_7_8`, wipe completeness |
| Vault key lifecycle (DEC-023) | `core/storage/.../LocalVaultCipher.kt`, `LocalProfileRepository.kt`, `core/ui/.../SecureMessengerViewModel.kt`, `V2BackgroundSync.kt` (`V2Pipeline`) | Key created only with a new profile; lost/invalidated/replaced key detected by the profile canary and blocks everything; wipe deletes the key first; storage failures never become protocol verdicts |
| Legacy reset | `core/ui/.../LegacyReset.kt`, `SecureDatabase.MIGRATION_7_8` | Runs once, removes legacy key/preferences/tables, never migrates legacy state |
| Release gating | `core/ui/src/*/res/values/transport_policy.xml` (`enable_v2_protocol`), `V2Availability.kt`, `SecureMessengerApp.kt`, `V2BackgroundSync.kt` | v2 unreachable in release; cleartext only in debug |
| Background sync | `core/ui/.../V2BackgroundSync.kt` | No sensitive logging, availability gate, process-wide lock shared with the UI |
| UI | `core/ui/.../V2Screens.kt`, `ConversationV2ViewModel.kt` | Request acceptance, safety number display, delivery status wording |
| Android transport policy | `app/src/main/res/xml/network_security_config.xml`, `app/src/debug/res/xml/network_security_config.xml` | Cleartext allowed only in debug paths |
| Build and packaging | `app/build.gradle.kts`, `core/signal-protocol/consumer-rules.pro` | R8 keep rules, desugaring, excluded test/desktop natives, `qaMinified` |
| Relay URL policy | `core/transport/.../RelayServerSettings.kt` | Release rejection of `http://`, URL validation |
| Relay service | `server/relay/src/` | Opaque storage, self-certifying queues and Bearer reads (DEC-024), canonical base64 only, pages of 100, TTL, QA-level limits, log policy |

Related review documents: `PROTOCOL_V2.md`, `ai-workflow/DECISIONS.md` (DEC-018 to DEC-026),
`PHASE5_SECURITY_TEST_PLAN.md`, `THREAT_MODEL.md`, `SECURITY_WARNINGS.md`,
`LIBSIGNAL_TOOLCHAIN_REVIEW.md`, `AUDIT_CHECKLIST.md`.
