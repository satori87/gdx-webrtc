package com.github.satori87.gdx.webrtc;

/**
 * Holds opaque handles to the reliable and unreliable data channels created
 * by a {@link PeerConnectionProvider}.
 *
 * <p>The concrete type of the channel handles is platform-specific and known
 * only to the {@link PeerConnectionProvider} implementation. The
 * {@link BaseWebRTCClient} stores these handles and passes them back to the
 * provider for send and query operations.</p>
 *
 * @see PeerConnectionProvider#createDataChannels(Object, int, DataChannelEventHandler)
 */
public class ChannelPair {

    /**
     * Opaque handle to the reliable (ordered, guaranteed delivery) data channel.
     * The concrete type depends on the platform implementation.
     */
    public final Object reliableChannel;

    /**
     * Opaque handle to the unreliable (unordered, fire-and-forget) data channel.
     * The concrete type depends on the platform implementation.
     */
    public final Object unreliableChannel;

    /**
     * Creates a new channel pair.
     *
     * @param reliableChannel   the reliable data channel handle
     * @param unreliableChannel the unreliable data channel handle
     */
    public ChannelPair(Object reliableChannel, Object unreliableChannel) {
        this.reliableChannel = reliableChannel;
        this.unreliableChannel = unreliableChannel;
    }
}
