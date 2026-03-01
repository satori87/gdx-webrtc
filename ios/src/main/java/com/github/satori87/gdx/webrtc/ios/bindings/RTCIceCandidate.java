package com.github.satori87.gdx.webrtc.ios.bindings;

import org.robovm.apple.foundation.NSObject;
import org.robovm.objc.annotation.Method;
import org.robovm.objc.annotation.NativeClass;
import org.robovm.objc.annotation.Property;

/** Binding for WebRTC's RTCIceCandidate. */
@NativeClass
public class RTCIceCandidate extends NSObject {

    @Method(selector = "initWithSdp:sdpMLineIndex:sdpMid:")
    protected native long initWithSdp(String sdp, int sdpMLineIndex, String sdpMid);

    @Property(selector = "sdp")
    public native String getSdp();

    @Property(selector = "sdpMLineIndex")
    public native int getSdpMLineIndex();

    @Property(selector = "sdpMid")
    public native String getSdpMid();

    public static RTCIceCandidate create(String sdp, int sdpMLineIndex, String sdpMid) {
        RTCIceCandidate candidate = new RTCIceCandidate();
        candidate.initWithSdp(sdp, sdpMLineIndex, sdpMid);
        return candidate;
    }
}
