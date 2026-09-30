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
- **Model check on every "go" (owner rule, 2026-09-30).** Each time the owner says a step is done
  or tells you to start the next phase/step/task, FIRST compare the model that is active now (the
  system prompt says "You are powered by the model named ..."; a `/model` line in the message also
  shows a switch) with the model you recommended for that step (see `docs/ai-workflow/HANDOFF.md`
  "Next step" and `REWRITE_PLAN.md`). If it does not match, **do not start**: say in Russian which
  model is active, which one the step needs, and ask whether to continue anyway or wait for the
  switch. If it matches, start. **Effort cannot be read reliably** (the internal `reasoning_effort`
  number varies per message and has no known mapping to Low/Medium/High/Extra/Max): never claim
  the effort is wrong; name the effort the step needs and trust what the owner's UI shows.
  When you recommend a model, also write it into `HANDOFF.md` so this check survives compaction.
  Current recommendations: Phase 5 (security test pass: list untrusted inputs, fuzz and attack
  matrix): **Opus 5.5 · High**. Routine test cases, docs, dependency updates: Sonnet 5.5 · High.
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
7. Invitations (`smp-invite-v3:`, v2 refused): one-time EC and Kyber pre-keys per invitation, digest-committed
   sealed bundle, strict parsing, expiry, one-sided pairing with explicit accept (TOFU). Kyber:
   only `KEMKeyType.KYBER_1024`. One-time Kyber keys are deleted on use; base-key reuse is rejected.
8. Secrets: never log plaintext, keys, ciphertext, full queue IDs. Message bodies, names and QR text
   are vault-encrypted (Keystore AES-GCM, row-bound AAD); libsignal records likewise. The identity
   private key is a software key encrypted at rest. Flash gives no secure-erase guarantee.
9. Release rejects cleartext `http://` relays. Debug allows it for QA only.
10. Changing wire formats, Room schema, invitation/envelope/receipt formats or vault serialization
    needs the compatibility write-up in `AGENTS.md` first, plus tests for safe failure.
12. **Relay queues are self-certifying** (DEC-024, relay API v3): queue ID = `q3-` + base64url(
    SHA-256("smp-relay-queue-v1\0" || readSecret)). Only the read secret reads or deletes (Bearer),
    anyone may send; the relay assigns message IDs; deletion is bound to the queue. Never put the
    read secret in a URL, a log or plain preferences (it is vault-encrypted), never share it with
    contacts (they get the queue ID only). The invitation (`smp-invite-v3:`) carries one secret; the
    seal key and mailbox read secret are HKDF-derived with distinct labels.
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
  makes Compose tests time out). Relay: from `android-app\server\relay` run `node src/server.mjs` (starts directly on Windows since
  Phase 3; `HOST=127.0.0.1` to bind locally), then `adb -s <serial> reverse tcp:8787 tcp:8787`;
  relay tests: `node --test` in that folder.
  Airplane mode does not cut the USB relay; stop the relay process to simulate an outage.
- PowerShell 5.1 traps: `Set-Content -Encoding utf8` writes a BOM (breaks TOML/properties; write
  with `[IO.File]::WriteAllText` and `UTF8Encoding($false)`); backticks inside double-quoted
  strings are escapes (they silently mangled Kotlin/SQL text twice: edit code with the Edit tool,
  not with PowerShell string replacement); a one-pair array needs `(,@(a,b))`; `Start-Process`
  has no `-Environment`; files with Cyrillic need a UTF-8 BOM to be read as UTF-8; `git commit -m`
  with a multi-line here-string breaks, use `git commit -F file`.
- Room schemas are exported to `core/storage/schemas` and must be committed with every version
  (currently 9.json). Migrations have instrumented tests.

## Findings and owner ideas worth remembering

- **Honest forward-secrecy boundary** (explained to the owner 2026-09-30): the relay never reads
  messages (end-to-end). Forward secrecy means a thief who steals the device state cannot decrypt
  messages already received (their keys are erased); a message not yet fetched when the state was
  stolen is decryptable with a copy of its ciphertext; after one reply round trip a passive thief
  is locked out (post-compromise security). Stored history on the phone is NOT forward secret
  against someone who can also use the Keystore key. Mitigations to design later: shorter relay
  TTL (now 24 h), hardware-bound/unlocked-device keys (Phase 5), app lock, disappearing messages.
