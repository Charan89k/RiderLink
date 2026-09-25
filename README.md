# RiderLink 🏍️

Group voice intercom for motorcyclists, built for the plain Bluetooth headset
already in your helmet.

Mesh intercom hardware from Sena or Cardo costs more than most riders want to
spend and only talks to its own brand. RiderLink carries the same conversation
over LiveKit/WebRTC on mobile data instead, so a group can ride together on
whatever headsets they happen to own, at whatever distance mobile coverage
reaches.

<p align="center">
  <img src="docs/screenshots/lobby.png" width="24%" alt="Lobby: create or join a ride" />
  <img src="docs/screenshots/ride-dashboard.png" width="24%" alt="Ride dashboard with rider roster and microphone control" />
  <img src="docs/screenshots/private-channel.png" width="24%" alt="Private channel with one rider" />
  <img src="docs/screenshots/voice-boost.png" width="24%" alt="Voice Boost settings" />
</p>

---

## Features

* **Group voice intercom** over LiveKit/WebRTC. Riders share a 4-digit ride code.
* **Standard Bluetooth helmet headsets.** Audio is routed to the headset as a
  communication device; the dashboard says plainly when it is not, because a
  silent intercom and a misrouted one look identical from the saddle.
* **Runs in your pocket.** A foreground microphone service keeps the ride alive
  with the screen off.
* **Music keeps playing.** RiderLink takes ducking audio focus, so your music
  drops in volume while someone talks and comes back afterwards. It can pause
  instead, if you prefer.
* **Voice Boost.** Lifts incoming rider voice for helmet speakers that cannot
  cut through wind and engine noise, with a limiter so it gets louder instead of
  distorted. Applied per voice track, so it never touches your music.
* **Helmet button gestures — channel switching, not push-to-talk.** Triple-press
  play/pause to mute, double long-press volume up to open your private channel,
  volume down to return to the group. Press once and talk normally; nothing is
  held down. Each one is confirmed out loud, so you never look at the phone.
* **Private rider channels.** Pick the rider your helmet gesture will call, then
  switch to them and back without touching the phone again.
* **Live rider roster.** Who is connected, who is speaking, who is muted.
* **Reconnection that survives tunnels.** Exponential backoff with jitter,
  waiting on the radio rather than retrying into a dead one, and bounded so a
  lost ride cannot hold your CPU awake indefinitely.
* **Glove-friendly.** Every primary control is at least 88dp; the microphone
  button is 132dp.

---

## How talking works

There is no push-to-talk. The microphone stays open while you are unmuted and
WebRTC's voice activity detection decides when you are actually speaking, which
is what drives the speaking indicator and music ducking. Holding a button down
to talk is not something you can do at speed in gloves, so the helmet gestures
change *which channel* your voice goes to instead:

```text
                    speak normally
                          │
                         VAD
                          │
              ┌───────────┴───────────┐
           GROUP                   PRIVATE
        all riders             one selected rider
              └───────────┬───────────┘
                          │
                    LiveKit / WebRTC
                          │
                    incoming audio
                          │
                     Voice Boost
                     gain + limiter
                          │
                  Bluetooth helmet
```

**Choosing who the gesture calls.** Long-press a rider in the roster to mark
them as your target — the row shows `TARGET` and the panel says "Volume up calls
Arjun". Tapping a rider opens the channel immediately and marks them too. The
target is remembered between rides, so with the same group you set it once.

| Gesture | What happens |
|---|---|
| Double long-press volume up | Opens your private channel. Press again to move to the next rider. |
| Double long-press volume down | Back to the group. Your target is kept. |
| Triple-press play/pause | Mute or unmute your microphone. |

Most Bluetooth helmet headsets send `MEDIA_NEXT` and `MEDIA_PREVIOUS` for a long
press of volume up and down, which is what RiderLink listens for.

## Voice Boost

Helmet speakers are small and a moving motorcycle is loud. Voice Boost lifts
incoming rider voice, and does it without turning speech into a buzz:

