# gdx-webrtc

A cross-platform WebRTC library for libGDX (and beyond). Provides a simple, java-websockets-style API for peer-to-peer connections with reliable and unreliable data channels. All signaling complexity (SDP offers/answers, ICE candidates) is hidden behind a connect-and-go interface.

## Supported Platforms

| Platform | Module | Status |
|----------|--------|--------|
| Desktop (LWJGL3) | `common` | Available |
| Web (TeaVM) | `teavm` | Available |
| Android | `android` | Available |
| iOS (RoboVM) | `ios` | Available (untested) |

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

**Your android module:**
```groovy
dependencies {
    implementation "com.github.satori87.gdx-webrtc:android:0.1.0"
}
```

**Your iOS/RoboVM module:**
```groovy
dependencies {
    implementation "com.github.satori87.gdx-webrtc:ios:0.1.0"
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

**Android launcher:**
```java
import com.github.satori87.gdx.webrtc.WebRTCClients;
import com.github.satori87.gdx.webrtc.android.AndroidWebRTCFactory;

public class AndroidLauncher extends AndroidApplication {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WebRTCClients.FACTORY = new AndroidWebRTCFactory(this);
        // ... create your libGDX application as normal ...
    }
}
```

**iOS launcher (RoboVM):**
```java
import com.github.satori87.gdx.webrtc.WebRTCClients;
import com.github.satori87.gdx.webrtc.ios.IOSWebRTCFactory;

public class IOSLauncher extends IOSApplication.Delegate {
    @Override
    protected IOSApplication createApplication() {
        WebRTCClients.FACTORY = new IOSWebRTCFactory();
        // ... create your libGDX application as normal ...
    }
}
```

> **iOS note:** Your RoboVM project must include `WebRTC.framework` in its `robovm.xml` configuration. Obtain it via CocoaPods (`GoogleWebRTC`) or as a prebuilt framework from webrtc.org.

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
            public void onSignalingConnected(int localId) {
                System.out.println("Connected to signaling server, my ID: " + localId);
            }

            public void onPeerJoined(int peerId) {
                System.out.println("Peer " + peerId + " joined");
                // Auto-connect to new peers:
                // client.connectToPeer(peerId);
            }

            public void onPeerLeft(int peerId) {
                System.out.println("Peer " + peerId + " left");
            }

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

```bash
# Basic signaling only
java -jar gdx-webrtc-server-0.1.0.jar --port 9090

# With embedded TURN server for NAT traversal
java -jar gdx-webrtc-server-0.1.0.jar --port 9090 --turn --turn-port 3478 --turn-host 203.0.113.1
```

**Ports to open:**

| Port | Protocol | Required | Purpose |
|------|----------|----------|---------|
| 9090 | TCP | Always | WebSocket signaling server |
| 3478 | UDP | TURN only | TURN server listening port |
| 49152–65535 | UDP | TURN only | Relay ports — each peer allocation gets an ephemeral UDP port from this range for relaying traffic |

If you're behind a firewall, the signaling port (TCP) is always required. When running with `--turn`, you also need UDP 3478 and the ephemeral relay range open. Without TURN, peers connect directly via STUN and no additional server ports are needed.

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

## Network Architecture

gdx-webrtc is a peer-to-peer library — all data flows directly between peers over WebRTC data channels. There is no dedicated server relay for game traffic (unless TURN is needed for NAT traversal).

This does not prevent you from using a client/server model. One peer simply acts as the authoritative host while other peers connect to it. The host maintains multiple `WebRTCPeer` connections and handles game logic — validating input, updating game state, and broadcasting results to connected peers. From WebRTC's perspective they're all equal peers, but in your game code the host is the authority.

```
          Signaling Server (WebSocket)
         /        |        \
     setup      setup     setup        ← signaling only (SDP + ICE)
       /          |          \
   [Host] ←——→ [Peer A]
   [Host] ←——→ [Peer B]               ← direct WebRTC data channels
   [Host] ←——→ [Peer C]
