import { createHash, randomBytes, timingSafeEqual } from "node:crypto";

/** Queue IDs are self-certifying (DEC-024): "q3-" + base64url(SHA-256(domain || readSecret)). */
export const QUEUE_ID_PATTERN = /^q3-[A-Za-z0-9_-]{43}$/;
const QUEUE_DOMAIN = Buffer.from("smp-relay-queue-v1\0", "utf8");
const READ_SECRET_BYTES = 32;
const MESSAGE_ID_PATTERN = /^[A-Za-z0-9_-]{22}$/;

export function queueIdForReadSecret(readSecret) {
  const digest = createHash("sha256").update(QUEUE_DOMAIN).update(readSecret).digest();
  return `q3-${digest.toString("base64url")}`;
}

/** True only if `bearer` is the base64url read secret whose hash is `queueId`. Constant time. */
export function readSecretMatches(queueId, bearer) {
  if (typeof bearer !== "string" || !/^[A-Za-z0-9_-]{43}$/.test(bearer)) return false;
  const secret = Buffer.from(bearer, "base64url");
  if (secret.length !== READ_SECRET_BYTES) return false;
  const expected = Buffer.from(queueIdForReadSecret(secret), "utf8");
  const actual = Buffer.from(queueId, "utf8");
  return expected.length === actual.length && timingSafeEqual(expected, actual);
}

export class RelayError extends Error {
  constructor(status, code) {
    super(code);
    this.status = status;
    this.code = code;
  }
}

/** True only for standard base64 in its one canonical spelling (padding, zero tail bits). */
export function isCanonicalBase64(value) {
  return typeof value === "string" && value.length > 0 && Buffer.from(value, "base64").toString("base64") === value;
}

/**
 * In-memory store of opaque envelopes. The relay is untrusted: it never sees plaintext, keeps no
 * per-queue secrets, and assigns message IDs itself so no client can overwrite another's record.
 * Phase 5 (DEC-026): it stores only canonical base64, so a sender cannot plant an item that the
 * reader fails to parse, and a read returns at most `maxListMessages` of the oldest messages.
 */
export class RelayStore {
  constructor({
    now = () => Date.now(),
    maxTtlMs = 7 * 24 * 60 * 60 * 1000,
    maxPendingPerQueue = 1000,
    maxCiphertextChars = 64 * 1024,
    maxListMessages = 100,
  } = {}) {
    this.now = now;
    this.maxTtlMs = maxTtlMs;
    this.maxPendingPerQueue = maxPendingPerQueue;
    this.maxCiphertextChars = maxCiphertextChars;
    this.maxListMessages = maxListMessages;
    this.queues = new Map();
  }

  put(queueId, input) {
    this.purgeExpired();
    const currentTime = this.now();
    const expiresAtEpochMillis = Number(input?.expiresAtEpochMillis);
    if (
      !QUEUE_ID_PATTERN.test(queueId) ||
      input === null ||
      typeof input !== "object" ||
      "messageId" in input ||
      typeof input.opaqueCiphertext !== "string" ||
      input.opaqueCiphertext.length > this.maxCiphertextChars ||
      !isCanonicalBase64(input.opaqueCiphertext) ||
      !Number.isSafeInteger(expiresAtEpochMillis) ||
      expiresAtEpochMillis <= currentTime ||
      expiresAtEpochMillis > currentTime + this.maxTtlMs
    ) {
      throw new RelayError(400, "invalid_request");
    }
    const queue = this.queues.get(queueId) ?? new Map();
    if (queue.size >= this.maxPendingPerQueue) throw new RelayError(429, "queue_full");
    const messageId = randomBytes(16).toString("base64url");
    const record = { messageId, opaqueCiphertext: input.opaqueCiphertext, expiresAtEpochMillis };
    queue.set(messageId, record);
    this.queues.set(queueId, queue);
    return record;
  }

  /** The oldest pending messages, at most `maxListMessages`; the reader deletes them to see more. */
  list(queueId) {
    this.purgeExpired();
    return [...(this.queues.get(queueId)?.values() ?? [])].slice(0, this.maxListMessages);
  }

  /** Deletes only a message of this queue. */
  delete(queueId, messageId) {
    if (!MESSAGE_ID_PATTERN.test(messageId)) return false;
    const queue = this.queues.get(queueId);
    const deleted = queue?.delete(messageId) ?? false;
    if (queue?.size === 0) this.queues.delete(queueId);
    return deleted;
  }

  purgeExpired() {
    const currentTime = this.now();
    for (const [queueId, queue] of this.queues) {
      for (const [id, record] of queue) {
        if (record.expiresAtEpochMillis <= currentTime) queue.delete(id);
      }
      if (queue.size === 0) this.queues.delete(queueId);
    }
  }
}

/** Token bucket per client address: a QA guard against trivial flooding, not production abuse control. */
export class RateLimiter {
  constructor({ now = () => Date.now(), capacity = 20, refillPerMinute = 60, maxClients = 10_000 } = {}) {
    this.now = now;
    this.capacity = capacity;
    this.refillPerMs = refillPerMinute / 60_000;
    this.maxClients = maxClients;
    this.buckets = new Map();
  }

  allow(client) {
    const time = this.now();
    let bucket = this.buckets.get(client);
    if (!bucket) {
      if (this.buckets.size >= this.maxClients) this.buckets.clear();
      bucket = { tokens: this.capacity, updatedAt: time };
      this.buckets.set(client, bucket);
    }
    bucket.tokens = Math.min(this.capacity, bucket.tokens + (time - bucket.updatedAt) * this.refillPerMs);
    bucket.updatedAt = time;
    if (bucket.tokens < 1) return false;
    bucket.tokens -= 1;
    return true;
  }
}
