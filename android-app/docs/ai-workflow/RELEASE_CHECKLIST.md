# Release Checklist — Secure Messenger MVP

Use this checklist before tagging, publishing, sharing a build, handing the app to a tester, or making any release-readiness claim.

This checklist supplements:

- `../../AGENTS.md`
- `SECURITY_CHECKLIST.md`
- `TESTING_CHECKLIST.md`
- `CURRENT_PROJECT_STATE.md`

It does not turn this MVP into a production-ready secure messenger.

For an applicable item, record the result. For an inapplicable item, record `N/A` and a short reason. Do not mark an item complete without evidence.

## 0. Classify the release or handoff

Select exactly one primary release class:

- [ ] **Documentation handoff** — documents or review material only; no app artifact.
- [ ] **Internal debug QA build** — disposable development/testing APK.
- [ ] **External tester debug build** — limited test distribution with explicit MVP and debug-risk warning.
- [ ] **MVP release candidate** — release-variant artifact for technical evaluation, not production use.
- [ ] **Tagged MVP snapshot** — source tag or repository checkpoint clearly labelled pre-production.
- [ ] **Production release** — currently blocked; see the stop conditions below.

Record:

```text
Release class:
Audience:
Purpose:
Repository branch:
Commit:
Version name:
Version code:
Artifact type:
Distribution channel:
```

Current repository defaults at the last documentation verification:

- application ID: `app.securemessenger`
- minimum Android API: 31
- target Android API: 34
- version code: 1
- version name: `0.1.0-mvp`
- release minification: enabled
- release resource shrinking: enabled
- `ENABLE_PARANOIA_C4`: false in debug and release

Reverify these values from the current repository before every release.

## 1. Immediate stop conditions

Do not publish or describe the build as production-ready when any of the following is true:

- [ ] The build is still an MVP using the prototype session layer.
- [ ] Normal chats do not use a reviewed Signal/PQXDH/Double Ratchet integration.
- [ ] Independent cryptographic and application security review is incomplete.
- [ ] A production transport and operational security model are absent.
- [ ] The release depends on a historical or unverified VPS.
- [ ] The build requires debug-only HTTP relay access.
- [ ] Release signing, key custody, reproducibility, or provenance is undefined.
- [ ] Critical or high security findings remain unresolved.
- [ ] Required build, test, or migration verification is missing.
- [ ] The release note would overstate confidentiality, forward secrecy, post-compromise security, secure deletion, multi-device behavior, or delivery guarantees.

For the current project, a **production release is not approved**. A release-variant artifact may only be shared as an MVP technical-evaluation build with explicit limitations.

## 2. Repository and source readiness

- [ ] The intended branch and exact commit are recorded.
- [ ] The commit exists in the authoritative GitHub repository.
- [ ] The complete working-tree diff was reviewed.
- [ ] The working tree is clean, or every uncommitted file is explicitly reported.
- [ ] No unrelated files are included.
- [ ] Staged changes match the approved scope.
- [ ] No lost local commit or historical chat-reported file is assumed present.
- [ ] No merge conflict marker remains.
- [ ] `git diff --check` passes.
- [ ] Generated build output is absent from the commit.
- [ ] No APK, AAB, mapping file, local report, temporary archive, or test log is committed unintentionally.
- [ ] No `local.properties`, local SDK path, machine-specific configuration, `node_modules`, IDE state, or temporary cache is committed.
- [ ] No secret, signing key, private key, token, bearer credential, QR secret, raw protocol record, plaintext message, or sensitive diagnostic is committed.
- [ ] Commit creation occurred only when explicitly requested.
- [ ] Push or tag creation occurred only when explicitly requested.
- [ ] No history rewrite, force-push, amend, rebase, branch deletion, or tag deletion occurred without explicit approval.

## 3. Versioning and release identity

- [ ] `applicationId` is correct for the intended distribution.
- [ ] `versionCode` is appropriate and does not unintentionally reuse a previously distributed value.
- [ ] `versionName` accurately labels the build as MVP/pre-production.
- [ ] The release class matches the version label.
- [ ] Debug and release artifacts cannot be confused by filename, notes, or distribution location.
- [ ] The exact commit hash is included in the handoff or release record.
- [ ] The build timestamp is recorded when relevant.
- [ ] A tag, when used, points to the exact reviewed commit.
- [ ] The tag name does not imply production readiness.
- [ ] Release notes describe actual repository state rather than planned or lost local work.
- [ ] Any version change is reflected consistently in documentation and distribution notes.

