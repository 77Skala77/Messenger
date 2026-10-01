import assert from "node:assert/strict";
import { randomBytes } from "node:crypto";
import { test } from "node:test";
import { RateLimiter, RelayStore, queueIdForReadSecret } from "../src/relay-store.mjs";
import { createRelayServer } from "../src/server.mjs";

function newQueue() {
  const secret = randomBytes(32);
  return { id: queueIdForReadSecret(secret), bearer: secret.toString("base64url") };
}

async function withRelay(options, body) {
  const server = createRelayServer(options);
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  try {
    await body(`http://127.0.0.1:${server.address().port}`);
  } finally {
    server.close();
  }
}

function send(base, queueId, payload) {
  return fetch(`${base}/v1/queues/${queueId}/messages`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(payload),
  });
}

function read(base, queue, bearer = queue.bearer) {
  return fetch(`${base}/v1/queues/${queue.id}/messages`, {
    headers: bearer === null ? {} : { authorization: `Bearer ${bearer}` },
  });
}

function remove(base, queue, messageId, bearer = queue.bearer) {
  return fetch(`${base}/v1/queues/${queue.id}/messages/${messageId}`, {
    method: "DELETE",
    headers: { authorization: `Bearer ${bearer}` },
  });
}

const envelope = () => ({ opaqueCiphertext: "bm90LXBsYWludGV4dA==", expiresAtEpochMillis: Date.now() + 60_000 });

test("queue IDs match the app's derivation (shared vector with RelayQueueCredentialsTest)", () => {
  const secret = Buffer.from([...Array(32).keys()]);
  assert.equal(queueIdForReadSecret(secret), "q3--3ndGxCkLNwXdS9yA7nYaH2uIosiRXqmYXeHybuC5Po");
});

test("reports health without requiring message content", async () => {
  await withRelay({}, async (base) => {
    const response = await fetch(`${base}/health`);
    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), { status: "ok" });
  });
});

test("anyone may send; only the read secret reads and deletes; the relay assigns message IDs", async () => {
  await withRelay({}, async (base) => {
    const queue = newQueue();
    const posted = await send(base, queue.id, envelope());
    assert.equal(posted.status, 201);
    const { messageId } = await posted.json();
    assert.match(messageId, /^[A-Za-z0-9_-]{22}$/);

    const listed = await (await read(base, queue)).json();
    assert.equal(listed.length, 1);
    assert.equal(listed[0].messageId, messageId);
    assert.equal(listed[0].opaqueCiphertext, envelope().opaqueCiphertext);

    assert.equal((await remove(base, queue, messageId)).status, 204);
    assert.deepEqual(await (await read(base, queue)).json(), []);
  });
});

test("a missing, malformed or other queue's secret is forbidden for reading and deleting", async () => {
  await withRelay({}, async (base) => {
    const queue = newQueue();
    const other = newQueue();
    const { messageId } = await (await send(base, queue.id, envelope())).json();

    assert.equal((await read(base, queue, null)).status, 403);
    assert.equal((await read(base, queue, "not-a-secret")).status, 403);
    assert.equal((await read(base, queue, other.bearer)).status, 403);
    assert.equal((await remove(base, queue, messageId, other.bearer)).status, 403);
    // The owner of another queue cannot delete this message through their own queue either.
    assert.equal((await remove(base, other, messageId)).status, 204);
    assert.equal((await (await read(base, queue)).json()).length, 1);
  });
});

test("a client-supplied message ID is refused, so no record can be overwritten", async () => {
  await withRelay({}, async (base) => {
    const queue = newQueue();
    const response = await send(base, queue.id, { ...envelope(), messageId: "chosen-by-client" });
    assert.equal(response.status, 400);
    assert.deepEqual(await (await read(base, queue)).json(), []);
  });
});

test("old routes and non-self-certifying queue IDs are refused", async () => {
  await withRelay({}, async (base) => {
    const old = await fetch(`${base}/v1/messages`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ queueId: "v2-00000000", ...envelope() }),
    });
    assert.equal(old.status, 404);
    assert.equal((await send(base, "v2-00000000-0000-0000-0000-000000000000", envelope())).status, 400);
  });
});

test("oversized bodies, full queues and floods are refused", async () => {
  const store = new RelayStore({ maxPendingPerQueue: 2 });
  const sendLimiter = new RateLimiter({ capacity: 5, refillPerMinute: 0 });
  await withRelay({ store, sendLimiter }, async (base) => {
    const queue = newQueue();
    const big = await send(base, queue.id, { ...envelope(), opaqueCiphertext: "A".repeat(100 * 1024) });
    assert.equal(big.status, 413);
    assert.equal((await send(base, queue.id, envelope())).status, 201);
    assert.equal((await send(base, queue.id, envelope())).status, 201);
    assert.equal((await send(base, queue.id, envelope())).status, 429);
    const other = newQueue();
    assert.equal((await send(base, other.id, envelope())).status, 201);
    assert.equal((await send(base, other.id, envelope())).status, 429); // sixth send from this address
  });
});

