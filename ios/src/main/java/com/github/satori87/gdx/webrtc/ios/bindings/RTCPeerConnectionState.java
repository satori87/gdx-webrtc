package com.github.satori87.gdx.webrtc.ios.bindings;

/**
 * Java integer constants mirroring the native {@code RTCPeerConnectionState} enumeration
 * from Apple's WebRTC.framework.
 *
 * <p>These constants represent the overall connection state of an {@link RTCPeerConnection}.
 * They are delivered to {@link RTCPeerConnectionDelegate#didChangeConnectionState} and
 * are mapped to the platform-agnostic {@code ConnectionState} values by
 * {@code IOSPeerConnectionProvider}.</p>
 *
 * <p>This is a RoboVM binding utility class; the values correspond 1:1 to the
 * Objective-C {@code RTCPeerConnectionState} enum ordinals.</p>
 *
 * @see RTCPeerConnectionDelegate#didChangeConnectionState(RTCPeerConnection, int)
 */
public final class RTCPeerConnectionState {
    /** The peer connection has been created but no networking has started. */
    public static final int NEW = 0;
    /** One or more ICE transports are currently establishing a connection. */
    public static final int CONNECTING = 1;
    /** All ICE transports are connected and data can flow. */
    public static final int CONNECTED = 2;
    /** One or more ICE transports have become disconnected unexpectedly. */
    public static final int DISCONNECTED = 3;
    /** One or more ICE transports have failed; the connection cannot be recovered without an ICE restart. */
    public static final int FAILED = 4;
    /** The peer connection has been closed. */
    public static final int CLOSED = 5;

    /** Private constructor to prevent instantiation of this constants-only class. */
    private RTCPeerConnectionState() {}
}
