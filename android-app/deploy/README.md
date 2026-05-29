# Relay Deployment Examples

These files are examples for QA deployment of the MVP relay behind HTTPS. They do
not contain real domains, IP addresses, SSH keys, TLS private keys, tokens or
secrets.

Files:

- `secure-messenger-relay.service.example` - systemd unit for the Node relay.
- `Caddyfile.example` - preferred HTTPS reverse proxy with automatic certificates.
- `nginx.example.conf` - Nginx alternative with Certbot placeholder paths.
- `check-relay-health.sh` - simple safe health-check helper.

Expected service layout on a VPS:

```text
/opt/secure-messenger-relay/
  package.json
  src/
    relay-store.mjs
    server.mjs
```

Recommended public URL:

```text
https://relay.example.com
```

The relay must stay a dumb delivery server. Do not add logs for plaintext, keys,
nonces, ciphertext contents, encrypted blob bodies, invitation payloads or full
queue IDs.

Full deployment notes: [../docs/VPS_RELAY_DEPLOYMENT.md](../docs/VPS_RELAY_DEPLOYMENT.md).
