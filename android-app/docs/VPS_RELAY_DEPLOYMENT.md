# VPS Relay Deployment For Controlled QA

## Role And Claim Boundary

**Repository baseline reviewed:** `32433ef4aba5110e2670525430ed41bf72def8f5`

This guide describes a temporary public HTTPS deployment of the minimal Node relay for
controlled MVP QA. It is not a production deployment design, authorization to expose a
server, or proof that any historical VPS is still available or secure.

The relay does not receive message plaintext from the intended client path, but it still
processes and can expose metadata including source IP, timing, queue identifiers, approximate
payload sizes, request frequency, and availability behavior.

Normal Android chats continue to use `SimplifiedSessionManager` and `STANDARD_AEAD`.
Signal-backed sessions remain disabled. `PARANOIA_C4_AEAD` no longer exists in the
codebase (removed 2026-09-27). A VPS does not repair the prototype session protocol,
warning-only first-contact risk, local-device exposure, or missing metadata protection.

TQ-107 production relay and operational-security design remains blocked.

## Historical VPS Evidence

Repository records describe a historical QA deployment with:

- Ubuntu 24.04 LTS;
- the Node relay bound to `127.0.0.1:8787`;
- Caddy and systemd;
- UFW blocking direct public access to port `8787`;
- SSH public-key login and disabled password/keyboard-interactive login;
- phone + emulator delivery, retry recovery, delivery receipts, duplicate suppression, and
  reboot recovery;
- a temporary public HTTP-by-IP endpoint at `194.87.226.46`.

These are historical results only. They do not establish current ownership, authorization,
reachability, DNS, TLS, service state, firewall, SSH policy, logs, retention, provider
configuration, or production readiness. The historical IP must not be reused as a current
endpoint merely because it appears in documentation.

## Preconditions And Authorization

Before provisioning a VPS, record:

```text
Commit:
Purpose and test window:
Infrastructure owner:
Provider and account owner:
VPS hostname / asset ID:
Public hostname:
Administrator(s):
Source IPs allowed for SSH:
Planned creation time:
Planned shutdown time:
Cleanup owner:
```

Required conditions:

- explicit permission from the infrastructure and provider account owner;
- disposable QA traffic only;
- no production users or unrelated services on the endpoint;
- trusted administrator workstation and fresh repository source;
- domain ownership when public HTTPS is required;
- credentials, SSH keys, tokens, and certificate private keys stored outside Git;
- an agreed shutdown and cleanup procedure;
- a written decision about logs and retention before traffic begins.

Do not deploy from an untrusted cache, unknown archive, or historical local clone.

## Current Relay Limitations

The current server:

- implements `/health` and the message POST/GET/DELETE API;
- stores envelopes in memory;
- limits request body size to 2 MB;
- accepts TTL only within its configured maximum;
- clears all in-memory messages when the process restarts;
- has no built-in client authentication or authorization;
- has no tenant isolation, durable queue, replication, backup, high availability, production
  rate limiting, quota, monitoring, or abuse response;
- cannot prevent a relay operator from observing metadata or denying service.

A reverse proxy can add TLS and some resource controls, but it does not turn this server into
production infrastructure.

## Minimum QA Host Baseline

A temporary QA host should have:

- a supported, fully patched Linux distribution;
- a dedicated provider account or clearly isolated project;
- one named administrator and recovery contact;
- public DNS controlled by the project owner;
- inbound TCP 80/443 only where required for certificate issuance and HTTPS;
- SSH restricted by provider firewall or host firewall where practical;
- public-key authentication with password and keyboard-interactive login disabled;
- a non-root service account;
- automatic security update policy reviewed for the test window;
- enough memory and disk for the OS, proxy, journal, and controlled QA load;
- no observability agent configured to capture request bodies;
- time synchronization;
- explicit teardown after QA.

Capacity values such as 1 vCPU or 1 GB RAM may be sufficient for a small test, but they are
not a performance or production-sizing recommendation.

## 1. Prepare The Host

On a newly provisioned authorized VPS:

