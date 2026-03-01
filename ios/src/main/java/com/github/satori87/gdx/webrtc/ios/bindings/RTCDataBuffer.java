package com.github.satori87.gdx.webrtc.ios.bindings;

import org.robovm.apple.foundation.NSData;
import org.robovm.apple.foundation.NSObject;
import org.robovm.objc.annotation.Method;
import org.robovm.objc.annotation.NativeClass;
import org.robovm.objc.annotation.Property;

/** Binding for WebRTC's RTCDataBuffer. */
@NativeClass
public class RTCDataBuffer extends NSObject {

    @Method(selector = "initWithData:isBinary:")
    protected native long initWithData(NSData data, boolean isBinary);

    @Property(selector = "data")
    public native NSData getData();

    @Property(selector = "isBinary")
    public native boolean isBinary();

    public static RTCDataBuffer create(byte[] bytes) {
        NSData data = new NSData(bytes);
        RTCDataBuffer buffer = new RTCDataBuffer();
        buffer.initWithData(data, true);
        return buffer;
    }

    /** Extract the buffer contents as a byte array. */
    public byte[] getBytes() {
        NSData data = getData();
        if (data == null) return new byte[0];
        return data.getBytes();
    }
}
