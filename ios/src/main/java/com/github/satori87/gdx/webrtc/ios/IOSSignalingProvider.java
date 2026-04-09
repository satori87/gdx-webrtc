package com.github.satori87.gdx.webrtc.ios;

import com.github.satori87.gdx.webrtc.SignalMessage;
import com.github.satori87.gdx.webrtc.SignalingEventHandler;
import com.github.satori87.gdx.webrtc.SignalingProvider;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;

/**
 * iOS implementation of SignalingProvider using Java-WebSocket.
 * Works on RoboVM without modification.
 */
class IOSSignalingProvider implements SignalingProvider {

    /** The Java-WebSocket client used for signaling communication. */
    private WebSocketClient wsClient;

    /** {@inheritDoc} */
    public void connect(String url, final SignalingEventHandler handler) {
        try {
            URI uri = new URI(url);
            wsClient = new WebSocketClient(uri) {
                public void onOpen(ServerHandshake handshake) {
                    handler.onOpen();
                }

                public void onMessage(String message) {
                    SignalMessage msg = SignalMessage.fromJson(message);
                    if (msg != null) {
                        handler.onMessage(msg);
                    }
                }

                public void onClose(int code, String reason, boolean remote) {
                    handler.onClose(reason != null ? reason : "Connection closed");
                }

                public void onError(Exception ex) {
                    handler.onError(ex != null ? ex.getMessage() : "Unknown error");
                }
            };
            // Run the WebSocket client on a daemon thread so it does not
            // prevent JVM shutdown when the application exits.
            Thread wsThread = new Thread(wsClient, "webrtc-signaling");
            wsThread.setDaemon(true);
            wsThread.start();
        } catch (Exception e) {
            handler.onError("Failed to connect to signaling server: " + e.getMessage());
        }
    }

    /** {@inheritDoc} */
    public void send(SignalMessage msg) {
        if (wsClient != null && wsClient.isOpen()) {
            wsClient.send(msg.toJson());
        }
    }

    /** {@inheritDoc} */
    public void close() {
        if (wsClient != null) {
            try {
                wsClient.close();
            } catch (Exception e) {
                // Ignore close errors
            }
            wsClient = null;
        }
    }

    /** {@inheritDoc} */
    public boolean isOpen() {
        return wsClient != null && wsClient.isOpen();
    }
}
