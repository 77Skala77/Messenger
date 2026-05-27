#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
output="${1:-qa-logcat.txt}"
adb_bin="${ADB:-adb}"

if ! command -v "$adb_bin" >/dev/null 2>&1; then
    sdk_adb="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
    if [[ -x "$sdk_adb" ]]; then
        adb_bin="$sdk_adb"
    else
        printf 'adb was not found. Install Android platform-tools or set ADB.\n' >&2
        exit 1
    fi
fi

devices=()
while IFS= read -r device; do
    [[ -n "$device" ]] && devices+=("$device")
done < <("$adb_bin" devices | awk 'NR > 1 && $2 == "device" { print $1 }')

if [[ -n "${ANDROID_SERIAL:-}" ]]; then
    serial="$ANDROID_SERIAL"
    if ! printf '%s\n' "${devices[@]:-}" | grep -Fxq "$serial"; then
        printf 'ANDROID_SERIAL does not identify an available adb device.\n' >&2
        exit 1
    fi
elif [[ "${#devices[@]}" -eq 1 ]]; then
    serial="${devices[0]}"
elif [[ "${#devices[@]}" -eq 0 ]]; then
    printf 'No authorized Android device is connected through adb.\n' >&2
    exit 1
else
    printf 'More than one Android device is connected. Set ANDROID_SERIAL before capture.\n' >&2
    exit 1
fi

"$adb_bin" -s "$serial" logcat -c
"$adb_bin" -s "$serial" shell am force-stop app.securemessenger >/dev/null
"$adb_bin" -s "$serial" shell am start -n app.securemessenger/.MainActivity >/dev/null
sleep 1
pid="$("$adb_bin" -s "$serial" shell pidof app.securemessenger | tr -d '\r' | awk '{ print $1 }')"
if [[ -z "$pid" ]]; then
    printf 'The debug application did not start; install it before starting QA capture.\n' >&2
    exit 1
fi

printf 'Recording sanitized app logcat to %s. Press Ctrl-C when the QA step is complete.\n' "$output"
"$adb_bin" -s "$serial" logcat --pid="$pid" -v threadtime |
    "$script_dir/analyze_qa_logcat.sh" --redact-stream > "$output"
