# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

gdx-webrtc is an open-source cross-platform WebRTC library for libGDX (and beyond). It provides a simple, java-websockets-style API for peer-to-peer connections with reliable and unreliable data channels. All signaling complexity (SDP offers/answers, ICE candidates) is hidden behind a connect-and-go interface.

## Build Commands

Requires JDK 17+ (Gradle 9.x). On this machine: `export JAVA_HOME="/c/Users/sator/.jdks/openjdk-23.0.2"`

```bash
./gradlew build              # Build all modules
./gradlew :core:build        # Build single module
./gradlew :server:jar        # Build standalone server fat JAR
./gradlew :server:run        # Run signaling server (port 9090)
```

No tests exist yet. No linter is configured.

## Module Architecture

Six modules, all under package `com.github.satori87.gdx.webrtc`:

| Module | Purpose | Java Target | Key Dependencies |
|--------|---------|-------------|-----------------|
| **core** | Platform-agnostic API interfaces | Java 8 | None |
| **common** | Desktop implementation | Java 11 | `dev.onvoid.webrtc:webrtc-java`, `Java-WebSocket` |
| **teavm** | Browser implementation | Java 11 | `teavm-jso`, `teavm-jso-apis` (compileOnly) |
| **android** | Android implementation | Java 8 | `io.github.webrtc-sdk:android`, `Java-WebSocket` |
| **ios** | iOS (RoboVM) implementation | Java 8 | `robovm-rt`, `robovm-objc`, `robovm-cocoatouch` (compileOnly), `Java-WebSocket` |
| **server** | Signaling server + TURN server | Java 11 | `Java-WebSocket` |

All platform modules depend on `core`. The `android` module uses the `com.android.library` Gradle plugin (not `java-library`); the root `build.gradle` excludes it from the `java-library` apply. The `ios` module includes RoboVM binding classes in `ios/bindings/` that map to WebRTC.framework's Objective-C API. The `server` module depends on `core` for `SignalMessage` only.

## Java Compatibility Constraint

**All code must use Java 7 language constructs** — no lambdas, method references, streams, try-with-resources, diamond operator on anonymous classes, or Java 8+ APIs. This is for future RoboVM/iOS compatibility. The Java 8/11 compilation targets are the minimum JDK 23 supports.

## Key Design Patterns

- **Factory pattern**: Users set `WebRTCClients.FACTORY` to a platform-specific factory (e.g., `DesktopWebRTCFactory`, `TeaVMWebRTCFactory`, `AndroidWebRTCFactory`, `IOSWebRTCFactory`) before calling `WebRTCClients.newClient(config, listener)`. `AndroidWebRTCFactory` takes a `Context` parameter in its constructor.
- **Signaling protocol**: JSON messages (`SignalMessage`) over WebSocket with hand-rolled parser (no JSON library). Types: WELCOME, CONNECT_REQUEST, OFFER, ANSWER, ICE, PEER_LIST, ERROR, PEER_JOINED, PEER_LEFT. The server is a dumb relay that stamps source IDs and forwards to targets. The CONNECT_REQUEST receiver becomes the SDP offerer.
- **Two data channels per peer**: `sendReliable()` (ordered, unlimited retransmits) and `sendUnreliable()` (unordered, maxRetransmits=0). Unreliable packets are silently dropped if send buffer exceeds 64KB; falls back to reliable if channel unavailable.
- **ICE restart stability**: On ICE DISCONNECTED, waits 3.5s then restarts ICE. On ICE FAILED, retries with exponential backoff (2s, 4s, 8s, max 3 attempts). `onDisconnected()` only fires after all retries are exhausted.
- **TeaVM native interop**: Uses `@JSBody` for inline JavaScript and `@JSFunctor` for callback interfaces. All browser WebRTC/WebSocket calls go through static native methods.

## Connection Management Patterns

All four platform clients (`DesktopWebRTCClient`, `TeaVMWebRTCClient`, `AndroidWebRTCClient`, `IOSWebRTCClient`) implement the same ICE restart and data channel management logic for their respective platforms.

## Publishing

Configured for JitPack (`com.github.satori87.gdx-webrtc`). All modules produce sources JARs via `java { withSourcesJar() }`.
