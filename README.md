# gdx-webrtc

A cross-platform WebRTC library for libGDX (and beyond). Provides a simple, java-websockets-style API for peer-to-peer connections with reliable and unreliable data channels. All signaling complexity (SDP offers/answers, ICE candidates) is hidden behind a connect-and-go interface.

## Supported Platforms

| Platform | Module | Status |
|----------|--------|--------|
| Desktop (LWJGL3) | `common` | Available |
| Web (TeaVM) | `teavm` | Available |
| Android | — | Planned |
| iOS (RoboVM) | — | Planned |

## Setup

### 1. Add the Repository

In your project's root `build.gradle`:
```groovy
allprojects {
    repositories {
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

### 2. Add Dependencies

**Your core module** (platform-agnostic game code):
```groovy
dependencies {
    implementation "com.github.satori87.gdx-webrtc:core:0.1.0"
}
```

**Your desktop/lwjgl3 module:**
```groovy
dependencies {
    implementation "com.github.satori87.gdx-webrtc:common:0.1.0"
}
```

**Your teavm module:**
```groovy
dependencies {
    implementation "com.github.satori87.gdx-webrtc:teavm:0.1.0"
}
```

**Your server (or standalone signaling/TURN):**
```groovy
dependencies {
    implementation "com.github.satori87.gdx-webrtc:server:0.1.0"
}
```

### 3. Initialize per Platform

Each platform must set `WebRTCClients.FACTORY` before using the library.

**Desktop launcher:**
```java
import com.github.satori87.gdx.webrtc.WebRTCClients;
import com.github.satori87.gdx.webrtc.common.DesktopWebRTCFactory;

public class DesktopLauncher {
    public static void main(String[] args) {
        WebRTCClients.FACTORY = new DesktopWebRTCFactory();
        // ... create your libGDX application as normal ...
    }
}
```

**TeaVM launcher:**
```java
import com.github.satori87.gdx.webrtc.WebRTCClients;
import com.github.satori87.gdx.webrtc.teavm.TeaVMWebRTCFactory;

public class TeaVMLauncher {
    public static void main(String[] args) {
        WebRTCClients.FACTORY = new TeaVMWebRTCFactory();
        // ... create your TeaVM application as normal ...
    }
}
```

### 4. Use in Game Code

All networking code lives in your core module and is platform-agnostic:

```java
import com.github.satori87.gdx.webrtc.*;

public class MyNetworkGame {

    private WebRTCClient client;

    public void startNetworking() {
        WebRTCConfiguration config = new WebRTCConfiguration();
        config.signalingServerUrl = "ws://myserver.com:9090";

        // Optional: custom STUN server (defaults to Google's)
        // config.stunServer = "stun:my-stun-server.com:19302";

        // Optional: TURN server for NAT traversal fallback
        // config.turnServer = "turn:myserver.com:3478";
        // config.turnUsername = "myuser";
        // config.turnPassword = "mypass";

        client = WebRTCClients.newClient(config, new WebRTCClientListener() {
            public void onConnected(WebRTCPeer peer) {
                System.out.println("Connected to peer " + peer.getId());
            }

            public void onDisconnected(WebRTCPeer peer) {
                System.out.println("Peer " + peer.getId() + " disconnected");
            }

            public void onMessage(WebRTCPeer peer, byte[] data, boolean reliable) {
                // Handle received data
            }

            public void onError(String error) {
                System.err.println("Error: " + error);
            }
        });

        // Connect to signaling server
        client.connect();
    }

    public void joinPeer(int peerId) {
        // Connect to a specific peer (signaling handled automatically)
        client.connectToPeer(peerId);
    }

    public void sendGameState(WebRTCPeer peer, byte[] data) {
        // Position updates — unreliable is fine, drops if congested
        peer.sendUnreliable(data);
    }

    public void sendChatMessage(WebRTCPeer peer, byte[] data) {
        // Chat — needs guaranteed delivery
        peer.sendReliable(data);
    }

    public void shutdown() {
        if (client != null) {
            client.disconnect();
        }
    }
}
```

### 5. Run the Signaling Server

A signaling server is required to broker WebRTC connections between peers. It relays SDP offers/answers and ICE candidates, then gets out of the way once peers connect directly.

**Option A — Standalone:**
```bash
# Basic signaling only
java -jar gdx-webrtc-server-0.1.0.jar --port 9090

# With embedded TURN server for NAT traversal
java -jar gdx-webrtc-server-0.1.0.jar --port 9090 --turn --turn-port 3478 --turn-host 203.0.113.1
```

**Option B — Embedded in your own server:**
```java
import com.github.satori87.gdx.webrtc.server.WebRTCSignalingServer;
import com.github.satori87.gdx.webrtc.server.turn.TurnServer;
import com.github.satori87.gdx.webrtc.server.turn.TurnConfig;

// Start signaling server
WebRTCSignalingServer signaling = new WebRTCSignalingServer(9090);
signaling.start();

// Optional: start embedded TURN server
TurnConfig turnConfig = new TurnConfig();
turnConfig.port = 3478;
turnConfig.host = "203.0.113.1"; // your public IP
TurnServer turn = new TurnServer(turnConfig);
turn.start();
```

## Data Channels

Each peer connection has two data channels:

| Channel | Method | Ordered | Retransmits | Use Case |
|---------|--------|---------|-------------|----------|
| Reliable | `sendReliable()` | Yes | Unlimited | Chat, commands, critical state |
| Unreliable | `sendUnreliable()` | No | 0 | Position updates, input, real-time data |

Unreliable packets are silently dropped if the send buffer exceeds 64KB. If the unreliable channel is unavailable, `sendUnreliable()` falls back to reliable.

## Connection Stability

The library handles transient WebRTC disconnects automatically:

- **ICE DISCONNECTED** — waits 3.5 seconds, then restarts ICE if still disconnected
- **ICE FAILED** — retries with exponential backoff (2s, 4s, 8s), up to 3 attempts
- **Permanent failure** — only fires `onDisconnected()` after all retries are exhausted
- **ICE CONNECTED** — resets all retry counters

This means brief network blips won't trigger false disconnects.

## STUN / TURN

- **STUN** helps peers discover their public IP for direct connections. Defaults to Google's public STUN server (`stun:stun.l.google.com:19302`).
- **TURN** relays traffic when direct peer-to-peer fails (symmetric NAT, firewalls). Optional — configure via `WebRTCConfiguration.turnServer`.

The server module includes a built-in TURN server (RFC 5766, UDP) that can run alongside the signaling server.

## Building from Source

Requires JDK 17+ (Gradle 9.x requirement).

```bash
./gradlew build
```

## License

Open source. See LICENSE file for details.
