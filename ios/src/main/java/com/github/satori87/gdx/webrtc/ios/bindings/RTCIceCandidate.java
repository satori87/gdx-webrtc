package com.github.satori87.gdx.webrtc.ios.bindings;

import org.robovm.apple.foundation.NSObject;
import org.robovm.objc.annotation.Method;
import org.robovm.objc.annotation.NativeClass;
import org.robovm.objc.annotation.Property;

/**
 * RoboVM Java binding for the Objective-C {@code RTCIceCandidate} class
 * from Apple's WebRTC.framework.
 *
 * <p>An RTCIceCandidate represents a potential network path (host, server-reflexive,
 * or relay) that can be used to establish a peer connection. ICE candidates are
 * generated locally by the {@link RTCPeerConnection} during the gathering phase
 * and exchanged with the remote peer via the signaling channel.</p>
 *
 * <p>Use the {@link #create(String, int, String)} factory method to construct
 * instances from candidate data received over the signaling channel.</p>
 *
 * @see RTCPeerConnection#addIceCandidate(RTCIceCandidate, org.robovm.objc.block.VoidBlock1)
 * @see RTCPeerConnectionDelegate#didGenerateIceCandidate(RTCPeerConnection, RTCIceCandidate)
 */
@NativeClass
public class RTCIceCandidate extends NSObject {

    /**
     * Initializes this ICE candidate with the given SDP, media line index, and media ID.
     *
     * <p>This is the native Objective-C initializer. Use
     * {@link #create(String, int, String)} instead for convenience.</p>
     *
     * @param sdp           the candidate attribute string from the SDP
     * @param sdpMLineIndex the zero-based index of the media line this candidate belongs to
     * @param sdpMid        the media stream identification tag for this candidate
     * @return the native object pointer
     */
    @Method(selector = "initWithSdp:sdpMLineIndex:sdpMid:")
    protected native long initWithSdp(String sdp, int sdpMLineIndex, String sdpMid);

    /**
     * Returns the SDP candidate attribute string.
     *
     * <p>This contains the candidate's transport address, priority, component,
     * and other ICE attributes as defined in RFC 5245.</p>
     *
     * @return the candidate SDP string
     */
    @Property(selector = "sdp")
    public native String getSdp();

    /**
     * Returns the zero-based index of the SDP media line this candidate belongs to.
     *
     * @return the SDP media line index
     */
    @Property(selector = "sdpMLineIndex")
    public native int getSdpMLineIndex();

    /**
     * Returns the media stream identification tag for this candidate.
     *
     * @return the SDP media ID string
     */
    @Property(selector = "sdpMid")
    public native String getSdpMid();

    /**
     * Creates a new RTCIceCandidate with the given SDP, media line index, and media ID.
     *
     * <p>Typically called when an ICE candidate is received from the remote peer
     * via the signaling channel and needs to be added to the local peer connection.</p>
     *
     * @param sdp           the candidate attribute string from the SDP
     * @param sdpMLineIndex the zero-based index of the media line this candidate belongs to
     * @param sdpMid        the media stream identification tag for this candidate
     * @return a new ICE candidate instance
     */
    public static RTCIceCandidate create(String sdp, int sdpMLineIndex, String sdpMid) {
        RTCIceCandidate candidate = new RTCIceCandidate();
        candidate.initWithSdp(sdp, sdpMLineIndex, sdpMid);
        return candidate;
    }
}
