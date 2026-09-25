# RiderLink token server

A Cloudflare Worker that mints short-lived, room-scoped LiveKit access tokens.

## Why this exists

LiveKit access tokens are JWTs signed with your LiveKit **API secret**. Signing
them inside the Android app means shipping that secret in every APK, where
anyone can read it out and mint admin tokens for your LiveKit project. This
Worker keeps the secret server-side; the app only ever receives a finished
token that is good for one room, for one hour.

## Deploy

```bash
cd server
npm install

# One-time: authenticate wrangler with your Cloudflare account
npx wrangler login

# Store the credentials as encrypted secrets (never in wrangler.toml)
npx wrangler secret put LIVEKIT_API_KEY
npx wrangler secret put LIVEKIT_API_SECRET

# Set LIVEKIT_URL in wrangler.toml to your project's wss:// address, then:
npx wrangler deploy
```

Deploy prints the Worker URL. Put that in
`app/src/main/java/com/example/riderlink/Config.kt` as `DEFAULT_TOKEN_SERVER_URL`.

## Local development

```bash
cat > .dev.vars <<'VARS'
LIVEKIT_API_KEY=your-key
LIVEKIT_API_SECRET=your-secret
VARS

npm run dev
```

`.dev.vars` is gitignored.

## API

### `POST /token`

```json
{ "room": "4821", "identity": "charan", "name": "Charan" }
```

```json
{ "token": "eyJhbGciOi...", "url": "wss://your-project.livekit.cloud", "expiresAt": 1758800000 }
```

`room` must be exactly 4 digits; `identity` must be 1-64 characters. The token
carries `roomJoin`, `canPublish`, `canSubscribe` and `canPublishData` for that
one room, and no administrative grants.

### `GET /health`

```json
{ "ok": true }
```

## Tests

```bash
npm test
```

The suite re-signs each minted token with Node's own HMAC implementation and
checks that the signatures match, that the TTL is within an hour, that
administrative grants are absent, that malformed input is rejected, and that a
missing secret produces a 500 rather than an unsigned token.

## Not done yet

- **Rate limiting.** Anyone who knows the URL can request tokens for any 4-digit
  code. Add a [Cloudflare rate limiting binding](https://developers.cloudflare.com/workers/runtime-apis/bindings/rate-limit/)
  or Turnstile before this is public.
- **Caller identity.** Tokens are handed to anonymous callers. Wiring in Firebase
  Auth (already a dependency of the app) and verifying the ID token here would
  tie each token to a real account.
