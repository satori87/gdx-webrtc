# gdx-webrtc

A cross-platform WebRTC library for libGDX (and beyond). Provides a simple, java-websockets-style API for peer-to-peer connections with reliable and unreliable data channels. All signaling complexity (SDP offers/answers, ICE candidates) is hidden behind a connect-and-go interface.

## Supported Platforms

| Platform | Module | Status |
|----------|--------|--------|
| Desktop (LWJGL3) | `lwjgl3` | Available |
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
    implementation "com.github.satori87.gdx-webrtc:core:$gdxWebRTCVersion"
}
```

**Your desktop/lwjgl3 module:**
```groovy
dependencies {
    implementation "com.github.satori87.gdx-webrtc:lwjgl3:$gdxWebRTCVersion"
}
```

**Your teavm module:**
```groovy
dependencies {
    implementation "com.github.satori87.gdx-webrtc:teavm:$gdxWebRTCVersion"
}
```

**Your android module:**
```groovy
dependencies {
    implementation "com.github.satori87.gdx-webrtc:android:$gdxWebRTCVersion"
}
```

**Your iOS/RoboVM module:**
```groovy
dependencies {
    implementation "com.github.satori87.gdx-webrtc:ios:$gdxWebRTCVersion"
}
```

**Your server (or standalone signaling/TURN):**
```groovy
dependencies {
    implementation "com.github.satori87.gdx-webrtc:server:$gdxWebRTCVersion"
}
```

### 3. Initialize per Platform

Each platform must set `WebRTCClients.FACTORY` before using the library.

**Desktop launcher:**
```java
import com.github.satori87.gdx.webrtc.WebRTCClients;
import com.github.satori87.gdx.webrtc.lwjgl3.DesktopWebRTCFactory;

public class DesktopLauncher {
    public static void main(String[] args) {
        WebRTCClients.FACTORY = new DesktopWebRTCFactory();
        // ... create your libGDX application as normal ...
    }
}
```

**Headless dedicated server** (no audio hardware):
```java
WebRTCClients.FACTORY = new DesktopWebRTCFactory(true); // headless mode
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

## Choosing an Architecture

gdx-webrtc supports two networking architectures. Both use peer-to-peer WebRTC data channels under the hood — the difference is how your game logic is organized.

### Fully Peer-to-Peer

Every peer connects to every other peer. All peers are equal — there is no authoritative host. Good for small games where all players share state symmetrically (e.g., a card game, a simple co-op game).

```
          Signaling Server (WebSocket)
         /        |        \
     setup      setup     setup        ← signaling only (SDP + ICE)
       /          |          \
 [Peer A] ←——→ [Peer B]
 [Peer A] ←——→ [Peer C]               ← direct WebRTC data channels
 [Peer B] ←——→ [Peer C]
```

Use `WebRTCClient` (the low-level peer-to-peer API) for this model. You get callbacks for every peer that joins or leaves, and you can connect to any of them.

### Client/Server (Player-Hosted or Dedicated)

One peer acts as the authoritative server (host). Clients connect only to the host, not to each other. The host validates input, updates game state, and broadcasts results. This is the standard model for competitive multiplayer games.

```
          Signaling Server (WebSocket)
         /        |        \
     setup      setup     setup        ← signaling only (SDP + ICE)
       /          |          \
   [Host] ←——→ [Client A]
   [Host] ←——→ [Client B]             ← direct WebRTC data channels
   [Host] ←——→ [Client C]
```

Use `WebRTCServer` + `WebRTCGameClient` for this model. The server automatically connects to all joining peers. The client waits for the server to initiate the connection. The signaling server only brokers the initial handshake — all game data flows directly.

## API Levels

gdx-webrtc provides three API levels, from simplest to most flexible:

### 1. Client/Server API (`WebRTCServer` / `WebRTCGameClient`)

The easiest way to build a client/server game. Uses the library's built-in signaling server. The server auto-accepts all clients.

