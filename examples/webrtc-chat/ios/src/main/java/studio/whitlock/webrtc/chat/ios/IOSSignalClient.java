package studio.whitlock.webrtc.chat.ios;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import studio.whitlock.webrtc.chat.ChatSignalClient;

import java.net.URI;

/** iOS implementation of {@link ChatSignalClient} using Java-WebSocket. */
public class IOSSignalClient implements ChatSignalClient {

    private WebSocketClient wsClient;

    public void connect(String url, final Listener listener) {
        try {
            wsClient = new WebSocketClient(new URI(url)) {
                public void onOpen(ServerHandshake handshake) {
                    listener.onOpen();
                }

                public void onMessage(String message) {
                    listener.onMessage(message);
                }

                public void onClose(int code, String reason, boolean remote) {
                    listener.onClose(reason != null ? reason : "Connection closed");
                }

                public void onError(Exception ex) {
                    listener.onError(ex != null ? ex.getMessage() : "Unknown error");
                }
            };
            wsClient.connect();
        } catch (Exception e) {
            listener.onError("Failed to connect: " + e.getMessage());
        }
    }

    public void send(String text) {
        if (wsClient != null && wsClient.isOpen()) {
            wsClient.send(text);
        }
    }

    public void close() {
        if (wsClient != null) {
            try {
                wsClient.close();
            } catch (Exception e) {
                // Ignore
            }
            wsClient = null;
        }
    }

    public boolean isOpen() {
        return wsClient != null && wsClient.isOpen();
    }
}
