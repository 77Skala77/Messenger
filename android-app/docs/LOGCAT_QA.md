# Logcat QA

## Purpose

These debug-only helper scripts collect crash, WorkManager, Room, Keystore,
camera/QR, relay/network, language and orientation clues during a manual test.
They do not establish protocol security. The app remains an MVP using
`SimplifiedSessionManager`; Signal-backed sessions and `PARANOIA_C4_AEAD` remain
disabled.

## Safe Test Rule

Use disposable test message text only. Never type or share real sensitive
messages, QR invitation payloads, keys, nonce values, full queue identifiers or
encrypted envelopes during QA. The scripts mask labelled sensitive fields and
token-shaped credentials before saving or displaying results, but a diagnostic
tool cannot reliably recognize arbitrary secrets accidentally logged by new code.

## Capture

Install and launch the debug APK once, then from `android-app` run:

```bash
./scripts/start_qa_logcat.sh
```

The script checks that exactly one authorized Android device is available over
`adb`, clears prior logcat output, launches `app.securemessenger/.MainActivity`,
and writes a sanitized capture to ignored local file `qa-logcat.txt`. If two
devices are attached, select the one being diagnosed without printing any
credentials:

```bash
ANDROID_SERIAL=<adb-device-serial> ./scripts/start_qa_logcat.sh
```

Press `Ctrl-C` after reproducing the QA step.

## Analyze

```bash
./scripts/analyze_qa_logcat.sh qa-logcat.txt
```

The summary searches the app capture for `FATAL EXCEPTION`, `AndroidRuntime`,
ANR, exceptions, errors, WorkManager, Room, Keystore, camera/QR, relay/network,
language and orientation signals. Output is redacted again before display.

Do not commit or publish `qa-logcat.txt`. When a test fails, share only the
sanitized summary along with the in-app diagnostic report and relay terminal
status that contains no payload contents.