**Server side:**
```java
import com.github.satori87.gdx.webrtc.*;

WebRTCConfiguration config = new WebRTCConfiguration();
config.signalingServerUrl = "ws://myserver.com:9090";

WebRTCServer server = WebRTCClients.newServer(config, new WebRTCServerListener() {
    public void onStarted(int serverId) {
        System.out.println("Server ready, ID: " + serverId);
    }
    public void onClientConnected(int clientId) {
        System.out.println("Client " + clientId + " connected");
    }
    public void onClientDisconnected(int clientId) {
        System.out.println("Client " + clientId + " disconnected");
    }
    public void onClientMessage(int clientId, byte[] data, boolean reliable) {
        // Handle message from client
    }
    public void onError(String error) {
        System.err.println("Error: " + error);
    }
});

server.start();

// Send data
server.sendToClient(clientId, data);          // To one client
server.broadcastReliable(data);                // To all clients
server.broadcastUnreliable(positionData);      // Unreliable broadcast
server.broadcastReliableExcept(clientId, data); // To all except one
```

**Client side:**
```java
import com.github.satori87.gdx.webrtc.*;

WebRTCConfiguration config = new WebRTCConfiguration();
config.signalingServerUrl = "ws://myserver.com:9090";

WebRTCGameClient client = WebRTCClients.newGameClient(config, new WebRTCGameClientListener() {
    public void onConnected() {
        System.out.println("Connected to server");
    }
    public void onDisconnected() {
        System.out.println("Disconnected from server");
    }
    public void onMessage(byte[] data, boolean reliable) {
        // Handle message from server
    }
    public void onError(String error) {
        System.err.println("Error: " + error);
    }
});

client.connect();

// Send data
client.sendReliable(data);
client.sendUnreliable(inputData);
```

### 2. Peer-to-Peer API (`WebRTCClient`)

Full control over which peers to connect to. Best for fully peer-to-peer games, lobbies where players choose who to connect to, or custom architectures.

```java
import com.github.satori87.gdx.webrtc.*;

WebRTCConfiguration config = new WebRTCConfiguration();
config.signalingServerUrl = "ws://myserver.com:9090";

WebRTCClient client = WebRTCClients.newClient(config, new WebRTCClientListener() {
    public void onSignalingConnected(int localId) {
        System.out.println("Connected to signaling server, my ID: " + localId);
    }
    public void onPeerJoined(int peerId) {
        // A new peer joined the signaling server
        // Connect to them if you want:
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

client.connect();
```

### 3. Transport API (External Signaling)

For games with their own signaling mechanism (e.g., a lobby server, matchmaking service). Your application relays SDP offers/answers and ICE candidates through its own infrastructure — the library does not use its built-in signaling.

**Server side (offerer):**
```java
import com.github.satori87.gdx.webrtc.*;
import com.github.satori87.gdx.webrtc.transport.*;

WebRTCConfiguration config = new WebRTCConfiguration();
WebRTCServerTransport server = WebRTCTransports.newServerTransport(config);

// Optional: configure TURN for NAT traversal
server.setTurnServer("turn:myserver.com:3478", "user", "pass");

server.setListener(new ServerTransportListener() {
    public void onClientConnected(int connId) {
        System.out.println("Client " + connId + " connected");
    }
    public void onClientDisconnected(int connId) {
        System.out.println("Client " + connId + " disconnected");
    }
    public void onClientMessage(int connId, byte[] data, boolean reliable) {
        // Handle message from client
    }
});

// When a client wants to connect (via your lobby):
int connId = server.createPeerForOffer(new WebRTCServerTransport.SignalCallback() {
    public void onOffer(int connId, String sdpOffer) {
        // Send this offer to the client through your lobby
        lobby.sendToClient(clientId, "offer", sdpOffer);
    }
    public void onIceCandidate(int connId, String iceJson) {
        // Send ICE candidate to the client through your lobby
        lobby.sendToClient(clientId, "ice", iceJson);
    }
});

// When the client responds:
server.setAnswer(connId, sdpAnswer);
server.addIceCandidate(connId, iceJson);

// Send data
server.sendReliable(connId, data);
server.broadcastReliable(data);
server.broadcastUnreliable(positionData);
```

