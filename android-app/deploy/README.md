# Relay Deployment Examples

## Role And Claim Boundary

**Repository baseline reviewed:** `6f48b99495b6c69e3d802bc835c59340d615d8bd`

This directory contains placeholder configuration examples for a temporary HTTPS QA deployment
of the minimal Node relay. The files are not a production stack, complete hardening profile,
infrastructure-as-code system, or proof that a deployment was reviewed or tested.

The examples contain no intended real domain, IP address, SSH key, TLS private key, provider
token, password, or application secret. Keep it that way.

Before use, read [VPS Relay Deployment For Controlled QA](../docs/VPS_RELAY_DEPLOYMENT.md).

## Current Relay Boundary

The examples front the repository's development relay, which currently:

- exposes `/health` and the relay API v3 (DEC-024): `POST`, `GET` and `DELETE` under
  `/v1/queues/{queueId}/messages`;
- stores opaque envelopes in process memory;
- loses queued records on process restart;
- has only QA-level limits (per-address rate limits, per-queue and whole-relay caps, DEC-026 and
  DEC-028) and no production authentication, tenant isolation, durable storage, replication,
  monitoring, or high availability;
- counts rate limits per connecting address; behind a reverse proxy on the same machine set
  `RELAY_TRUST_PROXY=1`, so it counts the client address the proxy adds last to
  `X-Forwarded-For` instead of the proxy's own (audit M1, DEC-028);
- can still expose source IP, timing, queue identifiers, envelope sizes, frequency, and
  availability metadata to infrastructure operators.

HTTPS and service supervision do not change those limitations.

## Files

### `secure-messenger-relay.service.example`

A systemd starting point for the Node process. The current example:

- runs as user and group `securemessenger`;
- expects `/opt/secure-messenger-relay`;
- executes `/usr/bin/node src/server.mjs`;
- binds `HOST=127.0.0.1` and `PORT=8787`;
- sets `RELAY_TRUST_PROXY=1` for the proxy examples below;
- restarts after failure;
- enables `NoNewPrivileges`, `PrivateTmp`, `ProtectSystem=full`, and `ProtectHome`.

It does not define a complete sandbox, resource quota, memory limit, CPU limit, file
permission policy, journal retention policy, deployment provenance, update procedure, or
rollback procedure.

Inspect and adapt:

- service user and group;
- working directory;
- Node binary path;
- writable paths;
- restart policy and failure loop behavior;
- systemd hardening supported by the selected distribution;
- resource and process limits;
- environment and secret handling;
- journal access and retention.

Do not place credentials in the unit file or environment lines committed to Git.

### `Caddyfile.example`

A Caddy placeholder for one HTTPS hostname. The current example:

