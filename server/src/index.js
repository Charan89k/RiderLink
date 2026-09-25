/**
 * RiderLink token server.
 *
 * Mints short-lived, room-scoped LiveKit access tokens so that the LiveKit API
 * secret never leaves this Worker. The Android app has no signing key at all --
 * it asks this endpoint for a token and connects with whatever it gets back.
 *
 * Secrets (set with `wrangler secret put <NAME>`):
 *   LIVEKIT_API_KEY
 *   LIVEKIT_API_SECRET
 * Plain vars (wrangler.toml):
 *   LIVEKIT_URL
 */

// Long enough to outlast a full day's ride: LiveKit needs a still-valid token to
// re-establish a dropped connection, and a rider losing the intercom mid-ride
// because their token expired is worse than the marginal risk of a longer TTL.
// Shorten this once the app refreshes its token on reconnect.
const TOKEN_TTL_SECONDS = 12 * 60 * 60; // 12 hours
const ROOM_CODE_PATTERN = /^[0-9]{4}$/;
const MAX_IDENTITY_LENGTH = 64;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (url.pathname === "/health") {
      return json({ ok: true });
    }
    if (url.pathname !== "/token") {
      return json({ error: "not_found" }, 404);
    }
    if (request.method !== "POST") {
      return json({ error: "method_not_allowed" }, 405);
    }

    let body;
    try {
      body = await request.json();
    } catch {
      return json({ error: "invalid_json" }, 400);
    }

    const room = String(body.room ?? "").trim();
    const identity = String(body.identity ?? "").trim();
    const name = String(body.name ?? identity).trim();

    if (!ROOM_CODE_PATTERN.test(room)) {
      return json({ error: "invalid_room", detail: "room must be 4 digits" }, 400);
    }
    if (!identity || identity.length > MAX_IDENTITY_LENGTH) {
      return json({ error: "invalid_identity" }, 400);
    }

    const missing = ["LIVEKIT_API_KEY", "LIVEKIT_API_SECRET", "LIVEKIT_URL"].filter((k) => !env[k]);
    if (missing.length) {
      console.error("missing configuration:", missing.join(", "));
      return json({ error: "server_misconfigured" }, 500);
    }

    const now = Math.floor(Date.now() / 1000);
    const exp = now + TOKEN_TTL_SECONDS;

    // Only the grants a rider actually needs. Deliberately no roomCreate,
    // roomAdmin, roomList or ingress/egress rights: a leaked rider token can
    // join exactly one room until it expires, and nothing else.
    const token = await signJwt(
      {
        iss: env.LIVEKIT_API_KEY,
        sub: identity,
        nbf: now,
        exp,
        name,
        video: {
          room,
          roomJoin: true,
          canPublish: true,
          canSubscribe: true,
          canPublishData: true,
        },
      },
      env.LIVEKIT_API_SECRET,
    );

    return json({ token, url: env.LIVEKIT_URL, expiresAt: exp });
  },
};

async function signJwt(payload, secret) {
  const header = { alg: "HS256", typ: "JWT" };
  const signingInput = `${base64url(JSON.stringify(header))}.${base64url(JSON.stringify(payload))}`;

  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(signingInput));

  return `${signingInput}.${base64url(new Uint8Array(signature))}`;
}

function base64url(input) {
  const bytes = typeof input === "string" ? new TextEncoder().encode(input) : input;
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function json(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}
