import { createServer } from "node:http";
import { pathToFileURL } from "node:url";
import { RateLimiter, RelayError, RelayStore, QUEUE_ID_PATTERN, readSecretMatches } from "./relay-store.mjs";

const MAX_BODY_BYTES = 80 * 1024;

/**
 * Relay API v3 (DEC-024):
 *   POST   /v1/queues/{queueId}/messages              anyone; body {expiresAtEpochMillis, opaqueCiphertext}
 *   GET    /v1/queues/{queueId}/messages              Authorization: Bearer <read secret>
 *   DELETE /v1/queues/{queueId}/messages/{messageId}  Authorization: Bearer <read secret>
 *   GET    /health
 */
export function createRelayServer({
  store = new RelayStore(),
  sendLimiter = new RateLimiter(),
  readLimiter = new RateLimiter({ capacity: 60, refillPerMinute: 240 }),
} = {}) {
  return createServer(async (request, response) => {
    response.setHeader("Content-Type", "application/json; charset=utf-8");
    try {
      const url = new URL(request.url, "http://relay.invalid");
      if (request.method === "GET" && url.pathname === "/health") {
        respond(response, 200, { status: "ok" });
        return;
      }
      const match = url.pathname.match(/^\/v1\/queues\/([^/]+)\/messages(?:\/([^/]+))?$/);
      if (!match) throw new RelayError(404, "not_found");
      const queueId = match[1];
      const messageId = match[2];
      if (!QUEUE_ID_PATTERN.test(queueId)) throw new RelayError(400, "invalid_request");
      const client = request.socket.remoteAddress ?? "unknown";

      if (request.method === "POST" && messageId === undefined) {
        if (!sendLimiter.allow(client)) throw new RelayError(429, "rate_limited");
        const input = JSON.parse(await readBody(request));
        const record = store.put(queueId, input);
        respond(response, 201, { messageId: record.messageId });
        return;
      }
      if ((request.method === "GET" && messageId === undefined) || (request.method === "DELETE" && messageId !== undefined)) {
        if (!readLimiter.allow(client)) throw new RelayError(429, "rate_limited");
        const bearer = /^Bearer (.+)$/.exec(request.headers.authorization ?? "")?.[1];
        if (!readSecretMatches(queueId, bearer)) throw new RelayError(403, "forbidden");
        if (request.method === "GET") {
          respond(response, 200, store.list(queueId));
        } else {
          store.delete(queueId, messageId);
          respond(response, 204);
        }
        return;
      }
      throw new RelayError(405, "method_not_allowed");
    } catch (failure) {
      if (failure instanceof RelayError) {
        respond(response, failure.status, { error: failure.code });
      } else {
        respond(response, 400, { error: "invalid_request" });
      }
    }
  });
}

/** Reads the body up to MAX_BODY_BYTES; the rest is drained unread so a 413 can still be sent. */
function readBody(request) {
  return new Promise((resolve, reject) => {
    let size = 0;
    let tooLarge = false;
    const chunks = [];
    request.on("data", (chunk) => {
      size += chunk.length;
      if (size > MAX_BODY_BYTES) {
        tooLarge = true;
        chunks.length = 0;
        return;
      }
      if (!tooLarge) chunks.push(chunk);
    });
    request.on("end", () =>
      tooLarge ? reject(new RelayError(413, "too_large")) : resolve(Buffer.concat(chunks).toString("utf8")),
    );
    request.on("error", reject);
  });
}

function respond(response, status, value) {
  if (response.writableEnded) return;
  response.statusCode = status;
  response.end(value === undefined ? "" : JSON.stringify(value));
}

// pathToFileURL makes the check work on Windows too.
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const port = Number(process.env.PORT || 8787);
  const host = process.env.HOST || "0.0.0.0";
  const server = createRelayServer();
  server.listen(port, host, () => {
    // Do not log message bodies, queue IDs, secrets, or ciphertext envelopes.
    console.info(`Relay listening on ${host}:${port}`);
  });
}
