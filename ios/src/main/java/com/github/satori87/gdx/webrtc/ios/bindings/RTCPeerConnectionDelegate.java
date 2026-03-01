package com.github.satori87.gdx.webrtc.ios.bindings;

import org.robovm.apple.foundation.NSArray;
import org.robovm.apple.foundation.NSObject;
import org.robovm.objc.annotation.Method;

/**
 * Adapter base class for the RTCPeerConnectionDelegate protocol.
 * Subclass this and override the methods you care about.
 */
public class RTCPeerConnectionDelegate extends NSObject {

    @Method(selector = "peerConnection:didChangeSignalingState:")
    public void didChangeSignalingState(RTCPeerConnection peerConnection, int newState) {}

    @Method(selector = "peerConnection:didAddStream:")
    public void didAddStream(RTCPeerConnection peerConnection, NSObject stream) {}

    @Method(selector = "peerConnection:didRemoveStream:")
    public void didRemoveStream(RTCPeerConnection peerConnection, NSObject stream) {}

    @Method(selector = "peerConnectionShouldNegotiate:")
    public void peerConnectionShouldNegotiate(RTCPeerConnection peerConnection) {}

    @Method(selector = "peerConnection:didChangeIceConnectionState:")
    public void didChangeIceConnectionState(RTCPeerConnection peerConnection, int newState) {}

    @Method(selector = "peerConnection:didChangeIceGatheringState:")
    public void didChangeIceGatheringState(RTCPeerConnection peerConnection, int newState) {}

    @Method(selector = "peerConnection:didGenerateIceCandidate:")
    public void didGenerateIceCandidate(RTCPeerConnection peerConnection, RTCIceCandidate candidate) {}

    @Method(selector = "peerConnection:didRemoveIceCandidates:")
    public void didRemoveIceCandidates(RTCPeerConnection peerConnection, NSArray<?> candidates) {}

    @Method(selector = "peerConnection:didOpenDataChannel:")
    public void didOpenDataChannel(RTCPeerConnection peerConnection, RTCDataChannel dataChannel) {}

    @Method(selector = "peerConnection:didChangeConnectionState:")
    public void didChangeConnectionState(RTCPeerConnection peerConnection, int newState) {}
}