```text
remote voice track
        │
   read-only tap  ──▶  peak level of every frame
        │                        │
        │                 limiter decision
        │              fast attack, slow release
        ▼                        │
 RemoteAudioTrack.setVolume(safe gain)
        │
   WebRTC mixer ──▶ Bluetooth ──▶ helmet
```

Two properties fall out of putting the gain *inside* WebRTC, per track:

* **It cannot amplify your phone.** Music, navigation and everything else keep
  their own levels, and the existing ducking behaviour is unchanged.
* **It cannot break privacy.** A track you are not subscribed to produces no
  audio to boost, so private mode works exactly as before.

| Level | Gain |
|---|---|
| Normal | 1.00x — a true bypass, nothing is processed |
| Boost | 1.25x |
| High | 1.50x |
| Maximum | 1.75x |

The limiter watches the real peak of each frame and only pulls gain back as far
as it must to stay under about −1 dBFS, then lets it climb again. Gain drops in
milliseconds and recovers over about half a second, so one shouted word does not
leave the next sentence quiet. It is an envelope follower, not a look-ahead
brickwall: a transient inside a single frame can still get through. It prevents
the sustained clipping that actually makes speech unintelligible.

Above roughly 1.75x the limiter ends up working through most normal speech,
which costs clarity for very little extra loudness — so that is where the scale
stops. **Find your own level with your own helmet**: wind noise and speaker
quality vary far more between bikes than these numbers do.

## Architecture

```text
┌──────────────────────────── Android client ───────────────────────────┐
│                                                                        │
│  Jetpack Compose                                                       │
│    Lobby · Ride dashboard · Settings                                   │
│         │                                                              │
│  MainViewModel  ── binds ──┐                                           │
│         │                  │                                           │
│  domain/model              │     IntercomService (foreground)          │
│    ConnectionStatus        └───▶   owns the ride, fetches its own      │
│    Rider / RiderState              tokens, supervises reconnection,    │
│    AudioRoute                      MediaSession, TTS, wakelock         │
│    ReconnectPolicy                        │                            │
│                                           ├── LiveKitIntercomClient    │
│                                           ├── BluetoothAudioRouter     │
│                                           └── NetworkMonitor           │
└────────────────────────────────────────────┬───────────────────────────┘
                                             │
                  ┌──────────────────────────┼──────────────────────────┐
                  ▼                          ▼                          ▼
        Token server (Worker)         LiveKit Cloud              Firestore
        mints room-scoped,            WebRTC voice room          4-digit ride
        short-lived tokens                                       code registry
```

Three deliberate boundaries:

**The app holds no LiveKit credentials.** Signing an access token needs the
LiveKit API secret, and anything compiled into an APK is readable by anyone
holding the APK. A Cloudflare Worker signs tokens instead and hands back tokens
scoped to one room. See [`server/README.md`](server/README.md).

**The service owns the ride, not the ViewModel.** The service outlives the
Activity, so it is the only component that can re-authenticate and rebuild a
session that drops halfway through a ride.

**Connection state is a domain type, not LiveKit's.** `ConnectionStatus` is
separate from `Room.State` so the dashboard cannot report CONNECTED because a
room object happens to exist.

---

## Getting started

### Requirements

| | |
|---|---|
| Android | 8.0 (API 26) or newer |
| Build JDK | 21+ |
| Android SDK | Platform 36, Build-Tools 36.1.0 |
| Accounts | LiveKit Cloud (or self-hosted), Firebase, Cloudflare |

### 1. Deploy the token server first

The app cannot connect without one — it has no credentials of its own.

```bash
cd server
npm install
npx wrangler login
npx wrangler secret put LIVEKIT_API_KEY
npx wrangler secret put LIVEKIT_API_SECRET
# set LIVEKIT_URL in wrangler.toml to your wss:// address
npx wrangler deploy
```

Put the printed URL into `DEFAULT_TOKEN_SERVER_URL` in
`app/src/main/java/com/example/riderlink/Config.kt`, or set it per-device under
Settings → Token server.

### 2. Firebase

Drop your own `app/google-services.json` in place, then publish the rules:

