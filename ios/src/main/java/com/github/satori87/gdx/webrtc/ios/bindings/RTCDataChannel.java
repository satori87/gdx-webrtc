package com.github.satori87.gdx.webrtc.ios.bindings;

import org.robovm.apple.foundation.NSObject;
import org.robovm.objc.annotation.Method;
import org.robovm.objc.annotation.NativeClass;
import org.robovm.objc.annotation.Property;

/** Binding for WebRTC's RTCDataChannel. */
@NativeClass
public class RTCDataChannel extends NSObject {

    @Property(selector = "label")
    public native String getLabel();

    @Property(selector = "readyState")
    public native int getReadyState();

    @Property(selector = "bufferedAmount")
    public native long getBufferedAmount();

    @Property(selector = "delegate")
    public native RTCDataChannelDelegate getDelegate();

    @Property(selector = "setDelegate:")
    public native void setDelegate(RTCDataChannelDelegate delegate);

    @Method(selector = "sendData:")
    public native boolean sendData(RTCDataBuffer buffer);

    @Method(selector = "close")
    public native void close();
}
