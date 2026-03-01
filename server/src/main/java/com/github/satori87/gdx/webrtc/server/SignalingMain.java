package com.github.satori87.gdx.webrtc.server;

import com.github.satori87.gdx.webrtc.server.turn.TurnConfig;
import com.github.satori87.gdx.webrtc.server.turn.TurnServer;

/**
 * Standalone signaling server with optional embedded TURN.
 *
 * <pre>
 * java -jar gdx-webrtc-server.jar [options]
 *
 * Options:
 *   --port N          Signaling server port (default 9090)
 *   --turn            Enable embedded TURN server
 *   --turn-port N     TURN server port (default 3478)
 *   --turn-host HOST  TURN public IP (auto-detect if omitted)
 *   --turn-user USER  TURN username (default "webrtc")
 *   --turn-pass PASS  TURN password (default "webrtc")
 * </pre>
 */
public class SignalingMain {

    public static void main(String[] args) {
        int port = 9090;
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
