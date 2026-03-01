package com.github.satori87.gdx.webrtc.common;

import com.github.satori87.gdx.webrtc.SignalMessage;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;

/**
 * WebSocket client for connecting to the signaling server (desktop).
 */
class DesktopSignalingClient {

    interface Listener {
        void onSignalingOpen();
        void onSignalingMessage(SignalMessage msg);
        void onSignalingClose(String reason);
        void onSignalingError(String error);
    }

    private WebSocketClient wsClient;
    private final Listener listener;

    DesktopSignalingClient(Listener listener) {
        this.listener = listener;
    }

    void connect(String url) {
        try {
            URI uri = new URI(url);
            wsClient = new WebSocketClient(uri) {
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
                    listener.onSignalingClose(reason);
                }

                @Override
                public void onError(Exception ex) {
                    listener.onSignalingError(ex.getMessage());
                }
            };
            wsClient.connect();
        } catch (Exception e) {
            listener.onSignalingError("Failed to connect to signaling server: " + e.getMessage());
        }
    }

    void send(SignalMessage msg) {
        if (wsClient != null && wsClient.isOpen()) {
            wsClient.send(msg.toJson());
        }
    }

    void close() {
        if (wsClient != null) {
            wsClient.close();
            wsClient = null;
        }
    }

    boolean isOpen() {
        return wsClient != null && wsClient.isOpen();
    }
}
