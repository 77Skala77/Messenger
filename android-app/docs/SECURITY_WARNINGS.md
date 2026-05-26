# Security Warnings

## Required User Warning

This is an MVP and audit-ready prototype only. Do not use it for real sensitive
conversations. It has not undergone independent cryptographic, Android, protocol,
server or privacy audit.

## Explicitly Unsafe Or Incomplete For Production

- Simplified ECDH session setup is not X3DH/PQXDH plus Double Ratchet.
- Debug relay synchronization is wired, but it exposes queue/timing/size/IP metadata
  and is not a production transport.
- New devices can be staged and approved locally; multi-device message fan-out,
  revocation, metadata protection, background safety, app lock, secure backup and
  attachment handling are incomplete.
- The official `libsignal` dependency is only a disabled migration gate. It does not
  make current chats Double Ratchet sessions.
- No forensic secure-delete assurance is possible in this version.
- Basic release defaults now disable cleartext networking and enable shrinking, but
  no reproducible-build review or external release-hardening audit has been completed.

## Paranoia C4

`PARANOIA_C4_AEAD` is disabled. The module is only an interface and design note until
all necessary primitives are sourced from reliable reviewed implementations, its full
composition is specified and tested, and an independent audit accepts the result.
It must not be enabled, advertised, or treated as secure based on its cascade name.

## Logging And Diagnostics Policy

Never log or export plaintext, decrypted attachments, passwords, private keys, session
keys, message keys, nonce values, or raw encrypted envelopes tied to identifiable users.
Diagnostics are a placeholder until a dedicated redaction review exists.

## Forbidden Claims

Do not market this project using claims such as "unbreakable", "military-grade",
"absolute security", or key-size-based security guarantees.
