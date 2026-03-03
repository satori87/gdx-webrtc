package com.github.satori87.gdx.webrtc;

/**
 * Configuration for WebRTC connections and signaling.
 *
 * <p>This class holds all configurable parameters for a {@link WebRTCClient},
 * including signaling server URL, ICE server settings, data channel options,
 * and ICE restart behavior.</p>
 *
 * <p>All fields have sensible defaults. At minimum, {@link #signalingServerUrl}
 * must be set before connecting:</p>
 * <pre>
 * WebRTCConfiguration config = new WebRTCConfiguration();
 * config.signalingServerUrl = "ws://myserver.com:9090";
 *
 * // Optional: configure TURN server for NAT traversal
 * config.turnServer = "turn:myserver.com:3478";
 * config.turnUsername = "user";
 * config.turnPassword = "pass";
 *
 * // Optional: tune ICE restart behavior
 * config.setIceRestartDelayMs(5000);
 * config.setMaxIceRestartAttempts(5);
 * </pre>
 *
 * <p>Fields are public for simple access, and getters/setters are provided
 * for framework compatibility.</p>
 *
 * @see WebRTCClient
 * @see WebRTCClients#newClient(WebRTCConfiguration, WebRTCClientListener)
 */
public class WebRTCConfiguration {

    /**
     * Default delay in milliseconds before restarting ICE after a temporary
     * disconnect (DISCONNECTED state). Value: 3500 ms.
     */
    public static final int DEFAULT_ICE_RESTART_DELAY_MS = 3500;

    /**
     * Default maximum number of ICE restart attempts after entering the FAILED
     * state. After this many attempts, {@link WebRTCClientListener#onDisconnected(WebRTCPeer)}
     * is called. Value: 3.
     */
    public static final int DEFAULT_MAX_ICE_RESTART_ATTEMPTS = 3;

    /**
     * Default send buffer limit in bytes for the unreliable data channel.
     * Packets are silently dropped when the buffered amount exceeds this value.
     * Value: 65536 (64 KB).
     */
    public static final long DEFAULT_UNRELIABLE_BUFFER_LIMIT = 65536;

    /**
     * Default base delay in milliseconds for ICE restart exponential backoff.
     * The actual delay doubles with each successive attempt (2s, 4s, 8s, ...).
     * Value: 2000 ms.
     */
    public static final int DEFAULT_ICE_BACKOFF_BASE_MS = 2000;

    /**
     * Default {@code maxRetransmits} value for the unreliable data channel.
     * 0 means fire-and-forget (no retransmissions). Value: 0.
     */
    public static final int DEFAULT_UNRELIABLE_MAX_RETRANSMITS = 0;

    /**
     * Default STUN server URLs for NAT traversal, queried simultaneously for redundancy.
     * Includes Google primary, Google secondary, and Cloudflare public STUN servers.
     */
    public static final String[] DEFAULT_STUN_SERVERS = new String[] {
        "stun:stun.l.google.com:19302",
        "stun:stun1.l.google.com:19302",
        "stun:stun.cloudflare.com:3478"
    };

    /**
     * URL of the signaling server WebSocket endpoint.
     * Required. Example: {@code "ws://localhost:9090"} or {@code "wss://myserver.com:9090"}.
     */
    public String signalingServerUrl;

    /**
     * STUN server URL used for NAT traversal and public IP discovery.
     * Default: {@code "stun:stun.l.google.com:19302"} (Google's public STUN server).
     *
     * @deprecated Use {@link #stunServers} instead for multi-server redundancy.
     */
    public String stunServer = "stun:stun.l.google.com:19302";

    /**
     * STUN server URLs used for NAT traversal and public IP discovery.
     * Multiple servers provide redundancy — WebRTC queries all simultaneously
     * during ICE gathering, so if one is down the others still respond.
     *
     * <p>Default: Google primary, Google secondary, and Cloudflare public STUN servers.</p>
     *
     * @see #DEFAULT_STUN_SERVERS
     */
    public String[] stunServers = DEFAULT_STUN_SERVERS;

    /**
     * Optional TURN server URL for relaying traffic when direct peer-to-peer
     * connections are not possible. Example: {@code "turn:myserver.com:3478"}.
     * Default: {@code null} (no TURN server).
     */
    public String turnServer;

    /**
     * Username for TURN server authentication. Only used when {@link #turnServer} is set.
     */
    public String turnUsername;

    /**
     * Password for TURN server authentication. Only used when {@link #turnServer} is set.
     */
    public String turnPassword;

