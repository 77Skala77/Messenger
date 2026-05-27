#!/usr/bin/env bash
set -euo pipefail

redact_stream() {
    sed -E \
        -e 's/(ghp_|github_pat_)[A-Za-z0-9_]+/[REDACTED_TOKEN]/g' \
        -e 's/([Bb]earer)[[:space:]]+[A-Za-z0-9._-]+/\1 [REDACTED]/g' \
        -e 's/(([Tt]oken|[Ss]ecret|[Pp]assword|[Pp]rivate[_ -]?[Kk]ey|[Ss]ession[_ -]?[Kk]ey|[Mm]essage[_ -]?[Kk]ey|[Nn]once|[Cc]iphertext|[Qq]ueue([_ -]?[Ii]d)?)[[:space:]]*[:=][[:space:]]*)[^,;[:space:]]+/\1[REDACTED]/g'
}

if [[ "${1:-}" == "--redact-stream" ]]; then
    redact_stream
    exit 0
fi

input="${1:-qa-logcat.txt}"
if [[ ! -f "$input" ]]; then
    printf 'QA log file not found: %s\n' "$input" >&2
    exit 1
fi

pattern='app\.securemessenger|FATAL EXCEPTION|AndroidRuntime|ANR|Exception|Error|WorkManager|Room|Key[Ss]tore|Camera|QR|Relay|network|locale|language|orientation'
printf 'Sanitized QA logcat summary for app.securemessenger (%s)\n' "$input"
if ! grep -Ei "$pattern" "$input" | redact_stream; then
    printf 'No matching QA issue lines were found.\n'
fi