1. apply OS security updates;
2. create or verify a non-root administrator with `sudo`;
3. install the administrator's public SSH key;
4. test a second authenticated session before changing SSH policy;
5. disable password and keyboard-interactive authentication after verifying key access;
6. restrict root login according to the owner's policy;
7. review all files under `/etc/ssh/sshd_config.d/`, including cloud-init overrides;
8. validate effective SSH configuration before reload;
9. keep the provider console or recovery channel available.

Do not blindly copy historical SSH settings. Distribution and provider defaults can differ.
A failed SSH change can lock out the administrator.

Never commit or paste private keys, passwords, agent sockets, provider tokens, or recovery
codes into repository files or QA reports.

## 2. Install Runtime From Trusted Sources

The relay requires Node.js 20+ according to the project documentation. Install it through a
trusted and verified package source appropriate to the selected OS. Record exact versions:

```bash
node --version
npm --version
```

Avoid treating an unaudited `curl | shell` command as a repository guarantee. Follow the
current official package instructions and verify package signatures or repository origin
according to the host policy.

The current relay has no npm runtime dependencies, but this must be rechecked at the selected
commit before deployment.

## 3. Create A Dedicated Service Account

Example:

```bash
sudo useradd --system \
  --home /opt/secure-messenger-relay \
  --shell /usr/sbin/nologin \
  securemessenger
sudo install -d -o securemessenger -g securemessenger -m 0750 \
  /opt/secure-messenger-relay
```

The account should not have interactive login, `sudo`, SSH keys, or access to unrelated
application data.

## 4. Transfer Reviewed Relay Files

Transfer only the reviewed `android-app/server/relay` contents from the selected commit.
Verify the source commit before copying.

One possible administrator workflow is to stage files in a temporary administrator-owned
directory and then copy them into `/opt/secure-messenger-relay` with controlled ownership.

Do not transfer:

- `.env` files or provider credentials;
- SSH, Android signing, or certificate private keys;
- APKs, Gradle caches, build outputs, `node_modules`, or local databases;
- QA logs, invitation payloads, messages, or user data;
- editor backups and machine-specific paths.

After transfer, review ownership and permissions:

```bash
sudo chown -R securemessenger:securemessenger /opt/secure-messenger-relay
sudo find /opt/secure-messenger-relay -type d -exec chmod 0750 {} \;
sudo find /opt/secure-messenger-relay -type f -exec chmod 0640 {} \;
```

Record the deployed commit in a local administrator-controlled deployment record. Do not
claim reproducible deployment solely from a copied directory.

## 5. Test The Relay On Loopback

Run manually as the service user:

```bash
cd /opt/secure-messenger-relay
sudo -u securemessenger \
  env HOST=127.0.0.1 PORT=8787 \
  /usr/bin/node src/server.mjs
```

In another session:

```bash
curl --fail --silent --show-error http://127.0.0.1:8787/health
```

Expected body:

```json
{"status":"ok"}
```

Stop the manual process before enabling systemd.

Loopback health proves only local process reachability. It does not test proxy routing,
public TLS, message API behavior, retention, or Android delivery.

## 6. Install The systemd Example Carefully

Repository example:

```text
android-app/deploy/secure-messenger-relay.service.example
```

The reviewed template currently:

- runs as `securemessenger`;
- uses `/opt/secure-messenger-relay`;
- binds `HOST=127.0.0.1` and `PORT=8787`;
- restarts after failure;
- enables `NoNewPrivileges`, `PrivateTmp`, `ProtectSystem=full`, and `ProtectHome`.

Copy it only after inspecting paths, Node location, user, group, and hardening settings for
the selected host:

```bash
sudo install -m 0644 \
  /path/to/repository/android-app/deploy/secure-messenger-relay.service.example \
  /etc/systemd/system/secure-messenger-relay.service
sudo systemctl daemon-reload
sudo systemctl enable --now secure-messenger-relay
sudo systemctl status --no-pager secure-messenger-relay
```

Check loopback health again.

The example is a starting point, not a complete sandbox. Review additional systemd controls,
resource limits, restart loops, writable paths, process memory, and journal retention before
broader exposure.

## 7. Journal And Logging Review

Inspect service logs:

