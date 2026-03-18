package com.github.satori87.gdx.webrtc.lwjgl3;

import com.github.satori87.gdx.webrtc.SignalMessage;
import com.github.satori87.gdx.webrtc.SignalingEventHandler;
import com.github.satori87.gdx.webrtc.SignalingProvider;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;

/**
 * Desktop/JVM implementation of {@link SignalingProvider} using the
 * <a href="https://github.com/TooTallNate/Java-WebSocket">Java-WebSocket</a>
 * library for WebSocket communication with the signaling server.
 *
 * <p>This provider manages a single {@link org.java_websocket.client.WebSocketClient}
 * connection to the signaling server. Incoming WebSocket messages are parsed into
 * {@link SignalMessage} objects and forwarded to the registered
 * {@link SignalingEventHandler}. Outgoing messages are serialized from
 * {@link SignalMessage} to JSON and sent over the WebSocket.</p>
 *
 * <p>Lifecycle of the connection:</p>
 * <ol>
 *   <li>{@link #connect(String, SignalingEventHandler)} -- creates and opens the
 *       WebSocket connection to the given URL.</li>
 *   <li>{@link #send(SignalMessage)} -- sends a signaling message while connected.</li>
 *   <li>{@link #close()} -- closes the WebSocket and releases the client reference.</li>
 * </ol>
 *
 * <p>This class is package-private and is instantiated by
 * {@link DesktopWebRTCFactory} when creating a new {@link BaseWebRTCClient}.</p>
 *
 * @see SignalingProvider
 * @see DesktopWebRTCFactory
 * @see BaseWebRTCClient
 */
class DesktopSignalingProvider implements SignalingProvider {

    /**
     * The underlying Java-WebSocket client instance. Set during
     * {@link #connect(String, SignalingEventHandler)} and cleared on {@link #close()}.
     * May be {@code null} if the connection has not been established or has been closed.
     */
    private WebSocketClient wsClient;

    /**
     * {@inheritDoc}
     *
     * <p>Creates a new {@link org.java_websocket.client.WebSocketClient} targeting
     * the given URL and initiates an asynchronous connection. The WebSocket's four
     * callback methods ({@code onOpen}, {@code onMessage}, {@code onClose},
     * {@code onError}) are wired to the corresponding methods on the provided
     * {@link SignalingEventHandler}.</p>
     *
     * <p>Incoming message strings are parsed via {@link SignalMessage#fromJson(String)};
     * malformed messages that return {@code null} are silently discarded.</p>
     *
     * <p>If the URI is invalid or any other exception occurs during setup, the
     * handler's {@link SignalingEventHandler#onError(String)} method is called
     * with a descriptive error message instead of throwing.</p>
     *
     * @param url     the WebSocket URL of the signaling server (e.g.,
     *                {@code "ws://localhost:9090"})
     * @param handler callbacks for connection open, incoming messages, close,
     *                and error events
     */
    public void connect(String url, final SignalingEventHandler handler) {
        try {
            // Close any existing connection before creating a new one
            // to prevent ghost peers in the signaling server
            if (wsClient != null) {
                try { wsClient.close(); } catch (Exception ignored) {}
                wsClient = null;
            }
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
                    handler.onClose(reason);
                }

                public void onError(Exception ex) {
                    handler.onError(ex.getMessage());
                }
            };
            wsClient.connect();
        } catch (Exception e) {
            handler.onError("Failed to connect to signaling server: " + e.getMessage());
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Serializes the given {@link SignalMessage} to JSON via
     * {@link SignalMessage#toJson()} and sends it over the WebSocket. The message
     * is silently dropped if the WebSocket client is {@code null} or not currently
     * in the open state.</p>
     *
     * @param msg the signaling message to send
     */
    public void send(SignalMessage msg) {
        if (wsClient != null && wsClient.isOpen()) {
            wsClient.send(msg.toJson());
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Closes the underlying {@link org.java_websocket.client.WebSocketClient}
     * connection and sets the client reference to {@code null}. If the client is
     * already {@code null} (not connected or previously closed), this method is
     * a no-op.</p>
     */
    public void close() {
        if (wsClient != null) {
            wsClient.close();
            wsClient = null;
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns {@code true} if the WebSocket client exists and its connection
     * is currently in the open state. This is checked before sending messages to
     * avoid exceptions on a closed or uninitialized connection.</p>
     *
     * @return {@code true} if the WebSocket connection is open, {@code false} otherwise
     */
    public boolean isOpen() {
        return wsClient != null && wsClient.isOpen();
    }
}