```

The signaling server brokers the initial WebRTC handshake and then gets out of the way. All game data flows directly between the host and each peer.

## STUN / TURN

- **STUN** helps peers discover their public IP for direct connections. Defaults to Google's public STUN server (`stun:stun.l.google.com:19302`).
- **TURN** relays traffic when direct peer-to-peer fails (symmetric NAT, firewalls). Optional — configure via `WebRTCConfiguration.turnServer`.

The server module includes a built-in TURN server (RFC 5766, UDP) that can run alongside the signaling server.

## How It Works Under the Hood

### Standards and Protocols

gdx-webrtc is built on real protocol specifications, not ad-hoc networking:

| Component | Specification | What It Does |
|-----------|--------------|--------------|
| **STUN** | [RFC 5389](https://tools.ietf.org/html/rfc5389) | Session Traversal Utilities for NAT — peers discover their public-facing IP and port by querying a STUN server |
| **TURN** | [RFC 5766](https://tools.ietf.org/html/rfc5766) | Traversal Using Relays around NAT — when direct peer-to-peer fails (symmetric NAT, corporate firewalls), traffic is relayed through a TURN server |
| **ICE** | [RFC 8445](https://tools.ietf.org/html/rfc8445) | Interactive Connectivity Establishment — the framework that coordinates STUN and TURN to find the best connection path between two peers |
| **SDP** | [RFC 4566](https://tools.ietf.org/html/rfc4566) / [RFC 3264](https://tools.ietf.org/html/rfc3264) | Session Description Protocol — the offer/answer model used to negotiate media capabilities and connection parameters |
| **WebRTC Data Channels** | [RFC 8831](https://tools.ietf.org/html/rfc8831) | SCTP-based data channels over DTLS — the transport layer that carries your actual game data |

### Embedded TURN Server

The server module includes a fully functional TURN server implementing RFC 5766 over UDP. It handles the complete TURN lifecycle:

- **Allocate** — clients request a relay address; the server assigns a dedicated UDP socket and returns the relay endpoint
- **CreatePermission** — clients authorize which peer IPs may send data through the relay
- **ChannelBind** — binds a channel number (0x4000–0x7FFF) to a peer address for fast-path data forwarding (4-byte header instead of full STUN framing)
- **Refresh** — clients extend or terminate their allocation lifetime (default 600s, max 3600s)
- **Send/Data indications** — relay data to/from peers before channel binding is established

Authentication uses the long-term credential mechanism from RFC 5389: MD5-hashed `username:realm:password` keys with HMAC-SHA1 message integrity verification. Nonces are generated per-client to prevent replay attacks.

### Signaling Architecture

WebRTC requires an out-of-band signaling mechanism to exchange SDP offers/answers and ICE candidates before peers can connect directly. This library uses a lightweight WebSocket-based signaling server that:

1. Assigns each connecting client a unique peer ID
2. Acts as a dumb relay — stamps the source ID on messages and forwards them to the target peer
3. Broadcasts `PEER_JOINED`/`PEER_LEFT` events so clients can discover each other
4. Gets out of the way once peers establish a direct connection

The signaling protocol is a simple JSON format: `{"type":N, "source":S, "target":T, "data":"..."}` with a hand-rolled parser (no JSON library dependency).

### ICE Restart Strategy

WebRTC connections can experience transient failures due to network changes (Wi-Fi to cellular, brief outages, NAT rebinding). Naively treating every ICE state change as a disconnect leads to false disconnects that ruin the user experience. This library implements a graduated restart strategy:

1. **ICE DISCONNECTED** — not immediately fatal. A timer is set (default 3.5s). If the connection recovers before the timer fires, nothing happens. If still disconnected, ICE is restarted.
2. **ICE FAILED** — more serious. The library retries with exponential backoff (2s, 4s, 8s) up to 3 attempts. Each retry performs a full ICE restart.
3. **ICE CONNECTED** — all retry counters and timers are reset. A recovered connection is treated as fresh.
4. **Permanent failure** — `onDisconnected()` only fires after all retry attempts are exhausted, meaning the connection is genuinely lost.

The disconnect timer uses timestamp-stamping to prevent stale timers from triggering restarts on already-recovered connections.

### Data Channel Design

Each peer connection creates two SCTP data channels with different reliability characteristics:

- **Reliable** (`ordered=true`) — uses SCTP's full reliability: messages are delivered in order with unlimited retransmissions. Used for chat, commands, and any data that must arrive.
- **Unreliable** (`ordered=false, maxRetransmits=0`) — fire-and-forget delivery with no retransmission attempts. Ideal for position updates and real-time game state where stale data is worse than missing data.

The unreliable channel implements backpressure: if the send buffer exceeds 64KB, packets are silently dropped rather than queuing up and adding latency. If the unreliable channel hasn't been established yet, `sendUnreliable()` transparently falls back to the reliable channel.

## Examples

The [`examples/webrtc-chat`](examples/webrtc-chat) directory contains a complete peer-to-peer chat application built with libGDX and gdx-webrtc. It runs on both Desktop (LWJGL3) and Browser (TeaVM) and demonstrates peer discovery, connecting, and sending messages over reliable data channels.

The example includes its own standalone signaling server in the `signal-server` module:

```bash
cd examples/webrtc-chat
./gradlew :signal-server:run           # Start signaling server on port 9090
./gradlew :lwjgl3:run                  # Run desktop client
./gradlew :teavm:run                   # Run browser client (http://localhost:8080)
```

## Building from Source

Requires JDK 17+ (Gradle 9.x requirement).

```bash
./gradlew build
```

## License

Open source. See LICENSE file for details.
