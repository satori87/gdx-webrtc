package com.github.satori87.gdx.webrtc.ios.bindings;

import org.robovm.apple.foundation.NSArray;
import org.robovm.apple.foundation.NSObject;
import org.robovm.objc.annotation.NativeClass;
import org.robovm.objc.annotation.Property;

/** Binding for WebRTC's RTCConfiguration. */
@NativeClass
public class RTCConfiguration extends NSObject {

    @Property(selector = "iceServers")
    public native NSArray<RTCIceServer> getIceServers();

    @Property(selector = "setIceServers:")
    public native void setIceServers(NSArray<RTCIceServer> iceServers);

    @Property(selector = "iceTransportPolicy")
    public native int getIceTransportPolicy();

    @Property(selector = "setIceTransportPolicy:")
    public native void setIceTransportPolicy(int policy);

    public static RTCConfiguration create() {
        return new RTCConfiguration();
    }
}