Do not remove the `mvp`, `alpha`, `preview`, or equivalent warning until the project has actually passed the required production gates.

## 4. Build configuration and variant separation

- [ ] The intended build variant is recorded.
- [ ] Debug-only controls remain absent from the release variant.
- [ ] Debug-only relay allowances remain scoped to debug resources/configuration.
- [ ] Release cleartext networking remains disabled.
- [ ] Release code rejects HTTP relay URLs.
- [ ] `ENABLE_PARANOIA_C4` remains `false`.
- [ ] Signal-backed session activation flags remain `false` unless reviewed activation was explicitly approved.
- [ ] No debug dependency, mock transport, test fixture, sample key, bypass, or development server setting leaks into release.
- [ ] Manifest merge output is reviewed when manifests, permissions, components, or build types changed.
- [ ] Exported activities, services, receivers, and providers are intentional.
- [ ] INTERNET and CAMERA permissions remain justified.
- [ ] Camera remains optional where intended.
- [ ] Backup and device-transfer exclusions remain appropriate.
- [ ] `android:allowBackup="false"` and `android:fullBackupContent="false"` remain consistent with the intended data policy.
- [ ] Database and shared-preference exclusions remain present in data-extraction rules.
- [ ] Release minification and resource shrinking complete successfully.
- [ ] R8/ProGuard warnings are reviewed.
- [ ] Required crypto, serialization, reflection, Room, or library classes are not removed or renamed unsafely.
- [ ] Native/JNI and ABI packaging is reviewed if native dependencies are present or changed.
- [ ] No unsupported ABI or accidental large native artifact is included.
- [ ] Build configuration changes are documented.

Current repository policy to reverify:

- main network-security config denies cleartext traffic;
- debug network-security config allows cleartext for development relay testing;
- release minification and resource shrinking are enabled.

## 5. Cryptographic and protocol readiness

- [ ] `SECURITY_CHECKLIST.md` was completed for the release diff.
- [ ] The active session implementation is identified accurately.
- [ ] `STANDARD_AEAD` remains the only active encryption suite.
- [ ] AES-GCM parameters, nonce/counter behavior, AAD, and HKDF behavior were not weakened.
- [ ] Replay protection remains enabled.
- [ ] Envelope and receipt authentication remain enabled.
- [ ] No plaintext or downgrade fallback exists.
- [ ] The relay remains untrusted.
- [ ] Device trust remains fail closed.
- [ ] Pending, revoked, `key_changed`, missing-primary, or ambiguous device state still blocks sending.
- [ ] No silent secondary-device fan-out was introduced.
- [ ] Revocation is not described as synchronized protocol revocation.
- [ ] `Delivered` still requires sender-side validation of an authenticated receiver receipt.
- [ ] `Delivered` is not described as a read receipt.
- [ ] WorkManager is not described as guaranteeing timely delivery.
- [ ] Debug reset is not described as secure erase.
- [ ] Local ciphertext storage is not described as full SQLCipher/database encryption.
- [ ] Metadata exposure remains documented.
- [ ] No production-security, Signal-interoperability, forward-secrecy, or post-compromise-security claim was added without evidence.

## 6. Disabled and experimental boundaries

- [ ] `SignalStoreFeatureFlags.SIGNAL_BACKED_SESSIONS_ENABLED` remains `false`.
- [ ] `OfficialSignalDoubleRatchetGate.MIGRATION_ENABLED` remains `false`.
- [ ] Normal chats still use the prototype session path.
- [ ] Official libsignal classes are not silently reachable from normal chats.
- [ ] In-memory Signal stores are not reachable from app runtime.
- [ ] Experimental PQXDH invitation v2 cannot enter the active v1 invitation flow.
- [ ] SignalStore record boundaries are not described as completed official protocol storage.
- [ ] `PARANOIA_C4_AEAD` remains disabled.
- [ ] No home-grown cascade, placeholder primitive, unsafe fallback, or incomplete protocol path is packaged as an active feature.
- [ ] Dependency, Gradle, Kotlin, manifest, resource, JNI/ABI, shrinker, and packaging changes did not accidentally activate disabled code.
- [ ] Licensing and distribution impact was reviewed for any libsignal-related artifact change.
- [ ] Historical lost local Signal work was not represented as included.

