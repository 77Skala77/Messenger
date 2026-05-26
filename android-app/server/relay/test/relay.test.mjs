import assert from "node:assert/strict";
import { test } from "node:test";
import { RelayStore } from "../src/relay-store.mjs";
import { createRelayServer } from "../src/server.mjs";

test("stores only an opaque envelope and deletes delivered messages", async () => {
  const server = createRelayServer();
  await listen(server);
  const base = `http://127.0.0.1:${server.address().port}`;
  const envelope = {
    messageId: "message-1",
    queueId: "queue-00000001",
    opaqueCiphertext: "bm90LXBsYWludGV4dA==",
    expiresAtEpochMillis: Date.now() + 60_000,
  };
  const post = await fetch(`${base}/v1/messages`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(envelope),
  });
  assert.equal(post.status, 201);
  const listed = await (await fetch(`${base}/v1/messages/queue-00000001`)).json();
  assert.deepEqual(listed, [envelope]);
  assert.equal((await fetch(`${base}/v1/messages/message-1`, { method: "DELETE" })).status, 204);
  assert.deepEqual(await (await fetch(`${base}/v1/messages/queue-00000001`)).json(), []);
  server.close();
});

test("expires pending encrypted messages by TTL", () => {
  let time = 10_000;
  const store = new RelayStore({ now: () => time });
  store.put({ queueId: "queue-00000002", opaqueCiphertext: "YmxvYg==", expiresAtEpochMillis: 10_001 });
  time = 10_002;
  assert.deepEqual(store.list("queue-00000002"), []);
});

function listen(server) {
  return new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
}
