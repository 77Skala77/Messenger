#!/usr/bin/env sh
set -eu

if [ "$#" -ne 1 ]; then
  echo "Usage: $0 https://relay.example.com" >&2
  exit 64
fi

BASE_URL="${1%/}"

case "$BASE_URL" in
  https://*) ;;
  *)
    echo "Relay URL must use HTTPS for public VPS testing." >&2
    exit 64
    ;;
esac

curl --fail --silent --show-error --max-time 10 "$BASE_URL/health"
printf '\n'
