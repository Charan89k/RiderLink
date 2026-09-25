/**
 * Runs the Worker's request handler on a plain Node HTTP server.
 *
 * Lets you point a debug build at a LiveKit instance on your own machine
 * (`livekit-server --dev`) without deploying anything. Same handler as
 * production, so what you test here is the code that ships.
 *
 *   livekit-server --dev
 *   node server/dev-local.mjs
 *
 * The Android emulator reaches the host as 10.0.2.2, so set the token server
 * to http://10.0.2.2:8787 in RiderLink's settings.
 */
import { createServer } from "node:http";
import worker from "./src/index.js";

const PORT = Number(process.env.PORT ?? 8787);

const env = {
  LIVEKIT_API_KEY: process.env.LIVEKIT_API_KEY ?? "devkey",
  LIVEKIT_API_SECRET: process.env.LIVEKIT_API_SECRET ?? "secret",
  LIVEKIT_URL: process.env.LIVEKIT_URL ?? "ws://10.0.2.2:7880",
};

createServer(async (req, res) => {
  const chunks = [];
  for await (const chunk of req) chunks.push(chunk);

  const request = new Request(`http://localhost:${PORT}${req.url}`, {
    method: req.method,
    headers: req.headers,
    body: chunks.length ? Buffer.concat(chunks) : undefined,
  });

  const response = await worker.fetch(request, env);
  res.writeHead(response.status, Object.fromEntries(response.headers));
  res.end(Buffer.from(await response.arrayBuffer()));

  console.log(`${req.method} ${req.url} -> ${response.status}`);
}).listen(PORT, "0.0.0.0", () => {
  console.log(`token server on http://0.0.0.0:${PORT} -> ${env.LIVEKIT_URL}`);
});
