package com.github.satori87.gdx.webrtc;

/**
 * Callback interface for WebRTC data channel lifecycle and message events.
 *
 * <p>Each peer connection has two data channels (reliable and unreliable), and
 * this handler receives events from both. The {@link BaseWebRTCClient} creates
 * instances of this interface to wire channel events to the appropriate
 * {@link WebRTCClientListener} callbacks.</p>
 *
 * <p>The reliable channel's open/close events drive the peer's connected state:
 * {@link #onReliableOpen()} triggers {@link WebRTCClientListener#onConnected(WebRTCPeer)},
 * and {@link #onReliableClose()} triggers {@link WebRTCClientListener#onDisconnected(WebRTCPeer)}.</p>
 *
 * @see PeerConnectionProvider#createDataChannels(Object, int, DataChannelEventHandler)
 * @see PeerConnectionProvider#setupReceivedChannel(Object, boolean, DataChannelEventHandler)
 */
public interface DataChannelEventHandler {

    /**
     * Called when the reliable data channel transitions to the OPEN state.
     *
     * <p>This marks the peer connection as fully established. The
     * {@link BaseWebRTCClient} sets the peer's connected flag and notifies
     * {@link WebRTCClientListener#onConnected(WebRTCPeer)}.</p>
     */
    void onReliableOpen();

    /**
     * Called when the reliable data channel closes.
     *
     * <p>This marks the peer as disconnected. The {@link BaseWebRTCClient}
     * clears the connected flag and notifies
     * {@link WebRTCClientListener#onDisconnected(WebRTCPeer)}.</p>
     */
    void onReliableClose();

    /**
     * Called when the unreliable data channel transitions to the OPEN state.
     *
     * <p>This is logged but does not affect the peer's connected state.
     * The unreliable channel is optional; if it fails to open, data can
     * still be sent via the reliable channel as a fallback.</p>
     */
    void onUnreliableOpen();

    /**
     * Called when the unreliable data channel closes.
     *
     * <p>This is logged but does not affect the peer's connected state.</p>
     */
    void onUnreliableClose();

    /**
     * Called when data is received on either data channel.
     *
     * <p>The {@link BaseWebRTCClient} forwards this to
     * {@link WebRTCClientListener#onMessage(WebRTCPeer, byte[], boolean)}.</p>
     *
     * @param data     the raw bytes received
     * @param reliable {@code true} if received on the reliable channel,
     *                 {@code false} if received on the unreliable channel
     */
    void onMessage(byte[] data, boolean reliable);
}
