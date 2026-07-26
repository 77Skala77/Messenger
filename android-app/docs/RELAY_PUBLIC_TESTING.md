# Public Relay Testing Notes

## Role And Evidence Boundary

**Repository baseline reviewed:** `05b7d542f59abefaf2db18e5b202bc49fdc1d3f0`

This document defines decision and safety boundaries for making the development relay
reachable by separated QA clients. It is not a deployment runbook, production design, audit
report, or proof that any public endpoint is currently authorized or reachable.

Public reachability does not improve the client session protocol. Normal chats still use
`SimplifiedSessionManager` and `STANDARD_AEAD`; Signal-backed sessions and
`PARANOIA_C4_AEAD` remain disabled.

## Prefer Local QA First

Use the least exposed route that satisfies the test:

1. local Node relay and Android Emulator host alias for emulator-only QA;
2. local Node relay over a trusted LAN for physical-device QA;
3. temporary authorized HTTPS tunnel or QA VPS only when clients cannot reach one local
   relay.

A public endpoint adds infrastructure, metadata, abuse, retention, provider, and cleanup
risk. Convenience alone is not a reason to keep it online.

## Current Minimal Relay Boundary

The repository relay currently provides:

```text
GET    /health
POST   /v1/messages
GET    /v1/messages/{queueId}
DELETE /v1/messages/{messageId}
```

The current implementation:

- uses an in-memory store;
- accepts opaque ciphertext and expiration metadata;
- enforces a request-body limit and maximum TTL;
- purges expired records during normal store operations;
- has no production authentication or authorization layer;
- has no built-in rate limiting, quotas, tenant isolation, durable storage, replication,
  monitoring, alerting, backup, or high-availability behavior;
- does not establish that proxy, host, platform, or network logs are payload-free.

These are development characteristics, not production guarantees.

## Reachability Is Not Security

A successful public `/health` response proves only that a route reached a server answering
that path. It does not prove:

- that the endpoint belongs to the expected operator;
- that all relay API methods are routed correctly;
- that TLS terminates at an authorized location;
- that logs exclude envelopes and queue identifiers;
- that data expires as expected;
- that the service has authentication, abuse controls, or resource limits;
- that the client is protected from delay, deletion, replay, reordering, flooding, or
  withholding;
- that the endpoint is suitable for real communications.

Test the complete QA flow separately and record the exact endpoint owner and lifetime.

## Minimum Rules For A Public QA Endpoint

A temporary public QA endpoint must:

- use HTTPS with certificate validation for every internet-reachable client request;
- be explicitly authorized by the infrastructure owner;
- have a named operator and planned shutdown time;
- route only the required relay API;
- avoid caching message-list responses or request bodies;
- avoid logging plaintext, invitation payloads, private or session keys, nonce values,
  ciphertext bodies, full queue IDs, credentials, or provider tokens;
- restrict administrative access;
- use disposable QA messages and profiles only;
- be removed or disabled immediately after the test;
- have sanitized evidence of creation, use, and cleanup.

Debug HTTP remains limited to local LAN, emulator-host, or explicitly isolated development
routing. Never expose the repository Node HTTP listener directly to the public internet.

## TLS And Metadata Boundary

HTTPS protects the transport hop between a client and the TLS endpoint. It does not hide
from the endpoint operator or upstream infrastructure:

- source IP and connection timing;
- queue identifiers in request paths or bodies;
- approximate request and response sizes;
- traffic frequency and retry patterns;
- the fact that multiple clients use the same endpoint;
- availability and error behavior.

A reverse tunnel may add another operator with access to metadata and logs. Document where
TLS terminates and which parties can observe traffic metadata.

## Endpoint Selection Checklist

Before using a tunnel, VPS, or hosted service, record:

```text
Commit:
Purpose:
Endpoint owner:
Operator:
Provider:
TLS termination point:
Public hostname:
Creation time:
Planned expiry:
Administrative access holders:
Logging configuration:
Retention configuration:
Cleanup owner:
```

Confirm:

- the service permits this QA use;
- no production or unrelated user traffic shares the test endpoint;
- credentials are stored outside Git;
- temporary configuration files are ignored and access-restricted;
- host and proxy clocks are correct enough for TTL diagnosis;
- payload and queue identifiers are excluded from application, proxy, platform, and access
  logs where possible;
- the endpoint can be shut down without affecting another service.

## Functional QA Requirements

For phone + emulator or separated-device QA, the endpoint must preserve:

- HTTP methods;
- API paths;
- JSON request and response bodies;
- status codes, including `201`, `204`, `400`, and `404` where applicable;
- request-body capacity required by the development relay;
- no-cache behavior for queue fetches;
- timeout values sufficient for controlled QA without masking hangs.

