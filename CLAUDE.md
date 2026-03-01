# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

gdx-webrtc is an open-source WebRTC library designed for use in, but not limited to, libGDX projects. It provides a simple, java-websockets-style API for peer-to-peer connections with data channels. Signaling (SDP/ICE exchange) is hidden from users behind a connect-and-go interface.

## Build Commands

```bash
./gradlew build          # Build all modules
./gradlew :core:build    # Build core API only
./gradlew :server:build  # Build server module only
./gradlew :server:jar    # Build standalone server JAR
```

## Module Architecture

- **core/** — Platform-agnostic API interfaces (pure Java 7, no dependencies)
- **common/** — Desktop implementation using webrtc-java (`dev.onvoid.webrtc`)
- **teavm/** — Browser implementation using TeaVM JSO (`@JSBody`/`@JSFunctor`)
- **server/** — WebSocket signaling server + embedded TURN server (RFC 5766)

Package: `com.github.satori87.gdx.webrtc`

## Target Platforms

- **Desktop** (LWJGL3) — via common module
- **Web** (TeaVM) — via teavm module
- **Android** — planned (future module)
- **iOS** (RoboVM/MobiVM) — planned (future module)

## Java Compatibility

**All code must use Java 7 language constructs.** No lambdas, method references, streams, or Java 8+ APIs. Core module compiles at Java 8 target; platform modules (common, teavm, server) at Java 11 target. Build requires JDK 17+ (Gradle 9.x requirement). Set `JAVA_HOME` to JDK 23 or similar.

## Key Design Patterns

- **Factory pattern**: `WebRTCClients.FACTORY` set by platform init, like gdx-websockets
- **Signaling hidden**: Users call `connect()`/`connectToPeer()`, library handles SDP/ICE internally via WebSocket to signaling server
- **Two data channels**: reliable (ordered) + unreliable (unordered, maxRetransmits=0)
- **ICE restart stability**: 3.5s delay on DISCONNECTED, exponential backoff on FAILED (2s/4s/8s, max 3 retries) — only fires `onDisconnected()` on permanent failure
- **64KB unreliable buffer limit**: packets silently dropped if congested

## Dependencies

- core: none
- common: `dev.onvoid.webrtc:webrtc-java`, `org.java-websocket:Java-WebSocket`
- teavm: `org.teavm:teavm-jso` + `teavm-jso-apis` (compileOnly)
- server: `org.java-websocket:Java-WebSocket`

## Reference

Connection management patterns ported from spacedout/voidgun (`c:/dev/spacedout`).
