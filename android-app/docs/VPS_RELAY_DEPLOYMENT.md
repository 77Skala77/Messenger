# VPS HTTPS Relay Deployment

## Scope

This guide prepares a public HTTPS relay for MVP QA when a phone and Android
Emulator cannot share a local LAN relay. The relay remains a dumb delivery server:
it stores opaque encrypted blobs with TTL and never receives plaintext, private
keys, session keys, message keys, nonces or decrypted files.

This is not a production deployment guide. Current Android chats still use
`SimplifiedSessionManager`; Signal-backed sessions and `PARANOIA_C4_AEAD` are
disabled. A VPS relay still sees IP addresses, request timing, approximate payload
sizes, queue metadata and traffic frequency.

## VPS Requirements

Minimum QA VPS:

- Ubuntu 22.04 LTS or 24.04 LTS.
- 1 vCPU, 512 MB to 1 GB RAM.
- Public IPv4 or IPv6 reachable from test devices.
- A DNS name such as `relay.example.com` pointing to the VPS.
- Inbound TCP 80 and 443 for HTTPS setup.
- Inbound SSH restricted to your admin IPs where practical.
- No shared application logs that print request bodies.

Recommended:

- Automatic security updates.
- Non-root service user.
- Caddy for automatic HTTPS.
- Basic firewall and reverse-proxy rate limits.
- Short message TTL and regular log review for operational errors only.

## Install Node.js

Install Node.js 20+ using your preferred package source. One common Ubuntu option:

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt-get install -y nodejs
node --version
npm --version
```

The relay currently has no npm runtime dependencies.

## Create Service User

```bash
sudo useradd --system --home /opt/secure-messenger-relay --shell /usr/sbin/nologin securemessenger
sudo mkdir -p /opt/secure-messenger-relay
sudo chown securemessenger:securemessenger /opt/secure-messenger-relay
```

## Copy Relay Server

From the repository, copy only the relay server files to the VPS. Example from your
workstation:

```bash
rsync -av --delete android-app/server/relay/ securemessenger@relay.example.com:/opt/secure-messenger-relay/
```

If SSH login as the service user is disabled, copy to a temporary admin location and
then move the files with `sudo`, preserving ownership:

```bash
sudo rsync -av --delete /tmp/relay/ /opt/secure-messenger-relay/
sudo chown -R securemessenger:securemessenger /opt/secure-messenger-relay
```

Do not copy `.env`, SSH keys, signing keys, local Android build artifacts, APKs,
`node_modules`, Gradle caches or QA logs.

## Run Relay Manually

On the VPS:

```bash
cd /opt/secure-messenger-relay
sudo -u securemessenger HOST=127.0.0.1 PORT=8787 node src/server.mjs
```

In another shell:

```bash
curl http://127.0.0.1:8787/health
```

Expected response:

```json
{"status":"ok"}
```

Stop the manual process before enabling systemd.

## systemd Service

Use [../deploy/secure-messenger-relay.service.example](../deploy/secure-messenger-relay.service.example)
as a starting point:

```bash
sudo cp android-app/deploy/secure-messenger-relay.service.example /etc/systemd/system/secure-messenger-relay.service
sudo systemctl daemon-reload
sudo systemctl enable --now secure-messenger-relay
sudo systemctl status secure-messenger-relay
```

The example binds the Node relay to `127.0.0.1:8787` so only the reverse proxy is
public. It uses `Restart=always` and a dedicated `securemessenger` user. It contains
no laptop paths, tokens or secrets.

Check local health:

```bash
curl http://127.0.0.1:8787/health
```

View logs without payloads:

```bash
journalctl -u secure-messenger-relay -n 100 --no-pager
```

The relay should log only minimal technical lifecycle events. Do not add logging for
request bodies, queue IDs, ciphertext envelopes, nonces or keys.

## HTTPS Reverse Proxy: Caddy Preferred

Caddy is preferred for QA because it can obtain and renew TLS certificates
automatically.

Install Caddy using the official package instructions for your OS, then adapt
[../deploy/Caddyfile.example](../deploy/Caddyfile.example):

```bash
sudo cp android-app/deploy/Caddyfile.example /etc/caddy/Caddyfile
sudo caddy validate --config /etc/caddy/Caddyfile
sudo systemctl reload caddy
```

The example:

- accepts `https://relay.example.com`;
- proxies to `127.0.0.1:8787`;
- keeps `/health` working;
- adds basic defensive headers;
- does not include real secrets or certificate paths.