```bash
sudo journalctl -u secure-messenger-relay -n 100 --no-pager
```

Expected normal output should be limited to lifecycle information such as listening address.
Do not add request-body, ciphertext, queue ID, key, nonce, invitation, or message logging.

Also review:

- reverse-proxy access and error logs;
- hosting-provider request capture;
- firewall and intrusion-detection logs;
- shell history;
- crash dumps and observability agents;
- systemd journal retention and access permissions.

The Node process's logging intent does not prove that surrounding infrastructure is
payload-free.

## 8. Configure DNS And HTTPS

Use a project-controlled domain such as `relay.example.com`. Confirm DNS ownership and record
changes before exposing the service.

### Caddy Example

Repository file:

```text
android-app/deploy/Caddyfile.example
```

The reviewed example:

- serves one placeholder hostname;
- routes only `/health`, `/v1/messages`, and `/v1/messages/*`;
- proxies to `127.0.0.1:8787`;
- adds basic response headers;
- returns 404 for other paths.

Install Caddy from its current official source for the selected OS, replace the placeholder
hostname, validate the file, and reload:

```bash
sudo caddy validate --config /etc/caddy/Caddyfile
sudo systemctl reload caddy
```

Caddy's successful certificate issuance and config validation do not add client
authentication, queue authorization, durable storage, or production abuse controls.

### Nginx Alternative

Repository file:

```text
android-app/deploy/nginx.example.conf
```

The reviewed example includes:

- HTTP-to-HTTPS redirect;
- placeholder certificate paths;
- a 2 MB client-body limit;
- routing to loopback relay;
- a basic per-IP request-rate placeholder for message paths;
- 404 for unrelated paths.

Replace every placeholder and validate the final configuration before reload:

```bash
sudo nginx -t
```

The example's rate limit is not a reviewed abuse-control policy and can affect legitimate
retry traffic. Test it with the current client behavior.

Do not commit private certificate files or provider-specific secrets.

## 9. Firewall And Network Exposure

At both provider and host layers:

- allow SSH only from approved administrator sources where practical;
- allow TCP 80 only if needed for redirect or certificate validation;
- allow TCP 443 for HTTPS QA;
- deny public TCP 8787;
- keep the Node relay bound to `127.0.0.1`;
- review IPv4 and IPv6 rules separately;
- verify that an old rule or provider security group does not expose the origin port.

Example UFW review sequence:

```bash
sudo ufw status verbose
sudo ss -lntp
```

Do not enable or change firewall rules remotely without preserving an authenticated recovery
path.

## 10. Public Verification

From a network outside the VPS:

```bash
curl --fail --silent --show-error https://relay.example.com/health
```

Then verify that direct public origin access is denied:

```text
http://relay.example.com:8787
```

Do not include a real admin IP, provider token, or private hostname in public evidence.

A public health response is not sufficient. Before Android QA, verify the message API through
controlled tests and inspect proxy logs for accidental identifiers or bodies.

## 11. Android QA Configuration

On the physical phone and Android Emulator:

1. use the same HTTPS base URL;
2. save the relay URL;
3. run the in-app health check;
4. run conversation preflight;
5. perform reciprocal invitation import;
6. verify warning-only first-contact behavior;
7. complete A → B and B → A delivery;
8. distinguish `SENT`, `RECEIVED`, and `DELIVERED`;
9. confirm encrypted delivery receipts are not visible chat messages;
10. perform independent safety-number or verification-QR comparison;
11. test retry only within the authorized outage window;
12. confirm no duplicate plaintext.

Follow [PHONE_EMULATOR_PUBLIC_RELAY_QA.md](PHONE_EMULATOR_PUBLIC_RELAY_QA.md) for the detailed
client procedure.

`DELIVERED` means authenticated receiver-app acceptance, decryption, and durable storage. It
is not a read receipt.

## 12. Controlled Restart And Reboot QA

A restart or reboot test must be explicitly in scope because the relay store is in memory.
Restarting the Node service loses queued envelopes that were not already deleted.

Record separately:

