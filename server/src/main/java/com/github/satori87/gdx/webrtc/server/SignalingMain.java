package com.github.satori87.gdx.webrtc.server;

import com.github.satori87.gdx.webrtc.server.turn.TurnConfig;
import com.github.satori87.gdx.webrtc.server.turn.TurnServer;

/**
 * Entry point for the standalone gdx-webrtc server process.
 *
 * <p>This class provides a {@code main} method that starts a WebSocket-based
 * {@link WebRTCSignalingServer} and, optionally, an embedded {@link TurnServer}
 * for relaying media when direct peer-to-peer connections cannot be established.
 * The TURN server runs on daemon threads; the signaling server uses the
 * java-websocket library's own threads. The main thread blocks until the JVM
 * receives a shutdown signal (Ctrl+C), at which point a shutdown hook cleanly
 * stops both servers.</p>
 *
 * <h3>Command-line usage</h3>
 * <pre>
 * java -jar gdx-webrtc-server.jar [options]
 *
 * Options:
 *   --port N          Signaling server WebSocket port (default 9090)
 *   --turn            Enable the embedded TURN server
 *   --turn-port N     TURN server UDP port (default 3478)
 *   --turn-host HOST  TURN public IP / hostname (auto-detected if omitted)
 *   --turn-user USER  TURN long-term credential username (default "webrtc")
 *   --turn-pass PASS  TURN long-term credential password (default "webrtc")
 * </pre>
 *
 * <h3>Example</h3>
 * <pre>
 * // Start signaling on port 9090 with TURN enabled on port 3478:
 * java -jar gdx-webrtc-server.jar --turn
 *
 * // Custom ports and credentials:
 * java -jar gdx-webrtc-server.jar --port 8080 --turn --turn-port 3479 \
 *     --turn-user myuser --turn-pass secret
 * </pre>
 *
 * @see WebRTCSignalingServer
 * @see TurnServer
 * @see TurnConfig
 */
public class SignalingMain {

    /**
     * Launches the signaling server (and optionally the TURN server) based on
     * command-line arguments, then blocks the main thread until the process is
     * terminated.
     *
     * <p>A JVM shutdown hook is registered to gracefully stop both servers
     * when the process receives a termination signal.</p>
     *
     * @param args command-line arguments; see class-level documentation for
     *             supported options
     */
    public static void main(String[] args) {
        int port = SignalingServerConfig.DEFAULT_PORT;
        boolean enableTurn = false;
        TurnConfig turnConfig = new TurnConfig();

        for (int i = 0; i < args.length; i++) {
            if ("--port".equals(args[i]) && i + 1 < args.length) {
                port = Integer.parseInt(args[++i]);
            } else if ("--turn".equals(args[i])) {
                enableTurn = true;
            } else if ("--turn-port".equals(args[i]) && i + 1 < args.length) {
                turnConfig.port = Integer.parseInt(args[++i]);
            } else if ("--turn-host".equals(args[i]) && i + 1 < args.length) {
                turnConfig.host = args[++i];
            } else if ("--turn-user".equals(args[i]) && i + 1 < args.length) {
                turnConfig.username = args[++i];
            } else if ("--turn-pass".equals(args[i]) && i + 1 < args.length) {
                turnConfig.password = args[++i];
            }
        }

        // Start signaling server
        WebRTCSignalingServer signaling = new WebRTCSignalingServer(port);
        signaling.start();

        // Optionally start TURN server
        TurnServer turnServer = null;
        if (enableTurn) {
            turnServer = new TurnServer(turnConfig);
            turnServer.start();
        }

        System.out.println("gdx-webrtc server running. Press Ctrl+C to stop.");

        // Block main thread
        final WebRTCSignalingServer signalingRef = signaling;
        final TurnServer turnRef = turnServer;
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            public void run() {
                signalingRef.stop();
                if (turnRef != null) {
                    turnRef.stop();
                }
            }
        }));

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
