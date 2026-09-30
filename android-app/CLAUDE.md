# CLAUDE.md — Secure Messenger MVP (read every session)

@AGENTS.md

This file is loaded automatically. It repeats the parts of the project that are easiest to lose when
a long session is compacted. `AGENTS.md` is the full rule set; `docs/ai-workflow/` holds the
decisions (`DECISIONS.md`), task queue, plan (`REWRITE_PLAN.md`) and handoff. Verify anything
mutable against the code before relying on it.

## How to work with the owner

- **Answer in Russian.** The owner is not a cryptography specialist and writes casually; give
  plain-language options with a recommendation for owner-level decisions. Repository docs stay in
  English.
- **Before each phase, step or task, name the recommended model and effort** (e.g. "Opus 5.5 · High")
  and say when the owner must switch models. The owner switches manually.
- **Commits:** one commit round at the end of each phase/step/task, after everything is verified
  (code and docs may be separate commits), message ends with the Co-Authored-By trailer of the
  current model. **Never push**: the session has no GitHub credentials; the owner pushes. Report how
  many commits are ahead of `origin/main`. Never commit `local.properties`, build outputs, keys.
- **Tools live on drive D only** (JDK `D:\Tools\jdk-17`, SDK `D:\Tools\Android\Sdk`, Node
  `D:\Tools\nodejs`, Gradle home `D:\Tools\gradle-home`). Never install to C:.
- Report skipped checks as skipped, never as passed. Use the final report format in `AGENTS.md`.

## Non-negotiable security invariants (do not weaken; they are the product)

1. **v2 (official libsignal PQXDH + Double Ratchet) is the only chat path and is debug-only.**
   `enable_v2_protocol` is `false` in `core/ui/src/main/res/values/transport_policy.xml` (release)
   and `true` only in `src/debug`. Never turn it on for release before the Phase 5 independent
   review and explicit owner approval. Release must never construct the pipeline.
2. The legacy prototype (`SimplifiedSessionManager`, `STANDARD_AEAD`, v1 invitations, modules
   `:core:crypto`/`:core:delivery`) and `PARANOIA_C4_AEAD` are **deleted; never reintroduce** them
   or any home-grown cipher/cascade. Legacy data is wiped once (`MIGRATION_7_8`, `LegacyReset`).
3. **The relay is untrusted.** Never trust relay-supplied message IDs (DEC-020), ordering,
   availability or content. Relay copy is deleted **only after** the local transaction commits.
   Byte-identical re-deliveries are recognised by envelope digest without decryption.
4. **Strict identity trust in both directions.** An identity that differs from the stored one is
   never trusted. An untrusted-identity result on *receive* means "unauthenticated" and **must not
   change contact state** (otherwise a relay could block contacts with forged envelopes).
   `KEY_CHANGED` contacts can neither send nor receive. Unaccepted contact requests are not
   contacts and get no receipts. Unknown routing tags are never routed.
5. **Authenticate before store.** Each protocol step (decrypt+store, encrypt+enqueue) runs under the
   process-wide lock `ConversationV2Service.PROTOCOL_LOCK` in **one Room transaction** together
   with the libsignal store writes. The local wipe and the profile check before building a
   pipeline must also go through `ConversationV2Service.exclusively { }`. Those DAO methods are
   deliberately **blocking** (not `suspend`): never hold the lock across a suspension point.
6. **`Delivered`** = an authenticated receipt from the *same contact* naming the inner message ID.
   `Sent to delivery server` = relay acceptance only. Neither is a read receipt. WorkManager sync
   is best effort, never a timing guarantee.
7. Invitations (`smp-invite-v2:`): one-time EC and Kyber pre-keys per invitation, digest-committed
   sealed bundle, strict parsing, expiry, one-sided pairing with explicit accept (TOFU). Kyber:
   only `KEMKeyType.KYBER_1024`. One-time Kyber keys are deleted on use; base-key reuse is rejected.
8. Secrets: never log plaintext, keys, ciphertext, full queue IDs. Message bodies, names and QR text
   are vault-encrypted (Keystore AES-GCM, row-bound AAD); libsignal records likewise. The identity
   private key is a software key encrypted at rest. Flash gives no secure-erase guarantee.