- Owner ideas, not yet tasks (recorded in `TASK_QUEUE.md`, "Deferred"): disappearing messages, app
  lock, and a Telegram-style UI shell on top of this core. Telegram's Android client is GPL and
  speaks Telegram's own protocol; libsignal is AGPL-3.0 — do not copy its code; re-implement the
  look in this project's own Compose UI, after an owner decision and a legal check.
- Phase 2 found that libsignal wraps *checked* exceptions from store callbacks in `AssertionError`:
  keep every vault/storage failure unchecked (`VaultException`).

## Decision log (details in `docs/ai-workflow/DECISIONS.md`)

DEC-018 strict identity trust; DEC-019 invitation/pairing design; DEC-020 relay message IDs never
trusted; DEC-021 legacy prototype removed, v2 debug-only, release has no chat until Phase 5;
DEC-022 AGP 8.13.2 / compileSdk 36; DEC-023 vault key never replaced silently; DEC-024 relay API v3
+ invitation v3 + Room v9; DEC-025 multi-device deferred. Design docs: `docs/PROTOCOL_V2.md`,
`docs/PHASE3_RELAY_DESIGN.md`, `docs/PHASE4_MULTIDEVICE_DESIGN.md` (deferred).

## Working cycle that has worked (repeat it)

1. On the owner's "go": model check (see above), then read the plan section and the code first.
2. For protocol, storage or API changes: write the design and the compatibility checklist, ask the
   owner the decisions with plain-language options and a recommendation, record them in `DECISIONS.md`.
3. Implement; run unit tests, then device tests on BOTH phones, lint, and the release build (check
   `enable_v2_protocol` is `false` in the APK with `aapt2 dump resources`); the owner checks the
   build by hand on the phones ("всё проверил, можешь коммитить").
4. Only then commit: code and docs as separate commits, message file + `git commit -F`, trailer of
   the current model. Update `HANDOFF.md`, `REWRITE_PLAN.md`, this file. Stop Gradle
   (`gradlew --stop`) and the relay afterwards so the owner can play games (RAM).
5. Tell the owner how many commits are ahead of `origin/main` and give the push command:
   `git -C D:\Messenger push origin main` (the session cannot push).
6. Final report in Russian with the `AGENTS.md` fields (incl. v2 / `enable_v2_protocol` / libsignal
   in release / format changes / legacy reintroduced).

## Open items and next steps

- **Phase 5 — security test pass** (next): list every untrusted input (invitation code and bundle,
  envelope, inner payload, relay JSON responses, vault blobs, stored records) and the property each
  test must prove; fuzz decoders (reject cleanly, never crash, never partially trust); replay /
  tamper / downgrade matrix for v2; optionally an external review of the libsignal integration.
  Revisit key hardening (StrongBox / unlocked-device keys), relay TTL, and whether lint-disabled
  checks or pinned libraries need attention.
- **Phase 6** Telegram-style UI (own Compose code, not Telegram's GPL code); ideas to design with
  it: disappearing messages, app lock. **Phase 7** release readiness (signing, SBOM, HTTPS relay)
  is out of scope until the owner wants to ship.
- Other open tasks: TQ-006 clean-clone and CI run, TQ-007 (signing, `lintRelease`, manifest audit),
  TQ-008 dependency verification/SBOM, `qaMinified` build not re-run after TQ-009, libraries that
  need compileSdk 37 (Compose 1.12, core 1.19, lifecycle 2.11), Kotlin/coroutines pinned to libsignal.
- Owner's environment: Windows laptop with ~3.9 GB RAM; the owner plays Hidden Deep on it and the
  Samsung, so stop Gradle/relay when idle; the owner pushes to GitHub themselves.
## Where things stand (update when it changes)

- Phase 1 of `docs/ai-workflow/REWRITE_PLAN.md` is done (exit gate met, 2026-09-30); TQ-009 done;
  Phase 2 storage hardening done (DEC-023); Phase 3 relay hardening done (DEC-024: self-certifying
  queues, invitation v3, Room v9). Phase 4 multi-device DEFERRED by the owner (DEC-025; version 1
  is one device per person; draft design kept). Next: Phase 5 security test pass (fuzzing, replay /
  tamper / downgrade matrix; Opus 5.5 · High), then Phase 6 Telegram-style UI.
- Verified state at the end of Phase 3 (2026-10-01): relay tests 8; JVM tests green; per phone\n  storage 27, ui 21, signal-protocol 49, transport 7; lint 0 errors; release has no chat.\n- Not production-ready; no independent audit; do not claim otherwise.
