package com.github.satori87.gdx.webrtc.ios.bindings;

/**
 * Java integer constants mirroring the native {@code RTCIceTransportPolicy} enumeration
 * from Apple's WebRTC.framework.
 *
 * <p>These constants control which ICE candidate types are permitted when establishing
 * a peer connection. They are set on {@link RTCConfiguration#setIceTransportPolicy(int)}
 * to restrict the ICE transport behavior.</p>
 *
 * <p>This is a RoboVM binding utility class; the values correspond 1:1 to the
 * Objective-C {@code RTCIceTransportPolicy} enum ordinals.</p>
 *
 * @see RTCConfiguration#setIceTransportPolicy(int)
 */
public final class RTCIceTransportPolicy {
    /** No ICE candidates will be gathered. */
    public static final int NONE = 0;
    /** Only relay (TURN) candidates are used; direct and server-reflexive candidates are excluded. */
    public static final int RELAY = 1;
    /** Host candidates are excluded; only server-reflexive and relay candidates are used. */
    public static final int NO_HOST = 2;
    /** All candidate types are permitted (default). */
    public static final int ALL = 3;

    /** Private constructor to prevent instantiation of this constants-only class. */
    private RTCIceTransportPolicy() {}
}
