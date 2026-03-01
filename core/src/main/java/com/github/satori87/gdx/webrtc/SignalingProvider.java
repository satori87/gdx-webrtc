package com.github.satori87.gdx.webrtc;

/**
 * Strategy interface for platform-specific WebSocket signaling operations.
 *
 * <p>The signaling provider manages the WebSocket connection to the signaling
 * server, which relays SDP offers/answers and ICE candidates between peers.
 * Each platform provides an implementation:</p>
 * <ul>
 *   <li>{@code DesktopSignalingProvider} / {@code AndroidSignalingProvider} /
 *       {@code IOSSignalingProvider} - use Java-WebSocket library</li>
 *   <li>{@code TeaVMSignalingProvider} - uses browser's native WebSocket API
 *       via {@code @JSBody}</li>
 * </ul>
 *
 * @see SignalingEventHandler
 * @see BaseWebRTCClient
 */
public interface SignalingProvider {

    /**
     * Opens a WebSocket connection to the signaling server.
     *
     * <p>The implementation must:</p>
     * <ul>
     *   <li>Create a WebSocket connection to the given URL</li>
     *   <li>Wire {@code onopen} to {@link SignalingEventHandler#onOpen()}</li>
     *   <li>Wire {@code onmessage} to parse JSON via {@link SignalMessage#fromJson(String)}
     *       and call {@link SignalingEventHandler#onMessage(SignalMessage)}</li>
     *   <li>Wire {@code onclose} to {@link SignalingEventHandler#onClose(String)}</li>
     *   <li>Wire {@code onerror} to {@link SignalingEventHandler#onError(String)}</li>
     * </ul>
     *
     * @param url     the WebSocket URL of the signaling server
     *                (e.g. {@code "ws://localhost:9090"})
     * @param handler callbacks for WebSocket lifecycle events
     */
    void connect(String url, SignalingEventHandler handler);

    /**
     * Sends a signaling message to the server.
     *
     * <p>The message is serialized to JSON via {@link SignalMessage#toJson()} and
     * sent over the WebSocket. This method should be a no-op if the WebSocket
     * is not currently open.</p>
     *
     * @param msg the signaling message to send
     */
    void send(SignalMessage msg);

    /**
     * Closes the WebSocket connection to the signaling server.
     *
     * <p>After calling this method, {@link #isOpen()} should return {@code false}.</p>
     */
    void close();

    /**
     * Returns whether the WebSocket connection is currently open.
     *
     * @return {@code true} if the WebSocket is connected and ready to send/receive
     */
    boolean isOpen();
}