**Client side (answerer):**
```java
import com.github.satori87.gdx.webrtc.*;
import com.github.satori87.gdx.webrtc.transport.*;

WebRTCConfiguration config = new WebRTCConfiguration();
WebRTCClientTransport client = WebRTCTransports.newClientTransport(config);

client.setListener(new TransportListener() {
    public void onConnected() {
        System.out.println("Connected to server");
    }
    public void onDisconnected() {
        System.out.println("Disconnected from server");
    }
    public void onMessage(byte[] data, boolean reliable) {
        // Handle message from server
    }
    public void onError(String message) {
        System.err.println("Error: " + message);
    }
});

// When you receive an offer from the server (via your lobby):
client.connectWithOffer(sdpOffer, new WebRTCClientTransport.SignalCallback() {
    public void onAnswer(String sdpAnswer) {
        // Send this answer back through your lobby
        lobby.sendToServer("answer", sdpAnswer);
    }
    public void onIceCandidate(String iceJson) {
        // Send ICE candidate through your lobby
        lobby.sendToServer("ice", iceJson);
    }
});

// When the server sends an ICE candidate:
client.addIceCandidate(iceJson);

// Send data
client.sendReliable(data);
client.sendUnreliable(inputData);
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

- **ICE DISCONNECTED** -- waits 3.5 seconds, then restarts ICE if still disconnected
- **ICE FAILED** -- retries with exponential backoff (2s, 4s, 8s), up to 3 attempts
- **Permanent failure** -- only fires `onDisconnected()` after all retries are exhausted
- **ICE CONNECTED** -- resets all retry counters

All ICE restart parameters are configurable via `WebRTCConfiguration`:

```java
config.iceRestartDelayMs = 3500;       // Delay before restarting ICE on DISCONNECTED
config.maxIceRestartAttempts = 3;       // Max retries on FAILED before giving up
config.iceBackoffBaseMs = 2000;         // Base for exponential backoff (2s, 4s, 8s)
```

## STUN / TURN

- **STUN** helps peers discover their public IP for direct connections. Defaults to three public STUN servers for redundancy (`stun.l.google.com`, `stun1.l.google.com`, `stun.cloudflare.com`). WebRTC queries all STUN servers simultaneously -- if one is down, the others respond.
- **TURN** relays traffic when direct peer-to-peer fails (symmetric NAT, firewalls). Optional -- configure via `WebRTCConfiguration.turnServer`.

The library logs a warning to stdout if no server-reflexive (srflx) ICE candidates are generated for a connection, which indicates that all STUN servers may be unreachable.

You can override the default STUN servers:

```java
config.stunServers = new String[] {
    "stun:my-stun-server.com:19302",
    "stun:backup-stun.com:3478"
};
```

The server module includes a built-in TURN server (RFC 5766, UDP) that can run alongside the signaling server.

### Running the Signaling Server

```bash
# Basic signaling only
java -jar gdx-webrtc-server-0.3.0.jar --port 9090

