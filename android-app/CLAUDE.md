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
  Current recommendations: Phase 6 (Telegram-style UI), steps 6a and 6b done; TQ-109 (system Back)
  and TQ-110 (fixes from the AI audits, DEC-028) done on 2026-10-03; step 6c (theme and
  components) done 2026-10-04 (owner approved; the Redmi device run is still owed).
  Next **6d** (chat list), 6e and 6f on Sonnet; **Opus 5.5 · High** for the Room v10
  migration (6d), the verified-mark rules (6f), the notification privacy rules (6f') and TQ-108
  (6g). Routine test cases, docs, dependency updates: Sonnet 5.5 · High.
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
   and `true` only in `src/debug`. Never turn it on for release before the independent external
   audit (TQ-106, planned before any release, DEC-026) and explicit owner approval. Release must never construct the pipeline.
2. The legacy prototype (`SimplifiedSessionManager`, `STANDARD_AEAD`, v1 invitations, modules
   `:core:crypto`/`:core:delivery`) and `PARANOIA_C4_AEAD` are **deleted; never reintroduce** them
   or any home-grown cipher/cascade. Legacy data is wiped once (`MIGRATION_7_8`, `LegacyReset`).
3. **The relay is untrusted.** Never trust relay-supplied message IDs (DEC-020), ordering,
   availability or content. An accepted envelope's relay copy is deleted **only after** the local
   transaction commits; a rejected one commits nothing (rolled back) and is deleted too; only a
   local failure keeps it (DEC-026). Byte-identical re-deliveries are recognised by envelope digest
   without decryption. A contact's envelopes are sent strictly in order.
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
7. Invitations (`smp-invite-v3:`, v2 refused, one canonical text): one-time EC and Kyber pre-keys per
   invitation, digest-committed sealed bundle, strict parsing, expiry, one-sided pairing with
   explicit accept (TOFU). Kyber: only `KEMKeyType.KYBER_1024`. Only a HELLO using exactly the
   invitation's three pre-keys opens it; all its private pre-keys are deleted once used (DEC-026).
   One-time Kyber keys are deleted on use; base-key reuse is rejected.
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
  lock.
- **Telegram for Android's code (DEC-027):** the project is AGPL-3.0 (`LICENSE`,
  `THIRD_PARTY_NOTICES.md` at the repository root). Check the header of every Telegram file before
  using it: 416 of 1,836 say "GPL v. 2 or later" (fine), 54 are Apache-2.0 (keep their notices), and
  1,366 have no header: those MAY be used **at the owner's knowingly accepted legal risk**, NOT
  confirmed by any lawyer (two documents the owner received were not lawyer's opinions) — never say
  "legally cleared"; list each such file in the notices as "no header; accepted risk"; prefer
  writing a piece anew when that is easy. An explicit "GPL v2 only" marking would exclude a file
  (none found). Keep original headers; never their name, logo, icons, sounds, fonts or images.
  Reference copies (read only, outside the repo): `D:\Tools\reference\Telegram` (commit `f2908b1`)
  and `D:\Tools\reference\themes` (five `.attheme` color files).
- Phase 2 found that libsignal wraps *checked* exceptions from store callbacks in `AssertionError`:
  keep every vault/storage failure unchecked (`VaultException`).
- Phase 5 found (tests prove it): libsignal raises only its checked protocol exceptions on hostile
  ciphertext and writes nothing on a failed decrypt; but a damaged *stored* session record gives
  the unchecked `InvalidSessionException` (`IllegalStateException`), now mapped to
  `StoredRecordCorruptedException`. A PreKey message's outer fields (e.g. registration ID) are not
  covered by its MAC: a changed copy can decrypt, but only to the original plaintext.
- Design lesson (Phase 5): never keep "not yet valid" envelopes on a paged relay queue; someone who
  can produce them blocks the head of the queue. Fix ordering at the sender instead.
- Second review lessons (Phase 5): libsignal hands PreKey key IDs above 2^31 to the store as
  negative ints, and the Room repository's `require(id >= 0)` turned one crafted message into a
  queue-stalling exception (R1, High). Test doubles must enforce the production rules, and attack
  tests must craft fields directly (`PreKeyMessageFields`), since byte mutations never produce
  them. Android opened Room in WAL with `synchronous` NORMAL on both phones (one had
  `secure_delete` FAST): measure platform defaults on the device before relying on them.

## Decision log (details in `docs/ai-workflow/DECISIONS.md`)

DEC-018 strict identity trust; DEC-019 invitation/pairing design; DEC-020 relay message IDs never
trusted; DEC-021 legacy prototype removed, v2 debug-only, release has no chat until Phase 5;
DEC-022 AGP 8.13.2 / compileSdk 36; DEC-023 vault key never replaced silently; DEC-024 relay API v3
+ invitation v3 + Room v9; DEC-025 multi-device deferred; DEC-026 Phase 5 fixes (rejected
envelopes deleted and rolled back, relay pages of 100 and canonical base64, bounded transport,
ordered sending, strict invitations; paid audit before release); DEC-027 Phase 6 (AGPL-3.0
`LICENSE`, reuse of Telegram for Android code incl. headerless files at the owner's accepted legal
risk, unread counters on Room v10, notifications without text). Design docs:
`docs/PROTOCOL_V2.md`, `docs/PHASE3_RELAY_DESIGN.md`, `docs/PHASE4_MULTIDEVICE_DESIGN.md`
(deferred), `docs/PHASE5_SECURITY_TEST_PLAN.md`, `docs/PHASE6_UI_PLAN.md`,
`docs/PHASE6_THEME_NOTES.md`.

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

- **Phase 5 — security test pass: DONE** (2026-10-01, `docs/PHASE5_SECURITY_TEST_PLAN.md`,
  DEC-026). Still open after it: TQ-108 (with Phase 6), key hardening (StrongBox /
  unlocked-device keys), relay TTL, lint-disabled checks, pinned libraries. The paid external
  audit (TQ-106) is planned before any release.
- **Phase 6** Telegram-style UI (in progress, `docs/PHASE6_UI_PLAN.md`, DEC-027: Compose; ported
  Telegram code per the licensing rules above; unread counters on Room v10; notifications without
  text); it also carries TQ-108. **TQ-109** (system Back goes one screen back) and **TQ-110**
  (fixes from the AI audits, DEC-028) are done (2026-10-03). **Step 6b done**
  (`docs/PHASE6_PORTING_NOTES.md`: bubble geometry 17/6 dp radii and
  tail, series = same direction within 5 minutes, time-on-last-line rule, avatar initials and 7
  gradients, chat-list row 70 dp / avatar 52 / badge 20.67 dp, date rules, header and input bar
  sizes; we write Compose code from these notes, nearly no Telegram code is copied). Owner's style:
  **classic palette** (outgoing `#EFFFDE` green ticks, incoming white, accent `#229AF0`), **own light
  wallpaper pattern**, **dark theme (Night values) with an in-app switch** Light/Dark/System. Next:
  6c theme and components, 6d chat list with
  unread marker (Room v10, Opus for the migration), 6e conversation, 6f screens and 6f'
  notifications, 6g TQ-108, 6h usability check. The owner will send screenshots and screen
  recordings of Telegram for the parts that are rebuilt, not ported. Ideas to design with it:
  disappearing messages, app lock. **Phase 7** release readiness (signing, SBOM, HTTPS relay) is
  out of scope until the owner wants to ship.
- **Owner facts worth keeping:** the owner (not a crypto or legal specialist) wants a UI that looks
  and behaves like Telegram; is relaxed about licensing but accepted the risk knowingly; pushes to
  GitHub themselves (last push: all commits up to `335ccca`); the phones are often disconnected
  between sessions (ask them to connect Samsung `R5CXA09HX0X` and Redmi `EE421L001242` and wake the
  screens before device tests); expects every session answer in Russian, short when asked.
- **Legal documents trap:** the owner received two `.docx` "Telegram GPLv3" papers (2026-10-01);
  neither is a lawyer's opinion (one says it was prepared by ChatGPT and needs a specialist's check,
  the other has no author and contradicts itself). Do not record or say that a lawyer cleared
  anything. Only a signed written opinion would change that.
