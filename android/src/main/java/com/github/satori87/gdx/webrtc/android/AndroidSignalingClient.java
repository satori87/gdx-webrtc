package com.github.satori87.gdx.webrtc.android;

import com.github.satori87.gdx.webrtc.SignalMessage;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;

/**
 * WebSocket client for connecting to the signaling server (Android).
 * Uses Java-WebSocket which works on Android without modification.
 */
class AndroidSignalingClient {

    interface Listener {
        void onSignalingOpen();
        void onSignalingMessage(SignalMessage msg);
        void onSignalingClose(String reason);
        void onSignalingError(String error);
    }

    private WebSocketClient ws;
    private final Listener listener;

    AndroidSignalingClient(Listener listener) {
        this.listener = listener;
    }

    void connect(String url) {
        try {
            ws = new WebSocketClient(new URI(url)) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    listener.onSignalingOpen();
                }

                @Override
                public void onMessage(String message) {
                    SignalMessage msg = SignalMessage.fromJson(message);
                    if (msg != null) {
                        listener.onSignalingMessage(msg);
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    listener.onSignalingClose(reason != null ? reason : "Connection closed");
                }

                @Override
                public void onError(Exception ex) {
                    listener.onSignalingError(ex != null ? ex.getMessage() : "Unknown error");
                }
            };
            ws.connect();
        } catch (Exception e) {
            listener.onSignalingError("Failed to connect: " + e.getMessage());
        }
    }

    void send(SignalMessage msg) {
        if (ws != null && ws.isOpen()) {
            ws.send(msg.toJson());
        }
    }

    void close() {
        if (ws != null) {
            try {
                ws.close();
            } catch (Exception e) {
                // Ignore close errors
            }
            ws = null;
        }
    }

    boolean isOpen() {
        return ws != null && ws.isOpen();
    }
}
