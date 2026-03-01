package com.github.satori87.gdx.webrtc.server;

/**
 * Configuration for the {@link WebRTCSignalingServer}.
 *
 * <p>All fields have sensible defaults via {@code DEFAULT_*} constants.
 * Fields are public for simple access.</p>
 *
 * @see WebRTCSignalingServer
 */
public class SignalingServerConfig {

    /** Default WebSocket port for the signaling server. Value: 9090. */
    public static final int DEFAULT_PORT = 9090;

    /**
     * Default connection-lost timeout in seconds. The WebSocket server uses this
     * to detect and clean up stale connections via ping/pong. Value: 30.
     */
    public static final int DEFAULT_CONNECTION_LOST_TIMEOUT = 30;

    /**
     * Default timeout in milliseconds for graceful server shutdown.
     * Value: 1000 (1 second).
     */
    public static final int DEFAULT_STOP_TIMEOUT_MS = 1000;

    /** The TCP port on which the WebSocket signaling server listens. */
    public int port = DEFAULT_PORT;

    /**
     * Connection-lost timeout in seconds. The WebSocket server sends periodic
     * pings and considers a connection lost if no pong is received within this
     * duration.
     */
    public int connectionLostTimeout = DEFAULT_CONNECTION_LOST_TIMEOUT;

    /**
     * Timeout in milliseconds for graceful server shutdown. The server waits
     * up to this duration for active connections to close before forcing
     * termination.
     */
    public int stopTimeoutMs = DEFAULT_STOP_TIMEOUT_MS;
}
