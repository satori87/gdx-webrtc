package com.github.satori87.gdx.webrtc;

/**
 * High-level WebRTC client interface for peer-to-peer communication.
 *
 * <p>A {@code WebRTCClient} connects to a signaling server via WebSocket, discovers
 * peers, and establishes direct peer-to-peer connections using WebRTC. All SDP
 * offer/answer negotiation and ICE candidate exchange is handled internally.</p>
 *
 * <p>Typical usage:</p>
 * <pre>
 * WebRTCConfiguration config = new WebRTCConfiguration();
 * config.signalingServerUrl = "ws://localhost:9090";
 *
 * WebRTCClient client = WebRTCClients.newClient(config, myListener);
 * client.connect();
 *
 * // Later, when a peer ID is known (e.g. from onPeerJoined):
 * client.connectToPeer(peerId);
 * </pre>
 *
 * <p>Create instances via {@link WebRTCClients#newClient(WebRTCConfiguration, WebRTCClientListener)}
 * after setting the platform-specific factory.</p>
 *
 * @see WebRTCClients
 * @see WebRTCClientListener
 * @see WebRTCConfiguration
 */
public interface WebRTCClient {

    /**
     * Connects to the signaling server specified in the configuration.
     *
     * <p>This opens a WebSocket connection to
     * {@link WebRTCConfiguration#signalingServerUrl}. When the server responds
     * with a WELCOME message, {@link WebRTCClientListener#onSignalingConnected(int)}
     * is called with the assigned local peer ID.</p>
     */
    void connect();

    /**
     * Disconnects from the signaling server and closes all active peer connections.
     *
     * <p>This method:</p>
     * <ul>
     *   <li>Closes all peer connections (cancelling ICE restart timers)</li>
     *   <li>Closes the WebSocket to the signaling server</li>
     *   <li>Shuts down the internal scheduler</li>
     *   <li>Resets the local ID to -1</li>
     * </ul>
     */
    void disconnect();

    /**
     * Returns whether the WebSocket connection to the signaling server is currently open.
     *
     * @return {@code true} if the signaling WebSocket is open
     */
    boolean isConnectedToSignaling();

    /**
     * Initiates a peer-to-peer connection to the specified peer.
     *
     * <p>Sends a CONNECT_REQUEST message to the signaling server, which relays it to
     * the target peer. The target peer then creates an SDP offer, and the two peers
     * exchange SDP and ICE candidates through the signaling server until the
     * connection is established.</p>
     *
     * <p>When the connection is ready, {@link WebRTCClientListener#onConnected(WebRTCPeer)}
     * is called.</p>
     *
     * @param peerId the signaling ID of the peer to connect to
     * @see WebRTCClientListener#onConnected(WebRTCPeer)
     */
    void connectToPeer(int peerId);

    /**
     * Replaces the current event listener.
     *
     * <p>The new listener will receive all subsequent events. Pass {@code null} to
     * disable event callbacks (events will be silently discarded).</p>
     *
     * @param listener the new listener, or {@code null} to disable callbacks
     */
    void setListener(WebRTCClientListener listener);

    /**
     * Returns the local peer ID assigned by the signaling server.
     *
     * @return the local peer ID, or -1 if not yet connected or after disconnect
     */
    int getLocalId();
}