test("only canonical base64 is stored, so no sender can plant an item the reader cannot parse", async () => {
  await withRelay({}, async (base) => {
    const queue = newQueue();
    for (const opaqueCiphertext of ["", "not base64!", "YmxvYg", "YmxvYh==", " YmxvYg==", "YmxvYg==\n", "Ym-xYg==", "Ym_xYg=="]) {
      const response = await send(base, queue.id, { ...envelope(), opaqueCiphertext });
      assert.equal(response.status, 400, JSON.stringify(opaqueCiphertext));
    }
    assert.equal((await send(base, queue.id, { ...envelope(), opaqueCiphertext: "YmxvYg==" })).status, 201);
    assert.equal((await (await read(base, queue)).json()).length, 1);
  });
});

test("a read returns the oldest page only; deleting it reveals the rest", async () => {
  const store = new RelayStore({ maxListMessages: 3 });
  const sendLimiter = new RateLimiter({ capacity: 100, refillPerMinute: 0 });
  await withRelay({ store, sendLimiter }, async (base) => {
    const queue = newQueue();
    const sent = [];
    for (let index = 0; index < 5; index++) sent.push((await (await send(base, queue.id, envelope())).json()).messageId);
    const first = await (await read(base, queue)).json();
    assert.deepEqual(first.map((item) => item.messageId), sent.slice(0, 3));
    for (const item of first) assert.equal((await remove(base, queue, item.messageId)).status, 204);
    assert.deepEqual((await (await read(base, queue)).json()).map((item) => item.messageId), sent.slice(3));
  });
});

test("hostile requests get a 4xx and the relay keeps serving", async () => {
  const permissive = () => new RateLimiter({ capacity: 10_000, refillPerMinute: 0 });
  await withRelay({ sendLimiter: permissive(), readLimiter: permissive() }, async (base) => {
    const queue = newQueue();
    const raw = (path, init = {}) => fetch(`${base}${path}`, init);
    const post = (body) => raw(`/v1/queues/${queue.id}/messages`, { method: "POST", headers: { "content-type": "application/json" }, body });
    const valid = envelope();
    const attempts = [
      post(""), post("{"), post("null"), post("[]"), post("42"), post('"text"'), post("{}"),
      post(Buffer.from([0xff, 0xfe, 0x00, 0x80])),
      post(JSON.stringify({ ...valid, opaqueCiphertext: 7 })),
      post(JSON.stringify({ ...valid, opaqueCiphertext: { nested: true } })),
      post(JSON.stringify({ opaqueCiphertext: valid.opaqueCiphertext })),
      post(JSON.stringify({ ...valid, expiresAtEpochMillis: "soon" })),
      post(JSON.stringify({ ...valid, expiresAtEpochMillis: -1 })),
      post(JSON.stringify({ ...valid, expiresAtEpochMillis: 1e300 })),
      post(JSON.stringify({ ...valid, expiresAtEpochMillis: Date.now() + 30 * 24 * 60 * 60 * 1000 })),
      post(JSON.stringify({ ...valid, expiresAtEpochMillis: 1.5 })),
      raw(`/v1/queues/${queue.id.toUpperCase()}/messages`, { method: "POST", body: JSON.stringify(valid) }),
      raw(`/v1/queues/${encodeURIComponent(queue.id).replace("q", "%71")}/messages`, { method: "POST", body: JSON.stringify(valid) }),
      raw("/v1/queues//messages", { method: "POST", body: JSON.stringify(valid) }),
      raw(`/v1/queues/${queue.id}/messages/x/y`),
      raw(`/v1/queues/${queue.id}/messages`, { method: "PUT", body: JSON.stringify(valid) }),
      raw(`/v1/queues/${queue.id}/messages`, { method: "DELETE", headers: { authorization: `Bearer ${queue.bearer}` } }),
      raw(`/v1/queues/${queue.id}/messages/AAAAAAAAAAAAAAAAAAAAAA`, { headers: { authorization: `Bearer ${queue.bearer}` } }),
      raw(`/v1/queues/${queue.id}/messages`, { headers: { authorization: "Bearer" } }),
      raw(`/v1/queues/${queue.id}/messages`, { headers: { authorization: `Bearer ${"A".repeat(10_000)}` } }),
      raw(`/v1/queues/${queue.id}/messages`, { headers: { authorization: `Basic ${queue.bearer}` } }),
      raw(`/v1/queues/${queue.id}/messages`, { headers: { authorization: `Bearer ${queue.bearer}=` } }),
      raw("/v2/anything"),
    ];
    for (const [index, response] of (await Promise.all(attempts)).entries()) {
      assert.ok(response.status >= 400 && response.status < 500, `attempt ${index} got ${response.status}`);
    }
    assert.equal((await raw("/health")).status, 200);
    assert.equal((await send(base, queue.id, valid)).status, 201);
    assert.equal((await (await read(base, queue)).json()).length, 1);
  });
});

test("expires pending encrypted messages by TTL and caps the TTL", () => {
  let time = 10_000;
  const store = new RelayStore({ now: () => time, maxTtlMs: 1_000 });
  const queue = newQueue();
  store.put(queue.id, { opaqueCiphertext: "YmxvYg==", expiresAtEpochMillis: 10_001 });
  assert.throws(() => store.put(queue.id, { opaqueCiphertext: "YmxvYg==", expiresAtEpochMillis: 20_000 }));
  time = 10_002;
  assert.deepEqual(store.list(queue.id), []);
});
