package com.github.satori87.gdx.webrtc.ios.bindings;

import org.robovm.apple.foundation.NSObject;
import org.robovm.objc.annotation.Method;
import org.robovm.objc.annotation.NativeClass;
import org.robovm.objc.annotation.Property;

/**
 * RoboVM Java binding for the Objective-C {@code RTCDataChannel} class
 * from Apple's WebRTC.framework.
 *
 * <p>An RTCDataChannel represents a bidirectional data channel between two peers.
 * Each peer connection in gdx-webrtc uses two data channels: one reliable (ordered,
 * unlimited retransmits) and one unreliable (unordered, zero retransmits).</p>
 *
 * <p>State changes and incoming messages are delivered via the
 * {@link RTCDataChannelDelegate} set on this channel.</p>
 *
 * @see RTCDataChannelDelegate
 * @see RTCDataChannelState
 * @see RTCPeerConnection#createDataChannel(String, RTCDataChannelConfiguration)
 */
@NativeClass
public class RTCDataChannel extends NSObject {

    /**
     * Returns the label assigned to this data channel when it was created.
     *
     * @return the channel label (e.g. "reliable" or "unreliable")
     */
    @Property(selector = "label")
    public native String getLabel();

    /**
     * Returns the current ready state of this data channel.
     *
     * <p>The returned integer corresponds to one of the constants in
     * {@link RTCDataChannelState} (CONNECTING, OPEN, CLOSING, CLOSED).</p>
     *
     * @return the ready state as a native enum ordinal
     * @see RTCDataChannelState
     */
    @Property(selector = "readyState")
    public native int getReadyState();

    /**
     * Returns the number of bytes currently queued for sending on this channel.
     *
     * <p>Used by the unreliable send logic to check buffer pressure before
     * sending additional packets. If the buffered amount exceeds the configured
     * limit, unreliable packets are silently dropped.</p>
     *
     * @return the buffered amount in bytes
     */
    @Property(selector = "bufferedAmount")
    public native long getBufferedAmount();

    /**
     * Returns the delegate currently receiving callbacks for this data channel.
     *
     * @return the current delegate, or {@code null} if none is set
     */
    @Property(selector = "delegate")
    public native RTCDataChannelDelegate getDelegate();

    /**
     * Sets the delegate that will receive state change and message callbacks
     * for this data channel.
     *
     * @param delegate the delegate to receive callbacks, or {@code null} to remove
     * @see RTCDataChannelDelegate
     */
    @Property(selector = "setDelegate:")
    public native void setDelegate(RTCDataChannelDelegate delegate);

    /**
     * Sends a data buffer over this channel.
     *
     * @param buffer the data buffer to send
     * @return {@code true} if the data was successfully queued for sending
     * @see RTCDataBuffer
     */
    @Method(selector = "sendData:")
    public native boolean sendData(RTCDataBuffer buffer);

    /**
     * Closes this data channel. After closing, no further data can be sent
     * or received on this channel.
     */
    @Method(selector = "close")
    public native void close();
}
