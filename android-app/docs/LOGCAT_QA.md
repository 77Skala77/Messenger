# Logcat QA

## Role And Evidence Boundary

**Repository baseline reviewed:** `33586086060c6175e96088eee8bd7c655986da1d`

This document describes debug-only Android Logcat capture for controlled MVP QA. It is a
failure-investigation aid, not protocol verification, security audit evidence by itself, or a
guarantee that captured output is free of sensitive information.

The active app remains a prototype using `SimplifiedSessionManager` and `STANDARD_AEAD`.
Signal-backed sessions and `PARANOIA_C4_AEAD` remain disabled.

## Current Helper Scripts

Repository scripts:

```text
scripts/start_qa_logcat.sh
scripts/analyze_qa_logcat.sh
```

The capture script currently:

- locates `adb` from `ADB`, PATH, or the expected Android SDK location;
- requires exactly one authorized device unless `ANDROID_SERIAL` selects one;
- clears existing Logcat output on that device;
- force-stops and launches `app.securemessenger/.MainActivity`;
- resolves the current app PID;
- captures only that PID in `threadtime` format;
- pipes each line through the analyzer's regex redaction;
- writes to `qa-logcat.txt` by default.

The analyzer currently:

- masks some GitHub token prefixes;
- masks Bearer credentials;
- masks values following selected labels such as token, secret, password, key, nonce,
  ciphertext, and queue ID;
- searches a saved file for broad crash, error, WorkManager, Room, Keystore, camera, QR,
  relay, network, locale, language, and orientation terms;
- applies the same redaction patterns again before displaying the summary.

This is best-effort filtering. It cannot recognize every secret, identifier, message, URL,
stack value, encoded payload, or future log format.

## Important Capture Side Effects

Starting the standard capture changes the app and device diagnostic state:

- `logcat -c` removes prior device Logcat history available through the current buffer;
- `am force-stop` terminates the app;
- launching `MainActivity` starts a new process and lifecycle;
- the selected PID limits capture to that process;
- behavior that happened before capture is not retained by the script;
- a process restart may receive a new PID and require a new capture;
- force-stop can affect WorkManager scheduling, pending lifecycle callbacks, and the failure
  being investigated.

Do not use the helper blindly when the issue depends on pre-existing logs, an unmodified
background process, boot behavior, or a crash/restart sequence. Record that the script
force-stopped the app.

For a specialized incident, define a separate approved capture procedure rather than
silently modifying these scripts during a test.

## Safe Test-Data Rule

Use only disposable profiles and meaningless test messages. Assume any text entered into the
app could appear in logs because of current or future code, Android framework behavior,
third-party libraries, keyboards, accessibility services, or error handling.

Never use or expose during QA:

- real sensitive message text;
- full invitation or verification payloads;
- private, session, message, or signing keys;
- nonce values;
- full queue identifiers;
- ciphertext or opaque relay envelopes;
- provider, GitHub, tunnel, VPS, or API credentials;
- SSH keys, passwords, recovery codes, or tokens;
- real user names, addresses, device identifiers, or account data.

Regex redaction is not permission to put secrets into a test.

## Preconditions

Before capture:

- use a trusted workstation and authorized Android device;
- verify the repository commit and installed debug APK;
- ensure USB debugging is authorized only for the intended workstation;
- close unrelated apps if their state could confuse testing;
- select one disposable scenario and expected event window;
- record device model, API level, ABI, time, network, battery state, and relay mode;
- verify there is enough local disk space;
- decide who may access the output and when it will be deleted.

Do not collect Logcat from real users or production devices under this QA procedure.

## Standard Capture

From `android-app`:

```bash
./scripts/start_qa_logcat.sh
```

The default output is:

```text
qa-logcat.txt
```

The root `.gitignore` excludes `qa-logcat*.txt`, but ignore rules are only a guardrail. They
do not make the file safe to share, back up, sync, or retain.

Press `Ctrl-C` after reproducing the intended QA step.

## Selecting A Device

When multiple authorized devices are attached, the script exits and requires an explicit
selection:

```bash
ANDROID_SERIAL=<adb-device-serial> ./scripts/start_qa_logcat.sh
```

Set the serial only in the local shell. Avoid copying device serials into public issues or
reports unless they are necessary and approved; device identifiers can be sensitive
metadata.

Confirm the selected device before reproducing a destructive reset, pairing, or retry
scenario.

## Custom Output Path

The capture script accepts an optional output filename:

```bash
./scripts/start_qa_logcat.sh qa-logcat-retry.txt
```

Use a local ignored filename beginning with `qa-logcat` where practical. Do not write directly
to a shared cloud folder, public attachment directory, repository path outside ignore rules,
or location with broad permissions.

Restrict access according to the workstation policy, for example with a private user-owned
directory and appropriate file mode.

## Analyze A Saved Capture

```bash
./scripts/analyze_qa_logcat.sh qa-logcat.txt
```

The summary searches for broad patterns. It can:

- miss relevant lines that do not match the pattern;
- include benign lines because words such as `Error` or `network` are broad;
- omit cross-process Android or system-service context because standard capture is PID-only;
- display unrecognized sensitive values;
- reflect a redacted line that is no longer sufficient to reproduce the issue.

Treat the output as a triage summary. Review the surrounding sanitized context locally before
concluding root cause.

## Redaction Review

Before sharing any line, manually inspect for:

