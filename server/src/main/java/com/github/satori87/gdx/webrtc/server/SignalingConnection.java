package com.github.satori87.gdx.webrtc.server;

import java.net.InetSocketAddress;

/**
 * Thin abstraction over a WebSocket connection for signaling.
 *
 * <p>This interface isolates the signaling server logic from the concrete
 * WebSocket library ({@code org.java_websocket}), enabling unit testing
 * with mock connections that don't require real network I/O.</p>
 *
 * <p>The production implementation ({@code WebSocketSignalingConnection},
 * a private inner class of {@link WebRTCSignalingServer}) wraps a
 * {@code org.java_websocket.WebSocket} instance.</p>
 *
 * @see WebRTCSignalingServer
 */
public interface SignalingConnection {

    /**
     * Sends a text message to the remote end of this connection.
     *
     * @param text the message to send
     */
    void send(String text);

    /**
     * Returns whether this connection is currently open and ready to send/receive.
     *
     * @return {@code true} if the connection is open
     */
    boolean isOpen();

    /**
     * Returns the remote socket address of this connection.
     *
     * @return the remote address, or {@code null} if not available
     */
    InetSocketAddress getRemoteSocketAddress();
}
