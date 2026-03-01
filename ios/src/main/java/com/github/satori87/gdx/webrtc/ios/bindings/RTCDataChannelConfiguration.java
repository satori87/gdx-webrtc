package com.github.satori87.gdx.webrtc.ios.bindings;

import org.robovm.apple.foundation.NSObject;
import org.robovm.objc.annotation.NativeClass;
import org.robovm.objc.annotation.Property;

/** Binding for WebRTC's RTCDataChannelConfiguration. */
@NativeClass
public class RTCDataChannelConfiguration extends NSObject {

    @Property(selector = "isOrdered")
    public native boolean isOrdered();

    @Property(selector = "setIsOrdered:")
    public native void setIsOrdered(boolean ordered);

    @Property(selector = "maxRetransmits")
    public native int getMaxRetransmits();

    @Property(selector = "setMaxRetransmits:")
    public native void setMaxRetransmits(int maxRetransmits);

    public static RTCDataChannelConfiguration createReliable() {
        RTCDataChannelConfiguration config = new RTCDataChannelConfiguration();
        config.setIsOrdered(true);
        return config;
    }

    public static RTCDataChannelConfiguration createUnreliable() {
        RTCDataChannelConfiguration config = new RTCDataChannelConfiguration();
        config.setIsOrdered(false);
        config.setMaxRetransmits(0);
        return config;
    }
}
