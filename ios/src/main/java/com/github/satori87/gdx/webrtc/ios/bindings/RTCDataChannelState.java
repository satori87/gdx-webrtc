package com.github.satori87.gdx.webrtc.ios.bindings;

/**
 * Java integer constants mirroring the native {@code RTCDataChannelState} enumeration
 * from Apple's WebRTC.framework.
 *
 * <p>These constants represent the lifecycle state of an {@link RTCDataChannel}.
 * They are returned by {@link RTCDataChannel#getReadyState()} and delivered to
 * {@link RTCDataChannelDelegate#dataChannelDidChangeState}.</p>
 *
 * <p>This is a RoboVM binding utility class; the values correspond 1:1 to the
 * Objective-C {@code RTCDataChannelState} enum ordinals.</p>
 *
 * @see RTCDataChannel#getReadyState()
 * @see RTCDataChannelDelegate#dataChannelDidChangeState(RTCDataChannel)
 */
public final class RTCDataChannelState {
    /** The data channel is being set up and is not yet ready for use. */
    public static final int CONNECTING = 0;
    /** The data channel is open and ready to send and receive messages. */
    public static final int OPEN = 1;
    /** The data channel is in the process of closing. */
    public static final int CLOSING = 2;
    /** The data channel has been closed; no further data can be sent or received. */
    public static final int CLOSED = 3;

    /** Private constructor to prevent instantiation of this constants-only class. */
    private RTCDataChannelState() {}
}
