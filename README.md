# RiderLink 🏍️💨

RiderLink is a real-time, low-latency group voice intercom Android application optimized for motorcyclists using standard, point-to-point Bluetooth helmet headsets. 

Instead of buying expensive, proprietary mesh communication hardware (like Sena or Cardo), RiderLink utilizes **LiveKit (WebRTC)** over cellular networks and **Firebase Firestore** to bridge standard Bluetooth headsets into a dynamic voice room. The app is engineered to run seamlessly inside a rider's pocket with advanced background processing and hands-free physical button shortcuts.

---

## ✨ Features

* 🔊 **Real-Time Group Intercom:** Powered by LiveKit WebRTC for ultra-low latency multicast voice rooms across long distances.
* 🎧 **Background Music Mixing:** Integrates with your favorite music apps (Spotify, YouTube Music) using advanced Android Audio Focus (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`).
* 📉 **Intelligent Auto-Ducking:** Automatically dims background music volume down to 20% the exact millisecond a fellow rider speaks, restoring it seamlessly afterward.
* 🖲️ **Hands-Free Helmet Gestures:** Intercepts media hardware keys using a custom background `MediaSession` decoder:
    * **Triple-Click Play/Pause:** Mutes/Unmutes your local microphone (with Text-to-Speech audio confirmation).
    * **Double Long-Press Vol Up:** Starts a private 1-on-1 chat with a specific rider, isolating their stream.
    * **Double Long-Press Vol Down:** Seamlessly returns to the main group mesh intercom.
* 🎨 **Ultra-Minimal Waveform UI:** A sleek, pitch-black Jetpack Compose dashboard (#0C0F12) featuring a dynamic breathing soundwave that shifts color based on system state and speaker identity. 
* 🧤 **Glove-Friendly Interaction:** Bottom action dock features massive, translucent tactile targets enforcing strict 88dp x 88dp boundaries.

---

## 🛠️ Architecture & Tech Stack

* **Language:** Kotlin (built on top of asynchronous Coroutines & Flows)
* **UI Framework:** Jetpack Compose & Material 3
* **Real-Time Communications:** LiveKit Android SDK (WebRTC framework)
* **Database & Orchestration:** Firebase Core, Auth, and Cloud Firestore (handles 4-digit room code lookups)
* **Background Lifecycle:** Persistent Android Foreground Service declared with `FOREGROUND_SERVICE_MICROPHONE` (Target API 34+ compliance) paired with CPU `WakeLocks`.
* **Token Framework:** Embedded local HMAC-SHA256 JWT generation framework for rapid standalone staging and offline simulation.

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug (or newer)
- Android SDK 34 (Compile & Target)
- Min SDK 26 (Android 8.0+)

### Setup Instructions

1. **Clone the Repository:**
   ```bash
   git clone [https://github.com/yourusername/RiderLink.git](https://github.com/yourusername/RiderLink.git)
   cd RiderLink