# With embedded TURN server for NAT traversal
java -jar gdx-webrtc-server-0.3.0.jar --port 9090 --turn --turn-port 3478 --turn-host 203.0.113.1
```

**Ports to open:**

| Port | Protocol | Required | Purpose |
|------|----------|----------|---------|
| 9090 | TCP | Always | WebSocket signaling server |
| 3478 | UDP | TURN only | TURN server listening port |
| 49152-65535 | UDP | TURN only | Relay ports -- each peer allocation gets an ephemeral UDP port from this range for relaying traffic |

If you're behind a firewall, the signaling port (TCP) is always required. When running with `--turn`, you also need UDP 3478 and the ephemeral relay range open. Without TURN, peers connect directly via STUN and no additional server ports are needed.

## How It Works Under the Hood

### Standards and Protocols

gdx-webrtc is built on real protocol specifications, not ad-hoc networking:

| Component | Specification | What It Does |
|-----------|--------------|--------------|
| **STUN** | [RFC 5389](https://tools.ietf.org/html/rfc5389) | Session Traversal Utilities for NAT -- peers discover their public-facing IP and port by querying a STUN server |
| **TURN** | [RFC 5766](https://tools.ietf.org/html/rfc5766) | Traversal Using Relays around NAT -- when direct peer-to-peer fails (symmetric NAT, corporate firewalls), traffic is relayed through a TURN server |
| **ICE** | [RFC 8445](https://tools.ietf.org/html/rfc8445) | Interactive Connectivity Establishment -- the framework that coordinates STUN and TURN to find the best connection path between two peers |
| **SDP** | [RFC 4566](https://tools.ietf.org/html/rfc4566) / [RFC 3264](https://tools.ietf.org/html/rfc3264) | Session Description Protocol -- the offer/answer model used to negotiate media capabilities and connection parameters |
| **WebRTC Data Channels** | [RFC 8831](https://tools.ietf.org/html/rfc8831) | SCTP-based data channels over DTLS -- the transport layer that carries your actual game data |

### Embedded TURN Server

The server module includes a fully functional TURN server implementing RFC 5766 over UDP. It handles the complete TURN lifecycle:

- **Allocate** -- clients request a relay address; the server assigns a dedicated UDP socket and returns the relay endpoint
- **CreatePermission** -- clients authorize which peer IPs may send data through the relay
- **ChannelBind** -- binds a channel number (0x4000-0x7FFF) to a peer address for fast-path data forwarding (4-byte header instead of full STUN framing)
- **Refresh** -- clients extend or terminate their allocation lifetime (default 600s, max 3600s)
- **Send/Data indications** -- relay data to/from peers before channel binding is established

Authentication uses the long-term credential mechanism from RFC 5389: MD5-hashed `username:realm:password` keys with HMAC-SHA1 message integrity verification. Nonces are generated per-client to prevent replay attacks.

### Signaling Architecture

WebRTC requires an out-of-band signaling mechanism to exchange SDP offers/answers and ICE candidates before peers can connect directly. This library uses a lightweight WebSocket-based signaling server that:

1. Assigns each connecting client a unique peer ID
2. Acts as a dumb relay -- stamps the source ID on messages and forwards them to the target peer
3. Broadcasts `PEER_JOINED`/`PEER_LEFT` events so clients can discover each other
4. Gets out of the way once peers establish a direct connection

The signaling protocol is a simple JSON format: `{"type":N, "source":S, "target":T, "data":"..."}` with a hand-rolled parser (no JSON library dependency).

### ICE Restart Strategy

WebRTC connections can experience transient failures due to network changes (Wi-Fi to cellular, brief outages, NAT rebinding). Naively treating every ICE state change as a disconnect leads to false disconnects that ruin the user experience. This library implements a graduated restart strategy:

1. **ICE DISCONNECTED** -- not immediately fatal. A timer is set (default 3.5s). If the connection recovers before the timer fires, nothing happens. If still disconnected, ICE is restarted.
2. **ICE FAILED** -- more serious. The library retries with exponential backoff (2s, 4s, 8s) up to 3 attempts. Each retry performs a full ICE restart.
3. **ICE CONNECTED** -- all retry counters and timers are reset. A recovered connection is treated as fresh.
4. **Permanent failure** -- `onDisconnected()` only fires after all retry attempts are exhausted, meaning the connection is genuinely lost.

The disconnect timer uses timestamp-stamping to prevent stale timers from triggering restarts on already-recovered connections.

### Data Channel Design

Each peer connection creates two SCTP data channels with different reliability characteristics:

- **Reliable** (`ordered=true`) -- uses SCTP's full reliability: messages are delivered in order with unlimited retransmissions. Used for chat, commands, and any data that must arrive.
- **Unreliable** (`ordered=false, maxRetransmits=0`) -- fire-and-forget delivery with no retransmission attempts. Ideal for position updates and real-time game state where stale data is worse than missing data.

The unreliable channel implements backpressure: if the send buffer exceeds 64KB, packets are silently dropped rather than queuing up and adding latency. If the unreliable channel hasn't been established yet, `sendUnreliable()` transparently falls back to the reliable channel.

## Examples

The [`examples/webrtc-chat`](examples/webrtc-chat) directory contains a complete client/server chat application built with libGDX and gdx-webrtc. It runs on both Desktop (LWJGL3) and Browser (TeaVM), demonstrating `WebRTCServer` and `WebRTCGameClient` in action -- one player hosts, others join and chat over reliable data channels.

```bash
cd examples/webrtc-chat
./gradlew :signal-server:run           # Start signaling server on port 9090
./gradlew :lwjgl3:run                  # Run desktop client
./gradlew :teavm:run                   # Run browser client (http://localhost:8080)
```

## References

The design choices in this library are grounded in IETF standards, peer-reviewed research, and established industry practice.

### Dual Data Channels (Reliable + Unreliable)

- [RFC 8831 -- WebRTC Data Channels](https://datatracker.ietf.org/doc/html/rfc8831) (2021) -- Defines reliable and unreliable data channels over a single SCTP association
- [RFC 3758 -- SCTP Partial Reliability Extension](https://datatracker.ietf.org/doc/html/rfc3758) (2004) -- Enables multiplexing reliable and unreliable messages over one PR-SCTP association
- [RFC 8832 -- WebRTC Data Channel Establishment Protocol](https://datatracker.ietf.org/doc/html/rfc8832) (2021) -- In-band negotiation of per-channel reliability parameters
- Glenn Fiedler, [UDP vs. TCP](https://gafferongames.com/post/udp_vs_tcp/) / [Reliability and Congestion Avoidance over UDP](https://gafferongames.com/post/reliability_ordering_and_congestion_avoidance_over_udp/) -- Industry standard argument for dual reliable/unreliable channels in game networking
- [Valve GameNetworkingSockets](https://github.com/ValveSoftware/GameNetworkingSockets) -- Production networking library (Dota 2, CS:GO) using the same dual-message paradigm
- MDN, [WebRTC Data Channels for Game Development](https://developer.mozilla.org/en-US/docs/Games/Techniques/WebRTC_data_channels) -- Recommends separate reliable and unreliable channels for games

### ICE Restart with Exponential Backoff

- [RFC 8445 -- Interactive Connectivity Establishment](https://www.rfc-editor.org/rfc/rfc8445.html) (2018) -- Defines ICE restart mechanics and keepalive intervals
- [RFC 6298 -- Computing TCP's Retransmission Timer](https://datatracker.ietf.org/doc/html/rfc6298) (2011) -- Canonical specification for exponential backoff in Internet protocols
- Philipp Hancke, [ICE restarts](https://webrtchacks.com/an-oort-oort-oort-oort-oort-oort-oort-oort-oort-oort/) (2017) -- Found ICE restarts succeed ~69% of the time; 3 retries yields ~3% false-negative probability
- Philipp Hancke, [ICE restarts (again)](https://webrtchacks.com/the-oort-cloud/) (2019) -- Confirms ICE restarts are essential for reliable WebRTC services

### Lightweight Relay Signaling Server

- [RFC 8829 -- JSEP](https://datatracker.ietf.org/doc/html/rfc8829) (2021) -- WebRTC signaling is deliberately unspecified, leaving it up to the application
- [RFC 8827 -- WebRTC Security Architecture](https://datatracker.ietf.org/doc/html/rfc8827) (2021) -- Security is handled at the DTLS layer, so the signaling server can be a simple relay
- [RFC 7478 -- WebRTC Use Cases and Requirements](https://datatracker.ietf.org/doc/html/rfc7478) (2015) -- Signaling requirements are minimal: relay session descriptions and ICE candidates

### Send Buffer Backpressure (64KB Threshold)

- [RFC 8085 -- UDP Usage Guidelines](https://www.rfc-editor.org/rfc/rfc8085.html) (2017) -- Applications SHOULD control send rate; the buffer threshold implements sender-side rate control
- Jim Gettys, [Bufferbloat: Dark Buffers in the Internet](https://queue.acm.org/detail.cfm?id=2071893) (ACM Queue, 2011) -- Seminal paper showing excessive buffering causes catastrophic latency in interactive applications

### Peer-to-Peer Architecture

- Yahyavi & Kemme, [Peer-to-Peer Architectures for Massively Multiplayer Online Games: A Survey](https://dl.acm.org/doi/10.1145/2522968.2522977) (ACM Computing Surveys, 2013) -- 51-page survey concluding P2P achieves low infrastructure costs and fast response times via direct connections
- [An Open-Source Framework Using WebRTC for Online Multiplayer Gaming](https://dl.acm.org/doi/abs/10.1145/3631085.3631238) (ACM SBGames, 2023) -- Concludes the WebRTC API is mature enough to serve as the basis for online multiplayer games
- Mahmoud & Abozariba, [A Systematic Review on WebRTC Beyond Audio/Video Streaming](https://link.springer.com/article/10.1007/s11042-024-20448-9) (Springer Multimedia Tools and Applications, 2024) -- Reviews 83 WebRTC studies, identifies gaming as a key application area
- Ilya Grigorik, [High Performance Browser Networking -- WebRTC](https://hpbn.co/webrtc/) (O'Reilly, 2013) -- Authoritative reference on WebRTC data channels for application data

## License

Licensed under the [Apache License, Version 2.0](LICENSE). Same license as [libGDX](https://github.com/libgdx/libgdx).
