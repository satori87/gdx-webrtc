package com.github.satori87.gdx.webrtc.ios.bindings;

import org.robovm.apple.foundation.NSObject;
import org.robovm.objc.annotation.Method;

/**
 * Adapter base class for the RTCDataChannelDelegate protocol.
 * Subclass this and override the methods you care about.
 */
public class RTCDataChannelDelegate extends NSObject {

    @Method(selector = "dataChannelDidChangeState:")
    public void dataChannelDidChangeState(RTCDataChannel dataChannel) {}

    @Method(selector = "dataChannel:didReceiveMessageWithBuffer:")
    public void didReceiveMessage(RTCDataChannel dataChannel, RTCDataBuffer buffer) {}

    @Method(selector = "dataChannel:didChangeBufferedAmount:")
    public void didChangeBufferedAmount(RTCDataChannel dataChannel, long amount) {}
}
