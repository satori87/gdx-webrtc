package com.github.satori87.gdx.webrtc.transport;

/**
 * Client-side transport event listener.
 *
 * <p>Implement this interface to receive notifications about connection state
 * changes, incoming data, and errors on a {@link ClientTransport}. All callbacks
 * may fire on internal threads (WebRTC callback thread or scheduler thread)
 * rather than the main/render thread.</p>
 *
 * <p>In a libGDX application, wrap UI-affecting logic in
 * {@code Gdx.app.postRunnable()} to ensure thread safety.</p>
 *
 * @see ClientTransport#setListener(TransportListener)
 */
public interface TransportListener {

    /**
     * Called when the transport connection is fully established and ready for
     * data transfer.
     *
     * <p>For WebRTC transports, this fires when the reliable data channel
     * opens after a successful SDP/ICE handshake.</p>
     */
    void onConnected();

    /**
     * Called when the transport connection is permanently lost.
     *
     * <p>For WebRTC transports, this only fires after all ICE restart attempts
     * have been exhausted or the connection is explicitly closed. Temporary
     * ICE disconnections are handled internally with automatic restart.</p>
     */
    void onDisconnected();

    /**
     * Called when data is received from the remote endpoint.
     *
     * @param data     the raw bytes received
     * @param reliable {@code true} if received on the reliable (ordered) channel,
     *                 {@code false} if received on the unreliable (unordered) channel
     */
    void onMessage(byte[] data, boolean reliable);

    /**
     * Called when an error occurs in the transport layer.
     *
     * <p>Errors may include WebRTC factory initialization failures, SDP
     * handshake failures, or internal transport errors.</p>
     *
     * @param message a human-readable error description
     */
    void onError(String message);
}