    /**
     * When {@code true}, forces all ICE traffic through the TURN relay server,
     * disabling direct peer-to-peer connections. Useful for testing TURN server
     * configurations. Default: {@code false}.
     */
    public boolean forceRelay = false;

    /**
     * Delay in milliseconds before restarting ICE after a temporary disconnect
     * (ICE DISCONNECTED state). This gives the connection a chance to recover
     * naturally before triggering an explicit ICE restart.
     *
     * @see #DEFAULT_ICE_RESTART_DELAY_MS
     */
    public int iceRestartDelayMs = DEFAULT_ICE_RESTART_DELAY_MS;

    /**
     * Maximum number of ICE restart attempts after entering the FAILED state.
     * Each attempt uses exponential backoff based on {@link #iceBackoffBaseMs}.
     * When this limit is exceeded, the peer is considered permanently disconnected.
     *
     * @see #DEFAULT_MAX_ICE_RESTART_ATTEMPTS
     */
    public int maxIceRestartAttempts = DEFAULT_MAX_ICE_RESTART_ATTEMPTS;

    /**
     * Send buffer limit in bytes for the unreliable data channel.
     * When the channel's buffered amount exceeds this value,
     * {@link WebRTCPeer#sendUnreliable(byte[])} silently drops the packet
     * to prevent buffer bloat and increased latency.
     *
     * @see #DEFAULT_UNRELIABLE_BUFFER_LIMIT
     */
    public long unreliableBufferLimit = DEFAULT_UNRELIABLE_BUFFER_LIMIT;

    /**
     * Base delay in milliseconds for exponential backoff when ICE enters
     * the FAILED state. The actual delay for attempt N is
     * {@code iceBackoffBaseMs * 2^(N-1)} (e.g. 2000, 4000, 8000 ms).
     *
     * @see #DEFAULT_ICE_BACKOFF_BASE_MS
     */
    public int iceBackoffBaseMs = DEFAULT_ICE_BACKOFF_BASE_MS;

    /**
     * The {@code maxRetransmits} value for the unreliable data channel.
     * 0 means fire-and-forget with no retransmissions.
     * Higher values allow limited retransmission for semi-reliable delivery.
     *
     * @see #DEFAULT_UNRELIABLE_MAX_RETRANSMITS
     */
    public int unreliableMaxRetransmits = DEFAULT_UNRELIABLE_MAX_RETRANSMITS;

    /** @return the ICE restart delay in milliseconds */
    public int getIceRestartDelayMs() { return iceRestartDelayMs; }

    /** @param iceRestartDelayMs the ICE restart delay in milliseconds */
    public void setIceRestartDelayMs(int iceRestartDelayMs) { this.iceRestartDelayMs = iceRestartDelayMs; }

    /** @return the maximum number of ICE restart attempts */
    public int getMaxIceRestartAttempts() { return maxIceRestartAttempts; }

    /** @param maxIceRestartAttempts the maximum number of ICE restart attempts */
    public void setMaxIceRestartAttempts(int maxIceRestartAttempts) { this.maxIceRestartAttempts = maxIceRestartAttempts; }

    /** @return the unreliable channel buffer limit in bytes */
    public long getUnreliableBufferLimit() { return unreliableBufferLimit; }

    /** @param unreliableBufferLimit the unreliable channel buffer limit in bytes */
    public void setUnreliableBufferLimit(long unreliableBufferLimit) { this.unreliableBufferLimit = unreliableBufferLimit; }

    /** @return the ICE backoff base delay in milliseconds */
    public int getIceBackoffBaseMs() { return iceBackoffBaseMs; }

    /** @param iceBackoffBaseMs the ICE backoff base delay in milliseconds */
    public void setIceBackoffBaseMs(int iceBackoffBaseMs) { this.iceBackoffBaseMs = iceBackoffBaseMs; }

    /** @return the maxRetransmits value for the unreliable data channel */
    public int getUnreliableMaxRetransmits() { return unreliableMaxRetransmits; }

    /** @param unreliableMaxRetransmits the maxRetransmits value for the unreliable data channel */
    public void setUnreliableMaxRetransmits(int unreliableMaxRetransmits) { this.unreliableMaxRetransmits = unreliableMaxRetransmits; }

    /**
     * Convenience method to set a single custom STUN server, replacing the default list.
     *
     * @param url the STUN server URL (e.g. {@code "stun:stun.example.com:3478"})
     */
    public void setStunServer(String url) {
        this.stunServer = url;
        this.stunServers = new String[] { url };
    }
}