- routes only `/health` and `/v1/queues/*` (relay API v3; the old `/v1/messages` routes are gone);
- refuses request bodies over 100 KB (the relay's own limit is 80 KB);
- proxies to `127.0.0.1:8787`, replacing `X-Forwarded-For` with the client address;
- adds basic response headers;
- returns 404 for unrelated paths;
- uses placeholder `relay.example.com`.

It does not add client authentication, queue authorization, rate limiting, durable storage,
privacy guarantees, or production monitoring. Review Caddy access/error logging and any
provider-layer capture before test traffic begins.

Replace the hostname, validate the final configuration, and confirm that the origin port is
not publicly reachable.

### `nginx.example.conf`

An Nginx alternative with:

- HTTP-to-HTTPS redirect;
- placeholder Certbot certificate paths;
- a 100 KB body-size limit, just above the relay's own 80 KB request limit;
- loopback proxying of `/health` and `/v1/queues/` (relay API v3), appending the client address
  to `X-Forwarded-For`;
- a simple per-IP request-rate placeholder for message paths;
- 404 for unrelated paths.

The included rate is not a reviewed abuse-control policy. It may reject legitimate retries or
be ineffective against distributed abuse. Test the chosen values against current client
behavior and record changes outside the example file when they are environment-specific.

Review and replace:

- hostname;
- certificate paths;
- IPv4 and IPv6 listeners;
- rate and burst values;
- log format and retention;
- proxy timeouts;
- body-size behavior;
- forwarded-header policy;
- provider or load-balancer interaction.

### `check-relay-health.sh`

A small helper that:

- requires exactly one base URL;
- rejects non-HTTPS URLs for public VPS testing;
- requests `<base-url>/health` with a ten-second timeout;
- exits nonzero on HTTP or connection failure.

Example:

```bash
./deploy/check-relay-health.sh https://relay.example.com
```

A successful result proves only HTTPS reachability to a health response. It does not prove
ownership, correct message routing, safe logs, authentication, retention, abuse controls,
Android delivery, or production readiness.

Do not pass a URL containing embedded credentials, access tokens, query secrets, or private
infrastructure information that could enter shell history or logs.

## Expected QA Layout

The current examples assume:

```text
/opt/secure-messenger-relay/
  package.json
  src/
    relay-store.mjs
    server.mjs
```

Deploy only files reviewed at a specific repository commit. Do not copy `.env`, keys,
credentials, APKs, Gradle caches, databases, QA logs, invitation payloads, `node_modules`, or
unrelated repository material.

The systemd example expects the service user to own or read the required files. Adapt
permissions deliberately rather than using broad world-writable access.

## Placeholder Policy

Values such as these are intentionally non-real:

```text
relay.example.com
/opt/secure-messenger-relay
securemessenger
127.0.0.1:8787
```

A deployment operator must replace or validate every placeholder. Never commit the resulting
real infrastructure configuration when it contains:

- private domains that should remain undisclosed;
- provider identifiers or account details;
- SSH authorized keys or private keys;
- TLS private keys;
- tokens, passwords, webhook URLs, or recovery codes;
- real source-IP allowlists;
- production paths or secrets;
- user data or message envelopes.

Environment-specific safe configuration may be maintained in a separately controlled private
operations repository only after an explicit ownership and access decision.

## Pre-Use Review Checklist

Before copying an example:

- [ ] Select and record an exact repository commit.
- [ ] Confirm the deployment is authorized and limited to controlled QA.
- [ ] Review the current relay source and tests.
- [ ] Confirm Node and proxy versions from trusted sources.
- [ ] Verify the service user, paths, and file permissions.
- [ ] Confirm the Node process binds only to loopback.
- [ ] Confirm public port `8787` is denied at host and provider layers.
- [ ] Replace every placeholder hostname and certificate path.
- [ ] Review IPv4 and IPv6 exposure.
- [ ] Review proxy and provider logs for body, path, and identifier leakage.
- [ ] Review journal permissions and retention.
- [ ] Set a test window and teardown owner.
- [ ] Keep credentials outside Git and shell transcripts.
- [ ] Validate systemd and proxy configuration before reload.
- [ ] Test rollback and recovery access.

## Validation Boundary

Typical validation commands may include:

```bash
sudo systemd-analyze verify /etc/systemd/system/secure-messenger-relay.service
sudo caddy validate --config /etc/caddy/Caddyfile
sudo nginx -t
sudo ss -lntp
```

Use only commands relevant to the chosen stack. A successful syntax check does not prove the
service is securely configured or functionally compatible with Android clients.

After deployment, verify separately:

- loopback `/health`;
- public HTTPS `/health`;
- direct public origin port denial;
- required relay API methods and status codes;
- Android mutual pairing and warning-only first-contact behavior;
- A → B and B → A `SENT`, `RECEIVED`, and `DELIVERED` transitions;
- retry and duplicate behavior;
- sanitized logs;
- endpoint teardown.

## Logging Rules

Do not add logs for:

- plaintext or user-visible message text;
- invitation payloads;
- private, session, or message keys;
- nonce values;
- ciphertext or opaque envelope bodies;
- full queue identifiers;
- credentials, provider tokens, or SSH material.

Even request paths, source IPs, timestamps, status codes, and response sizes are metadata.
Limit collection and retention to the controlled QA need.

The Node process's minimal logging does not govern Caddy, Nginx, systemd, cloud provider,
firewall, or observability logs. Review the complete path.

## Update And Rollback

These examples do not automate deployment. For each update:

1. record old and new commits;
2. review the exact file change;
3. run relay tests before transfer;
4. back up only the previous non-secret configuration and source needed for rollback;
5. validate the candidate configuration;
6. reload or restart in a controlled window;
7. test health and Android delivery;
8. record rollback if required.

Do not deploy an unpinned moving branch as if it were a reproducible artifact.

## Teardown

Temporary QA infrastructure must have an explicit end state:

- stop and disable the relay service;
- remove or disable the proxy site;
- remove temporary DNS and firewall rules;
- revoke temporary credentials;
- inspect and sanitize logs;
- remove temporary source and configuration according to owner policy;
- deprovision the VPS when appropriate;
- verify the public health endpoint is no longer reachable;
- record anything intentionally retained and its owner.

Deletion of a VPS does not prove immediate deletion of provider snapshots, logs, or backups.

## Production Gap

These examples must not be extended casually into production. A production relay requires a
separate approved architecture and operational program covering authentication,
authorization, abuse controls, durable storage, availability, privacy, retention, secret and
certificate lifecycle, monitoring, incident response, backup/recovery, updates, rollback,
infrastructure ownership, and independent review.

The production program remains blocked in the project task queue.

## Related Documents

- [VPS Relay Deployment For Controlled QA](../docs/VPS_RELAY_DEPLOYMENT.md)
- [Public Relay Testing Notes](../docs/RELAY_PUBLIC_TESTING.md)
- [Phone + Emulator Relay QA](../docs/PHONE_EMULATOR_PUBLIC_RELAY_QA.md)
- [Known Limitations](../docs/KNOWN_LIMITATIONS.md)
- [Task Queue](../docs/ai-workflow/TASK_QUEUE.md)
