package com.github.satori87.gdx.webrtc.server.turn;

/**
 * Configuration holder for the embedded {@link TurnServer}.
 *
 * <p>This class contains all settings needed to start the TURN server,
 * including the network bind address/port, long-term credential parameters
 * (realm, username, password), and operational parameters like cleanup
 * intervals, buffer sizes, and allocation lifetime limits.</p>
 *
 * <p>All fields have sensible defaults via {@code DEFAULT_*} constants
 * and can be modified directly before passing the configuration to the
 * {@link TurnServer} constructor. Fields can also be set via command-line
 * arguments when using
 * {@link com.github.satori87.gdx.webrtc.server.SignalingMain}.</p>
 *
 * @see TurnServer
 * @see com.github.satori87.gdx.webrtc.server.SignalingMain
 */
public class TurnConfig {

    /** Default public hostname/IP. Value: {@code "0.0.0.0"} (bind to all interfaces, auto-detect public IP). */
    public static final String DEFAULT_HOST = "0.0.0.0";

    /** Default UDP port for TURN. Value: 3478 (standard TURN port per RFC 5766). */
    public static final int DEFAULT_PORT = 3478;

    /** Default TURN realm for long-term credentials. Value: {@code "webrtc"}. */
    public static final String DEFAULT_REALM = "webrtc";

    /** Default username for long-term credentials. Value: {@code "webrtc"}. */
    public static final String DEFAULT_USERNAME = "webrtc";

    /** Default password for long-term credentials. Value: {@code "webrtc"}. */
    public static final String DEFAULT_PASSWORD = "webrtc";

    /** Default interval in milliseconds between allocation cleanup sweeps. Value: 30000 (30 seconds). */
    public static final int DEFAULT_CLEANUP_INTERVAL_MS = 30000;

    /** Default socket read timeout in milliseconds for relay listeners. Value: 5000 (5 seconds). */
    public static final int DEFAULT_RELAY_TIMEOUT_MS = 5000;

    /** Default UDP receive buffer size in bytes. Value: 65536 (64 KB). */
    public static final int DEFAULT_RECEIVE_BUFFER_SIZE = 65536;

    /** Default minimum allocation lifetime in seconds. Value: 60 (1 minute). */
    public static final int DEFAULT_MIN_LIFETIME = 60;

    /**
     * Public hostname or IP address that clients use to reach this TURN
     * server. When set to {@code "0.0.0.0"} (the default), the server
     * binds to all interfaces and attempts to auto-detect the public IP
     * for relay address reporting.
     */
    public String host = DEFAULT_HOST;

    /**
     * UDP port on which the TURN server listens. The IANA-assigned default
     * for TURN is 3478 (RFC 5766 Section 5).
     */
    public int port = DEFAULT_PORT;

    /**
     * TURN realm string used for long-term credential authentication
     * (RFC 5389 Section 10.2). Included in REALM attributes of
     * 401/438 error responses.
     */
    public String realm = DEFAULT_REALM;

    /**
     * Fixed username for long-term credential authentication.
     * Clients must present this username in their STUN requests.
     */
    public String username = DEFAULT_USERNAME;

    /**
     * Fixed password for long-term credential authentication.
     * Used together with {@link #username} and {@link #realm} to compute
     * the MESSAGE-INTEGRITY key via
     * {@link StunMessage#computeKey(String, String, String)}.
     */
    public String password = DEFAULT_PASSWORD;

    /**
     * Interval in milliseconds between allocation cleanup sweeps.
     * The cleanup thread removes expired allocations at this interval.
     */
    public int cleanupIntervalMs = DEFAULT_CLEANUP_INTERVAL_MS;

    /**
     * Socket read timeout in milliseconds for relay listener threads.
     * Each relay socket uses this timeout to periodically check for
     * allocation expiry.
     */
    public int relayTimeoutMs = DEFAULT_RELAY_TIMEOUT_MS;

    /** UDP receive buffer size in bytes for the main and relay sockets. */
    public int receiveBufferSize = DEFAULT_RECEIVE_BUFFER_SIZE;

    /**
     * Minimum allocation lifetime in seconds. Clients cannot request
     * a lifetime shorter than this value (per RFC 5766).
     */
    public int minLifetime = DEFAULT_MIN_LIFETIME;
}
