import { randomUUID } from "node:crypto";

export class RelayStore {
  constructor({ now = () => Date.now(), maxTtlMs = 7 * 24 * 60 * 60 * 1000 } = {}) {
    this.now = now;
    this.maxTtlMs = maxTtlMs;
    this.messages = new Map();
  }

  put(input) {
    this.purgeExpired();
    const currentTime = this.now();
    const expiresAtEpochMillis = Number(input.expiresAtEpochMillis);
    if (
      typeof input.queueId !== "string" ||
      input.queueId.length < 8 ||
      typeof input.opaqueCiphertext !== "string" ||
      input.opaqueCiphertext.length === 0 ||
      !Number.isSafeInteger(expiresAtEpochMillis) ||
      expiresAtEpochMillis <= currentTime ||
      expiresAtEpochMillis > currentTime + this.maxTtlMs
    ) {
      throw new Error("Invalid encrypted message envelope");
    }
    const messageId = typeof input.messageId === "string" && input.messageId.length > 0
      ? input.messageId
      : randomUUID();
    const record = {
      messageId,
      queueId: input.queueId,
      opaqueCiphertext: input.opaqueCiphertext,
      expiresAtEpochMillis,
    };
    this.messages.set(messageId, record);
    return record;
  }

  list(queueId) {
    this.purgeExpired();
    return [...this.messages.values()].filter((record) => record.queueId === queueId);
  }

  delete(messageId) {
    return this.messages.delete(messageId);
  }

  purgeExpired() {
    const currentTime = this.now();
    for (const [id, record] of this.messages) {
      if (record.expiresAtEpochMillis <= currentTime) {
        this.messages.delete(id);
      }
    }
  }
}
