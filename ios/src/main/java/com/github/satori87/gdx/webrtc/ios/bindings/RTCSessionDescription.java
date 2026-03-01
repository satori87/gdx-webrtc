package com.github.satori87.gdx.webrtc.ios.bindings;

import org.robovm.apple.foundation.NSObject;
import org.robovm.objc.annotation.Method;
import org.robovm.objc.annotation.NativeClass;
import org.robovm.objc.annotation.Property;

/**
 * RoboVM Java binding for the Objective-C {@code RTCSessionDescription} class
 * from Apple's WebRTC.framework.
 *
 * <p>An RTCSessionDescription wraps an SDP (Session Description Protocol) string
 * together with its type (offer, answer, provisional answer, or rollback).
 * It is exchanged between peers during the WebRTC signaling handshake.</p>
 *
 * <p>Use the {@link #create(int, String)} factory method to construct instances
 * with the appropriate type from {@link RTCSdpType}.</p>
 *
 * @see RTCSdpType
 * @see RTCPeerConnection#setLocalDescription(RTCSessionDescription, org.robovm.objc.block.VoidBlock1)
 * @see RTCPeerConnection#setRemoteDescription(RTCSessionDescription, org.robovm.objc.block.VoidBlock1)
 */
@NativeClass
public class RTCSessionDescription extends NSObject {

    /**
     * Initializes this session description with the given type and SDP string.
     *
     * <p>This is the native Objective-C initializer. Use {@link #create(int, String)}
     * instead for convenience.</p>
     *
     * @param type the SDP type (one of the {@link RTCSdpType} constants)
     * @param sdp  the SDP string
     * @return the native object pointer
     */
    @Method(selector = "initWithType:sdp:")
    protected native long initWithType(int type, String sdp);

    /**
     * Returns the type of this session description.
     *
     * <p>The returned integer corresponds to one of the constants in
     * {@link RTCSdpType} (OFFER, PRANSWER, ANSWER, ROLLBACK).</p>
     *
     * @return the SDP type as a native enum ordinal
     * @see RTCSdpType
     */
    @Property(selector = "type")
    public native int getType();

    /**
     * Returns the SDP string of this session description.
     *
     * <p>The SDP contains media capabilities, codec preferences, ICE credentials,
     * and other connection parameters exchanged during the signaling handshake.</p>
     *
     * @return the SDP string
     */
    @Property(selector = "sdp")
    public native String getSdp();

    /**
     * Creates a new RTCSessionDescription with the given type and SDP string.
     *
     * @param type the SDP type (one of the {@link RTCSdpType} constants, e.g.
     *             {@link RTCSdpType#OFFER} or {@link RTCSdpType#ANSWER})
     * @param sdp  the SDP string
     * @return a new session description instance
     * @see RTCSdpType
     */
    public static RTCSessionDescription create(int type, String sdp) {
        RTCSessionDescription desc = new RTCSessionDescription();
        desc.initWithType(type, sdp);
        return desc;
    }
}