- service restart behavior;
- process crash/restart loop;
- VPS reboot and service ordering;
- proxy availability;
- client retry recovery;
- any message lost because the current in-memory relay restarted;
- receipt and duplicate behavior.

Do not interpret automatic service startup as durable message storage or high availability.

## 13. TTL, Retention, And Deletion

The relay accepts client expiration timestamps up to its configured maximum and purges
expired records during store activity. For QA:

- use short, bounded TTL values from the current client behavior;
- do not modify the server to retain messages indefinitely;
- distinguish client `DELETE`, TTL expiry, and process-memory loss;
- inspect reverse-proxy and provider retention separately;
- document that client reset does not remotely purge every copy;
- do not describe deletion as forensic erasure.

A production retention policy requires a separate privacy and operations design.

## 14. Basic Resource And Abuse Controls

The minimal Node relay has no built-in production controls. Temporary QA exposure should
limit:

- test window duration;
- source networks where possible;
- request rate and body size at proxy/provider layers;
- total provider resources;
- administrator access;
- log retention.

Do not perform load, flood, denial-of-service, firewall-bypass, or credential testing without
separate written authorization and scope.

## 15. Updates And Rollback

For every QA redeployment:

1. record the old and new commit;
2. review the one-step change;
3. run relay tests before transfer;
4. keep a known deployment artifact or source snapshot for rollback without including
   secrets;
5. stop the service before replacing incompatible files when required;
6. validate health and full Android flow after update;
7. record rollback result if used.

Copying `main` without pinning a commit is not a controlled deployment procedure.

## 16. Incident Response During QA

If unexpected traffic, logging, data retention, compromise, or provider warning occurs:

1. stop public access;
2. preserve only necessary sanitized evidence;
3. revoke temporary credentials and provider tokens;
4. inspect administrative access and host changes;
5. do not continue Android testing until ownership and integrity are restored;
6. create a separate incident and remediation record;
7. disclose any possibility that invitation data, queue identifiers, ciphertext, or
   credentials were exposed.

Do not place raw sensitive evidence in a public issue.

## 17. Teardown

A temporary QA deployment is incomplete until teardown is recorded.

1. stop and disable the relay service when no longer needed;
2. stop or remove the reverse proxy site;
3. remove temporary DNS records;
4. close provider and host firewall rules;
5. revoke temporary SSH and provider credentials;
6. inspect and sanitize logs;
7. remove relay files and temporary deployment material according to the owner's policy;
8. deprovision the VPS when appropriate;
9. verify the public health endpoint is unreachable;
10. record resources intentionally retained and their owner.

Deleting the VPS does not prove provider backups or logs were immediately erased.

## QA Exit Criteria

A QA deployment result must record:

- exact repository commit and deployed files;
- administrator and infrastructure authorization;
- OS, Node, proxy, and service versions;
- DNS and TLS result;
- origin port exposure check;
- systemd and proxy configuration validation;
- log review;
- Android bidirectional delivery, receipt, retry, and duplicate results;
- restart/reboot result if in scope;
- all failures and skips;
- endpoint teardown or explicit retained-resource owner.

No result from this guide authorizes production use.

## Production Gap

A production relay would require a separate approved design covering:

- authenticated and authorized queue access;
- rate, abuse, quota, storage, and resource controls;
- durable storage and availability semantics;
- certificate, DNS, secret, and administrative-access lifecycle;
- privacy, metadata, logging, and retention policy;
- monitoring, alerting, patching, incident response, backup, and recovery;
- deployment provenance, signing, rollback, and vulnerability handling;
- legal and operational ownership;
- independent security review.

TLS, systemd, Caddy/Nginx, UFW, and SSH hardening are necessary operational measures but are
not sufficient for production security.

## Related Documents

- [Deployment Examples](../deploy/README.md)
- [Public Relay Testing Notes](RELAY_PUBLIC_TESTING.md)
- [Phone + Emulator Relay QA](PHONE_EMULATOR_PUBLIC_RELAY_QA.md)
- [Threat Model](THREAT_MODEL.md)
- [Known Limitations](KNOWN_LIMITATIONS.md)
- [Task Queue](ai-workflow/TASK_QUEUE.md)
