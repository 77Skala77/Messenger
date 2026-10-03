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
 * Anyone may create queues, so the whole store is bounded too, not only each queue, and expired
 * messages are swept at most once per `purgeIntervalMs` instead of on every request (audit SRV-01).
 */
export class RelayStore {
  constructor({
    now = () => Date.now(),
    maxTtlMs = 7 * 24 * 60 * 60 * 1000,
    maxPendingPerQueue = 1000,
    maxCiphertextChars = 64 * 1024,
    maxListMessages = 100,
    maxTotalMessages = 100_000,
    maxTotalCiphertextChars = 256 * 1024 * 1024,
    purgeIntervalMs = 1000,
  } = {}) {
    this.now = now;
    this.maxTtlMs = maxTtlMs;
    this.maxPendingPerQueue = maxPendingPerQueue;
    this.maxCiphertextChars = maxCiphertextChars;
    this.maxListMessages = maxListMessages;
    this.maxTotalMessages = maxTotalMessages;
    this.maxTotalCiphertextChars = maxTotalCiphertextChars;
    this.purgeIntervalMs = purgeIntervalMs;
    this.queues = new Map();
    this.totalMessages = 0;
    this.totalCiphertextChars = 0;
    this.lastPurgeAt = -Infinity;
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
    if (
      this.totalMessages >= this.maxTotalMessages ||
      this.totalCiphertextChars + input.opaqueCiphertext.length > this.maxTotalCiphertextChars
    ) {
      throw new RelayError(503, "relay_full");
    }
    const messageId = randomBytes(16).toString("base64url");
    const record = { messageId, opaqueCiphertext: input.opaqueCiphertext, expiresAtEpochMillis };
    queue.set(messageId, record);
    this.queues.set(queueId, queue);
    this.totalMessages += 1;
    this.totalCiphertextChars += record.opaqueCiphertext.length;
    return record;
  }

  /** The oldest pending messages, at most `maxListMessages`; the reader deletes them to see more. */
  list(queueId) {
    this.purgeExpired();
    const currentTime = this.now();
    const pending = [];
    for (const record of this.queues.get(queueId)?.values() ?? []) {
      if (pending.length === this.maxListMessages) break;
      // The sweep may not have run yet: what has expired is never handed out.
      if (record.expiresAtEpochMillis > currentTime) pending.push(record);
    }
    return pending;
  }

  /** Deletes only a message of this queue. */
  delete(queueId, messageId) {
    if (!MESSAGE_ID_PATTERN.test(messageId)) return false;
    const queue = this.queues.get(queueId);
    const record = queue?.get(messageId);
    if (record === undefined) return false;
    queue.delete(messageId);
    this.forget(record);
    if (queue.size === 0) this.queues.delete(queueId);
    return true;
  }

  purgeExpired() {
    const currentTime = this.now();
    if (currentTime - this.lastPurgeAt < this.purgeIntervalMs) return;
    this.lastPurgeAt = currentTime;
    for (const [queueId, queue] of this.queues) {
      for (const [id, record] of queue) {
        if (record.expiresAtEpochMillis <= currentTime) {
          queue.delete(id);
          this.forget(record);
        }
      }
      if (queue.size === 0) this.queues.delete(queueId);
    }
  }

  forget(record) {
    this.totalMessages -= 1;
    this.totalCiphertextChars -= record.opaqueCiphertext.length;
  }
}

/**
 * Token bucket per client address: a QA guard against trivial flooding, not production abuse
 * control. A full table drops only the client seen least recently, never the whole table, so a
 * burst of new source addresses cannot reset everyone's limit at once (audit M1).
 */
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
    if (bucket) {
      // A Map keeps insertion order: inserting again marks the client as the most recent one.
      this.buckets.delete(client);
    } else {
      if (this.buckets.size >= this.maxClients) this.buckets.delete(this.buckets.keys().next().value);
      bucket = { tokens: this.capacity, updatedAt: time };
    }
    this.buckets.set(client, bucket);
    bucket.tokens = Math.min(this.capacity, bucket.tokens + (time - bucket.updatedAt) * this.refillPerMs);
    bucket.updatedAt = time;
    if (bucket.tokens < 1) return false;
    bucket.tokens -= 1;
    return true;
  }
}