Both clients use the same HTTPS base URL. Follow
[PHONE_EMULATOR_PUBLIC_RELAY_QA.md](PHONE_EMULATOR_PUBLIC_RELAY_QA.md) for pairing,
warning-only trust, receipt, retry, and cleanup verification.

## Test Data Rules

Use only:

- disposable local profiles;
- random or clearly synthetic display names;
- meaningless test messages;
- short-lived invitations exchanged directly between test operators;
- sanitized diagnostic reports.

Do not use:

- real sensitive messages;
- real user identities or address books;
- production keys, signing material, access tokens, or credentials;
- a public issue, chat, or log to transfer invitation payloads;
- a shared endpoint whose other users could be affected by outage or flooding tests.

## Logging Boundary

The repository Node process is intended not to log message bodies, queue IDs, keys, or
ciphertext envelopes during normal startup and request processing. That intent does not cover
all surrounding layers.

Review separately:

- reverse-proxy access and error logs;
- tunnel-provider dashboards;
- hosting-platform request capture;
- systemd journal output;
- firewall and intrusion-detection logs;
- crash dumps and observability agents;
- shell history and copied terminal output.

Prefer minimal event classes, status codes, aggregate counts, and redacted identifiers. Do
not claim logs are safe without inspecting the complete path.

## TTL And Deletion Boundary

Relay deletion occurs through the client protocol after durable acceptance or authenticated
duplicate recognition. Undelivered records may remain until TTL or infrastructure behavior.

A QA report should distinguish:

- successful client `DELETE`;
- TTL expiry;
- process restart that clears the current in-memory store;
- proxy or hosting retention outside the Node process;
- client reset, which does not remotely purge every relay copy.

Do not represent local app reset as public relay deletion or forensic erasure.

## Failure And Abuse Testing

Safe QA may cover:

- temporary endpoint unavailability;
- client retry and manual synchronization;
- expired envelope handling;
- duplicate authenticated delivery;
- invalid request rejection;
- endpoint cleanup verification.

Do not perform load, flooding, denial-of-service, credential, firewall-bypass, or provider
abuse testing without explicit authorization and a scoped test plan. The minimal relay has no
production abuse controls and should not be left exposed for opportunistic testing.

## Diagnostics

Record only:

- commit and environment;
- endpoint mode and owner;
- creation and cleanup timestamps;
- health status;
- HTTP status classes;
- client message-state transitions;
- counts and short identifier fingerprints;
- sanitized errors.

Do not record message text, full queue IDs, invitation payloads, keys, nonce, ciphertext,
provider credentials, SSH private keys, or live tokens.

## Shutdown And Cleanup

After testing:

1. stop or revoke the public route;
2. verify the public `/health` is no longer reachable when shutdown was intended;
3. remove temporary DNS, tunnel, proxy, firewall, and service configuration that is no
   longer needed;
4. revoke temporary provider tokens or credentials;
5. inspect and sanitize retained logs;
6. delete local credential files and shell exports;
7. preserve only the sanitized QA result;
8. record any resource that could not be deleted and its owner.

Endpoint cleanup must be an explicit result, not an assumption.

## Historical Infrastructure Boundary

Repository documents describe historical QA through a temporary Ubuntu VPS and an HTTP-by-IP
endpoint. Those records are useful context only.

They do not establish current:

- endpoint reachability or ownership;
- DNS or TLS;
- Caddy, systemd, firewall, or SSH configuration;
- logging and retention behavior;
- provider authorization;
- production readiness.

Never reuse a historical endpoint solely because it appears in documentation.

## Production Gap

TQ-107 production relay and operational-security design remains blocked. A production
service would require a separately approved design covering, at minimum:

- authenticated and authorized access;
- abuse, rate, queue, storage, and resource controls;
- durable and highly available storage semantics where required;
- certificate, DNS, key, and secret lifecycle;
- minimal logging and retention enforcement;
- monitoring, alerting, patching, incident response, backup, and recovery;
- infrastructure ownership and administrative access control;
- privacy review and metadata limitations;
- deployment, rollback, and vulnerability-handling procedures;
- independent security review.

Even a hardened relay would not repair the prototype session protocol, first-contact
identity risk, local-device exposure, or missing metadata-protection features.

## Related Documents

- [Phone + Emulator Relay QA](PHONE_EMULATOR_PUBLIC_RELAY_QA.md)
- [Two Physical Devices](TWO_DEVICE_TEST.md)
- [VPS Relay Deployment](VPS_RELAY_DEPLOYMENT.md)
- [Deployment Examples](../deploy/README.md)
- [Threat Model](THREAT_MODEL.md)
- [Known Limitations](KNOWN_LIMITATIONS.md)
- [Task Queue](ai-workflow/TASK_QUEUE.md)