- **AI audits are not the independent audit:** the owner also has other AI tools review the project
  (2026-10: ZCode on the repository, Codex on a copy at `D:\Messenger-Audit`). Their reports are
  internal checks like the Phase 5 reviews, never TQ-106, and never open the release gate. Check
  every finding against the code before acting (triage: `docs/AI_AUDITS_2026-10.md`, DEC-028). Such
  tools may leave working files in the project folder (`.zcodeignore`, `SECURITY_AUDIT.md`,
  `PROJECT_CONTEXT_FOR_HANDOFF.md`): never commit them; ask the owner (in 2026-10 they were moved
  to the owner's desktop).
- Other open tasks: TQ-006 clean-clone and CI run, TQ-007 (signing, `lintRelease`, manifest audit),
  TQ-008 dependency verification/SBOM, `qaMinified` build not re-run after TQ-009, libraries that
  need compileSdk 37 (Compose 1.12, core 1.19, lifecycle 2.11), Kotlin/coroutines pinned to libsignal.
- Owner's environment: Windows laptop with ~3.9 GB RAM; the owner plays Hidden Deep on it and the
  Samsung, so stop Gradle/relay when idle; the owner pushes to GitHub themselves.
## Where things stand (update when it changes)

- Phase 1 of `docs/ai-workflow/REWRITE_PLAN.md` is done (exit gate met, 2026-09-30); TQ-009 done;
  Phase 2 storage hardening done (DEC-023); Phase 3 relay hardening done (DEC-024: self-certifying
  queues, invitation v3, Room v9). Phase 4 multi-device DEFERRED by the owner (DEC-025; version 1
  is one device per person; draft design kept). Phase 5 security test pass done (DEC-026). Phase 6
  Telegram-style UI in progress: step 6a done (plan, `LICENSE`, `THIRD_PARTY_NOTICES.md`, DEC-027,
  committed `996a004`), step 6b done; TQ-109 (system Back) and TQ-110 (AI-audit fixes, DEC-028)
  done 2026-10-03.
- Verified state after TQ-110 (2026-10-03): relay tests 15; JVM tests green (signal-protocol 75,
  storage 5, ui 17, signalstore 3, domain 3); per phone storage 31, transport 13 (live relay),
  signal-protocol 75, ui 27; lint 0 errors (16 warnings); the owner's hand check passed. Release
  build not re-run since Phase 5 (no release-relevant change). Earlier, end of Phase 5
  (2026-10-01): release with R8, no chat.
- The Redmi now has font scale 1.15: lazy lists may not compose items below the fold, so device
  tests must scroll a list with `performScrollToNode`, never `performScrollTo` on a node that may not
  exist yet (`InvitationSharingInstrumentedTest`, 2026-10-03).
- Device tests: a phone lying sideways runs the tests in landscape (that is how F12 was found);
  `LanguageSwitchHarnessActivity` follows the sensor.
- Reference material outside the repo (read only): Telegram for Android Java sources
  `D:\Tools\reference\Telegram` (sparse shallow clone of `org/telegram/ui` and
  `org/telegram/messenger`, commit `f2908b1`; other blobs can be read with `git show HEAD:<path>`)
  and `D:\Tools\reference\themes` (five `.attheme` color files). Survey of 1,836 Java files:
  416 "GPL v. 2 or later" header, 54 Apache-2.0, 1,366 no header, 0 marked "v2 only".
- Back navigation: no navigation component; screens are state variables (`Screen` in
  `SecureMessengerApp.kt`, `V2Screen` in `V2Screens.kt`); system Back is handled with `BackHandler`
  enabled only on screens that have a parent (`SystemBackInstrumentedTest` pins it).
- Not production-ready; no independent audit; do not claim otherwise.
