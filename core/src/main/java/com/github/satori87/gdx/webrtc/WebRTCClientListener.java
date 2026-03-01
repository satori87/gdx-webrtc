package com.github.satori87.gdx.webrtc;

/**
 * Listener interface for WebRTC client events.
 *
 * <p>Implement this interface to receive notifications about signaling connections,
 * peer connections, data messages, and errors. All callbacks may fire on internal
 * threads (WebSocket thread, WebRTC callback thread, or scheduler thread) rather
 * than the main/render thread.</p>
 *
 * <p>In a libGDX application, wrap UI-affecting logic in {@code Gdx.app.postRunnable()}
 * to ensure thread safety:</p>
 * <pre>
 * public void onConnected(final WebRTCPeer peer) {
 *     Gdx.app.postRunnable(new Runnable() {
 *         public void run() {
 *             // Safe to update game state here
 *         }
 *     });
 * }
 * </pre>
 *
 * @see WebRTCClient#setListener(WebRTCClientListener)
 */
public interface WebRTCClientListener {

    /**
     * Called when a peer-to-peer connection is fully established and ready for data transfer.
     *
     * <p>At this point, the reliable data channel is open and
     * {@link WebRTCPeer#sendReliable(byte[])} / {@link WebRTCPeer#sendUnreliable(byte[])}
     * can be called.</p>
     *
     * @param peer the newly connected peer
     */
    void onConnected(WebRTCPeer peer);

    /**
     * Called when a peer connection is permanently lost.
     *
     * <p>This only fires after all ICE restart attempts have been exhausted
     * (configurable via {@link WebRTCConfiguration#maxIceRestartAttempts}),
     * or when the peer explicitly closes the connection. Temporary ICE
     * disconnections are handled internally with automatic restart.</p>
     *
     * @param peer the disconnected peer (no longer usable for sending data)
     */
    void onDisconnected(WebRTCPeer peer);

    /**
     * Called when data is received from a remote peer.
     *
     * @param peer     the peer that sent the message
     * @param data     the raw bytes received
     * @param reliable {@code true} if received on the reliable (ordered) channel,
     *                 {@code false} if received on the unreliable (unordered) channel
     */
    void onMessage(WebRTCPeer peer, byte[] data, boolean reliable);

    /**
     * Called when the signaling server connection is established and a local peer ID
     * has been assigned.
     *
     * <p>This corresponds to receiving a WELCOME message from the signaling server.
     * After this callback, {@link WebRTCClient#connectToPeer(int)} can be used to
     * initiate peer connections.</p>
     *
     * @param localId the unique peer ID assigned by the signaling server
     */
    void onSignalingConnected(int localId);

    /**
     * Called when another peer joins the signaling server.
     *
     * <p>Use this to discover available peers and optionally initiate connections
     * via {@link WebRTCClient#connectToPeer(int)}.</p>
     *
     * @param peerId the ID of the peer that joined
     */
    void onPeerJoined(int peerId);

    /**
     * Called when another peer leaves the signaling server.
     *
     * @param peerId the ID of the peer that left
     */
    void onPeerLeft(int peerId);

    /**
     * Called when an error occurs in the signaling or WebRTC layer.
     *
     * <p>Errors may include signaling WebSocket failures, WebRTC factory initialization
     * failures, SDP offer/answer creation failures, or handshake errors.</p>
     *
     * @param error a human-readable error description
     */
    void onError(String error);
}