If any disabled boundary changed, stop ordinary release processing and require a separate security, compatibility, migration, licensing, and rollback review.

## 7. Storage, migration, backup, and reset readiness

- [ ] Current Room schema version is recorded.
- [ ] Exported Room schemas match the release source.
- [ ] Required migrations exist and are tested.
- [ ] Migration preserves ciphertext, counters, replay markers, retry state, device trust, and identifiers.
- [ ] Migration does not create plaintext message columns.
- [ ] Migration failure fails safely.
- [ ] Upgrade from every supported prior distributed version was tested or explicitly declared unsupported.
- [ ] Downgrade behavior is understood.
- [ ] App restart after migration succeeds.
- [ ] Keystore-backed profile/session vault behavior was verified when affected.
- [ ] Key invalidation or unavailable-Keystore behavior fails safely.
- [ ] Backup and device transfer do not export protected database or preference state.
- [ ] Reinstall and restore behavior are documented when relevant.
- [ ] Test-data reset remains debug-only.
- [ ] Reset does not claim guaranteed flash-storage erasure.
- [ ] Any destructive migration or data loss is explicitly disclosed and approved.

## 8. Network, relay, and infrastructure readiness

- [ ] Release artifact does not require cleartext HTTP.
- [ ] The configured tester endpoint uses HTTPS when the release variant is used.
- [ ] Endpoint ownership and authorization to use it are confirmed.
- [ ] DNS, TLS certificate, hostname, redirect, and expiry are currently verified when applicable.
- [ ] Relay health is currently verified rather than assumed from historical QA.
- [ ] Relay process, reverse proxy, firewall, retention, logging, resource limits, abuse controls, and restart behavior are verified for the intended testing window.
- [ ] Direct backend ports are not unintentionally public.
- [ ] Relay logs do not contain plaintext, keys, tokens, QR payloads, or unnecessary full queue identifiers.
- [ ] The relay remains an untrusted opaque transport.
- [ ] Metadata exposure is disclosed to testers.
- [ ] Historical endpoint `http://194.87.226.46` is not used or described as current without fresh verification.
- [ ] Temporary tunnels or public QA endpoints have an expiry and shutdown plan.
- [ ] Test data retention and deletion responsibilities are stated.
- [ ] Infrastructure credentials are not bundled with the app or release package.
- [ ] A public QA relay is not described as production infrastructure.

For a documentation-only handoff or offline APK review, mark infrastructure checks `N/A` with a reason rather than implying that a server was verified.

## 9. Build and automated verification

Select checks according to `TESTING_CHECKLIST.md`.

### Required baseline for code-bearing MVP builds

```bash
cd android-app
./gradlew :app:assembleDebug
./gradlew :app:lintDebug :core:signal-protocol:testDebugUnitTest :core:signalstore:test test
node --test server/relay/test/*.test.mjs
```

- [ ] Debug assembly passes.
- [ ] Debug lint passes.
- [ ] General unit tests pass.
- [ ] Signal protocol tests pass.
- [ ] SignalStore tests pass when present and relevant.
- [ ] Relay tests pass.

### Release-variant verification

For an MVP release candidate:

```bash
cd android-app
./gradlew :app:assembleRelease :app:lintRelease
```

- [ ] Release assembly passes.
- [ ] Release lint passes.
- [ ] Minification and resource shrinking complete.
- [ ] Release artifact is inspected.
- [ ] Release cleartext policy is verified.
- [ ] Debug-only controls are absent.
- [ ] The artifact's signing state is known.

Current GitHub Actions primarily verifies debug assembly/lint/unit tests, relay tests, and emulator UI/storage flows. CI success alone does not prove that `assembleRelease`, `lintRelease`, signing, R8 behavior, or a release artifact passed.

### Instrumented verification

When a compatible device or emulator is available:

