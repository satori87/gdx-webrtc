package com.github.satori87.gdx.webrtc.ios.bindings;

import org.robovm.apple.foundation.NSObject;
import org.robovm.objc.annotation.Method;

/**
 * RoboVM Java adapter for the Objective-C {@code RTCDataChannelDelegate} protocol
 * from Apple's WebRTC.framework.
 *
 * <p>This class provides no-op default implementations for every delegate method.
 * Subclass it and override only the callbacks you need. The two primary callbacks
 * used by gdx-webrtc are:</p>
 * <ul>
 *   <li>{@link #dataChannelDidChangeState} -- detect when a channel opens or closes</li>
 *   <li>{@link #didReceiveMessage} -- receive incoming data from the remote peer</li>
 * </ul>
 *
 * <p>Each method is annotated with the corresponding Objective-C selector so that
 * RoboVM can dispatch native delegate callbacks into Java.</p>
 *
 * @see RTCDataChannel
 * @see RTCDataChannel#setDelegate(RTCDataChannelDelegate)
 */
public class RTCDataChannelDelegate extends NSObject {

    /**
     * Called when the data channel's state changes (e.g., connecting, open, closing, closed).
     * Check the channel's {@link RTCDataChannel#getReadyState()} to determine the new state,
     * comparing against constants in {@link RTCDataChannelState}.
     *
     * @param dataChannel the data channel whose state changed
     */
    @Method(selector = "dataChannelDidChangeState:")
    public void dataChannelDidChangeState(RTCDataChannel dataChannel) {}

    /**
     * Called when a message is received on the data channel.
     * Use {@link RTCDataBuffer#getBytes()} to extract the raw byte payload.
     *
     * @param dataChannel the data channel that received the message
     * @param buffer      the received data buffer containing the message payload
     */
    @Method(selector = "dataChannel:didReceiveMessageWithBuffer:")
    public void didReceiveMessage(RTCDataChannel dataChannel, RTCDataBuffer buffer) {}

    /**
     * Called when the data channel's buffered amount changes.
     * This can be used to implement flow control by monitoring outgoing buffer pressure.
     *
     * @param dataChannel the data channel whose buffer changed
     * @param amount      the current amount of data (in bytes) buffered for sending
     */
    @Method(selector = "dataChannel:didChangeBufferedAmount:")
    public void didChangeBufferedAmount(RTCDataChannel dataChannel, long amount) {}
}
