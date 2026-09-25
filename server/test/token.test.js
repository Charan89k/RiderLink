import { test } from "node:test";
import assert from "node:assert/strict";
import { createHmac } from "node:crypto";
import worker from "../src/index.js";

const ENV = {
  LIVEKIT_API_KEY: "APItestkey",
  LIVEKIT_API_SECRET: "test-secret-value",
  LIVEKIT_URL: "wss://example.livekit.cloud",
};

const tokenRequest = (body) =>
  new Request("https://worker.dev/token", {
    method: "POST",
    body: typeof body === "string" ? body : JSON.stringify(body),
  });

const decode = (segment) => JSON.parse(Buffer.from(segment, "base64url").toString("utf8"));

test("mints a room-scoped token whose signature verifies", async () => {
  const response = await worker.fetch(tokenRequest({ room: "4821", identity: "charan" }), ENV);
  assert.equal(response.status, 200);

  const { token, url, expiresAt } = await response.json();
  assert.equal(url, ENV.LIVEKIT_URL);

  const [headerB64, payloadB64, signatureB64] = token.split(".");
  assert.deepEqual(decode(headerB64), { alg: "HS256", typ: "JWT" });

  // Independently re-sign with Node's crypto and compare.
  const expected = createHmac("sha256", ENV.LIVEKIT_API_SECRET)
    .update(`${headerB64}.${payloadB64}`)
    .digest("base64url");
  assert.equal(signatureB64, expected, "signature must verify against the API secret");

  const payload = decode(payloadB64);
  assert.equal(payload.iss, ENV.LIVEKIT_API_KEY);
  assert.equal(payload.sub, "charan");
  assert.equal(payload.name, "charan");
  assert.equal(payload.video.room, "4821");
  assert.equal(payload.video.roomJoin, true);
  assert.equal(payload.exp, expiresAt);

  // Scoped down: none of the administrative grants.
  for (const grant of ["roomCreate", "roomAdmin", "roomList", "ingressAdmin"]) {
    assert.ok(!(grant in payload.video), `token must not carry ${grant}`);
  }
});

test("token outlasts a long ride but is not open-ended", async () => {
  const response = await worker.fetch(tokenRequest({ room: "0001", identity: "r" }), ENV);
  const { expiresAt } = await response.json();
  const ttl = expiresAt - Math.floor(Date.now() / 1000);
  assert.ok(ttl >= 8 * 3600, `ttl of ${ttl}s is too short to survive a day's ride`);
  assert.ok(ttl <= 12 * 3600, `ttl was ${ttl}`);
});

test("rejects room codes that are not 4 digits", async () => {
  for (const room of ["", "123", "12345", "abcd", "12a4", "  ", "../admin"]) {
    const response = await worker.fetch(tokenRequest({ room, identity: "charan" }), ENV);
    assert.equal(response.status, 400, `expected 400 for room ${JSON.stringify(room)}`);
  }
});

test("rejects a missing or oversized identity", async () => {
  for (const identity of ["", " ", "x".repeat(65)]) {
    const response = await worker.fetch(tokenRequest({ room: "4821", identity }), ENV);
    assert.equal(response.status, 400);
  }
});

test("rejects malformed json and wrong methods", async () => {
  assert.equal((await worker.fetch(tokenRequest("not json"), ENV)).status, 400);

  const getRequest = new Request("https://worker.dev/token", { method: "GET" });
  assert.equal((await worker.fetch(getRequest, ENV)).status, 405);

  const unknown = new Request("https://worker.dev/whatever", { method: "POST" });
  assert.equal((await worker.fetch(unknown, ENV)).status, 404);
});

test("refuses to mint anything when the secret is not configured", async () => {
  const response = await worker.fetch(tokenRequest({ room: "4821", identity: "charan" }), {
    ...ENV,
    LIVEKIT_API_SECRET: "",
  });
  assert.equal(response.status, 500);
  const body = await response.json();
  assert.equal(body.error, "server_misconfigured");
  assert.ok(!JSON.stringify(body).includes("test-secret"), "must not echo secrets");
});