```bash
cd android-app
./gradlew :core:ui:connectedDebugAndroidTest \
  :core:storage:connectedDebugAndroidTest \
  :core:transport:connectedDebugAndroidTest
```

- [ ] Relevant UI tests pass.
- [ ] Relevant storage/Keystore tests pass.
- [ ] Relevant transport tests pass.
- [ ] Every omitted instrumented task is listed with a reason and risk.

Do not mark skipped checks as passed.

## 10. Signing, artifact, and provenance

- [ ] The artifact type is recorded: debug APK, unsigned release APK, signed APK, or AAB.
- [ ] The artifact was built from the recorded commit.
- [ ] The build environment and required tool versions are recorded.
- [ ] Signing status is explicitly known.
- [ ] No signing secret or private key is stored in the repository.
- [ ] Signing keys are held outside the repository with controlled access.
- [ ] Key alias, passwords, and private key material are not included in logs or handoff notes.
- [ ] A lost, shared, test, or unknown-custody key is not used for broader distribution.
- [ ] Signing configuration was not added as plaintext.
- [ ] The package name and certificate identity match the intended upgrade path.
- [ ] Existing testers can upgrade only when the signing identity and versioning permit it.
- [ ] Artifact hash is recorded for any externally shared file.
- [ ] Artifact filename includes enough identity to avoid confusion.
- [ ] The artifact was scanned or inspected for accidental secrets and unexpected files.
- [ ] Debuggable status matches the release class.
- [ ] Release artifact does not contain debug-only resources, endpoints, test data, logs, or local paths.
- [ ] R8 mapping or native symbols are retained securely when needed for diagnosis and are not published carelessly.
- [ ] Reproducibility is not claimed unless independently demonstrated.

The current app Gradle file contains no explicit release signing configuration. Do not assume a produced release artifact is signed or suitable for installation/distribution; inspect and report its actual state.

## 11. Manual application QA

Record devices, Android versions, build identity, relay class, and exact test steps.

For a tester-facing app build, verify when possible:

- [ ] Fresh install launches.
- [ ] Upgrade from the intended previous build works.
- [ ] Russian is the default on first launch.
- [ ] RU/EN selection works and persists.
- [ ] Onboarding completes.
- [ ] QR invitation generation works.
- [ ] Camera scanning works.
- [ ] Manual invitation import works.
- [ ] Invalid invitation is rejected.
- [ ] Reciprocal pairing is understood for bidirectional messaging.
- [ ] Message send works in both directions.
- [ ] Relay acceptance is shown only as sent-to-server.
- [ ] Authenticated delivery receipt produces `Delivered`.
- [ ] No read-receipt claim appears.
- [ ] Relay outage preserves pending state.
- [ ] Retry after relay recovery works.
- [ ] App restart preserves required pending/session/counter state.
- [ ] Duplicate replay does not create duplicate visible messages.
- [ ] Pending, revoked, `key_changed`, and missing-primary states block sending.
- [ ] Diagnostics are useful and sanitized.
- [ ] Copied report contains no plaintext, key, token, nonce, ciphertext, QR payload, or full sensitive identifier.
- [ ] Release build rejects HTTP relay URLs.
- [ ] Debug-only reset/preflight/diagnostic controls are absent or correctly scoped in release.
- [ ] No unexpected crash appears during the tested flows.
- [ ] Relevant Logcat output is sanitized.

Historical phone/emulator and VPS QA must be labelled historical unless rerun on the exact release commit and artifact.

## 12. Privacy and tester-safety readiness

- [ ] Testers are told that the app is an MVP and not for real sensitive communications.
- [ ] Testers are instructed to use synthetic identities and disposable messages.
- [ ] Testers are told what metadata the relay can observe.
- [ ] Testers are told whether the build is debug, debuggable, unsigned, or signed with a test key.
- [ ] Testers are warned that reset is not secure erase.
- [ ] Testers are warned that Android flash deletion cannot guarantee historical-block erasure.
- [ ] Testers are told that Room metadata remains visible locally.
- [ ] Testers know that attachments, backup, app lock, biometric unlock, synchronized revocation, and read receipts are absent.
- [ ] Tester contact channel and issue-reporting instructions are provided.
- [ ] Diagnostic collection instructions require sanitization.
- [ ] No real credentials, contacts, or sensitive conversations are requested.
- [ ] Test endpoint lifetime and data-retention expectations are provided.
- [ ] Tester removal/uninstall steps are provided when relevant.

