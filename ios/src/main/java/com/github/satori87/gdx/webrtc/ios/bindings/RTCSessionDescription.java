package com.github.satori87.gdx.webrtc.ios.bindings;

import org.robovm.apple.foundation.NSObject;
import org.robovm.objc.annotation.Method;
import org.robovm.objc.annotation.NativeClass;
import org.robovm.objc.annotation.Property;

/** Binding for WebRTC's RTCSessionDescription. */
@NativeClass
public class RTCSessionDescription extends NSObject {

    @Method(selector = "initWithType:sdp:")
    protected native long initWithType(int type, String sdp);

    @Property(selector = "type")
    public native int getType();

    @Property(selector = "sdp")
    public native String getSdp();

    public static RTCSessionDescription create(int type, String sdp) {
        RTCSessionDescription desc = new RTCSessionDescription();
        desc.initWithType(type, sdp);
        return desc;
    }
}
