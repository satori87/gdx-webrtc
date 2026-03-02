package com.github.satori87.gdx.webrtc.transport;

/**
 * Abstract client transport for connecting to a single server.
 *
 * <p>Provides reliable and unreliable data channels for sending data to the
 * server. The connection lifecycle is managed through {@link TransportListener}
 * callbacks.</p>
 *
 * <p>This interface does not include a {@code connect(host, port)} method because
 * WebRTC connections are established through signaling (SDP/ICE exchange) rather
 * than direct socket connections. See {@link WebRTCClientTransport} for the
 * signaling methods.</p>
 *
 * @see WebRTCClientTransport
 * @see TransportListener
 */
public interface ClientTransport {

    /**
     * Disconnects from the server and releases all resources.
     *
     * <p>Closes the underlying connection, cancels any pending timers (e.g.
     * ICE restart timers), and shuts down the internal scheduler. After calling
     * this method, the transport should not be reused.</p>
     */
    void disconnect();

    /**
     * Returns whether the transport is currently connected to the server.
     *
     * @return {@code true} if the reliable data channel is open and ready
     *         for data transfer
     */
    boolean isConnected();

    /**
     * Sends data to the server over the reliable (ordered, guaranteed delivery)
     * channel.
     *
     * <p>Data is delivered in order and retransmitted until acknowledged. Use
     * this for game state, chat messages, or any data that must not be lost.</p>
     *
     * <p>This method is a no-op if the transport is not currently connected.</p>
     *
     * @param data the raw bytes to send
     */
    void sendReliable(byte[] data);

    /**
     * Sends data to the server over the unreliable (unordered, no retransmit)
     * channel.
     *
     * <p>Packets are silently dropped if the send buffer exceeds the configured
     * limit (default 64 KB). If the unreliable channel is not available, falls
     * back to the reliable channel.</p>
     *
     * <p>Use this for position updates, input snapshots, or any latency-sensitive
     * data where occasional packet loss is acceptable.</p>
     *
     * @param data the raw bytes to send
     * @see com.github.satori87.gdx.webrtc.WebRTCConfiguration#unreliableBufferLimit
     */
    void sendUnreliable(byte[] data);

    /**
     * Sets the event listener for this transport.
     *
     * <p>The listener receives connection, disconnection, message, and error
     * events. Pass {@code null} to disable callbacks.</p>
     *
     * @param listener the listener to set, or {@code null} to disable callbacks
     */
    void setListener(TransportListener listener);
}
