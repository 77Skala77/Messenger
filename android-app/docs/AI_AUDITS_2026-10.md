# AI security audits of October 2026 — triage

Status: **TRIAGED, FIXES DONE** (2026-10-03, DEC-028). Fixes and their test results: TQ-110 in
`ai-workflow/TASK_QUEUE.md`. Items for step 6f: H1, L2.

## 1. What these audits are

Two audits the owner had AI tools run. Both are internal checks, like the Phase 5 test pass and its
second AI review. **Neither is the independent external audit** (TQ-106), and neither changes the
release gate: release builds still have no chat.

| | Audit A | Audit B |
| --- | --- | --- |
| Object | Repository source, commit `335ccca` plus the TQ-109 working tree | Debug APK (SHA-256 `ef1d095d…3250a`) built from a copy of the same code |
| Method | Reading the code, small attack probes against the relay and the JDK, history scan, manual CVE check | Bytecode-level review (DEX, smali, partly decompiled Java), relay source, offline fixtures |
| Dates | 2026-10-01 | 2026-10-02 to 2026-10-03 |
| Result | 0 critical, 1 high (accepted design risk), 2 medium, 4 low, 10 informational | 0 confirmed, 9 conditional, 9 informational, 9 closed-safe |
| Not covered | Device runs, full Gradle run, libsignal internals | Live relay, release APK, native libsignal, device runs |

The original reports stay with the owner, outside the repository: they hold paths of the auditing
machine. Both found no flaw in the cryptographic core (libsignal use, invitations, vault
encryption, replay protection). Every finding below was checked against the code on 2026-10-03.

## 2. Findings that needed work

| ID | Finding | Checked in the code | Decision |
| --- | --- | --- | --- |
| V2-01 (B) | A rejected envelope that only arrived out of order is deleted, so a real message can be lost | Confirmed, with a concrete path the audit had not shown: Bob's HELLO hits a local failure on Alice's side (no verdict, kept), and the text right behind it, which cannot be routed before the HELLO, is rejected and deleted in the same sync. Bob sees one tick forever | **Fixed (TQ-110):** behind an envelope without a verdict, a rejection of an envelope with the same routing tag waits for the next sync, at most 10 per sync |
| SESS-01 (B) | The chat screen can show one contact's safety number under another contact's name | Confirmed: `refresh()` read the state, did slow store reads, then wrote back a copy of the state it had read first | **Fixed (TQ-110):** atomic state updates; messages and the number are applied only for the contact they were read for |
| STORE-04 (B) | Two quick taps on "Create profile" may create two vault keys, leaving the profile unreadable | Confirmed: no in-progress guard, no lock around check-and-generate, profile row written with REPLACE | **Fixed (TQ-110):** process-wide locks, insert that never replaces, button off while creating |
| L1 (A) | The transport follows HTTP redirects | Confirmed (the `HttpURLConnection` default); the audit saw no secret leak on the JDK | **Fixed (TQ-110):** redirects are not followed and count as an error |
| M1 (A), SRV-02 (B) | Behind a reverse proxy all clients share one rate limit; a full limiter table is cleared for everyone; read quota is charged before authentication | Confirmed | **Fixed (TQ-110):** least-recent eviction; `RELAY_TRUST_PROXY=1` counts the client address a local proxy forwards. Charging before authentication stays: it throttles guessing |
| SRV-01 (B) | No relay-wide memory bound; every request sweeps all stored messages | Confirmed | **Fixed (TQ-110):** whole-relay caps (100,000 messages, 256 MiB of ciphertext, then 503 `relay_full`); sweep at most once a second |
| M2 (A) | The deployment examples route the old `/v1/messages` API | Confirmed (Caddy, nginx, deployment README, VPS guide) | **Fixed (TQ-110)** |
| L3 (A) | CI actions referenced by moving tags; no checksum for the Gradle download | Confirmed | **Fixed (TQ-110):** actions pinned to commit hashes, wrapper validation, `distributionSha256Sum` (checked against gradle.org; the wrapper JAR is the official Gradle 8.9 one). Verification metadata and SBOM stay with TQ-008 |
| H1 (A), INV-01 (B), INV-I01 (B) | Trust on first use: whoever sees an invitation QR can pair first, or delete the invitation from the relay | By design (DEC-019); the main practical risk for real users | **Step 6f (owner):** a "compare the safety number" step and a local "verified" mark. Documented in `THREAT_MODEL.md` |
| L2 (A) | After a failed upload the QR is shown but cannot work; `retryInvitationUpload` has no caller | Confirmed | **Step 6f:** show whether the upload worked, offer "try again" |

## 3. Accepted, documented, or already planned

| ID | Finding | Decision |
| --- | --- | --- |
| STORE-01 (B) | Old encrypted records of the same row still authenticate (no freshness counter) | Out of scope: needs write access to the app's files with the same Keystore key, in practice root. Backup and device transfer are off. `THREAT_MODEL.md` |
| STORE-02 (B) | The last-resort Kyber replay ledger is not vault-authenticated | Not reachable: the app never creates last-resort Kyber keys (only tests do). Revisit if it ever does |
| TRN-01 (B), I-2 (A) | Debug builds allow cleartext HTTP to any host | Debug and QA only; release rejects HTTP (`allow_insecure_relay_http` and cleartext are false in main) |
| I-3 (A) | `JcaLocalVaultCipher` (fixed-key test vault) is in the main source set | Unused by production code, removed from release by R8; move it once shared test fixtures exist |
| L4 (A), I-4 (A), I-7 (A) | Lost queue secret, no `FLAG_SECURE`, envelope digests never pruned | Already TQ-108 (step 6g) |
| I-1 (A) | `qaMinified` build has v2 and cleartext | Known QA build; never distributed |
| I-5, I-6 (A) | No StrongBox or unlocked-only vault key; software identity key in memory | Known (Phase 5 notes, as in Signal for Android) |
| I-10 (A) | CVE check was manual | TQ-008 |
| STORE-I01, I02 (B) | IDs and flags are plain columns; buffers are wiped best-effort only | Known limits: payload encryption, not a whole-database encryption |
| V2-I01, V2-I02 (B) | Network retries resend the same envelope; an authenticated but unreadable payload is dropped | By design |
| INV-I02, I03, I04 (B) | Bounded candidate work; arrays shared inside invitation objects; native libsignal not reverse engineered | Acknowledged; native libsignal belongs to the TQ-106 scope decision |
| STORE-I03 (B) | A decompiler run timed out | Tooling only |

Closed-safe in audit B and consistent with our tests: WIR-01, SESS-02, STORE-03, STORE-SAFE01,
STORE-SAFE02, INV-SAFE01 to INV-SAFE04. Audit A confirmed every Phase 5 fix (F1–F12, R1–R7) in place,
a clean secret scan of the whole Git history, and no logging of secrets.

## 4. Compatibility

No wire, invitation or storage format change and no Room schema change (one new DAO insert
method). v2 stays debug-only. A relay behind a reverse proxy needs `RELAY_TRUST_PROXY=1`.
