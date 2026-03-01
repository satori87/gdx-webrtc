package com.github.satori87.gdx.webrtc.ios.bindings;

import org.robovm.apple.foundation.NSObject;
import org.robovm.objc.annotation.NativeClass;
import org.robovm.objc.annotation.Property;

/**
 * RoboVM Java binding for the Objective-C {@code RTCDataChannelConfiguration} class
 * from Apple's WebRTC.framework.
 *
 * <p>An RTCDataChannelConfiguration specifies the behavior of an {@link RTCDataChannel},
 * including whether messages are delivered in order and the maximum number of
 * retransmission attempts for unreliable delivery.</p>
 *
 * <p>gdx-webrtc uses two standard configurations:</p>
 * <ul>
 *   <li><b>Reliable</b> -- ordered delivery with unlimited retransmits
 *       (see {@link #createReliable()})</li>
 *   <li><b>Unreliable</b> -- unordered delivery with zero retransmits
 *       (see {@link #createUnreliable()})</li>
 * </ul>
 *
 * @see RTCDataChannel
 * @see RTCPeerConnection#createDataChannel(String, RTCDataChannelConfiguration)
 */
@NativeClass
public class RTCDataChannelConfiguration extends NSObject {

    /**
     * Returns whether messages on this channel are delivered in order.
     *
     * @return {@code true} if ordered delivery is enabled
     */
    @Property(selector = "isOrdered")
    public native boolean isOrdered();

    /**
     * Sets whether messages on this channel should be delivered in order.
     *
     * @param ordered {@code true} for ordered delivery, {@code false} for unordered
     */
    @Property(selector = "setIsOrdered:")
    public native void setIsOrdered(boolean ordered);

    /**
     * Returns the maximum number of retransmission attempts for this channel.
     *
     * <p>A value of zero means no retransmission (fire-and-forget). The default
     * value (when not explicitly set) allows unlimited retransmits.</p>
     *
     * @return the maximum number of retransmits
     */
    @Property(selector = "maxRetransmits")
    public native int getMaxRetransmits();

    /**
     * Sets the maximum number of retransmission attempts for this channel.
     *
     * @param maxRetransmits the maximum retransmit count (0 for fire-and-forget)
     */
    @Property(selector = "setMaxRetransmits:")
    public native void setMaxRetransmits(int maxRetransmits);

    /**
     * Creates a configuration for a reliable data channel.
     *
     * <p>The channel will deliver messages in order with unlimited retransmits,
     * providing TCP-like reliability over the underlying SCTP transport.</p>
     *
     * @return a new reliable channel configuration
     */
    public static RTCDataChannelConfiguration createReliable() {
        RTCDataChannelConfiguration config = new RTCDataChannelConfiguration();
        config.setIsOrdered(true);
        return config;
    }

    /**
     * Creates a configuration for an unreliable data channel.
     *
     * <p>The channel will deliver messages unordered with zero retransmits,
     * providing UDP-like fire-and-forget semantics suitable for real-time
     * game data where latency matters more than reliability.</p>
     *
     * @return a new unreliable channel configuration
     */
    public static RTCDataChannelConfiguration createUnreliable() {
        RTCDataChannelConfiguration config = new RTCDataChannelConfiguration();
        config.setIsOrdered(false);
        config.setMaxRetransmits(0);
        return config;
    }
}