## 13. Documentation readiness

- [ ] Main README matches actual behavior.
- [ ] Build and test commands match the repository.
- [ ] `CURRENT_PROJECT_STATE.md` reflects factual state changes.
- [ ] `HANDOFF.md` reflects unfinished work only.
- [ ] Workflow README index is current.
- [ ] `SECURITY_CHECKLIST.md` and `TESTING_CHECKLIST.md` results are available.
- [ ] Known limitations remain complete.
- [ ] Security tradeoffs are documented.
- [ ] Active, disabled, experimental, planned, historical, unverified, and not-implemented status is used accurately.
- [ ] No absent file or lost local commit is referenced as current repository content.
- [ ] Release notes identify the exact commit and artifact.
- [ ] Historical infrastructure claims are labelled historical.
- [ ] The release note does not promise future roadmap features.
- [ ] The release note does not confuse relay acceptance, delivery receipt, or read status.
- [ ] Installation, upgrade, reset, and tester-safety instructions are current.
- [ ] Licenses and dependency notices are reviewed when distribution changes.
- [ ] Any missing documentation is listed as a release blocker or explicit limitation.

## 14. Distribution and rollback

- [ ] The intended recipients are listed.
- [ ] The distribution channel is controlled and appropriate for the release class.
- [ ] Access to the artifact can be revoked when practical.
- [ ] The artifact is not posted publicly by default.
- [ ] The release note travels with the artifact.
- [ ] Artifact hash and commit identity are distributed through a trustworthy channel.
- [ ] Rollback or withdrawal procedure is stated.
- [ ] Test relay or tunnel can be shut down.
- [ ] A compromised or incorrect artifact can be identified and withdrawn.
- [ ] Upgrade compatibility and signing identity are understood.
- [ ] Data migration rollback limitations are documented.
- [ ] No automatic update claim is made unless implemented.
- [ ] Source tag and artifact are traceable to each other.
- [ ] Retention of artifacts, reports, logs, and tester data is defined.

## 15. Approval record

Record who reviewed each applicable area:

```text
Scope/repository review:
Security review:
Testing review:
Release-variant review:
Signing/artifact review:
Infrastructure review:
Documentation review:
Distribution approval:
```

One person may perform multiple roles for an internal MVP snapshot, but the absence of independent review must be stated. Do not imply independent security approval when none occurred.

## 16. Final release or handoff report

Every release/build handoff must include:

### Identity

- Release class
- Audience and purpose
- Repository
- Branch
- Commit
- Version name/code
- Artifact type
- Artifact filename
- Artifact hash
- Signing state
- Distribution channel

### Change summary

- User-visible changes
- Security-sensitive changes
- Storage/migration changes
- Build/dependency/configuration changes
- Documentation changes
- Disabled/experimental boundary changes

### Verification

- Exact commands and manual procedures run
- Environment and devices used
- Pass/fail result for each
- Checks not run and why
- CI status and what CI did not cover
- Release-variant result
- Remaining verification gaps

### Security and limitations

- Active session/crypto path
- Relay trust and endpoint class
- Device-trust behavior
- Known metadata exposure
- Known missing features
- Production-readiness statement
- Independent-review status
- Remaining risks

### Git and distribution

- Commit created: yes/no
- Push performed: yes/no
- Tag created: yes/no
- Working-tree state
- Artifact shared: yes/no
- Recipients/channel
- Rollback or withdrawal plan
- Next recommended step

## 17. Current project release decision

At the current documented state:

- internal debug QA builds may be produced for controlled testing;
- release-variant artifacts may be produced for technical evaluation after release-specific verification;
- tagged snapshots may be created when clearly labelled MVP/pre-production;
- public production distribution is blocked;
- real sensitive communication is out of scope;
- historical VPS success does not satisfy current infrastructure readiness;
- GitHub Actions success does not by itself satisfy release-variant, signing, operational, or independent-security requirements.
