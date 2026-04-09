package com.github.satori87.gdx.webrtc.android;

import com.github.satori87.gdx.webrtc.SignalMessage;
import com.github.satori87.gdx.webrtc.SignalingEventHandler;
import com.github.satori87.gdx.webrtc.SignalingProvider;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;

/**
 * Android implementation of {@link SignalingProvider} using the Java-WebSocket library
 * ({@code org.java_websocket}).
 *
 * <p>This class manages the WebSocket connection to the signaling server, which
 * relays SDP offers/answers and ICE candidates between peers during the WebRTC
 * connection establishment process. It wraps a {@link WebSocketClient} instance
 * and translates its callbacks into the platform-agnostic
 * {@link SignalingEventHandler} interface.</p>
 *
 * <h3>Connection Lifecycle</h3>
 * <p>The WebSocket connection is opened via {@link #connect(String, SignalingEventHandler)}
 * and closed via {@link #close()}. Incoming messages are parsed from JSON into
 * {@link SignalMessage} objects using {@link SignalMessage#fromJson(String)} and
 * forwarded to the handler. Outgoing messages are serialized via
 * {@link SignalMessage#toJson()} before being sent.</p>
 *
 * <h3>Thread Safety</h3>
 * <p>The Java-WebSocket library manages its own threading. WebSocket callbacks
 * (onOpen, onMessage, onClose, onError) are invoked on the WebSocket's reader
 * thread. The {@link #send(SignalMessage)} method may be called from any thread.</p>
 *
 * @see SignalingProvider
 * @see SignalingEventHandler
 * @see SignalMessage
 * @see com.github.satori87.gdx.webrtc.BaseWebRTCClient
 */
class AndroidSignalingProvider implements SignalingProvider {

    /**
     * The underlying Java-WebSocket client instance.
     * Created in {@link #connect(String, SignalingEventHandler)} and
     * nulled out in {@link #close()}.
     */
    private WebSocketClient ws;

    /**
     * {@inheritDoc}
     *
     * <p>Creates a new {@link WebSocketClient} connected to the given signaling
     * server URL. The client's lifecycle callbacks are wired to the provided
     * {@link SignalingEventHandler}:</p>
     * <ul>
     *   <li>{@code onOpen} -- calls {@link SignalingEventHandler#onOpen()}</li>
     *   <li>{@code onMessage} -- parses JSON via {@link SignalMessage#fromJson(String)}
     *       and calls {@link SignalingEventHandler#onMessage(SignalMessage)} (messages
     *       that fail to parse are silently dropped)</li>
     *   <li>{@code onClose} -- calls {@link SignalingEventHandler#onClose(String)}</li>
     *   <li>{@code onError} -- calls {@link SignalingEventHandler#onError(String)}</li>
     * </ul>
     *
     * <p>If the URI is malformed or the connection attempt throws an exception,
     * the error is reported via {@link SignalingEventHandler#onError(String)}.</p>
     *
     * @param url     the WebSocket URL of the signaling server
     *                (e.g. {@code "ws://localhost:9090"})
     * @param handler callbacks for WebSocket lifecycle events
     */
    public void connect(String url, final SignalingEventHandler handler) {
        try {
            ws = new WebSocketClient(new URI(url)) {
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
            Thread wsThread = new Thread(ws, "webrtc-signaling");
            wsThread.setDaemon(true);
            wsThread.start();
        } catch (Exception e) {
            handler.onError("Failed to connect: " + e.getMessage());
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Serializes the message to JSON via {@link SignalMessage#toJson()} and
     * sends it over the WebSocket. This method is a no-op if the WebSocket
     * is {@code null} or not currently open.</p>
     *
     * @param msg the signaling message to send
     */
    public void send(SignalMessage msg) {
        if (ws != null && ws.isOpen()) {
            ws.send(msg.toJson());
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Closes the underlying {@link WebSocketClient} and sets the internal
     * reference to {@code null}. Any exceptions thrown during close are silently
     * ignored. After this call, {@link #isOpen()} returns {@code false} and
     * {@link #send(SignalMessage)} becomes a no-op.</p>
     */
    public void close() {
        if (ws != null) {
            try {
                ws.close();
            } catch (Exception e) {
                // Ignore close errors
            }
            ws = null;
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns {@code true} if the internal {@link WebSocketClient} is non-null
     * and its {@link WebSocketClient#isOpen()} method returns {@code true}.</p>
     *
     * @return {@code true} if the WebSocket connection is open
     */
    public boolean isOpen() {
        return ws != null && ws.isOpen();
    }
}
