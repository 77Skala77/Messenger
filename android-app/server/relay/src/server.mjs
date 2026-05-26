import { createServer } from "node:http";
import { RelayStore } from "./relay-store.mjs";

const MAX_BODY_BYTES = 2 * 1024 * 1024;

export function createRelayServer({ store = new RelayStore() } = {}) {
  return createServer(async (request, response) => {
    response.setHeader("Content-Type", "application/json; charset=utf-8");
    try {
      const url = new URL(request.url, "http://relay.invalid");
      if (request.method === "POST" && url.pathname === "/v1/messages") {
        const input = JSON.parse(await readJsonBody(request));
        const record = store.put(input);
        respond(response, 201, { messageId: record.messageId });
        return;
      }
      const queueMatch = url.pathname.match(/^\/v1\/messages\/([^/]+)$/);
      if (request.method === "GET" && queueMatch) {
        respond(response, 200, store.list(decodeURIComponent(queueMatch[1])));
        return;
      }
      if (request.method === "DELETE" && queueMatch) {
        store.delete(decodeURIComponent(queueMatch[1]));
        respond(response, 204);
        return;
      }
      respond(response, 404, { error: "not_found" });
    } catch {
      respond(response, 400, { error: "invalid_request" });
    }
  });
}

function readJsonBody(request) {
  return new Promise((resolve, reject) => {
    let body = "";
    request.setEncoding("utf8");
    request.on("data", (chunk) => {
      body += chunk;
      if (Buffer.byteLength(body, "utf8") > MAX_BODY_BYTES) {
        reject(new Error("Request too large"));
        request.destroy();
      }
    });
    request.on("end", () => resolve(body));
    request.on("error", reject);
  });
}

function respond(response, status, value) {
  response.statusCode = status;
  response.end(value === undefined ? "" : JSON.stringify(value));
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const port = Number(process.env.PORT || 8787);
  const server = createRelayServer();
  server.listen(port, "127.0.0.1", () => {
    // Do not log message bodies, queue IDs, keys, or ciphertext envelopes.
    console.info(`Relay listening on http://127.0.0.1:${port}`);
  });
}