```bash
firebase deploy --only firestore:rules
```

Firestore only records that a 4-digit code is in use. It holds no credentials,
and an unreachable directory does not ground a ride: the token server is what
actually controls access.

### 3. Build

```bash
./gradlew assembleDebug                     # debug APK
./gradlew testDebugUnitTest                 # unit tests
./gradlew assembleRelease                   # signed release APK
```

### 4. Release signing

Generate a keystore outside the repository and point a gitignored
`keystore.properties` at it:

```bash
keytool -genkeypair -v \
  -keystore ~/.riderlink/riderlink-release.jks \
  -alias riderlink -keyalg RSA -keysize 4096 -validity 10000

cat > keystore.properties <<EOF
storeFile=$HOME/.riderlink/riderlink-release.jks
storePassword=<your password>
keyAlias=riderlink
keyPassword=<your password>
EOF
```

CI can supply `RIDERLINK_STORE_FILE`, `RIDERLINK_STORE_PASSWORD`,
`RIDERLINK_KEY_ALIAS` and `RIDERLINK_KEY_PASSWORD` instead. With neither
present the release build is left unsigned rather than silently falling back to
the debug key.

**Never commit the keystore or its passwords.** Both are gitignored.

### 5. Install

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

Install on two phones to try a real two-rider ride.

---

## Local development without deploying anything

Run LiveKit and the token server on your own machine:

```bash
livekit-server --dev          # devkey / secret, ws://localhost:7880
node server/dev-local.mjs     # same Worker handler, on :8787
```

Then set Settings → Token server to `http://10.0.2.2:8787` on an emulator, or
your machine's LAN address on a physical device. Debug builds permit cleartext
to those hosts only; release builds stay HTTPS/WSS-only.

---

## Permissions

| Permission | Why |
|---|---|
| `RECORD_AUDIO` | Carrying your voice. |
| `BLUETOOTH_CONNECT` | Finding and routing to the helmet headset. |
| `INTERNET`, `ACCESS_NETWORK_STATE` | The voice room, and knowing when the radio drops. |
| `MODIFY_AUDIO_SETTINGS` | Audio mode, routing and music ducking. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE` | Staying live with the screen off. |
| `WAKE_LOCK` | Keeping the CPU up for audio during doze. |
| `POST_NOTIFICATIONS` | The ongoing ride notification. Optional; declining does not block the intercom. |

Notification access is requested separately from Settings. It is what lets
RiderLink see your headset's media keys and read the current track, and the
helmet gestures do not work without it.

The LiveKit SDK also declares `CAMERA` and `FOREGROUND_SERVICE_MEDIA_PROJECTION`
because it supports video. Both are stripped from the merged manifest — a
motorcycle intercom has no business asking for the camera.

---

## Known limitations

* **R8 is off for release builds.** LiveKit and Firebase both resolve classes
  reflectively, and a shrunk build that has not been ridden with is a poor
  trade. Turning it on needs a tested keep-rule set.
* **The token server has no rate limiting or caller identity.** Anyone who knows
  the URL can request a token for any 4-digit code. Adding Firebase Auth
  verification and a rate-limit binding is the next step; see
  [`server/README.md`](server/README.md).
* **Ride codes are 4 digits and never expire.** That is 9,000 codes with no
  cleanup. Fine for friends; not enough for strangers.
* **Tokens last 12 hours.** LiveKit needs a valid token to re-establish a
  dropped link, and the app does not yet refresh on reconnect, so the TTL has to
  outlast a full day's ride.
* **Voice Boost gains are unverified on real helmet hardware.** The limiter's
  behaviour is unit tested and was measured end-to-end against a live LiveKit
  server, but whether 1.75x is the right ceiling for a given helmet is a
  question only a ride can answer.
* **Voice Boost is global, not per-rider.** Everyone incoming gets the same
  treatment. Per-rider gain would be a natural next step.
* **No PTT, by design.** The intercom is open while unmuted and voice activity
  detection decides when you are speaking.

---

## Licence

No licence has been chosen yet, so default copyright applies.