9. Release rejects cleartext `http://` relays. Debug allows it for QA only.
10. Changing wire formats, Room schema, invitation/envelope/receipt formats or vault serialization
    needs the compatibility write-up in `AGENTS.md` first, plus tests for safe failure.
11. **The vault key is created in exactly one place** (DEC-023): `LocalProfileRepository.createProfile`
    on an empty vault. Encrypt/decrypt must never create a key; a missing or invalidated key throws
    `VaultKeyUnavailableException`. The profile row is the canary (`localDataState()`): if it does
    not decrypt, nothing runs, and the user sees an explanation with "Check again" and a confirmed
    "Delete local data and start over" — never an automatic wipe. The wipe deletes the key first.
    A storage failure must never be turned into a protocol verdict or a "not stored" result.

## libsignal traps (learned the hard way)

- `SessionBuilder(store, remote, local)` but `SessionCipher(store, local, remote)`: the address
  argument order differs. Get it wrong and sessions silently fail.
- libsignal address names are local-only labels; they are not sent on the wire.
- A PreKey message's *claimed* identity is checked before authentication (see invariant 4).
- Versions are pinned: libsignal `0.94.1`, Kotlin `2.2.20`, coroutines `1.10.2` (libsignal's own
  POM versions). Do not bump them inside unrelated work. AGP 9 is not adopted (DEC-022).
- libsignal-android needs core library desugaring (`desugar_jdk_libs`). Packaging must exclude
  `libsignal_jni_testing.so` and the desktop natives (`*.dll`, `*.dylib` are Java resources of
  `libsignal-client`). R8 keep rules: `core/signal-protocol/consumer-rules.pro`.
- Forward secrecy and post-compromise security are **proven by tests** (`ForwardSecrecyTest`,
  `ConversationV2RoomProofInstrumentedTest`), including the honest boundary: stored history is not
  forward secret against a thief who also has the vault key.

## Build and test facts

- Machine has ~3.9 GB RAM. Close games/heavy programs first; below ~1.3 GB free the JVM cannot
  start. Run from `android-app` with per-command env and low-memory flags:
  `$env:JAVA_HOME='D:\Tools\jdk-17'; $env:ANDROID_HOME='D:\Tools\Android\Sdk'; $env:GRADLE_USER_HOME='D:\Tools\gradle-home'`
  and `"-Dorg.gradle.jvmargs=-Xmx1280m -Dfile.encoding=UTF-8" "-Pkotlin.compiler.execution.strategy=in-process" --max-workers=1 --console=plain`.
- Current toolchain (TQ-009, DEC-022): AGP 8.13.2, Gradle 8.14.3, compileSdk 36, targetSdk 34,
  minSdk 31, Room 2.8.5 (KAPT), Compose BOM 2025.10.01. Libraries needing compileSdk 37 wait.
- Device tests: Samsung S24 Ultra `R5CXA09HX0X` and Redmi A3x `EE421L001242` (Android 16 arm64),
  `adb` = `D:\Tools\Android\Sdk\platform-tools\adb.exe`. Wake the screens first (a dark screen
  makes Compose tests time out). Relay: `node D:\Tools\probe\relay-run.mjs` (a wrapper, because
  `server.mjs` does not start directly on Windows), then `adb -s <serial> reverse tcp:8787 tcp:8787`.
  Airplane mode does not cut the USB relay; stop the relay process to simulate an outage.
- PowerShell 5.1 traps: `Set-Content -Encoding utf8` writes a BOM (breaks TOML/properties; write
  with `[IO.File]::WriteAllText` and `UTF8Encoding($false)`); backticks inside double-quoted
  strings are escapes; files with Cyrillic need a UTF-8 BOM to be read as UTF-8.
- Room schemas are exported to `core/storage/schemas` and must be committed with every version
  (currently 8.json). Migrations have instrumented tests.

## Where things stand (update when it changes)

- Phase 1 of `docs/ai-workflow/REWRITE_PLAN.md` is done (exit gate met, 2026-09-30); TQ-009 done;
  Phase 2 storage hardening done (DEC-023). Next: Phase 3 relay auth (changes the relay API and
  likely the invitation: design and owner decisions first), then Phase 4 multi-device, Phase 5
  independent audit (gate for release chat).
- Not production-ready; no independent audit; do not claim otherwise.