Public health check:

```bash
curl https://relay.example.com/health
```

## HTTPS Reverse Proxy: Nginx Alternative

Use [../deploy/nginx.example.conf](../deploy/nginx.example.conf) if you prefer Nginx.
The file includes placeholder Certbot paths for `relay.example.com` and a simple
request rate limit.

Typical setup:

```bash
sudo apt-get install -y nginx certbot python3-certbot-nginx
sudo cp android-app/deploy/nginx.example.conf /etc/nginx/sites-available/secure-messenger-relay
sudo ln -s /etc/nginx/sites-available/secure-messenger-relay /etc/nginx/sites-enabled/secure-messenger-relay
sudo nginx -t
sudo certbot --nginx -d relay.example.com
sudo systemctl reload nginx
```

Do not commit real certificate files or private keys. Certbot-managed files stay on
the VPS under `/etc/letsencrypt`.

## Firewall

Example with UFW:

```bash
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
sudo ufw status verbose
```

Do not expose port `8787` publicly if Caddy/Nginx is running on the same host.
The relay process should bind to `127.0.0.1`.

## TTL And Storage

The MVP relay stores messages in memory and enforces TTL through its relay store.
Keep TTL short for QA. Do not modify the relay to persist encrypted blobs
indefinitely. A production relay would need explicit retention policy, abuse
controls, rate limits, monitoring, backups policy and privacy review.

## Rate Limiting

The Node relay intentionally stays minimal and does not implement production abuse
controls. For public QA:

- add reverse-proxy rate limits where supported;
- restrict test windows where practical;
- monitor only aggregate technical errors and request counts;
- keep logs secret-free;
- rotate logs with system defaults.

Caddy OSS does not include a built-in generic rate limiter without plugins, so use
firewall, provider-level controls or a fronting gateway if rate limiting is required.
The Nginx example includes a conservative placeholder `limit_req` policy.

## Android App Configuration

On both Android clients:

1. Open `Настройки` -> `Сервер доставки`.
2. Enter the HTTPS relay URL, for example `https://relay.example.com`.
3. Tap `Проверить сервер доставки`.
4. Expect `Сервер доступен` / `Relay server is available`.
5. Run `Готовность к тесту переписки` before sending messages.

Use the same HTTPS URL on the physical phone and Android Emulator. Public HTTPS
relay testing does not require `10.0.2.2` or a LAN IP.

## Repeat QA Through VPS

1. Start `secure-messenger-relay` on the VPS.
2. Confirm `curl https://relay.example.com/health`.
3. Configure the same HTTPS relay URL on both clients.
4. Perform reciprocal QR/invitation pairing.
5. Send A -> B and B -> A.
6. Confirm outgoing status changes from `Отправлено на сервер доставки` to
   `Доставлено` only after delivery receipt.
7. Stop the systemd service:

```bash
sudo systemctl stop secure-messenger-relay
```

8. Send a disposable test message and confirm `Ожидает синхронизации`.
9. Restart the relay:

```bash
sudo systemctl start secure-messenger-relay
```

10. Wait for background sync or tap `Синхронизировать сейчас`.
11. Confirm retry delivery, receipt update and no duplicate chat messages.

## Security Boundaries

- HTTPS protects the transport channel to the VPS; it does not replace end-to-end
  encryption or protocol audit.
- The relay is untrusted for confidentiality and integrity of message content.
- The relay still observes metadata: IP addresses, timing, message size, queue
  identifiers and request frequency.
- Do not log plaintext, keys, nonces, ciphertext contents, encrypted blob bodies,
  full queue IDs or invitation payloads.
- This deployment is for QA only and makes no production security claim.
