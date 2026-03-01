package com.github.satori87.gdx.webrtc.ios.bindings;

/**
 * Java integer constants mirroring the native {@code RTCSdpType} enumeration
 * from Apple's WebRTC.framework.
 *
 * <p>These constants represent the type of an SDP (Session Description Protocol)
 * message exchanged during WebRTC signaling. They are used with
 * {@link RTCSessionDescription} to indicate whether a description is an offer,
 * a provisional answer, a final answer, or a rollback.</p>
 *
 * <p>This is a RoboVM binding utility class; the values correspond 1:1 to the
 * Objective-C {@code RTCSdpType} enum ordinals.</p>
 *
 * @see RTCSessionDescription
 */
public final class RTCSdpType {
    /** SDP offer -- the initial session description sent by the offerer. */
    public static final int OFFER = 0;
    /** SDP provisional answer -- an intermediate answer before the final one. */
    public static final int PRANSWER = 1;
    /** SDP answer -- the final session description sent by the answerer. */
    public static final int ANSWER = 2;
    /** SDP rollback -- reverts the session description to the previous stable state. */
    public static final int ROLLBACK = 3;

    /** Private constructor to prevent instantiation of this constants-only class. */
    private RTCSdpType() {}
}
