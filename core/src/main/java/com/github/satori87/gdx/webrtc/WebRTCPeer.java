package com.github.satori87.gdx.webrtc;

/**
 * Represents a remote peer connected via WebRTC.
 *
 * <p>Each {@code WebRTCPeer} wraps a single RTCPeerConnection with two data channels:
 * a reliable channel (ordered, guaranteed delivery) and an unreliable channel (unordered,
 * fire-and-forget). Obtain a peer instance via
 * {@link WebRTCClientListener#onConnected(WebRTCPeer)}.</p>
 *
 * <p>Implementations are not thread-safe. Send calls may be invoked from any thread,
 * but concurrent sends on the same peer are not guaranteed to interleave correctly.</p>
 *
 * @see WebRTCClient#connectToPeer(int)
 * @see WebRTCClientListener#onConnected(WebRTCPeer)
 */
public interface WebRTCPeer {

    /**
     * Returns the unique numeric ID of this peer, as assigned by the signaling server.
     *
     * @return the peer's signaling ID
     */
    int getId();

    /**
     * Sends data to this peer over the reliable (ordered, guaranteed delivery) data channel.
     *
     * <p>Data is delivered in order and retransmitted until acknowledged. Use this for
     * game state, chat messages, or any data that must not be lost.</p>
     *
     * <p>This method is a no-op if the peer is not currently connected or the reliable
     * channel is not available.</p>
     *
     * @param data the raw bytes to send
     */
    void sendReliable(byte[] data);

    /**
     * Sends data to this peer over the unreliable (unordered, no retransmit) data channel.
     *
     * <p>Packets are silently dropped if the send buffer exceeds the configured limit
     * (default 64 KB, configurable via {@link WebRTCConfiguration#unreliableBufferLimit}).
     * If the unreliable channel is not available, falls back to the reliable channel.</p>
     *
     * <p>Use this for position updates, input snapshots, or any latency-sensitive data
     * where occasional packet loss is acceptable.</p>
     *
     * @param data the raw bytes to send
     * @see WebRTCConfiguration#unreliableBufferLimit
     */
    void sendUnreliable(byte[] data);

    /**
     * Returns whether this peer connection is currently open and ready for data transfer.
     *
     * @return {@code true} if the reliable data channel is open and the ICE connection
     *         has not been permanently lost
     */
    boolean isConnected();

    /**
     * Closes this peer connection and releases all associated resources.
     *
     * <p>Cancels any pending ICE restart timers, closes the underlying RTCPeerConnection,
     * and removes this peer from the client's peer map. After calling this method,
     * the peer instance should not be reused.</p>
     */
    void close();
}
