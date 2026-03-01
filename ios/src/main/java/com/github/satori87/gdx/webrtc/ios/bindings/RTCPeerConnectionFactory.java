package com.github.satori87.gdx.webrtc.ios.bindings;

import org.robovm.apple.foundation.NSObject;
import org.robovm.objc.annotation.Method;
import org.robovm.objc.annotation.NativeClass;

/** Binding for WebRTC's RTCPeerConnectionFactory. */
@NativeClass
public class RTCPeerConnectionFactory extends NSObject {

    @Method(selector = "peerConnectionWithConfiguration:constraints:delegate:")
    public native RTCPeerConnection createPeerConnection(
            RTCConfiguration configuration,
            RTCMediaConstraints constraints,
            RTCPeerConnectionDelegate delegate);

    public static RTCPeerConnectionFactory create() {
        return new RTCPeerConnectionFactory();
    }
}
