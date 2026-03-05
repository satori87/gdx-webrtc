package com.github.satori87.gdx.webrtc;

/**
 * Normalized connection state constants for WebRTC peer connections.
 *
 * <p>Platform implementations must map their native connection state types
 * to these constants when calling
 * {@link PeerEventHandler#onConnectionStateChanged(int)}. For example:</p>
 * <ul>
 *   <li>Desktop (webrtc-java): maps {@code RTCPeerConnectionState} enum values</li>
 *   <li>Browser (TeaVM): maps JavaScript state strings ("connected", "failed", etc.)</li>
 *   <li>Android: maps {@code PeerConnection.PeerConnectionState} enum values</li>
 *   <li>iOS (RoboVM): maps {@code RTCPeerConnectionState} integer constants</li>
 * </ul>
 *
 * <p>The ICE state machine in {@link BaseWebRTCClient} uses these constants to
 * drive connection recovery behavior:</p>
 * <ul>
 *   <li>{@link #CONNECTED} - resets ICE restart counters and timers</li>
 *   <li>{@link #DISCONNECTED} - schedules a delayed ICE restart</li>
 *   <li>{@link #FAILED} - triggers exponential backoff ICE restart</li>
 *   <li>{@link #CLOSED} - fires permanent disconnect notification</li>
 * </ul>
 *
 * @see PeerEventHandler#onConnectionStateChanged(int)
 */
public final class ConnectionState {

    /** The peer connection has been created but not yet started. */
    public static final int NEW = 0;

    /** The peer connection is in the process of establishing a connection. */
    public static final int CONNECTING = 1;

    /** The peer connection is fully established and data can be exchanged. */
    public static final int CONNECTED = 2;

    /**
     * The peer connection has temporarily lost connectivity.
     * An automatic ICE restart is scheduled after the configured delay.
     *
     * @see WebRTCConfiguration#iceRestartDelayMs
     */
    public static final int DISCONNECTED = 3;

    /**
     * The peer connection has failed. ICE restart with exponential backoff is attempted.
     * If the maximum number of attempts is exceeded, the peer is permanently disconnected.
     *
     * @see WebRTCConfiguration#maxIceRestartAttempts
     * @see WebRTCConfiguration#iceBackoffBaseMs
     */
    public static final int FAILED = 4;

    /** The peer connection has been permanently closed. */
    public static final int CLOSED = 5;

    /**
     * Returns a human-readable name for the given connection state constant.
     *
     * @param state one of the {@code ConnectionState} constants
     * @return a string such as {@code "CONNECTED"}, {@code "FAILED"}, etc.;
     *         returns {@code "UNKNOWN(N)"} for unrecognized values
     */
    public static String toString(int state) {
        switch (state) {
            case NEW: return "NEW";
            case CONNECTING: return "CONNECTING";
            case CONNECTED: return "CONNECTED";
            case DISCONNECTED: return "DISCONNECTED";
            case FAILED: return "FAILED";
            case CLOSED: return "CLOSED";
            default: return "UNKNOWN(" + state + ")";
        }
    }

    private ConnectionState() {}
}