- message fragments and user-entered text;
- invitation, verification, QR, Base64, JSON, or encoded payloads;
- hostnames, IP addresses, URLs, paths, query strings, and headers;
- device serial, Android ID, account name, package data path, and file names;
- queue, message, conversation, contact, or profile identifiers;
- stack variables and exception messages;
- tokens without a recognized prefix;
- credentials split across multiple lines;
- clipboard, keyboard, accessibility, notification, or camera data;
- ciphertext and cryptographic material not preceded by a recognized label.

Replace sensitive values with stable neutral markers such as `[REDACTED_QUEUE]` rather than
inventing realistic-looking data.

Do not publish raw capture first and promise to redact it later.

## Scope Of Useful Signals

Logcat may help investigate:

- `FATAL EXCEPTION`, `AndroidRuntime`, or ANR symptoms;
- WorkManager scheduling and worker failures;
- Room and migration exceptions;
- Android Keystore errors;
- camera or QR scanner lifecycle failures;
- relay reachability and HTTP client errors;
- locale, resource, and language persistence problems;
- orientation and activity recreation;
- app startup and process lifecycle.

A lack of matching lines does not prove absence of a bug, data leak, or security problem.

## Delivery And Trust Interpretation

Do not infer protocol success solely from Logcat.

Correlate logs with:

- in-app message status;
- sanitized delivery diagnostics;
- relay health and status;
- exact pairing and device state;
- timestamps on sender and receiver;
- test procedure and expected receipt flow.

Remember:

- `SENT` means relay acceptance;
- `DELIVERED` requires an authenticated delivery receipt confirming receiver-app acceptance,
  decryption, and durable storage;
- `DELIVERED` is not a read receipt;
- warning-only `UNVERIFIED` / `PRIMARY_UNVERIFIED` communication is permitted with a
  persistent warning;
- `KEY_CHANGED`, pending approval, revoked, or missing primary state must block relevant
  delivery paths;
- full fail-closed handling of unknown and invalid persisted trust state remains an open
  hardening area.

A log line saying “sent” or “success” is not sufficient evidence for these semantics.

## Two-Device Capture Strategy

For phone + phone or phone + emulator QA:

- capture one selected client at a time unless a separate synchronized procedure is approved;
- record clocks and timestamps for both clients;
- do not merge raw captures before reviewing each one;
- label direction A → B or B → A;
- distinguish sender, receiver, relay, and receipt events;
- avoid collecting unrelated device-wide logs;
- use in-app sanitized diagnostics for cross-device state where possible.

If both captures are necessary, use separate local filenames and sanitize them independently.

## Background And Restart Scenarios

The standard helper force-stops and launches the app, so it is not neutral for every
WorkManager or restart test.

For background retry investigation:

1. start capture before creating the retry condition if the force-stop is acceptable;
2. record whether the app stayed foreground, background, or was killed;
3. record battery optimization and network state;
4. avoid stating a guaranteed retry delay;
5. distinguish automatic WorkManager execution from manual sync;
6. start a new capture after an app process restart if the PID changed;
7. retain only sanitized evidence needed for the issue.

For boot/reboot behavior, use a separately reviewed capture plan that includes relevant
system components; the PID-only helper may be insufficient.

## Relay And VPS Logs Are Separate

This guide covers Android app Logcat. It does not sanitize:

- Node relay terminal output;
- systemd journal;
- Caddy or Nginx logs;
- tunnel-provider dashboards;
- hosting-platform or firewall logs.

Review those sources independently under the relay and VPS guides. Do not combine raw server
and client logs in a public attachment.

## Failure Report

A useful sanitized report includes:

```text
Commit:
Debug APK:
Device / API / ABI:
ANDROID_SERIAL disclosed: yes / no
Capture start and stop time:
Scenario:
App was force-stopped by helper: yes
Network / relay mode:
Expected result:
Observed result:
Visible app statuses:
Sanitized diagnostic events:
Sanitized Logcat summary:
Relevant pass/fail/skip prerequisites:
```

Include the minimum lines needed to support the issue. State when redaction removed data that
could affect reproduction.

## Retention And Cleanup

After triage:

1. keep the raw local capture only as long as necessary;
2. verify no automatic cloud backup or collaboration sync retained it unexpectedly;
3. store any required evidence in an approved restricted location;
4. delete unneeded raw and intermediate files;
5. clear shell variables containing device identifiers or paths where appropriate;
6. retain only the manually reviewed sanitized summary in ordinary project records;
7. document any evidence that must remain and its owner.

Local file deletion is not a forensic erase guarantee, especially on flash storage or synced
backup systems.

## Script Modification Boundary

Do not broaden the capture from PID-only to device-wide logging, add network packet capture,
or add new secret-detection patterns as an incidental documentation task.

A script change requires:

- separate review and authorization;
- test fixtures with synthetic secrets;
- verification of false negatives and false positives;
- shell portability and failure-behavior review;
- documentation update;
- exact test results.

No regex set should be described as complete secret detection.

## Exit Criteria

Logcat QA is complete only when:

- the exact commit, device, and scenario are recorded;
- the helper side effects are acknowledged;
- the capture and summary were manually reviewed;
- no unapproved sensitive material is shared;
- conclusions are correlated with app diagnostics and visible behavior;
- raw local evidence has an explicit retention or deletion decision;
- failures and missing context are stated.

## Related Documents

- [Manual Two-Device QA](MANUAL_TWO_DEVICE_QA.md)
- [Phone + Emulator Relay QA](PHONE_EMULATOR_PUBLIC_RELAY_QA.md)
- [Localization](LOCALIZATION.md)
- [MVP Delivery Baseline](MVP_DELIVERY_BASELINE.md)
- [Known Limitations](KNOWN_LIMITATIONS.md)
- [VPS Relay Deployment](VPS_RELAY_DEPLOYMENT.md)
