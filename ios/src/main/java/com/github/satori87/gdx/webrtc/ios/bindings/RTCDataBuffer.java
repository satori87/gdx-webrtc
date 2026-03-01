package com.github.satori87.gdx.webrtc.ios.bindings;

import org.robovm.apple.foundation.NSData;
import org.robovm.apple.foundation.NSObject;
import org.robovm.objc.annotation.Method;
import org.robovm.objc.annotation.NativeClass;
import org.robovm.objc.annotation.Property;

/**
 * RoboVM Java binding for the Objective-C {@code RTCDataBuffer} class
 * from Apple's WebRTC.framework.
 *
 * <p>An RTCDataBuffer wraps a binary or text payload for transmission over
 * an {@link RTCDataChannel}. In gdx-webrtc, all data is sent as binary
 * (the {@code isBinary} flag is always {@code true}).</p>
 *
 * @see RTCDataChannel#sendData(RTCDataBuffer)
 * @see RTCDataChannelDelegate#didReceiveMessage(RTCDataChannel, RTCDataBuffer)
 */
@NativeClass
public class RTCDataBuffer extends NSObject {

    /**
     * Initializes this data buffer with the given NSData and binary flag.
     *
     * <p>This is the native Objective-C initializer. Use {@link #create(byte[])}
     * instead for convenience.</p>
     *
     * @param data     the raw data to wrap
     * @param isBinary {@code true} for binary data, {@code false} for text
     * @return the native object pointer
     */
    @Method(selector = "initWithData:isBinary:")
    protected native long initWithData(NSData data, boolean isBinary);

    /**
     * Returns the underlying data as an NSData object.
     *
     * @return the buffer's data, or {@code null} if empty
     */
    @Property(selector = "data")
    public native NSData getData();

    /**
     * Returns whether this buffer contains binary data.
     *
     * @return {@code true} if the data is binary, {@code false} if text
     */
    @Property(selector = "isBinary")
    public native boolean isBinary();

    /**
     * Creates a new binary RTCDataBuffer from a byte array.
     *
     * <p>Wraps the provided bytes in an NSData object and creates a buffer
     * with {@code isBinary} set to {@code true}.</p>
     *
     * @param bytes the raw bytes to send
     * @return a new binary data buffer ready for transmission
     */
    public static RTCDataBuffer create(byte[] bytes) {
        NSData data = new NSData(bytes);
        RTCDataBuffer buffer = new RTCDataBuffer();
        buffer.initWithData(data, true);
        return buffer;
    }

    /**
     * Extracts the buffer contents as a byte array.
     *
     * @return the buffer's bytes, or an empty array if the underlying data is {@code null}
     */
    public byte[] getBytes() {
        NSData data = getData();
        if (data == null) return new byte[0];
        return data.getBytes();
    }
}
